package app.yurune.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 符号反転の並べ替え検定（R2）。純粋な計算なので、時計も保存先も使わない。
 */
class PairedSignFlipTestTest {

    private static final double DELTA = 1e-9;

    @Test
    void allPositiveDiffsGiveTheSmallestPossiblePValueForBetter() {
        List<Double> diffs = List.of(5.0, 5.0, 5.0, 5.0, 5.0);

        double pValue = PairedSignFlipTest.pValueForBetter(diffs);

        assertEquals(1.0 / 32.0, pValue, DELTA, "5 夜すべて同じ正の値なら、符号の組み合わせの中で最も極端（1/2^5）");
    }

    @Test
    void allNegativeDiffsGiveTheSmallestPossiblePValueForWorse() {
        List<Double> diffs = List.of(-5.0, -5.0, -5.0, -5.0, -5.0);

        assertEquals(1.0 / 32.0, PairedSignFlipTest.pValueForWorse(diffs), DELTA);
        assertEquals(1.0, PairedSignFlipTest.pValueForBetter(diffs), DELTA, "正の方向には、まったく偏っていない");
    }

    @Test
    void mixedDiffsAreNotSignificantInEitherDirection() {
        List<Double> diffs = List.of(3.0, -3.0, 2.0, -2.0, 1.0, -1.0);

        assertTrue(PairedSignFlipTest.pValueForBetter(diffs) > 0.4, "符号が半々なら、どちらの向きにも有意にならない");
        assertTrue(PairedSignFlipTest.pValueForWorse(diffs) > 0.4);
    }

    @Test
    void allZeroDiffsAreNeverSignificant() {
        List<Double> diffs = List.of(0.0, 0.0, 0.0, 0.0);

        assertEquals(1.0, PairedSignFlipTest.pValueForBetter(diffs), DELTA);
        assertEquals(1.0, PairedSignFlipTest.pValueForWorse(diffs), DELTA);
    }

    @Test
    void emptyDiffsAreNeverSignificant() {
        assertEquals(1.0, PairedSignFlipTest.pValueForBetter(List.of()), DELTA);
        assertEquals(1.0, PairedSignFlipTest.pValueForWorse(List.of()), DELTA);
    }

    @Test
    void aClearMajorityInOneDirectionIsSignificantEvenWithManyNights() {
        List<Double> diffs = List.of(5.0, 6.0, 4.0, 7.0, 5.0, 6.0, 4.0, 5.0, 6.0, 5.0, 4.0, 6.0, 5.0, 6.0, 4.0, 5.0, 6.0);

        assertTrue(PairedSignFlipTest.pValueForBetter(diffs) < 0.01,
                "17 夜（モンテカルロ側）すべて同じ向きなら、はっきり有意: " + PairedSignFlipTest.pValueForBetter(diffs));
    }
}
