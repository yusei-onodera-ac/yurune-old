package app.yurune.controller.view;

/**
 * 曜日ごとのグラフの目盛り線。
 */
public final class GridLineView {

    private final double lineY;
    private final double textY;
    private final String label;

    public GridLineView(double lineY, double textY, String label) {
        this.lineY = lineY;
        this.textY = textY;
        this.label = label;
    }

    public double getLineY() {
        return lineY;
    }

    public double getTextY() {
        return textY;
    }

    public String getLabel() {
        return label;
    }
}
