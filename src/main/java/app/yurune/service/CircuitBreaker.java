package app.yurune.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * サーキットブレーカー。LLM が続けて失敗したとき、一定時間、呼ぶのをやめる。
 * 壊れているものを呼び続けて、待たされることと、費用が増えることを防ぐ。
 * 開いている時間が過ぎたら、1 回だけ試す（成功すれば閉じ、失敗すればまた開く）。
 */
public final class CircuitBreaker {

    /** 開閉の状態（運用の記録 control ログ用）。 */
    public enum State {
        CLOSED("closed"), OPEN("open"), HALF_OPEN("half_open");

        private final String label;

        State(String label) {
            this.label = label;
        }

        public String asLabel() {
            return label;
        }
    }

    private final int failureLimit;
    private final Duration openDuration;
    private final Clock clock;
    private final Consumer<State> onStateChange;

    private int consecutiveFailures;
    private Instant openedAt;
    private State state = State.CLOSED;

    public CircuitBreaker(int failureLimit, Duration openDuration, Clock clock) {
        this(failureLimit, openDuration, clock, null);
    }

    /** @param onStateChange 開閉の状態が変わったときだけ呼ばれる（運用の記録用）。使わないときは null */
    public CircuitBreaker(int failureLimit, Duration openDuration, Clock clock, Consumer<State> onStateChange) {
        this.failureLimit = failureLimit;
        this.openDuration = openDuration;
        this.clock = clock;
        this.onStateChange = onStateChange;
    }

    /** 呼んでよいか。閉じているとき、または開いてから時間が過ぎたとき（1 回だけ試す、半開き）は true。 */
    public synchronized boolean allowCall() {
        if (openedAt == null) {
            return true;
        }
        boolean trialDue = !clock.instant().isBefore(openedAt.plus(openDuration));
        if (trialDue) {
            setState(State.HALF_OPEN);
        }
        return trialDue;
    }

    public synchronized void recordSuccess() {
        consecutiveFailures = 0;
        openedAt = null;
        setState(State.CLOSED);
    }

    public synchronized void recordFailure() {
        consecutiveFailures++;
        if (consecutiveFailures >= failureLimit) {
            openedAt = clock.instant();
            setState(State.OPEN);
        }
    }

    private void setState(State next) {
        if (state != next) {
            state = next;
            if (onStateChange != null) {
                onStateChange.accept(next);
            }
        }
    }
}
