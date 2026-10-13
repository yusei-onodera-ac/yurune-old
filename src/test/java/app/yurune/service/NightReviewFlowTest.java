package app.yurune.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.config.AppConfig;
import app.yurune.constant.GentlePolicy;
import app.yurune.domain.entity.Decision;
import app.yurune.domain.entity.NightPlan;
import app.yurune.domain.value.ArmKey;
import app.yurune.domain.value.DecisionPhase;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.NightMode;
import app.yurune.domain.value.NightGoal;
import app.yurune.domain.value.NightRole;
import app.yurune.domain.value.PdcaState;
import app.yurune.domain.value.UserSettings;
import app.yurune.domain.value.WakeDate;
import app.yurune.repository.InMemoryArmRepository;
import app.yurune.repository.InMemoryDecisionRepository;
import app.yurune.repository.InMemoryNightRepository;
import app.yurune.repository.InMemoryNightSessionRepository;
import app.yurune.repository.InMemoryPdcaStateRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 夜の流れを、実際のサービス（計画、夜の制御、確認と見直し）で何夜か回す。
 * 寝つきの値は、夜が無反応で終わったときに、端末の反応から作られる（本物の流れと同じ道）。
 * これはアルゴリズムの確かめであって、効果の主張ではない。
 */
class NightReviewFlowTest {

    private static final int NO_REACTION_MIN = 15;
    private static final int FAR_PAST_SEC = 86_000;
    private static final int BETTER_ONSET_MIN = 10;
    private static final int STANDARD_ONSET_MIN = 40;
    private static final int WORSE_ONSET_MIN = 70;
    private static final int BASELINE_AND_MORE_NIGHTS = 60;

    private MutableClock clock;
    private InMemoryNightRepository nights;
    private InMemoryArmRepository arms;
    private InMemoryPdcaStateRepository states;
    private InMemoryDecisionRepository decisions;
    private PlanService planService;
    private NightSessionService sessionService;
    private final List<WakeDate> wakeDates = new ArrayList<>();
    private final List<String> violations = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // 日本時間の 22:10 に夜を始める（起きた日は翌日）
        clock = new MutableClock("2026-10-06T13:10:00Z", "Asia/Tokyo");
        nights = new InMemoryNightRepository();
        arms = new InMemoryArmRepository();
        states = new InMemoryPdcaStateRepository();
        decisions = new InMemoryDecisionRepository();
        AppConfig config = new AppConfig(NO_REACTION_MIN, 60, 30);
        planService = new PlanService(nights, new ThompsonOptionSelector(arms, new BetaSampler()), states, clock);
        NightReviewService review = new NightReviewService(nights, arms, states, decisions,
                new AuditLogger(clock, null, line -> { }), clock, UserSettings::defaults,
                () -> NightGoal.DEFAULT);
        sessionService = new NightSessionService(new InMemoryNightSessionRepository(), nights, decisions,
                new RuleBasedNightAgent(config), new ToolGuard(config, NightFixtures.createOutputGuard()), config, clock,
                UserSettings::defaults, review::reviewEndedNight);
    }

    /** 1 晩を、計画から終わりまで回す。変える夜は variantOnsetMin、標準の夜は standardOnsetMin の寝つきになる。 */
    private void runOneNight(int variantOnsetMin, int standardOnsetMin) {
        WakeDate wakeDate = WakeDate.forNightStartingNow(clock);
        PdcaState before = states.findState();
        NightPlan plan = planService.proposeTonightPlan();
        if (plan.findRole().orElse(null) == NightRole.VARIANT) {
            ArmKey key = new ArmKey(plan.findChangedFactor().orElseThrow(),
                    plan.getOption(plan.findChangedFactor().orElseThrow()));
            if (before.isQuarantined(key, wakeDate)) {
                violations.add(key.asDocumentId() + " on " + wakeDate.asIsoText());
            }
        }
        sessionService.startNight(plan, NightMode.SOUND);
        wakeDates.add(wakeDate);

        int onsetMin = plan.findRole().orElse(null) == NightRole.VARIANT ? variantOnsetMin : standardOnsetMin;
        clock.advance(Duration.ofMinutes(onsetMin));
        sessionService.recordUserReaction();
        clock.advance(Duration.ofMinutes(NO_REACTION_MIN));
        sessionService.observe(FAR_PAST_SEC);
        clock.advance(Duration.ofDays(1).minus(Duration.ofMinutes(onsetMin + NO_REACTION_MIN)));
    }

    private List<Decision> decisionsOf(DecisionPhase phase) {
        List<Decision> found = new ArrayList<>();
        for (WakeDate wakeDate : wakeDates) {
            decisions.findDecisions(wakeDate).stream().filter(decision -> decision.getPhase() == phase).forEach(found::add);
        }
        return found;
    }

    private boolean hasReview(String rule) {
        return decisionsOf(DecisionPhase.REVIEW).stream().anyMatch(decision -> rule.equals(decision.getObserved().get("rule")));
    }

    @Test
    void theFirstBaselineNightsAreStandardAndThenVariantNightsAppear() {
        List<NightRole> roles = new ArrayList<>();
        for (int night = 0; night < BASELINE_AND_MORE_NIGHTS; night++) {
            WakeDate wakeDate = WakeDate.forNightStartingNow(clock);
            roles.add(planService.proposeTonightPlan().findRole().orElse(null));
            runOneNight(BETTER_ONSET_MIN, STANDARD_ONSET_MIN);
            assertEquals(wakeDate, wakeDates.get(wakeDates.size() - 1));
        }

        assertEquals(NightRole.BASELINE, roles.get(0));
        assertTrue(roles.subList(0, GentlePolicy.BASELINE_NIGHTS).stream().allMatch(role -> role == NightRole.BASELINE));
        assertTrue(roles.stream().anyMatch(role -> role == NightRole.VARIANT), "基準のあとは、変える夜も出る");
        assertTrue(roles.stream().anyMatch(role -> role == NightRole.CONTROL), "対照の夜も、同じ周期で混ざる");
    }

    @Test
    void aBetterVariantIsCheckedAndPreferredWithoutAnyMorningAnswer() {
        for (int night = 0; night < BASELINE_AND_MORE_NIGHTS; night++) {
            runOneNight(BETTER_ONSET_MIN, STANDARD_ONSET_MIN);
        }

        assertTrue(decisionsOf(DecisionPhase.CHECK).stream().anyMatch(decision -> "better".equals(decision.getObserved().get("verdict"))));
        assertTrue(hasReview("R1"));
        assertFalse(states.findState().getPreferred().isEmpty(), "よく選ぶ選択肢が、状態に残る");
        assertTrue(arms.findArmsByDimension(Dimension.SOUND).stream().anyMatch(arm -> arm.getWinCount().asInt() > 0),
                "勝ちは、夜の指標で増える");
    }

    @Test
    void aWorseVariantIsRolledBackOrQuarantinedAndNotChosenWhileQuarantined() {
        for (int night = 0; night < BASELINE_AND_MORE_NIGHTS; night++) {
            runOneNight(WORSE_ONSET_MIN, STANDARD_ONSET_MIN);
        }

        assertFalse(states.findState().getQuarantine().isEmpty(), "わるい選択肢は、外される");
        assertTrue(hasReview("R4") || hasReview("R3"), "戻すか、外す判断が残る");
        assertTrue(violations.isEmpty(), "外している間は、その選択肢を選ばない: " + violations);
    }

    @Test
    void theCheckOfEachNightIsRecordedWithItsRule() {
        for (int night = 0; night < BASELINE_AND_MORE_NIGHTS; night++) {
            runOneNight(BETTER_ONSET_MIN, STANDARD_ONSET_MIN);
        }

        List<Decision> checks = decisionsOf(DecisionPhase.CHECK);
        assertFalse(checks.isEmpty());
        assertTrue(checks.stream().allMatch(decision -> decision.getObserved().containsKey("rule")));
        assertTrue(checks.size() <= BASELINE_AND_MORE_NIGHTS, "確認は、夜ごとに一度");
    }
}
