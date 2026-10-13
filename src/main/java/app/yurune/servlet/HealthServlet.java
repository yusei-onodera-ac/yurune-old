package app.yurune.servlet;

import app.yurune.constant.AuthConstants;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 稼働の確認（ログイン不要）。中身は何も返さず、動いていることだけを伝える（Cloud Run の確認や、負荷の試験に使う）。
 */
@WebServlet(AuthConstants.PATH_HEALTH)
public final class HealthServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/plain");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("ok");
    }
}
