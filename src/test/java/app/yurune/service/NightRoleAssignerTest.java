package app.yurune.service;

import static app.yurune.service.ReviewFixtures.day;
import static app.yurune.service.ReviewFixtures.soundVariant;
import static app.yurune.service.ReviewFixtures.standard;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.constant.GentlePolicy;
import app.yurune.domain.entity.NightRecord;
import app.yurune.domain.value.ArmKey;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.MorningAnswer;
import app.yurune.domain.value.NightAssignment;
import app.yurune.domain.value.NightGoal;
import app.yurune.domain.value.NightRole;
import app.yurune.domain.value.OptionName;
import app.yurune.domain.value.PdcaState;
import app.yurune.domain.value.SelectedOption;
import app.yurune.domain.value.WakeDate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 今夜の役割と、変える選択肢の規則。候補の選び方（乱数）は、先頭の候補を返す偽物にして、規則だけを確かめる。
 */
class NightRoleAssignerTest {

    private static final String WAVE = "wave";
    private static final String FOREST = "forest";
    private static final int STANDARD_ONSET = 40;

    /** 呼び出された候補の並びを記録し、先頭の候補を返す。 */
    private final List<List<String>> offered = new ArrayList<>();

    private NightRoleAssigner assigner() {
        offered.clear();
        return new NightRoleAssigner(() -> NightGoal.ONSET, (dimension, candidates, seed) -> {
            List<String> names = new ArrayList<>();
            candidates.forEach(option -> names.add(option.asText()));
            offered.add(names);
            return new SelectedOption(candidates.get(0), seed);
        });
    }

    /** 過去の夜（起きた日の新しい順）。 */
    private static List<NightRecord> newestFirst(List<NightRecord> oldestFirst) {
        List<NightRecord> copy = new ArrayList<>(oldestFirst);
        Collections.reverse(copy);
        return copy;
    }

    private static List<NightRecord> baselineNights(int count, Integer onset) {
        List<NightRecord> nights = new ArrayList<>();
        for (int offset = 0; offset < count; offset++) {
            nights.add(standard(day(offset), NightRole.BASELINE, onset, null));
        }
        return nights;
    }

    /** 基準を終えたあとの、標準の夜の数（寝つきがそろった基準の夜）。 */
    private static List<NightRecord> pastAfterBaseline(int variantOffsetStart, int count) {
        List<NightRecord> nights = baselineNights(GentlePolicy.BASELINE_NIGHTS, STANDARD_ONSET);
        for (int index = 0; index < count; index++) {
            nights.add(standard(day(variantOffsetStart + index), NightRole.CONTROL, STANDARD_ONSET, null));
        }
        return nights;
    }

    /** 対照の日（通し日が、周期で割り切れる日）と、そうでない日を探す。 */
    private static WakeDate wakeDayWith(boolean control, boolean evenEpochDay) {
        for (int offset = 40; offset < 100; offset++) {
            WakeDate candidate = day(offset);
            boolean isControl = candidate.toEpochDay() % GentlePolicy.CONTROL_EVERY_NIGHTS == 0;
            boolean isEven = candidate.toEpochDay() % 2 == 0;
            if (isControl == control && isEven == evenEpochDay) {
                return candidate;
            }
        }
        throw new IllegalStateException("no such day");
    }

    private static WakeDate nonControlDay() {
        return wakeDayWith(false, false);
    }

    @Test
    void withLearningOffThereIsNoRoleAndNoVariant() {
        NightAssignment assignment = assigner().assign(nonControlDay(), baselineNights(9, STANDARD_ONSET),
                PdcaState.empty(), false);

        assertTrue(assignment.findRole().isEmpty());
        assertTrue(assignment.findChangedFactor().isEmpty());
        assertTrue(offered.isEmpty(), "好みを覚えないときは、選び方も動かさない");
    }

    @Test
    void theFirstBaselineNightsAreStandardAndCountedAsBaseline() {
        NightAssignment assignment = assigner().assign(day(20), baselineNights(GentlePolicy.BASELINE_NIGHTS - 1,
                STANDARD_ONSET), PdcaState.empty(), true);

        assertEquals(NightRole.BASELINE, assignment.findRole().orElseThrow());
        assertTrue(assignment.findChangedFactor().isEmpty());
    }

    @Test
    void theBaselineEndsAfterSevenValidNights() {
        NightAssignment assignment = assigner().assign(nonControlDay(), baselineNights(GentlePolicy.BASELINE_NIGHTS,
                STANDARD_ONSET), PdcaState.empty(), true);

        assertFalse(assignment.findRole().orElseThrow() == NightRole.BASELINE);
    }

    @Test
    void theBaselineExtendsWhileTheOnsetIsMissingUpToTheLimit() {
        List<NightRecord> withoutOnset = baselineNights(GentlePolicy.BASELINE_NIGHTS, null);

        NightAssignment extended = assigner().assign(day(30), withoutOnset, PdcaState.empty(), true);

        assertEquals(NightRole.BASELINE, extended.findRole().orElseThrow(), "寝つきのない基準の夜は、延ばす");
    }

    @Test
    void theBaselineStopsAtTheLimitEvenWithoutOnsets() {
        List<NightRecord> limit = baselineNights(GentlePolicy.MAX_BASELINE_NIGHTS, null);

        NightAssignment assignment = assigner().assign(nonControlDay(), limit, PdcaState.empty(), true);

        assertFalse(assignment.findRole().orElseThrow() == NightRole.BASELINE, "上限まで延ばしたら、基準は終える");
    }

    @Test
    void aControlNightComesEveryThirdDayAndTheSameDayAlwaysGivesTheSameRole() {
        WakeDate controlDay = wakeDayWith(true, false);
        List<NightRecord> past = pastAfterBaseline(20, 2);

        NightRoleAssigner assigner = assigner();
        NightAssignment first = assigner.assign(controlDay, past, PdcaState.empty(), true);
        NightAssignment second = assigner.assign(controlDay, past, PdcaState.empty(), true);

        assertEquals(NightRole.CONTROL, first.findRole().orElseThrow());
        assertEquals(first.findRole(), second.findRole());
    }

    @Test
    void aVariantNightChangesOneFactorToANonStandardOptionOnly() {
        NightAssignment assignment = assigner().assign(nonControlDay(), pastAfterBaseline(20, 2), PdcaState.empty(), true);

        assertEquals(NightRole.VARIANT, assignment.findRole().orElseThrow());
        Dimension factor = assignment.findChangedFactor().orElseThrow();
        assertTrue(factor == Dimension.SOUND || factor == Dimension.FADE, "変えてよいのは、音か音量の下げ方だけ");
        String option = assignment.findChangedOption().orElseThrow().asText();
        assertFalse(option.equals(factor == Dimension.SOUND ? "rain" : "30"), "標準と違う選択肢");
    }

    @Test
    void theFactorIsSoundFirstWhenNothingHasBeenJudgedYet() {
        NightAssignment assignment = assigner().assign(nonControlDay(), pastAfterBaseline(20, 2), PdcaState.empty(), true);

        assertEquals(Dimension.SOUND, assignment.findChangedFactor().orElseThrow(), "R3: 音を先に試す");
    }

    @Test
    void theFactorMovesToFadeOnceBothSoundOptionsAreJudged() {
        PdcaState soundResolved = PdcaState.empty()
                .withPreferred(new ArmKey(Dimension.SOUND, new OptionName(WAVE)))
                .withQuarantine(new ArmKey(Dimension.SOUND, new OptionName(FOREST)), day(100));

        NightAssignment assignment = assigner().assign(nonControlDay(), pastAfterBaseline(20, 2), soundResolved, true);

        assertEquals(Dimension.FADE, assignment.findChangedFactor().orElseThrow(), "R3: 音が終わったら、音量の下げ方へ進む"
                + "（よく使う、または外す、のどちらも「動かない判定」）");
    }

    @Test
    void theFactorMovesToFadeAfterTheNightCapEvenWithoutAJudgement() {
        List<NightRecord> past = baselineNights(GentlePolicy.BASELINE_NIGHTS, STANDARD_ONSET);
        for (int offset = 0; offset < GentlePolicy.FACTOR_MAX_VARIANT_NIGHTS; offset++) {
            past.add(soundVariant(day(20 + offset), WAVE, STANDARD_ONSET, null));
        }

        NightAssignment assignment = assigner().assign(nonControlDay(), newestFirst(past), PdcaState.empty(), true);

        assertEquals(Dimension.FADE, assignment.findChangedFactor().orElseThrow(),
                "判定が出ていなくても、24 夜に届いたら次の要因へ進む");
    }

    @Test
    void aQuarantinedOptionIsNotOffered() {
        WakeDate tonight = nonControlDay();
        PdcaState quarantined = PdcaState.empty()
                .withQuarantine(new ArmKey(Dimension.SOUND, new OptionName(WAVE)), tonight.plusDays(GentlePolicy.QUARANTINE_DAYS));

        assigner().assign(tonight, pastAfterBaseline(20, 2), quarantined, true);
        assertFalse(offered.isEmpty(), "今夜は、変える夜であること");

        assertTrue(offered.stream().noneMatch(names -> names.contains(WAVE)), offered.toString());
    }

    @Test
    void anOptionWithMostlyNotFitRecentAnswersIsNotOffered() {
        List<NightRecord> past = baselineNights(GentlePolicy.BASELINE_NIGHTS, STANDARD_ONSET);
        past.add(soundVariant(day(20), WAVE, 40, MorningAnswer.NOT_FIT));
        past.add(soundVariant(day(21), WAVE, 40, MorningAnswer.FIT));
        past.add(soundVariant(day(22), WAVE, 40, MorningAnswer.NOT_FIT));
        past.add(standard(day(23), NightRole.CONTROL, STANDARD_ONSET, null));
        past.add(standard(day(24), NightRole.CONTROL, STANDARD_ONSET, null));

        assigner().assign(nonControlDay(), newestFirst(past), PdcaState.empty(), true);

        assertTrue(offered.stream().noneMatch(names -> names.contains(WAVE)), "直近の答えで「合わなかった」が多い");
    }

    @Test
    void theSameOptionIsRestedAfterTheConsecutiveLimit() {
        List<NightRecord> past = baselineNights(GentlePolicy.BASELINE_NIGHTS, STANDARD_ONSET);
        for (int offset = 0; offset < GentlePolicy.MAX_CONSECUTIVE_VARIANT; offset++) {
            past.add(soundVariant(day(20 + offset), WAVE, 40, null));
        }

        assigner().assign(nonControlDay(), newestFirst(past), PdcaState.empty(), true);

        assertTrue(offered.stream().noneMatch(names -> names.contains(WAVE)), "3 夜続けたら、4 夜目は休む");
    }

    /** 再挑戦の間隔より先の日（見送りの除外が、まだ効いているあいだを確かめるテストで使う）。 */
    private static WakeDate farBeforeParkRetry() {
        return day(1000);
    }

    @Test
    void aParkedOptionIsOnlyUsedWithinItsFactorWhenNothingElseIsLeft() {
        PdcaState parked = PdcaState.empty()
                .withParked(new ArmKey(Dimension.SOUND, new OptionName(WAVE)), farBeforeParkRetry());

        NightAssignment assignment = assigner().assign(nonControlDay(), pastAfterBaseline(20, 2), parked, true);

        assertEquals(Dimension.SOUND, assignment.findChangedFactor().orElseThrow(),
                "見送り（SIMILAR）は、要因を終える判定には数えない（R3 改訂: ノイズで SIMILAR になりやすいため）");
        assertTrue(offered.stream().noneMatch(names -> names.contains(WAVE)), "見送りより、ほかの候補（森）を先に");
    }

    @Test
    void aParkedOptionIsStillUsedWhenItIsTheOnlyChoice() {
        PdcaState allParked = PdcaState.empty()
                .withParked(new ArmKey(Dimension.SOUND, new OptionName(WAVE)), farBeforeParkRetry())
                .withParked(new ArmKey(Dimension.SOUND, new OptionName(FOREST)), farBeforeParkRetry())
                .withParked(new ArmKey(Dimension.FADE, new OptionName("15")), farBeforeParkRetry())
                .withParked(new ArmKey(Dimension.FADE, new OptionName("45")), farBeforeParkRetry());

        NightAssignment assignment = assigner().assign(nonControlDay(), pastAfterBaseline(20, 2), allParked, true);

        assertEquals(NightRole.VARIANT, assignment.findRole().orElseThrow(), "ほかに候補がないときだけ、見送りを使う");
    }

    @Test
    void aParkedOptionIsTreatedAsEligibleAfterTheRetryInterval() {
        WakeDate retryFrom = nonControlDay();
        PdcaState parked = PdcaState.empty().withParked(new ArmKey(Dimension.SOUND, new OptionName(WAVE)), retryFrom);

        assigner().assign(retryFrom, pastAfterBaseline(20, 2), parked, true);

        assertTrue(offered.stream().anyMatch(names -> names.contains(WAVE)),
                "再挑戦の間隔が過ぎたら、見送りより先に外さない");
    }

    @Test
    void aPreferredOptionIsOfferedAloneOnEveryOtherDay() {
        PdcaState preferred = PdcaState.empty().withPreferred(new ArmKey(Dimension.SOUND, new OptionName(WAVE)));
        WakeDate evenDay = wakeDayWith(false, true);

        assigner().assign(evenDay, pastAfterBaseline(20, 2), preferred, true);

        assertEquals(List.of(WAVE), offered.get(0), "よく選ぶと決めたものは、半分の日だけ、優先して出す");
    }

    @Test
    void aHoldAfterARollbackGivesTheStandardPlanUntilItsLastDay() {
        ArmKey wave = new ArmKey(Dimension.SOUND, new OptionName(WAVE));
        PdcaState holding = PdcaState.empty().withQuarantine(wave, day(30)).withRollback(wave, day(30));

        NightRoleAssigner assigner = assigner();
        assertEquals(NightRole.CONTROL, assigner.assign(day(29), pastAfterBaseline(20, 2), holding, true)
                .findRole().orElseThrow());
        assertEquals(NightRole.CONTROL, assigner.assign(day(30), pastAfterBaseline(20, 2), holding, true)
                .findRole().orElseThrow());
        assertFalse(holding.isHolding(day(31)), "保留は、終わりの日まで");
    }
}
