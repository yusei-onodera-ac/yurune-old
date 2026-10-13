package app.yurune.service;

import app.yurune.constant.LlmConstants;
import app.yurune.domain.entity.NightSession;
import app.yurune.domain.value.AgentSource;
import app.yurune.domain.value.FallbackReason;
import app.yurune.domain.value.Observation;
import app.yurune.domain.value.ToolCall;

import java.util.ArrayList;
import java.util.List;

/**
 * 夜のエージェントの、固定の枠と LLM の組み合わせ。
 * 1 終了（無反応が基準を超えたとき）は、LLM に聞かず、コードの規則が行う。
 * 2 反応のない時間が短いうちは、何もしない（利用者が操作しているため、LLM にも聞かない）。
 * 3 LLM を呼べるのは、呼び出しの間隔と日次の上限の範囲内で、ブレーカーが閉じているときだけ。
 *   呼べないとき、また LLM が使えないときは、規則に切り替える。音の再生と自動終了は、止まらない。
 *
 * 判断のたびに、経路（規則・LLM・切り替え）と、かかった時間を、AgentDecisionContext に記録する
 * 。
 */
public final class ResilientNightAgent implements NightAgent {

    private static final long NANOS_PER_MILLI = 1_000_000L;

    private final RuleBasedNightAgent ruleBasedAgent;
    private final NightAgent llmAgent;
    private final LlmCallLimiter callLimiter;
    private final CircuitBreaker circuitBreaker;

    public ResilientNightAgent(RuleBasedNightAgent ruleBasedAgent, NightAgent llmAgent,
            LlmCallLimiter callLimiter, CircuitBreaker circuitBreaker) {
        this.ruleBasedAgent = ruleBasedAgent;
        this.llmAgent = llmAgent;
        this.callLimiter = callLimiter;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public List<ToolCall> decide(Observation observation, NightSession session) {
        long startedAt = System.nanoTime();
        List<ToolCall> endCalls = ruleBasedAgent.decideEnd(observation);
        if (!endCalls.isEmpty()) {
            AgentDecisionContext.record(AgentSource.RULE, null, elapsedMillis(startedAt));
            return endCalls;
        }
        // 規則の音量下げ（FADE）は、LLM が使えるかどうかに関係なく、毎回まず判断する。
        // ここで判断するだけにしておき、呼び出し元へ返す一覧の先頭に差し込むのは withFade で行う。
        List<ToolCall> fadeCalls = ruleBasedAgent.decideFade(observation, session);
        if (observation.getSilenceSec() < LlmConstants.CONSIDER_AFTER_SILENCE_SEC) {
            AgentDecisionContext.record(AgentSource.RULE, null, elapsedMillis(startedAt));
            return fadeCalls;
        }
        if (!circuitBreaker.allowCall()) {
            List<ToolCall> calls = withFade(fadeCalls, ruleBasedAgent.decide(observation, session));
            AgentDecisionContext.record(AgentSource.FALLBACK, FallbackReason.BREAKER_OPEN, elapsedMillis(startedAt));
            return calls;
        }
        if (!callLimiter.tryAcquire()) {
            List<ToolCall> calls = withFade(fadeCalls, ruleBasedAgent.decide(observation, session));
            AgentDecisionContext.record(AgentSource.FALLBACK, FallbackReason.LIMITER, elapsedMillis(startedAt));
            return calls;
        }
        return askLlm(observation, session, startedAt, fadeCalls);
    }

    private List<ToolCall> askLlm(Observation observation, NightSession session, long startedAt, List<ToolCall> fadeCalls) {
        try {
            List<ToolCall> calls = llmAgent.decide(observation, session);
            circuitBreaker.recordSuccess();
            AgentDecisionContext.record(AgentSource.LLM, null, elapsedMillis(startedAt));
            return withFade(fadeCalls, calls);
        } catch (LlmUnavailableException exception) {
            circuitBreaker.recordFailure();
            List<ToolCall> calls = ruleBasedAgent.decide(observation, session);
            AgentDecisionContext.record(AgentSource.FALLBACK, exception.getReason(), elapsedMillis(startedAt));
            return withFade(fadeCalls, calls);
        }
    }

    /** 規則の音量下げ（FADE）を、一覧の先頭にする（規則の下げが優先）。 */
    private static List<ToolCall> withFade(List<ToolCall> fadeCalls, List<ToolCall> rest) {
        if (fadeCalls.isEmpty()) {
            return rest;
        }
        if (rest.isEmpty()) {
            return fadeCalls;
        }
        List<ToolCall> combined = new ArrayList<>(fadeCalls);
        combined.addAll(rest);
        return combined;
    }

    private long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / NANOS_PER_MILLI;
    }
}
