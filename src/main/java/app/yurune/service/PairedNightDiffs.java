package app.yurune.service;

import app.yurune.constant.GentlePolicy;
import app.yurune.domain.entity.NightRecord;
import app.yurune.domain.value.ArmKey;
import app.yurune.domain.value.NightGoal;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 変えた夜ごとに、時間が近い標準の夜（基準か対照。前後 3 夜以内、最大 2 夜）との差を作る（R2: ドリフト対策）。
 * 全部の期間の対照とまとめて比べると、時間とともに自然に良くなる・わるくなる人では、あとの変える夜が、
 * 前の対照より必ず良く（わるく）見えてしまう。近い時期の対照とだけ比べることで、この偏りを抑える。
 * 近い標準の夜がない変えた夜は、比べる対象から外す。
 */
public final class PairedNightDiffs {

    private PairedNightDiffs() {
    }

    /** 標準の夜（基準か対照）で、めやすの値がある夜だけを取り出す。 */
    public static List<NightRecord> standardNightsOf(NightGoal goal, List<NightRecord> nights) {
        return nights.stream()
                .filter(NightMetricReader::isStandardNight)
                .filter(night -> NightMetricReader.isValidFor(goal, night))
                .toList();
    }

    /**
     * 1 つの変えた夜の、近い標準の夜との差（よい方向が正）。近い標準の夜がないときは空。
     * standards は、standardNightsOf で作ったものを渡す（毎回作り直さないため）。
     */
    public static Optional<Double> diffOf(NightGoal goal, NightRecord variantNight, List<NightRecord> standards) {
        List<NightRecord> nearby = standards.stream()
                .filter(standard -> distanceOf(standard, variantNight) <= GentlePolicy.PAIR_WINDOW_DAYS)
                .sorted(Comparator.comparingInt(standard -> distanceOf(standard, variantNight)))
                .limit(GentlePolicy.MAX_PAIRED_CONTROLS)
                .toList();
        if (nearby.isEmpty()) {
            return Optional.empty();
        }
        double controlAverage = nearby.stream().mapToDouble(standard -> scoreOf(goal, standard)).average().orElseThrow();
        return Optional.of(scoreOf(goal, variantNight) - controlAverage);
    }

    /** ある選択肢の、変えた夜すべての差（近い標準の夜がない夜は除く）。夜の並び順は問わない。 */
    public static List<Double> diffsFor(NightGoal goal, List<NightRecord> nights, ArmKey key) {
        List<NightRecord> standards = standardNightsOf(goal, nights);
        return nights.stream()
                .filter(night -> NightMetricReader.isValidFor(goal, night))
                .filter(night -> NightMetricReader.findVariantKey(night).filter(key::equals).isPresent())
                .map(night -> diffOf(goal, night, standards))
                .filter(Optional::isPresent)
                .map(Optional::orElseThrow)
                .toList();
    }

    private static int distanceOf(NightRecord standard, NightRecord variantNight) {
        return Math.abs(standard.getWakeDate().daysUntil(variantNight.getWakeDate()));
    }

    private static double scoreOf(NightGoal goal, NightRecord night) {
        return NightMetricReader.goodnessOf(goal, NightMetricReader.valueFor(goal, night).orElseThrow());
    }
}
