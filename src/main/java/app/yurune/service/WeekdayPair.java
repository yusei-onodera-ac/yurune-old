package app.yurune.service;

import app.yurune.domain.entity.SleepRecord;

import java.util.Objects;

/**
 * 同じ曜日の、今週と先週の記録。
 */
public final class WeekdayPair {

    private final SleepRecord thisWeekRecord;
    private final SleepRecord previousWeekRecord;

    public WeekdayPair(SleepRecord thisWeekRecord, SleepRecord previousWeekRecord) {
        this.thisWeekRecord = Objects.requireNonNull(thisWeekRecord);
        this.previousWeekRecord = Objects.requireNonNull(previousWeekRecord);
    }

    public SleepRecord getThisWeekRecord() {
        return thisWeekRecord;
    }

    public SleepRecord getPreviousWeekRecord() {
        return previousWeekRecord;
    }
}
