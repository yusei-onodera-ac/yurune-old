package app.yurune.pdca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.domain.entity.NightPlan;
import app.yurune.domain.value.ArmKey;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.DisplayText;
import app.yurune.domain.value.MorningAnswer;
import app.yurune.domain.value.OptionName;
import app.yurune.domain.value.WakeDate;
import app.yurune.pdca.SimulatedSleeper.SleepModel;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * 合成の利用者（SimulatedSleeper）の単体の確認。同じ種で同じ結果になること、分布が狙いの範囲にあること、条件どおりに欠けること。
 */
class SimulatedSleeperTest {

    private static final long SEED = 7L;
    private static final int SAMPLE_NIGHTS = 3000;
    private static final int GRID_POINTS = 1000;
    private static final int REPLAY_NIGHTS = 56;
    private static final int LONG_RUN_NIGHTS = 1000;
    private static final double MEAN_TOLERANCE_MIN = 2.0;
    private static final double EFFECT_TOLERANCE_MIN = 1.0;
    private static final double SPREAD_LOW_MIN = 17.0;
    private static final double SPREAD_HIGH_MIN = 23.0;
    private static final double RATIO_TOLERANCE = 0.03;
    private static final double DRIFT_CHECK_DAYS = 100.0;
    private static final double FIT_GAP_MIN = 0.2;
    private static final int SHORT_ONSET_MIN = 25;
    private static final int LONG_ONSET_MIN = 65;
    private static final String WEEKDAY = "2026-10-05";
    private static final String SATURDAY = "2026-10-03";
    private static final String SUNDAY = "2026-10-04";
    private static final String OPTION_RAIN = "rain";
    private static final String OPTION_FADE_STANDARD = "30";
    private static final String OPTION_WAVE = "wave";
    private static final String DURATION_STANDARD = "45";
    private static final String TALK_STANDARD = "none";
    private static final double WAVE_SHIFT_MIN = -15.0;
    private static final double DRIFT_RATE = -0.3;
    private static final double NO_DRIFT = 0.0;
    private static final double NO_EFFECT = 0.0;
    private static final double MISSING_RATIO = 0.7;
    private static final double CHANGE_RATIO = 0.3;
    private static final double FIT_BASE = 0.35;

    @Test
    void theSameSeedGivesTheSameNights() {
        SleepModel model = Scenarios.model(FIT_BASE, CHANGE_RATIO, MISSING_RATIO, NO_DRIFT, Map.of());
        List<Integer> first = onsets(new SimulatedSleeper(SEED, model), REPLAY_NIGHTS, WEEKDAY);
        List<Integer> second = onsets(new SimulatedSleeper(SEED, model), REPLAY_NIGHTS, WEEKDAY);
        List<Integer> other = onsets(new SimulatedSleeper(SEED + 1, model), REPLAY_NIGHTS, WEEKDAY);
        assertEquals(first, second, "同じ種は、同じ寝つきの列になる");
        assertFalse(first.equals(other), "違う種は、違う列になる");
    }

    @Test
    void theNightlySpreadMatchesTheModel() {
        SimulatedSleeper sleeper = new SimulatedSleeper(SEED, plainModel());
        List<Integer> onsets = onsets(sleeper, SAMPLE_NIGHTS, WEEKDAY);
        double mean = mean(onsets);
        double spread = Math.sqrt(variance(onsets, mean));
        assertTrue(Math.abs(mean - sleeper.personMean()) <= MEAN_TOLERANCE_MIN, "平均は、人の基準の周りに寄る: " + mean);
        assertTrue(spread >= SPREAD_LOW_MIN && spread <= SPREAD_HIGH_MIN, "夜ごとのばらつきは、仮置きの近さ: " + spread);
    }

    @Test
    void weekendNightsRunALittleLater() {
        SleepModel model = plainModel();
        double weekday = mean(onsets(new SimulatedSleeper(SEED, model), SAMPLE_NIGHTS, WEEKDAY));
        double weekend = mean(onsets(new SimulatedSleeper(SEED, model), SAMPLE_NIGHTS, SATURDAY));
        double sunday = mean(onsets(new SimulatedSleeper(SEED, model), SAMPLE_NIGHTS, SUNDAY));
        assertTrue(Math.abs((weekend - weekday) - SimulatedSleeper.WEEKEND_SHIFT_MIN) <= MEAN_TOLERANCE_MIN,
                "土曜の夜は、平日より少し遅い");
        assertTrue(Math.abs((sunday - weekday) - SimulatedSleeper.WEEKEND_SHIFT_MIN) <= MEAN_TOLERANCE_MIN,
                "日曜の夜は、平日より少し遅い");
    }

    @Test
    void theDriftMovesTheOnsetsByTheRateEachDay() {
        SleepModel model = Scenarios.model(FIT_BASE, NO_EFFECT, NO_EFFECT, DRIFT_RATE, Map.of());
        SimulatedSleeper sleeper = new SimulatedSleeper(SEED, model);
        double early = mean(onsetsOnDay(sleeper, 0, SAMPLE_NIGHTS));
        double late = mean(onsetsOnDay(sleeper, (int) DRIFT_CHECK_DAYS, SAMPLE_NIGHTS));
        assertTrue(Math.abs((late - early) - DRIFT_RATE * DRIFT_CHECK_DAYS) <= MEAN_TOLERANCE_MIN,
                "傾向は、1 日あたりの量どおりに動く: " + (late - early));
    }

    @Test
    void aChosenOptionShiftsTheOnsetByItsEffect() {
        SleepModel withEffect = Scenarios.model(FIT_BASE, NO_EFFECT, NO_EFFECT, NO_DRIFT,
                Map.of(new ArmKey(Dimension.SOUND, new OptionName(OPTION_WAVE)), WAVE_SHIFT_MIN));
        SleepModel withoutEffect = Scenarios.model(FIT_BASE, NO_EFFECT, NO_EFFECT, NO_DRIFT, Map.of());
        List<Integer> chosen = new ArrayList<>();
        List<Integer> standard = new ArrayList<>();
        SimulatedSleeper effectSleeper = new SimulatedSleeper(SEED, withEffect);
        SimulatedSleeper plainSleeper = new SimulatedSleeper(SEED, withoutEffect);
        for (int night = 0; night < LONG_RUN_NIGHTS; night++) {
            chosen.add(effectSleeper.onsetMinutes(WakeDate.parse(WEEKDAY), night, wavePlan()));
            standard.add(plainSleeper.onsetMinutes(WakeDate.parse(WEEKDAY), night, standardPlan()));
        }
        assertTrue(Math.abs((mean(chosen) - mean(standard)) - WAVE_SHIFT_MIN) <= EFFECT_TOLERANCE_MIN,
                "選んだ選択肢の差は、同じ種の上で、そのまま寝つきに乗る");
    }

    @Test
    void theSameSeedKeepsTheNightsWhenOnlyTheMorningAnswersAreMissing() {
        SleepModel complete = Scenarios.model(FIT_BASE, NO_EFFECT, NO_EFFECT, NO_DRIFT, Map.of());
        SleepModel missing = Scenarios.model(FIT_BASE, NO_EFFECT, MISSING_RATIO, NO_DRIFT, Map.of());
        assertEquals(onsets(new SimulatedSleeper(SEED, complete), REPLAY_NIGHTS, WEEKDAY),
                onsets(new SimulatedSleeper(SEED, missing), REPLAY_NIGHTS, WEEKDAY),
                "朝の答えの欠けを変えても、寝つきの列は変わらない（S1 と S7 を比べられる）");
    }

    @Test
    void missingMorningAnswersFollowTheRatio() {
        SimulatedSleeper sleeper = new SimulatedSleeper(SEED, Scenarios.model(FIT_BASE, NO_EFFECT, MISSING_RATIO,
                NO_DRIFT, Map.of()));
        int answered = 0;
        for (int night = 0; night < SAMPLE_NIGHTS; night++) {
            if (sleeper.morningInputFor(SHORT_ONSET_MIN).findAnswer().isPresent()) {
                answered++;
            }
        }
        double answeredRatio = (double) answered / SAMPLE_NIGHTS;
        assertTrue(Math.abs(answeredRatio - (1.0 - MISSING_RATIO)) <= RATIO_TOLERANCE, "答えの割合: " + answeredRatio);
    }

    @Test
    void changedNightsFollowTheRatio() {
        SimulatedSleeper sleeper = new SimulatedSleeper(SEED, Scenarios.model(FIT_BASE, CHANGE_RATIO, NO_EFFECT,
                NO_DRIFT, Map.of()));
        int changed = 0;
        for (int night = 0; night < SAMPLE_NIGHTS; night++) {
            if (sleeper.changesModeTonight()) {
                changed++;
            }
        }
        double changedRatio = (double) changed / SAMPLE_NIGHTS;
        assertTrue(Math.abs(changedRatio - CHANGE_RATIO) <= RATIO_TOLERANCE, "切り替えの割合: " + changedRatio);
    }

    @Test
    void shortOnsetsTendToBeFitAndLongOnesTendToBeNotFit() {
        SimulatedSleeper sleeper = new SimulatedSleeper(SEED, plainModel());
        double shortFit = fitShare(sleeper, SHORT_ONSET_MIN);
        double longFit = fitShare(sleeper, LONG_ONSET_MIN);
        assertTrue(shortFit - longFit >= FIT_GAP_MIN, "寝つきが短い夜は、「合っていた」になりやすい");
    }

    private static SleepModel plainModel() {
        return Scenarios.model(FIT_BASE, NO_EFFECT, NO_EFFECT, NO_DRIFT, Map.of());
    }

    private static double fitShare(SimulatedSleeper sleeper, int onsetMin) {
        int fit = 0;
        for (int point = 0; point < GRID_POINTS; point++) {
            double draw = (point + 0.5) / GRID_POINTS;
            if (sleeper.answerFor(onsetMin, draw) == MorningAnswer.FIT) {
                fit++;
            }
        }
        return (double) fit / GRID_POINTS;
    }

    private static List<Integer> onsets(SimulatedSleeper sleeper, int count, String date) {
        List<Integer> onsets = new ArrayList<>();
        WakeDate wakeDate = WakeDate.parse(date);
        for (int night = 0; night < count; night++) {
            onsets.add(sleeper.onsetMinutes(wakeDate, night, standardPlan()));
        }
        return onsets;
    }

    private static List<Integer> onsetsOnDay(SimulatedSleeper sleeper, int day, int count) {
        List<Integer> onsets = new ArrayList<>();
        WakeDate wakeDate = WakeDate.parse(WEEKDAY);
        for (int night = 0; night < count; night++) {
            onsets.add(sleeper.onsetMinutes(wakeDate, day, standardPlan()));
        }
        return onsets;
    }

    private static double mean(List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).average().orElse(0.0);
    }

    private static double variance(List<Integer> values, double mean) {
        return values.stream().mapToDouble(value -> (value - mean) * (value - mean)).average().orElse(0.0);
    }

    private static NightPlan standardPlan() {
        return plan(null, null);
    }

    private static NightPlan wavePlan() {
        return plan(Dimension.SOUND, OPTION_WAVE);
    }

    private static NightPlan plan(Dimension changed, String option) {
        Map<Dimension, OptionName> options = new EnumMap<>(Dimension.class);
        options.put(Dimension.SOUND, new OptionName(OPTION_RAIN));
        options.put(Dimension.FADE, new OptionName(OPTION_FADE_STANDARD));
        options.put(Dimension.DURATION, new OptionName(DURATION_STANDARD));
        options.put(Dimension.TALK, new OptionName(TALK_STANDARD));
        Set<Dimension> changedSet = EnumSet.noneOf(Dimension.class);
        if (changed != null) {
            options.put(changed, new OptionName(option));
            changedSet.add(changed);
        }
        return new NightPlan(options, changedSet, new DisplayText("テスト用の計画"), 0L);
    }
}
