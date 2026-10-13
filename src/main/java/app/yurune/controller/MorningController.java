package app.yurune.controller;

import app.yurune.constant.ViewConstants;
import app.yurune.domain.value.ClockTime;
import app.yurune.domain.value.MorningAnswer;
import app.yurune.domain.value.MorningMood;
import app.yurune.service.MorningCheckService;
import app.yurune.service.MorningInput;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 「朝のひとこと」の保存を受け持つ。気分と答えは、答えなくても保存できる。
 */
public final class MorningController {

    private final MorningCheckService morningCheckService;

    public MorningController(MorningCheckService morningCheckService) {
        this.morningCheckService = morningCheckService;
    }

    /** 保存して、戻る画面の場所を返す。値が正しくないときは BadRequestException。 */
    public String saveMorning(HttpServletRequest request) {
        MorningInput input;
        try {
            input = readInput(request);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("invalid morning input", exception);
        }
        morningCheckService.completeMorning(input);
        return ViewConstants.PATH_HOME;
    }

    private MorningInput readInput(HttpServletRequest request) {
        ClockTime bedTime = readTime(request.getParameter(ViewConstants.PARAM_BED));
        ClockTime wakeTime = readTime(request.getParameter(ViewConstants.PARAM_WAKE));
        if (request.getParameter(ViewConstants.PARAM_SKIP) != null) {
            return MorningInput.dismissed(bedTime, wakeTime);
        }
        return MorningInput.saved(bedTime, wakeTime, readAnswer(request), readMood(request));
    }

    /** 空のときは「分からない」として null。形式が違うときは例外。 */
    private ClockTime readTime(String text) {
        return text == null || text.isEmpty() ? null : ClockTime.parse(text);
    }

    private MorningAnswer readAnswer(HttpServletRequest request) {
        String answer = request.getParameter(ViewConstants.PARAM_ANSWER);
        return answer == null || answer.isEmpty() ? null : MorningAnswer.fromId(answer);
    }

    private MorningMood readMood(HttpServletRequest request) {
        String mood = request.getParameter(ViewConstants.PARAM_MOOD);
        return mood == null || mood.isEmpty() ? null : MorningMood.fromId(mood);
    }
}
