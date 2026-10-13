package app.yurune.service;

import static app.yurune.service.ReviewFixtures.day;
import static app.yurune.service.ReviewFixtures.soundVariant;
import static app.yurune.service.ReviewFixtures.soundVariantsWithControls;
import static app.yurune.service.ReviewFixtures.standard;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.constant.GentlePolicy;
import app.yurune.domain.entity.Arm;
import app.yurune.domain.entity.Decision;
import app.yurune.domain.entity.NightRecord;
import app.yurune.domain.value.ArmKey;
import app.yurune.domain.value.DecisionPhase;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.MorningAnswer;
import app.yurune.domain.value.NightGoal;
import app.yurune.domain.value.NightRole;
import app.yurune.domain.value.OptionName;
import app.yurune.domain.value.PdcaState;
import app.yurune.domain.value.UserSettings;
import app.yurune.repository.InMemoryArmRepository;
import app.yurune.repository.InMemoryDecisionRepository;
import app.yurune.repository.InMemoryNightRepository;
import app.yurune.repository.InMemoryPdcaStateRepository;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 確認（規則 C）と見直し（規則 R）。M2r で、本検定は週 1 回（R1）、
 * 比べ方は時間が近い対照とのペアの差（R2）、要因は順番に試す（R3）、受診のすすめは「よく使う」が
 * 8 週間見つからないこと（R6）に変わった。夜の記録は、テストごとに、使う分だけ用意する（ReviewFixtures）。
 */
class NightReviewServiceTest {

    private static final String WAVE = "wave";
    private static final int STANDARD_ONSET = 40;
    private static final int BETTER_ONSET = 10;
    private static final int WORSE_ONSET = 70;

    private InMemoryNightRepository nights;
    private InMemoryArmRepository arms;
    private InMemoryPdcaStateRepository states;
    private InMemoryDecisionRepository decisions;
    private List<String> auditLines;
    private boolean learningOn;
    private NightGoal currentGoal;

    @BeforeEach
    void setUp() {
        nights = new InMemoryNightRepository();
        arms = new InMemoryArmRepository();
        states = new InMemoryPdcaStateRepository();
        decisions = new InMemoryDecisionRepository();
        auditLines = new ArrayList<>();
        learningOn = true;
        currentGoal = NightGoal.ONSET;
    }

    private NightReviewService service() {
        return new NightReviewService(nights, arms, states, decisions,
                new AuditLogger(new MutableClock("2026-10-20T12:00:00Z", "Asia/Tokyo"), null, auditLines::add),
                new MutableClock("2026-10-20T12:00:00Z", "Asia/Tokyo"),
                () -> learningOn ? UserSettings.defaults() : UserSettings.defaults().withLearningEnabled(false),
                () -> currentGoal);
    }

    private void save(NightRecord... records) {
        for (NightRecord record : records) {
            nights.saveNight(record);
        }
    }

    private void save(List<NightRecord> records) {
        records.forEach(nights::saveNight);
    }

    private void baseline(int count) {
        for (int offset = 0; offset < count; offset++) {
            save(standard(day(offset), NightRole.BASELINE, STANDARD_ONSET, null));
        }
    }

    private List<Decision> decisionsOf(DecisionPhase phase) {
        List<Decision> found = new ArrayList<>();
        for (int offset = 0; offset < 200; offset++) {
            for (Decision decision : decisions.findDecisions(day(offset))) {
                if (decision.getPhase() == phase) {
                    found.add(decision);
                }
            }
        }
        return found;
    }

    private List<Decision> decisionsOfRule(DecisionPhase phase, String rule) {
        return decisionsOf(phase).stream().filter(decision -> rule.equals(decision.getObserved().get("rule"))).toList();
    }

    private ArmKey soundKey(String option) {
        return new ArmKey(Dimension.SOUND, new OptionName(option));
    }

    private PdcaState state() {
        return states.findState();
    }

    // ---- 毎晩（規則 C3: 配分の集計の更新） ----

    @Test
    void aValidVariantNightCountsOneTrialAgainstTheNearbyStandard() {
        baseline(7);
        save(soundVariant(day(7), WAVE, BETTER_ONSET, null));

        service().reviewEndedNight(day(7));

        assertEquals(1, arms.findArm(soundKey(WAVE)).orElseThrow().getTrialCount().asInt());
        assertEquals(1, arms.findArm(soundKey(WAVE)).orElseThrow().getWinCount().asInt(), "標準より短かったので勝ち");
        assertEquals(1, decisionsOfRule(DecisionPhase.CHECK, "C3").size(), "毎晩の集計は、C3 として記録する");
        assertTrue(decisionsOfRule(DecisionPhase.CHECK, "C2").isEmpty(), "見直しの日でなければ、本検定（C2）はしない");
    }

    @Test
    void callingTheSameNightTwiceCountsOnlyOnce() {
        baseline(7);
        save(soundVariant(day(7), WAVE, BETTER_ONSET, null));
        NightReviewService service = service();

        service.reviewEndedNight(day(7));
        service.reviewEndedNight(day(7));

        assertEquals(1, arms.findArm(soundKey(WAVE)).orElseThrow().getTrialCount().asInt(), "二度呼ばれても、一度しか数えない");
    }

    @Test
    void aNightEndedByTheUserHasNoOnsetAndIsNotCounted() {
        baseline(7);
        save(soundVariant(day(7), WAVE, null, null));

        service().reviewEndedNight(day(7));

        assertTrue(arms.findArm(soundKey(WAVE)).isEmpty(), "寝つきの値がない夜は、比べない");
        assertTrue(decisionsOf(DecisionPhase.CHECK).isEmpty());
    }

    @Test
    void learningOffDoesNothingAtAllNotEvenTheNightlyCount() {
        baseline(7);
        save(soundVariant(day(7), WAVE, BETTER_ONSET, null));
        learningOn = false;

        service().reviewEndedNight(day(7));

        assertTrue(decisionsOf(DecisionPhase.CHECK).isEmpty());
        assertTrue(decisionsOf(DecisionPhase.REVIEW).isEmpty());
        assertTrue(arms.findArm(soundKey(WAVE)).isEmpty());
    }

    // ---- 毎晩（規則 R4: 戻す） ----

    @Test
    void threeWorseVariantNightsRollBackImmediatelyEvenOnANonReviewDay() {
        baseline(7);
        for (int offset = 7; offset <= 9; offset++) {
            save(soundVariant(day(offset), WAVE, WORSE_ONSET, null));
        }

        service().reviewEndedNight(day(9));

        assertTrue(state().isQuarantined(soundKey(WAVE), day(9)));
        assertTrue(state().isHolding(day(10)));
        assertTrue(state().isHolding(day(11)));
        assertFalse(state().isHolding(day(12)));
        assertEquals(1, decisionsOfRule(DecisionPhase.REVIEW, "R4").size());
    }

    @Test
    void twoWorseNightsAndOneOkNightDoNotRollBack() {
        baseline(7);
        save(soundVariant(day(7), WAVE, WORSE_ONSET, null), soundVariant(day(8), WAVE, WORSE_ONSET, null),
                soundVariant(day(9), WAVE, STANDARD_ONSET, null));

        service().reviewEndedNight(day(9));

        assertFalse(state().isQuarantined(soundKey(WAVE), day(9)), "直近 3 夜が、すべてわるいわけではない");
        assertTrue(decisionsOf(DecisionPhase.REVIEW).isEmpty());
    }

    // ---- 週に 1 回（規則 C1、C2、R1、R2、R3） ----

    @Test
    void onANonReviewDayTheWeeklyVerdictDoesNotRun() {
        baseline(7);
        for (int offset = 7; offset <= 10; offset++) {
            save(soundVariant(day(offset), WAVE, BETTER_ONSET, null));
        }

        service().reviewEndedNight(day(10));

        assertTrue(decisionsOfRule(DecisionPhase.CHECK, "C1").isEmpty());
        assertTrue(decisionsOfRule(DecisionPhase.CHECK, "C2").isEmpty());
        assertTrue(decisionsOf(DecisionPhase.REVIEW).isEmpty());
    }

    @Test
    void tooFewPairedNightsOnAReviewDayIsNeedMore() {
        baseline(7);
        save(soundVariant(day(7), WAVE, BETTER_ONSET, null));

        service().reviewEndedNight(day(13));

        assertEquals(2, decisionsOfRule(DecisionPhase.CHECK, "C1").size(), "いま試している要因（音）の、両方の選択肢（波、森）を見直す");
        assertTrue(decisionsOf(DecisionPhase.REVIEW).isEmpty(), "様子見のあいだは、見直しの決まりは動かない");
    }

    @Test
    void aSimilarVariantIsParkedOnTheReviewDay() {
        baseline(7);
        int[] onsets = {38, 42, 40, 36, 44, 40};
        save(variantNightsWithOwnOnsets(onsets, STANDARD_ONSET));
        save(standard(day(16), NightRole.CONTROL, STANDARD_ONSET, null), standard(day(17), NightRole.CONTROL, STANDARD_ONSET, null),
                standard(day(18), NightRole.CONTROL, STANDARD_ONSET, null), standard(day(19), NightRole.CONTROL, STANDARD_ONSET, null),
                standard(day(20), NightRole.CONTROL, STANDARD_ONSET, null));

        service().reviewEndedNight(day(20));

        assertTrue(state().isParked(soundKey(WAVE)));
        assertFalse(state().isQuarantined(soundKey(WAVE), day(20)));
        assertEquals(1, decisionsOfRule(DecisionPhase.REVIEW, "R2").size());
    }

    /** 変える夜 7,8,10,11,13,14 に、それぞれ違う寝つきの値を割り振り、対照夜を 9,12,15 に挟む。 */
    private List<NightRecord> variantNightsWithOwnOnsets(int[] onsets, int controlOnset) {
        int[] variantOffsets = {7, 8, 10, 11, 13, 14};
        int[] controlOffsets = {9, 12, 15};
        List<NightRecord> run = new ArrayList<>();
        for (int index = 0; index < variantOffsets.length; index++) {
            run.add(soundVariant(day(variantOffsets[index]), WAVE, onsets[index], null));
        }
        for (int offset : controlOffsets) {
            run.add(standard(day(offset), NightRole.CONTROL, controlOnset, null));
        }
        return run;
    }

    @Test
    void aWorseVariantIsDroppedOnTheReviewDayWhenTonightItselfIsAControlNight() {
        baseline(7);
        save(soundVariantsWithControls(7, 6, WAVE, WORSE_ONSET, STANDARD_ONSET));
        save(standard(day(16), NightRole.CONTROL, STANDARD_ONSET, null), standard(day(17), NightRole.CONTROL, STANDARD_ONSET, null),
                standard(day(18), NightRole.CONTROL, STANDARD_ONSET, null), standard(day(19), NightRole.CONTROL, STANDARD_ONSET, null),
                standard(day(20), NightRole.CONTROL, STANDARD_ONSET, null));

        service().reviewEndedNight(day(20));

        assertTrue(state().isQuarantined(soundKey(WAVE), day(20)));
        assertEquals(1, decisionsOfRule(DecisionPhase.REVIEW, "R3").size());
        assertTrue(decisionsOf(DecisionPhase.REVIEW).stream()
                .noneMatch(decision -> "R4".equals(decision.getObserved().get("rule"))),
                "今夜は対照夜なので、戻す（R4）は動かない。外す（R3）だけが働く");
    }

    @Test
    void aVariantThatMostlyDidNotFitIsDroppedEvenWithoutEnoughNightsForAVerdict() {
        baseline(7);
        MorningAnswer[] answers = {MorningAnswer.NOT_FIT, MorningAnswer.NOT_FIT, MorningAnswer.FIT,
            MorningAnswer.NOT_FIT, MorningAnswer.NEUTRAL, MorningAnswer.NOT_FIT};
        int[] variantOffsets = {7, 8, 10, 11, 13, 14};
        for (int index = 0; index < variantOffsets.length; index++) {
            save(soundVariant(day(variantOffsets[index]), WAVE, STANDARD_ONSET, answers[index]));
        }
        save(standard(day(9), NightRole.CONTROL, STANDARD_ONSET, null), standard(day(12), NightRole.CONTROL, STANDARD_ONSET, null),
                standard(day(15), NightRole.CONTROL, STANDARD_ONSET, null), standard(day(16), NightRole.CONTROL, STANDARD_ONSET, null),
                standard(day(20), NightRole.CONTROL, STANDARD_ONSET, null));

        service().reviewEndedNight(day(20));

        assertTrue(state().isQuarantined(soundKey(WAVE), day(20)));
        assertTrue(state().isQuarantined(soundKey(WAVE), day(20).plusDays(GentlePolicy.QUARANTINE_DAYS)));
        assertFalse(state().isQuarantined(soundKey(WAVE), day(20).plusDays(GentlePolicy.QUARANTINE_DAYS + 1)));
    }

    @Test
    void aBetterVariantNeedsTwoConsecutiveWeeklyReviewsBeforeItIsPreferred() {
        baseline(7);
        save(soundVariantsWithControls(7, 6, WAVE, BETTER_ONSET, STANDARD_ONSET));
        save(standard(day(16), NightRole.CONTROL, STANDARD_ONSET, null), standard(day(17), NightRole.CONTROL, STANDARD_ONSET, null),
                standard(day(18), NightRole.CONTROL, STANDARD_ONSET, null), standard(day(19), NightRole.CONTROL, STANDARD_ONSET, null),
                standard(day(20), NightRole.CONTROL, STANDARD_ONSET, null));
        NightReviewService service = service();

        service.reviewEndedNight(day(20));

        assertFalse(state().isPreferred(soundKey(WAVE)), "まだ 1 回目の BETTER だけ");
        assertEquals(1, decisionsOfRule(DecisionPhase.CHECK, "C2").stream()
                .filter(decision -> "better".equals(decision.getObserved().get("verdict"))).count());

        save(soundVariant(day(21), WAVE, BETTER_ONSET, null), soundVariant(day(22), WAVE, BETTER_ONSET, null),
                standard(day(23), NightRole.CONTROL, STANDARD_ONSET, null),
                soundVariant(day(24), WAVE, BETTER_ONSET, null), soundVariant(day(25), WAVE, BETTER_ONSET, null),
                standard(day(26), NightRole.CONTROL, STANDARD_ONSET, null),
                standard(day(27), NightRole.CONTROL, STANDARD_ONSET, null));

        service.reviewEndedNight(day(27));

        assertTrue(state().isPreferred(soundKey(WAVE)), "2 回続けて BETTER だったので、よく使うにする");
        assertEquals(1, decisionsOfRule(DecisionPhase.REVIEW, "R1").size());
    }

    // ---- 受診のすすめ（規則 R6） ----

    @Test
    void stagnationWithNoPreferredOptionForEightWeeksRecordsTheReferralOnce() {
        baseline(7);
        for (int offset = 7; offset <= 63; offset++) {
            save(standard(day(offset), NightRole.CONTROL, STANDARD_ONSET, null));
        }

        service().reviewEndedNight(day(63));

        assertEquals(day(63), state().findReferredOn().orElseThrow());
        assertEquals(1, decisionsOfRule(DecisionPhase.REVIEW, "R6").size());
    }

    @Test
    void noReferralBeforeEightWeeksEvenWithoutAnyPreferredOption() {
        baseline(7);
        for (int offset = 7; offset <= 40; offset++) {
            save(standard(day(offset), NightRole.CONTROL, STANDARD_ONSET, null));
        }

        service().reviewEndedNight(day(40));

        assertTrue(state().findReferredOn().isEmpty());
    }

    @Test
    void noReferralWhenAPreferredOptionAlreadyExists() {
        baseline(7);
        for (int offset = 7; offset <= 63; offset++) {
            save(standard(day(offset), NightRole.CONTROL, STANDARD_ONSET, null));
        }
        states.saveState(PdcaState.empty().withPreferred(soundKey(WAVE)));

        service().reviewEndedNight(day(63));

        assertTrue(state().findReferredOn().isEmpty(), "よく使う選択肢が見つかっているので、停滞ではない");
    }

    @Test
    void theReferralIsNotRepeatedWithinTheCooldown() {
        baseline(7);
        for (int offset = 7; offset <= 63; offset++) {
            save(standard(day(offset), NightRole.CONTROL, STANDARD_ONSET, null));
        }
        NightReviewService service = service();
        service.reviewEndedNight(day(63));

        save(standard(day(64), NightRole.CONTROL, STANDARD_ONSET, null));
        service.reviewEndedNight(day(64));

        assertEquals(1, decisionsOfRule(DecisionPhase.REVIEW, "R6").size(), "クールダウンの間は、出さない");
    }

    @Test
    void referralDoesNotRunWhenLearningIsOff() {
        baseline(7);
        for (int offset = 7; offset <= 63; offset++) {
            save(standard(day(offset), NightRole.CONTROL, STANDARD_ONSET, null));
        }
        learningOn = false;

        service().reviewEndedNight(day(63));

        assertTrue(state().findReferredOn().isEmpty(), "好みを覚えない設定では、受診のすすめも動かさない");
    }

    // ---- めやすの切り替え。学習の状態と配分が、めやすをまたいで混ざらないこと ----

    @Test
    void switchingTheNightGoalResetsThePreferredStateAndTheArmAllocation() {
        baseline(7);
        save(soundVariantsWithControls(7, 6, WAVE, BETTER_ONSET, STANDARD_ONSET));
        save(standard(day(16), NightRole.CONTROL, STANDARD_ONSET, null), standard(day(17), NightRole.CONTROL, STANDARD_ONSET, null),
                standard(day(18), NightRole.CONTROL, STANDARD_ONSET, null), standard(day(19), NightRole.CONTROL, STANDARD_ONSET, null),
                standard(day(20), NightRole.CONTROL, STANDARD_ONSET, null));
        NightReviewService service = service();
        service.reviewEndedNight(day(20));
        save(soundVariant(day(21), WAVE, BETTER_ONSET, null), soundVariant(day(22), WAVE, BETTER_ONSET, null),
                standard(day(23), NightRole.CONTROL, STANDARD_ONSET, null),
                soundVariant(day(24), WAVE, BETTER_ONSET, null), soundVariant(day(25), WAVE, BETTER_ONSET, null),
                standard(day(26), NightRole.CONTROL, STANDARD_ONSET, null),
                standard(day(27), NightRole.CONTROL, STANDARD_ONSET, null));
        service.reviewEndedNight(day(27));
        assertTrue(state().isPreferred(soundKey(WAVE)), "前提: ONSET で、よく使うに達している");
        arms.saveArm(Arm.untried(soundKey(WAVE)).withMetricResult(true));
        assertTrue(arms.findArm(soundKey(WAVE)).orElseThrow().getTrialCount().asInt() > 0, "前提: 配分の集計がある");

        currentGoal = NightGoal.WAKE;
        service.reviewEndedNight(day(28));

        assertFalse(state().isPreferred(soundKey(WAVE)), "めやすを切り替えたら、古いめやすの「よく使う」は引き継がない");
        assertEquals(0, arms.findArm(soundKey(WAVE)).orElseThrow().getTrialCount().asInt(),
                "配分の集計も、新しいめやすで作り直す（未試行に戻す）");
        assertEquals(NightGoal.WAKE, state().findLastGoal().orElseThrow());
    }

    // ---- 監査 ----

    @Test
    void theAuditRecordsOnlyTheRuleIdsAndCountsNotTheText() {
        baseline(7);
        save(soundVariant(day(7), WAVE, BETTER_ONSET, null));

        service().reviewEndedNight(day(7));

        assertTrue(auditLines.stream().anyMatch(line -> line.contains("\"event\":\"pdca\"") && line.contains("\"rule\":\"C3\"")),
                auditLines.toString());
        assertTrue(auditLines.stream().noneMatch(line -> line.contains("寝つき")), "文の本文は、監査に入れない");
    }
}
