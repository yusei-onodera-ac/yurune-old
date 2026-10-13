package app.yurune.servlet;

import app.yurune.AppComponents;
import app.yurune.UserComponents;
import app.yurune.constant.ViewConstants;
import app.yurune.controller.BadRequestException;
import app.yurune.controller.Navigation;
import app.yurune.controller.SessionUsers;
import app.yurune.domain.value.AuthenticatedUser;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 各画面の入口に共通の処理（部品の取り出し、表示、移動、入力エラーの返し方）。
 * URL と Controller を結ぶだけで、画面ごとの処理は持たない。
 */
public abstract class BaseServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    protected AppComponents getComponents() {
        return (AppComponents) getServletContext().getAttribute(ViewConstants.ATTR_COMPONENTS);
    }

    /** ログイン中の利用者の部品。ログインしていない要求は、手前の Filter が止めるので、ここには届かない。 */
    protected UserComponents getUserComponents(HttpServletRequest request) {
        AuthenticatedUser user = SessionUsers.find(request)
                .orElseThrow(() -> new IllegalStateException("request reached a servlet without a signed-in user"));
        return getComponents().forUser(user);
    }

    /** Controller が決めた行き先（画面の表示か、移動か）に従う。 */
    protected void navigate(HttpServletRequest request, HttpServletResponse response, Navigation navigation)
            throws ServletException, IOException {
        if (navigation.isRedirect()) {
            redirect(request, response, navigation.getTarget());
        } else {
            forward(request, response, navigation.getTarget());
        }
    }

    protected void forward(HttpServletRequest request, HttpServletResponse response, String viewPath)
            throws ServletException, IOException {
        request.getRequestDispatcher(viewPath).forward(request, response);
    }

    /** 保存のあと、同じ操作を二重に送らないよう、画面へ移動させる。 */
    protected void redirect(HttpServletRequest request, HttpServletResponse response, String path)
            throws IOException {
        response.sendRedirect(request.getContextPath() + path);
    }

    protected void sendBadRequest(HttpServletResponse response, BadRequestException exception)
            throws IOException {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, exception.getMessage());
    }
}
