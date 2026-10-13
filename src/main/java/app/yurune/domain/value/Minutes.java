package app.yurune.domain.value;

/**
 * 経過した分数。
 */
public final class Minutes {

    private final int minutes;

    public Minutes(int minutes) {
        if (minutes < 0) {
            throw new IllegalArgumentException("minutes must not be negative");
        }
        this.minutes = minutes;
    }

    public int asInt() {
        return minutes;
    }
}
