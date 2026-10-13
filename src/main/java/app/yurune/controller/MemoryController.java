package app.yurune.controller;

import app.yurune.constant.InfoConstants;
import app.yurune.constant.LearningConstants;
import app.yurune.constant.NightGoalConstants;
import app.yurune.constant.NoteSummaryConstants;
import app.yurune.constant.OptionLabelConstants;
import app.yurune.controller.view.NoteGroupView;
import app.yurune.controller.view.NoteRowView;
import app.yurune.domain.value.NightGoal;
import app.yurune.service.MemoryGroup;
import app.yurune.service.MemoryService;
import app.yurune.service.NoteRow;

import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 「覚えたことを見る」の入出力を受け持つ。ぽてねは話さない画面。
 * 見せるのは、ログイン中の本人の集計（構造化した項目）だけ。消す操作は、ここには置かない（「データを消す」にある）。
 */
public final class MemoryController {

    private final MemoryService memoryService;
    private final Supplier<NightGoal> goalSupplier;

    public MemoryController(MemoryService memoryService, Supplier<NightGoal> goalSupplier) {
        this.memoryService = memoryService;
        this.goalSupplier = goalSupplier;
    }

    public Navigation showMemory(HttpServletRequest request) {
        List<NoteGroupView> groups = new ArrayList<>();
        for (MemoryGroup group : memoryService.buildMemory()) {
            List<NoteRowView> rows = new ArrayList<>();
            for (NoteRow row : group.getRows()) {
                rows.add(new NoteRowView(row));
            }
            groups.add(new NoteGroupView(OptionLabelConstants.dimensionLabelOf(group.getDimension()), rows));
        }
        request.setAttribute(InfoConstants.ATTR_MEMORY_GROUPS, groups);
        request.setAttribute(InfoConstants.ATTR_MEMORY_MIN_TRIALS, LearningConstants.MIN_TRIALS_FOR_TOP_CANDIDATE);
        request.setAttribute(NoteSummaryConstants.ATTR_COUNT_PHRASE, NightGoalConstants.countPhraseOf(goalSupplier.get()));
        return Navigation.forward(InfoConstants.VIEW_MEMORY);
    }
}
