package app.yurune.filter;

import app.yurune.AppComponents;
import app.yurune.constant.SecurityConstants;
import app.yurune.constant.ViewConstants;
import app.yurune.service.SecurityHeaderPolicy;
import app.yurune.service.WebRequestPolicy;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

/**
 * すべての応答に、セキュリティヘッダーを付ける（エラー画面にも付く）。
 * 何を付けるかは {@link SecurityHeaderPolicy} が決める。ここは、付けるだけ。
 */
public final class SecurityHeadersFilter implements Filter {

    private SecurityHeaderPolicy headerPolicy;
    private WebRequestPolicy requestPolicy;

    @Override
    public void init(FilterConfig filterConfig) {
        AppComponents components = (AppComponents) filterConfig.getServletContext()
                .getAttribute(ViewConstants.ATTR_COMPONENTS);
        this.headerPolicy = components.getSecurityHeaderPolicy();
        this.requestPolicy = components.getWebRequestPolicy();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        boolean secure = httpRequest.isSecure()
                || SecurityConstants.FORWARDED_PROTO_HTTPS.equalsIgnoreCase(
                        httpRequest.getHeader(SecurityConstants.FORWARDED_PROTO_HEADER));
        for (Map.Entry<String, String> header : headerPolicy.createHeaders(secure).entrySet()) {
            httpResponse.setHeader(header.getKey(), header.getValue());
        }
        requestPolicy.findCacheControl(httpRequest.getServletPath())
                .ifPresent(value -> httpResponse.setHeader(SecurityConstants.HEADER_CACHE_CONTROL, value));
        chain.doFilter(request, response);
    }
}
