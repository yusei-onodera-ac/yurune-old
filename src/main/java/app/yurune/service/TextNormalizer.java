package app.yurune.service;

import java.text.Normalizer;
import java.util.Locale;

/**
 * 検査の前に、文の見かけを揃える。全角と半角、大文字と小文字、目に見えない文字を使った、すり抜けを防ぐため。
 */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    /** NFKC に揃え、目に見えない文字（ゼロ幅、制御文字）を取り除き、空白を 1 つにして、前後を削る。 */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String unified = Normalizer.normalize(text, Normalizer.Form.NFKC);
        String visible = unified.replaceAll("[\\p{Cf}\\p{Cc}&&[^\\n\\t]]", "");
        return visible.replaceAll("\\s+", " ").strip();
    }

    /** 語の照合用。正規化に加えて、小文字にする。 */
    public static String normalizeForMatching(String text) {
        return normalize(text).toLowerCase(Locale.ROOT);
    }
}
