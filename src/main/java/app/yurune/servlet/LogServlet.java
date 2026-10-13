package app.yurune.servlet;

import app.yurune.constant.ViewConstants;
import app.yurune.controller.BadRequestException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 記録画面の入口。表示と、時刻の保存。
 */
@WebServlet(ViewConstants.PATH_LOG)
public final class LogServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        forward(request, response, getUserComponents(request).getLogController().showLog(request));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            redirect(request, response, getUserComponents(request).getLogController().saveRecord(request));
        } catch (BadRequestException exception) {
            sendBadRequest(response, exception);
        }
    }
}
