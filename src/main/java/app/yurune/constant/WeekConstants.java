package app.yurune.constant;

/**
 * 週の比較の表示ルール（仕様書 6 節。しきい値は仮置き）。
 */
public final class WeekConstants {

    /** 比較を出すために、各期間に必要な記録日数。少ない記録で差を示さないため。 */
    public static final int MIN_RECORDED_DAYS_PER_PERIOD = 5;

    /** 比較を出すために必要な、平均睡眠時間の差（分）。 */
    public static final int MIN_AVERAGE_GAIN_MINUTES = 15;

    /** 曜日ごとのグラフで、縦軸の下端にする時間。 */
    public static final int CHART_MIN_HOURS = 4;

    /** 曜日ごとのグラフで、縦軸の幅（時間）。 */
    public static final int CHART_SPAN_HOURS = 4;

    /** グラフの基準線の高さ。 */
    public static final int CHART_BASELINE_Y = 104;

    /** 目盛りの文字を、線より、少し下に置くための距離。 */
    public static final double CHART_LABEL_OFFSET_Y = 3;

    /** グラフの描画領域の高さ。 */
    public static final int CHART_HEIGHT = 90;

    /** グラフの最初の棒の位置と、棒の組の間隔、組の中の棒の間隔。 */
    public static final int CHART_FIRST_X = 36;
    public static final int CHART_GROUP_STEP = 37;
    public static final int CHART_BAR_STEP = 13;
    public static final int CHART_BAR_WIDTH = 11;

    /** 目盛りに出す時間。 */
    public static final int[] CHART_GRID_HOURS = {5, 6, 7};

    private WeekConstants() {
    }
}
