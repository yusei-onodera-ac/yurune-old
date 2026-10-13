package app.yurune;

import app.yurune.config.AppConfig;
import app.yurune.config.DeploymentConfig;
import app.yurune.constant.AuthConstants;
import app.yurune.constant.LlmConstants;
import app.yurune.constant.SoundCreditConstants;
import app.yurune.constant.TimeConstants;
import app.yurune.controller.AccountController;
import app.yurune.controller.HomeController;
import app.yurune.controller.InfoController;
import app.yurune.controller.LogController;
import app.yurune.controller.MemoryController;
import app.yurune.controller.MorningController;
import app.yurune.controller.NightController;
import app.yurune.controller.PrepareController;
import app.yurune.controller.ReviewController;
import app.yurune.controller.SettingsController;
import app.yurune.domain.value.AuthenticatedUser;
import app.yurune.domain.value.NightGoal;
import app.yurune.domain.value.UserSettings;
import app.yurune.domain.value.WakeDate;
import app.yurune.gateway.AccessTokenProvider;
import app.yurune.gateway.FirebaseAuthGateway;
import app.yurune.gateway.FirestoreDocumentStore;
import app.yurune.gateway.GeminiRestGateway;
import app.yurune.gateway.GoogleTtsGateway;
import app.yurune.gateway.InMemoryAuthGateway;
import app.yurune.gateway.MetadataServerTokenProvider;
import app.yurune.gateway.ModelArmorRestGateway;
import app.yurune.gateway.StaticTokenProvider;
import app.yurune.repository.AuditingDecisionRepository;
import app.yurune.repository.DecisionRepository;
import app.yurune.repository.DocumentStore;
import app.yurune.repository.DocumentUserStorage;
import app.yurune.repository.InMemoryDocumentStore;
import app.yurune.repository.UserRepositories;
import app.yurune.repository.UserStorage;
import app.yurune.sample.DemoDataSeeder;
import app.yurune.service.ArmorGateway;
import app.yurune.service.AuditLogger;
import app.yurune.service.AttemptLimiter;
import app.yurune.service.AuthGateway;
import app.yurune.service.AuthService;
import app.yurune.service.BetaSampler;
import app.yurune.service.CircuitBreaker;
import app.yurune.service.ConsentService;
import app.yurune.service.ConversationPromptBuilder;
import app.yurune.service.ConversationService;
import app.yurune.service.CsrfTokenService;
import app.yurune.service.DataEraseService;
import app.yurune.service.DisabledArmorGateway;
import app.yurune.service.DisabledSpeechGateway;
import app.yurune.service.ForbiddenDomainFilter;
import app.yurune.service.GeminiNightAgent;
import app.yurune.service.GeminiRequestBuilder;
import app.yurune.service.GeminiResponseParser;
import app.yurune.service.GuardStatistics;
import app.yurune.service.InjectionDetector;
import app.yurune.service.InputGuard;
import app.yurune.service.LearningService;
import app.yurune.service.LlmCallLimiter;
import app.yurune.service.LlmGateway;
import app.yurune.service.MemoryService;
import app.yurune.service.MorningCheckService;
import app.yurune.service.NightAgent;
import app.yurune.service.NightReviewService;
import app.yurune.service.NightSessionService;
import app.yurune.service.NoteService;
import app.yurune.service.NoteSummaryService;
import app.yurune.service.OutputGuard;
import app.yurune.service.PiiMasker;
import app.yurune.service.PlanService;
import app.yurune.service.PromptSecrets;
import app.yurune.service.ReadinessService;
import app.yurune.service.RecordService;
import app.yurune.service.ResilientNightAgent;
import app.yurune.service.RuleBasedNightAgent;
import app.yurune.service.SecurityHeaderPolicy;
import app.yurune.service.SettingsService;
import app.yurune.service.SoundCreditService;
import app.yurune.service.ThompsonOptionSelector;
import app.yurune.service.SpeechGateway;
import app.yurune.service.ToolGuard;
import app.yurune.service.WebRequestPolicy;
import app.yurune.service.WeekComparisonService;
import app.yurune.domain.value.ControlState;
import app.yurune.domain.value.DegradationKind;
import app.yurune.service.AvailabilityTracker;
import app.yurune.service.TelemetryCounters;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 部品の組み立て（ここだけが、どの実装を使うかを知っている）。
 * 全員で共有する部品（守り、LLM の接続、認証）は 1 つだけ作る。
 * 利用者ごとの部品（保存先、学習、夜の制御、画面の入口）は、ログインした利用者ごとに作り、その人の保存先にだけ結びつける。
 * これで、別の利用者のデータには、触れる窓口がなくなる。
 */
public final class AppComponents {

    private static final String ENV_GEMINI_API_KEY = "GEMINI_API_KEY";
    private static final String ENV_GEMINI_MODEL = "GEMINI_MODEL";
    /** 試験で、擬似の Gemini に向けるための設定。本番では使わない。 */
    private static final String ENV_GEMINI_ENDPOINT = "GEMINI_ENDPOINT";
    private static final String FIRESTORE_URL = "https://firestore.googleapis.com";
    private static final String IDENTITY_TOOLKIT_URL = "https://identitytoolkit.googleapis.com";
    private static final String EMULATOR_SCHEME = "http://";
    private static final String AUTH_EMULATOR_PATH = "/identitytoolkit.googleapis.com";
    /** 同時に持っておく、利用者ごとの部品の数。超えたら、使われていないものから捨てる（データは保存先にあるので、失われない）。 */
    private static final int USER_CACHE_LIMIT = 500;
    private static final int USER_CACHE_INITIAL_CAPACITY = 16;
    private static final float USER_CACHE_LOAD_FACTOR = 0.75f;
    /** LLM 呼び出しの間隔による拒否を、運用の記録で区別する名前。 */
    private static final ControlState RATE_LIMIT_PURPOSE_NIGHT = ControlState.NIGHT;
    private static final ControlState RATE_LIMIT_PURPOSE_TALK = ControlState.TALK;

    private final Clock clock;
    private final AppConfig config;
    private final SecurityHeaderPolicy securityHeaderPolicy = new SecurityHeaderPolicy();
    private final CsrfTokenService csrfTokenService = new CsrfTokenService();
    private final WebRequestPolicy webRequestPolicy = new WebRequestPolicy();
    private final ToolGuard toolGuard;
    private final RuleBasedNightAgent ruleBasedAgent;
    private final LlmGateway llmGateway;
    private final NightAgent llmAgent;
    private final CircuitBreaker llmBreaker;
    private final InputGuard inputGuard;
    private final OutputGuard outputGuard;
    private final ConversationPromptBuilder conversationPromptBuilder;
    private final ArmorGateway armorGateway;
    private final SpeechGateway speechGateway;
    private final GuardStatistics guardStatistics;
    private final AuditLogger auditLogger;
    private final UserStorage userStorage;
    private final AuthService authService;
    private final AccountController accountController;
    private final InfoController infoController;
    private final Map<String, UserComponents> users = new LinkedHashMap<>(USER_CACHE_INITIAL_CAPACITY, USER_CACHE_LOAD_FACTOR, true);
    /** 審査用アカウントに、デモのデータを置いた日。日が変わったら、部品を作り直して、その日の日付で入れ直す。 */
    private final Map<String, WakeDate> demoDataDays = new HashMap<>();
    // ---- 運用の記録（可観測性）の部品 ----
    private final TelemetryCounters telemetryCounters;
    private final AvailabilityTracker armorAvailability;
    // ---- 運用の記録（可観測性）の部品。準備ができているかの確認 ----
    private final ReadinessService readinessService;

    private AppComponents(Clock clock, DeploymentConfig deployment, AppConfig config, Consumer<String> logSink) {
        this.clock = clock;
        this.config = config;
        this.auditLogger = new AuditLogger(clock, deployment.getProjectId(), logSink);
        ForbiddenDomainFilter forbiddenDomainFilter = new ForbiddenDomainFilter();
        PromptSecrets promptSecrets = new PromptSecrets();
        PiiMasker piiMasker = new PiiMasker();
        this.ruleBasedAgent = new RuleBasedNightAgent(config);
        this.llmGateway = createLlmGateway(config, auditLogger);
        this.llmAgent = llmGateway == null ? null : new GeminiNightAgent(llmGateway,
                new GeminiRequestBuilder(config, promptSecrets),
                new GeminiResponseParser(forbiddenDomainFilter, piiMasker), auditLogger);
        this.llmBreaker = new CircuitBreaker(LlmConstants.BREAKER_FAILURE_LIMIT,
                Duration.ofSeconds(LlmConstants.BREAKER_OPEN_SEC), clock,
                state -> auditLogger.control(DegradationKind.LLM_BREAKER, toControlState(state)));
        this.conversationPromptBuilder = new ConversationPromptBuilder(promptSecrets);
        this.inputGuard = new InputGuard(piiMasker, new InjectionDetector(), forbiddenDomainFilter);
        // 守る指示は、登録済みのものだけが対象なので、指示を作る部品を作ったあとで作る
        this.outputGuard = new OutputGuard(forbiddenDomainFilter, piiMasker, promptSecrets);
        this.toolGuard = new ToolGuard(config, outputGuard);
        this.armorGateway = createArmorGateway(deployment, clock, auditLogger);
        this.speechGateway = createSpeechGateway(deployment, clock, auditLogger);

        DocumentStore store = createStore(deployment, clock, auditLogger);
        this.userStorage = new DocumentUserStorage(store, clock);
        this.authService = createAuthService(deployment, clock);
        this.accountController = new AccountController(authService, new ConsentService(userStorage, clock), csrfTokenService,
                auditLogger);
        this.infoController = new InfoController(new SoundCreditService(SoundCreditConstants.ENTRIES));

        // ---- 運用の記録（可観測性）の配線（A3、A4） ----
        this.telemetryCounters = new TelemetryCounters();
        this.guardStatistics = new GuardStatistics(telemetryCounters);
        this.armorAvailability = new AvailabilityTracker(
                down -> auditLogger.control(DegradationKind.ARMOR_DOWN, down ? ControlState.DOWN : ControlState.UP));
        // 注意: 停止スイッチの状態の「起動時」記録は、ここに置かない。AppComponents.create() は、本物の起動で 1 回だけ
        // でなく、多くの既存テストが、共有の記録先（lines::add など）に対して、テストごとに作り直して呼ぶため、
        // ここで記録すると、既存テストの「ちょうど N 行」という前提を広く壊してしまう（そのため、ここでは記録しない）。

        // ---- 運用の記録（可観測性）の配線。準備ができているかの確認（B1） ----
        AvailabilityTracker storageAvailability = new AvailabilityTracker(
                down -> auditLogger.control(DegradationKind.STORAGE_DOWN, down ? ControlState.DOWN : ControlState.UP));
        this.readinessService = new ReadinessService(store, clock, storageAvailability);
    }

    public static AppComponents create(DeploymentConfig deployment) {
        return create(deployment, Clock.system(ZoneId.of(TimeConstants.TIME_ZONE_ID)), AppConfig.fromEnvironment());
    }

    public static AppComponents create(DeploymentConfig deployment, Clock clock, AppConfig config) {
        return create(deployment, clock, config, System.out::println);
    }

    /** @param logSink 運用の記録（JSON の 1 行）の出し先。本番は標準出力（Cloud Logging が読む）。試験では、集めて調べる。 */
    public static AppComponents create(DeploymentConfig deployment, Clock clock, AppConfig config, Consumer<String> logSink) {
        return new AppComponents(clock, deployment, config, logSink);
    }

    // ---- 全員で共有する部品 ----

    /** サーキットブレーカーの開閉の状態を、control ログの状態の名前に変える。文字列はこれまでと同じ。 */
    private static ControlState toControlState(CircuitBreaker.State state) {
        return switch (state) {
            case CLOSED -> ControlState.CLOSED;
            case OPEN -> ControlState.OPEN;
            case HALF_OPEN -> ControlState.HALF_OPEN;
        };
    }

    /**
     * LLM（Gemini）の窓口。Gemini のキー（環境変数）があり、停止の設定がないときだけ作る。
     * なければ null で、夜の制御は規則だけで、会話は定型文 T-DOWN で動く（キーが無い環境でも、アプリは動く）。
     */
    private static LlmGateway createLlmGateway(AppConfig config, AuditLogger auditLogger) {
        String apiKey = System.getenv(ENV_GEMINI_API_KEY);
        if (!config.isAgentEnabled() || apiKey == null || apiKey.isBlank()) {
            return null;
        }
        String model = System.getenv(ENV_GEMINI_MODEL);
        String endpoint = System.getenv(ENV_GEMINI_ENDPOINT);
        return new GeminiRestGateway(apiKey, model == null || model.isBlank() ? LlmConstants.DEFAULT_MODEL : model,
                endpoint == null || endpoint.isBlank() ? LlmConstants.ENDPOINT : endpoint, auditLogger);
    }

    /** 入出力の検査サービス（Model Armor）。テンプレートの設定があるときだけ、自前の検査に重ねる。 */
    private static ArmorGateway createArmorGateway(DeploymentConfig deployment, Clock clock, AuditLogger auditLogger) {
        if (!deployment.usesModelArmor()) {
            return new DisabledArmorGateway();
        }
        String endpoint = deployment.getArmorEndpoint();
        String base = endpoint != null && !endpoint.isBlank() ? endpoint
                : ModelArmorRestGateway.createEndpointBase(deployment.getArmorLocation());
        return new ModelArmorRestGateway(base, deployment.getProjectId(), deployment.getArmorLocation(),
                deployment.getArmorTemplate(), createCloudTokenProvider(deployment, clock), auditLogger);
    }

    /** サーバーでの読み上げ（Cloud Text-to-Speech）。設定があるときだけ。ないときは、端末の読み上げ。 */
    private static SpeechGateway createSpeechGateway(DeploymentConfig deployment, Clock clock, AuditLogger auditLogger) {
        if (!deployment.usesServerSpeech()) {
            return new DisabledSpeechGateway();
        }
        String endpoint = deployment.getTtsEndpoint();
        AccessTokenProvider tokens = createCloudTokenProvider(deployment, clock);
        return endpoint != null && !endpoint.isBlank() ? new GoogleTtsGateway(endpoint, tokens, auditLogger)
                : new GoogleTtsGateway(tokens, auditLogger);
    }

    private static AccessTokenProvider createCloudTokenProvider(DeploymentConfig deployment, Clock clock) {
        String token = deployment.getAccessToken();
        return token != null && !token.isBlank() ? new StaticTokenProvider(token) : new MetadataServerTokenProvider(clock);
    }

    private static DocumentStore createStore(DeploymentConfig deployment, Clock clock, AuditLogger auditLogger) {
        if (!deployment.usesFirestore()) {
            return new InMemoryDocumentStore();
        }
        String emulator = deployment.getFirestoreEmulatorHost();
        boolean useEmulator = emulator != null && !emulator.isBlank();
        String baseUrl = useEmulator ? EMULATOR_SCHEME + emulator : FIRESTORE_URL;
        return new FirestoreDocumentStore(FirestoreDocumentStore.createDocumentsUrl(baseUrl, deployment.getProjectId()),
                createTokenProvider(deployment, useEmulator, clock), auditLogger);
    }

    private static AccessTokenProvider createTokenProvider(DeploymentConfig deployment, boolean useEmulator, Clock clock) {
        if (useEmulator) {
            return new StaticTokenProvider(StaticTokenProvider.EMULATOR_OWNER);
        }
        String token = deployment.getAccessToken();
        return token != null && !token.isBlank() ? new StaticTokenProvider(token) : new MetadataServerTokenProvider(clock);
    }

    private static AuthService createAuthService(DeploymentConfig deployment, Clock clock) {
        AuthGateway gateway = createAuthGateway(deployment);
        return new AuthService(gateway,
                new AttemptLimiter(AuthConstants.LOGIN_MAX_FAILURES, Duration.ofMinutes(AuthConstants.LOGIN_WINDOW_MINUTES), clock),
                new AttemptLimiter(AuthConstants.GLOBAL_MAX_FAILURES, Duration.ofMinutes(AuthConstants.GLOBAL_WINDOW_MINUTES), clock),
                new AttemptLimiter(AuthConstants.REGISTRATION_MAX, Duration.ofMinutes(AuthConstants.REGISTRATION_WINDOW_MINUTES), clock),
                deployment.getDemoEmail());
    }

    private static AuthGateway createAuthGateway(DeploymentConfig deployment) {
        if (deployment.usesFirebaseAuth()) {
            String emulator = deployment.getAuthEmulatorHost();
            String baseUrl = emulator != null && !emulator.isBlank()
                    ? EMULATOR_SCHEME + emulator + AUTH_EMULATOR_PATH : IDENTITY_TOOLKIT_URL;
            return new FirebaseAuthGateway(baseUrl, deployment.getFirebaseApiKey());
        }
        InMemoryAuthGateway gateway = new InMemoryAuthGateway();
        String demoEmail = deployment.getDemoEmail();
        String demoPassword = deployment.getDemoPassword();
        if (demoEmail != null && !demoEmail.isBlank() && demoPassword != null && !demoPassword.isBlank()) {
            gateway.register(demoEmail, demoPassword);
        }
        return gateway;
    }

    // ---- 利用者ごとの部品 ----

    /** ログインした利用者の部品を返す。なければ、その人の保存先に結びつけて作る。 */
    public synchronized UserComponents forUser(AuthenticatedUser user) {
        if (user.isDemo()) {
            WakeDate today = WakeDate.today(clock);
            if (!today.equals(demoDataDays.put(user.getUid(), today))) {
                users.remove(user.getUid());
            }
        }
        UserComponents existing = users.get(user.getUid());
        if (existing != null) {
            return existing;
        }
        UserComponents created = createUserComponents(user);
        users.put(user.getUid(), created);
        evictLeastRecentlyUsed();
        return created;
    }

    /** 利用者について持っているメモリ上の状態を捨てる（データを消したあと）。 */
    public synchronized void forget(String uid) {
        users.remove(uid);
    }

    private void evictLeastRecentlyUsed() {
        while (users.size() > USER_CACHE_LIMIT) {
            users.remove(users.keySet().iterator().next());
        }
    }

    private UserComponents createUserComponents(AuthenticatedUser user) {
        UserRepositories repositories = userStorage.openFor(user.getUid());
        if (user.isDemo()) {
            new DemoDataSeeder(clock).seedIfNeeded(repositories);
        }
        DecisionRepository decisionRepository = new AuditingDecisionRepository(repositories.getDecisionRepository(),
                auditLogger::decision);
        RecordService recordService = new RecordService(repositories.getSleepRecordRepository(), clock);
        SettingsService settingsService = new SettingsService(repositories.getUserProfileRepository());
        Supplier<UserSettings> enforcedSettings = settingsService::findEnforcedSettings;
        LearningService learningService = new LearningService(repositories.getArmRepository(), enforcedSettings);
        // 夜のめやすは、設定から、そのつど読む（変えたら、次の夜の見直しから変わる）
        Supplier<NightGoal> goalSupplier = () -> enforcedSettings.get().getNightGoal();
        NightReviewService reviewService = new NightReviewService(repositories.getNightRepository(),
                repositories.getArmRepository(), repositories.getPdcaStateRepository(), decisionRepository, auditLogger,
                clock, enforcedSettings, goalSupplier);
        PlanService planService = new PlanService(repositories.getNightRepository(),
                new ThompsonOptionSelector(repositories.getArmRepository(), new BetaSampler()),
                repositories.getPdcaStateRepository(), clock, enforcedSettings);
        NightSessionService nightSessionService = new NightSessionService(repositories.getNightSessionRepository(),
                repositories.getNightRepository(), decisionRepository, createNightAgent(), toolGuard, config, clock,
                enforcedSettings, reviewService::reviewEndedNight, auditLogger);
        MorningCheckService morningCheckService = new MorningCheckService(repositories.getNightRepository(),
                decisionRepository, learningService, recordService, clock, reviewService::reviewAfterMorning);
        DataEraseService dataEraseService = new DataEraseService(authService, userStorage, (uid, success) -> {
            forget(uid);
            auditLogger.erase(AuditLogger.hashUid(uid), success);
        });

        return new UserComponents(
                new HomeController(recordService, morningCheckService),
                new LogController(recordService),
                new PrepareController(planService, settingsService),
                new NightController(planService, nightSessionService, config, createConversationService(), speechGateway,
                        settingsService),
                new ReviewController(new WeekComparisonService(recordService),
                        new NoteSummaryService(repositories.getNightRepository(), repositories.getPdcaStateRepository(),
                                goalSupplier, clock),
                        goalSupplier),
                new SettingsController(user, dataEraseService, auditLogger, settingsService),
                new MorningController(morningCheckService),
                new MemoryController(new MemoryService(new NoteService(repositories.getArmRepository())), goalSupplier));
    }

    /** 会話。LLM の呼び出しの間隔と日次の上限は、利用者ごと。止まったときの扱い（ブレーカー）と、守りの数え方は、全員で共有する。 */
    private ConversationService createConversationService() {
        return new ConversationService(inputGuard, outputGuard, conversationPromptBuilder, llmGateway, llmBreaker,
                new LlmCallLimiter(config.getTalkMinIntervalSec(), config.getLlmDailyCap(), clock,
                        RATE_LIMIT_PURPOSE_TALK, telemetryCounters, auditLogger),
                armorGateway,
                event -> {
                    guardStatistics.accept(event);
                    auditLogger.conversation(event);
                }, auditLogger, armorAvailability);
    }

    /** 夜のエージェント。呼び出しの間隔と日次の上限は、利用者ごと。止まったときの扱い（ブレーカー）は、全員で共有する。 */
    private NightAgent createNightAgent() {
        if (llmAgent == null) {
            return ruleBasedAgent;
        }
        return new ResilientNightAgent(ruleBasedAgent, llmAgent,
                new LlmCallLimiter(config.getLlmMinIntervalSec(), config.getLlmDailyCap(), clock,
                        RATE_LIMIT_PURPOSE_NIGHT, telemetryCounters, auditLogger),
                llmBreaker);
    }

    // ---- Servlet、Filter が使う、共有の部品 ----

    public AccountController getAccountController() {
        return accountController;
    }

    public SecurityHeaderPolicy getSecurityHeaderPolicy() {
        return securityHeaderPolicy;
    }

    public CsrfTokenService getCsrfTokenService() {
        return csrfTokenService;
    }

    public WebRequestPolicy getWebRequestPolicy() {
        return webRequestPolicy;
    }

    public AuditLogger getAuditLogger() {
        return auditLogger;
    }

    public InfoController getInfoController() {
        return infoController;
    }

    /** 保存先に読み書きできるかで、稼働の準備ができているかを判断する。 */
    public ReadinessService getReadinessService() {
        return readinessService;
    }

    /** 起動時の記録が、停止スイッチの状態を読むために使う。 */
    public AppConfig getConfig() {
        return config;
    }
}
