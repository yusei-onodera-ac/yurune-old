package app.yurune.constant;

/**
 * 時刻と日付の計算、表示で使う固定値。
 */
public final class TimeConstants {

    public static final int SECONDS_PER_MINUTE = 60;

    public static final int MINUTES_PER_HOUR = 60;

    public static final int MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR;

    public static final int DAYS_PER_WEEK = 7;

    /** 利用者の生活時間に合わせる。サーバーが UTC で動いても「今日」がずれないようにするため。 */
    public static final String TIME_ZONE_ID = "Asia/Tokyo";

    /** 月曜から日曜の順。{@link java.time.DayOfWeek#getValue()} から 1 を引いた番号で引く。 */
    public static final String[] WEEKDAY_TEXTS = {"月", "火", "水", "木", "金", "土", "日"};

    private TimeConstants() {
    }
}
