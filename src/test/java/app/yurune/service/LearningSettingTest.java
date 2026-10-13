package app.yurune.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.constant.PlanConstants;
import app.yurune.domain.entity.Arm;
import app.yurune.domain.entity.Decision;
import app.yurune.domain.entity.NightPlan;
import app.yurune.domain.entity.NightRecord;
import app.yurune.domain.value.ArmKey;
import app.yurune.domain.value.DecisionPhase;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.MorningAnswer;
import app.yurune.domain.value.NightRole;
import app.yurune.domain.value.OptionName;
import app.yurune.domain.value.UserSettings;
import app.yurune.domain.value.WakeDate;
import app.yurune.repository.ArmRepository;
import app.yurune.repository.DecisionRepository;
import app.yurune.repository.InMemoryArmRepository;
import app.yurune.repository.InMemoryDecisionRepository;
import app.yurune.repository.InMemoryNightRepository;
import app.yurune.repository.InMemoryPdcaStateRepository;
import app.yurune.repository.InMemorySleepRecordRepository;
import app.yurune.repository.NightRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 「好みを覚える」が、実際に効くこと。
 * 朝の答えは、集計の勝ちに使わない（勝ちは、夜の指標で決める）。オフの利用者は、集計を更新せず、夜のやり方は標準のままにする。
 * すでに覚えたものは、設定では消えない。
 */
class LearningSettingTest {

    private static final WakeDate THIS_MORNING = WakeDate.parse("2026-10-07");
    private static final Clock PLAN_CLOCK = Clock.fixed(Instant.parse("2026-10-06T00:00:00Z"), ZoneId.of("Asia/Tokyo"));
    private static final int STRONG_EVIDENCE = 30;

    private final AtomicReference<UserSettings> settings = new AtomicReference<>(UserSettings.defaults());
    private MutableClock clock;
    private ArmRepository armRepository;
    private NightRepository nightRepository;
    private DecisionRepository decisionRepository;
    private MorningCheckService morning;

    @BeforeEach
    void setUp() {
        clock = new MutableClock("2026-10-06T21:40:00Z", "Asia/Tokyo");
        armRepository = new InMemoryArmRepository();
        nightRepository = new InMemoryNightRepository();
        decisionRepository = new InMemoryDecisionRepository();
        morning = new MorningCheckService(nightRepository, decisionRepository,
                new LearningService(armRepository, settings::get),
                new RecordService(new InMemorySleepRecordRepository(), clock), clock);
    }

    private void answerThisMorning(MorningAnswer answer) {
        nightRepository.saveNight(NightRecord.started(THIS_MORNING, NightFixtures.createPlan("rain", "none", "30")));
        morning.completeMorning(MorningInput.saved(null, null, answer, null));
    }

    private ArmKey sound(String option) {
        return new ArmKey(Dimension.SOUND, new OptionName(option));
    }

    private void teach(String option, int wins, int losses) {
        Arm arm = Arm.untried(sound(option));
        for (int count = 0; count < wins; count++) {
            arm = arm.withResult(MorningAnswer.FIT);
        }
        for (int count = 0; count < losses; count++) {
            arm = arm.withResult(MorningAnswer.NOT_FIT);
        }
        armRepository.saveArm(arm);
    }

    private PlanService createPlanService(NightRepository nights) {
        return new PlanService(nights, new ThompsonOptionSelector(armRepository, new BetaSampler()),
                new InMemoryPdcaStateRepository(), PLAN_CLOCK, settings::get);
    }

    private NightRepository nightsWithPreviousPlan(String sound, String fade) {
        InMemoryNightRepository nights = new InMemoryNightRepository();
        nights.saveNight(NightRecord.started(WakeDate.parse("2026-10-05"), NightFixtures.createPlan(sound, "none", fade)));
        return nights;
    }

    // ---- 朝の答え ----

    @Test
    void withLearningOnTheAnswerIsKeptInTheNightRecordButDoesNotMoveTheWins() {
        teach("rain", 2, 1);

        answerThisMorning(MorningAnswer.FIT);

        assertEquals(3, armRepository.findArm(sound("rain")).orElseThrow().getTrialCount().asInt(), "朝の答えでは、試した回数も増えない");
        assertEquals(2, armRepository.findArm(sound("rain")).orElseThrow().getWinCount().asInt(), "朝の答えでは、勝ちも増えない");
        NightRecord night = nightRepository.findNight(THIS_MORNING).orElseThrow();
        assertEquals(MorningAnswer.FIT, night.findAnswer().orElseThrow(), "答えは、晩の記録に残る");
    }

    @Test
    void withLearningOnTheDecisionLogSaysTheAnswerIsForTheNextReview() {
        answerThisMorning(MorningAnswer.NOT_FIT);

        Decision decision = decisionRepository.findDecisions(THIS_MORNING).get(0);
        assertEquals(DecisionPhase.UPDATE, decision.getPhase());
        assertTrue(decision.getReason().asText().contains("見直し"), decision.getReason().asText());
    }

    @Test
    void withLearningOffTheAnswerIsKeptInTheNightRecordButNeverReachesTheTallies() {
        settings.set(UserSettings.defaults().withLearningEnabled(false));

        answerThisMorning(MorningAnswer.FIT);

        assertTrue(armRepository.findArm(sound("rain")).isEmpty(), "集計を作らない");
        assertTrue(armRepository.findArm(new ArmKey(Dimension.TALK, new OptionName("none"))).isEmpty());
        assertTrue(armRepository.findArm(new ArmKey(Dimension.FADE, new OptionName("30"))).isEmpty());
        NightRecord night = nightRepository.findNight(THIS_MORNING).orElseThrow();
        assertTrue(night.isMorningChecked());
        assertEquals(MorningAnswer.FIT, night.findAnswer().orElseThrow(), "晩の記録としては、残る");
    }

    @Test
    void withLearningOffTheDecisionLogSaysTheUpdateWasSkippedAndWhy() {
        settings.set(UserSettings.defaults().withLearningEnabled(false));

        answerThisMorning(MorningAnswer.FIT);

        List<Decision> decisions = decisionRepository.findDecisions(THIS_MORNING);
        assertEquals(1, decisions.size());
        Decision decision = decisions.get(0);
        assertEquals(DecisionPhase.UPDATE, decision.getPhase());
        assertTrue(decision.getChosen().asText().contains("更新しなかった"), decision.getChosen().asText());
        assertTrue(decision.getReason().asText().contains("好みを覚えない"), decision.getReason().asText());
        assertFalse(decision.getChosen().asText().contains("朝の答え"), "答えを集計に使ったように見せない");
        assertTrue(decision.getObserved().isEmpty());
    }

    @Test
    void whatWasLearnedIsKeptWhenLearningIsTurnedOffAndTurningItOnAgainDoesNotErase() {
        teach("rain", 3, 1);
        settings.set(UserSettings.defaults().withLearningEnabled(false));
        answerThisMorning(MorningAnswer.FIT);
        assertEquals(4, armRepository.findArm(sound("rain")).orElseThrow().getTrialCount().asInt(), "消えず、増えもしない");
        assertEquals(3, armRepository.findArm(sound("rain")).orElseThrow().getWinCount().asInt());

        settings.set(UserSettings.defaults());
        nightRepository.saveNight(NightRecord.started(WakeDate.parse("2026-10-08"),
                NightFixtures.createPlan("rain", "none", "30")));
        clock.advance(Duration.ofDays(1));
        morning.completeMorning(MorningInput.saved(null, null, MorningAnswer.FIT, null));

        assertEquals(4, armRepository.findArm(sound("rain")).orElseThrow().getTrialCount().asInt(),
                "設定を戻しても、朝の答えでは、集計は増えない");
    }

    @Test
    void theLearningServiceItselfRefusesToCountWhenOff() {
        settings.set(UserSettings.defaults().withLearningEnabled(false));
        LearningService service = new LearningService(armRepository, settings::get);

        service.recordAnsweredNight(List.of(sound("rain")), MorningAnswer.FIT);

        assertFalse(service.isEnabled());
        assertTrue(armRepository.findArm(sound("rain")).isEmpty());
    }

    @Test
    void withoutASettingsSourceTheLearningServiceAlwaysCountsForTheSampleReplay() {
        LearningService service = new LearningService(armRepository);

        service.recordAnsweredNight(List.of(sound("rain")), MorningAnswer.FIT);

        assertTrue(service.isEnabled());
        assertEquals(1, armRepository.findArm(sound("rain")).orElseThrow().getWinCount().asInt());
    }

    // ---- 夜のやり方 ----

    @Test
    void withLearningOnTheTonightPlanIsNotChosenByTheTalliesButByTheRoleRules() {
        teach("rain", STRONG_EVIDENCE, 0);
        teach("forest", 0, STRONG_EVIDENCE);
        teach("wave", 0, STRONG_EVIDENCE);

        NightPlan plan = createPlanService(nightsWithPreviousPlan("forest", "45")).proposeTonightPlan();

        assertEquals(NightRole.BASELINE, plan.findRole().orElseThrow(), "最初の夜は、集計によらず、基準（標準のやり方）");
        assertEquals(PlanConstants.DEFAULT_SOUND, plan.getOption(Dimension.SOUND).asText());
        assertEquals(0, plan.countChangedDimensions());
    }

    @Test
    void withLearningOffTheTonightPlanIsTheStandardPlanWithoutARole() {
        teach("rain", STRONG_EVIDENCE, 0);
        settings.set(UserSettings.defaults().withLearningEnabled(false));

        NightPlan plan = createPlanService(nightsWithPreviousPlan("forest", "45")).proposeTonightPlan();

        assertEquals(PlanConstants.DEFAULT_SOUND, plan.getOption(Dimension.SOUND).asText(), "前の晩の音ではなく、標準の音");
        assertEquals(PlanConstants.DEFAULT_FADE, plan.getOption(Dimension.FADE).asText(), "前の晩の下げ方ではなく、標準の下げ方");
        assertEquals("none", plan.getOption(Dimension.TALK).asText(), "会話は、前の晩の値を引き継ぐ");
        assertEquals("45", plan.getOption(Dimension.DURATION).asText(), "長さは、前の晩の値を引き継ぐ");
        assertEquals(0, plan.countChangedDimensions(), "変える項目はない");
        assertTrue(plan.findRole().isEmpty(), "役割は持たない");
        assertTrue(plan.getReason().asText().contains("好みを覚えない設定"), plan.getReason().asText());
    }

    @Test
    void withLearningOffTheFirstNightUsesTheBasicPlan() {
        settings.set(UserSettings.defaults().withLearningEnabled(false));

        NightPlan plan = createPlanService(new InMemoryNightRepository()).proposeTonightPlan();

        assertEquals(PlanConstants.DEFAULT_SOUND, plan.getOption(Dimension.SOUND).asText());
        assertEquals(PlanConstants.DEFAULT_FADE, plan.getOption(Dimension.FADE).asText());
        assertEquals(PlanConstants.DEFAULT_TALK, plan.getOption(Dimension.TALK).asText());
        assertEquals(PlanConstants.DEFAULT_DURATION, plan.getOption(Dimension.DURATION).asText());
        assertEquals(0, plan.countChangedDimensions());
    }

    @Test
    void turningLearningOffDoesNotEraseTheTalliesAndTheRolesResumeWhenItIsTurnedBackOn() {
        teach("rain", STRONG_EVIDENCE, 0);
        PlanService service = createPlanService(nightsWithPreviousPlan("forest", "45"));

        settings.set(UserSettings.defaults().withLearningEnabled(false));
        assertTrue(service.proposeTonightPlan().findRole().isEmpty());
        settings.set(UserSettings.defaults());

        assertEquals(NightRole.BASELINE, service.proposeTonightPlan().findRole().orElseThrow());
        assertEquals(STRONG_EVIDENCE, armRepository.findArm(sound("rain")).orElseThrow().getTrialCount().asInt());
    }
}
