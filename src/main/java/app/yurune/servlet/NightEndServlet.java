package app.yurune.servlet;

import app.yurune.constant.ViewConstants;
import app.yurune.controller.BadRequestException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 夜を終える入口。利用者の「終わる」と、端末のタイマーによる自動終了。
 */
@WebServlet(ViewConstants.PATH_NIGHT_END)
public final class NightEndServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            redirect(request, response, getUserComponents(request).getNightController().endNight(request));
        } catch (BadRequestException exception) {
            sendBadRequest(response, exception);
        }
    }
}
