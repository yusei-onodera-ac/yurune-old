package app.yurune.service;

import app.yurune.constant.GuardConstants;

import java.util.Map;

/**
 * 利用者の発言を、LLM に渡す前に検査する固定の枠。発言は命令ではなく、信頼しないデータとして扱う。
 * 順番: 正規化 → 長さを切る → 個人情報の伏せ字 → インジェクション → つらい気持ちの言葉 → 医療領域。
 * 医療領域とインジェクションは、LLM を呼ばず、コードの定型文で応じる。つらい気持ちの言葉は、LLM を呼ばず、返事もしない。
 */
public final class InputGuard {

    private final PiiMasker piiMasker;
    private final InjectionDetector injectionDetector;
    private final ForbiddenDomainFilter forbiddenDomainFilter;

    public InputGuard(PiiMasker piiMasker, InjectionDetector injectionDetector,
            ForbiddenDomainFilter forbiddenDomainFilter) {
        this.piiMasker = piiMasker;
        this.injectionDetector = injectionDetector;
        this.forbiddenDomainFilter = forbiddenDomainFilter;
    }

    public InputVerdict inspect(String rawText) {
        String normalized = TextNormalizer.normalize(rawText);
        if (normalized.isEmpty()) {
            return new InputVerdict(InputVerdict.Outcome.EMPTY, "", Map.of(), false);
        }
        boolean truncated = normalized.codePointCount(0, normalized.length()) > GuardConstants.INPUT_MAX_CHARS;
        String limited = truncated ? cutToLimit(normalized) : normalized;

        MaskResult masked = piiMasker.mask(limited);
        InputVerdict.Outcome outcome = decideOutcome(masked.getMaskedText());
        return new InputVerdict(outcome, masked.getMaskedText(), masked.getCounts(), truncated);
    }

    private InputVerdict.Outcome decideOutcome(String maskedText) {
        if (injectionDetector.isInjection(maskedText)) {
            return InputVerdict.Outcome.INJECTION;
        }
        if (forbiddenDomainFilter.containsDistressTerm(maskedText)) {
            return InputVerdict.Outcome.DISTRESS;
        }
        if (forbiddenDomainFilter.containsMedicalTopic(maskedText)) {
            return InputVerdict.Outcome.MEDICAL;
        }
        return InputVerdict.Outcome.PASS;
    }

    /** 文字数は、サロゲートペアを 1 文字として数える。 */
    private String cutToLimit(String text) {
        int end = text.offsetByCodePoints(0, GuardConstants.INPUT_MAX_CHARS);
        return text.substring(0, end);
    }
}
