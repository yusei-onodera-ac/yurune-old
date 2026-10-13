package app.yurune.service;

import app.yurune.constant.ConversationConstants;
import app.yurune.constant.TemplateConstants;
import app.yurune.domain.value.ChatTurn;
import app.yurune.domain.value.FactSheet;
import app.yurune.domain.value.FallbackReason;
import app.yurune.domain.value.LlmOutcome;
import app.yurune.domain.value.LlmPurpose;
import app.yurune.domain.value.ModelName;
import app.yurune.domain.value.TokenUsage;
import app.yurune.domain.value.ViolationKind;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 夜の会話の 1 回分。利用者の発言から、返す文までを、固定の枠（コード）で挟んで行う。
 *
 * 順番: 入力の検査（正規化、長さ、個人情報の伏せ字、インジェクション、医療領域）
 *       → 利用の制限と停止（停止の設定、ブレーカー、呼び出しの間隔と日次の上限）
 *       → 入力の検査サービス（Model Armor）→ LLM → 出力の検査（自前）→ 出力の検査サービス
 *       → 通ったら、LLM の文。引っかかったら、定型文に差し替える。
 *
 * 入力の検査サービスが、不適切な言葉（憎悪、嫌がらせ、性的な表現）だけを見つけたときは、返事をせずに、無視する。
 * 指示の無視などを見つけたときは、定型文に差し替える。
 * 定型文は、LLM に書かせない（T-MED、T-FALLBACK、T-DOWN）。医療領域の発言は、LLM を呼ばずに T-MED を返す。
 * LLM・検査サービスが使えないときは、T-DOWN で応じる。記録・グラフ・夜の音と自動終了は、この会話に左右されない。
 * 発言も返事も、保存も、記録もしない（種類と件数だけを、守りの記録に渡す）。
 */
public final class ConversationService {

    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final String DOWN_DISABLED = "disabled";
    private static final String DOWN_BREAKER = "breaker_open";
    private static final String DOWN_LIMITED = "limited";
    private static final String DOWN_LLM = "llm_unavailable";
    private static final String DOWN_ARMOR = "armor_unavailable";

    private final InputGuard inputGuard;
    private final OutputGuard outputGuard;
    private final ConversationPromptBuilder promptBuilder;
    private final LlmGateway llmGateway;
    private final CircuitBreaker circuitBreaker;
    private final LlmCallLimiter callLimiter;
    private final ArmorGateway armorGateway;
    private final Consumer<GuardEvent> audit;
    private final AuditLogger auditLogger;
    private final AvailabilityTracker armorAvailability;

    /** @param llmGateway LLM に つなげないとき（鍵がない、停止の設定）は null。このときは、いつも T-DOWN で応じる */
    public ConversationService(InputGuard inputGuard, OutputGuard outputGuard, ConversationPromptBuilder promptBuilder,
            LlmGateway llmGateway, CircuitBreaker circuitBreaker, LlmCallLimiter callLimiter, ArmorGateway armorGateway,
            Consumer<GuardEvent> audit) {
        this(inputGuard, outputGuard, promptBuilder, llmGateway, circuitBreaker, callLimiter, armorGateway, audit,
                null, null);
    }

    /**
     * @param auditLogger LLM 呼び出し 1 回ごとの記録（A2）と、Model Armor の停止・復帰の記録（A3）の出し先。
     *                     省略できる（既存の呼び出し元と互換を保つため。省略時は記録しない）
     * @param armorAvailability Model Armor が落ちているかどうかを、全員で共有して覚える部品。省略できる
     */
    public ConversationService(InputGuard inputGuard, OutputGuard outputGuard, ConversationPromptBuilder promptBuilder,
            LlmGateway llmGateway, CircuitBreaker circuitBreaker, LlmCallLimiter callLimiter, ArmorGateway armorGateway,
            Consumer<GuardEvent> audit, AuditLogger auditLogger, AvailabilityTracker armorAvailability) {
        this.inputGuard = inputGuard;
        this.outputGuard = outputGuard;
        this.promptBuilder = promptBuilder;
        this.llmGateway = llmGateway;
        this.circuitBreaker = circuitBreaker;
        this.callLimiter = callLimiter;
        this.armorGateway = armorGateway;
        this.audit = audit;
        this.auditLogger = auditLogger;
        this.armorAvailability = armorAvailability == null ? new AvailabilityTracker(null) : armorAvailability;
    }

    public ConversationReply respond(String utterance, List<ChatTurn> history, FactSheet facts) {
        long startedAt = System.nanoTime();
        InputVerdict input = inputGuard.inspect(utterance);
        Result result = decide(input, history, facts);
        audit.accept(new GuardEvent(input.getOutcome().asId(), input.getMaskedCounts(), input.wasTruncated(),
                result.reply.getSource(), result.reply.getViolationKind().map(ViolationKind::asId).orElse(null),
                result.downReason, (System.nanoTime() - startedAt) / NANOS_PER_MILLI));
        return result.reply;
    }

    private static final class Result {
        private final ConversationReply reply;
        private final String downReason;

        private Result(ConversationReply reply, String downReason) {
            this.reply = reply;
            this.downReason = downReason;
        }
    }

    private Result decide(InputVerdict input, List<ChatTurn> history, FactSheet facts) {
        return switch (input.getOutcome()) {
            case EMPTY -> new Result(ConversationReply.silence(), null);
            case DISTRESS -> new Result(ConversationReply.silence(), null);
            case MEDICAL -> new Result(
                    ConversationReply.of(TemplateConstants.MEDICAL_REPLY, ConversationReply.Source.MEDICAL_TEMPLATE), null);
            case INJECTION -> new Result(
                    ConversationReply.replaced(TemplateConstants.FALLBACK_REPLY, ViolationKind.INJECTION), null);
            case PASS -> askLlm(input, history, facts);
        };
    }

    private Result askLlm(InputVerdict input, List<ChatTurn> history, FactSheet facts) {
        if (llmGateway == null) {
            return down(DOWN_DISABLED);
        }
        if (!circuitBreaker.allowCall()) {
            return down(DOWN_BREAKER);
        }
        if (!callLimiter.tryAcquire()) {
            return down(DOWN_LIMITED);
        }
        long startedAt = System.nanoTime();
        try {
            if (armorGateway.isEnabled()) {
                ArmorVerdict verdict = armorGateway.screenPrompt(input.getSanitizedText());
                armorAvailability.recordSuccess();
                if (verdict.isContentOnly()) {
                    // 不適切な言葉（憎悪、嫌がらせ、性的な表現）だけが見つかったときは、返事をせずに、無視する
                    return new Result(ConversationReply.ignored(ViolationKind.INAPPROPRIATE), null);
                }
                if (verdict.isBlocked()) {
                    return new Result(ConversationReply.replaced(TemplateConstants.FALLBACK_REPLY, ViolationKind.INJECTION), null);
                }
            }
            String response = llmGateway.generate(
                    promptBuilder.build(facts, sanitizeHistory(history, facts), input.getSanitizedText()));
            String text = extractText(response);
            circuitBreaker.recordSuccess();
            ConversationReply reply = checkOutput(text, facts);
            logLlmCall(reply, startedAt, response);
            return new Result(reply, null);
        } catch (LlmUnavailableException exception) {
            circuitBreaker.recordFailure();
            logLlmFailure(exception, startedAt);
            return down(DOWN_LLM);
        } catch (ArmorUnavailableException exception) {
            armorAvailability.recordFailure();
            return down(DOWN_ARMOR);
        }
    }

    private Result down(String reason) {
        return new Result(ConversationReply.of(TemplateConstants.DOWN_REPLY, ConversationReply.Source.DOWN_TEMPLATE), reason);
    }

    /** 出力を、自前の検査と、検査サービスに通す。引っかかったら、出力は捨てて、定型文にする。 */
    private ConversationReply checkOutput(String text, FactSheet facts) {
        OutputVerdict verdict = outputGuard.inspect(text, facts);
        if (!verdict.isAccepted()) {
            return ConversationReply.replaced(TemplateConstants.FALLBACK_REPLY, verdict.getViolationKind().orElseThrow());
        }
        if (armorGateway.isEnabled()) {
            boolean blocked = armorGateway.screenResponse(verdict.getText()).isBlocked();
            armorAvailability.recordSuccess();
            if (blocked) {
                return ConversationReply.replaced(TemplateConstants.FALLBACK_REPLY, ViolationKind.ROLE_BREAK);
            }
        }
        return ConversationReply.of(verdict.getText(), ConversationReply.Source.LLM);
    }

    /** LLM 呼び出し 1 回分の記録（A2）。出力の検査で差し替えたときは REJECTED にする。 */
    private void logLlmCall(ConversationReply reply, long startedAtNanos, String rawResponse) {
        if (auditLogger == null) {
            return;
        }
        LlmOutcome outcome = reply.getViolationKind().isPresent() ? LlmOutcome.REJECTED : LlmOutcome.OK;
        auditLogger.llm(outcome, elapsedMillis(startedAtNanos), new ModelName(llmGateway.getModel()), LlmPurpose.TALK,
                GeminiResponseParser.readTokenUsage(rawResponse));
    }

    private void logLlmFailure(LlmUnavailableException exception, long startedAtNanos) {
        if (auditLogger == null) {
            return;
        }
        auditLogger.llm(LlmOutcome.fromFailure(exception.getReason()), elapsedMillis(startedAtNanos),
                new ModelName(llmGateway.getModel()), LlmPurpose.TALK, TokenUsage.none());
    }

    private long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / NANOS_PER_MILLI;
    }

    /**
     * 応答の文を取り出す。文がない（安全のために止められた、など）ときは、空の文を返し、出力の検査が差し替える。
     * 応答が JSON として読めないときは、LlmUnavailableException（理由は PARSE_ERROR）。
     */
    private String extractText(String responseJson) {
        try {
            JsonObject root = JsonParser.parseString(responseJson).getAsJsonObject();
            JsonArray candidates = root.getAsJsonArray("candidates");
            if (candidates == null || candidates.isEmpty()) {
                return "";
            }
            JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
            if (content == null || content.getAsJsonArray("parts") == null) {
                return "";
            }
            StringBuilder text = new StringBuilder();
            for (JsonElement part : content.getAsJsonArray("parts")) {
                JsonElement piece = part.getAsJsonObject().get("text");
                if (piece != null && piece.isJsonPrimitive()) {
                    text.append(piece.getAsString());
                }
            }
            return text.toString();
        } catch (JsonParseException | IllegalStateException exception) {
            throw new LlmUnavailableException("response is not a JSON object", FallbackReason.PARSE_ERROR, exception);
        }
    }

    /**
     * 端末から届いた直前のやり取りを、検査し直す。発言は、入力と同じ検査（通らないものは捨てる）、
     * 返事は、出力と同じ検査（通らないものは捨てる）。件数も、上限までに絞る。
     */
    private List<ChatTurn> sanitizeHistory(List<ChatTurn> history, FactSheet facts) {
        List<ChatTurn> safe = new ArrayList<>();
        int from = Math.max(0, history.size() - ConversationConstants.HISTORY_MAX_TURNS);
        for (ChatTurn turn : history.subList(from, history.size())) {
            if (turn.isFromUser()) {
                InputVerdict verdict = inputGuard.inspect(turn.getText());
                if (verdict.canAskLlm()) {
                    safe.add(new ChatTurn(true, verdict.getSanitizedText()));
                }
            } else {
                OutputVerdict verdict = outputGuard.inspect(turn.getText(), facts);
                if (verdict.isAccepted()) {
                    safe.add(new ChatTurn(false, verdict.getText()));
                }
            }
        }
        return safe;
    }
}
