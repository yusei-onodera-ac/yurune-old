package app.yurune.servlet;

import app.yurune.constant.SettingsConstants;
import app.yurune.controller.BadRequestException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 設定の 1 項目を変える入口（スイッチと、選択肢の画面）。選べない値は、400 で断る。
 */
@WebServlet(SettingsConstants.PATH_UPDATE)
public final class SettingsUpdateServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            navigate(request, response, getUserComponents(request).getSettingsController().updateSetting(request));
        } catch (BadRequestException exception) {
            sendBadRequest(response, exception);
        }
    }
}
