package app.yurune.servlet;

import app.yurune.constant.AuthConstants;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 登録画面の入口。表示（GET）と、登録（POST）。
 */
@WebServlet(AuthConstants.PATH_REGISTER)
public final class RegisterServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        navigate(request, response, getComponents().getAccountController().showRegister(request));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        navigate(request, response, getComponents().getAccountController().register(request));
    }
}
