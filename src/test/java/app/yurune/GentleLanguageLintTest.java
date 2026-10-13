package app.yurune;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * 「ゆるり」の文言の検査。この M3 で新しく書いた、または文を直した画面と文言だけを対象にする。
 *
 * 対象外（理由）: 既存の画面（review.jsp の「週」「目標」、既存の設定画面の既存の行など）の、
 * M3 で触っていない文言。「ふりかえり」の「実験」区分は、ユーザーが確定した画面で、別途決める対象のため、
 * ここでは検査しない（review.jsp は、LINT:BEGIN〜LINT:END で囲んだ、新しく足した一部分だけを見る）。
 *
 * 対象（このテストが実際に読むファイル。新しいファイルを足したら、ここにも足す）:
 * - Java の文字列リテラルだけ（コメントは、内部の用語の説明に、禁止語を使わざるを得ないため対象外）
 *   constant/NoteSummaryConstants.java, constant/NightGoalConstants.java, constant/ReviewTextConstants.java,
 *   service/NoteSummaryBuilder.java, service/NoteSummaryService.java
 * - JSP 全体（新しい画面、または全体を読んでも既存の禁止語がないことを確かめた画面）:
 *   WEB-INF/views/settings.jsp, WEB-INF/views/settings-choice.jsp, WEB-INF/views/memory.jsp, WEB-INF/views/guide.jsp
 * - JSP の一部（LINT:BEGIN 〜 LINT:END の間だけ）: WEB-INF/views/review.jsp
 *
 * 「点」は、「時点」「点検」のような語を誤って拾わないよう、許す語をあらかじめ取り除いてから照合する。
 */
class GentleLanguageLintTest {

    private static final Path MAIN_JAVA = Paths.get("src", "main", "java", "app", "yurune");
    private static final Path WEBAPP = Paths.get("src", "main", "webapp");

    /** 「画面に出さない語」。 */
    private static final List<String> GENTLE_FORBIDDEN = List.of(
            "実験", "基準", "対照", "目標", "スコア", "連続", "達成", "未達", "悪化", "改善", "効果", "効く", "治療", "不眠症");

    /** ProhibitedExpressionTest（既存の禁止表現の検査）と同じ語。新しい画面にも、同じ基準をかける。 */
    private static final List<String> MEDICAL_FORBIDDEN = List.of(
            "治る", "予防", "症状", "眠れるようになる", "よく眠れる", "効果がある",
            "入眠した", "寝かしつけに成功", "不眠症の方向け", "患者向け", "良くなった", "悪くなった");

    private static final Pattern PDCA_WORD = Pattern.compile("pdca", Pattern.CASE_INSENSITIVE);

    /** 「点」の前後によく出る、採点や評価の意味ではない語。これらを取り除いてから「点」を探す。 */
    private static final List<String> ALLOWED_POINT_WORDS = List.of(
            "時点", "起点", "終点", "拠点", "焦点", "観点", "地点", "頂点", "要点", "点在", "点線", "点検", "点滅");

    private static final List<Path> JAVA_TEXT_FILES = List.of(
            Paths.get("constant", "NoteSummaryConstants.java"),
            Paths.get("constant", "NightGoalConstants.java"),
            Paths.get("constant", "ReviewTextConstants.java"),
            Paths.get("service", "NoteSummaryBuilder.java"),
            Paths.get("service", "NoteSummaryService.java"));

    private static final List<Path> JSP_WHOLE_FILES = List.of(
            Paths.get("WEB-INF", "views", "settings.jsp"),
            Paths.get("WEB-INF", "views", "settings-choice.jsp"),
            Paths.get("WEB-INF", "views", "memory.jsp"),
            Paths.get("WEB-INF", "views", "guide.jsp"));

    private static final List<Path> JSP_REGION_FILES = List.of(
            Paths.get("WEB-INF", "views", "review.jsp"));

    @Test
    void newOrChangedScreensAndWordingHaveNoForbiddenExpression() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path relative : JAVA_TEXT_FILES) {
            checkText(stringLiteralsOf(readFile(MAIN_JAVA.resolve(relative))), relative.toString(), violations);
        }
        for (Path relative : JSP_WHOLE_FILES) {
            checkText(stripJspNonText(readFile(WEBAPP.resolve(relative))), relative.toString(), violations);
        }
        for (Path relative : JSP_REGION_FILES) {
            // 印（LINT:BEGIN/END）は JSP のコメントの中にあるので、取り除く前の生の文字列から区間を切り出し、
            // 切り出したところだけを、あとでコメントを取り除いて検査する
            String regions = stripJspNonText(lintRegionsOf(readFile(WEBAPP.resolve(relative))));
            assertTrue(regions.contains("いまのところ"), relative + " に LINT:BEGIN の区間が見つからない（検査が空では意味がない）");
            checkText(regions, relative.toString(), violations);
        }
        assertTrue(violations.isEmpty(), "gentle language violations: " + violations);
    }

    private void checkText(String text, String fileName, List<String> violations) {
        for (String word : GENTLE_FORBIDDEN) {
            if (text.contains(word)) {
                violations.add(fileName + " : " + word);
            }
        }
        for (String word : MEDICAL_FORBIDDEN) {
            if (text.contains(word)) {
                violations.add(fileName + " : " + word);
            }
        }
        if (PDCA_WORD.matcher(text).find()) {
            violations.add(fileName + " : PDCA");
        }
        String withoutAllowedPointWords = text;
        for (String allowed : ALLOWED_POINT_WORDS) {
            withoutAllowedPointWords = withoutAllowedPointWords.replace(allowed, "");
        }
        if (withoutAllowedPointWords.contains("点")) {
            violations.add(fileName + " : 点");
        }
    }

    private static String readFile(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** 文字列リテラルだけを取り出す（コメントと、コードそのものは対象外）。 */
    private static String stringLiteralsOf(String source) {
        StringBuilder literals = new StringBuilder();
        int length = source.length();
        for (int index = 0; index < length; index++) {
            char current = source.charAt(index);
            if (current == '/' && index + 1 < length && source.charAt(index + 1) == '/') {
                while (index < length && source.charAt(index) != '\n') {
                    index++;
                }
            } else if (current == '/' && index + 1 < length && source.charAt(index + 1) == '*') {
                index += 2;
                while (index + 1 < length && !(source.charAt(index) == '*' && source.charAt(index + 1) == '/')) {
                    index++;
                }
                index++;
            } else if (current == '\'') {
                index++;
                while (index < length && source.charAt(index) != '\'') {
                    index += source.charAt(index) == '\\' ? 2 : 1;
                }
            } else if (current == '"') {
                index++;
                while (index < length && source.charAt(index) != '"') {
                    if (source.charAt(index) == '\\') {
                        index += 2;
                    } else {
                        literals.append(source.charAt(index));
                        index++;
                    }
                }
            }
        }
        return literals.toString();
    }

    /** JSP のコメントと、スクリプトレット／ディレクティブを取り除く（EL 式や HTML、タグの属性値は残す）。 */
    private static String stripJspNonText(String source) {
        return source.replaceAll("(?s)<%--.*?--%>", "").replaceAll("(?s)<%.*?%>", "");
    }

    /** LINT:BEGIN から LINT:END までの区間だけを取り出し、つなげる。 */
    private static String lintRegionsOf(String text) {
        StringBuilder regions = new StringBuilder();
        int from = 0;
        while (true) {
            int begin = text.indexOf("LINT:BEGIN", from);
            if (begin < 0) {
                break;
            }
            int end = text.indexOf("LINT:END", begin);
            if (end < 0) {
                break;
            }
            regions.append(text, begin, end);
            from = end + "LINT:END".length();
        }
        return regions.toString();
    }
}
