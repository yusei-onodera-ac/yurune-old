package app.yurune.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.config.AppConfig;
import app.yurune.domain.entity.Decision;
import app.yurune.domain.entity.NightSession;
import app.yurune.domain.value.DecisionPhase;
import app.yurune.domain.value.DisplayText;
import app.yurune.domain.value.NightMode;
import app.yurune.domain.value.Observation;
import app.yurune.domain.value.ToolCall;
import app.yurune.domain.value.UserSettings;
import app.yurune.domain.value.VolumePercent;
import app.yurune.domain.value.WakeDate;
import app.yurune.repository.DecisionRepository;
import app.yurune.repository.InMemoryDecisionRepository;
import app.yurune.repository.InMemoryNightRepository;
import app.yurune.repository.InMemoryNightSessionRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 設定が、夜の制御で実際に効くこと（画面で隠すだけでなく、コードが守る）。
 * 夜の会話がオフなら会話にならない。音量は、利用者が選んだ上限を、エージェントの提案でも、超えない。
 */
class NightSettingsTest {

    private static final WakeDate WAKE_DATE = WakeDate.parse("2026-10-07");
    private static final int OPERATOR_CAP = 60;

    private MutableClock clock;
    private DecisionRepository decisionRepository;
    private AppConfig config;
    private final AtomicReference<UserSettings> settings = new AtomicReference<>(UserSettings.defaults());
    private final List<ToolCall> agentCalls = new ArrayList<>();

    @BeforeEach
    void setUp() {
        clock = new MutableClock("2026-10-06T13:10:00Z", "Asia/Tokyo");
        decisionRepository = new InMemoryDecisionRepository();
        config = new AppConfig(15, OPERATOR_CAP, 30);
    }

    private NightSessionService createService() {
        NightAgent agent = (observation, session) -> List.copyOf(agentCalls);
        return new NightSessionService(new InMemoryNightSessionRepository(), new InMemoryNightRepository(),
                decisionRepository, agent, new ToolGuard(config, NightFixtures.createOutputGuard()), config, clock,
                settings::get);
    }

    private NightSession start(NightSessionService service, NightMode mode) {
        return service.startNight(NightFixtures.createPlan("rain", "none", "30"), mode);
    }

    private ToolCall setVolume(int levelPct, int rampSec) {
        return new ToolCall("set_volume", Map.of("levelPct", String.valueOf(levelPct), "rampSec", String.valueOf(rampSec)),
                new DisplayText("テスト"));
    }

    private List<Decision> decisions() {
        return decisionRepository.findDecisions(WAKE_DATE);
    }

    // ---- 音量の上限 ----

    @Test
    void theStartingVolumeNeverExceedsTheUsersCap() {
        settings.set(UserSettings.defaults().withVolumeCapPct(20));

        assertEquals(20, start(createService(), NightMode.SOUND).getVolume().asInt());
    }

    @Test
    void withTheDefaultCapTheNightStartsAtTheUsualVolume() {
        assertEquals(40, start(createService(), NightMode.SOUND).getVolume().asInt());
    }

    @Test
    void aLowerOperatorCapStillWinsOverTheUsersCap() {
        config = new AppConfig(15, 30, 30);

        assertEquals(30, start(createService(), NightMode.SOUND).getVolume().asInt());
        assertEquals(30, createService().findVolumeCapPct());
    }

    @Test
    void theAgentCannotRaiseTheVolumeAboveTheUsersCapEvenWhenTheOperatorCapAllowsIt() {
        settings.set(UserSettings.defaults().withVolumeCapPct(30));
        NightSessionService service = createService();
        start(service, NightMode.SOUND);
        agentCalls.add(setVolume(35, 30));

        NightObservationResult result = service.observe(0);

        assertFalse(result.getAppliedCalls().get(0).isAccepted());
        assertEquals(30, service.findActiveSession().orElseThrow().getVolume().asInt());
        Decision rejected = decisions().get(decisions().size() - 1);
        assertFalse(rejected.isAccepted(), "拒否も、判断の記録に残る");
        assertTrue(rejected.getReason().asText().contains("0〜30"), rejected.getReason().asText());
    }

    @Test
    void theAgentMayStillChangeTheVolumeWithinTheCap() {
        settings.set(UserSettings.defaults().withVolumeCapPct(50));
        NightSessionService service = createService();
        start(service, NightMode.SOUND);
        agentCalls.add(setVolume(50, 30));

        NightObservationResult result = service.observe(0);

        assertTrue(result.getAppliedCalls().get(0).isAccepted());
        assertEquals(50, service.findActiveSession().orElseThrow().getVolume().asInt());
    }

    @Test
    void loweringTheCapDuringTheNightBringsTheVolumeDownAtTheNextObservation() {
        NightSessionService service = createService();
        start(service, NightMode.SOUND);
        settings.set(UserSettings.defaults().withVolumeCapPct(20));

        NightObservationResult result = service.observe(0);

        assertEquals(20, service.findActiveSession().orElseThrow().getVolume().asInt());
        assertTrue(result.hasAcceptedCall("set_volume"));
        Decision update = decisions().get(decisions().size() - 1);
        assertTrue(update.isAccepted());
        assertEquals(DecisionPhase.ACT, update.getPhase());
        assertTrue(update.getChosen().asText().contains("20%"), update.getChosen().asText());
        assertTrue(update.getReason().asText().contains("上限"), update.getReason().asText());
    }

    @Test
    void theVolumeIsNotTouchedWhenItIsWithinTheCap() {
        NightSessionService service = createService();
        start(service, NightMode.SOUND);
        int recorded = decisions().size();

        NightObservationResult result = service.observe(0);

        assertTrue(result.getAppliedCalls().isEmpty());
        assertEquals(recorded, decisions().size());
    }

    @Test
    void theGuardNeverPassesMoreThanTheOperatorCapEvenIfAskedTo() {
        ToolGuard guard = new ToolGuard(new AppConfig(15, 50, 30), NightFixtures.createOutputGuard());
        NightSession session = new NightSession(WAKE_DATE, NightMode.SOUND, new VolumePercent(45),
                Instant.parse("2026-10-06T13:10:00Z"), false);
        Observation observation = new Observation(10, NightMode.SOUND, new VolumePercent(45), 0, 0);

        assertTrue(guard.validate(setVolume(50, 20), session, observation, 100).isAccepted());
        assertFalse(guard.validate(setVolume(55, 20), session, observation, 100).isAccepted(), "運用の上限 50 を超える");
        assertFalse(guard.validate(setVolume(50, 20), session, observation, 40).isAccepted(), "渡された上限 40 を超える");
    }

    // ---- 夜の会話 ----

    @Test
    void whenTalkIsOffANightStartedWithTalkBeginsWithSoundOnly() {
        settings.set(UserSettings.defaults().withNightTalkEnabled(false));
        NightSessionService service = createService();

        NightSession session = start(service, NightMode.TALK);

        assertEquals(NightMode.SOUND, session.getMode());
        assertEquals(NightMode.SOUND, service.findActiveSession().orElseThrow().getMode());
        assertTrue(service.findTalkFacts().isEmpty());
    }

    @Test
    void whenTalkIsOnANightStartedWithTalkTalks() {
        NightSessionService service = createService();

        assertEquals(NightMode.TALK, start(service, NightMode.TALK).getMode());
        assertTrue(service.findTalkFacts().isPresent());
        assertTrue(service.isTalkEnabled());
    }

    @Test
    void whenTalkIsOffSwitchingToTalkIsIgnored() {
        settings.set(UserSettings.defaults().withNightTalkEnabled(false));
        NightSessionService service = createService();
        start(service, NightMode.SOUND);

        service.changeMode(NightMode.TALK);

        assertEquals(NightMode.SOUND, service.findActiveSession().orElseThrow().getMode());
        assertFalse(service.isTalkEnabled());
    }

    @Test
    void turningTalkOffDuringATalkNightStopsTheFactsAtOnce() {
        NightSessionService service = createService();
        start(service, NightMode.TALK);
        assertTrue(service.findTalkFacts().isPresent());

        settings.set(UserSettings.defaults().withNightTalkEnabled(false));

        assertTrue(service.findTalkFacts().isEmpty(), "会話の事実を作らない。LLM に渡すものがなくなる");
    }

    @Test
    void turningTalkOffDuringATalkNightSwitchesToSoundAtTheNextObservationAndRecordsIt() {
        NightSessionService service = createService();
        start(service, NightMode.TALK);
        settings.set(UserSettings.defaults().withNightTalkEnabled(false));

        NightObservationResult result = service.observe(0);

        assertEquals(NightMode.SOUND, result.getMode());
        assertTrue(result.hasAcceptedCall("switch_mode"), "端末が画面を読み直す");
        Decision decision = decisions().get(decisions().size() - 1);
        assertTrue(decision.isAccepted());
        assertTrue(decision.getReason().asText().contains("オフ"), decision.getReason().asText());
    }

    @Test
    void theSettingsCanBeEnforcedWhenTheScreenIsOpened() {
        NightSessionService service = createService();
        assertTrue(service.enforceSettings().isEmpty(), "夜が始まっていないときは、何もない");
        start(service, NightMode.TALK);
        assertTrue(service.enforceSettings().isEmpty(), "合っているときは、何もしない");

        settings.set(UserSettings.defaults().withNightTalkEnabled(false).withVolumeCapPct(20));
        List<AppliedCall> applied = service.enforceSettings();

        assertEquals(List.of("switch_mode", "set_volume"), applied.stream().map(AppliedCall::getToolId).toList());
        assertTrue(applied.stream().allMatch(AppliedCall::isAccepted));
        NightSession session = service.findActiveSession().orElseThrow();
        assertEquals(NightMode.SOUND, session.getMode());
        assertEquals(20, session.getVolume().asInt());
        assertTrue(service.enforceSettings().isEmpty(), "直したあとは、もう何もしない");
    }

    @Test
    void withoutASettingsSourceTheServiceUsesTheDefaults() {
        NightSessionService service = new NightSessionService(new InMemoryNightSessionRepository(), new InMemoryNightRepository(),
                decisionRepository, (observation, session) -> List.of(), new ToolGuard(config, NightFixtures.createOutputGuard()),
                config, clock);

        assertEquals(NightMode.TALK, service.startNight(NightFixtures.createPlan("rain", "none", "30"), NightMode.TALK).getMode());
        assertEquals(40, service.findActiveSession().orElseThrow().getVolume().asInt());
    }
}
