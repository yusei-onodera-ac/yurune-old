package app.yurune.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.config.DeploymentConfig;
import app.yurune.servlet.AppContextListener;

import jakarta.servlet.SessionCookieConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * 画面ファイル（JSP、タグ、JS、設定）を読んで、画面の守りを崩す書き方がないことを確かめる。
 */
class WebHardeningLintTest {

    private static final Path WEBAPP = Path.of("src", "main", "webapp");
    private static final Pattern POST_FORM = Pattern.compile("<form[^>]*method=\"post\"[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern INLINE_SCRIPT = Pattern.compile("<script(?![^>]*\\bsrc=)[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern EVENT_ATTRIBUTE = Pattern.compile("\\son[a-z]+\\s*=", Pattern.CASE_INSENSITIVE);
    private static final Pattern JAVASCRIPT_URL =
            Pattern.compile("(href|src|action)\\s*=\\s*[\"']\\s*javascript:", Pattern.CASE_INSENSITIVE);

    private static List<Path> viewFiles() throws IOException {
        try (Stream<Path> files = Files.walk(WEBAPP.resolve("WEB-INF"))) {
            return files.filter(path -> path.toString().endsWith(".jsp") || path.toString().endsWith(".tag")).toList();
        }
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void everyPostFormCarriesTheCsrfToken() throws IOException {
        for (Path file : viewFiles()) {
            String content = read(file);
            Matcher matcher = POST_FORM.matcher(content);
            while (matcher.find()) {
                assertTrue(content.substring(matcher.end()).startsWith("<y:csrf/>"), file + " の POST form に、トークンがない");
            }
        }
    }

    @Test
    void viewsHaveNoInlineScriptsOrEventHandlersSoTheStrictCspHolds() throws IOException {
        for (Path file : viewFiles()) {
            String content = read(file);
            assertFalse(INLINE_SCRIPT.matcher(content).find(), file + " に、インラインのスクリプトがある");
            assertFalse(EVENT_ATTRIBUTE.matcher(content).find(), file + " に、on○○ 属性がある");
            assertFalse(JAVASCRIPT_URL.matcher(content).find(), file + " に、javascript: の URL がある");
        }
    }

    @Test
    void clientScriptWritesTextWithoutInterpretingHtml() throws IOException {
        String script = read(WEBAPP.resolve("js").resolve("yurune.js"));

        assertFalse(script.contains("innerHTML"), "文字は textContent で入れる");
        assertFalse(script.contains("outerHTML"));
        assertFalse(script.contains("insertAdjacentHTML"));
        assertFalse(script.contains("document.write"));
        assertFalse(script.contains("eval("));
    }

    @Test
    void layoutPutsTheCsrfTokenInAMetaTagForFetch() throws IOException {
        assertTrue(read(WEBAPP.resolve("WEB-INF").resolve("tags").resolve("document.tag")).contains("name=\"csrf-token\""));
    }

    @Test
    void deploymentDescriptorOrdersTheFiltersAndHidesErrorDetails() throws IOException {
        String xml = read(WEBAPP.resolve("WEB-INF").resolve("web.xml"));

        assertTrue(xml.indexOf("securityHeadersFilter") < xml.indexOf("csrfFilter"), "ヘッダー → CSRF の順");
        assertTrue(xml.contains("<exception-type>java.lang.Throwable</exception-type>"));
        assertTrue(xml.contains("<tracking-mode>COOKIE</tracking-mode>"));
    }

    @Test
    void sessionCookieIsHttpOnlySecureAndSameSiteStrict() {
        Map<String, Object> settings = new HashMap<>();
        SessionCookieConfig cookie = FakeHttp.proxy(SessionCookieConfig.class, (self, called, args) -> {
            if (called.getName().startsWith("set")) {
                settings.put(called.getName(), args.length == 2 ? args[0] + "=" + args[1] : args[0]);
            }
            return null;
        });

        new AppContextListener().configureSessionCookie(cookie, new DeploymentConfig(true));

        assertEquals(true, settings.get("setHttpOnly"));
        assertEquals(true, settings.get("setSecure"));
        assertEquals("SameSite=Strict", settings.get("setAttribute"));
    }
}
