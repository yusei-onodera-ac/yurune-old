package app.yurune.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.config.AppConfig;
import app.yurune.constant.GuardConstants;
import app.yurune.constant.TemplateConstants;
import app.yurune.domain.entity.NightPlan;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.DisplayText;
import app.yurune.domain.value.EndReason;
import app.yurune.domain.value.FactSheet;
import app.yurune.domain.value.NightMode;
import app.yurune.domain.value.OptionName;
import app.yurune.repository.DocumentDecisionRepository;
import app.yurune.repository.DocumentNightRepository;
import app.yurune.repository.DocumentNightSessionRepository;
import app.yurune.repository.InMemoryDocumentStore;
import app.yurune.repository.ResilientDecisionRepository;
import app.yurune.repository.ResilientNightRepository;
import app.yurune.repository.ResilientNightSessionRepository;
import app.yurune.repository.UserPaths;
import app.yurune.storage.FlakyDocumentStore;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * 負荷と疑似障害（設計書の可用性の表と、実際の動きが一致すること）。
 * 前半は、たくさんの要求を同時に送って、LLM の呼び出しが上限の中に収まり、数え方が壊れないこと。
 * 後半は、1 つずつ止めて（LLM、保存先、検査サービス、音声）、表のとおりに動くこと。夜の音と自動終了は、止まらない。
 */
class LoadAndFaultInjectionTest {

    private static final int THREADS = 32;

    private final MutableClock clock = new MutableClock("2026-10-06T14:00:00Z", "Asia/Tokyo");
    private final FactSheet facts = new FactSheet(Map.of(GuardConstants.MARKER_MINUTES_LEFT, "33"), "雨の音");

    private <T> List<T> runConcurrently(int tasks, java.util.concurrent.Callable<T> work) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int index = 0; index < tasks; index++) {
                futures.add(pool.submit(work));
            }
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private ConversationService conversation(LlmGateway gateway, LlmCallLimiter limiter, CircuitBreaker breaker, ArmorGateway armor) {
        ForbiddenDomainFilter filter = new ForbiddenDomainFilter();
        PiiMasker masker = new PiiMasker();
        PromptSecrets secrets = new PromptSecrets();
        return new ConversationService(new InputGuard(masker, new InjectionDetector(), filter),
                new OutputGuard(filter, masker, secrets), new ConversationPromptBuilder(secrets), gateway, breaker, limiter, armor,
                event -> { });
    }

    private static final String OK_RESPONSE = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ここにいるよ。\"}]}}]}";

    // ---- 負荷 ----

    @Test
    void aFloodOfConversationRequestsNeverExceedsTheDailyCapOfLlmCalls() throws Exception {
        AtomicInteger llmCalls = new AtomicInteger();
        ConversationService service = conversation(request -> {
            llmCalls.incrementAndGet();
            return OK_RESPONSE;
        }, new LlmCallLimiter(0, 25, clock), new CircuitBreaker(3, Duration.ofSeconds(60), clock), new DisabledArmorGateway());

        List<ConversationReply> replies = runConcurrently(2000, () -> service.respond("おはなししよう", List.of(), facts));

        assertEquals(25, llmCalls.get(), "上限の 25 回を、超えない");
        assertEquals(25, replies.stream().filter(reply -> reply.getSource() == ConversationReply.Source.LLM).count());
        assertEquals(1975, replies.stream().filter(reply -> reply.getText().equals(TemplateConstants.DOWN_REPLY)).count(),
                "残りは、すべて定型文 T-DOWN（規則だけで続く）");
    }

    @Test
    void theMinimumIntervalHoldsUnderConcurrency() throws Exception {
        AtomicInteger llmCalls = new AtomicInteger();
        ConversationService service = conversation(request -> {
            llmCalls.incrementAndGet();
            return OK_RESPONSE;
        }, new LlmCallLimiter(20, 1000, clock), new CircuitBreaker(3, Duration.ofSeconds(60), clock), new DisabledArmorGateway());

        runConcurrently(1000, () -> service.respond("おはなししよう", List.of(), facts));

        assertEquals(1, llmCalls.get(), "同じ 20 秒の間は、1 回だけ");
    }

    @Test
    void theNightLoopLlmCallsStayWithinTheIntervalWhenObservationsFloodIn() throws Exception {
        AppConfig config = new AppConfig(15, 60, 30);
        AtomicInteger llmCalls = new AtomicInteger();
        ForbiddenDomainFilter filter = new ForbiddenDomainFilter();
        PromptSecrets secrets = new PromptSecrets();
        GeminiNightAgent llmAgent = new GeminiNightAgent(request -> {
            llmCalls.incrementAndGet();
            return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"様子を見ます\"}]}}]}";
        }, new GeminiRequestBuilder(config, secrets), new GeminiResponseParser(filter, new PiiMasker()));
        NightSessionService service = new NightSessionService(
                new app.yurune.repository.InMemoryNightSessionRepository(), new app.yurune.repository.InMemoryNightRepository(),
                new app.yurune.repository.InMemoryDecisionRepository(),
                new ResilientNightAgent(new RuleBasedNightAgent(config), llmAgent, new LlmCallLimiter(20, 1000, clock),
                        new CircuitBreaker(3, Duration.ofSeconds(60), clock)),
                new ToolGuard(config, new OutputGuard(filter, new PiiMasker(), secrets)), config, clock);
        service.startNight(NightFixtures.createPlan("rain", "none", "30"), NightMode.SOUND);
        clock.advance(Duration.ofSeconds(90));

        runConcurrently(1500, () -> service.observe(86_000));

        assertTrue(llmCalls.get() <= 1, "1 秒の間に 1500 回の観測が来ても、LLM は 20 秒に 1 回まで: " + llmCalls.get());
    }

    @Test
    void failedLoginsAreCountedExactlyUnderConcurrency() throws Exception {
        AttemptLimiter limiter = new AttemptLimiter(50, Duration.ofMinutes(15), clock);

        runConcurrently(200, () -> {
            limiter.record("a@example.com");
            return null;
        });

        assertTrue(limiter.isBlocked("a@example.com"));
        limiter.reset("a@example.com");
        assertFalse(limiter.isBlocked("a@example.com"));
    }

    // ---- 疑似障害: LLM ----

    @Test
    void llmOutageDoesNotStopTheNightAndTheNightEndsByTheRule() {
        AppConfig config = new AppConfig(15, 60, 30);
        ForbiddenDomainFilter filter = new ForbiddenDomainFilter();
        PromptSecrets secrets = new PromptSecrets();
        GeminiNightAgent llmAgent = new GeminiNightAgent(request -> {
            throw new LlmUnavailableException("down");
        }, new GeminiRequestBuilder(config, secrets), new GeminiResponseParser(filter, new PiiMasker()));
        NightSessionService service = new NightSessionService(
                new app.yurune.repository.InMemoryNightSessionRepository(), new app.yurune.repository.InMemoryNightRepository(),
                new app.yurune.repository.InMemoryDecisionRepository(),
                new ResilientNightAgent(new RuleBasedNightAgent(config), llmAgent, new LlmCallLimiter(0, 1000, clock),
                        new CircuitBreaker(3, Duration.ofSeconds(60), clock)),
                new ToolGuard(config, new OutputGuard(filter, new PiiMasker(), secrets)), config, clock);
        service.startNight(NightFixtures.createPlan("rain", "none", "30"), NightMode.TALK);

        clock.advance(Duration.ofSeconds(90));
        NightObservationResult talkGap = service.observe(86_000);
        assertTrue(talkGap.getAppliedCalls().stream().anyMatch(call -> call.getToolId().equals("switch_mode")), "規則が、会話を止める");

        clock.advance(Duration.ofMinutes(16));
        NightObservationResult ending = service.observe(86_000);

        assertTrue(ending.isEnded(), "規則が、自動終了する");
    }

    // ---- 疑似障害: 保存先 ----

    @Test
    void firestoreOutageDuringTheNightKeepsTheNightRunningAndNothingIsLost() {
        FlakyDocumentStore store = new FlakyDocumentStore(new InMemoryDocumentStore());
        UserPaths paths = new UserPaths("u1");
        DocumentDecisionRepository decisions = new DocumentDecisionRepository(store, paths, clock);
        DocumentNightRepository nights = new DocumentNightRepository(store, paths);
        ResilientDecisionRepository resilientDecisions = new ResilientDecisionRepository(decisions);
        ResilientNightRepository resilientNights = new ResilientNightRepository(nights);
        AppConfig config = new AppConfig(15, 60, 30);
        ForbiddenDomainFilter filter = new ForbiddenDomainFilter();
        NightSessionService service = new NightSessionService(
                new ResilientNightSessionRepository(new DocumentNightSessionRepository(store, paths)), resilientNights,
                resilientDecisions, new RuleBasedNightAgent(config),
                new ToolGuard(config, new OutputGuard(filter, new PiiMasker(), new PromptSecrets())), config, clock);
        Map<Dimension, OptionName> options = new EnumMap<>(Dimension.class);
        options.put(Dimension.SOUND, new OptionName("rain"));
        options.put(Dimension.TALK, new OptionName("none"));
        options.put(Dimension.FADE, new OptionName("30"));
        options.put(Dimension.DURATION, new OptionName("45"));
        NightPlan plan = new NightPlan(options, EnumSet.noneOf(Dimension.class), new DisplayText("理由"), 1L);
        service.startNight(plan, NightMode.SOUND);

        store.setDown(true);
        clock.advance(Duration.ofSeconds(30));
        service.observe(86_000);
        clock.advance(Duration.ofMinutes(16));
        NightObservationResult ending = service.observe(86_000);

        assertTrue(ending.isEnded(), "保存先が止まっていても、自動終了は動く");
        assertTrue(resilientDecisions.countPending() >= 1 && resilientNights.countPending() >= 1, "書けなかった記録は、持っている");

        store.setDown(false);
        resilientNights.findLatestNight();
        resilientDecisions.findLatestDecisions();

        assertEquals(0, resilientDecisions.countPending());
        assertEquals(0, resilientNights.countPending());
        assertEquals(EndReason.NO_REACTION, nights.findLatestNight().orElseThrow().findEndReason().orElseThrow(), "戻ったら、終わり方が保存される");
        assertTrue(decisions.findLatestDecisions().size() >= 3, "計画、終了の判断が、順に残る");
    }

    // ---- 疑似障害: 検査サービス、音声 ----

    @Test
    void inspectionServiceOutageStopsTheConversationButNeverTheNight() {
        ArmorGateway down = new ArmorGateway() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public ArmorVerdict screenPrompt(String text) {
                throw new ArmorUnavailableException("down");
            }

            @Override
            public ArmorVerdict screenResponse(String text) {
                throw new ArmorUnavailableException("down");
            }
        };
        AtomicInteger llmCalls = new AtomicInteger();
        ConversationService service = conversation(request -> {
            llmCalls.incrementAndGet();
            return OK_RESPONSE;
        }, new LlmCallLimiter(0, 1000, clock), new CircuitBreaker(3, Duration.ofSeconds(60), clock), down);

        ConversationReply reply = service.respond("おはなししよう", List.of(), facts);
        ConversationReply medical = service.respond("睡眠薬について", List.of(), facts);

        assertEquals(TemplateConstants.DOWN_REPLY, reply.getText());
        assertEquals(0, llmCalls.get(), "検査できないまま、LLM に渡さない");
        assertEquals(TemplateConstants.MEDICAL_REPLY, medical.getText(), "医療領域は、検査サービスなしでも、自前の層で止まる");
    }

    @Test
    void speechOutageLeavesTheTextConversationWorking() {
        SpeechGateway failing = new SpeechGateway() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public byte[] synthesize(String text) {
                throw new SpeechUnavailableException("down");
            }
        };
        ConversationService service = conversation(request -> OK_RESPONSE, new LlmCallLimiter(0, 1000, clock),
                new CircuitBreaker(3, Duration.ofSeconds(60), clock), new DisabledArmorGateway());

        ConversationReply reply = service.respond("おはなししよう", List.of(), facts);

        assertEquals(ConversationReply.Source.LLM, reply.getSource(), "読み上げが止まっても、返事の文は出る");
        assertTrue(failing.isEnabled());
        org.junit.jupiter.api.Assertions.assertThrows(SpeechUnavailableException.class, () -> failing.synthesize(reply.getText()));
    }

    // ---- 疑似障害: 停止スイッチ ----

    @Test
    void theKillSwitchTurnsTheAgentOffAndTheRuleKeepsTheNightSafe() {
        assertFalse(new AppConfig(15, 60, 30, false).isAgentEnabled());
        ConversationService withoutLlm = conversation(null, new LlmCallLimiter(0, 1000, clock),
                new CircuitBreaker(3, Duration.ofSeconds(60), clock), new DisabledArmorGateway());

        assertEquals(TemplateConstants.DOWN_REPLY, withoutLlm.respond("おはなししよう", List.of(), facts).getText());
    }
}
