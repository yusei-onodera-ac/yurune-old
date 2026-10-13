package app.yurune.repository;

/**
 * 保存先（Firestore など）が使えないとき（通信の失敗、時間切れ、サーバーの不調）に投げる。
 * 夜の音と自動終了は、これで止めない。呼び出し側が、再試行や縮退を決める。
 */
public final class StorageUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public StorageUnavailableException(String message) {
        super(message);
    }

    public StorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
