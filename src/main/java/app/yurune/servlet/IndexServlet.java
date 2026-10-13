package app.yurune.servlet;

import app.yurune.constant.ViewConstants;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * アプリの入口（ルート）。ホーム画面へ移す。
 */
@WebServlet("")
public final class IndexServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        redirect(request, response, ViewConstants.PATH_HOME);
    }
}
