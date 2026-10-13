package app.yurune.domain.value;

/**
 * 学習する要素の種類（次元）。
 */
public enum Dimension {

    SOUND("sound"),
    DURATION("duration"),
    TALK("talk"),
    FADE("fade"),
    NO_REACTION("noReaction");

    private final String id;

    Dimension(String id) {
        this.id = id;
    }

    /** 保存先の文書 ID に使う文字列。 */
    public String asText() {
        return id;
    }

    public static Dimension fromText(String text) {
        for (Dimension dimension : values()) {
            if (dimension.id.equals(text)) {
                return dimension;
            }
        }
        throw new IllegalArgumentException("unknown dimension: " + text);
    }
}
