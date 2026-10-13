package app.yurune.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.config.AppConfig;
import app.yurune.constant.GentlePolicy;
import app.yurune.domain.entity.Decision;
import app.yurune.domain.entity.NightRecord;
import app.yurune.domain.entity.NightSession;
import app.yurune.domain.value.DecisionPhase;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.DisplayText;
import app.yurune.domain.value.EndReason;
import app.yurune.domain.value.NightMode;
import app.yurune.domain.value.Observation;
import app.yurune.domain.value.ToolCall;
import app.yurune.domain.value.WakeDate;
import app.yurune.repository.DecisionRepository;
import app.yurune.repository.InMemoryDecisionRepository;
import app.yurune.repository.InMemoryNightRepository;
import app.yurune.repository.InMemoryNightSessionRepository;
import app.yurune.repository.NightRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 夜の制御ループ。受け入れ条件 AT-08（すべての呼び出しを記録）と AT-11（無反応での終了）を確かめる。
 * 反応のない時間を、端末の申告でなく、サーバーの時計で数えることも確かめる。
 */
class NightSessionServiceTest {

    private static final int NO_REACTION_MIN = 15;
    private static final int NO_REACTION_SEC = NO_REACTION_MIN * 60;
    private static final int SIXTY_PERCENT_PLUS = 90;
    private static final int FAR_PAST_SEC = 86_000;

    private MutableClock clock;
    private NightRepository nightRepository;
    private DecisionRepository decisionRepository;
    private AppConfig config;

    @BeforeEach
    void setUp() {
        // 日本時間の 22:10（UTC では 13:10）に夜を始める
        clock = new MutableClock("2026-10-06T13:10:00Z", "Asia/Tokyo");
        nightRepository = new InMemoryNightRepository();
        decisionRepository = new InMemoryDecisionRepository();
        config = new AppConfig(NO_REACTION_MIN, 60, 30);
    }

    private NightSessionService createService(NightAgent agent) {
        return new NightSessionService(new InMemoryNightSessionRepository(), nightRepository, decisionRepository,
                agent, new ToolGuard(config, NightFixtures.createOutputGuard()), config, clock);
    }

    private NightSessionService createRuleBasedService() {
        return createService(new RuleBasedNightAgent(config));
    }

    @Test
    void requestsThatArriveAfterTheNightEndedAreToldApartFromOtherFailures() {
        NightSessionService service = createRuleBasedService();

        assertThrows(NoActiveNightException.class, () -> service.observe(0));
        assertThrows(NoActiveNightException.class, () -> service.endNight(EndReason.USER, 0));
        assertThrows(NoActiveNightException.class, () -> service.changeMode(NightMode.SOUND));
    }

    @Test
    void nightStartedInTheEveningWakesTomorrowAndRecordsThePlan() {
        NightSessionService service = createRuleBasedService();

        NightSession session = service.startNight(NightFixtures.createPlan("rain", "none", "30"), NightMode.SOUND);

        WakeDate expected = WakeDate.parse("2026-10-07");
        assertEquals(expected, session.getWakeDate());
        assertTrue(nightRepository.findNight(expected).isPresent());
        List<Decision> decisions = decisionRepository.findDecisions(expected);
        assertEquals(1, decisions.size());
        assertEquals(DecisionPhase.PLAN, decisions.get(0).getPhase());
    }

    @Test
    void nothingHappensBeforeTheNoReactionTime() {
        NightSessionService service = createRuleBasedService();
        service.startNight(NightFixtures.createPlan("rain", "none", "30"), NightMode.SOUND);
        clock.advance(Duration.ofSeconds(NO_REACTION_SEC - 1));

        NightObservationResult result = service.observe(FAR_PAST_SEC);

        assertTrue(result.getAppliedCalls().isEmpty());
        assertFalse(result.isEnded());
    }

    @Test
    void noReactionEndsInTheDecidedOrderAndSavesLastReaction() {
        NightSessionService service = createRuleBasedService();
        service.startNight(NightFixtures.createPlan("rain", "none", "30"), NightMode.SOUND);
        clock.advance(Duration.ofSeconds(NO_REACTION_SEC));

        NightObservationResult result = service.observe(FAR_PAST_SEC);

        List<String> tools = new ArrayList<>();
        result.getAppliedCalls().forEach(call -> tools.add(call.getToolId()));
        assertEquals(List.of("fade_out", "end_session"), tools);
        assertTrue(result.getAppliedCalls().stream().allMatch(AppliedCall::isAccepted));
        assertTrue(result.isEnded());
        assertTrue(service.findActiveSession().isEmpty());

        NightRecord night = nightRepository.findNight(WakeDate.parse("2026-10-07")).orElseThrow();
        assertEquals(EndReason.NO_REACTION, night.findEndReason().orElseThrow());
        assertEquals(NO_REACTION_MIN, night.findEndedAfter().orElseThrow().asInt());
        // 夜を始めてから一度も反応がなかったので、最後の反応は始めた時刻の 22:10
        assertEquals("22:10", night.findLastReactionTime().orElseThrow().asText());

        List<Decision> decisions = decisionRepository.findDecisions(WakeDate.parse("2026-10-07"));
        assertEquals(List.of(DecisionPhase.PLAN, DecisionPhase.ACT, DecisionPhase.END),
                decisions.stream().map(Decision::getPhase).toList());
    }

    @Test
    void reactionTimeIsHeldByTheServerAndOnlyMovesForward() {
        NightSessionService service = createRuleBasedService();
        NightSession started = service.startNight(NightFixtures.createPlan("rain", "none", "30"), NightMode.SOUND);
        Instant startedAt = started.getStartedAt();

        clock.advance(Duration.ofMinutes(10));
        service.observe(5);
        assertEquals(startedAt.plusSeconds(595), service.findActiveSession().orElseThrow().getLastReactionAt());

        clock.advance(Duration.ofMinutes(10));
        NightObservationResult result = service.observe(FAR_PAST_SEC);

        // 古い時刻を申告しても、最後の反応は過去に戻らない。反応のない時間は、サーバーの時計の 605 秒
        assertEquals(startedAt.plusSeconds(595), service.findActiveSession().orElseThrow().getLastReactionAt());
        assertFalse(result.isEnded());
    }

    @Test
    void aClientCannotMakeTheSilenceLongerThanItReallyIs() {
        NightSessionService service = createRuleBasedService();
        service.startNight(NightFixtures.createPlan("rain", "none", "30"), NightMode.SOUND);
        clock.advance(Duration.ofSeconds(30));

        // 申告できるのは「何秒前に反応したか」だけで、長く見せかける方法がない
        NightObservationResult result = service.observe(FAR_PAST_SEC);

        assertTrue(result.getAppliedCalls().isEmpty());
        assertFalse(result.isEnded());
    }

    @Test
    void outOfRangeReportsAreRejected() {
        NightSessionService service = createRuleBasedService();
        service.startNight(NightFixtures.createPlan("rain", "none", "30"), NightMode.SOUND);

        assertThrows(IllegalArgumentException.class, () -> service.observe(-1));
        assertThrows(IllegalArgumentException.class, () -> service.observe(86_401));
    }

    @Test
    void longGapInTalkSwitchesToSoundOnly() {
        NightSessionService service = createRuleBasedService();
        service.startNight(NightFixtures.createPlan("rain", "on", "30"), NightMode.TALK);
        clock.advance(Duration.ofSeconds(30));

        NightObservationResult result = service.observe(FAR_PAST_SEC);

        assertTrue(result.hasAcceptedCall("switch_mode"));
        assertEquals(NightMode.SOUND, result.getMode());
        assertEquals(NightMode.SOUND, service.findActiveSession().orElseThrow().getMode());
        assertFalse(result.isEnded());
        clock.advance(Duration.ofSeconds(30));
        assertTrue(service.observe(FAR_PAST_SEC).getAppliedCalls().isEmpty());
    }

    @Test
    void userCanEndTheNight() {
        NightSessionService service = createRuleBasedService();
        service.startNight(NightFixtures.createPlan("rain", "none", "30"), NightMode.SOUND);

        service.endNight(EndReason.USER, 0);

        assertTrue(service.findActiveSession().isEmpty());
        NightRecord night = nightRepository.findNight(WakeDate.parse("2026-10-07")).orElseThrow();
        assertEquals(EndReason.USER, night.findEndReason().orElseThrow());
    }

    @Test
    void deviceTimerCanEndTheNightOnlyAfterTheNoReactionTime() {
        NightSessionService service = createRuleBasedService();
        service.startNight(NightFixtures.createPlan("rain", "none", "30"), NightMode.SOUND);
        clock.advance(Duration.ofSeconds(NO_REACTION_SEC));

        assertThrows(IllegalArgumentException.class, () -> service.endNight(EndReason.NO_REACTION, NO_REACTION_SEC - 1));
        assertTrue(service.findActiveSession().isPresent());

        service.endNight(EndReason.NO_REACTION, NO_REACTION_SEC);

        assertTrue(service.findActiveSession().isEmpty());
        NightRecord night = nightRepository.findNight(WakeDate.parse("2026-10-07")).orElseThrow();
        assertEquals(EndReason.NO_REACTION, night.findEndReason().orElseThrow());
        assertEquals("22:10", night.findLastReactionTime().orElseThrow().asText());
    }

    @Test
    void rejectedCallsAreRecordedAndDoNotChangeTheNight() {
        NightAgent misbehavingAgent = (Observation observation, NightSession session) -> List.of(
                new ToolCall("format_disk", Map.of(), new DisplayText("許可リスト外")),
                new ToolCall("set_volume", Map.of("levelPct", String.valueOf(SIXTY_PERCENT_PLUS), "rampSec", "5"),
                        new DisplayText("上限を超える音量")),
                new ToolCall("say", Map.of("text", "こんにちは"), new DisplayText("環境音のときは話せない")),
                new ToolCall("switch_mode", Map.of("mode", "talk"), new DisplayText("会話へは、利用者だけ")));
        NightSessionService service = createService(misbehavingAgent);
        NightSession started = service.startNight(NightFixtures.createPlan("rain", "none", "30"), NightMode.SOUND);

        NightObservationResult result = service.observe(0);

        assertEquals(4, result.getAppliedCalls().size());
        assertTrue(result.getAppliedCalls().stream().noneMatch(AppliedCall::isAccepted));
        NightSession after = service.findActiveSession().orElseThrow();
        assertEquals(started.getVolume().asInt(), after.getVolume().asInt());
        assertEquals(NightMode.SOUND, after.getMode());

        List<Decision> decisions = decisionRepository.findDecisions(WakeDate.parse("2026-10-07"));
        assertEquals(5, decisions.size());
        assertEquals(4, decisions.stream().filter(decision -> !decision.isAccepted()).count());
    }

    @Test
    void acceptedVolumeChangeIsAppliedWithinTheStepLimit() {
        NightAgent raisingAgent = (Observation observation, NightSession session) -> List.of(
                new ToolCall("set_volume", Map.of("levelPct", "50", "rampSec", "10"), new DisplayText("少し上げる")));
        NightSessionService service = createService(raisingAgent);
        service.startNight(NightFixtures.createPlan("rain", "none", "30"), NightMode.SOUND);

        service.observe(0);

        assertEquals(50, service.findActiveSession().orElseThrow().getVolume().asInt());
    }

    @Test
    void decisionsNeverContainSpokenText() {
        NightAgent talkingAgent = (Observation observation, NightSession session) -> List.of(
                new ToolCall("say", Map.of("text", "ひみつの本文"), new DisplayText("返事をする")));
        NightSessionService service = createService(talkingAgent);
        service.startNight(NightFixtures.createPlan("rain", "on", "30"), NightMode.TALK);

        service.observe(0);

        for (Decision decision : decisionRepository.findDecisions(WakeDate.parse("2026-10-07"))) {
            assertFalse(decision.getChosen().asText().contains("ひみつの本文"));
            assertFalse(decision.getReason().asText().contains("ひみつの本文"));
            assertFalse(decision.getObserved().values().stream().anyMatch(value -> value.contains("ひみつの本文")));
        }
    }

    @Test
    void startingTheNightTakesTheSoundFromThePlan() {
        NightSessionService service = createRuleBasedService();

        service.startNight(NightFixtures.createPlan("wave", "none", "30"), NightMode.SOUND);

        assertEquals("wave", service.findSoundState().orElseThrow().getSoundKey());
    }

    @Test
    void trackSwitchAndFadeOutAreAppliedAndReportedToTheDevice() {
        List<ToolCall> pending = new ArrayList<>(List.of(
                new ToolCall("switch_track", Map.of("soundKey", "forest"), new DisplayText("森の音に切り替える")),
                new ToolCall("fade_out", Map.of("minutes", "15"), new DisplayText("ゆっくり下げる"))));
        NightSessionService service = createService((Observation observation, NightSession session) -> takeAll(pending));
        service.startNight(NightFixtures.createPlan("rain", "none", "30"), NightMode.SOUND);

        NightObservationResult result = service.observe(0);

        NightSoundState sound = result.getSoundState();
        assertEquals("forest", sound.getSoundKey());
        assertEquals(0, sound.getVolumePct());
        assertEquals(15 * 60, sound.getFadeRemainingSec());
        assertFalse(result.isEnded());
    }

    @Test
    void fadeCountsDownWithTheClockAndStopsAtZero() {
        List<ToolCall> pending = new ArrayList<>(List.of(
                new ToolCall("fade_out", Map.of("minutes", "15"), new DisplayText("ゆっくり下げる"))));
        NightSessionService service = createService((Observation observation, NightSession session) -> takeAll(pending));
        service.startNight(NightFixtures.createPlan("rain", "none", "30"), NightMode.SOUND);
        service.observe(0);

        clock.advance(Duration.ofMinutes(5));
        assertEquals(10 * 60, service.findSoundState().orElseThrow().getFadeRemainingSec());

        clock.advance(Duration.ofMinutes(10));
        assertEquals(0, service.findSoundState().orElseThrow().getFadeRemainingSec());
    }

    @Test
    void settingTheVolumeAgainEndsTheFade() {
        List<ToolCall> pending = new ArrayList<>(List.of(
                new ToolCall("fade_out", Map.of("minutes", "15"), new DisplayText("ゆっくり下げる"))));
        NightSessionService service = createService((Observation observation, NightSession session) -> takeAll(pending));
        service.startNight(NightFixtures.createPlan("rain", "none", "30"), NightMode.SOUND);
        service.observe(0);

        pending.add(new ToolCall("set_volume", Map.of("levelPct", "5", "rampSec", "10"), new DisplayText("少しだけ戻す")));
        NightObservationResult result = service.observe(0);

        assertEquals(5, result.getSoundState().getVolumePct());
        assertEquals(0, result.getSoundState().getFadeRemainingSec());
    }

    @Test
    void aNewNightRecordsThePolicyVersion() {
        createRuleBasedService().startNight(NightFixtures.createPlan("rain", "none", "30"), NightMode.SOUND);

        NightRecord night = nightRepository.findNight(WakeDate.parse("2026-10-07")).orElseThrow();
        assertEquals(GentlePolicy.POLICY_VERSION, night.getPolicyVersion());
    }

    @Test
    void startingAgainOnTheSameWakeDateKeepsTheFirstNightAndCountsTheRestart() {
        NightSessionService service = createRuleBasedService();
        service.startNight(NightFixtures.createPlan("rain", "none", "30"), NightMode.SOUND);
        clock.advance(Duration.ofMinutes(5));
        service.observe(0);
        clock.advance(Duration.ofMinutes(NO_REACTION_MIN));
        service.observe(FAR_PAST_SEC);
        WakeDate wakeDate = WakeDate.parse("2026-10-07");
        assertEquals(5, nightRepository.findNight(wakeDate).orElseThrow().findOnsetProxyMin().orElseThrow().asInt());

        NightSession restarted = service.startNight(NightFixtures.createPlan("wave", "none", "30"), NightMode.SOUND);

        NightRecord after = nightRepository.findNight(wakeDate).orElseThrow();
        assertEquals(1, after.getRestartCount());
        assertEquals("rain", after.getPlan().getOption(Dimension.SOUND).asText());
        assertEquals(EndReason.NO_REACTION, after.findEndReason().orElseThrow());
        assertEquals(NO_REACTION_MIN + 5, after.findEndedAfter().orElseThrow().asInt());
        assertEquals(5, after.findOnsetProxyMin().orElseThrow().asInt());
        assertEquals("rain", restarted.getSoundKey());
        long planDecisions = decisionRepository.findDecisions(wakeDate).stream()
                .filter(decision -> decision.getPhase() == DecisionPhase.PLAN)
                .count();
        assertEquals(1, planDecisions);
    }

    @Test
    void aNightEndedByTheUserHasNoOnsetProxy() {
        NightSessionService service = createRuleBasedService();
        service.startNight(NightFixtures.createPlan("rain", "none", "30"), NightMode.SOUND);
        clock.advance(Duration.ofMinutes(5));
        service.observe(0);

        service.endNight(EndReason.USER, 0);

        NightRecord night = nightRepository.findNight(WakeDate.parse("2026-10-07")).orElseThrow();
        assertEquals(EndReason.USER, night.findEndReason().orElseThrow());
        assertTrue(night.findOnsetProxyMin().isEmpty());
    }

    @Test
    void aNightStartedInTalkIsMarkedAsUserChanged() {
        createRuleBasedService().startNight(NightFixtures.createPlan("rain", "on", "30"), NightMode.TALK);

        assertTrue(nightRepository.findNight(WakeDate.parse("2026-10-07")).orElseThrow().isUserChanged());
    }

    @Test
    void switchingToTalkMarksTheNightButSwitchingOnlyToSoundDoesNot() {
        NightSessionService service = createRuleBasedService();
        service.startNight(NightFixtures.createPlan("rain", "on", "30"), NightMode.SOUND);
        WakeDate wakeDate = WakeDate.parse("2026-10-07");
        service.changeMode(NightMode.SOUND);
        assertFalse(nightRepository.findNight(wakeDate).orElseThrow().isUserChanged());

        service.changeMode(NightMode.TALK);
        assertTrue(nightRepository.findNight(wakeDate).orElseThrow().isUserChanged());
    }

    /** 呼び出しを 1 回ぶん渡し、次の観測のために空にする（テストのエージェント）。 */
    private static List<ToolCall> takeAll(List<ToolCall> pending) {
        List<ToolCall> calls = List.copyOf(pending);
        pending.clear();
        return calls;
    }
}
