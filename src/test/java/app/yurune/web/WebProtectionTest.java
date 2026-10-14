package app.yurune.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.AppComponents;
import app.yurune.config.DeploymentConfig;
import app.yurune.constant.SecurityConstants;
import app.yurune.constant.ViewConstants;
import app.yurune.filter.CsrfFilter;
import app.yurune.filter.SecurityHeadersFilter;
import app.yurune.service.CsrfTokenService;
import app.yurune.service.SecurityHeaderPolicy;
import app.yurune.service.WebRequestPolicy;

import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 画面の守り: セキュリティヘッダー、CSRF トークン、同じサイトからの操作だけを受けること。
 */
class WebProtectionTest {

    private static final String HOST = "yurune.example";

    private final SecurityHeaderPolicy headerPolicy = new SecurityHeaderPolicy();
    private final CsrfTokenService tokenService = new CsrfTokenService();
    private final WebRequestPolicy requestPolicy = new WebRequestPolicy();
    private CsrfFilter csrfFilter;
    private SecurityHeadersFilter headersFilter;

    @BeforeEach
    void setUp() {
        AppComponents components = AppComponents.create(new DeploymentConfig(false));
        ServletContext context = FakeHttp.proxy(ServletContext.class, (self, called, args) ->
                "getAttribute".equals(called.getName()) && ViewConstants.ATTR_COMPONENTS.equals(args[0]) ? components : null);
        FilterConfig config = FakeHttp.proxy(FilterConfig.class, (self, called, args) ->
                "getServletContext".equals(called.getName()) ? context : null);
        csrfFilter = new CsrfFilter();
        csrfFilter.init(config);
        headersFilter = new SecurityHeadersFilter();
        headersFilter.init(config);
    }

    // ---- ヘッダー ----

    @Test
    void cspAllowsScriptsOnlyFromOurOwnDomainAndNoInlineScripts() {
        String csp = headerPolicy.createHeaders(false).get(SecurityConstants.HEADER_CSP);

        assertTrue(csp.contains("script-src 'self';"));
        assertFalse(csp.contains("script-src 'self' 'unsafe-inline'"));
        assertFalse(csp.contains("'unsafe-eval'"));
        assertTrue(csp.contains("default-src 'none'"));
        assertTrue(csp.contains("frame-ancestors 'none'"));
        assertTrue(csp.contains("form-action 'self'"));
        assertTrue(csp.contains("base-uri 'none'"));
        assertTrue(csp.contains("img-src 'self' data:"), "外部の画像は許さない");
        assertTrue(csp.contains("font-src https://fonts.gstatic.com"));
        assertTrue(csp.contains("style-src 'self' https://fonts.googleapis.com;"));
    }

    @Test
    void allResponsesCarryTheSecurityHeaders() throws IOException, ServletException {
        FakeHttp.Exchange exchange = new FakeHttp.Exchange();

        headersFilter.doFilter(exchange.request(), exchange.response(), exchange.chain());

        Map<String, String> headers = exchange.responseHeaders;
        assertEquals("nosniff", headers.get("X-Content-Type-Options"));
        assertEquals("DENY", headers.get("X-Frame-Options"));
        assertEquals("same-origin", headers.get("Referrer-Policy"));
        assertNotNull(headers.get("Content-Security-Policy"));
        assertTrue(headers.get("Permissions-Policy").contains("microphone=(self)"));
        assertEquals("no-store", headers.get("Cache-Control"), "利用者のデータを含む画面は、残さない");
        assertEquals(1, exchange.chainCalls.get());
    }

    @Test
    void staticFilesMayBeCachedAndHstsIsOnlySentOverHttps() throws IOException, ServletException {
        FakeHttp.Exchange http = new FakeHttp.Exchange();
        http.servletPath = "/images/icons/icon-192.png";
        headersFilter.doFilter(http.request(), http.response(), http.chain());
        assertNull(http.responseHeaders.get("Cache-Control"));
        assertNull(http.responseHeaders.get("Strict-Transport-Security"));

        FakeHttp.Exchange forwarded = new FakeHttp.Exchange();
        forwarded.requestHeaders.put("X-Forwarded-Proto", "https");
        headersFilter.doFilter(forwarded.request(), forwarded.response(), forwarded.chain());
        assertTrue(forwarded.responseHeaders.get("Strict-Transport-Security").contains("max-age="));
    }

    // ---- CSRF ----

    @Test
    void tokensAreRandomAndComparedExactly() {
        String first = tokenService.issueToken();
        String second = tokenService.issueToken();

        assertNotEquals(first, second);
        assertTrue(first.length() >= 43, "32 バイト以上の乱数");
        assertTrue(tokenService.matches(first, first));
        assertFalse(tokenService.matches(first, second));
        assertFalse(tokenService.matches(first, null));
        assertFalse(tokenService.matches(null, null));
        assertFalse(tokenService.matches("", ""));
    }

    @Test
    void aPageViewGetsATokenForItsForms() throws IOException, ServletException {
        FakeHttp.Exchange exchange = new FakeHttp.Exchange();

        csrfFilter.doFilter(exchange.request(), exchange.response(), exchange.chain());

        Object token = exchange.requestAttributes.get(SecurityConstants.ATTR_CSRF_TOKEN);
        assertNotNull(token);
        assertEquals(token, exchange.sessionAttributes.get(SecurityConstants.SESSION_CSRF_TOKEN));
        assertEquals(1, exchange.chainCalls.get());
    }

    @Test
    void aPostWithoutTheTokenIsRejected() throws IOException, ServletException {
        FakeHttp.Exchange exchange = new FakeHttp.Exchange();
        exchange.method = "POST";
        exchange.servletPath = "/night/end";

        csrfFilter.doFilter(exchange.request(), exchange.response(), exchange.chain());

        assertEquals(403, exchange.errorStatus);
        assertEquals(0, exchange.chainCalls.get());
    }

    @Test
    void aPostWithAWrongTokenIsRejected() throws IOException, ServletException {
        FakeHttp.Exchange exchange = new FakeHttp.Exchange();
        exchange.method = "POST";
        exchange.sessionAttributes.put(SecurityConstants.SESSION_CSRF_TOKEN, tokenService.issueToken());
        exchange.parameters.put(SecurityConstants.CSRF_PARAM, tokenService.issueToken());

        csrfFilter.doFilter(exchange.request(), exchange.response(), exchange.chain());

        assertEquals(403, exchange.errorStatus);
    }

    @Test
    void aPostWithTheRightTokenInAFormOrAHeaderIsAccepted() throws IOException, ServletException {
        String token = tokenService.issueToken();

        FakeHttp.Exchange form = new FakeHttp.Exchange();
        form.method = "POST";
        form.sessionAttributes.put(SecurityConstants.SESSION_CSRF_TOKEN, token);
        form.parameters.put(SecurityConstants.CSRF_PARAM, token);
        csrfFilter.doFilter(form.request(), form.response(), form.chain());
        assertEquals(1, form.chainCalls.get());

        FakeHttp.Exchange fetch = new FakeHttp.Exchange();
        fetch.method = "POST";
        fetch.sessionAttributes.put(SecurityConstants.SESSION_CSRF_TOKEN, token);
        fetch.requestHeaders.put(SecurityConstants.CSRF_HEADER, token);
        csrfFilter.doFilter(fetch.request(), fetch.response(), fetch.chain());
        assertEquals(1, fetch.chainCalls.get());
    }

    @Test
    void aPostFromAnotherSiteIsRejectedEvenWithTheToken() throws IOException, ServletException {
        String token = tokenService.issueToken();
        FakeHttp.Exchange exchange = new FakeHttp.Exchange();
        exchange.method = "POST";
        exchange.sessionAttributes.put(SecurityConstants.SESSION_CSRF_TOKEN, token);
        exchange.parameters.put(SecurityConstants.CSRF_PARAM, token);
        exchange.requestHeaders.put("Host", HOST);
        exchange.requestHeaders.put("Origin", "https://evil.example");

        csrfFilter.doFilter(exchange.request(), exchange.response(), exchange.chain());

        assertEquals(403, exchange.errorStatus);
        assertEquals(0, exchange.chainCalls.get());
    }

    @Test
    void getRequestsNeverNeedTheToken() throws IOException, ServletException {
        FakeHttp.Exchange exchange = new FakeHttp.Exchange();
        exchange.servletPath = "/review";

        csrfFilter.doFilter(exchange.request(), exchange.response(), exchange.chain());

        assertEquals(0, exchange.errorStatus);
        assertEquals(1, exchange.chainCalls.get());
    }

    @Test
    void staticFilesDoNotCreateASession() throws IOException, ServletException {
        FakeHttp.Exchange exchange = new FakeHttp.Exchange();
        exchange.servletPath = "/js/yurune.js";

        csrfFilter.doFilter(exchange.request(), exchange.response(), exchange.chain());

        assertTrue(exchange.sessionAttributes.isEmpty());
        assertEquals(1, exchange.chainCalls.get());
    }

    @Test
    void healthCheckPathsDoNotCreateASession() throws IOException, ServletException {
        FakeHttp.Exchange health = new FakeHttp.Exchange();
        health.servletPath = "/healthz";
        csrfFilter.doFilter(health.request(), health.response(), health.chain());
        assertTrue(health.sessionAttributes.isEmpty());
        assertEquals(1, health.chainCalls.get());

        FakeHttp.Exchange ready = new FakeHttp.Exchange();
        ready.servletPath = "/ready";
        csrfFilter.doFilter(ready.request(), ready.response(), ready.chain());
        assertTrue(ready.sessionAttributes.isEmpty());
        assertEquals(1, ready.chainCalls.get());
    }

    @Test
    void aNormalPageStillGetsASessionAndAToken() throws IOException, ServletException {
        FakeHttp.Exchange exchange = new FakeHttp.Exchange();
        exchange.servletPath = "/login";

        csrfFilter.doFilter(exchange.request(), exchange.response(), exchange.chain());

        assertFalse(exchange.sessionAttributes.isEmpty(), "通常の画面は、これまでどおりトークンを出す");
        assertEquals(1, exchange.chainCalls.get());
    }

    @Test
    void originChecksUseTheHostAndTheFetchMetadata() {
        assertTrue(requestPolicy.isSameOrigin("https://yurune.example", HOST, null));
        assertTrue(requestPolicy.isSameOrigin("http://localhost:18080", "localhost:18080", "same-origin"));
        assertTrue(requestPolicy.isSameOrigin(null, HOST, null));
        assertFalse(requestPolicy.isSameOrigin("https://evil.example", HOST, null));
        assertFalse(requestPolicy.isSameOrigin("https://yurune.example", HOST, "cross-site"));
        assertFalse(requestPolicy.isSameOrigin("not a uri", HOST, null));
        assertFalse(requestPolicy.isSameOrigin("null", HOST, null), "Origin: null は、別のサイトからも作れるので、信用しない");
    }
}
