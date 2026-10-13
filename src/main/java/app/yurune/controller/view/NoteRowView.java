package app.yurune.controller.view;

import app.yurune.constant.OptionLabelConstants;
import app.yurune.service.NoteLabel;
import app.yurune.service.NoteRow;

/**
 * JSP に渡す表示用の 1 行。JSP が Entity や Service の型を直接知らないようにする。
 */
public final class NoteRowView {

    private static final int PERCENT = 100;

    private final String optionText;
    private final int trialCount;
    private final int winCount;
    private final int percent;
    private final String labelText;
    private final boolean labelMuted;

    public NoteRowView(NoteRow row) {
        this.optionText = OptionLabelConstants.labelOf(
                row.getArm().getKey().getDimension(), row.getArm().getKey().getOptionName());
        this.trialCount = row.getArm().getTrialCount().asInt();
        this.winCount = row.getArm().getWinCount().asInt();
        this.percent = (int) Math.round(row.getArm().getWinRate().asDouble() * PERCENT);
        this.labelText = toLabelText(row.getLabel());
        this.labelMuted = row.getLabel() == NoteLabel.TRY_MORE;
    }

    private static String toLabelText(NoteLabel label) {
        return switch (label) {
            case TOP_CANDIDATE -> "いまの第一候補";
            case TRY_MORE -> "もう少し試す";
            case NONE -> "";
        };
    }

    public String getOptionText() {
        return optionText;
    }

    public int getTrialCount() {
        return trialCount;
    }

    public int getWinCount() {
        return winCount;
    }

    public int getPercent() {
        return percent;
    }

    public String getLabelText() {
        return labelText;
    }

    public boolean isLabelMuted() {
        return labelMuted;
    }
}
