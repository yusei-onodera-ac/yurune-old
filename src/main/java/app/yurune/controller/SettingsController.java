package app.yurune.controller;

import app.yurune.constant.AuthConstants;
import app.yurune.constant.NightGoalConstants;
import app.yurune.constant.SettingsConstants;
import app.yurune.constant.ViewConstants;
import app.yurune.controller.view.SettingChoiceView;
import app.yurune.controller.view.SettingsView;
import app.yurune.domain.value.AuthenticatedUser;
import app.yurune.domain.value.NightGoal;
import app.yurune.domain.value.SettingItem;
import app.yurune.domain.value.UserSettings;
import app.yurune.domain.value.VoiceStyle;
import app.yurune.repository.StorageUnavailableException;
import app.yurune.service.AuditLogger;
import app.yurune.service.AuthException;
import app.yurune.service.DataEraseService;
import app.yurune.service.SettingsService;

import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * 設定画面の入出力を受け持つ。ぽてねは話さない画面。
 * 4 つの項目（夜の会話、音量の上限、ぽてねの声、好みを覚える）は、選べる値だけを受け付けて保存する。
 * 「データを消す」は、確認の画面を挟み、パスワードをもう一度入れてもらってから行う。
 */
public final class SettingsController {

    private static final String MESSAGE_WRONG_PASSWORD = "パスワードが違います。";
    private static final String MESSAGE_TOO_MANY = "試行が多すぎます。しばらくしてから、もう一度お試しください。";
    private static final String MESSAGE_UNAVAILABLE = "いまは、消せませんでした。時間をおいて、お試しください。";
    private static final String NO_HINT = "";

    private final AuthenticatedUser user;
    private final DataEraseService dataEraseService;
    private final AuditLogger auditLogger;
    private final SettingsService settingsService;

    public SettingsController(AuthenticatedUser user, DataEraseService dataEraseService, AuditLogger auditLogger,
            SettingsService settingsService) {
        this.user = user;
        this.dataEraseService = dataEraseService;
        this.auditLogger = auditLogger;
        this.settingsService = settingsService;
    }

    public String showSettings(HttpServletRequest request) {
        UserSettings settings = settingsService.findSettings();
        request.setAttribute(SettingsConstants.ATTR_SETTINGS, new SettingsView(settings.isNightTalkEnabled(),
                settings.getVolumeCap().asInt() + SettingsConstants.VOLUME_CAP_TEXT_SUFFIX,
                settings.getVoiceStyle().asLabel(), settings.isLearningEnabled(),
                NightGoalConstants.labelOf(settings.getNightGoal()), settings.isKeepScreenOnEnabled()));
        return ViewConstants.VIEW_SETTINGS;
    }

    /** 夜のめやすの選択肢。選ぶと、その場で決まる。 */
    public String showGoalChoices(HttpServletRequest request) {
        NightGoal current = settingsService.findSettings().getNightGoal();
        List<SettingChoiceView> choices = new ArrayList<>();
        for (NightGoal goal : NightGoal.values()) {
            String hint = goal == NightGoal.DEFAULT
                    ? NightGoalConstants.HINT_DEFAULT + "。" + NightGoalConstants.hintOf(goal)
                    : NightGoalConstants.hintOf(goal);
            choices.add(new SettingChoiceView(goal.asId(), NightGoalConstants.labelOf(goal), hint, goal == current));
        }
        return showChoices(request, SettingItem.GOAL, NightGoalConstants.TITLE, NightGoalConstants.FOOT, choices);
    }

    /** 音量の上限の選択肢。選ぶと、その場で決まる。 */
    public String showVolumeChoices(HttpServletRequest request) {
        int current = settingsService.findSettings().getVolumeCap().asInt();
        List<SettingChoiceView> choices = new ArrayList<>();
        for (int percent : SettingsConstants.VOLUME_CAP_CHOICES) {
            String hint = percent == SettingsConstants.DEFAULT_VOLUME_CAP_PCT
                    ? SettingsConstants.VOLUME_CAP_DEFAULT_HINT : NO_HINT;
            choices.add(new SettingChoiceView(String.valueOf(percent),
                    percent + SettingsConstants.VOLUME_CAP_TEXT_SUFFIX, hint, percent == current));
        }
        return showChoices(request, SettingItem.VOLUME_CAP, SettingsConstants.TITLE_VOLUME,
                SettingsConstants.FOOT_VOLUME, choices);
    }

    /** ぽてねの声の選択肢。選ぶと、その場で決まる。 */
    public String showVoiceChoices(HttpServletRequest request) {
        VoiceStyle current = settingsService.findSettings().getVoiceStyle();
        List<SettingChoiceView> choices = new ArrayList<>();
        for (VoiceStyle style : VoiceStyle.values()) {
            choices.add(new SettingChoiceView(style.asId(), style.asLabel(), style.asHint(), style == current));
        }
        return showChoices(request, SettingItem.VOICE, SettingsConstants.TITLE_VOICE,
                SettingsConstants.FOOT_VOICE, choices);
    }

    /**
     * 1 項目を変えて保存し、設定へ戻す（二重に送らないよう、移動で返す）。
     * 項目の名前と値は、選べるものだけを受け付ける（それ以外は 400）。変えたことは、項目の名前と値だけを記録に残す。
     */
    public Navigation updateSetting(HttpServletRequest request) {
        try {
            SettingItem item = SettingItem.fromId(request.getParameter(SettingsConstants.PARAM_ITEM));
            UserSettings changed = settingsService.changeSetting(item, request.getParameter(SettingsConstants.PARAM_VALUE));
            auditLogger.setting(item, changed);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("invalid setting", exception);
        }
        return Navigation.redirect(ViewConstants.PATH_SETTINGS);
    }

    public Navigation showEraseConfirmation(HttpServletRequest request) {
        request.setAttribute(AuthConstants.ATTR_CURRENT_EMAIL, user.getEmail());
        return Navigation.forward(AuthConstants.VIEW_ERASE);
    }

    /** 消せたら、設定へ戻す（二重に送らないよう、移動で返す）。 */
    public Navigation erase(HttpServletRequest request) {
        try {
            dataEraseService.erase(user, request.getParameter(AuthConstants.PARAM_PASSWORD));
            return Navigation.redirect(ViewConstants.PATH_SETTINGS + "?erased=1");
        } catch (AuthException exception) {
            auditLogger.auth(AuditLogger.AuthAction.ERASE_CONFIRM, exception.getReason());
            request.setAttribute(AuthConstants.ATTR_CURRENT_EMAIL, user.getEmail());
            request.setAttribute(AuthConstants.ATTR_MESSAGE, describe(exception.getReason()));
            return Navigation.forward(AuthConstants.VIEW_ERASE);
        } catch (StorageUnavailableException exception) {
            request.setAttribute(AuthConstants.ATTR_CURRENT_EMAIL, user.getEmail());
            request.setAttribute(AuthConstants.ATTR_MESSAGE, MESSAGE_UNAVAILABLE);
            return Navigation.forward(AuthConstants.VIEW_ERASE);
        }
    }

    private String showChoices(HttpServletRequest request, SettingItem item, String title, String foot,
            List<SettingChoiceView> choices) {
        request.setAttribute(SettingsConstants.ATTR_CHOICE_ITEM, item.asId());
        request.setAttribute(SettingsConstants.ATTR_CHOICE_TITLE, title);
        request.setAttribute(SettingsConstants.ATTR_CHOICE_FOOT, foot);
        request.setAttribute(SettingsConstants.ATTR_CHOICES, choices);
        return SettingsConstants.VIEW_CHOICE;
    }

    private String describe(AuthException.Reason reason) {
        return switch (reason) {
            case TOO_MANY_ATTEMPTS -> MESSAGE_TOO_MANY;
            case UNAVAILABLE -> MESSAGE_UNAVAILABLE;
            default -> MESSAGE_WRONG_PASSWORD;
        };
    }
}
