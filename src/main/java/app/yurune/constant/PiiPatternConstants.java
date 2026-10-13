package app.yurune.constant;

import app.yurune.domain.value.PiiKind;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 個人情報・認証情報を見つける、形のパターン。NFKC で正規化した文に照合する（全角の数字や記号も、ここで半角になる）。
 * 氏名のように、形で見つけにくいものは、ここでは扱わない。
 */
public final class PiiPatternConstants {

    private static final String DASH = "[-ー−‐]";
    /** 電話番号の区切り（ハイフンの種類、または空白。なくてもよい）。 */
    private static final String PHONE_SEPARATOR = "[-ー−‐ ]?";

    /**
     * 伏せ字にする順番（入れた順）。長く、確かな形から先に置換して、数字の並びを取り合わないようにする。
     * 電話番号の一部（090-1234）を、先に郵便番号とみなして、残りの数字を残してしまわないよう、電話番号は郵便番号より先。
     */
    public static final Map<PiiKind, Pattern> PATTERNS = createPatterns();

    private static Map<PiiKind, Pattern> createPatterns() {
        Map<PiiKind, Pattern> patterns = new LinkedHashMap<>();
        patterns.put(PiiKind.EMAIL, Pattern.compile("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}"));
        patterns.put(PiiKind.URL, Pattern.compile("(?:https?://|www\\.)[^\\s、。]+", Pattern.CASE_INSENSITIVE));
        patterns.put(PiiKind.SECRET_KEY, Pattern.compile(
                "AIza[0-9A-Za-z_\\-]{35}|sk-[A-Za-z0-9_\\-]{20,}|gh[pousr]_[A-Za-z0-9]{36,}|AKIA[0-9A-Z]{16}"
                        + "|(?<![A-Za-z0-9_\\-])[A-Za-z0-9_\\-]{32,}(?![A-Za-z0-9_\\-])"));
        patterns.put(PiiKind.CARD_NUMBER, Pattern.compile("(?<!\\d)(?:\\d[ \\-]?){12,18}\\d(?!\\d)"));
        patterns.put(PiiKind.PHONE, Pattern.compile(
                "(?<![\\d])(?:\\+81" + PHONE_SEPARATOR + "|0)\\d{1,4}" + PHONE_SEPARATOR + "\\d{1,4}" + PHONE_SEPARATOR
                        + "\\d{3,4}(?![\\d])"));
        patterns.put(PiiKind.POSTAL_CODE, Pattern.compile("(?<![\\d\\-])〒?\\s?\\d{3}" + DASH + "\\d{4}(?![\\d\\-])"));
        patterns.put(PiiKind.ID_NUMBER, Pattern.compile("(?<!\\d)\\d{12}(?!\\d)"));
        patterns.put(PiiKind.ADDRESS, Pattern.compile(
                "(?:北海道|(?:東京|京都|大阪)(?:都|府)|[^\\s、。]{2,3}県)[^\\s、。]{0,30}?\\d{1,4}(?:丁目|番地|番|号|" + DASH + ")[^\\s、。]{0,12}"));
        return patterns;
    }

    private PiiPatternConstants() {
    }
}
