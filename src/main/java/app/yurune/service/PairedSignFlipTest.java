package app.yurune.service;

import java.util.List;
import java.util.Random;

/**
 * 符号反転の並べ替え検定（paired sign-flip permutation test。R2）。
 * 帰無仮説は「各夜の差は、正負どちらに出ても確率は同じ」。正規分布を仮定しないため、
 * 夜数が少なく、外れ値が出やすい夜の値（寝つきなど）にも使える。片側の p 値を、良い方向・悪い方向それぞれで返す。
 *
 * 夜数が少ないときは、符号の組み合わせをすべて試す（正確な検定）。多いときは、種を固定したモンテカルロで近似する
 * （再現できること。乱数は Java だけで書けるものを使う）。
 */
public final class PairedSignFlipTest {

    /** すべての符号の組み合わせを試す、夜数の上限（2^15 通り）。これを超えたら、モンテカルロにする。仮置き。根拠なし。 */
    private static final int EXACT_MAX_NIGHTS = 15;

    /** モンテカルロで試す、符号の組み合わせの数。仮置き。根拠なし。 */
    private static final int MONTE_CARLO_SAMPLES = 20_000;

    /** モンテカルロの乱数の種。固定して、同じ入力には同じ結果になるようにする。 */
    private static final long MONTE_CARLO_SEED = 20_261_007L;

    private PairedSignFlipTest() {
    }

    /**
     * 良い方向（diff が正）に、観測した合計以上に偏った、符号反転の組み合わせの割合（片側 p 値）。
     * 差がすべて 0 のときは、符号を変えても変わらないので 1.0（有意にしない）。
     */
    public static double pValueForBetter(List<Double> diffs) {
        return pValue(diffs, true);
    }

    /** わるい方向（diff が負）に、観測した合計以下に偏った、符号反転の組み合わせの割合（反対側の片側 p 値）。 */
    public static double pValueForWorse(List<Double> diffs) {
        return pValue(diffs, false);
    }

    private static double pValue(List<Double> diffs, boolean betterSide) {
        int n = diffs.size();
        if (n == 0) {
            return 1.0;
        }
        double[] values = new double[n];
        for (int index = 0; index < n; index++) {
            values[index] = diffs.get(index);
        }
        double observed = sum(values);
        return n <= EXACT_MAX_NIGHTS ? exactPValue(values, observed, betterSide) : monteCarloPValue(values, observed, betterSide);
    }

    /** すべての 2^n 通りの符号の組み合わせ（観測した組み合わせ自身も含む）を試す、正確な検定。 */
    private static double exactPValue(double[] values, double observed, boolean betterSide) {
        int n = values.length;
        long combinations = 1L << n;
        long extreme = 0;
        for (long mask = 0; mask < combinations; mask++) {
            if (isAtLeastAsExtreme(signedSum(values, mask), observed, betterSide)) {
                extreme++;
            }
        }
        return (double) extreme / combinations;
    }

    /** 符号の組み合わせが多すぎるとき、種を固定した乱数で、符号の反転を近似する。 */
    private static double monteCarloPValue(double[] values, double observed, boolean betterSide) {
        Random random = new Random(MONTE_CARLO_SEED);
        int extreme = 0;
        for (int sample = 0; sample < MONTE_CARLO_SAMPLES; sample++) {
            double total = 0.0;
            for (double value : values) {
                total += random.nextBoolean() ? value : -value;
            }
            if (isAtLeastAsExtreme(total, observed, betterSide)) {
                extreme++;
            }
        }
        return (double) extreme / MONTE_CARLO_SAMPLES;
    }

    /** mask のビットが 1 の夜は符号そのまま、0 の夜は符号を反転した合計。 */
    private static double signedSum(double[] values, long mask) {
        double total = 0.0;
        for (int index = 0; index < values.length; index++) {
            boolean unflipped = ((mask >> index) & 1L) == 1L;
            total += unflipped ? values[index] : -values[index];
        }
        return total;
    }

    private static boolean isAtLeastAsExtreme(double permuted, double observed, boolean betterSide) {
        return betterSide ? permuted >= observed : permuted <= observed;
    }

    private static double sum(double[] values) {
        double total = 0.0;
        for (double value : values) {
            total += value;
        }
        return total;
    }
}
