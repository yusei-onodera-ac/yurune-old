package app.yurune.constant;

/**
 * 運用の記録（構造化ログ）の名前。自由な文を書く窓口はなく、ここにある名前と、種類・件数だけを書く。
 * 会話の本文、音声、メールアドレス、パスワード、LLM に送った文は、どの項目にも入らない。
 */
public final class LogConstants {

    /** Cloud Run が付ける、リクエストのトレースの情報。形は TRACE_ID/SPAN_ID;o=1。 */
    public static final String TRACE_HEADER = "X-Cloud-Trace-Context";
    public static final String RESPONSE_TRACE_HEADER = "X-Trace-Id";
    public static final String ATTR_TRACE_ID = "traceId";
    public static final int TRACE_ID_HEX_CHARS = 32;

    /** 記録の項目の名前。Cloud Logging が特別に扱う名前（severity、message、trace）を含む。 */
    public static final String FIELD_SEVERITY = "severity";
    public static final String FIELD_MESSAGE = "message";
    public static final String FIELD_TIME = "time";
    public static final String FIELD_TRACE = "logging.googleapis.com/trace";
    public static final String FIELD_EVENT = "event";
    public static final String FIELD_TRACE_ID = "traceId";
    public static final String FIELD_DETAIL = "detail";

    public static final String EVENT_REQUEST = "request";
    public static final String EVENT_CONVERSATION = "conversation";
    public static final String EVENT_DECISION = "decision";
    public static final String EVENT_AUTH = "auth";
    public static final String EVENT_ERASE = "erase";
    public static final String EVENT_SETTING = "setting";
    public static final String EVENT_PDCA = "pdca";

    public static final String SEVERITY_INFO = "INFO";
    public static final String SEVERITY_WARNING = "WARNING";

    /** 利用者の記録上の名前（uid を、そのまま出さず、短く一方向に変えたもの）の長さ。 */
    public static final int USER_HASH_CHARS = 16;

    // ---- 運用の記録（可観測性）の種類 ----

    /** 夜のエージェントの、道具の呼び出し 1 回ごとの記録（A1）。 */
    public static final String EVENT_AGENT = "agent";
    /** LLM 呼び出し 1 回ごとの記録（A2）。 */
    public static final String EVENT_LLM = "llm";
    /** 制御の状態が変わったときの記録（A3。ブレーカー、呼び出しの上限、停止スイッチ、Model Armor）。 */
    public static final String EVENT_CONTROL = "control";
    /** 外部呼び出しの区間の記録（B2。O1 では項目名の登録のみで、実装は O2）。 */
    public static final String EVENT_SPAN = "span";

    private LogConstants() {
    }
}
