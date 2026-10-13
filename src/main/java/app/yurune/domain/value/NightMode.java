package app.yurune.domain.value;

/**
 * 夜のすごし方（音だけ、または会話）。
 */
public enum NightMode {

    SOUND("sound"),
    TALK("talk");

    private final String id;

    NightMode(String id) {
        this.id = id;
    }

    /** 画面から届いた ID から作る。 */
    public static NightMode fromId(String id) {
        for (NightMode mode : values()) {
            if (mode.id.equals(id)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("unknown mode: " + id);
    }

    public String asId() {
        return id;
    }
}
