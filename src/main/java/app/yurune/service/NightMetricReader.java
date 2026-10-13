package app.yurune.service;

import app.yurune.constant.GentlePolicy;
import app.yurune.domain.entity.NightRecord;
import app.yurune.domain.value.ArmKey;
import app.yurune.domain.value.MorningMood;
import app.yurune.domain.value.NightGoal;
import app.yurune.domain.value.NightRole;

import java.util.Optional;

/**
 * 夜の記録から、めやすの値を読む（数値だけ。会話の本文は、ここにも記録にもない）。
 * 欠測（値のない夜）は空を返し、失敗とは扱わない。利用者が自分で変えた夜は、比べる対象から外す。
 */
public final class NightMetricReader {

    private NightMetricReader() {
    }

    /**
     * めやすの値。寝つきは、無反応で終わった夜だけにある。夜中は、どの夜にもある。朝のようすは、朝の答えと気分があるときだけ。
     */
    public static Optional<Double> valueFor(NightGoal goal, NightRecord night) {
        return switch (goal) {
            case ONSET -> night.findOnsetProxyMin().map(minutes -> (double) minutes.asInt());
            case WAKE -> Optional.of((double) night.getRestartCount());
            case MORNING -> morningPointOf(night);
        };
    }

    /** 小さいほどよいめやす（寝つき、夜中）か。朝のようすは、点が大きいほどよい。 */
    public static boolean lowerIsBetter(NightGoal goal) {
        return goal != NightGoal.MORNING;
    }

    /** 比べて意味があると見なす、最小の差（めやすの単位）。 */
    public static double minDiffOf(NightGoal goal) {
        return switch (goal) {
            case ONSET -> GentlePolicy.MIN_ONSET_DIFF_MIN;
            case WAKE -> GentlePolicy.MIN_WAKE_DIFF;
            case MORNING -> GentlePolicy.MIN_MORNING_DIFF;
        };
    }

    /**
     * よい方向を正にそろえた値（大きいほどよい）。寝つきや夜中は、符号を反転して、比べる向きを同じにする。
     */
    public static double goodnessOf(NightGoal goal, double value) {
        return lowerIsBetter(goal) ? -value : value;
    }

    /** 比べる対象になる夜か。利用者が自分で変えた夜と、値のない夜（欠測）は、外す。 */
    public static boolean isValidFor(NightGoal goal, NightRecord night) {
        return !night.isUserChanged() && valueFor(goal, night).isPresent();
    }

    /** 変える夜の、変えた選択肢（要因と選択肢の組）。変える夜でなければ空。 */
    public static Optional<ArmKey> findVariantKey(NightRecord night) {
        if (!hasRole(night, NightRole.VARIANT)) {
            return Optional.empty();
        }
        return night.findChangedFactor().map(factor -> new ArmKey(factor, night.getPlan().getOption(factor)));
    }

    public static boolean hasRole(NightRecord night, NightRole role) {
        return night.findRole().filter(value -> value == role).isPresent();
    }

    /** 標準のやり方の夜（基準か、対照）。比べるときの、標準の側になる。 */
    public static boolean isStandardNight(NightRecord night) {
        return hasRole(night, NightRole.BASELINE) || hasRole(night, NightRole.CONTROL);
    }

    private static Optional<Double> morningPointOf(NightRecord night) {
        if (!night.isMorningChecked()) {
            return Optional.empty();
        }
        return night.findMood().map(NightMetricReader::moodPoint);
    }

    /** 気分の点。画面には出さない（仮置き）。 */
    static double moodPoint(MorningMood mood) {
        return switch (mood) {
            case CALM -> GentlePolicy.MOOD_CALM_POINT;
            case NORMAL -> GentlePolicy.MOOD_NORMAL_POINT;
            case UNEASY, SLEEPY, HEAVY -> GentlePolicy.MOOD_OTHER_POINT;
        };
    }
}
