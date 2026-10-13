package app.yurune.constant;

import java.util.Set;

/**
 * アプリをホーム画面に追加できるようにする、固定のファイル（マニフェスト、Service Worker、つながらないときの案内ページ）の経路と、応答の決め。
 */
public final class PwaConstants {

    public static final String PATH_MANIFEST = "/manifest.webmanifest";
    public static final String PATH_SERVICE_WORKER = "/sw.js";
    public static final String PATH_OFFLINE = "/offline.html";

    /**
     * ルート直下の固定ファイル。ログインなしで取れ、セッションも記録も作らない（静的なファイルと同じ扱い）。
     * 利用者ごとの内容は、何も含めない。Service Worker は、ここにあるものだけを、端末に残す。
     */
    public static final Set<String> ROOT_FILE_PATHS = Set.of(PATH_MANIFEST, PATH_SERVICE_WORKER, PATH_OFFLINE);

    /**
     * 毎回、サーバーに確かめさせる（変わっていなければ 304 で済む）。
     * Service Worker とマニフェストの更新が、端末の古い写しのせいで遅れないようにする。
     */
    public static final String CACHE_CONTROL_REVALIDATE = "no-cache";

    private PwaConstants() {
    }
}
