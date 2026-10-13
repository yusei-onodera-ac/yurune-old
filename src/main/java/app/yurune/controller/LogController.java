package app.yurune.controller;

import app.yurune.constant.TimeConstants;
import app.yurune.constant.ViewConstants;
import app.yurune.controller.view.SleepRecordView;
import app.yurune.domain.entity.SleepRecord;
import app.yurune.domain.value.ClockTime;
import app.yurune.domain.value.WakeDate;
import app.yurune.service.RecordService;

import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * 記録画面の入出力を受け持つ。過去 7 日を見て、時刻を直す。
 */
public final class LogController {

    private final RecordService recordService;

    public LogController(RecordService recordService) {
        this.recordService = recordService;
    }

    public String showLog(HttpServletRequest request) {
        List<SleepRecordView> days = new ArrayList<>();
        for (SleepRecord record : recordService.findRecentRecordsNewestFirst(TimeConstants.DAYS_PER_WEEK)) {
            days.add(new SleepRecordView(record));
        }
        request.setAttribute(ViewConstants.ATTR_DAYS, days);
        request.setAttribute(ViewConstants.ATTR_SAYS, "記録は、朝に自動で聞くよ。直したいときだけ、ここで直してね。");
        return ViewConstants.VIEW_LOG;
    }

    /** 直した時刻を保存し、戻る画面の場所を返す。値が正しくないときは BadRequestException。 */
    public String saveRecord(HttpServletRequest request) {
        try {
            WakeDate wakeDate = WakeDate.parse(request.getParameter(ViewConstants.PARAM_DATE));
            ClockTime bedTime = ClockTime.parse(request.getParameter(ViewConstants.PARAM_BED));
            ClockTime wakeTime = ClockTime.parse(request.getParameter(ViewConstants.PARAM_WAKE));
            recordService.saveRecord(wakeDate, bedTime, wakeTime);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("invalid record input", exception);
        }
        return ViewConstants.PATH_LOG;
    }
}
