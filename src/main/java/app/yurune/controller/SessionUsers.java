package app.yurune.controller;

import app.yurune.constant.AuthConstants;
import app.yurune.constant.SecurityConstants;
import app.yurune.domain.value.AuthenticatedUser;
import app.yurune.service.CsrfTokenService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.Optional;

/**
 * セッションに入れた、ログイン中の利用者の出し入れ。
 * ログインのときは、セッション ID と CSRF トークンを作り直す（セッション固定の攻撃を防ぐ）。
 */
public final class SessionUsers {

    private SessionUsers() {
    }

    public static Optional<AuthenticatedUser> find(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(AuthConstants.SESSION_USER) instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static void signIn(HttpServletRequest request, AuthenticatedUser user, CsrfTokenService tokenService) {
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute(AuthConstants.SESSION_USER, user);
        session.setAttribute(SecurityConstants.SESSION_CSRF_TOKEN, tokenService.issueToken());
    }

    public static void signOut(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
