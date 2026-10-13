package app.yurune.service;

import app.yurune.domain.value.NightMode;

import java.util.List;
import java.util.Objects;

/**
 * 観測を渡したあとの結果（実行を試みた呼び出し、夜が終わったか、すごし方、端末が鳴らす音の状態）。
 */
public final class NightObservationResult {

    private final List<AppliedCall> appliedCalls;
    private final boolean ended;
    private final NightMode mode;
    private final NightSoundState soundState;

    public NightObservationResult(List<AppliedCall> appliedCalls, boolean ended, NightMode mode,
            NightSoundState soundState) {
        this.appliedCalls = List.copyOf(appliedCalls);
        this.ended = ended;
        this.mode = Objects.requireNonNull(mode);
        this.soundState = Objects.requireNonNull(soundState);
    }

    public List<AppliedCall> getAppliedCalls() {
        return appliedCalls;
    }

    public boolean isEnded() {
        return ended;
    }

    public NightMode getMode() {
        return mode;
    }

    public NightSoundState getSoundState() {
        return soundState;
    }

    /** すごし方が、この観測のあとに切り替わったか。 */
    public boolean hasAcceptedCall(String toolId) {
        for (AppliedCall call : appliedCalls) {
            if (call.isAccepted() && call.getToolId().equals(toolId)) {
                return true;
            }
        }
        return false;
    }
}
