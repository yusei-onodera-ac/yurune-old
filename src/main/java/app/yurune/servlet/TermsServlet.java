package app.yurune.servlet;

import app.yurune.constant.AuthConstants;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 利用規約の画面の入口。
 */
@WebServlet(AuthConstants.PATH_TERMS)
public final class TermsServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        navigate(request, response, getComponents().getAccountController().showTerms(request));
    }
}
