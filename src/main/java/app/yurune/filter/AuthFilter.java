package app.yurune.filter;

import app.yurune.AppComponents;
import app.yurune.constant.AuthConstants;
import app.yurune.constant.ViewConstants;
import app.yurune.controller.SessionUsers;
import app.yurune.domain.value.AuthenticatedUser;
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
import java.util.Optional;

/**
 * ログインしていない要求を、画面や操作に通さない。
 * 画面の表示（GET）は、ログイン画面へ移し、それ以外（操作、観測の送信）は、401 で断る。
 * 通すのは、ログイン・登録・規約・稼働の確認と、静的なファイルだけ。
 */
public final class AuthFilter implements Filter {

    private WebRequestPolicy requestPolicy;

    @Override
    public void init(FilterConfig filterConfig) {
        AppComponents components = (AppComponents) filterConfig.getServletContext()
                .getAttribute(ViewConstants.ATTR_COMPONENTS);
        this.requestPolicy = components.getWebRequestPolicy();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        Optional<AuthenticatedUser> user = SessionUsers.find(httpRequest);
        if (user.isPresent() && user.get().isDemo()) {
            /* 審査員用のデモアカウントは、どの画面にも「デモ」と出す（デモ用のデータであることを、画面で明記するため） */
            httpRequest.setAttribute(ViewConstants.ATTR_DEMO_ACCOUNT, Boolean.TRUE);
        }
        if (requestPolicy.isPublicPath(httpRequest.getServletPath()) || user.isPresent()) {
            chain.doFilter(request, response);
            return;
        }
        if ("GET".equals(httpRequest.getMethod())) {
            httpResponse.sendRedirect(httpRequest.getContextPath() + AuthConstants.PATH_LOGIN);
        } else {
            httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
}
