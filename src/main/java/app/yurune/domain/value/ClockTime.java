package app.yurune.domain.value;

import app.yurune.constant.TimeConstants;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/**
 * 時刻（時と分）。就寝時刻と起床時刻に使う。
 */
public final class ClockTime {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final LocalTime time;

    private ClockTime(LocalTime time) {
        this.time = time.withSecond(0).withNano(0);
    }

    /** 入力欄の値（例 23:42）から作る。形式が違うときは例外にする。 */
    public static ClockTime parse(String text) {
        try {
            return new ClockTime(LocalTime.parse(text, FORMAT));
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new IllegalArgumentException("invalid time: " + text, exception);
        }
    }

    /** 時計の時刻から作る（秒は切り捨てる）。 */
    public static ClockTime of(LocalTime time) {
        return new ClockTime(time);
    }

    public String asText() {
        return time.format(FORMAT);
    }

    public int asMinutesOfDay() {
        return time.getHour() * TimeConstants.MINUTES_PER_HOUR + time.getMinute();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ClockTime that && time.equals(that.time);
    }

    @Override
    public int hashCode() {
        return Objects.hash(time);
    }
}
