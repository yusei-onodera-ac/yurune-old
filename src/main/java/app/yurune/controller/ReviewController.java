package app.yurune.controller;

import app.yurune.constant.NightGoalConstants;
import app.yurune.constant.NoteSummaryConstants;
import app.yurune.constant.TimeConstants;
import app.yurune.constant.ViewConstants;
import app.yurune.constant.WeekConstants;
import app.yurune.controller.view.BarGroupView;
import app.yurune.controller.view.GridLineView;
import app.yurune.controller.view.WeekView;
import app.yurune.domain.entity.SleepRecord;
import app.yurune.domain.value.NightGoal;
import app.yurune.service.NoteSummary;
import app.yurune.service.NoteSummaryService;
import app.yurune.service.WeekComparison;
import app.yurune.service.WeekComparisonService;
import app.yurune.service.WeekdayPair;

import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * ふりかえり画面の入出力を受け持つ。週の比較と、いまの目標（と、「いまのところ」の一文）を、1 画面にまとめる。
 * 結果は事実の件数だけを示し、「良い」「効く」とは言わない。
 */
public final class ReviewController {

    private final WeekComparisonService weekComparisonService;
    private final NoteSummaryService noteSummaryService;
    private final Supplier<NightGoal> goalSupplier;

    public ReviewController(WeekComparisonService weekComparisonService, NoteSummaryService noteSummaryService,
            Supplier<NightGoal> goalSupplier) {
        this.weekComparisonService = weekComparisonService;
        this.noteSummaryService = noteSummaryService;
        this.goalSupplier = goalSupplier;
    }

    public String showReview(HttpServletRequest request) {
        WeekComparison comparison = weekComparisonService.compare();
        request.setAttribute(ViewConstants.ATTR_WEEK, buildWeekView(comparison));
        request.setAttribute(ViewConstants.ATTR_WEEK_SAY, buildWeekSay(comparison));

        addGoal(request);
        addSummary(request);
        return ViewConstants.VIEW_REVIEW;
    }

    // ---- いまのところ ----

    /** 「いまのところ」の文と、「今は表示しない」を出すかどうか。 */
    private void addSummary(HttpServletRequest request) {
        NoteSummary summary = noteSummaryService.findSummary();
        request.setAttribute(NoteSummaryConstants.ATTR_SUMMARY_LINES, summary.lines());
        request.setAttribute(NoteSummaryConstants.ATTR_REFER_SHOWN, summary.referralShown());
    }

    /** 「今は表示しない」。受診のすすめを、一定の日数のあいだ出さない。ふりかえりの「目標」に戻す。 */
    public Navigation hideReferral() {
        noteSummaryService.hideReferral();
        return Navigation.redirect(ViewConstants.PATH_REVIEW + "?" + NoteSummaryConstants.PARAM_PANEL + "="
                + NoteSummaryConstants.PANEL_GOAL);
    }

    // ---- 週 ----

    private WeekView buildWeekView(WeekComparison comparison) {
        return new WeekView(
                comparison.isShown(),
                comparison.getThisWeekAverage().asHourMinuteText(),
                comparison.getPreviousWeekAverage().asHourMinuteText(),
                comparison.getGain().asInt() + "分長い",
                buildGridLines(),
                buildBarGroups(comparison.getPairs()));
    }

    private String buildWeekSay(WeekComparison comparison) {
        if (comparison.isShown()) {
            return "今週は、先週より" + comparison.getGain().asInt() + "分長かったよ。";
        }
        return "記録、続けてくれてありがとう。";
    }

    private List<GridLineView> buildGridLines() {
        List<GridLineView> lines = new ArrayList<>();
        for (int hours : WeekConstants.CHART_GRID_HOURS) {
            double lineY = WeekConstants.CHART_BASELINE_Y - toBarHeight(hours * TimeConstants.MINUTES_PER_HOUR);
            lines.add(new GridLineView(lineY, lineY + WeekConstants.CHART_LABEL_OFFSET_Y, hours + "時間"));
        }
        return lines;
    }

    private List<BarGroupView> buildBarGroups(List<WeekdayPair> pairs) {
        List<BarGroupView> groups = new ArrayList<>();
        for (int index = 0; index < pairs.size(); index++) {
            WeekdayPair pair = pairs.get(index);
            int previousX = WeekConstants.CHART_FIRST_X + index * WeekConstants.CHART_GROUP_STEP;
            int thisX = previousX + WeekConstants.CHART_BAR_STEP;
            double previousHeight = toBarHeight(pair.getPreviousWeekRecord());
            double thisHeight = toBarHeight(pair.getThisWeekRecord());
            groups.add(new BarGroupView(
                    pair.getThisWeekRecord().getWakeDate().asWeekdayText(),
                    previousX + WeekConstants.CHART_BAR_WIDTH + 1,
                    previousX, WeekConstants.CHART_BASELINE_Y - previousHeight, previousHeight,
                    pair.getPreviousWeekRecord().isRecorded(),
                    thisX, WeekConstants.CHART_BASELINE_Y - thisHeight, thisHeight,
                    pair.getThisWeekRecord().isRecorded()));
        }
        return groups;
    }

    private double toBarHeight(SleepRecord record) {
        return record.isRecorded() ? toBarHeight(record.getDuration().asInt()) : 0.0;
    }

    /** 縦軸（4〜8 時間）の中に収まるように、棒の高さを決める。 */
    private double toBarHeight(int minutes) {
        double hours = (double) minutes / TimeConstants.MINUTES_PER_HOUR;
        double ratio = (hours - WeekConstants.CHART_MIN_HOURS) / WeekConstants.CHART_SPAN_HOURS;
        return Math.max(0.0, Math.min(1.0, ratio)) * WeekConstants.CHART_HEIGHT;
    }

    // ---- 目標 ----

    /** いまの夜のめやす（設定の値）を、ふりかえりの「目標」タブに出す。新しい保存先は作らない（既存の設定を読むだけ）。 */
    private void addGoal(HttpServletRequest request) {
        NightGoal goal = goalSupplier.get();
        String label = NightGoalConstants.labelOf(goal);
        request.setAttribute(ViewConstants.ATTR_GOAL_LABEL, label);
        request.setAttribute(ViewConstants.ATTR_GOAL_DESCRIPTION, NightGoalConstants.hintOf(goal));
        request.setAttribute(ViewConstants.ATTR_GOAL_SAY, "いまの目標は「" + label + "」だよ。");
    }
}
