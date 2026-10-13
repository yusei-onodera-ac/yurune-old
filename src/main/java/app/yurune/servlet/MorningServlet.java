package app.yurune.servlet;

import app.yurune.constant.ViewConstants;
import app.yurune.controller.BadRequestException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 「朝のひとこと」の保存の入口。
 */
@WebServlet(ViewConstants.PATH_MORNING)
public final class MorningServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            redirect(request, response, getUserComponents(request).getMorningController().saveMorning(request));
        } catch (BadRequestException exception) {
            sendBadRequest(response, exception);
        }
    }
}
