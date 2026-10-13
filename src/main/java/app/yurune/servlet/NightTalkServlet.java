package app.yurune.servlet;

import app.yurune.constant.ConversationConstants;
import app.yurune.controller.BadRequestException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 夜の会話の入口。端末が、声を文字にした発言を送り、検査を通した返事を JSON で受け取る。
 */
@WebServlet(ConversationConstants.PATH_TALK)
public final class NightTalkServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String json = getUserComponents(request).getNightController().talk(request);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(json);
        } catch (BadRequestException exception) {
            sendBadRequest(response, exception);
        }
    }
}
