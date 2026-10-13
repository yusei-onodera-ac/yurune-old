package app.yurune.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.AppComponents;
import app.yurune.config.DeploymentConfig;
import app.yurune.constant.PwaConstants;
import app.yurune.constant.SecurityConstants;
import app.yurune.constant.ViewConstants;
import app.yurune.filter.AuthFilter;
import app.yurune.filter.CsrfFilter;
import app.yurune.filter.SecurityHeadersFilter;
import app.yurune.service.SecurityHeaderPolicy;
import app.yurune.service.WebRequestPolicy;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * ホーム画面に追加するための固定ファイル（マニフェスト、Service Worker、案内ページ、アイコン）の届け方:
 * ログインなしで取れること、セッションを作らないこと、キャッシュの指示、CSP を緩めていないこと。
 */
class PwaDeliveryTest {

    private static final List<String> PWA_FILES = List.of(PwaConstants.PATH_MANIFEST, PwaConstants.PATH_SERVICE_WORKER,
            PwaConstants.PATH_OFFLINE);
    private static final List<String> ICON_FILES = List.of("/images/icons/icon-192.png", "/images/icons/icon-512.png",
            "/images/icons/icon-maskable-512.png", "/images/icons/apple-touch-icon.png");
    private static final List<String> PERSONAL_PATHS = List.of("/", "/home", "/log", "/prepare", "/night", "/night/mode",
            "/night/end", "/night/observe", "/night/talk", "/review", "/settings", "/settings/erase", "/morning", "/logout");

    private final WebRequestPolicy requestPolicy = new WebRequestPolicy();
    private AuthFilter authFilter;
    private CsrfFilter csrfFilter;
    private SecurityHeadersFilter headersFilter;

    @BeforeEach
    void setUp() {
        AppComponents components = AppComponents.create(new DeploymentConfig(false));
        ServletContext context = FakeHttp.proxy(ServletContext.class, (self, called, args) ->
                "getAttribute".equals(called.getName()) && ViewConstants.ATTR_COMPONENTS.equals(args[0]) ? components : null);
        FilterConfig config = FakeHttp.proxy(FilterConfig.class, (self, called, args) ->
                "getServletContext".equals(called.getName()) ? context : null);
        authFilter = new AuthFilter();
        authFilter.init(config);
        csrfFilter = new CsrfFilter();
        csrfFilter.init(config);
        headersFilter = new SecurityHeadersFilter();
        headersFilter.init(config);
    }

    private FakeHttp.Exchange pass(String path, Filter filter) throws IOException, ServletException {
        FakeHttp.Exchange exchange = new FakeHttp.Exchange();
        exchange.servletPath = path;
        filter.doFilter(exchange.request(), exchange.response(), exchange.chain());
        return exchange;
    }

    // ---- ログインなしで取れる ----

    @Test
    void manifestServiceWorkerOfflinePageAndIconsAreOpenWithoutLogin() throws IOException, ServletException {
        for (String path : concat(PWA_FILES, ICON_FILES)) {
            FakeHttp.Exchange exchange = pass(path, authFilter);

            assertEquals(1, exchange.chainCalls.get(), path);
            assertNull(exchange.redirectedTo, path);
        }
    }

    @Test
    void screensAndApisStayBehindLogin() throws IOException, ServletException {
        for (String path : PERSONAL_PATHS) {
            FakeHttp.Exchange exchange = pass(path, authFilter);

            assertEquals(0, exchange.chainCalls.get(), path);
            assertFalse(requestPolicy.isPublicPath(path), path);
        }
    }

    @Test
    void lookAlikePathsAreNotOpenedByTheNewFiles() {
        for (String path : List.of("/sw.js/x", "/sw.jsp", "/SW.JS", "/manifest.webmanifest/", "/offline.html.bak", "/offline",
                "/sw.js;jsessionid=1", "//sw.js")) {
            assertFalse(requestPolicy.isStaticPath(path), path);
            assertFalse(requestPolicy.isPublicPath(path), path);
        }
    }

    @Test
    void fetchingThemNeverCreatesASessionOrAToken() throws IOException, ServletException {
        for (String path : concat(PWA_FILES, ICON_FILES)) {
            FakeHttp.Exchange exchange = pass(path, csrfFilter);

            assertTrue(exchange.sessionAttributes.isEmpty(), path + " が、セッションを作った（更新の確認のたびに作られてしまう）");
            assertNull(exchange.requestAttributes.get(SecurityConstants.ATTR_CSRF_TOKEN), path);
            assertEquals(1, exchange.chainCalls.get(), path);
        }
    }

    // ---- キャッシュの指示 ----

    @Test
    void theFixedFilesMustBeRevalidatedSoAnUpdateIsNotDelayed() throws IOException, ServletException {
        for (String path : PWA_FILES) {
            assertEquals("no-cache", pass(path, headersFilter).responseHeaders.get("Cache-Control"), path);
        }
    }

    @Test
    void personalPagesAndApisAreStillNeverStored() throws IOException, ServletException {
        for (String path : PERSONAL_PATHS) {
            assertEquals("no-store", pass(path, headersFilter).responseHeaders.get("Cache-Control"), path);
        }
        assertEquals("no-store", pass("/login", headersFilter).responseHeaders.get("Cache-Control"), "ログイン画面にはトークンがある");
    }

    @Test
    void staticAssetsAndIconsKeepTheUnspecifiedCacheControl() throws IOException, ServletException {
        for (String path : concat(List.of("/css/yurune.css", "/js/yurune.js"), ICON_FILES)) {
            assertNull(pass(path, headersFilter).responseHeaders.get("Cache-Control"), path);
        }
    }

    @Test
    void everyResponseKeepsTheSecurityHeadersIncludingTheFixedFiles() throws IOException, ServletException {
        for (String path : concat(PWA_FILES, ICON_FILES)) {
            FakeHttp.Exchange exchange = pass(path, headersFilter);

            assertTrue(exchange.responseHeaders.get("Content-Security-Policy").contains("script-src 'self';"), path);
            assertEquals("nosniff", exchange.responseHeaders.get("X-Content-Type-Options"), path);
        }
    }

    // ---- CSP ----

    @Test
    void cspAllowsTheManifestAndTheWorkerOnlyFromOurOwnDomainWithoutLooseningScripts() {
        List<String> directives = List.of(
                new SecurityHeaderPolicy().createHeaders(false).get(SecurityConstants.HEADER_CSP).split("; "));

        assertTrue(directives.contains("manifest-src 'self'"));
        assertTrue(directives.contains("worker-src 'self'"), "blob: や外のドメインの Worker は許さない");
        assertTrue(directives.contains("script-src 'self'"), "インラインも eval も許さない");
        assertTrue(directives.contains("connect-src 'self'"), "Service Worker の通信は、自分のドメインだけ");
        assertTrue(directives.contains("default-src 'none'"));
        assertFalse(directives.stream().anyMatch(directive -> directive.startsWith("script-src") && directive.contains("unsafe")));
    }

    // ---- 配信 ----

    @Test
    void deploymentDescriptorServesTheManifestAsManifestJson() throws IOException {
        String xml = Files.readString(Path.of("src", "main", "webapp", "WEB-INF", "web.xml"), StandardCharsets.UTF_8);
        int mapping = xml.indexOf("<extension>webmanifest</extension>");

        assertTrue(mapping > 0, "Tomcat の既定には、この拡張子がない");
        assertTrue(xml.indexOf("<mime-type>application/manifest+json</mime-type>", mapping) > mapping);
    }

    private static List<String> concat(List<String> first, List<String> second) {
        return Stream.concat(first.stream(), second.stream()).toList();
    }
}
