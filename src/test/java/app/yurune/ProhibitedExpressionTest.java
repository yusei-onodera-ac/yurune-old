package app.yurune;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * 受け入れ条件 AT-04: 画面の文言、説明文、コードの文面に、禁止表現がない。
 * 禁止領域の検査データ（語の一覧）だけは、検査の対象から外す。
 */
class ProhibitedExpressionTest {

    private static final List<String> PROHIBITED = List.of(
            "治る", "治療", "改善", "予防", "症状", "眠れるようになる", "よく眠れる", "効果がある",
            "入眠した", "寝かしつけに成功", "不眠症の方向け", "患者向け", "良くなった", "悪くなった");

    private static final String EXCLUDED_FILE = "ForbiddenTermConstants.java";

    @Test
    void sourcesContainNoProhibitedExpression() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Paths.get("src", "main"))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                if (file.getFileName().toString().equals(EXCLUDED_FILE) || !isTextFile(file)) {
                    continue;
                }
                String text = Files.readString(file, StandardCharsets.UTF_8);
                for (String word : PROHIBITED) {
                    if (text.contains(word)) {
                        violations.add(file + " : " + word);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), "prohibited expressions: " + violations);
    }

    private boolean isTextFile(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".java") || name.endsWith(".jsp") || name.endsWith(".tag")
                || name.endsWith(".js") || name.endsWith(".css")
                || name.endsWith(".html") || name.endsWith(".webmanifest");
    }
}
