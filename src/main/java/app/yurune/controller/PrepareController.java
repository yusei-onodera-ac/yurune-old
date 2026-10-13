package app.yurune.controller;

import app.yurune.constant.OptionLabelConstants;
import app.yurune.constant.SettingsConstants;
import app.yurune.constant.ViewConstants;
import app.yurune.controller.view.PlanItemView;
import app.yurune.domain.entity.NightPlan;
import app.yurune.domain.value.Dimension;
import app.yurune.service.PlanService;
import app.yurune.service.SettingsService;

import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * 夜の準備画面の入出力を受け持つ。エージェントの計画（今夜のやり方）を、項目と理由で見せる。
 */
public final class PrepareController {

    /** 計画の項目を、画面に並べる順。 */
    private static final List<Dimension> PLAN_ORDER =
            List.of(Dimension.SOUND, Dimension.DURATION, Dimension.TALK, Dimension.FADE);

    private final PlanService planService;
    private final SettingsService settingsService;

    public PrepareController(PlanService planService, SettingsService settingsService) {
        this.planService = planService;
        this.settingsService = settingsService;
    }

    public String showPrepare(HttpServletRequest request) {
        NightPlan plan = planService.proposeTonightPlan();

        List<PlanItemView> items = new ArrayList<>();
        for (Dimension dimension : PLAN_ORDER) {
            items.add(new PlanItemView(
                    OptionLabelConstants.dimensionLabelOf(dimension),
                    OptionLabelConstants.labelOf(dimension, plan.getOption(dimension)),
                    plan.isChanged(dimension)));
        }
        request.setAttribute(SettingsConstants.ATTR_TALK_ENABLED, settingsService.findEnforcedSettings().isNightTalkEnabled());
        request.setAttribute(ViewConstants.ATTR_PLAN_ITEMS, items);
        request.setAttribute(ViewConstants.ATTR_PLAN_REASON, plan.getReason().asText());
        request.setAttribute(ViewConstants.ATTR_SAYS, "今夜は、このやり方にしてみたよ。|変えたいところは、変えてね。");
        return ViewConstants.VIEW_PREPARE;
    }
}
