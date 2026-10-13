package app.yurune.service;

import app.yurune.constant.GuardConstants;
import app.yurune.constant.PiiPatternConstants;
import app.yurune.domain.value.PiiKind;

import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 個人情報・認証情報の形をした文字列を、「○○」に置き換える。入力にも出力にも使う。
 * カード番号と電話番号は、数字の並びだけでは普通の数と区別できないので、桁数と検査（Luhn）で確かめてから置き換える。
 * 氏名のように形で見つけにくいものは、ここでは扱わない（別の層で扱う）。
 */
public final class PiiMasker {

    private static final int DECIMAL_BASE = 10;

    public MaskResult mask(String text) {
        String current = TextNormalizer.normalize(text);
        Map<PiiKind, Integer> counts = new EnumMap<>(PiiKind.class);
        for (Map.Entry<PiiKind, Pattern> entry : PiiPatternConstants.PATTERNS.entrySet()) {
            current = replaceMatches(current, entry.getKey(), entry.getValue(), counts);
        }
        return new MaskResult(current, counts);
    }

    private String replaceMatches(String text, PiiKind kind, Pattern pattern, Map<PiiKind, Integer> counts) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            if (isGenuine(kind, matcher.group())) {
                counts.merge(kind, 1, Integer::sum);
                matcher.appendReplacement(result, Matcher.quoteReplacement(GuardConstants.MASK_TOKEN));
            } else {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private boolean isGenuine(PiiKind kind, String candidate) {
        String digits = candidate.replaceAll("\\D", "");
        return switch (kind) {
            case CARD_NUMBER -> digits.length() >= GuardConstants.CARD_MIN_DIGITS
                    && digits.length() <= GuardConstants.CARD_MAX_DIGITS && passesLuhn(digits);
            case PHONE -> isPhoneLength(candidate, digits);
            default -> true;
        };
    }

    /** 国内の番号は 10〜11 桁。国番号（+81）つきは、先頭の 0 がないので、国番号を除いて 9〜10 桁。 */
    private boolean isPhoneLength(String candidate, String digits) {
        if (candidate.startsWith(GuardConstants.COUNTRY_PREFIX)) {
            int national = digits.length() - GuardConstants.COUNTRY_CODE_DIGITS;
            return national >= GuardConstants.PHONE_MIN_DIGITS - 1 && national <= GuardConstants.PHONE_MAX_DIGITS - 1;
        }
        return digits.length() >= GuardConstants.PHONE_MIN_DIGITS && digits.length() <= GuardConstants.PHONE_MAX_DIGITS;
    }

    /** カード番号の検査桁。普通の数の並びを、カード番号と取り違えないため。 */
    private boolean passesLuhn(String digits) {
        int sum = 0;
        boolean doubleNext = false;
        for (int index = digits.length() - 1; index >= 0; index--) {
            int digit = digits.charAt(index) - '0';
            if (doubleNext) {
                digit *= 2;
                if (digit >= DECIMAL_BASE) {
                    digit -= DECIMAL_BASE - 1;
                }
            }
            sum += digit;
            doubleNext = !doubleNext;
        }
        return sum % DECIMAL_BASE == 0;
    }
}
