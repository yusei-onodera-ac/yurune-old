package app.yurune.domain.value;

/**
 * 見直しの結果として決めたこと。どれも、ぽてねが自分で決めて、理由を判断の記録に残す。
 */
public enum ReviewAction {

    PREFER("prefer"),
    PARK("park"),
    DROP("drop"),
    ROLLBACK("rollback"),
    KEEP_WATCHING("keep_watching"),
    REFER("refer");

    private final String id;

    ReviewAction(String id) {
        this.id = id;
    }

    public String asId() {
        return id;
    }
}
