package app.yurune.filter;

import app.yurune.AppComponents;
import app.yurune.constant.SecurityConstants;
import app.yurune.constant.ViewConstants;
import app.yurune.service.CsrfTokenService;
import app.yurune.service.WebRequestPolicy;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * 状態を変える操作（POST など）に、CSRF トークンを要求する。
 * トークンはセッションごとに 1 つ。画面には、request の属性で渡す（フォームと fetch が使う）。
 * 加えて、別のサイトから送られた操作（Origin、Sec-Fetch-Site）は、トークンの前に断る。
 */
public final class CsrfFilter implements Filter {

    private static final String HEADER_ORIGIN = "Origin";
    private static final String HEADER_HOST = "Host";
    private static final String HEADER_FETCH_SITE = "Sec-Fetch-Site";

    private CsrfTokenService tokenService;
    private WebRequestPolicy requestPolicy;

    @Override
    public void init(FilterConfig filterConfig) {
        AppComponents components = (AppComponents) filterConfig.getServletContext()
                .getAttribute(ViewConstants.ATTR_COMPONENTS);
        this.tokenService = components.getCsrfTokenService();
        this.requestPolicy = components.getWebRequestPolicy();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getServletPath();
        if (requestPolicy.isStaticPath(path) || requestPolicy.isHealthCheckPath(path)) {
            chain.doFilter(request, response);
            return;
        }
        String token = findOrIssueToken(httpRequest);
        httpRequest.setAttribute(SecurityConstants.ATTR_CSRF_TOKEN, token);

        if (requestPolicy.isStateChanging(httpRequest.getMethod()) && !isAllowed(httpRequest, token)) {
            httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }

    private String findOrIssueToken(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        Object stored = session.getAttribute(SecurityConstants.SESSION_CSRF_TOKEN);
        if (stored instanceof String existing) {
            return existing;
        }
        String issued = tokenService.issueToken();
        session.setAttribute(SecurityConstants.SESSION_CSRF_TOKEN, issued);
        return issued;
    }

    private boolean isAllowed(HttpServletRequest request, String token) {
        boolean sameOrigin = requestPolicy.isSameOrigin(request.getHeader(HEADER_ORIGIN),
                request.getHeader(HEADER_HOST), request.getHeader(HEADER_FETCH_SITE));
        if (!sameOrigin) {
            return false;
        }
        String provided = request.getHeader(SecurityConstants.CSRF_HEADER);
        if (provided == null) {
            provided = request.getParameter(SecurityConstants.CSRF_PARAM);
        }
        return tokenService.matches(token, provided);
    }
}
