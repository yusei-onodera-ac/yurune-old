package app.yurune.controller.view;

import java.util.List;

/**
 * 週の比較の表示用。比較を出さないときは shown が false。
 */
public final class WeekView {

    private final boolean shown;
    private final String thisAverageText;
    private final String previousAverageText;
    private final String gainText;
    private final List<GridLineView> gridLines;
    private final List<BarGroupView> barGroups;

    public WeekView(boolean shown, String thisAverageText, String previousAverageText, String gainText,
            List<GridLineView> gridLines, List<BarGroupView> barGroups) {
        this.shown = shown;
        this.thisAverageText = thisAverageText;
        this.previousAverageText = previousAverageText;
        this.gainText = gainText;
        this.gridLines = List.copyOf(gridLines);
        this.barGroups = List.copyOf(barGroups);
    }

    public boolean isShown() {
        return shown;
    }

    public String getThisAverageText() {
        return thisAverageText;
    }

    public String getPreviousAverageText() {
        return previousAverageText;
    }

    public String getGainText() {
        return gainText;
    }

    public List<GridLineView> getGridLines() {
        return gridLines;
    }

    public List<BarGroupView> getBarGroups() {
        return barGroups;
    }
}
