package app.yurune.domain.value;

/**
 * 夜を終えた理由。
 */
public enum EndReason {

    NO_REACTION("no_reaction"),
    USER("user");

    private final String id;

    EndReason(String id) {
        this.id = id;
    }

    public static EndReason fromId(String id) {
        for (EndReason reason : values()) {
            if (reason.id.equals(id)) {
                return reason;
            }
        }
        throw new IllegalArgumentException("unknown end reason: " + id);
    }

    public String asId() {
        return id;
    }
}
