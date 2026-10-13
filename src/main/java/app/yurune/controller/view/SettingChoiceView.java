package app.yurune.controller.view;

/**
 * 設定の選択肢 1 つ（送る値、画面に出す名前、名前の下の説明、いま選ばれているか）。
 */
public final class SettingChoiceView {

    private final String id;
    private final String label;
    private final String hint;
    private final boolean checked;

    public SettingChoiceView(String id, String label, String hint, boolean checked) {
        this.id = id;
        this.label = label;
        this.hint = hint;
        this.checked = checked;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    /** 説明がないときは、空の文字。 */
    public String getHint() {
        return hint;
    }

    public boolean isChecked() {
        return checked;
    }
}
