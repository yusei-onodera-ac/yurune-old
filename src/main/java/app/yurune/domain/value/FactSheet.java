package app.yurune.domain.value;

import java.util.Map;
import java.util.Optional;

/**
 * LLM に渡してよい「事実」と、LLM の文に差し込む数字の一覧（コードが作る）。
 * LLM は数字を書かず、{{印}} を置く。コードが、ここにある値を、後から入れる。ここにない事実は、言えない。
 */
public final class FactSheet {

    private static final FactSheet EMPTY = new FactSheet(Map.of(), null);

    private final Map<String, String> markerValues;
    private final String soundLabel;

    /** @param soundLabel 今夜の音の名前。ここにない音の名前を、LLM は言えない（null なら、どの音の名前も言えない） */
    public FactSheet(Map<String, String> markerValues, String soundLabel) {
        this.markerValues = Map.copyOf(markerValues);
        this.soundLabel = soundLabel;
    }

    public static FactSheet empty() {
        return EMPTY;
    }

    public Optional<String> findMarkerValue(String markerName) {
        return Optional.ofNullable(markerValues.get(markerName));
    }

    public Optional<String> findSoundLabel() {
        return Optional.ofNullable(soundLabel);
    }
}
