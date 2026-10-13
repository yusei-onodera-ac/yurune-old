package app.yurune.controller.view;

/**
 * 今夜の計画の 1 項目（項目名、値、今夜変えるか）。
 */
public final class PlanItemView {

    private final String label;
    private final String valueText;
    private final boolean changed;

    public PlanItemView(String label, String valueText, boolean changed) {
        this.label = label;
        this.valueText = valueText;
        this.changed = changed;
    }

    public String getLabel() {
        return label;
    }

    public String getValueText() {
        return valueText;
    }

    public boolean isChanged() {
        return changed;
    }
}
