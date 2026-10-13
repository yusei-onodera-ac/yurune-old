package app.yurune.service;

import app.yurune.constant.ForbiddenTermConstants;
import app.yurune.domain.value.ViolationKind;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 禁止領域（疾病の断定、投薬の助言、効果の断定、評価、根拠のない知識の主張、役割の逸脱）の語を照合する。
 * 出力の検査と、入力の医療領域の検査に使う。語の一覧はコードから分けて、{@link ForbiddenTermConstants} に置く。
 */
public final class ForbiddenDomainFilter {

    private final List<Pattern> medicalOutputPatterns = ForbiddenTermConstants.MEDICAL_OUTPUT_PATTERNS.stream()
            .map(Pattern::compile).toList();

    /** 出力に、禁止領域の語や言い回しが入っているときの、種類。入っていなければ、空。 */
    public Optional<ViolationKind> findViolation(String text) {
        String target = TextNormalizer.normalizeForMatching(text);
        for (Map.Entry<ViolationKind, List<String>> entry : ForbiddenTermConstants.OUTPUT_TERMS.entrySet()) {
            for (String term : entry.getValue()) {
                if (target.contains(term)) {
                    return Optional.of(entry.getKey());
                }
            }
        }
        for (Pattern pattern : medicalOutputPatterns) {
            if (pattern.matcher(target).find()) {
                return Optional.of(ViolationKind.MEDICAL_TOPIC);
            }
        }
        return Optional.empty();
    }

    public boolean containsForbiddenTerm(String text) {
        return findViolation(text).isPresent();
    }

    /**
     * 利用者の発言に、医療領域（病名、薬、受診、つらさの訴えなど）の語が入っているか。
     * 語を、文字として含むだけの日常の言葉（憂うつ、うつ伏せなど）は、先に取り除いてから照合する。
     */
    public boolean containsMedicalTopic(String text) {
        String target = TextNormalizer.normalizeForMatching(text);
        for (String everyday : ForbiddenTermConstants.MEDICAL_TERM_EVERYDAY_WORDS) {
            target = target.replace(everyday, " ");
        }
        for (String term : ForbiddenTermConstants.MEDICAL_INPUT_TERMS) {
            if (target.contains(term)) {
                return true;
            }
        }
        return false;
    }
}
