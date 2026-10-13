package app.yurune.domain.value;

import app.yurune.constant.NightConstants;
import app.yurune.constant.TimeConstants;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * 起きた日。1 晩の記録を、起きた日で数える。
 */
public final class WakeDate implements Comparable<WakeDate> {

    private final LocalDate date;

    private WakeDate(LocalDate date) {
        this.date = Objects.requireNonNull(date);
    }

    public static WakeDate today(Clock clock) {
        return new WakeDate(LocalDate.now(clock));
    }

    /**
     * いま夜を始めたときの、起きた日。昼前に始めた夜は今日起きる夜、それ以外は明日起きる夜として数える。
     * 夜を始める処理と、今夜の計画は、この同じ規則で起きた日を決める。
     */
    public static WakeDate forNightStartingNow(Clock clock) {
        WakeDate today = today(clock);
        return LocalTime.now(clock).getHour() < NightConstants.NOON_HOUR ? today : today.plusDays(1);
    }

    /** 画面の値（例 2026-10-06）から作る。形式が違うときは例外にする。 */
    public static WakeDate parse(String isoText) {
        try {
            return new WakeDate(LocalDate.parse(isoText));
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new IllegalArgumentException("invalid date: " + isoText, exception);
        }
    }

    public WakeDate minusDays(int days) {
        return new WakeDate(date.minusDays(days));
    }

    public WakeDate plusDays(int days) {
        return new WakeDate(date.plusDays(days));
    }

    public boolean isAfter(WakeDate other) {
        return date.isAfter(other.date);
    }

    /** この日から相手の日までの日数。相手が後なら正。 */
    public int daysUntil(WakeDate other) {
        return (int) ChronoUnit.DAYS.between(date, other.date);
    }

    /** 1970-01-01 からの通し日。同じ日には同じ値になるので、今夜の乱数の種（同じ日は同じ結果）にする。 */
    public long toEpochDay() {
        return date.toEpochDay();
    }

    public String asIsoText() {
        return date.toString();
    }

    /** 例: 10月6日（火） */
    public String asLabelText() {
        return date.getMonthValue() + "月" + date.getDayOfMonth() + "日（" + asWeekdayText() + "）";
    }

    /** 例: 10/6 */
    public String asMonthDayText() {
        return date.getMonthValue() + "/" + date.getDayOfMonth();
    }

    public String asWeekdayText() {
        return TimeConstants.WEEKDAY_TEXTS[date.getDayOfWeek().getValue() - 1];
    }

    @Override
    public int compareTo(WakeDate other) {
        return date.compareTo(other.date);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof WakeDate that && date.equals(that.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(date);
    }
}
