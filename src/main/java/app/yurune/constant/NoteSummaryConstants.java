package app.yurune.constant;

/**
 * ふりかえり「目標」の「いまのところ」の文言、経路、画面の名前。
 * 文は、事実（夜数、中央値、どの選択肢か）から、決まった文型で作る（LLM は使わない）。
 * 結論は言い切らず、「まだ、はっきりとは言えないよ」を添える。原因や効果は言わない。
 * 文言の検査（GentleLanguageLintTest）の対象。
 */
public final class NoteSummaryConstants {

    public static final String BASELINE_LINE = "いまは、標準のすごし方で、夜のようすを見ているよ。";
    public static final String WATCHING_LINE = "まだ、はっきりしないよ。もう少し、様子を見るね。";
    public static final String HEDGE_LINE = "まだ、はっきりとは言えないよ。";

    public static final String STANDARD_ONSET_HEAD = "標準の夜は、寝つきのめやすが、だいたい";
    public static final String STANDARD_ONSET_UNIT = "分ごろだよ";
    public static final String STANDARD_WAKE_HEAD = "標準の夜は、夜中にまた始めた回数が、だいたい";
    public static final String STANDARD_WAKE_UNIT = "回ごろだよ";
    public static final String STANDARD_MORNING_HEAD = "標準の夜の、朝のようすは、";
    public static final String STANDARD_MORNING_TAIL = "夜ぶん集まっているよ。";

    public static final String COMPARE_HEAD = "「";
    public static final String COMPARE_LABEL_TAIL = "」の夜は、標準より";
    public static final String NIGHT_COUNT_HEAD = "（";
    public static final String NIGHT_COUNT_TAIL = "夜）。";

    /** 受診のすすめの、見つけたものが長く無いときの一文。悪化そのものを検出しての受診のすすめはしない。 */
    public static final String REFER_STAGNANT_LINE = "最近、あまり変わらないみたい。";

    /** 「今は表示しない」を押したときに止める日数（仮置き。根拠なし）。 */
    public static final int REFER_HIDE_DAYS = 30;

    public static final String PATH_REFER_HIDE = "/review/refer/hide";
    public static final String PARAM_PANEL = "panel";
    public static final String PANEL_GOAL = "goal";

    public static final String ATTR_SUMMARY_LINES = "noteSummary";
    public static final String ATTR_REFER_SHOWN = "noteReferShown";
    public static final String ATTR_COUNT_PHRASE = "memoryCountPhrase";

    private NoteSummaryConstants() {
    }
}
