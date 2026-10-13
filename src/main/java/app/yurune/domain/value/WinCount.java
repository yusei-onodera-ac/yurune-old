package app.yurune.domain.value;

/**
 * 朝の答えが「合っていた」だった回数。
 */
public final class WinCount {

    private final int count;

    public WinCount(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("win count must not be negative");
        }
        this.count = count;
    }

    public WinCount increase() {
        return new WinCount(count + 1);
    }

    public int asInt() {
        return count;
    }
}
