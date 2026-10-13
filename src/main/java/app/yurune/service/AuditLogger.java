package app.yurune.service;

import app.yurune.constant.HttpStatusConstants;
import app.yurune.constant.LogConstants;
import app.yurune.constant.ObservabilityConstants;
import app.yurune.domain.entity.Decision;
import app.yurune.domain.value.AgentSource;
import app.yurune.domain.value.ControlState;
import app.yurune.domain.value.DegradationKind;
import app.yurune.domain.value.FallbackReason;
import app.yurune.domain.value.LlmOutcome;
import app.yurune.domain.value.LlmPurpose;
import app.yurune.domain.value.ModelName;
import app.yurune.domain.value.PiiKind;
import app.yurune.domain.value.SettingItem;
import app.yurune.domain.value.SpanName;
import app.yurune.domain.value.TokenUsage;
import app.yurune.domain.value.ToolName;
import app.yurune.domain.value.ToolRejectReason;
import app.yurune.domain.value.UserSettings;

import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Locale;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * 運用の記録（構造化ログ）を 1 行の JSON で出す。Cloud Run では、標準出力の JSON 行が、そのまま Cloud Logging の
 * 構造化ログになる（severity、message、トレースの項目は、Cloud Logging が読む）。
 *
 * 自由な文を書く窓口は、作らない。書けるのは、種類と件数、経路、状態の名前だけで、
 * 会話の本文、音声、メールアドレス、パスワード、LLM に送った文は、型の上で渡せない。
 * 利用者は、uid を一方向に変えた短い名前（uidHash）で表す。
 */
public final class AuditLogger {

    /** control ログの state のうち、縮退している（異常に気づきたい）ものだけを、目立たせる（WARNING）。 */
    private static final Set<ControlState> DEGRADED_STATES =
            Set.of(ControlState.OPEN, ControlState.HALF_OPEN, ControlState.OFF, ControlState.DOWN);

    private final Clock clock;
    private final String projectId;
    private final Consumer<String> sink;

    /** @param projectId トレースを Cloud Trace とつなぐための、プロジェクト ID（なければ null で、トレース ID だけを出す） */
    public AuditLogger(Clock clock, String projectId, Consumer<String> sink) {
        this.clock = clock;
        this.projectId = projectId;
        this.sink = sink;
    }

    public void request(String method, String path, int status, long latencyMillis, String uidHash) {
        JsonObject detail = new JsonObject();
        detail.addProperty("method", method);
        detail.addProperty("path", path);
        detail.addProperty("status", status);
        detail.addProperty("latencyMs", latencyMillis);
        if (uidHash != null) {
            detail.addProperty("user", uidHash);
        }
        write(status >= HttpStatusConstants.SERVER_ERROR ? LogConstants.SEVERITY_WARNING : LogConstants.SEVERITY_INFO, LogConstants.EVENT_REQUEST, detail);
    }

    /** 会話 1 回分の、守りの結果。入力の検査、LLM（定型文への差し替え）、出力の検査を、1 本の記録にする。 */
    public void conversation(GuardEvent event) {
        JsonObject detail = new JsonObject();
        detail.addProperty("input", event.getInputOutcome());
        detail.addProperty("reply", event.getSource().asId());
        detail.addProperty("truncated", event.wasInputTruncated());
        detail.addProperty("latencyMs", event.getLatencyMillis());
        Map<String, Integer> masked = new TreeMap<>();
        for (Map.Entry<PiiKind, Integer> entry : event.getMaskedCounts().entrySet()) {
            masked.put(entry.getKey().asId(), entry.getValue());
        }
        if (!masked.isEmpty()) {
            JsonObject kinds = new JsonObject();
            masked.forEach(kinds::addProperty);
            detail.add("masked", kinds);
        }
        event.findViolationKind().ifPresent(kind -> detail.addProperty("outputViolation", kind));
        event.findDownReason().ifPresent(reason -> detail.addProperty("down", reason));
        write(event.findDownReason().isPresent() ? LogConstants.SEVERITY_WARNING : LogConstants.SEVERITY_INFO,
                LogConstants.EVENT_CONVERSATION, detail);
    }

    /** 判断の記録を保存したことを、段階と、受け入れたかどうかだけで残す（選んだこと、理由の文は出さない）。 */
    public void decision(Decision decision) {
        JsonObject detail = new JsonObject();
        detail.addProperty("phase", decision.getPhase().asId());
        detail.addProperty("accepted", decision.isAccepted());
        write(decision.isAccepted() ? LogConstants.SEVERITY_INFO : LogConstants.SEVERITY_WARNING, LogConstants.EVENT_DECISION, detail);
    }

    /** 認証まわりの操作の名前。 */
    public enum AuthAction {
        LOGIN, REGISTER, LOGOUT, ERASE_CONFIRM
    }

    /** 登録・ログイン・ログアウト・消去の確認の結果を、操作の名前と、失敗の種類（なければ成功）だけで残す。メールアドレスは出さない。 */
    public void auth(AuthAction action, AuthException.Reason failure) {
        JsonObject detail = new JsonObject();
        detail.addProperty("action", action.name().toLowerCase(Locale.ROOT));
        detail.addProperty("outcome", failure == null ? "success" : failure.name().toLowerCase(Locale.ROOT));
        write(failure == null ? LogConstants.SEVERITY_INFO : LogConstants.SEVERITY_WARNING, LogConstants.EVENT_AUTH, detail);
    }

    /** 設定を変えたことを、項目の名前と、変えたあとの値（選べる値の中のもの）だけで残す。値は、設定の型から作る。 */
    public void setting(SettingItem item, UserSettings changed) {
        JsonObject detail = new JsonObject();
        detail.addProperty("item", item.asId());
        detail.addProperty("value", changed.describeValue(item));
        write(LogConstants.SEVERITY_INFO, LogConstants.EVENT_SETTING, detail);
    }

    /**
     * 見直しの規則（確認 C1〜C3、見直し R1〜R6）の結果を、規則の名前、判定（または決めたこと）、夜数だけで残す。文の本文は入れない。
     */
    public void pdca(String ruleId, String verdict, int nights) {
        JsonObject detail = new JsonObject();
        detail.addProperty("rule", ruleId);
        detail.addProperty("verdict", verdict);
        detail.addProperty("nights", nights);
        write(LogConstants.SEVERITY_INFO, LogConstants.EVENT_PDCA, detail);
    }

    /** 「データを消す」の結果を、利用者の名前と、成功したかどうかだけで残す。 */
    public void erase(String uidHash, boolean success) {
        JsonObject detail = new JsonObject();
        detail.addProperty("user", uidHash);
        detail.addProperty("success", success);
        write(success ? LogConstants.SEVERITY_INFO : LogConstants.SEVERITY_WARNING, LogConstants.EVENT_ERASE, detail);
    }

    // ---- 運用の記録（可観測性）のための記録 ----

    /**
     * 夜のエージェントの、道具の呼び出し 1 回ごとの記録（A1）。本文・理由の自由な文は入れない。
     * @param toolId 呼ばれた道具の ID。許可リストにある名前だけを、そのまま記録する（下の knownToolId を参照）
     * @param rejectReason 拒否のときの理由のコード（受理のときは null）
     * @param fallbackReason source が FALLBACK のときだけの、切り替えた理由（それ以外は null）
     */
    public void agent(String toolId, boolean accepted, ToolRejectReason rejectReason, AgentSource source,
            long latencyMillis, FallbackReason fallbackReason) {
        JsonObject detail = new JsonObject();
        detail.addProperty("tool", knownToolId(toolId));
        detail.addProperty("accepted", accepted);
        if (rejectReason != null) {
            detail.addProperty("rejectReason", rejectReason.asId());
        }
        detail.addProperty("source", source.asId());
        detail.addProperty("latencyMs", latencyMillis);
        if (fallbackReason != null) {
            detail.addProperty("fallbackReason", fallbackReason.asId());
        }
        write(accepted ? LogConstants.SEVERITY_INFO : LogConstants.SEVERITY_WARNING, LogConstants.EVENT_AGENT, detail);
    }

    /**
     * 道具の ID は、LLM の応答から読んだ、検査前の文字列（ToolCall の toolId）のことがある。許可リストにない名前は、
     * 長さを切って出しても、プロンプトに注入された発言の一部（本文）が、そのまま記録に残りうる（AT-06）。
     * 切るのではなく、許可リストの名前だけを通し、それ以外は固定の語に置き換える。
     */
    private String knownToolId(String toolId) {
        try {
            return ToolName.fromId(toolId).asId();
        } catch (IllegalArgumentException exception) {
            return ObservabilityConstants.UNKNOWN_TOOL_ID;
        }
    }

    /** LLM 呼び出し 1 回ごとの記録（A2）。本文は入れない。トークン数は、分かるときだけ。 */
    public void llm(LlmOutcome outcome, long latencyMillis, ModelName model, LlmPurpose purpose, TokenUsage tokens) {
        JsonObject detail = new JsonObject();
        detail.addProperty("outcome", outcome.asId());
        detail.addProperty("latencyMs", latencyMillis);
        detail.addProperty("model", model.asId());
        detail.addProperty("purpose", purpose.asId());
        tokens.getPromptTokens().ifPresent(value -> detail.addProperty("promptTokens", value));
        tokens.getCandidateTokens().ifPresent(value -> detail.addProperty("candidateTokens", value));
        write(outcome == LlmOutcome.OK || outcome == LlmOutcome.REJECTED
                ? LogConstants.SEVERITY_INFO : LogConstants.SEVERITY_WARNING, LogConstants.EVENT_LLM, detail);
    }

    /** 制御の状態が変わったときの記録（A3）。呼ぶ側が、変化したときだけ呼ぶ（連続して同じ状態を出さない）。 */
    public void control(DegradationKind kind, ControlState state) {
        control(kind, state, null);
    }

    /** @param rejectedCount 60 秒ごとに集計した、拒否の総数（それ以外の kind では null） */
    public void control(DegradationKind kind, ControlState state, Long rejectedCount) {
        JsonObject detail = new JsonObject();
        detail.addProperty("kind", kind.asId());
        detail.addProperty("state", state.asId());
        if (rejectedCount != null) {
            detail.addProperty("rejectedCount", rejectedCount);
        }
        write(DEGRADED_STATES.contains(state) ? LogConstants.SEVERITY_WARNING : LogConstants.SEVERITY_INFO,
                LogConstants.EVENT_CONTROL, detail);
    }

    /**
     * 外部呼び出し 1 区間の記録。本文は入れない。Firestore は量が多いため、
     * 呼ぶ側が「遅いもの・失敗だけ」に絞ってよい（ほかの区間は、毎回呼ぶ）。
     */
    public void span(SpanName name, long durationMillis, boolean ok) {
        JsonObject detail = new JsonObject();
        detail.addProperty("name", name.asId());
        detail.addProperty("durationMs", durationMillis);
        detail.addProperty("outcome", ok ? "ok" : "error");
        write(ok ? LogConstants.SEVERITY_INFO : LogConstants.SEVERITY_WARNING, LogConstants.EVENT_SPAN, detail);
    }

    /** uid を、一方向に、短い名前にする（同じ利用者の記録をつなげるため。uid そのものは出さない）。 */
    public static String hashUid(String uid) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(uid.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, LogConstants.USER_HASH_CHARS);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private void write(String severity, String event, JsonObject detail) {
        JsonObject line = new JsonObject();
        line.addProperty(LogConstants.FIELD_SEVERITY, severity);
        line.addProperty(LogConstants.FIELD_MESSAGE, event);
        line.addProperty(LogConstants.FIELD_TIME, clock.instant().toString());
        line.addProperty(LogConstants.FIELD_EVENT, event);
        TraceContext.current().ifPresent(traceId -> {
            line.addProperty(LogConstants.FIELD_TRACE_ID, traceId);
            if (projectId != null && !projectId.isBlank()) {
                line.addProperty(LogConstants.FIELD_TRACE, "projects/" + projectId + "/traces/" + traceId);
            }
        });
        line.add(LogConstants.FIELD_DETAIL, detail);
        sink.accept(line.toString());
    }
}
