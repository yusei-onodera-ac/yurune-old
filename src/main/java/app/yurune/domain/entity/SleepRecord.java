package app.yurune.domain.entity;

import app.yurune.constant.TimeConstants;
import app.yurune.domain.value.ClockTime;
import app.yurune.domain.value.SleepMinutes;
import app.yurune.domain.value.WakeDate;

import java.util.Objects;

/**
 * 1 晩の睡眠の記録（起きた日、就寝時刻、起床時刻）。
 * 記録のない日も、同じ型で表す。
 */
public final class SleepRecord {

    private final WakeDate wakeDate;
    private final ClockTime bedTime;
    private final ClockTime wakeTime;

    private SleepRecord(WakeDate wakeDate, ClockTime bedTime, ClockTime wakeTime) {
        this.wakeDate = Objects.requireNonNull(wakeDate);
        this.bedTime = bedTime;
        this.wakeTime = wakeTime;
    }

    public static SleepRecord recorded(WakeDate wakeDate, ClockTime bedTime, ClockTime wakeTime) {
        return new SleepRecord(wakeDate, Objects.requireNonNull(bedTime), Objects.requireNonNull(wakeTime));
    }

    public static SleepRecord unrecorded(WakeDate wakeDate) {
        return new SleepRecord(wakeDate, null, null);
    }

    public boolean isRecorded() {
        return bedTime != null;
    }

    public WakeDate getWakeDate() {
        return wakeDate;
    }

    public ClockTime getBedTime() {
        requireRecorded();
        return bedTime;
    }

    public ClockTime getWakeTime() {
        requireRecorded();
        return wakeTime;
    }

    /** 日をまたぐ就寝（23:30 に寝て 06:40 に起きる、など）も、正しい長さにする。 */
    public SleepMinutes getDuration() {
        requireRecorded();
        int minutes = wakeTime.asMinutesOfDay() - bedTime.asMinutesOfDay();
        if (minutes < 0) {
            minutes += TimeConstants.MINUTES_PER_DAY;
        }
        return new SleepMinutes(minutes);
    }

    private void requireRecorded() {
        if (!isRecorded()) {
            throw new IllegalStateException("record is empty: " + wakeDate.asIsoText());
        }
    }
}
