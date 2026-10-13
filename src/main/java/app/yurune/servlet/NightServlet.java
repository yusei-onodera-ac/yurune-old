package app.yurune.servlet;

import app.yurune.constant.ViewConstants;
import app.yurune.controller.BadRequestException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 夜の画面の入口。表示（GET）と、夜を始める（POST）。
 */
@WebServlet(ViewConstants.PATH_NIGHT)
public final class NightServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        navigate(request, response, getUserComponents(request).getNightController().showNight(request));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            redirect(request, response, getUserComponents(request).getNightController().startNight(request));
        } catch (BadRequestException exception) {
            sendBadRequest(response, exception);
        }
    }
}
