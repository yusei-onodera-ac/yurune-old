package app.yurune.servlet;

import app.yurune.constant.ViewConstants;
import app.yurune.controller.BadRequestException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 端末が観測（反応のない秒数など）を送る入口。エージェントの判断の結果を、JSON で返す。
 */
@WebServlet(ViewConstants.PATH_NIGHT_OBSERVE)
public final class NightObserveServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String json = getUserComponents(request).getNightController().observe(request);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(json);
        } catch (BadRequestException exception) {
            sendBadRequest(response, exception);
        }
    }
}
