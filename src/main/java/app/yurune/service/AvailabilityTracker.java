package app.yurune.service;

import java.util.function.Consumer;

/**
 * 外部サービス（Model Armor など）が、落ちているかどうかを覚え、変わったときだけ知らせる。
 * 洪水にしないため（連続して同じ状態を知らせない）。全員で共有する 1 つの部品として、AppComponents が作る。
 */
public final class AvailabilityTracker {

    private final Consumer<Boolean> onChange;

    private boolean down;

    /** @param onChange 状態が変わったときに呼ばれる（引数は、落ちているなら true）。使わないときは null */
    public AvailabilityTracker(Consumer<Boolean> onChange) {
        this.onChange = onChange;
    }

    public synchronized void recordSuccess() {
        setDown(false);
    }

    public synchronized void recordFailure() {
        setDown(true);
    }

    private void setDown(boolean next) {
        if (down != next) {
            down = next;
            if (onChange != null) {
                onChange.accept(next);
            }
        }
    }
}
