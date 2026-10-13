package app.yurune.servlet;

import app.yurune.constant.InfoConstants;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 「音源のクレジット」の画面の入口。
 */
@WebServlet(InfoConstants.PATH_CREDITS)
public final class CreditsServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        navigate(request, response, getComponents().getInfoController().showCredits(request));
    }
}
