package app.yurune.domain.value;

/**
 * エージェントの判断の段階（計画、実行、終了、更新）。
 */
public enum DecisionPhase {

    PLAN("plan", "計画"),
    ACT("act", "実行"),
    END("end", "終了"),
    UPDATE("update", "更新"),
    CHECK("check", "確認"),
    REVIEW("review", "見直し");

    private final String id;
    private final String label;

    DecisionPhase(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String asId() {
        return id;
    }

    public String asLabel() {
        return label;
    }
}
