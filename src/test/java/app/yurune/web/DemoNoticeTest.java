package app.yurune.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.AppComponents;
import app.yurune.config.AppConfig;
import app.yurune.config.DeploymentConfig;
import app.yurune.constant.AuthConstants;
import app.yurune.constant.ViewConstants;
import app.yurune.domain.value.AuthenticatedUser;
import app.yurune.filter.AuthFilter;
import app.yurune.service.MutableClock;

import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 審査員用のデモアカウントには、どの画面にも「デモ」と明記する。ほかの利用者には出さない。
 */
class DemoNoticeTest {

    private static final Path PAGE_TAG = Path.of("src", "main", "webapp", "WEB-INF", "tags", "page.tag");

    private final MutableClock clock = new MutableClock("2026-10-12T13:00:00Z", "Asia/Tokyo");
    private AuthFilter filter;

    @BeforeEach
    void setUp() {
        AppComponents components = AppComponents.create(new DeploymentConfig(false), clock, AppConfig.defaults(), line -> { });
        ServletContext context = FakeHttp.proxy(ServletContext.class, (self, called, args) ->
                "getAttribute".equals(called.getName()) && ViewConstants.ATTR_COMPONENTS.equals(args[0]) ? components : null);
        filter = new AuthFilter();
        filter.init(FakeHttp.proxy(FilterConfig.class, (self, called, args) ->
                "getServletContext".equals(called.getName()) ? context : null));
    }

    private FakeHttp.Exchange visitAs(AuthenticatedUser user) throws Exception {
        FakeHttp.Exchange exchange = new FakeHttp.Exchange();
        exchange.sessionAttributes.put(AuthConstants.SESSION_USER, user);
        filter.doFilter(exchange.request(), exchange.response(), exchange.chain());
        return exchange;
    }

    @Test
    void theDemoAccountIsMarkedOnEveryScreen() throws Exception {
        FakeHttp.Exchange exchange = visitAs(new AuthenticatedUser("uid-demo", "demo@example.com", true));

        assertEquals(1, exchange.chainCalls.get());
        assertEquals(Boolean.TRUE, exchange.requestAttributes.get(ViewConstants.ATTR_DEMO_ACCOUNT));
    }

    @Test
    void anOrdinaryUserIsNotMarked() throws Exception {
        FakeHttp.Exchange exchange = visitAs(new AuthenticatedUser("uid-alice", "alice@example.com", false));

        assertEquals(1, exchange.chainCalls.get());
        assertNull(exchange.requestAttributes.get(ViewConstants.ATTR_DEMO_ACCOUNT));
    }

    @Test
    void theScreenFrameShowsTheDemoNoticeOnlyForTheDemoAccount() throws IOException {
        String frame = Files.readString(PAGE_TAG, StandardCharsets.UTF_8);

        assertTrue(frame.contains("test=\"${demoAccount}\""));
        assertTrue(frame.contains("審査員用のデモアカウントです。"));
    }
}
