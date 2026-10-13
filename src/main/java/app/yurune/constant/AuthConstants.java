package app.yurune.constant;

/**
 * 登録・ログインの入力の範囲と、試行の制限、規約の版。
 */
public final class AuthConstants {

    public static final int PASSWORD_MIN_CHARS = 8;
    public static final int PASSWORD_MAX_CHARS = 128;
    public static final int EMAIL_MAX_CHARS = 254;

    /** 同じメールアドレスへの、パスワードの失敗の上限と、数える期間。超えたら、期間が過ぎるまで受け付けない。 */
    public static final int LOGIN_MAX_FAILURES = 5;
    public static final int LOGIN_WINDOW_MINUTES = 15;

    /** 全体の失敗の上限（1 つのサーバーあたり）。認証の API の使用量を守る。 */
    public static final int GLOBAL_MAX_FAILURES = 200;
    public static final int GLOBAL_WINDOW_MINUTES = 5;

    /** 登録の上限（1 つのサーバーあたり）。作り放題を防ぐ。 */
    public static final int REGISTRATION_MAX = 30;
    public static final int REGISTRATION_WINDOW_MINUTES = 60;

    /** いま表示している規約・プライバシーの版。同意した版を記録する。 */
    public static final String TERMS_VERSION = "2026-10-07";

    public static final String SESSION_USER = "yuruneUser";

    public static final String PATH_LOGIN = "/login";
    public static final String PATH_REGISTER = "/register";
    public static final String PATH_LOGOUT = "/logout";
    public static final String PATH_TERMS = "/terms";
    public static final String PATH_PRIVACY = "/privacy";
    public static final String PATH_HEALTH = "/healthz";
    public static final String PATH_ERASE = "/settings/erase";
    /** 保存先（Firestore）に読み書きできるかの確認。 */
    public static final String PATH_READY = "/ready";

    public static final String VIEW_LOGIN = "/WEB-INF/views/login.jsp";
    public static final String VIEW_REGISTER = "/WEB-INF/views/register.jsp";
    public static final String VIEW_TERMS = "/WEB-INF/views/terms.jsp";
    public static final String VIEW_PRIVACY = "/WEB-INF/views/privacy.jsp";
    public static final String VIEW_ERASE = "/WEB-INF/views/erase.jsp";

    public static final String PARAM_EMAIL = "email";
    public static final String PARAM_PASSWORD = "password";
    public static final String PARAM_AGE = "age";
    public static final String PARAM_TERMS = "terms";

    public static final String ATTR_MESSAGE = "authMessage";
    public static final String ATTR_EMAIL = "authEmail";
    public static final String ATTR_TERMS_VERSION = "termsVersion";
    public static final String ATTR_CURRENT_EMAIL = "currentEmail";

    private AuthConstants() {
    }
}
