package app.yurune.service;

/**
 * 登録・ログインが成り立たなかったことを、理由の種類で伝える。
 * 画面には、どのメールアドレスが登録済みかを推測できる書き方をしない（呼び出し側が、まとめて同じ文にする）。
 */
public final class AuthException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 失敗の理由。 */
    public enum Reason {
        INVALID_INPUT,
        INVALID_CREDENTIALS,
        EMAIL_ALREADY_USED,
        WEAK_PASSWORD,
        EMAIL_NOT_VERIFIED,
        CONSENT_MISSING,
        TOO_MANY_ATTEMPTS,
        UNAVAILABLE
    }

    private final Reason reason;

    public AuthException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public AuthException(Reason reason, Throwable cause) {
        super(reason.name(), cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
