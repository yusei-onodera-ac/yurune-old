package app.yurune.servlet;

import app.yurune.constant.AuthConstants;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 「データを消す」の入口。確認の画面（GET）と、パスワードを確かめて消す（POST）。
 */
@WebServlet(AuthConstants.PATH_ERASE)
public final class SettingsEraseServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        navigate(request, response, getUserComponents(request).getSettingsController().showEraseConfirmation(request));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        navigate(request, response, getUserComponents(request).getSettingsController().erase(request));
    }
}
