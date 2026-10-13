package app.yurune.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.constant.PwaConstants;
import app.yurune.service.WebRequestPolicy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/**
 * ホーム画面に追加するためのファイル（マニフェスト、アイコン、Service Worker、案内ページ、共通の head）を読んで、
 * 形が正しいことと、利用者ごとのデータを端末に残す書き方がないことを確かめる。
 */
class PwaAssetsLintTest {

    private static final Path WEBAPP = Path.of("src", "main", "webapp");
    private static final WebRequestPolicy REQUEST_POLICY = new WebRequestPolicy();
    private static final Path CONSTANT_SOURCES = Path.of("src", "main", "java", "app", "yurune", "constant");
    private static final String BASE_URL = "https://yurune.example/manifest.webmanifest";
    private static final int APPLE_TOUCH_ICON_SIZE = 180;
    private static final int MINIMUM_ICON_SIZE = 192;
    private static final int LARGE_ICON_SIZE = 512;
    private static final int ALPHA_SHIFT = 24;
    private static final int OPAQUE_ALPHA = 0xFF;
    private static final Set<String> STATIC_FILE_EXTENSIONS = Set.of("html", "css", "js", "png", "svg", "woff2");
    private static final Pattern STATIC_PATHS = Pattern.compile("var STATIC_PATHS = \\[([^\\]]*)\\];");
    private static final Pattern STRING_LITERAL = Pattern.compile("'([^'\\n]*)'");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("(?m)^\\s*//.*$");
    private static final Pattern PATH_CONSTANT = Pattern.compile("String PATH_[A-Z_]+ = \"(/[^\"]*)\"");

    /** 改行の種類（CRLF か LF か）で、検査の結果が変わらないようにする。 */
    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static JsonObject readManifest() throws IOException {
        return JsonParser.parseString(read(WEBAPP.resolve("manifest.webmanifest"))).getAsJsonObject();
    }

    private static String stripComments(String script) {
        return LINE_COMMENT.matcher(BLOCK_COMMENT.matcher(script).replaceAll("")).replaceAll("");
    }

    /** css/yurune.css に、同じ名前の色が並ぶ順に返す（先頭がライト、2 つ目がダーク）。 */
    private static List<String> readCssColors(String tokenName) throws IOException {
        Matcher matcher = Pattern.compile("--" + Pattern.quote(tokenName) + ":(#[0-9A-Fa-f]{6})")
                .matcher(read(WEBAPP.resolve("css").resolve("yurune.css")));
        List<String> colors = new ArrayList<>();
        while (matcher.find()) {
            colors.add(matcher.group(1).toUpperCase(Locale.ROOT));
        }
        return colors;
    }

    /** 利用者ごとに違う画面と API の経路（定数クラスの PATH_ を読むので、画面が増えても、自動で対象になる）。 */
    private static Set<String> readPersonalPaths() throws IOException {
        Set<String> paths = new HashSet<>();
        try (Stream<Path> files = Files.list(CONSTANT_SOURCES)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (file.getFileName().toString().equals("PwaConstants.java")) {
                    continue;
                }
                Matcher matcher = PATH_CONSTANT.matcher(read(file));
                while (matcher.find()) {
                    paths.add(matcher.group(1));
                }
            }
        }
        return paths;
    }

    private static List<String> readStaticPaths(String script) {
        Matcher list = STATIC_PATHS.matcher(script);
        assertTrue(list.find(), "sw.js に、STATIC_PATHS の一覧がない");
        Matcher literals = STRING_LITERAL.matcher(list.group(1));
        List<String> paths = new ArrayList<>();
        while (literals.find()) {
            paths.add(literals.group(1));
        }
        return paths;
    }

    private static int countOccurrences(String text, String word) {
        int count = 0;
        for (int index = text.indexOf(word); index >= 0; index = text.indexOf(word, index + word.length())) {
            count++;
        }
        return count;
    }

    private static boolean hasTransparentPixel(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) >>> ALPHA_SHIFT != OPAQUE_ALPHA) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---- マニフェスト ----

    @Test
    void manifestIsValidJsonThatOpensTheAppAsAStandaloneJapaneseApp() throws IOException {
        JsonObject manifest = readManifest();

        assertEquals("ゆるね", manifest.get("name").getAsString());
        assertEquals("ゆるね", manifest.get("short_name").getAsString());
        assertEquals("standalone", manifest.get("display").getAsString());
        assertEquals("ja", manifest.get("lang").getAsString());
        assertFalse(manifest.get("description").getAsString().isBlank());
    }

    @Test
    void manifestUrlsAreRelativeSoTheAppWorksUnderAnyContextPath() throws IOException {
        JsonObject manifest = readManifest();

        for (String member : List.of("id", "start_url", "scope")) {
            String value = manifest.get(member).getAsString();
            assertFalse(URI.create(value).isAbsolute() || value.startsWith("/"), member + " は、マニフェストからの相対にする");
        }
        URI manifestUrl = URI.create(BASE_URL);
        String scope = manifestUrl.resolve(manifest.get("scope").getAsString()).getPath();
        String startUrl = manifestUrl.resolve(manifest.get("start_url").getAsString()).getPath();
        assertTrue(startUrl.startsWith(scope), "start_url は、scope の中");
        assertEquals("/home", startUrl, "ホーム画面から開くと、ホームへ（ログイン前なら、ログイン画面へ移る）");
    }

    @Test
    void manifestColorsComeFromTheStylesheetTokens() throws IOException {
        JsonObject manifest = readManifest();
        String lightBackground = readCssColors("bg").get(0);

        assertEquals(lightBackground, manifest.get("theme_color").getAsString().toUpperCase(Locale.ROOT));
        assertEquals(lightBackground, manifest.get("background_color").getAsString().toUpperCase(Locale.ROOT));
    }

    @Test
    void manifestIconsExistAndTheirSizesMatchTheDeclaration() throws IOException {
        JsonArray icons = readManifest().getAsJsonArray("icons");
        Set<String> declared = new HashSet<>();

        for (JsonElement element : icons) {
            JsonObject icon = element.getAsJsonObject();
            Path file = WEBAPP.resolve(icon.get("src").getAsString());
            assertTrue(Files.isRegularFile(file), file + " がない");
            assertEquals("image/png", icon.get("type").getAsString());
            BufferedImage image = ImageIO.read(file.toFile());
            assertNotNull(image, file + " は、PNG として読めない");
            int declaredSize = Integer.parseInt(icon.get("sizes").getAsString().split("x")[0]);
            assertEquals(declaredSize, image.getWidth(), file + " の幅");
            assertEquals(declaredSize, image.getHeight(), file + " の高さ");
            declared.add(icon.get("purpose").getAsString() + ":" + declaredSize);
        }

        assertTrue(declared.contains("any:" + MINIMUM_ICON_SIZE));
        assertTrue(declared.contains("any:" + LARGE_ICON_SIZE));
        assertTrue(declared.contains("maskable:" + LARGE_ICON_SIZE), "端末の形に切り抜かれても欠けないアイコン");
    }

    @Test
    void iconsThatTheDeviceMasksOrFillsAreFullyOpaque() throws IOException {
        Path icons = WEBAPP.resolve("images").resolve("icons");

        assertFalse(hasTransparentPixel(ImageIO.read(icons.resolve("icon-maskable-512.png").toFile())),
                "maskable は、透明な所があると、切り抜いたとき黒や白が出る");
        BufferedImage appleIcon = ImageIO.read(icons.resolve("apple-touch-icon.png").toFile());
        assertEquals(APPLE_TOUCH_ICON_SIZE, appleIcon.getWidth());
        assertEquals(APPLE_TOUCH_ICON_SIZE, appleIcon.getHeight());
        assertFalse(hasTransparentPixel(appleIcon), "iOS は、透明な所を黒で埋める");
    }

    @Test
    void iconSourcesUseTheAccentColorOfTheStylesheet() throws IOException {
        String accent = readCssColors("accent").get(0);
        Path icons = WEBAPP.resolve("images").resolve("icons");

        assertTrue(read(icons.resolve("icon.svg")).toUpperCase(Locale.ROOT).contains(accent));
        assertTrue(read(icons.resolve("icon-maskable.svg")).toUpperCase(Locale.ROOT).contains(accent));
    }

    // ---- 共通の head ----

    @Test
    void layoutLinksTheManifestIconsAndColorsInTheHead() throws IOException {
        String layout = read(WEBAPP.resolve("WEB-INF").resolve("tags").resolve("document.tag"));
        List<String> backgrounds = readCssColors("bg");

        assertTrue(layout.contains("rel=\"manifest\" href=\"${base}" + PwaConstants.PATH_MANIFEST + "\""));
        assertTrue(layout.contains("rel=\"apple-touch-icon\" href=\"${base}/images/icons/apple-touch-icon.png\""));
        assertTrue(layout.contains("name=\"mobile-web-app-capable\""));
        assertTrue(layout.contains("name=\"apple-mobile-web-app-title\" content=\"ゆるね\""),
                "ホーム画面の名前が、画面の題（ホーム ・ ゆるね など）にならないように");
        assertTrue(layout.contains("content=\"" + backgrounds.get(0) + "\" media=\"(prefers-color-scheme: light)\""));
        assertTrue(layout.contains("content=\"" + backgrounds.get(1) + "\" media=\"(prefers-color-scheme: dark)\""));
        assertTrue(layout.contains("name=\"theme-color\" content=\"" + readCssColors("n-bg").get(0) + "\""), "夜の画面は、暗いまま");
        assertTrue(layout.contains("name=\"service-worker\" content=\"${base}" + PwaConstants.PATH_SERVICE_WORKER + "\""));
        for (String path : List.of("icon-192.png", "apple-touch-icon.png")) {
            assertTrue(Files.isRegularFile(WEBAPP.resolve("images").resolve("icons").resolve(path)), path);
        }
    }

    @Test
    void clientScriptRegistersTheServiceWorkerWithoutBreakingThePageWhenItFails() throws IOException {
        String script = read(WEBAPP.resolve("js").resolve("yurune.js"));
        int register = script.indexOf("navigator.serviceWorker.register(");

        assertTrue(register > 0);
        assertTrue(script.indexOf(".catch(", register) > register, "登録の失敗を受け止める");
        assertTrue(script.contains("'serviceWorker' in navigator"), "使えない環境では、何もしない");
    }

    // ---- つながらないときの案内ページ ----

    @Test
    void offlinePageIsStaticAndHoldsNothingPersonal() throws IOException {
        String page = read(WEBAPP.resolve("offline.html"));

        assertFalse(page.contains("<script"), "案内ページに、スクリプトは要らない（CSP も許さない）");
        assertFalse(page.contains("<form") || page.contains("<input") || page.contains("csrf"), "トークンも、入力も持たない");
        assertFalse(page.contains("${") || page.contains("<%"), "テンプレートで作らない（作ると、利用者ごとの内容が入りうる）");
        assertTrue(page.contains("<link rel=\"stylesheet\" href=\"/css/yurune.css\">"));
        assertTrue(page.contains("つらさが続くときは、医師に相談してください。<br>ゆるねは医療機器ではありません。"), "注意文は、常に出す");
        assertTrue(page.contains("lang=\"ja\""));
    }

    // ---- Service Worker ----

    @Test
    void serviceWorkerCachesOnlyTheListedStaticFilesThatExistAndAreOpenToEveryone() throws IOException {
        String script = stripComments(read(WEBAPP.resolve("sw.js")));
        List<String> paths = readStaticPaths(script);

        assertTrue(paths.contains("offline.html"));
        assertTrue(script.contains("var OFFLINE_PATH = 'offline.html';"));
        for (String path : paths) {
            assertTrue(Files.isRegularFile(WEBAPP.resolve(path)), path + " がない");
            String extension = path.substring(path.lastIndexOf('.') + 1);
            assertTrue(STATIC_FILE_EXTENSIONS.contains(extension), path + " は、画面（HTML）や API ではなく、固定の資源にする");
            assertTrue(REQUEST_POLICY.isPublicPath("/" + path),
                    path + " は、ログインなしで取れなければ、取る側がログイン画面へ飛ばされる");
        }
        long pages = paths.stream().filter(path -> path.endsWith(".html")).count();
        assertEquals(1, pages, "HTML は、案内ページの 1 つだけ");
    }

    @Test
    void serviceWorkerNeverMentionsAnyScreenOrApiOfTheApp() throws IOException {
        String script = stripComments(read(WEBAPP.resolve("sw.js")));
        Set<String> personalPaths = readPersonalPaths();
        assertTrue(personalPaths.contains("/home") && personalPaths.contains("/night/observe"), "定数から画面の経路を読めている");

        Matcher literals = STRING_LITERAL.matcher(script);
        while (literals.find()) {
            String literal = "/" + literals.group(1).replaceFirst("^/+", "");
            for (String personal : personalPaths) {
                assertFalse(literal.equals(personal) || literal.startsWith(personal + "/"),
                        "sw.js が、利用者ごとの経路 " + personal + " に触れている: " + literals.group(1));
            }
        }
    }

    @Test
    void serviceWorkerWritesToTheCacheOnlyThroughOneGuardedFunction() throws IOException {
        String script = stripComments(read(WEBAPP.resolve("sw.js")));
        int start = script.indexOf("function saveStatic(");
        String guard = script.substring(start, script.indexOf("\n}\n", start));

        assertEquals(1, countOccurrences(script, ".put("), "キャッシュへ書く所は、1 つだけ");
        assertFalse(script.contains(".add(") || script.contains(".addAll("), "put 以外の書き込みは使わない");
        assertTrue(guard.contains("STATIC_URLS.indexOf(url) >= 0"), "一覧にある URL だけ");
        assertTrue(guard.contains("response.ok") && guard.contains("!response.redirected") && guard.contains("response.type === 'basic'"),
                "失敗の応答、転送された応答（ログイン画面など）、別のサイトの応答は、入れない");
    }

    @Test
    void serviceWorkerLeavesEveryNonGetRequestAndEveryApiCallToTheNetwork() throws IOException {
        String script = stripComments(read(WEBAPP.resolve("sw.js")));
        int handler = script.indexOf("self.addEventListener('fetch'");
        String body = script.substring(handler);

        assertEquals(2, countOccurrences(script, "respondWith("), "手を出すのは、ページを開くことと、案内ページの CSS だけ");
        assertTrue(body.indexOf("request.method !== 'GET'") > 0);
        assertTrue(body.indexOf("request.method !== 'GET'") < body.indexOf("respondWith("), "POST などは、先に外す");
        assertTrue(body.contains("request.mode === 'navigate'"));
        assertTrue(body.contains("STATIC_URLS.indexOf(request.url) >= 0"), "CSS は、一覧にあるものだけ");
    }

    @Test
    void serviceWorkerUsesNoPushNoBackgroundSyncAndNoOtherStorage() throws IOException {
        String script = stripComments(read(WEBAPP.resolve("sw.js")));

        for (String word : List.of("'push'", "showNotification", "'sync'", "periodicsync", "indexedDB", "localStorage", "importScripts",
                "XMLHttpRequest", "postMessage")) {
            assertFalse(script.contains(word), "sw.js に " + word + " がある（通知、同期、別の保存先、外の読み込みは使わない）");
        }
    }

    @Test
    void serviceWorkerReplacesOldVersionsAndRemovesOnlyItsOwnCaches() throws IOException {
        String script = stripComments(read(WEBAPP.resolve("sw.js")));

        assertTrue(script.contains("var CACHE_NAME = CACHE_PREFIX + CACHE_VERSION;"), "版が、キャッシュ名に入る");
        assertTrue(script.contains("self.skipWaiting()"));
        assertTrue(script.contains("self.clients.claim()"));
        assertTrue(script.contains("name.indexOf(CACHE_PREFIX) === 0 && name !== CACHE_NAME"), "自分の名前の、古い版だけを消す");
        assertTrue(script.contains("caches.delete(name)"));
    }

    @Test
    void serviceWorkerIsServedFromTheAppRootSoItsScopeCoversTheWholeApp() {
        assertEquals("/sw.js", PwaConstants.PATH_SERVICE_WORKER);
        assertTrue(Files.isRegularFile(WEBAPP.resolve("sw.js")), "ルート直下なら、Service-Worker-Allowed を足さなくても、全体が対象になる");
        assertTrue(PwaConstants.ROOT_FILE_PATHS.containsAll(
                List.of(PwaConstants.PATH_MANIFEST, PwaConstants.PATH_SERVICE_WORKER, PwaConstants.PATH_OFFLINE)));
    }
}
