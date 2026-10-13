package app.yurune.pdca;

import app.yurune.domain.entity.NightPlan;
import app.yurune.domain.value.ArmKey;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.MorningAnswer;
import app.yurune.domain.value.MorningMood;
import app.yurune.domain.value.WakeDate;
import app.yurune.service.MorningInput;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 合成の利用者。寝つきの分（個人差、夜ごとのばらつき、曜日、時間の傾向、選んだすごし方の差）、会話への切り替え、朝の答えを、乱数で作る。
 * 乱数は、利用者の種から 4 本の流れに分ける（個人差、寝つき、切り替え、朝の答え）。
 * ある条件（例: 朝の答えの欠け）だけを変えても、ほかの流れは変わらないので、条件を 1 つずつ比べられる。
 * 寝つきの本当の値は、このクラスだけが知る。アプリは、端末の反応の時刻からしか寝つきを知らない。
 * すべての数値は仮置き。実際の利用者の分布を測ったものではない（報告に書く）。
 */
public final class SimulatedSleeper {

    /** 基準の寝つき（分）の、利用者の平均。仮置き。 */
    static final double BASELINE_MEAN_MIN = 45.0;
    /** 利用者どうしの、基準の寝つきのばらつき（標準偏差、分）。仮置き。 */
    static final double PERSON_SD_MIN = 5.0;
    /** 夜ごとのばらつき（標準偏差、分）。仕様の「夜ごとの差 20 分」を、標準偏差として読む（仮置き）。 */
    static final double NIGHT_SD_MIN = 20.0;
    /** 土曜と日曜に起きる夜の寝つきの遅れ（分）。「週末は少し遅い」を、小さく表す（仮置き）。 */
    static final double WEEKEND_SHIFT_MIN = 5.0;
    /** 寝つきの値の下限と上限（分）。ばらつきの裾を、現実の範囲に収めるため。仮置き。 */
    static final int MIN_ONSET_MIN = 5;
    static final int MAX_ONSET_MIN = 180;
    /** 寝つきの差 1 分あたりで、朝の「合っていた」の確率が動く量（仮置き）。 */
    static final double FIT_SLOPE_PER_MIN = 0.01;
    /** 朝の「合わなかった」の基準の確率（仮置き）。 */
    static final double NOT_FIT_BASE = 0.25;
    /** 朝の答えの確率を、この範囲に収める（仮置き）。 */
    static final double MIN_ANSWER_PROBABILITY = 0.05;
    static final double MAX_ANSWER_PROBABILITY = 0.90;

    private static final long STREAM_STRIDE = 0x9E3779B97F4A7C15L;
    private static final int STREAM_PERSON = 1;
    private static final int STREAM_ONSET = 2;
    private static final int STREAM_CHANGE = 3;
    private static final int STREAM_ANSWER = 4;
    private static final int STREAM_USER_END = 5;
    private static final List<Dimension> CHANGEABLE = List.of(Dimension.SOUND, Dimension.FADE);

    /**
     * シナリオの条件。fitBase は、寝つきが基準どおりの夜の「合っていた」の確率。
     * driftMinPerDay は 1 日あたりの寝つきの変化（負は早くなる）。effectMin は、選択肢ごとの寝つきの差（分、負は早くなる）。
     * userEndsRatio は、利用者が自分で夜を終える（寝つきのめやすが欠ける）割合（R7）。
     * nightSdMin は、夜ごとのばらつき（標準偏差、分。保留のシナリオで変える。既定は NIGHT_SD_MIN）。
     */
    public record SleepModel(double fitBase, double changeRatio, double missingAnswerRatio, double driftMinPerDay,
            Map<ArmKey, Double> effectMin, double userEndsRatio, double nightSdMin) {

        public SleepModel {
            effectMin = Map.copyOf(effectMin);
        }
    }

    private final SleepModel model;
    private final double personMean;
    private final Random onsetRandom;
    private final Random changeRandom;
    private final Random answerRandom;
    private final Random userEndRandom;

    public SimulatedSleeper(long seed, SleepModel model) {
        this.model = model;
        this.personMean = BASELINE_MEAN_MIN
                + PERSON_SD_MIN * new Random(streamSeed(seed, STREAM_PERSON)).nextGaussian();
        this.onsetRandom = new Random(streamSeed(seed, STREAM_ONSET));
        this.changeRandom = new Random(streamSeed(seed, STREAM_CHANGE));
        this.answerRandom = new Random(streamSeed(seed, STREAM_ANSWER));
        this.userEndRandom = new Random(streamSeed(seed, STREAM_USER_END));
    }

    /** 今夜の寝つき（分）。夜の開始から、最後の反応までの時間になる。 */
    public int onsetMinutes(WakeDate wakeDate, int dayIndex, NightPlan plan) {
        double mean = personMean + effectOf(plan) + model.driftMinPerDay() * dayIndex + weekendShiftOf(wakeDate);
        double value = mean + model.nightSdMin() * onsetRandom.nextGaussian();
        long minutes = Math.round(value);
        return (int) Math.max(MIN_ONSET_MIN, Math.min(MAX_ONSET_MIN, minutes));
    }

    /** 今夜、利用者が会話へ切り替えるか（利用者が自分で変える夜）。 */
    public boolean changesModeTonight() {
        return changeRandom.nextDouble() < model.changeRatio();
    }

    /** 今夜、利用者が自分で夜を終えるか（R7: 寝つきのめやすが欠ける夜）。 */
    public boolean endsNightThemselvesTonight() {
        return userEndRandom.nextDouble() < model.userEndsRatio();
    }

    /** 翌朝の答え。欠ける夜は、答えずに閉じた扱いにする。 */
    public MorningInput morningInputFor(int onsetMin) {
        boolean missing = answerRandom.nextDouble() < model.missingAnswerRatio();
        double draw = answerRandom.nextDouble();
        if (missing) {
            return MorningInput.dismissed(null, null);
        }
        MorningAnswer answer = answerFor(onsetMin, draw);
        return MorningInput.saved(null, null, answer, moodOf(answer));
    }

    /** 利用者の基準の寝つき（分）。比べるときの、本当の状態の目安に使う。 */
    double personMean() {
        return personMean;
    }

    /** 朝の答え。寝つきが基準より短いほど「合っていた」になりやすい（仮置き）。 */
    MorningAnswer answerFor(int onsetMin, double draw) {
        double delta = personMean - onsetMin;
        double fit = clampProbability(model.fitBase() + FIT_SLOPE_PER_MIN * delta);
        double notFit = clampProbability(NOT_FIT_BASE - FIT_SLOPE_PER_MIN * delta);
        if (draw < fit) {
            return MorningAnswer.FIT;
        }
        if (draw < 1.0 - notFit) {
            return MorningAnswer.NEUTRAL;
        }
        return MorningAnswer.NOT_FIT;
    }

    static long streamSeed(long seed, int stream) {
        return seed * STREAM_STRIDE + stream;
    }

    private double effectOf(NightPlan plan) {
        double total = 0.0;
        for (Dimension dimension : CHANGEABLE) {
            if (plan.isChanged(dimension)) {
                total += model.effectMin().getOrDefault(new ArmKey(dimension, plan.getOption(dimension)), 0.0);
            }
        }
        return total;
    }

    private static double weekendShiftOf(WakeDate wakeDate) {
        DayOfWeek day = LocalDate.ofEpochDay(wakeDate.toEpochDay()).getDayOfWeek();
        boolean weekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
        return weekend ? WEEKEND_SHIFT_MIN : 0.0;
    }

    private static double clampProbability(double probability) {
        return Math.max(MIN_ANSWER_PROBABILITY, Math.min(MAX_ANSWER_PROBABILITY, probability));
    }

    /** 気分は、答えと合わせる（画面には出さない。ONSET の見直しでは使わない）。 */
    private static MorningMood moodOf(MorningAnswer answer) {
        return switch (answer) {
            case FIT -> MorningMood.CALM;
            case NEUTRAL -> MorningMood.NORMAL;
            case NOT_FIT -> MorningMood.UNEASY;
        };
    }
}
