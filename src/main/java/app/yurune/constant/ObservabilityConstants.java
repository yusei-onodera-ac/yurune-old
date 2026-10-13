package app.yurune.constant;

/**
 * 可観測性の固定値。値は仮置き（根拠なし。計測してから調整する）。
 */
public final class ObservabilityConstants {

    /** リクエストが、これ以上かかったら「遅い」とみなし、静かな経路（A5）でも記録する（ミリ秒。仮置き）。 */
    public static final long SLOW_REQUEST_MS = 1000;

    /** 呼び出しの上限による拒否（A3）を、件数にまとめて記録する間隔（秒。仮置き）。 */
    public static final long RATE_LIMIT_AGGREGATION_SEC = 60;

    /**
     * agent ログの tool に、許可リストにない名前が来たときの記録（AT-06）。LLM の応答から読んだ、検査前の文字列を
     * 切って出すと、プロンプトに注入された発言の一部が、ログに出る恐れがある。切るのではなく、置き換える。
     */
    public static final String UNKNOWN_TOOL_ID = "unknown";

    /** Firestore への呼び出しが、これ以上かかったら span に記録する（ミリ秒。仮置き）。B2。 */
    public static final long FIRESTORE_SPAN_SLOW_MS = 300;

    /** /ready が、保存先への読み書きを確かめる間隔（ミリ秒。仮置き）。叩かれても保存先を叩きすぎないため。B1。 */
    public static final long READY_CACHE_MS = 10_000;

    private ObservabilityConstants() {
    }
}
