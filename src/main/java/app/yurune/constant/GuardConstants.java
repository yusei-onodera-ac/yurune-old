package app.yurune.constant;

/**
 * 入力と出力の検査の、上限と置き換えの文字。
 */
public final class GuardConstants {

    /** 個人情報の伏せ字。入力も出力も、これに置き換える。 */
    public static final String MASK_TOKEN = "○○";

    /** 利用者の発言の長さの上限（文字数）。超えた分は切る（請求の高騰と、長い指示の注入を抑える）。 */
    public static final int INPUT_MAX_CHARS = 200;

    /** 出力の長さの上限。仕様書の say の上限と同じ。 */
    public static final int OUTPUT_MAX_CHARS = NightConstants.SAY_MAX_CHARS;

    /** システムの指示の一部が、出力に現れたかを見る、連続した文字数。短すぎると、普通の言葉に当たってしまう。 */
    public static final int LEAK_WINDOW_CHARS = 12;

    /** 数字を LLM に書かせず、コードが後から入れるための印。{{名前}} の形。 */
    public static final String MARKER_OPEN = "{{";
    public static final String MARKER_CLOSE = "}}";
    public static final String MARKER_MINUTES_LEFT = "minutes_left";
    public static final String MARKER_CLOCK_TIME = "clock_time";

    /** カード番号とみなす桁数。 */
    public static final int CARD_MIN_DIGITS = 13;
    public static final int CARD_MAX_DIGITS = 19;
    /** 電話番号とみなす桁数（国内）。 */
    public static final int PHONE_MIN_DIGITS = 10;
    public static final int PHONE_MAX_DIGITS = 11;
    /** 日本の国番号。先頭の + と、数字 2 桁。 */
    public static final String COUNTRY_PREFIX = "+81";
    public static final int COUNTRY_CODE_DIGITS = 2;

    /** 合言葉（システムの指示に埋め込む、出力に出てはいけない文字列）の前置き。 */
    public static final String CANARY_PREFIX = "YRN-";
    public static final int CANARY_BYTES = 9;

    private GuardConstants() {
    }
}
