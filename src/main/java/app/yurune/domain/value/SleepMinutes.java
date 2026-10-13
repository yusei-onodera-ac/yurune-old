package app.yurune.domain.value;

import app.yurune.constant.TimeConstants;

/**
 * 睡眠時間（分）。
 */
public final class SleepMinutes {

    /** 分を 2 桁にそろえる書式。 */
    private static final String CLOCK_FORMAT = "%d:%02d";

    private final int minutes;

    public SleepMinutes(int minutes) {
        if (minutes < 0) {
            throw new IllegalArgumentException("minutes must not be negative");
        }
        this.minutes = minutes;
    }

    public int asInt() {
        return minutes;
    }

    /** 例: 6時間40分 */
    public String asHourMinuteText() {
        return minutes / TimeConstants.MINUTES_PER_HOUR + "時間" + minutes % TimeConstants.MINUTES_PER_HOUR + "分";
    }

    /** 例: 6:40 */
    public String asClockText() {
        return String.format(CLOCK_FORMAT, minutes / TimeConstants.MINUTES_PER_HOUR,
                minutes % TimeConstants.MINUTES_PER_HOUR);
    }
}
