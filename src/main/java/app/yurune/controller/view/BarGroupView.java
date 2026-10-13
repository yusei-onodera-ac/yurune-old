package app.yurune.controller.view;

/**
 * 曜日ごとのグラフの、1 曜日分の棒（先週と今週）。記録のない週は棒を描かない。
 */
public final class BarGroupView {

    private final String label;
    private final int labelX;
    private final int previousX;
    private final double previousY;
    private final double previousHeight;
    private final boolean previousRecorded;
    private final int thisX;
    private final double thisY;
    private final double thisHeight;
    private final boolean thisRecorded;

    public BarGroupView(String label, int labelX, int previousX, double previousY, double previousHeight,
            boolean previousRecorded, int thisX, double thisY, double thisHeight, boolean thisRecorded) {
        this.label = label;
        this.labelX = labelX;
        this.previousX = previousX;
        this.previousY = previousY;
        this.previousHeight = previousHeight;
        this.previousRecorded = previousRecorded;
        this.thisX = thisX;
        this.thisY = thisY;
        this.thisHeight = thisHeight;
        this.thisRecorded = thisRecorded;
    }

    public String getLabel() {
        return label;
    }

    public int getLabelX() {
        return labelX;
    }

    public int getPreviousX() {
        return previousX;
    }

    public double getPreviousY() {
        return previousY;
    }

    public double getPreviousHeight() {
        return previousHeight;
    }

    public boolean isPreviousRecorded() {
        return previousRecorded;
    }

    public int getThisX() {
        return thisX;
    }

    public double getThisY() {
        return thisY;
    }

    public double getThisHeight() {
        return thisHeight;
    }

    public boolean isThisRecorded() {
        return thisRecorded;
    }
}
