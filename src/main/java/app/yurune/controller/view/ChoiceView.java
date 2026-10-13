package app.yurune.controller.view;

/**
 * 選ぶ項目 1 つ（送る値と、画面に出す名前）。
 */
public final class ChoiceView {

    private final String id;
    private final String label;
    private final boolean checked;

    public ChoiceView(String id, String label, boolean checked) {
        this.id = id;
        this.label = label;
        this.checked = checked;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public boolean isChecked() {
        return checked;
    }
}
