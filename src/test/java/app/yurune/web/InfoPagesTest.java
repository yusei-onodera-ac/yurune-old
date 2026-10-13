package app.yurune.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.AppComponents;
import app.yurune.config.AppConfig;
import app.yurune.config.DeploymentConfig;
import app.yurune.constant.AuthConstants;
import app.yurune.constant.InfoConstants;
import app.yurune.constant.SoundCreditConstants;
import app.yurune.constant.ViewConstants;
import app.yurune.controller.InfoController;
import app.yurune.controller.Navigation;
import app.yurune.controller.view.SoundCreditView;
import app.yurune.domain.value.AuthenticatedUser;
import app.yurune.domain.value.SoundCredit;
import app.yurune.filter.AuthFilter;
import app.yurune.service.SoundCreditService;

import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * 設定の下の 3 つの画面（覚えたこと、音源のクレジット、ご利用にあたって）。
 * ログインの要否、本人の分だけが出ること、空の状態、台帳の表示、固定の文言を確かめる。
 */
class InfoPagesTest {

    private static final List<String> PATHS = List.of(InfoConstants.PATH_MEMORY, InfoConstants.PATH_CREDITS, InfoConstants.PATH_GUIDE);
    private static final Path VIEWS = Path.of("src", "main", "webapp", "WEB-INF", "views");
    private static final Pattern EXTERNAL_LINK = Pattern.compile("<a\\b[^>]*target=\"_blank\"[^>]*>");

    private final AppComponents components =
            AppComponents.create(new DeploymentConfig(false), Clock.systemUTC(), AppConfig.defaults());

    private static String read(String viewName) throws IOException {
        return Files.readString(VIEWS.resolve(viewName), StandardCharsets.UTF_8);
    }

    private AuthFilter createAuthFilter() {
        ServletContext context = FakeHttp.proxy(ServletContext.class, (self, called, args) ->
                "getAttribute".equals(called.getName()) && ViewConstants.ATTR_COMPONENTS.equals(args[0]) ? components : null);
        AuthFilter filter = new AuthFilter();
        filter.init(FakeHttp.proxy(FilterConfig.class, (self, called, args) ->
                "getServletContext".equals(called.getName()) ? context : null));
        return filter;
    }

    // ---- ログイン ----

    @Test
    void thePagesAreForSignedInUsersOnly() throws Exception {
        for (String path : PATHS) {
            assertFalse(components.getWebRequestPolicy().isPublicPath(path), path);

            FakeHttp.Exchange anonymous = new FakeHttp.Exchange();
            anonymous.servletPath = path;
            createAuthFilter().doFilter(anonymous.request(), anonymous.response(), anonymous.chain());
            assertEquals(AuthConstants.PATH_LOGIN, anonymous.redirectedTo, path);
            assertEquals(0, anonymous.chainCalls.get(), path);

            FakeHttp.Exchange signedIn = new FakeHttp.Exchange();
            signedIn.servletPath = path;
            signedIn.sessionAttributes.put(AuthConstants.SESSION_USER, new AuthenticatedUser("u1", "taro@example.com", false));
            createAuthFilter().doFilter(signedIn.request(), signedIn.response(), signedIn.chain());
            assertEquals(1, signedIn.chainCalls.get(), path);
        }
    }

    // ---- 覚えたこと ----

    private List<?> showMemoryAs(AuthenticatedUser user, FakeHttp.Exchange exchange) {
        Navigation navigation = components.forUser(user).getMemoryController().showMemory(exchange.request());
        assertEquals(InfoConstants.VIEW_MEMORY, navigation.getTarget());
        assertFalse(navigation.isRedirect());
        return (List<?>) exchange.requestAttributes.get(InfoConstants.ATTR_MEMORY_GROUPS);
    }

    @Test
    void theDemoAccountStartsWithNoCountsLikeEveryoneElse() {
        FakeHttp.Exchange exchange = new FakeHttp.Exchange();

        List<?> groups = showMemoryAs(new AuthenticatedUser("demo-uid", "demo@example.com", true), exchange);

        assertTrue(groups.isEmpty(), "審査用アカウントにも、学習の結果（サンプルの集計）は置かない");
        assertEquals(3, exchange.requestAttributes.get(InfoConstants.ATTR_MEMORY_MIN_TRIALS));
    }

    @Test
    void aNewUserSeesAnEmptyStateAndNeverTheDemoAccountsCounts() {
        showMemoryAs(new AuthenticatedUser("demo-uid", "demo@example.com", true), new FakeHttp.Exchange());
        FakeHttp.Exchange exchange = new FakeHttp.Exchange();

        List<?> groups = showMemoryAs(new AuthenticatedUser("new-uid", "new@example.com", false), exchange);

        assertTrue(groups.isEmpty());
    }

    @Test
    void theMemoryPageOffersNoWayToChangeOrEraseAnything() throws IOException {
        String view = read("memory.jsp");

        assertFalse(view.contains("<form"));
        assertFalse(view.contains("<button"));
        assertTrue(view.contains("会話の全文は保存しません。"));
    }

    // ---- 音源のクレジット ----

    private static SoundCredit sampleCredit() {
        return new SoundCredit("rain", "試験用の雨", SoundCreditConstants.LICENSE_CC0, "https://example.com/rain?a=1&b=2",
                LocalDate.of(2026, 10, 7));
    }

    private static List<?> showCredits(InfoController controller, FakeHttp.Exchange exchange) {
        Navigation navigation = controller.showCredits(exchange.request());
        assertEquals(InfoConstants.VIEW_CREDITS, navigation.getTarget());
        return (List<?>) exchange.requestAttributes.get(InfoConstants.ATTR_SOUND_CREDITS);
    }

    @Test
    void theShippedLedgerIsEmptySoThePageSaysThereIsNothingToList() {
        FakeHttp.Exchange exchange = new FakeHttp.Exchange();

        assertTrue(showCredits(components.getInfoController(), exchange).isEmpty());
    }

    @Test
    void anEntryAddedToTheLedgerReachesThePage() {
        InfoController controller = new InfoController(new SoundCreditService(List.of(sampleCredit())));

        List<?> credits = showCredits(controller, new FakeHttp.Exchange());

        assertEquals(1, credits.size());
        SoundCreditView view = (SoundCreditView) credits.get(0);
        assertEquals("試験用の雨", view.getNameText());
        assertEquals("rain", view.getKeyText());
        assertEquals("CC0 1.0", view.getLicenseText());
        assertEquals("https://example.com/rain?a=1&b=2", view.getSourceUrl());
        assertEquals("2026年10月7日", view.getAcquiredText());
    }

    @Test
    void theCreditsPageListsTheLedgerWithSafeExternalLinks() throws IOException {
        String view = read("credits.jsp");

        assertTrue(view.contains("<c:forEach var=\"credit\" items=\"${soundCredits}\">"));
        assertTrue(view.contains("<c:out value='${credit.sourceUrl}'/>"), "出典の URL は、そのまま書かず、エスケープして出す");
        assertTrue(view.contains("<c:out value=\"${credit.licenseText}\"/>"));
        assertTrue(view.contains("<c:out value=\"${credit.acquiredText}\"/>"));
    }

    @Test
    void everyLinkThatOpensANewTabKeepsTheOpenerAway() throws IOException {
        try (var files = Files.walk(Path.of("src", "main", "webapp", "WEB-INF"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".jsp") || path.toString().endsWith(".tag")).toList()) {
                Matcher matcher = EXTERNAL_LINK.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    assertTrue(matcher.group().contains("rel=\"noopener noreferrer\""), file + " : " + matcher.group());
                }
            }
        }
    }

    // ---- ご利用にあたって ----

    @Test
    void theGuideShowsWithoutUserData() {
        FakeHttp.Exchange exchange = new FakeHttp.Exchange();

        Navigation navigation = components.getInfoController().showGuide(exchange.request());

        assertEquals(InfoConstants.VIEW_GUIDE, navigation.getTarget());
        assertTrue(exchange.requestAttributes.isEmpty());
    }

    @Test
    void theGuideUsesTheFixedSentencesAsTheyAre() throws IOException {
        String view = read("guide.jsp");

        assertTrue(view.contains("眠っても大丈夫です。反応がなくなったら、静かに終わります。"));
        assertTrue(view.contains("つらさが続くときは、医師に相談してください。ここでは、病気や薬のことには答えられません。"));
        assertTrue(view.contains("病気の診断や、薬についての助言は、行いません。"));
    }
}
