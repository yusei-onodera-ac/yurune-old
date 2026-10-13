package app.yurune.constant;

/**
 * 外部のサービスの応答（HTTP の状態コード）の判断に使う値。
 */
public final class HttpStatusConstants {

    public static final int OK = 200;
    public static final int NOT_FOUND = 404;
    public static final int TOO_MANY_REQUESTS = 429;
    public static final int SERVER_ERROR = 500;

    private HttpStatusConstants() {
    }
}
