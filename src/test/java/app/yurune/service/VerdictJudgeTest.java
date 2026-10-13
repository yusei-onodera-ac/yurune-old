package app.yurune.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.constant.GentlePolicy;
import app.yurune.domain.value.NightGoal;
import app.yurune.domain.value.ReviewVerdict;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 判定（Mann-Whitney の正規近似と、中央値、一貫性）。純粋な計算なので、時計も保存先も使わない。
 */
class VerdictJudgeTest {

    private static final double DELTA = 1e-9;
    private static final double MIN_ONSET = 10.0;

    /** 寝つきの値（分）を、よい方向を正にそろえた値（負の値）にする。 */
    private static List<Double> onsets(double... minutes) {
        return Arrays.stream(minutes).map(minute -> -minute).boxed().toList();
    }

    @Test
    void medianOfAnOddAndAnEvenCount() {
        assertEquals(3.0, VerdictJudge.median(List.of(5.0, 1.0, 3.0)), DELTA);
        assertEquals(2.5, VerdictJudge.median(List.of(4.0, 1.0, 2.0, 3.0)), DELTA);
        assertEquals(0.0, VerdictJudge.median(List.of()), DELTA, "空のときは 0（呼び出し側が夜数を確かめる）");
    }

    @Test
    void identicalDistributionsHaveAZScoreOfZeroEvenWithTies() {
        List<Double> values = List.of(1.0, 2.0, 3.0, 4.0, 5.0);

        assertEquals(0.0, VerdictJudge.zScore(values, values), DELTA);
    }

    @Test
    void aVariantThatIsAlwaysAboveTheControlHasAClearlyPositiveZScore() {
        double z = VerdictJudge.zScore(List.of(10.0, 11.0, 12.0, 13.0, 14.0), List.of(1.0, 2.0, 3.0, 4.0, 5.0));

        assertTrue(z > GentlePolicy.SIGNIFICANCE_Z, "z = " + z);
    }

    @Test
    void notEnoughNightsInAGroupIsNeedMore() {
        ReviewVerdict verdict = VerdictJudge.judge(onsets(10, 10, 10, 10), onsets(40, 40, 40, 40, 40, 40), MIN_ONSET);

        assertEquals(ReviewVerdict.NEED_MORE, verdict, "変えた夜が 4 夜しかない");
    }

    @Test
    void shorterOnsetsInTheVariantAreBetterWhenAllTheChecksAgree() {
        List<Double> variant = onsets(10, 12, 9, 11, 10, 8);
        List<Double> control = onsets(40, 42, 38, 41, 39, 40);

        assertEquals(ReviewVerdict.BETTER, VerdictJudge.judge(variant, control, MIN_ONSET));
    }

    @Test
    void longerOnsetsInTheVariantAreWorse() {
        List<Double> variant = onsets(70, 72, 69, 71, 70, 68);
        List<Double> control = onsets(40, 42, 38, 41, 39, 40);

        assertEquals(ReviewVerdict.WORSE, VerdictJudge.judge(variant, control, MIN_ONSET));
    }

    @Test
    void interleavedNightsWithNoClearDifferenceAreSimilar() {
        List<Double> variant = onsets(38, 42, 40, 36, 44, 40);
        List<Double> control = onsets(41, 39, 43, 37, 40, 42);

        assertEquals(ReviewVerdict.SIMILAR, VerdictJudge.judge(variant, control, MIN_ONSET));
    }

    @Test
    void aDifferenceSmallerThanTheMinimumIsSimilarEvenWhenItIsConsistent() {
        List<Double> variant = onsets(36, 37, 35, 36, 37, 36);
        List<Double> control = onsets(40, 41, 39, 40, 41, 40);

        assertEquals(ReviewVerdict.SIMILAR, VerdictJudge.judge(variant, control, MIN_ONSET), "差が 10 分に届かない");
    }

    @Test
    void theMorningGoalIsJudgedTheOtherWayAroundBecauseHigherIsBetter() {
        // 朝のようすは、点が大きいほどよい。よい方向に正にそろえた値（点そのもの）で比べる
        List<Double> variant = List.of(2.0, 2.0, 2.0, 2.0, 2.0, 2.0);
        List<Double> control = List.of(1.0, 1.0, 1.0, 0.0, 1.0, 1.0);
        double minDiff = NightMetricReader.minDiffOf(NightGoal.MORNING);

        assertEquals(ReviewVerdict.BETTER, VerdictJudge.judge(variant, control, minDiff));
        assertFalse(NightMetricReader.lowerIsBetter(NightGoal.MORNING));
        assertTrue(NightMetricReader.lowerIsBetter(NightGoal.ONSET));
    }

    @Test
    void tiedValuesDoNotBreakTheRankingAndTheZScoreStaysFinite() {
        double z = VerdictJudge.zScore(List.of(5.0, 5.0, 5.0, 5.0, 5.0), List.of(5.0, 5.0, 5.0, 5.0, 5.0));

        assertFalse(Double.isNaN(z));
        assertEquals(0.0, z, DELTA);
    }

    // ---- judgePaired（R2: ペアの差 + 符号反転の並べ替え検定） ----

    @Test
    void judgePairedNeedsMoreNightsWhenTheDiffCountIsBelowTheMinimum() {
        List<Double> diffs = List.of(30.0, 30.0, 30.0, 30.0);

        assertEquals(ReviewVerdict.NEED_MORE, VerdictJudge.judgePaired(diffs, MIN_ONSET, 0.05));
    }

    @Test
    void judgePairedIsBetterWhenDiffsAreConsistentlyPositiveAndSignificant() {
        List<Double> diffs = List.of(30.0, 30.0, 30.0, 30.0, 30.0, 30.0);

        assertEquals(ReviewVerdict.BETTER, VerdictJudge.judgePaired(diffs, MIN_ONSET, 0.05));
    }

    @Test
    void judgePairedIsWorseWhenDiffsAreConsistentlyNegativeAndSignificant() {
        List<Double> diffs = List.of(-30.0, -30.0, -30.0, -30.0, -30.0, -30.0);

        assertEquals(ReviewVerdict.WORSE, VerdictJudge.judgePaired(diffs, MIN_ONSET, 0.05));
    }

    @Test
    void judgePairedIsSimilarWhenTheMedianDiffIsBelowTheMinimum() {
        List<Double> diffs = List.of(2.0, -2.0, 3.0, -1.0, 1.0, -3.0);

        assertEquals(ReviewVerdict.SIMILAR, VerdictJudge.judgePaired(diffs, MIN_ONSET, 0.05));
    }

    @Test
    void aStricterAlphaFromBonferroniCanTurnABorderlineBetterIntoSimilar() {
        List<Double> diffs = List.of(12.0, 12.0, 12.0, 12.0, 12.0, -12.0);

        ReviewVerdict loose = VerdictJudge.judgePaired(diffs, MIN_ONSET, 0.5);
        ReviewVerdict strict = VerdictJudge.judgePaired(diffs, MIN_ONSET, 0.001);

        assertEquals(ReviewVerdict.BETTER, loose);
        assertEquals(ReviewVerdict.SIMILAR, strict, "選択肢の数で割った、厳しい有意水準では、有意にならない");
    }

    @Test
    void shareOfNightsOnOneSideOfTheControlMedian() {
        List<Double> values = List.of(1.0, 2.0, 3.0, 4.0);

        assertEquals(0.5, VerdictJudge.shareAbove(values, 2.5), DELTA);
        assertEquals(0.5, VerdictJudge.shareBelow(values, 2.5), DELTA);
        assertEquals(0.0, VerdictJudge.shareAbove(List.of(), 2.5), DELTA);
    }
}
