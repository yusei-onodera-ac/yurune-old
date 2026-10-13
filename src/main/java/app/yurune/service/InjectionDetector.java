package app.yurune.service;

import app.yurune.constant.ForbiddenTermConstants;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 利用者の発言が、指示の無視、システム指示の聞き出し、役割のすり替えを狙っていないかを、言い回しで見つける。
 * 見つからなくても、LLM が出せるのは「会話の文」と「許可リストのツール」だけで、出力の検査とツールの検証が止める。
 * ここは、その手前の、最初の層。
 */
public final class InjectionDetector {

    private final List<Pattern> patterns = ForbiddenTermConstants.INJECTION_PATTERNS.stream()
            .map(Pattern::compile).toList();

    public boolean isInjection(String text) {
        String target = TextNormalizer.normalizeForMatching(text);
        for (Pattern pattern : patterns) {
            if (pattern.matcher(target).find()) {
                return true;
            }
        }
        return false;
    }
}
