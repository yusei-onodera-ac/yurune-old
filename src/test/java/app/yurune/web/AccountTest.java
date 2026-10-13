package app.yurune.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.AppComponents;
import app.yurune.config.AppConfig;
import app.yurune.config.DeploymentConfig;
import app.yurune.constant.AuthConstants;
import app.yurune.constant.ViewConstants;
import app.yurune.domain.value.AuthenticatedUser;
import app.yurune.filter.AuthFilter;
import app.yurune.gateway.InMemoryAuthGateway;
import app.yurune.repository.DocumentUserStorage;
import app.yurune.repository.InMemoryDocumentStore;
import app.yurune.repository.StorageUnavailableException;
import app.yurune.repository.UserStorage;
import app.yurune.service.AttemptLimiter;
import app.yurune.service.AuthAccount;
import app.yurune.service.AuthException;
import app.yurune.service.AuthGateway;
import app.yurune.service.AuthService;
import app.yurune.service.DataEraseService;
import app.yurune.service.MutableClock;
import app.yurune.storage.PartialDeleteDocumentStore;

import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 登録とログイン（入力の検査、試行の制限、メール確認）、ログインの確認（Filter）、データの消去の再認証。
 */
class AccountTest {

    private static final String EMAIL = "taro@example.com";
    private static final String PASSWORD = "correct-horse-1";

    private final MutableClock clock = new MutableClock("2026-10-07T01:00:00Z", "Asia/Tokyo");
    private InMemoryAuthGateway gateway;
    private AuthService authService;

    private AuthService createService(AuthGateway authGateway, int emailFailures, int globalFailures, int registrations) {
        return new AuthService(authGateway,
                new AttemptLimiter(emailFailures, Duration.ofMinutes(15), clock),
                new AttemptLimiter(globalFailures, Duration.ofMinutes(5), clock),
                new AttemptLimiter(registrations, Duration.ofMinutes(60), clock),
                "demo@example.com");
    }

    @BeforeEach
    void setUp() {
        gateway = new InMemoryAuthGateway();
        authService = createService(gateway, 5, 200, 30);
    }

    private AuthException failure(Runnable action) {
        return assertThrows(AuthException.class, action::run);
    }

    // ---- 登録 ----

    @Test
    void registeredAccountsCanLogInWithANormalizedEmail() {
        AuthService.RegistrationResult result = authService.register("  Taro@Example.COM ", PASSWORD, true, true);
        assertFalse(result.isVerificationRequired());

        AuthenticatedUser user = authService.login("ＴＡＲＯ@example.com", PASSWORD);

        assertEquals(result.getUser().getUid(), user.getUid());
        assertEquals(EMAIL, user.getEmail());
        assertFalse(user.isDemo());
    }

    @Test
    void registrationNeedsBothConsentsAndAUsablePassword() {
        assertEquals(AuthException.Reason.CONSENT_MISSING, failure(() -> authService.register(EMAIL, PASSWORD, false, true)).getReason());
        assertEquals(AuthException.Reason.CONSENT_MISSING, failure(() -> authService.register(EMAIL, PASSWORD, true, false)).getReason());
        assertEquals(AuthException.Reason.WEAK_PASSWORD, failure(() -> authService.register(EMAIL, "short", true, true)).getReason());
        assertEquals(AuthException.Reason.INVALID_INPUT, failure(() -> authService.register("not-an-email", PASSWORD, true, true)).getReason());
        assertEquals(AuthException.Reason.INVALID_INPUT, failure(() -> authService.register(EMAIL, "x".repeat(129), true, true)).getReason());
    }

    @Test
    void anEmailCannotBeRegisteredTwice() {
        authService.register(EMAIL, PASSWORD, true, true);

        assertEquals(AuthException.Reason.EMAIL_ALREADY_USED, failure(() -> authService.register(EMAIL, PASSWORD, true, true)).getReason());
    }

    @Test
    void registrationsAreRateLimited() {
        AuthService limited = createService(gateway, 5, 200, 2);
        limited.register("a@example.com", PASSWORD, true, true);
        limited.register("b@example.com", PASSWORD, true, true);

        assertEquals(AuthException.Reason.TOO_MANY_ATTEMPTS, failure(() -> limited.register("c@example.com", PASSWORD, true, true)).getReason());

        clock.advance(Duration.ofMinutes(61));
        limited.register("c@example.com", PASSWORD, true, true);
    }

    // ---- ログイン ----

    @Test
    void wrongPasswordAndUnknownEmailLookTheSame() {
        authService.register(EMAIL, PASSWORD, true, true);

        assertEquals(AuthException.Reason.INVALID_CREDENTIALS, failure(() -> authService.login(EMAIL, "wrong-password")).getReason());
        assertEquals(AuthException.Reason.INVALID_CREDENTIALS, failure(() -> authService.login("nobody@example.com", PASSWORD)).getReason());
    }

    @Test
    void repeatedFailuresLockTheAccountForAWhileEvenForTheRightPassword() {
        authService.register(EMAIL, PASSWORD, true, true);
        for (int attempt = 0; attempt < 5; attempt++) {
            failure(() -> authService.login(EMAIL, "wrong-password"));
        }

        assertEquals(AuthException.Reason.TOO_MANY_ATTEMPTS, failure(() -> authService.login(EMAIL, PASSWORD)).getReason());

        clock.advance(Duration.ofMinutes(16));
        assertEquals(EMAIL, authService.login(EMAIL, PASSWORD).getEmail());
    }

    @Test
    void aSuccessfulLoginClearsTheFailureCount() {
        authService.register(EMAIL, PASSWORD, true, true);
        for (int attempt = 0; attempt < 4; attempt++) {
            failure(() -> authService.login(EMAIL, "wrong-password"));
        }
        authService.login(EMAIL, PASSWORD);

        for (int attempt = 0; attempt < 4; attempt++) {
            failure(() -> authService.login(EMAIL, "wrong-password"));
        }
        assertEquals(EMAIL, authService.login(EMAIL, PASSWORD).getEmail(), "成功のあとは、数え直し");
    }

    @Test
    void guessingAcrossManyEmailsIsStoppedByTheGlobalLimit() {
        AuthService limited = createService(gateway, 100, 3, 30);
        for (int attempt = 0; attempt < 3; attempt++) {
            String email = "user" + attempt + "@example.com";
            failure(() -> limited.login(email, PASSWORD));
        }

        assertEquals(AuthException.Reason.TOO_MANY_ATTEMPTS, failure(() -> limited.login("another@example.com", PASSWORD)).getReason());
    }

    @Test
    void aWrongPasswordIsStillRefusedWhenTheEmailIsMalformed() {
        assertEquals(AuthException.Reason.INVALID_INPUT, failure(() -> authService.login("", PASSWORD)).getReason());
        assertEquals(AuthException.Reason.INVALID_INPUT, failure(() -> authService.login(EMAIL, "")).getReason());
        assertEquals(AuthException.Reason.INVALID_INPUT, failure(() -> authService.login(EMAIL, null)).getReason());
    }

    @Test
    void anUnverifiedEmailCannotLogInAndRegistrationAsksForVerification() {
        AuthGateway unverified = new AuthGateway() {
            @Override
            public AuthAccount register(String email, String password) {
                return new AuthAccount("uid1", email, false);
            }

            @Override
            public AuthAccount signIn(String email, String password) {
                return new AuthAccount("uid1", email, false);
            }
        };
        AuthService strict = createService(unverified, 5, 200, 30);

        assertTrue(strict.register(EMAIL, PASSWORD, true, true).isVerificationRequired());
        assertEquals(AuthException.Reason.EMAIL_NOT_VERIFIED, failure(() -> strict.login(EMAIL, PASSWORD)).getReason());
    }

    @Test
    void theDemoAccountIsRecognizedByItsEmail() {
        gateway.register("demo@example.com", PASSWORD);

        assertTrue(authService.login("Demo@Example.com", PASSWORD).isDemo());
    }

    // ---- データの消去 ----

    @Test
    void erasingNeedsTheRightPasswordAndClearsOnlyThatUsersData() {
        InMemoryDocumentStore store = new InMemoryDocumentStore();
        UserStorage storage = new DocumentUserStorage(store, clock);
        List<String> forgotten = new ArrayList<>();
        DataEraseService erase = new DataEraseService(authService, storage, (uid, success) -> forgotten.add(uid));
        AuthenticatedUser alice = authService.register("alice@example.com", PASSWORD, true, true).getUser();
        AuthenticatedUser bob = authService.register("bob@example.com", PASSWORD, true, true).getUser();
        store.set("users/" + alice.getUid() + "/arms/a", java.util.Map.of("n", 1L));
        store.set("users/" + bob.getUid() + "/arms/a", java.util.Map.of("n", 1L));

        assertEquals(AuthException.Reason.INVALID_CREDENTIALS, assertThrows(AuthException.class,
                () -> erase.erase(alice, "wrong-password")).getReason());
        assertTrue(store.get("users/" + alice.getUid() + "/arms/a").isPresent(), "パスワードが違うときは、何も消さない");
        assertTrue(forgotten.isEmpty(), "パスワードが違うときは、メモリ上の状態も捨てない");

        erase.erase(alice, PASSWORD);

        assertTrue(store.get("users/" + alice.getUid() + "/arms/a").isEmpty());
        assertTrue(store.get("users/" + bob.getUid() + "/arms/a").isPresent());
        assertEquals(List.of(alice.getUid()), forgotten);
    }

    @Test
    void whenStorageFailsDuringEraseTheFailurePropagatesButMemoryStateIsStillDiscarded() {
        PartialDeleteDocumentStore store = new PartialDeleteDocumentStore();
        UserStorage storage = new DocumentUserStorage(store, clock);
        List<String> forgotten = new ArrayList<>();
        List<Boolean> outcomes = new ArrayList<>();
        DataEraseService erase = new DataEraseService(authService, storage, (uid, success) -> {
            forgotten.add(uid);
            outcomes.add(success);
        });
        AuthenticatedUser alice = authService.register("alice@example.com", PASSWORD, true, true).getUser();
        store.set("users/" + alice.getUid() + "/arms/a", java.util.Map.of("n", 1L));
        store.set("users/" + alice.getUid() + "/arms/b", java.util.Map.of("n", 2L));
        store.failAfterDeletes(1);

        assertThrows(StorageUnavailableException.class, () -> erase.erase(alice, PASSWORD));

        assertEquals(List.of(alice.getUid()), forgotten, "失敗しても、メモリ上の状態は捨てる（戻ってこないように）");
        assertEquals(List.of(false), outcomes);
    }

    @Test
    void erasingCanBeRetriedAfterAFailureAndFinishesWithoutTouchingOtherUsers() {
        PartialDeleteDocumentStore store = new PartialDeleteDocumentStore();
        UserStorage storage = new DocumentUserStorage(store, clock);
        DataEraseService erase = new DataEraseService(authService, storage, (uid, success) -> { });
        AuthenticatedUser alice = authService.register("alice@example.com", PASSWORD, true, true).getUser();
        AuthenticatedUser bob = authService.register("bob@example.com", PASSWORD, true, true).getUser();
        store.set("users/" + alice.getUid() + "/arms/a", java.util.Map.of("n", 1L));
        store.set("users/" + alice.getUid() + "/arms/b", java.util.Map.of("n", 2L));
        store.set("users/" + alice.getUid() + "/arms/c", java.util.Map.of("n", 3L));
        store.set("users/" + bob.getUid() + "/arms/a", java.util.Map.of("n", 1L));
        store.failAfterDeletes(1);

        assertThrows(StorageUnavailableException.class, () -> erase.erase(alice, PASSWORD));
        assertTrue(store.get("users/" + bob.getUid() + "/arms/a").isPresent(), "失敗しても、他の利用者のデータは消えない");

        store.failAfterDeletes(Integer.MAX_VALUE);
        erase.erase(alice, PASSWORD);

        assertTrue(store.get("users/" + alice.getUid() + "/arms/a").isEmpty());
        assertTrue(store.get("users/" + alice.getUid() + "/arms/b").isEmpty());
        assertTrue(store.get("users/" + alice.getUid() + "/arms/c").isEmpty());
        assertTrue(store.get("users/" + bob.getUid() + "/arms/a").isPresent(), "やり直したあとも、他の利用者のデータは消えない");
    }

    @Test
    void erasingCannotBeUsedToGuessPasswordsEndlessly() {
        UserStorage storage = new DocumentUserStorage(new InMemoryDocumentStore(), clock);
        DataEraseService erase = new DataEraseService(authService, storage, (uid, success) -> { });
        AuthenticatedUser alice = authService.register("alice@example.com", PASSWORD, true, true).getUser();

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThrows(AuthException.class, () -> erase.erase(alice, "wrong-password"));
        }

        assertEquals(AuthException.Reason.TOO_MANY_ATTEMPTS, assertThrows(AuthException.class,
                () -> erase.erase(alice, PASSWORD)).getReason());
    }

    // ---- ログインの確認（Filter） ----

    private AuthFilter createAuthFilter() {
        AppComponents components = AppComponents.create(new DeploymentConfig(false), Clock.systemUTC(), AppConfig.defaults());
        ServletContext context = FakeHttp.proxy(ServletContext.class, (self, called, args) ->
                "getAttribute".equals(called.getName()) && ViewConstants.ATTR_COMPONENTS.equals(args[0]) ? components : null);
        AuthFilter filter = new AuthFilter();
        filter.init(FakeHttp.proxy(FilterConfig.class, (self, called, args) ->
                "getServletContext".equals(called.getName()) ? context : null));
        return filter;
    }

    @Test
    void pagesSendAnonymousVisitorsToTheLoginScreen() throws Exception {
        FakeHttp.Exchange exchange = new FakeHttp.Exchange();
        exchange.servletPath = "/home";

        createAuthFilter().doFilter(exchange.request(), exchange.response(), exchange.chain());

        assertEquals("/login", exchange.redirectedTo);
        assertEquals(0, exchange.chainCalls.get());
    }

    @Test
    void operationsFromAnonymousVisitorsAreRefusedWith401() throws Exception {
        for (String path : List.of("/night/observe", "/night/end", "/morning", "/settings/erase", "/log", "/night")) {
            FakeHttp.Exchange exchange = new FakeHttp.Exchange();
            exchange.method = "POST";
            exchange.servletPath = path;

            createAuthFilter().doFilter(exchange.request(), exchange.response(), exchange.chain());

            assertEquals(401, exchange.errorStatus, path);
            assertEquals(0, exchange.chainCalls.get(), path);
        }
    }

    @Test
    void publicPagesAndStaticFilesAreOpenToEveryone() throws Exception {
        for (String path : List.of("/login", "/register", "/terms", "/privacy", "/healthz", "/ready", "/css/yurune.css", "/js/yurune.js")) {
            FakeHttp.Exchange exchange = new FakeHttp.Exchange();
            exchange.servletPath = path;

            createAuthFilter().doFilter(exchange.request(), exchange.response(), exchange.chain());

            assertEquals(1, exchange.chainCalls.get(), path);
        }
    }

    @Test
    void aSignedInUserPassesThrough() throws Exception {
        FakeHttp.Exchange exchange = new FakeHttp.Exchange();
        exchange.servletPath = "/home";
        exchange.sessionAttributes.put(AuthConstants.SESSION_USER, new AuthenticatedUser("u1", EMAIL, false));

        createAuthFilter().doFilter(exchange.request(), exchange.response(), exchange.chain());

        assertEquals(1, exchange.chainCalls.get());
    }

    @Test
    void protectedPathsAreNotInThePublicList() {
        AppComponents components = AppComponents.create(new DeploymentConfig(false), Clock.systemUTC(), AppConfig.defaults());
        for (String path : List.of("/", "/home", "/log", "/prepare", "/night", "/night/mode", "/night/end", "/night/observe",
                "/review", "/settings", "/settings/erase", "/morning", "/logout")) {
            assertFalse(components.getWebRequestPolicy().isPublicPath(path), path);
        }
    }
}
