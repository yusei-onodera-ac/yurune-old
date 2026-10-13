package app.yurune.controller.view;

/**
 * 実験の進み具合の、1 日分の目印。
 */
public final class DotView {

    private final String label;
    private final String cssClass;

    public DotView(String label, String cssClass) {
        this.label = label;
        this.cssClass = cssClass;
    }

    public String getLabel() {
        return label;
    }

    public String getCssClass() {
        return cssClass;
    }
}
