package app.yurune.constant;

/**
 * 画面の守り（セキュリティヘッダー、CSRF 対策、Cookie）の名前と値。
 */
public final class SecurityConstants {

    /** CSRF トークンを、フォームで送るときの名前と、fetch で送るときのヘッダー名。 */
    public static final String CSRF_PARAM = "_csrf";
    public static final String CSRF_HEADER = "X-CSRF-Token";
    public static final String ATTR_CSRF_TOKEN = "csrfToken";
    public static final String SESSION_CSRF_TOKEN = "yuruneCsrfToken";
    public static final int CSRF_TOKEN_BYTES = 32;

    /** 画面（JSP）を作らない静的なファイルの経路。ここでは、セッションもトークンも作らない。 */
    public static final String[] STATIC_PATH_PREFIXES = {"/css/", "/js/", "/images/"};

    public static final String HEADER_CSP = "Content-Security-Policy";
    public static final String HEADER_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    public static final String HEADER_FRAME_OPTIONS = "X-Frame-Options";
    public static final String HEADER_REFERRER_POLICY = "Referrer-Policy";
    public static final String HEADER_PERMISSIONS_POLICY = "Permissions-Policy";
    public static final String HEADER_OPENER_POLICY = "Cross-Origin-Opener-Policy";
    public static final String HEADER_CACHE_CONTROL = "Cache-Control";
    public static final String HEADER_HSTS = "Strict-Transport-Security";

    /**
     * スクリプトは自分のドメインだけ。外部画像は不可。フォントだけ Google Fonts を許す。
     * style 属性（棒の長さなど、値で変わる見た目）だけ、インラインを許す。スクリプトは許さない。
     * worker-src は、script-src から引き継がれるので足さなくても動くが、将来 script-src を広げても、Service Worker は自分のドメインだけに留めるために、別に書く。
     */
    public static final String CSP_VALUE = "default-src 'none'; "
            + "script-src 'self'; "
            + "style-src 'self' https://fonts.googleapis.com; "
            + "style-src-attr 'unsafe-inline'; "
            + "font-src https://fonts.gstatic.com; "
            + "img-src 'self' data:; "
            + "connect-src 'self'; "
            + "media-src 'self' blob:; "
            + "manifest-src 'self'; "
            + "worker-src 'self'; "
            + "form-action 'self'; "
            + "base-uri 'none'; "
            + "frame-ancestors 'none'";

    public static final String CONTENT_TYPE_OPTIONS_VALUE = "nosniff";
    public static final String FRAME_OPTIONS_VALUE = "DENY";
    /** no-referrer だと、ブラウザが同じサイトへのフォーム送信にも Origin: null を付け、同一サイトの確認ができなくなる。 */
    public static final String REFERRER_POLICY_VALUE = "same-origin";
    /** マイクだけ自分のページに許す（会話の音声入力）。位置情報やカメラは使わない。 */
    public static final String PERMISSIONS_POLICY_VALUE = "microphone=(self), camera=(), geolocation=(), payment=()";
    public static final String OPENER_POLICY_VALUE = "same-origin";
    /** 利用者のデータを含む画面を、ブラウザや中継に残さない。 */
    public static final String CACHE_CONTROL_VALUE = "no-store";
    public static final String HSTS_VALUE = "max-age=31536000; includeSubDomains";

    /** Cloud Run などの手前の中継が、元の通信が HTTPS だったことを伝えるヘッダー。 */
    public static final String FORWARDED_PROTO_HEADER = "X-Forwarded-Proto";
    public static final String FORWARDED_PROTO_HTTPS = "https";

    public static final String COOKIE_SAME_SITE_ATTRIBUTE = "SameSite";
    public static final String COOKIE_SAME_SITE_VALUE = "Strict";

    private SecurityConstants() {
    }
}
