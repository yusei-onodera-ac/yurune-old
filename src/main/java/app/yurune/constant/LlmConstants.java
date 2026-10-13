package app.yurune.constant;

/**
 * LLM（Gemini）の呼び出しに関する固定値。
 */
public final class LlmConstants {

    /** 夜の制御ループに使うモデル。測定（p50 約 0.9 秒、p95 約 1.4 秒）の結果、最速だったもの。 */
    public static final String DEFAULT_MODEL = "gemini-3.5-flash-lite";

    public static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/";

    /** 応答を待つ上限。超えたら、規則に切り替える（音の再生と自動終了は止めない）。 */
    public static final int TIMEOUT_SEC = 10;

    /** 判断の理由の長さの上限（仕様書の propose_plan の reason に合わせる）。 */
    public static final int REASON_MAX_CHARS = 80;

    /** 理由が空、または出力検査に引っかかったときに、記録に残す文。 */
    public static final String REASON_MISSING = "理由の記載なし";
    public static final String REASON_REPLACED = "出力検査により、理由を差し替えました";

    /** 1 回の応答で、生成させる文字数（トークン）の上限。判断はツール呼び出しだけで済むため、小さく。 */
    public static final int MAX_OUTPUT_TOKENS = 256;

    public static final double TEMPERATURE = 0.3;

    /**
     * 反応のない時間がこの秒数に届くまでは、LLM に聞かない（仮置き）。
     * 利用者が操作しているときに、音を下げるなど、不要な判断をさせないため。費用も抑えられる。
     */
    public static final int CONSIDER_AFTER_SILENCE_SEC = 60;

    /** 夜のセッションで、LLM を呼ぶ最小の間隔（秒）の初期値。端末が偽って、何度も呼ばせるのを防ぐ。 */
    public static final int DEFAULT_MIN_CALL_INTERVAL_SEC = 20;

    /** 1 人が 1 日に、LLM を呼べる回数の初期値（D-3。使用量を測ってから調整する仮置き）。 */
    public static final int DEFAULT_DAILY_CALL_CAP = 2000;

    /** サーキットブレーカー: この回数続けて失敗したら、一定時間、LLM を呼ばない（D-10）。 */
    public static final int BREAKER_FAILURE_LIMIT = 3;

    /** サーキットブレーカーが開いている時間（秒）。 */
    public static final int BREAKER_OPEN_SEC = 60;

    private LlmConstants() {
    }
}
