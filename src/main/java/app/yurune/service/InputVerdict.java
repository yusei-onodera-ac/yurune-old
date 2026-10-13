package app.yurune.service;

import app.yurune.domain.value.PiiKind;

import java.util.Map;

/**
 * 利用者の発言を検査した結果。LLM に渡してよいか、渡さずに定型文で返すかを表す。
 * 記録に使うのは、結果の種類と、伏せ字の種類・件数だけで、発言の本文は持ち出さない。
 */
public final class InputVerdict {

    /** 検査の結果。PASS 以外は、LLM を呼ばない。 */
    public enum Outcome {
        PASS("pass"),
        /** 医療領域の語がある。定型文 T-MED を返す。 */
        MEDICAL("medical"),
        /** 指示の無視や、役割のすり替えを狙う言い回しがある。 */
        INJECTION("injection"),
        /** つらい気持ちの言葉（死ぬこと、消えること）がある。LLM を呼ばず、返事もせずに、無視する。 */
        DISTRESS("distress"),
        /** 空（無言）。何も返さない。 */
        EMPTY("empty");

        private final String id;

        Outcome(String id) {
            this.id = id;
        }

        public String asId() {
            return id;
        }
    }

    private final Outcome outcome;
    private final String sanitizedText;
    private final Map<PiiKind, Integer> maskedCounts;
    private final boolean truncated;

    public InputVerdict(Outcome outcome, String sanitizedText, Map<PiiKind, Integer> maskedCounts, boolean truncated) {
        this.outcome = outcome;
        this.sanitizedText = sanitizedText;
        this.maskedCounts = Map.copyOf(maskedCounts);
        this.truncated = truncated;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public boolean canAskLlm() {
        return outcome == Outcome.PASS;
    }

    /** LLM に渡してよい文（正規化し、長さを切り、個人情報を伏せ字にしたもの）。PASS のときだけ使う。 */
    public String getSanitizedText() {
        return sanitizedText;
    }

    public Map<PiiKind, Integer> getMaskedCounts() {
        return maskedCounts;
    }

    public boolean wasTruncated() {
        return truncated;
    }
}
