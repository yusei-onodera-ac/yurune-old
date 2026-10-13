package app.yurune.domain.value;

/**
 * 試した回数。
 */
public final class TrialCount {

    private final int count;

    public TrialCount(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("trial count must not be negative");
        }
        this.count = count;
    }

    public TrialCount increase() {
        return new TrialCount(count + 1);
    }

    public int asInt() {
        return count;
    }

    public boolean isAtLeast(int threshold) {
        return count >= threshold;
    }
}
