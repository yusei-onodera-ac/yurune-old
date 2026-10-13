package app.yurune.domain.value;

/**
 * 朝のひとことの答え（昨夜のすごし方が合っていたか）。
 */
public enum MorningAnswer {

    NOT_FIT(0, "not_fit", "合わなかった"),
    NEUTRAL(1, "neutral", "どちらでもない"),
    FIT(2, "fit", "合っていた");

    private final int score;
    private final String id;
    private final String label;

    MorningAnswer(int score, String id, String label) {
        this.score = score;
        this.id = id;
        this.label = label;
    }

    /** サンプルデータの数値（0、1、2）から答えを作る。 */
    public static MorningAnswer fromScore(int score) {
        for (MorningAnswer answer : values()) {
            if (answer.score == score) {
                return answer;
            }
        }
        throw new IllegalArgumentException("unknown score: " + score);
    }

    /** 画面から届いた ID（fit、neutral、not_fit）から答えを作る。 */
    public static MorningAnswer fromId(String id) {
        for (MorningAnswer answer : values()) {
            if (answer.id.equals(id)) {
                return answer;
            }
        }
        throw new IllegalArgumentException("unknown answer id: " + id);
    }

    /** 学習で「合っていた」と数えるのは FIT だけ。 */
    public boolean isFit() {
        return this == FIT;
    }

    public String asId() {
        return id;
    }

    public String asLabel() {
        return label;
    }
}
