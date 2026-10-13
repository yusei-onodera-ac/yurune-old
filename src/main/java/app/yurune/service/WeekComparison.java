package app.yurune.service;

import app.yurune.domain.value.SleepMinutes;

import java.util.List;
import java.util.Objects;

/**
 * 直近 7 日と、その前の 7 日の比較。
 * 条件を満たさないときは、比較そのものを出さない（「悪化」を示さないため）。
 */
public final class WeekComparison {

    private final boolean shown;
    private final SleepMinutes thisWeekAverage;
    private final SleepMinutes previousWeekAverage;
    private final List<WeekdayPair> pairs;

    public WeekComparison(boolean shown, SleepMinutes thisWeekAverage, SleepMinutes previousWeekAverage,
            List<WeekdayPair> pairs) {
        this.shown = shown;
        this.thisWeekAverage = Objects.requireNonNull(thisWeekAverage);
        this.previousWeekAverage = Objects.requireNonNull(previousWeekAverage);
        this.pairs = List.copyOf(pairs);
    }

    public boolean isShown() {
        return shown;
    }

    public SleepMinutes getThisWeekAverage() {
        return thisWeekAverage;
    }

    public SleepMinutes getPreviousWeekAverage() {
        return previousWeekAverage;
    }

    /** 今週が先週より長い分（分）。短いときは 0。 */
    public SleepMinutes getGain() {
        return new SleepMinutes(Math.max(0, thisWeekAverage.asInt() - previousWeekAverage.asInt()));
    }

    public List<WeekdayPair> getPairs() {
        return pairs;
    }
}
