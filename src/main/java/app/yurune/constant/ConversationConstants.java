package app.yurune.constant;

/**
 * 夜の会話（利用者の声 → 文字 → LLM → 検査 → 声）の、上限と名前。
 */
public final class ConversationConstants {

    /** LLM に渡す、直前までのやり取りの数（1 回の発言と返事で 2）。長い履歴での、指示の注入と、費用を抑える。 */
    public static final int HISTORY_MAX_TURNS = 4;

    /** 会話の要求（JSON）の大きさの上限（バイト）。 */
    public static final int REQUEST_MAX_BYTES = 4096;

    /** 会話の LLM 呼び出しの最小の間隔（秒）。夜の制御（20 秒）より短く、声のやり取りの速さに合わせる。 */
    public static final int DEFAULT_TALK_MIN_INTERVAL_SEC = 2;

    public static final double TEMPERATURE = 0.7;
    public static final int MAX_OUTPUT_TOKENS = 160;

    public static final String PATH_TALK = "/night/talk";

    public static final String ROLE_USER = "user";
    public static final String ROLE_MODEL = "model";

    /** 読み上げの声（日本語の標準の声、遅め・低め。仕様書 U-09 の暗黙の扱い）。 */
    public static final String VOICE_LANGUAGE = "ja-JP";
    public static final String VOICE_NAME = "ja-JP-Neural2-B";
    public static final double SPEAKING_RATE = 0.9;
    public static final double PITCH = -2.0;
    /** 夜に眩しくないよう、小さめの声にする（dB）。 */
    public static final double VOLUME_GAIN_DB = -6.0;

    /** 読み上げる文の長さの上限。検査を通した返事（60 文字）より少し余裕を持たせる。 */
    public static final int SPEAK_MAX_CHARS = 120;

    private ConversationConstants() {
    }
}
