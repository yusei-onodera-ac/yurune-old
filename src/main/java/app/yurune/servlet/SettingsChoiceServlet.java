package app.yurune.servlet;

import app.yurune.constant.SettingsConstants;
import app.yurune.controller.SettingsController;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 設定の選択肢の画面の入口（音量の上限、ぽてねの声）。
 */
@WebServlet({SettingsConstants.PATH_VOLUME, SettingsConstants.PATH_VOICE, SettingsConstants.PATH_GOAL})
public final class SettingsChoiceServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        SettingsController controller = getUserComponents(request).getSettingsController();
        String path = request.getServletPath();
        String view;
        if (SettingsConstants.PATH_VOLUME.equals(path)) {
            view = controller.showVolumeChoices(request);
        } else if (SettingsConstants.PATH_GOAL.equals(path)) {
            view = controller.showGoalChoices(request);
        } else {
            view = controller.showVoiceChoices(request);
        }
        forward(request, response, view);
    }
}
