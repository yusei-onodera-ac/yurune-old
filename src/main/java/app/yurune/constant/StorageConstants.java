package app.yurune.constant;

/**
 * 保存先（Firestore）と、利用者ごとのデータの置き場所の名前。
 * パスは、仕様書 4 節の users/{uid}/… に合わせる。
 */
public final class StorageConstants {

    public static final String USERS = "users";
    public static final String ARMS = "arms";
    public static final String SLEEP_RECORDS = "sleepRecords";
    public static final String NIGHTS = "nights";
    public static final String DECISIONS = "decisions";
    public static final String SESSIONS = "sessions";
    public static final String PROFILE = "profile";
    /** 見直し（PDCA）の状態。学習の下に置く（利用者の文書の中なので、データの消去で一緒に消える）。 */
    public static final String LEARNING = "learning";

    public static final String CURRENT_SESSION_ID = "current";
    public static final String PROFILE_ID = "main";
    public static final String PDCA_STATE_ID = "pdca";

    /** Firestore への 1 回の通信の時間切れと、試す回数。夜の観測を、長く止めないための短い値。 */
    public static final int REQUEST_TIMEOUT_SEC = 4;
    public static final int REQUEST_ATTEMPTS = 2;
    public static final long RETRY_BACKOFF_MS = 200L;
    public static final int LIST_PAGE_SIZE = 300;

    /** 書き込みに失敗したとき、あとで再試行するために持っておく件数の上限（メモリを使い切らないため）。 */
    public static final int PENDING_WRITE_LIMIT = 500;

    /** 最近の夜を調べるときの件数（朝のひとことの対象は、最近の夜にしかない）。 */
    public static final int RECENT_NIGHT_LIMIT = 30;

    private StorageConstants() {
    }
}
