package app.yurune.service;

import app.yurune.domain.value.PiiKind;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 個人情報を伏せ字にした結果。記録に使うのは、種類と件数だけ（元の文字列は持たない）。
 */
public final class MaskResult {

    private final String maskedText;
    private final Map<PiiKind, Integer> counts;

    public MaskResult(String maskedText, Map<PiiKind, Integer> counts) {
        this.maskedText = maskedText;
        Map<PiiKind, Integer> copy = new EnumMap<>(PiiKind.class);
        copy.putAll(counts);
        this.counts = Collections.unmodifiableMap(copy);
    }

    public String getMaskedText() {
        return maskedText;
    }

    public Map<PiiKind, Integer> getCounts() {
        return counts;
    }

    public int totalCount() {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    public boolean hasMasked() {
        return totalCount() > 0;
    }
}
