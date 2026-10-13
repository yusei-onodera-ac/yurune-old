package app.yurune.service;

import app.yurune.constant.GentlePolicy;
import app.yurune.domain.value.ReviewVerdict;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.DoublePredicate;

/**
 * 変えた夜と、標準の夜を比べて、判定を出す（純粋な計算。時計も乱数も使わない）。
 *
 * 検定は Mann-Whitney の U（正規近似）を選ぶ。夜の値は、寝つきや夜中のように、外れ値が出やすく、正規分布を仮定しにくいため。
 * 同順位は平均順位にする。区間推定（ブートストラップ）は使わない（仮置き。シミュレータで確かめる）。
 * 値は、よい方向を正にそろえたもの（大きいほどよい）を渡す。
 */
public final class VerdictJudge {

    /** 平均の式 n1 * n2 / 2 の、2。 */
    private static final double MEAN_DIVISOR = 2.0;
    /** 分散の式 n1 * n2 / 12 の、12。 */
    private static final double VARIANCE_DIVISOR = 12.0;
    /** 中央値の、夜数が偶数のときの平均の 2。 */
    private static final double MEDIAN_DIVISOR = 2.0;

    private VerdictJudge() {
    }

    /**
     * 判定（R2: 時間が近い対照とのペアの差 diffs を、符号反転の並べ替え検定で見る）。
     * diffs が足りないときは NEED_MORE。そろったら、次の 3 つがすべてそろうときだけ、向きを付ける。
     * 中央値の差が最小の差以上、並べ替え検定の p 値が有意水準（alpha）以下、一貫性（向きをそろえた夜の割合）が下限以上。
     * alpha は、呼び出し側が Bonferroni 補正（同じ要因の選択肢の数 k で割る）をして渡す（規則 R3）。
     * diffs は、すでにペアで作った、よい方向が正の差（PairedNightDiffs）。正規近似は使わない。
     */
    public static ReviewVerdict judgePaired(List<Double> diffs, double minDiff, double alpha) {
        if (diffs.size() < GentlePolicy.MIN_NIGHTS_PER_GROUP) {
            return ReviewVerdict.NEED_MORE;
        }
        double diff = median(diffs);
        if (diff >= minDiff && PairedSignFlipTest.pValueForBetter(diffs) <= alpha
                && shareAbove(diffs, 0.0) >= GentlePolicy.CONSISTENCY_RATIO) {
            return ReviewVerdict.BETTER;
        }
        if (diff <= -minDiff && PairedSignFlipTest.pValueForWorse(diffs) <= alpha
                && shareBelow(diffs, 0.0) >= GentlePolicy.CONSISTENCY_RATIO) {
            return ReviewVerdict.WORSE;
        }
        return ReviewVerdict.SIMILAR;
    }

    /**
     * 判定（旧: Mann-Whitney の正規近似。M2 で使った版）。いまの規則では使わないが、既存のテストと、
     * 将来の比較のために残す（削除しない）。どちらかの夜数が足りないときは NEED_MORE。夜数がそろったら、次の 3 つがすべてそろうときだけ、向きを付ける。
     * 中央値の差が最小の差以上、検定の z が有意（向きも一致）、変えた夜の一貫性（向きをそろえた夜の割合）が下限以上。
     * どれかが欠けるときは SIMILAR（差がない、またはばらつきの中の差）。
     */
    public static ReviewVerdict judge(List<Double> variant, List<Double> control, double minDiff) {
        if (variant.size() < GentlePolicy.MIN_NIGHTS_PER_GROUP || control.size() < GentlePolicy.MIN_NIGHTS_PER_GROUP) {
            return ReviewVerdict.NEED_MORE;
        }
        double diff = median(variant) - median(control);
        double z = zScore(variant, control);
        double controlMedian = median(control);
        if (diff >= minDiff && z >= GentlePolicy.SIGNIFICANCE_Z
                && shareAbove(variant, controlMedian) >= GentlePolicy.CONSISTENCY_RATIO) {
            return ReviewVerdict.BETTER;
        }
        if (diff <= -minDiff && z <= -GentlePolicy.SIGNIFICANCE_Z
                && shareBelow(variant, controlMedian) >= GentlePolicy.CONSISTENCY_RATIO) {
            return ReviewVerdict.WORSE;
        }
        return ReviewVerdict.SIMILAR;
    }

    /** 中央値。空のときは 0 を返す（呼び出し側が、夜数を確かめてから使う）。 */
    public static double median(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        return (sorted.get(middle - 1) + sorted.get(middle)) / MEDIAN_DIVISOR;
    }

    /** 値が、基準の値より大きい夜の割合。 */
    public static double shareAbove(List<Double> values, double threshold) {
        return share(values, value -> value > threshold);
    }

    /** 値が、基準の値より小さい夜の割合。 */
    public static double shareBelow(List<Double> values, double threshold) {
        return share(values, value -> value < threshold);
    }

    /**
     * Mann-Whitney の U の正規近似の z。variant が大きい方に寄っているほど正。
     * 同順位があるときは、分散を同順位の補正（tie correction）で小さくする。分散が 0 のときは 0。
     */
    public static double zScore(List<Double> variant, List<Double> control) {
        int n1 = variant.size();
        int n2 = control.size();
        int total = n1 + n2;
        List<double[]> pooled = new ArrayList<>();
        for (double value : variant) {
            pooled.add(new double[] {value, 1.0});
        }
        for (double value : control) {
            pooled.add(new double[] {value, 0.0});
        }
        pooled.sort((left, right) -> Double.compare(left[0], right[0]));

        double rankSumVariant = 0.0;
        double tieTerm = 0.0;
        int start = 0;
        while (start < total) {
            int end = start;
            while (end + 1 < total && pooled.get(end + 1)[0] == pooled.get(start)[0]) {
                end++;
            }
            double averageRank = (start + 1 + end + 1) / MEAN_DIVISOR;
            int tied = end - start + 1;
            tieTerm += (double) tied * tied * tied - tied;
            for (int index = start; index <= end; index++) {
                rankSumVariant += pooled.get(index)[1] * averageRank;
            }
            start = end + 1;
        }
        double u = rankSumVariant - n1 * (n1 + 1) / MEAN_DIVISOR;
        double mean = n1 * (double) n2 / MEAN_DIVISOR;
        double variance = n1 * (double) n2 / VARIANCE_DIVISOR
                * ((total + 1) - tieTerm / ((double) total * (total - 1)));
        if (variance <= 0.0) {
            return 0.0;
        }
        return (u - mean) / Math.sqrt(variance);
    }

    private static double share(List<Double> values, DoublePredicate condition) {
        if (values.isEmpty()) {
            return 0.0;
        }
        long count = values.stream().filter(value -> condition.test(value)).count();
        return count / (double) values.size();
    }
}
