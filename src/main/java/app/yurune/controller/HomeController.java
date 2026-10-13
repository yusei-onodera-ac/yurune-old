package app.yurune.controller;

import app.yurune.constant.ViewConstants;
import app.yurune.controller.view.ChoiceView;
import app.yurune.controller.view.MorningSheetView;
import app.yurune.controller.view.SleepRecordView;
import app.yurune.domain.value.MorningAnswer;
import app.yurune.domain.value.MorningMood;
import app.yurune.service.MorningCheckService;
import app.yurune.service.RecordService;

import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * ホーム画面の入出力を受け持つ。判断や計算は Service に任せる。
 */
public final class HomeController {

    private final RecordService recordService;
    private final MorningCheckService morningCheckService;

    public HomeController(RecordService recordService, MorningCheckService morningCheckService) {
        this.recordService = recordService;
        this.morningCheckService = morningCheckService;
    }

    /** 表示に必要な値をリクエストに載せ、表示する JSP の場所を返す。 */
    public String showHome(HttpServletRequest request) {
        SleepRecordView lastNight = new SleepRecordView(recordService.findLastNight());
        List<String> says = new ArrayList<>();
        says.add(lastNight.isRecorded() ? "昨夜の記録を保存したよ。" : "昨夜の記録は、まだないよ。");
        says.add("今夜の準備ができるよ。");

        request.setAttribute(ViewConstants.ATTR_LAST_NIGHT, lastNight);
        request.setAttribute(ViewConstants.ATTR_SAYS, String.join(ViewConstants.SAY_SEPARATOR, says));

        morningCheckService.findPendingSheet().ifPresent(sheet -> {
            request.setAttribute(ViewConstants.ATTR_MORNING_SHEET, new MorningSheetView(sheet));
            request.setAttribute(ViewConstants.ATTR_MOODS, buildMoodChoices());
            request.setAttribute(ViewConstants.ATTR_ANSWERS, buildAnswerChoices());
        });
        return ViewConstants.VIEW_HOME;
    }

    private List<ChoiceView> buildMoodChoices() {
        List<ChoiceView> choices = new ArrayList<>();
        for (MorningMood mood : MorningMood.values()) {
            choices.add(new ChoiceView(mood.asId(), mood.asLabel(), false));
        }
        return choices;
    }

    /** 「合っていた」を先頭に並べる（画面の並びを、よい順にそろえるため）。 */
    private List<ChoiceView> buildAnswerChoices() {
        List<ChoiceView> choices = new ArrayList<>();
        for (MorningAnswer answer : List.of(MorningAnswer.FIT, MorningAnswer.NEUTRAL, MorningAnswer.NOT_FIT)) {
            choices.add(new ChoiceView(answer.asId(), answer.asLabel(), false));
        }
        return choices;
    }
}
