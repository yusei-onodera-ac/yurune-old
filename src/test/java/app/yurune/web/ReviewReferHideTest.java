package app.yurune.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.AppComponents;
import app.yurune.config.AppConfig;
import app.yurune.config.DeploymentConfig;
import app.yurune.constant.NoteSummaryConstants;
import app.yurune.constant.SecurityConstants;
import app.yurune.constant.ViewConstants;
import app.yurune.controller.Navigation;
import app.yurune.domain.value.AuthenticatedUser;
import app.yurune.filter.CsrfFilter;
import app.yurune.service.MutableClock;

import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 「今は表示しない」（受診のすすめを、しばらく出さない）。POST だけで、ログインと CSRF トークンが要る。
 * 押すと、ふりかえりの「目標」に戻る。受診のすすめが出ていないときは、何も変えない。
 */
class ReviewReferHideTest {

    private final MutableClock clock = new MutableClock("2026-10-07T13:00:00Z", "Asia/Tokyo");
    private final AuthenticatedUser alice = new AuthenticatedUser("alice", "alice@example.com", false);
    private AppComponents components;

    @BeforeEach
    void setUp() {
        components = AppComponents.create(new DeploymentConfig(false), clock, AppConfig.defaults(), line -> { });
    }

    @Test
    void thePathNeedsLoginAndIsNotPublic() {
        assertFalse(components.getWebRequestPolicy().isPublicPath(NoteSummaryConstants.PATH_REFER_HIDE));
    }

    @Test
    void postingWithoutTheCsrfTokenIsRefused() throws Exception {
        ServletContext context = FakeHttp.proxy(ServletContext.class, (self, called, args) ->
                "getAttribute".equals(called.getName()) && ViewConstants.ATTR_COMPONENTS.equals(args[0]) ? components : null);
        CsrfFilter filter = new CsrfFilter();
        filter.init(FakeHttp.proxy(FilterConfig.class, (self, called, args) ->
                "getServletContext".equals(called.getName()) ? context : null));

        FakeHttp.Exchange withoutToken = new FakeHttp.Exchange();
        withoutToken.method = "POST";
        withoutToken.servletPath = NoteSummaryConstants.PATH_REFER_HIDE;
        filter.doFilter(withoutToken.request(), withoutToken.response(), withoutToken.chain());

        assertEquals(403, withoutToken.errorStatus);
        assertEquals(0, withoutToken.chainCalls.get());

        FakeHttp.Exchange withToken = new FakeHttp.Exchange();
        withToken.method = "POST";
        withToken.servletPath = NoteSummaryConstants.PATH_REFER_HIDE;
        String token = components.getCsrfTokenService().issueToken();
        withToken.sessionAttributes.put(SecurityConstants.SESSION_CSRF_TOKEN, token);
        withToken.requestHeaders.put(SecurityConstants.CSRF_HEADER, token);
        filter.doFilter(withToken.request(), withToken.response(), withToken.chain());

        assertEquals(1, withToken.chainCalls.get());
    }

    @Test
    void hidingGoesBackToTheGoalPanelOfTheReviewScreen() {
        Navigation navigation = components.forUser(alice).getReviewController().hideReferral();

        assertTrue(navigation.isRedirect());
        assertEquals(ViewConstants.PATH_REVIEW + "?" + NoteSummaryConstants.PARAM_PANEL + "=" + NoteSummaryConstants.PANEL_GOAL,
                navigation.getTarget());
    }

    @Test
    void hidingWhenNoReferralIsShownChangesNothingAndStaysSilentNextTime() {
        components.forUser(alice).getReviewController().hideReferral();

        FakeHttp.Exchange exchange = new FakeHttp.Exchange();
        components.forUser(alice).getReviewController().showReview(exchange.request());

        assertEquals(Boolean.FALSE, exchange.requestAttributes.get(NoteSummaryConstants.ATTR_REFER_SHOWN));
    }
}
