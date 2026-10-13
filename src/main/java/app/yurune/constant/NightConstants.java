package app.yurune.constant;

/**
 * 夜の制御ループの固定値と上限（仕様書 5 節、9 節。値は仮置き）。
 * ここにある上限は、エージェント（LLM を含む）が変えられない固定の枠。
 */
public final class NightConstants {

    /** 夜を始めた時刻がこの時より前なら「今日起きる夜」、後なら「明日起きる夜」として数える。 */
    public static final int NOON_HOUR = 12;

    /** 自動終了の無反応時間の初期値（分）。 */
    public static final int DEFAULT_NO_REACTION_MIN = 15;

    /** 音量の上限の初期値（%）。 */
    public static final int DEFAULT_VOLUME_CAP_PCT = 60;

    /** 観測を端末から受け取る間隔の初期値（秒）。 */
    public static final int DEFAULT_OBSERVATION_INTERVAL_SEC = 30;

    /** 夜を始めたときの音量（%）。上限を超える場合は上限にそろえる。 */
    public static final int START_VOLUME_PCT = 40;

    /** 会話で、利用者の反応がこの秒数以上ないとき、環境音だけに切り替える。 */
    public static final int TALK_GAP_LIMIT_SEC = 30;

    /** 自動終了のとき、音を下げきるまでの時間（分）。許可リストの範囲（5〜60）の中。 */
    public static final int GOODNIGHT_FADE_MINUTES = 15;

    /** 利用者が、これだけ黙っているときは、こちらから話しかけない（起こさないため。D-13、仮置き）。 */
    public static final int NO_INITIATIVE_SILENCE_SEC = 60;

    /** 音量を上げるときの、1 回の上げ幅の上限（%）。急に大きくして起こさないため（D-13、仮置き）。 */
    public static final int MAX_VOLUME_STEP_UP_PCT = 10;

    /** 音量を上げるときに、かける時間の下限（秒）。 */
    public static final int MIN_RAMP_UP_SEC = 10;

    /** 音量の下限（%）。開始音量の半分（仮置き。根拠なし）。 */
    public static final int FADE_FLOOR_VOLUME_PCT = START_VOLUME_PCT / 2;

    /** 音量を下げるときの、1 回の下げ幅の上限（%）。急に下げて起こさないため（仮置き。根拠なし）。 */
    public static final int MAX_VOLUME_STEP_DOWN_PCT = 10;

    /** 計画の FADE を指定しない（古い呼び出し元、既定の計画）ときの、標準の分数。PlanConstants.DEFAULT_FADE と同じ値。 */
    public static final int DEFAULT_FADE_MINUTES = 30;

    public static final int SET_VOLUME_RAMP_MAX_SEC = 120;
    public static final int FADE_OUT_MIN_MINUTES = 5;
    public static final int FADE_OUT_MAX_MINUTES = 60;
    public static final int TALK_LEVEL_MAX = 2;
    public static final int SAY_MAX_CHARS = 60;

    /** 夜の状態が、これより長く前に始まっていたら、終わっていない記録が残っていても、終わった夜として扱う（端末が消えたときの備え）。 */
    public static final int MAX_SESSION_HOURS = 12;

    /** 端末から届く秒数の上限（1 日）。範囲外の値を、そのまま計算に使わないため。 */
    public static final int MAX_REPORTED_SECONDS = 24 * 60 * 60;

    public static final int PERCENT_MAX = 100;

    private NightConstants() {
    }
}
