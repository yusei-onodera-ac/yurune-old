package app.yurune.servlet;

import app.yurune.constant.AuthConstants;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * ログイン画面の入口。表示（GET）と、ログイン（POST）。
 */
@WebServlet(AuthConstants.PATH_LOGIN)
public final class LoginServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        navigate(request, response, getComponents().getAccountController().showLogin(request));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        navigate(request, response, getComponents().getAccountController().login(request));
    }
}
