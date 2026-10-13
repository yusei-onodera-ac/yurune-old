package app.yurune.servlet;

import app.yurune.AppComponents;
import app.yurune.constant.AuthConstants;
import app.yurune.constant.ViewConstants;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 稼働の準備の確認（ログイン不要）。保存先（Firestore）に読み書きできるかだけで決める。
 * LLM の停止や Model Armor の停止では、ここを 503 にしない（規則だけで動く縮退運転も「動いている」）。
 */
@WebServlet(AuthConstants.PATH_READY)
public final class ReadyServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final int HTTP_SERVICE_UNAVAILABLE = 503;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AppComponents components = (AppComponents) getServletContext().getAttribute(ViewConstants.ATTR_COMPONENTS);
        boolean ready = components.getReadinessService().isReady();
        response.setStatus(ready ? HttpServletResponse.SC_OK : HTTP_SERVICE_UNAVAILABLE);
        response.setContentType("text/plain");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(ready ? "ready" : "not ready");
    }
}
