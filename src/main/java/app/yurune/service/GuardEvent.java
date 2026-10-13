package app.yurune.service;

import app.yurune.domain.value.PiiKind;

import java.util.Map;
import java.util.Optional;

/**
 * 会話の 1 回分の、守りの結果（記録と、数えるためのもの）。種類と件数だけで、発言も返事も含まない。
 */
public final class GuardEvent {

    private final String inputOutcome;
    private final Map<PiiKind, Integer> maskedCounts;
    private final boolean inputTruncated;
    private final ConversationReply.Source source;
    private final String violationKind;
    private final String downReason;
    private final long latencyMillis;

    public GuardEvent(String inputOutcome, Map<PiiKind, Integer> maskedCounts, boolean inputTruncated,
            ConversationReply.Source source, String violationKind, String downReason, long latencyMillis) {
        this.inputOutcome = inputOutcome;
        this.maskedCounts = Map.copyOf(maskedCounts);
        this.inputTruncated = inputTruncated;
        this.source = source;
        this.violationKind = violationKind;
        this.downReason = downReason;
        this.latencyMillis = latencyMillis;
    }

    public String getInputOutcome() {
        return inputOutcome;
    }

    public Map<PiiKind, Integer> getMaskedCounts() {
        return maskedCounts;
    }

    public boolean wasInputTruncated() {
        return inputTruncated;
    }

    public ConversationReply.Source getSource() {
        return source;
    }

    /** 出力の検査で捨てた理由の種類（捨てていなければ空）。 */
    public Optional<String> findViolationKind() {
        return Optional.ofNullable(violationKind);
    }

    /** 定型文 T-DOWN にした理由（そうでなければ空）。 */
    public Optional<String> findDownReason() {
        return Optional.ofNullable(downReason);
    }

    public long getLatencyMillis() {
        return latencyMillis;
    }
}
