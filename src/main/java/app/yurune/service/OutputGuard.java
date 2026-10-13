package app.yurune.service;

import app.yurune.constant.ForbiddenTermConstants;
import app.yurune.constant.GuardConstants;
import app.yurune.constant.OptionLabelConstants;
import app.yurune.domain.value.FactSheet;
import app.yurune.domain.value.ViolationKind;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM が書いた文を、画面や音声に出す前に、コードで検査する固定の枠。
 * 順番: 空 → システムの指示の漏れ（合言葉、指示の一部）→ 禁止領域の語 → 個人情報 → 事実シート → 印の置き換え → 長さ。
 * 違反した出力は捨て、呼び出し側が定型文に差し替える。ここが返すのは、違反の種類だけ（本文は持ち出さない）。
 *
 * 事実シートの検査（許可リスト）: 数字は書かせない（印を置かせ、コードが入れる）。
 * 過去や継続の主張（「昨日も」など）と、今夜の計画にない音の名前は、LLM が持たない事実なので、言わせない。
 */
public final class OutputGuard {

    private static final Pattern MARKER = Pattern.compile("\\{\\{([a-z_]+)}}");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    /** 漢数字と単位の組。数の主張に当たる。「十分（足りている）」「一時的」は、普通の言葉なので、先に外す。 */
    private static final Pattern KANJI_QUANTITY = Pattern.compile(
            "[〇一二三四五六七八九十百千万]+(?:分|時間|時|秒|回|日|晩|週|ヶ月|か月|パーセント)");
    private static final Pattern ORDINARY_WORDS_WITH_NUMERALS = Pattern.compile(
            "(?<![〇一二三四五六七八九十百千万])十分|一時的");
    private static final String SENTENCE_ENDINGS = "。!?";
    private static final String MARKER_PLACEHOLDER = "\u0000";

    private final ForbiddenDomainFilter forbiddenDomainFilter;
    private final PiiMasker piiMasker;
    private final PromptSecrets promptSecrets;
    private final Set<String> protectedWindows;
    private final List<String> soundLabels = OptionLabelConstants.soundLabels();

    /** 守る指示は、この時点で登録済みのものだけが対象。指示を作る部品を、先に作っておくこと。 */
    public OutputGuard(ForbiddenDomainFilter forbiddenDomainFilter, PiiMasker piiMasker, PromptSecrets promptSecrets) {
        this.forbiddenDomainFilter = forbiddenDomainFilter;
        this.piiMasker = piiMasker;
        this.promptSecrets = promptSecrets;
        this.protectedWindows = createWindows(promptSecrets.getProtectedTexts());
    }

    public OutputVerdict inspect(String rawText, FactSheet facts) {
        String text = TextNormalizer.normalize(rawText);
        if (text.isEmpty()) {
            return OutputVerdict.rejected(ViolationKind.EMPTY);
        }
        if (leaksProtectedText(text)) {
            return OutputVerdict.rejected(ViolationKind.SYSTEM_PROMPT_LEAK);
        }
        Optional<ViolationKind> forbidden = forbiddenDomainFilter.findViolation(text);
        if (forbidden.isPresent()) {
            return OutputVerdict.rejected(forbidden.get());
        }
        if (piiMasker.mask(text).hasMasked()) {
            return OutputVerdict.rejected(ViolationKind.PERSONAL_INFORMATION);
        }
        if (violatesFactSheet(text, facts)) {
            return OutputVerdict.rejected(ViolationKind.FACT_SHEET);
        }
        return limitLength(fillMarkers(text, facts));
    }

    // ---- システムの指示の漏れ ----

    private boolean leaksProtectedText(String text) {
        String target = text.toLowerCase(Locale.ROOT);
        if (target.contains(promptSecrets.getCanary().toLowerCase(Locale.ROOT))) {
            return true;
        }
        target = hideMarkers(target);
        int window = GuardConstants.LEAK_WINDOW_CHARS;
        for (int start = 0; start + window <= target.length(); start++) {
            if (protectedWindows.contains(target.substring(start, start + window))) {
                return true;
            }
        }
        return false;
    }

    private Set<String> createWindows(List<String> protectedTexts) {
        Set<String> windows = new HashSet<>();
        int window = GuardConstants.LEAK_WINDOW_CHARS;
        for (String protectedText : protectedTexts) {
            String target = hideMarkers(TextNormalizer.normalizeForMatching(protectedText));
            for (int start = 0; start + window <= target.length(); start++) {
                windows.add(target.substring(start, start + window));
            }
        }
        return windows;
    }

    /**
     * 数字の印（{{名前}}）は、指示にも返事にも、正しく現れる。漏れの照合では、印を、どちらにも現れない文字に置き換えて、
     * 印があるだけで、指示が漏れたとみなさないようにする。
     */
    private String hideMarkers(String text) {
        return MARKER.matcher(text).replaceAll(MARKER_PLACEHOLDER);
    }

    // ---- 事実シート ----

    private boolean violatesFactSheet(String text, FactSheet facts) {
        return containsNumber(text) || mentionsHistory(text) || mentionsOtherSound(text, facts) || hasUnknownMarker(text, facts);
    }

    /** 数字は、印を通してしか出さない。ここに残る数字は、LLM が自分で書いたもの。 */
    private boolean containsNumber(String text) {
        String withoutMarkers = MARKER.matcher(text).replaceAll("");
        if (DIGIT.matcher(withoutMarkers).find()) {
            return true;
        }
        String ordinary = ORDINARY_WORDS_WITH_NUMERALS.matcher(withoutMarkers).replaceAll("");
        return KANJI_QUANTITY.matcher(ordinary).find();
    }

    private boolean mentionsHistory(String text) {
        for (String term : ForbiddenTermConstants.HISTORY_CLAIM_TERMS) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private boolean mentionsOtherSound(String text, FactSheet facts) {
        Optional<String> allowed = facts.findSoundLabel();
        for (String label : soundLabels) {
            if (text.contains(label) && !allowed.map(label::equals).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    /** 事実シートにない印、または、閉じていない印。 */
    private boolean hasUnknownMarker(String text, FactSheet facts) {
        Matcher matcher = MARKER.matcher(text);
        while (matcher.find()) {
            if (facts.findMarkerValue(matcher.group(1)).isEmpty()) {
                return true;
            }
        }
        String rest = MARKER.matcher(text).replaceAll("");
        return rest.contains(GuardConstants.MARKER_OPEN) || rest.contains(GuardConstants.MARKER_CLOSE);
    }

    private String fillMarkers(String text, FactSheet facts) {
        Matcher matcher = MARKER.matcher(text);
        StringBuilder filled = new StringBuilder();
        while (matcher.find()) {
            String value = facts.findMarkerValue(matcher.group(1)).orElseThrow();
            matcher.appendReplacement(filled, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(filled);
        return filled.toString();
    }

    // ---- 長さ ----

    /** 上限を超えたら、上限以内の最後の文の終わりで切る。切れる所がなければ、違反とする。 */
    private OutputVerdict limitLength(String text) {
        int limit = GuardConstants.OUTPUT_MAX_CHARS;
        if (text.codePointCount(0, text.length()) <= limit) {
            return OutputVerdict.accepted(text);
        }
        String head = text.substring(0, text.offsetByCodePoints(0, limit));
        int cut = lastSentenceEnd(head);
        if (cut < 0) {
            return OutputVerdict.rejected(ViolationKind.LENGTH);
        }
        return OutputVerdict.accepted(head.substring(0, cut + 1));
    }

    private int lastSentenceEnd(String head) {
        int last = -1;
        for (int index = 0; index < head.length(); index++) {
            if (SENTENCE_ENDINGS.indexOf(head.charAt(index)) >= 0) {
                last = index;
            }
        }
        return last;
    }
}
