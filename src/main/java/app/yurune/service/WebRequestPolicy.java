package app.yurune.service;

import app.yurune.constant.AuthConstants;
import app.yurune.constant.PwaConstants;
import app.yurune.constant.SecurityConstants;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * リクエストの種類の見分け方（静的ファイルか、状態を変える操作か、同じサイトからか）。
 * Filter が使う判断だけを、サーブレットの API から切り離して置く。
 */
public final class WebRequestPolicy {

    private static final Set<String> STATE_CHANGING_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");
    private static final String FETCH_SITE_CROSS = "cross-site";
    private static final Set<String> PUBLIC_PATHS = Set.of(AuthConstants.PATH_LOGIN, AuthConstants.PATH_REGISTER,
            AuthConstants.PATH_TERMS, AuthConstants.PATH_PRIVACY, AuthConstants.PATH_HEALTH, AuthConstants.PATH_READY);

    public boolean isStaticPath(String path) {
        if (path == null) {
            return false;
        }
        if (PwaConstants.ROOT_FILE_PATHS.contains(path)) {
            return true;
        }
        for (String prefix : SecurityConstants.STATIC_PATH_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 稼働・準備の確認の経路（監視が、ログインなしで短い間隔で呼ぶ）。
     * CsrfFilter が、ここではトークンを出さず、セッションを作らないために使う。
     */
    public boolean isHealthCheckPath(String path) {
        return AuthConstants.PATH_HEALTH.equals(path) || AuthConstants.PATH_READY.equals(path);
    }

    /**
     * 応答に付ける Cache-Control。利用者ごとに違う画面と API は、端末にも中継にも残させない（no-store）。
     * 利用者によらず同じ、アプリの入口の固定ファイル（マニフェスト、Service Worker、案内ページ）は、残してよいが、毎回確かめさせる（no-cache）。
     * css・js・画像は、指定しない。
     */
    public Optional<String> findCacheControl(String path) {
        if (path != null && PwaConstants.ROOT_FILE_PATHS.contains(path)) {
            return Optional.of(PwaConstants.CACHE_CONTROL_REVALIDATE);
        }
        if (isStaticPath(path)) {
            return Optional.empty();
        }
        return Optional.of(SecurityConstants.CACHE_CONTROL_VALUE);
    }

    /** ログインしていなくても開ける経路（ログイン、登録、規約、稼働の確認）と、静的なファイル。 */
    public boolean isPublicPath(String path) {
        return isStaticPath(path) || PUBLIC_PATHS.contains(path);
    }

    /**
     * リクエストの記録（request ログ）を、通常は出さない経路（静的ファイル、稼働・準備の確認）。
     * 5xx のときと、遅いときは、呼び出し側（TraceFilter）が、それでも記録する。
     */
    public boolean isQuietPath(String path) {
        return isStaticPath(path) || AuthConstants.PATH_HEALTH.equals(path) || AuthConstants.PATH_READY.equals(path);
    }

    public boolean isStateChanging(String method) {
        return method != null && STATE_CHANGING_METHODS.contains(method.toUpperCase(Locale.ROOT));
    }

    /**
     * ブラウザが付ける Origin（または Referer）の宛先が、この画面の宛先（Host）と同じかを確かめる。
     * どちらも無いときは、トークンの照合に任せる。
     */
    public boolean isSameOrigin(String origin, String host, String fetchSite) {
        if (FETCH_SITE_CROSS.equals(fetchSite)) {
            return false;
        }
        if (origin == null || origin.isEmpty()) {
            return true;
        }
        try {
            URI uri = URI.create(origin);
            String originHost = uri.getPort() < 0 ? uri.getHost() : uri.getHost() + ":" + uri.getPort();
            return originHost != null && originHost.equalsIgnoreCase(host);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
