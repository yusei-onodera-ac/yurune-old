package app.yurune.service;

import app.yurune.domain.entity.NightSession;
import app.yurune.domain.value.LlmOutcome;
import app.yurune.domain.value.LlmPurpose;
import app.yurune.domain.value.ModelName;
import app.yurune.domain.value.Observation;
import app.yurune.domain.value.TokenUsage;
import app.yurune.domain.value.ToolCall;

import java.util.List;

/**
 * Gemini が判断する夜のエージェント（枠の中の判断）。
 * 観測を送り、返ってきたツール呼び出しを、そのまま渡す。検証は ToolGuard が行う。
 */
public final class GeminiNightAgent implements NightAgent {

    private static final long NANOS_PER_MILLI = 1_000_000L;

    private final LlmGateway llmGateway;
    private final GeminiRequestBuilder requestBuilder;
    private final GeminiResponseParser responseParser;
    private final AuditLogger auditLogger;

    public GeminiNightAgent(LlmGateway llmGateway, GeminiRequestBuilder requestBuilder,
            GeminiResponseParser responseParser) {
        this(llmGateway, requestBuilder, responseParser, null);
    }

    /**
     * @param auditLogger LLM 呼び出し 1 回ごとの記録（A2）の出し先。省略できる（既存の呼び出し元と互換を保つため。
     *                     省略時は記録しない）
     */
    public GeminiNightAgent(LlmGateway llmGateway, GeminiRequestBuilder requestBuilder,
            GeminiResponseParser responseParser, AuditLogger auditLogger) {
        this.llmGateway = llmGateway;
        this.requestBuilder = requestBuilder;
        this.responseParser = responseParser;
        this.auditLogger = auditLogger;
    }

    /** LLM が使えないとき、または応答が読めないときは、LlmUnavailableException。 */
    @Override
    public List<ToolCall> decide(Observation observation, NightSession session) {
        long startedAt = System.nanoTime();
        try {
            String response = llmGateway.generate(requestBuilder.build(observation, session));
            List<ToolCall> calls = responseParser.parse(response);
            logCall(LlmOutcome.OK, startedAt, response);
            return calls;
        } catch (LlmUnavailableException exception) {
            logCall(LlmOutcome.fromFailure(exception.getReason()), startedAt, null);
            throw exception;
        }
    }

    private void logCall(LlmOutcome outcome, long startedAtNanos, String rawResponse) {
        if (auditLogger == null) {
            return;
        }
        long latencyMillis = (System.nanoTime() - startedAtNanos) / NANOS_PER_MILLI;
        TokenUsage tokens = rawResponse == null ? TokenUsage.none() : GeminiResponseParser.readTokenUsage(rawResponse);
        auditLogger.llm(outcome, latencyMillis, new ModelName(llmGateway.getModel()), LlmPurpose.NIGHT, tokens);
    }
}
