package app.yurune.servlet;

import app.yurune.constant.ViewConstants;
import app.yurune.controller.BadRequestException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 夜のすごし方（音だけ、会話）を、利用者が切り替える入口。
 */
@WebServlet(ViewConstants.PATH_NIGHT_MODE)
public final class NightModeServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            redirect(request, response, getUserComponents(request).getNightController().changeMode(request));
        } catch (BadRequestException exception) {
            sendBadRequest(response, exception);
        }
    }
}
