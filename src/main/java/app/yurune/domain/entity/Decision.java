package app.yurune.domain.entity;

import app.yurune.domain.value.ClockTime;
import app.yurune.domain.value.DecisionPhase;
import app.yurune.domain.value.DisplayText;
import app.yurune.domain.value.WakeDate;

import java.util.Map;
import java.util.Objects;

/**
 * エージェントの判断 1 件（見たこと、選んだこと、理由）。拒否したものも残す。
 * 会話の本文、音声、自由記述は含めない。
 */
public final class Decision {

    private final WakeDate nightId;
    private final ClockTime at;
    private final DecisionPhase phase;
    private final Map<String, String> observed;
    private final DisplayText chosen;
    private final DisplayText reason;
    private final boolean accepted;

    public Decision(WakeDate nightId, ClockTime at, DecisionPhase phase, Map<String, String> observed,
            DisplayText chosen, DisplayText reason, boolean accepted) {
        this.nightId = Objects.requireNonNull(nightId);
        this.at = Objects.requireNonNull(at);
        this.phase = Objects.requireNonNull(phase);
        this.observed = Map.copyOf(observed);
        this.chosen = Objects.requireNonNull(chosen);
        this.reason = Objects.requireNonNull(reason);
        this.accepted = accepted;
    }

    public WakeDate getNightId() {
        return nightId;
    }

    public ClockTime getAt() {
        return at;
    }

    public DecisionPhase getPhase() {
        return phase;
    }

    public Map<String, String> getObserved() {
        return observed;
    }

    public DisplayText getChosen() {
        return chosen;
    }

    public DisplayText getReason() {
        return reason;
    }

    public boolean isAccepted() {
        return accepted;
    }
}
