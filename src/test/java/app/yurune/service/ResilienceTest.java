package app.yurune.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.config.AppConfig;
import app.yurune.domain.entity.NightSession;
import app.yurune.domain.value.AgentSource;
import app.yurune.domain.value.ControlState;
import app.yurune.domain.value.FallbackReason;
import app.yurune.domain.value.NightMode;
import app.yurune.domain.value.Observation;
import app.yurune.domain.value.ToolCall;
import app.yurune.domain.value.VolumePercent;
import app.yurune.domain.value.WakeDate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 可用性と、請求の守り: サーキットブレーカー、呼び出し間隔と日次の上限、規則への切り替え。
 */
class ResilienceTest {

    private static final int FAILURE_LIMIT = 3;
    private static final int OPEN_SEC = 60;
    private static final int MIN_INTERVAL_SEC = 20;
    private static final int TALK_GAP_SEC = 90;
    private static final String SOUND_ONLY_RESPONSE =
            "{\"candidates\":[{\"content\":{\"parts\":[{\"functionCall\":{\"name\":\"fade_out\","
            + "\"args\":{\"minutes\":10,\"why\":\"静かに下げます。\"}}}]}}]}";

    private MutableClock clock;
    private AppConfig config;

    @BeforeEach
    void setUp() {
        clock = new MutableClock("2026-10-06T13:10:00Z", "Asia/Tokyo");
        config = new AppConfig(15, 60, 30);
    }

    @AfterEach
    void tearDown() {
        AgentDecisionContext.clear();
    }

    private NightSession session() {
        return new NightSession(WakeDate.parse("2026-10-07"), NightMode.TALK, new VolumePercent(40),
                Instant.parse("2026-10-06T13:10:00Z"), false);
    }

    /**
     * elapsedMin は 0（夜の始まり）にする。ここは可用性（ブレーカー・呼び出しの制限・切り替え）を見るテストで、
     * PDCA の FADE（ResilientNightAgent が毎回先頭に差し込む音量下げ）の対象ではないため、
     * elapsedMin が 0 より大きいと、session() の音量（40%）がまだ下げ切られていない時間帯に入り、
     * 一覧の先頭に意図しない set_volume が混ざってしまう。
     */
    private Observation observation(int silenceSec) {
        return new Observation(0, NightMode.TALK, new VolumePercent(40), silenceSec, silenceSec);
    }

    private ResilientNightAgent createAgent(LlmGateway gateway, int minIntervalSec, int dailyCap) {
        ForbiddenDomainFilter filter = new ForbiddenDomainFilter();
        GeminiNightAgent llmAgent = new GeminiNightAgent(gateway, new GeminiRequestBuilder(config),
                new GeminiResponseParser(filter, new PiiMasker()));
        return new ResilientNightAgent(new RuleBasedNightAgent(config), llmAgent,
                new LlmCallLimiter(minIntervalSec, dailyCap, clock),
                new CircuitBreaker(FAILURE_LIMIT, Duration.ofSeconds(OPEN_SEC), clock));
    }

    // ---- サーキットブレーカー ----

    @Test
    void breakerOpensAfterConsecutiveFailuresAndTriesAgainLater() {
        CircuitBreaker breaker = new CircuitBreaker(FAILURE_LIMIT, Duration.ofSeconds(OPEN_SEC), clock);

        breaker.recordFailure();
        breaker.recordFailure();
        assertTrue(breaker.allowCall(), "2 回の失敗では、まだ閉じている");
        breaker.recordFailure();
        assertFalse(breaker.allowCall(), "3 回続けて失敗したら、開く");

        clock.advance(Duration.ofSeconds(OPEN_SEC - 1));
        assertFalse(breaker.allowCall());
        clock.advance(Duration.ofSeconds(1));
        assertTrue(breaker.allowCall(), "時間が過ぎたら、1 回試す");

        breaker.recordFailure();
        assertFalse(breaker.allowCall(), "試して失敗したら、また開く");

        clock.advance(Duration.ofSeconds(OPEN_SEC));
        breaker.recordSuccess();
        assertTrue(breaker.allowCall(), "成功したら、閉じる");
    }

    @Test
    void aSuccessResetsTheFailureCount() {
        CircuitBreaker breaker = new CircuitBreaker(FAILURE_LIMIT, Duration.ofSeconds(OPEN_SEC), clock);

        breaker.recordFailure();
        breaker.recordFailure();
        breaker.recordSuccess();
        breaker.recordFailure();
        breaker.recordFailure();

        assertTrue(breaker.allowCall());
    }

    // ---- 呼び出しの制限 ----

    @Test
    void limiterEnforcesTheMinimumIntervalOnTheServer() {
        LlmCallLimiter limiter = new LlmCallLimiter(MIN_INTERVAL_SEC, 100, clock);

        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
        clock.advance(Duration.ofSeconds(MIN_INTERVAL_SEC - 1));
        assertFalse(limiter.tryAcquire());
        clock.advance(Duration.ofSeconds(1));
        assertTrue(limiter.tryAcquire());
    }

    @Test
    void limiterEnforcesTheDailyCapAndResetsTheNextDay() {
        LlmCallLimiter limiter = new LlmCallLimiter(0, 3, clock);

        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire(), "4 回目は、日次の上限で断る");

        clock.advance(Duration.ofDays(1));
        assertTrue(limiter.tryAcquire(), "翌日は、数え直す");
    }

    // ---- 組み合わせ ----

    @Test
    void whenTheLlmKeepsFailingItIsNotCalledAnymoreAndRulesTakeOver() {
        AtomicInteger llmCalls = new AtomicInteger();
        ResilientNightAgent agent = createAgent(request -> {
            llmCalls.incrementAndGet();
            throw new LlmUnavailableException("timeout");
        }, 0, 1000);

        for (int attempt = 0; attempt < FAILURE_LIMIT; attempt++) {
            agent.decide(observation(TALK_GAP_SEC), session());
        }
        assertEquals(FAILURE_LIMIT, llmCalls.get());

        List<ToolCall> whileOpen = agent.decide(observation(TALK_GAP_SEC), session());

        assertEquals(FAILURE_LIMIT, llmCalls.get(), "ブレーカーが開いている間は、LLM を呼ばない");
        assertEquals(List.of("switch_mode"), whileOpen.stream().map(ToolCall::getToolId).toList(), "規則が代わる");

        clock.advance(Duration.ofSeconds(OPEN_SEC));
        agent.decide(observation(TALK_GAP_SEC), session());
        assertEquals(FAILURE_LIMIT + 1, llmCalls.get(), "時間が過ぎたら、1 回試す");
    }

    @Test
    void callsFasterThanTheMinimumIntervalDoNotReachTheLlm() {
        AtomicInteger llmCalls = new AtomicInteger();
        ResilientNightAgent agent = createAgent(request -> {
            llmCalls.incrementAndGet();
            return SOUND_ONLY_RESPONSE;
        }, MIN_INTERVAL_SEC, 1000);

        agent.decide(observation(TALK_GAP_SEC), session());
        List<ToolCall> tooSoon = agent.decide(observation(TALK_GAP_SEC), session());

        assertEquals(1, llmCalls.get());
        assertEquals(List.of("switch_mode"), tooSoon.stream().map(ToolCall::getToolId).toList(), "規則が代わる");
    }

    @Test
    void reachingTheDailyCapFallsBackToRulesButNeverStopsTheEnd() {
        AtomicInteger llmCalls = new AtomicInteger();
        ResilientNightAgent agent = createAgent(request -> {
            llmCalls.incrementAndGet();
            return SOUND_ONLY_RESPONSE;
        }, 0, 1);

        agent.decide(observation(TALK_GAP_SEC), session());
        List<ToolCall> overCap = agent.decide(observation(TALK_GAP_SEC), session());
        List<ToolCall> ending = agent.decide(observation(15 * 60), session());

        assertEquals(1, llmCalls.get());
        assertEquals(List.of("switch_mode"), overCap.stream().map(ToolCall::getToolId).toList());
        assertEquals(List.of("fade_out", "end_session"),
                ending.stream().map(ToolCall::getToolId).toList(), "上限に達しても、自動終了は動く");
    }

    // ---- PDCA の FADE 要因: LLM が使えるときも、規則の音量下げが先頭に来る ----

    @Test
    void theRuleFadeIsPrependedEvenWhenTheLlmIsAvailable() {
        ResilientNightAgent agent = createAgent(request -> SOUND_ONLY_RESPONSE, 0, 1000);
        // session() の音量（40%）は、始まってから 15 分経てば、既定の FADE（30 分）の下げ目標より高い
        // ので、規則の音量下げが働く（RuleBasedNightAgentTest で、下げの計算そのものは確かめている）。
        Observation midNight = new Observation(15, NightMode.TALK, new VolumePercent(40), TALK_GAP_SEC, TALK_GAP_SEC);

        List<ToolCall> decided = agent.decide(midNight, session());

        assertEquals(List.of("set_volume", "fade_out"), decided.stream().map(ToolCall::getToolId).toList(),
                "規則の音量下げ（FADE）が、LLM の判断より前に来る");
    }

    // ---- 判断の経路（AgentDecisionContext）が、正しく記録されること ----

    @Test
    void ruleOnlyDecisionsAreRecordedAsRule() {
        ResilientNightAgent agent = createAgent(request -> SOUND_ONLY_RESPONSE, 0, 1000);

        // 無反応が短いうちは、LLM に聞かず、何もしない（規則だけの判断）
        agent.decide(observation(5), session());

        AgentDecisionContext.Entry decision = AgentDecisionContext.current().orElseThrow();
        assertEquals(AgentSource.RULE, decision.getSource());
        assertTrue(decision.getFallbackReason().isEmpty());
    }

    @Test
    void llmDecisionsAreRecordedAsLlm() {
        ResilientNightAgent agent = createAgent(request -> SOUND_ONLY_RESPONSE, 0, 1000);

        agent.decide(observation(TALK_GAP_SEC), session());

        AgentDecisionContext.Entry decision = AgentDecisionContext.current().orElseThrow();
        assertEquals(AgentSource.LLM, decision.getSource());
        assertTrue(decision.getFallbackReason().isEmpty());
    }

    @Test
    void anLlmFailureIsRecordedAsFallbackWithItsClassifiedReason() {
        ResilientNightAgent agent = createAgent(request -> {
            throw new LlmUnavailableException("llm timed out", FallbackReason.TIMEOUT);
        }, 0, 1000);

        agent.decide(observation(TALK_GAP_SEC), session());

        AgentDecisionContext.Entry decision = AgentDecisionContext.current().orElseThrow();
        assertEquals(AgentSource.FALLBACK, decision.getSource());
        assertEquals(FallbackReason.TIMEOUT, decision.getFallbackReason().orElseThrow());
    }

    @Test
    void breakerOpenIsRecordedAsFallbackWithBreakerOpenReason() {
        ResilientNightAgent agent = createAgent(request -> {
            throw new LlmUnavailableException("down");
        }, 0, 1000);
        for (int attempt = 0; attempt < FAILURE_LIMIT; attempt++) {
            agent.decide(observation(TALK_GAP_SEC), session());
        }

        agent.decide(observation(TALK_GAP_SEC), session());

        AgentDecisionContext.Entry decision = AgentDecisionContext.current().orElseThrow();
        assertEquals(AgentSource.FALLBACK, decision.getSource());
        assertEquals(FallbackReason.BREAKER_OPEN, decision.getFallbackReason().orElseThrow());
    }

    @Test
    void theRateLimiterIsRecordedAsFallbackWithLimiterReason() {
        ResilientNightAgent agent = createAgent(request -> SOUND_ONLY_RESPONSE, MIN_INTERVAL_SEC, 1000);
        agent.decide(observation(TALK_GAP_SEC), session());

        agent.decide(observation(TALK_GAP_SEC), session());

        AgentDecisionContext.Entry decision = AgentDecisionContext.current().orElseThrow();
        assertEquals(AgentSource.FALLBACK, decision.getSource());
        assertEquals(FallbackReason.LIMITER, decision.getFallbackReason().orElseThrow());
    }

    // ---- サーキットブレーカーの開閉が、状態の変わったときだけ知らされること ----

    @Test
    void theBreakerReportsEachStateChangeOnlyOnce() {
        List<CircuitBreaker.State> changes = new ArrayList<>();
        CircuitBreaker breaker = new CircuitBreaker(FAILURE_LIMIT, Duration.ofSeconds(OPEN_SEC), clock, changes::add);

        breaker.recordFailure();
        breaker.recordFailure();
        assertTrue(changes.isEmpty(), "まだ開いていない");
        breaker.recordFailure();
        assertEquals(List.of(CircuitBreaker.State.OPEN), changes);

        clock.advance(Duration.ofSeconds(OPEN_SEC));
        breaker.allowCall();
        breaker.allowCall();
        assertEquals(List.of(CircuitBreaker.State.OPEN, CircuitBreaker.State.HALF_OPEN), changes, "半開きは、1 回だけ知らせる");

        breaker.recordSuccess();
        assertEquals(List.of(CircuitBreaker.State.OPEN, CircuitBreaker.State.HALF_OPEN, CircuitBreaker.State.CLOSED), changes);
    }

    // ---- 呼び出しの上限の拒否が、60 秒ごとにまとめて記録されること ----

    @Test
    void rateLimitRejectionsAreAggregatedIntoOneControlLogPerWindow() {
        List<String> lines = new ArrayList<>();
        AuditLogger logger = new AuditLogger(clock, null, lines::add);
        TelemetryCounters counters = new TelemetryCounters();
        LlmCallLimiter limiter = new LlmCallLimiter(10, 1000, clock, ControlState.NIGHT, counters, logger);

        limiter.tryAcquire();
        limiter.tryAcquire();
        limiter.tryAcquire();
        assertTrue(lines.isEmpty(), "まだ 60 秒経っていない（まとめて出す前）");
        assertEquals(2L, counters.get("llm.limiter.rejected.night"), "件数（A4）は、毎回数える");

        clock.advance(Duration.ofSeconds(61));
        limiter.tryAcquire(); // 間隔は十分なので、ここは受け入れ（次の拒否で、まとめて出す窓が閉じる）
        limiter.tryAcquire(); // この拒否も、窓が閉じる前の分として、まとめて数える

        assertEquals(1, lines.size(), "60 秒分を、まとめて 1 行で出す");
        assertTrue(lines.get(0).contains("\"rejectedCount\":3"));
        assertTrue(lines.get(0).contains("\"kind\":\"llm_rate_limit\""));
        assertTrue(lines.get(0).contains("\"state\":\"night\""));
    }
}
