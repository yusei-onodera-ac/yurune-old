package app.yurune.domain.value;

/**
 * 朝のひとことの気分。
 */
public enum MorningMood {

    CALM("calm", "穏やか"),
    NORMAL("normal", "ふつう"),
    UNEASY("uneasy", "そわそわ"),
    SLEEPY("sleepy", "眠い"),
    HEAVY("heavy", "重い");

    private final String id;
    private final String label;

    MorningMood(String id, String label) {
        this.id = id;
        this.label = label;
    }

    /** 画面から届いた ID から気分を作る。 */
    public static MorningMood fromId(String id) {
        for (MorningMood mood : values()) {
            if (mood.id.equals(id)) {
                return mood;
            }
        }
        throw new IllegalArgumentException("unknown mood id: " + id);
    }

    public String asId() {
        return id;
    }

    public String asLabel() {
        return label;
    }
}
