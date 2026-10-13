package app.yurune.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.AppComponents;
import app.yurune.config.AppConfig;
import app.yurune.config.DeploymentConfig;
import app.yurune.constant.AuditEventSchema;
import app.yurune.constant.GuardConstants;
import app.yurune.constant.LogConstants;
import app.yurune.constant.ViewConstants;
import app.yurune.domain.entity.Decision;
import app.yurune.domain.entity.NightPlan;
import app.yurune.domain.value.AgentSource;
import app.yurune.domain.value.ClockTime;
import app.yurune.domain.value.DecisionPhase;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.DisplayText;
import app.yurune.domain.value.FactSheet;
import app.yurune.domain.value.FallbackReason;
import app.yurune.domain.value.LlmOutcome;
import app.yurune.domain.value.LlmPurpose;
import app.yurune.domain.value.NightMode;
import app.yurune.domain.value.Observation;
import app.yurune.domain.value.OptionName;
import app.yurune.domain.value.ToolRejectReason;
import app.yurune.domain.value.VolumePercent;
import app.yurune.domain.value.WakeDate;
import app.yurune.filter.TraceFilter;
import app.yurune.repository.AuditingDecisionRepository;
import app.yurune.repository.InMemoryDecisionRepository;
import app.yurune.repository.InMemoryNightRepository;
import app.yurune.repository.InMemoryNightSessionRepository;
import app.yurune.service.AgentDecisionContext;
import app.yurune.service.ArmorGateway;
import app.yurune.service.ArmorUnavailableException;
import app.yurune.service.ArmorVerdict;
import app.yurune.service.AuditLogger;
import app.yurune.service.AuthException;
import app.yurune.service.CircuitBreaker;
import app.yurune.service.ConversationPromptBuilder;
import app.yurune.service.ConversationReply;
import app.yurune.service.ConversationService;
import app.yurune.service.DisabledArmorGateway;
import app.yurune.service.ForbiddenDomainFilter;
import app.yurune.service.GeminiNightAgent;
import app.yurune.service.GeminiRequestBuilder;
import app.yurune.service.GeminiResponseParser;
import app.yurune.service.GuardEvent;
import app.yurune.service.GuardStatistics;
import app.yurune.service.InjectionDetector;
import app.yurune.service.InputGuard;
import app.yurune.service.LlmCallLimiter;
import app.yurune.service.LlmUnavailableException;
import app.yurune.service.MutableClock;
import app.yurune.service.NightAgent;
import app.yurune.service.NightSessionService;
import app.yurune.service.OutputGuard;
import app.yurune.service.PiiMasker;
import app.yurune.service.PromptSecrets;
import app.yurune.service.ResilientNightAgent;
import app.yurune.service.RuleBasedNightAgent;
import app.yurune.service.TelemetryCounters;
import app.yurune.service.ToolGuard;
import app.yurune.service.TraceContext;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 運用の記録（受け入れ条件 AT-06）: 会話の本文、メールアドレス、パスワード、個人情報が、記録のどこにも出ないこと。
 * 記録は、トレース ID で 1 本につながり、種類と件数だけを持つこと。
 */
class AuditLogTest {

    private static final String SECRET_UTTERANCE = "ひみつのはなしZXQ9";
    private static final String SECRET_REPLY = "それはぽてねだけのひみつだねKJWQ";

    private final MutableClock clock = new MutableClock("2026-10-07T01:00:00Z", "Asia/Tokyo");
    private final List<String> lines = new ArrayList<>();
    private final AuditLogger logger = new AuditLogger(clock, "yurune-prod", lines::add);

    @AfterEach
    void tearDown() {
        TraceContext.end();
        AgentDecisionContext.clear();
    }

    private JsonObject only() {
        assertEquals(1, lines.size());
        return JsonParser.parseString(lines.get(0)).getAsJsonObject();
    }

    // ---- 会話の本文が、どこにも残らない ----

    @Test
    void aConversationLeavesNoTextInTheLogs() {
        ForbiddenDomainFilter filter = new ForbiddenDomainFilter();
        PiiMasker masker = new PiiMasker();
        PromptSecrets secrets = new PromptSecrets();
        ConversationService service = new ConversationService(new InputGuard(masker, new InjectionDetector(), filter),
                new OutputGuard(filter, masker, secrets), new ConversationPromptBuilder(secrets),
                request -> "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + SECRET_REPLY + "\"}]}}]}",
                new CircuitBreaker(3, Duration.ofSeconds(60), clock), new LlmCallLimiter(0, 100, clock),
                new DisabledArmorGateway(), logger::conversation);
        TraceContext.begin("0123456789abcdef0123456789abcdef");

        ConversationReply reply = service.respond(SECRET_UTTERANCE + " 電話は090-1234-5678 メールはtaro@example.com", List.of(),
                new FactSheet(Map.of(), "雨の音"));

        assertEquals(SECRET_REPLY, reply.getText(), "返事は、画面に出る");
        String logs = String.join("\n", lines);
        assertFalse(logs.contains("ZXQ9"), "発言の本文が、記録に出ている");
        assertFalse(logs.contains("KJWQ"), "返事の本文が、記録に出ている");
        assertFalse(logs.contains("090"));
        assertFalse(logs.contains("taro@example.com"));
        JsonObject detail = only().getAsJsonObject(LogConstants.FIELD_DETAIL);
        assertEquals("pass", detail.get("input").getAsString());
        assertEquals("llm", detail.get("reply").getAsString());
        assertEquals(1, detail.getAsJsonObject("masked").get("phone").getAsInt());
        assertEquals(1, detail.getAsJsonObject("masked").get("email").getAsInt());
    }

    @Test
    void aReplacedReplyIsLoggedByKindOnly() {
        logger.conversation(new GuardEvent("pass", Map.of(), false, ConversationReply.Source.FALLBACK_TEMPLATE, "medical_topic", null, 12));

        JsonObject detail = only().getAsJsonObject(LogConstants.FIELD_DETAIL);
        assertEquals("medical_topic", detail.get("outputViolation").getAsString());
        assertEquals("fallback", detail.get("reply").getAsString());
    }

    @Test
    void anOutageIsLoggedAsAWarningWithItsReason() {
        logger.conversation(new GuardEvent("pass", Map.of(), false, ConversationReply.Source.DOWN_TEMPLATE, null, "armor_unavailable", 3));

        assertEquals("WARNING", only().get(LogConstants.FIELD_SEVERITY).getAsString());
        assertEquals("armor_unavailable", only().getAsJsonObject(LogConstants.FIELD_DETAIL).get("down").getAsString());
    }

    // ---- 判断の記録 ----

    @Test
    void decisionsAreLoggedByPhaseAndOutcomeWithoutTheirWording() {
        InMemoryDecisionRepository inner = new InMemoryDecisionRepository();
        AuditingDecisionRepository repository = new AuditingDecisionRepository(inner, logger::decision);

        repository.saveDecision(new Decision(WakeDate.parse("2026-10-07"), ClockTime.parse("23:00"), DecisionPhase.ACT,
                Map.of("silenceSec", "61"), new DisplayText("会話を止めて、環境音だけにした"), new DisplayText("応答の間隔が長いため"), false));

        assertEquals(1, inner.findDecisions(WakeDate.parse("2026-10-07")).size(), "保存は、そのまま行われる");
        JsonObject detail = only().getAsJsonObject(LogConstants.FIELD_DETAIL);
        assertEquals("act", detail.get("phase").getAsString());
        assertFalse(detail.get("accepted").getAsBoolean());
        assertFalse(lines.get(0).contains("環境音"));
        assertFalse(lines.get(0).contains("応答の間隔"));
        assertEquals("WARNING", only().get(LogConstants.FIELD_SEVERITY).getAsString(), "拒否された判断は、目立たせる");
    }

    @Test
    void theReviewRulesAreLoggedByRuleVerdictAndCountOnly() {
        logger.pdca("R4", "rollback", 3);

        JsonObject detail = only().getAsJsonObject(LogConstants.FIELD_DETAIL);
        assertEquals("R4", detail.get("rule").getAsString());
        assertEquals("rollback", detail.get("verdict").getAsString());
        assertEquals(3, detail.get("nights").getAsInt());
        assertEquals("pdca", only().get(LogConstants.FIELD_EVENT).getAsString());
        assertEverySchemaCompliant(lines);
    }

    // ---- 認証と消去 ----

    @Test
    void authEventsNameTheActionAndTheOutcomeButNeverTheEmail() {
        logger.auth(AuditLogger.AuthAction.LOGIN, AuthException.Reason.INVALID_CREDENTIALS);
        logger.auth(AuditLogger.AuthAction.REGISTER, null);

        JsonObject failure = JsonParser.parseString(lines.get(0)).getAsJsonObject().getAsJsonObject(LogConstants.FIELD_DETAIL);
        assertEquals("login", failure.get("action").getAsString());
        assertEquals("invalid_credentials", failure.get("outcome").getAsString());
        JsonObject success = JsonParser.parseString(lines.get(1)).getAsJsonObject().getAsJsonObject(LogConstants.FIELD_DETAIL);
        assertEquals("success", success.get("outcome").getAsString());
    }

    @Test
    void usersAreNamedByAShortOneWayHashNotByTheirUid() {
        String uid = "abcDEF123456789";

        String hash = AuditLogger.hashUid(uid);
        logger.erase(hash, true);

        assertEquals(LogConstants.USER_HASH_CHARS, hash.length());
        assertNotEquals(uid, hash);
        assertEquals(hash, AuditLogger.hashUid(uid), "同じ利用者は、同じ名前");
        assertFalse(lines.get(0).contains(uid));
    }

    @Test
    void eraseFailuresAreRecordedAsSuchAndEverySchemaCompliantFieldIsAllowed() {
        logger.erase(AuditLogger.hashUid("abcDEF123456789"), false);

        JsonObject line = JsonParser.parseString(lines.get(0)).getAsJsonObject();
        JsonObject detail = line.getAsJsonObject(LogConstants.FIELD_DETAIL);
        assertFalse(detail.get("success").getAsBoolean());
        assertEquals("WARNING", line.get(LogConstants.FIELD_SEVERITY).getAsString(), "消去の失敗は、気づけるようにする");
        assertEverySchemaCompliant(lines);
    }

    // ---- トレース ----

    @Test
    void theTraceIdIsTakenFromTheCloudRunHeaderOrCreated() {
        assertEquals("105445aa7843bc8bf206b12000100000", TraceContext.resolve("105445aa7843bc8bf206b12000100000/1;o=1"));
        assertEquals("105445aa7843bc8bf206b12000100000", TraceContext.resolve("105445AA7843BC8BF206B12000100000/9"));
        String created = TraceContext.resolve(null);
        assertEquals(LogConstants.TRACE_ID_HEX_CHARS, created.length());
        assertTrue(created.matches("[0-9a-f]+"));
        String forged = TraceContext.resolve("../../etc/passwd/1;o=1");
        assertTrue(forged.matches("[0-9a-f]{32}"), "形が正しくないものは、使わない");
    }

    @Test
    void everyLineInOneRequestSharesTheTraceIdAndLinksToCloudTrace() {
        TraceContext.begin("0123456789abcdef0123456789abcdef");

        logger.conversation(new GuardEvent("pass", Map.of(), false, ConversationReply.Source.LLM, null, null, 5));
        logger.request("POST", "/night/talk", 200, 12, "0123456789abcdef");

        for (String line : lines) {
            JsonObject json = JsonParser.parseString(line).getAsJsonObject();
            assertEquals("0123456789abcdef0123456789abcdef", json.get(LogConstants.FIELD_TRACE_ID).getAsString());
            assertEquals("projects/yurune-prod/traces/0123456789abcdef0123456789abcdef", json.get(LogConstants.FIELD_TRACE).getAsString());
        }
    }

    // ---- リクエストの記録（Filter） ----

    private TraceFilter createFilter() {
        AppComponents components = AppComponents.create(new DeploymentConfig(false), Clock.systemUTC(), AppConfig.defaults(), lines::add);
        ServletContext context = FakeHttp.proxy(ServletContext.class, (self, called, args) ->
                "getAttribute".equals(called.getName()) && ViewConstants.ATTR_COMPONENTS.equals(args[0]) ? components : null);
        TraceFilter filter = new TraceFilter();
        filter.init(FakeHttp.proxy(FilterConfig.class, (self, called, args) ->
                "getServletContext".equals(called.getName()) ? context : null));
        return filter;
    }

    @Test
    void theRequestLineHasThePathStatusAndTimeButNotTheQueryOrTheBody() throws Exception {
        FakeHttp.Exchange exchange = new FakeHttp.Exchange();
        exchange.method = "POST";
        exchange.servletPath = "/night/talk";
        exchange.parameters.put("text", SECRET_UTTERANCE);
        exchange.requestHeaders.put("X-Cloud-Trace-Context", "105445aa7843bc8bf206b12000100000/1;o=1");

        createFilter().doFilter(exchange.request(), exchange.response(), exchange.chain());

        assertEquals("105445aa7843bc8bf206b12000100000", exchange.responseHeaders.get("X-Trace-Id"));
        assertEquals(1, lines.size());
        JsonObject detail = only().getAsJsonObject(LogConstants.FIELD_DETAIL);
        assertEquals("/night/talk", detail.get("path").getAsString());
        assertEquals("POST", detail.get("method").getAsString());
        assertFalse(lines.get(0).contains("ZXQ9"));
        assertTrue(TraceContext.current().isEmpty(), "処理が終わったら、トレースを捨てる");
    }

    @Test
    void staticFilesAreNotLogged() throws Exception {
        FakeHttp.Exchange exchange = new FakeHttp.Exchange();
        exchange.servletPath = "/js/yurune.js";

        createFilter().doFilter(exchange.request(), exchange.response(), exchange.chain());

        assertTrue(lines.isEmpty());
    }

    @Test
    void armorFailuresAreReportedWithoutTheirDetailsToTheLogger() {
        assertTrue(new ArmorUnavailableException("model armor returned 503").getMessage().contains("503"));
        logger.conversation(new GuardEvent("pass", Map.of(), false, ConversationReply.Source.DOWN_TEMPLATE, null, "armor_unavailable", 1));
        assertFalse(lines.get(0).contains("503"));
        assertTrue(GuardConstants.MASK_TOKEN.length() > 0);
    }

    // ============================================================
    // agent / llm / control ログ（A1〜A6）
    // ============================================================

    private OutputGuard createOutputGuard() {
        return new OutputGuard(new ForbiddenDomainFilter(), new PiiMasker(), new PromptSecrets());
    }

    /**
     * FADE は 45（候補の最大）にする。ここは agent / llm / control ログ（A1〜A6）を見るテストで、
     * PDCA の FADEの対象ではないため、これらのテストが使う短い経過時間（60〜90 秒）では、
     * ResilientNightAgent が差し込む規則の音量下げが、まだ働かない（FADE_FLOOR へ向けて下がり始める前）ようにする。
     */
    private NightPlan createPlan() {
        Map<Dimension, OptionName> options = new EnumMap<>(Dimension.class);
        options.put(Dimension.SOUND, new OptionName("rain"));
        options.put(Dimension.TALK, new OptionName("none"));
        options.put(Dimension.FADE, new OptionName("45"));
        options.put(Dimension.DURATION, new OptionName("45"));
        return new NightPlan(options, java.util.EnumSet.of(Dimension.SOUND), new DisplayText("テスト用の計画"), 1L);
    }

    private NightSessionService createNightSessionService(NightAgent agent, AuditLogger auditLogger, AppConfig config,
            Supplier<app.yurune.domain.value.UserSettings> settings) {
        return new NightSessionService(new InMemoryNightSessionRepository(), new InMemoryNightRepository(),
                new InMemoryDecisionRepository(), agent, new ToolGuard(config, createOutputGuard()), config, clock,
                settings, wakeDate -> { }, auditLogger);
    }

    private String functionCallResponse(String toolId, String argsJson) {
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"functionCall\":{\"name\":\"" + toolId + "\","
                + "\"args\":" + argsJson + "}}]}}]}";
    }

    private String functionCallResponseWithUsage(String toolId, String argsJson, int promptTokens, int candidateTokens) {
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"functionCall\":{\"name\":\"" + toolId + "\","
                + "\"args\":" + argsJson + "}}]}}],\"usageMetadata\":{\"promptTokenCount\":" + promptTokens
                + ",\"candidatesTokenCount\":" + candidateTokens + "}}";
    }

    /** ちょうど 1 件のはずの種類を見つける（A1〜A3 の検査で使う）。 */
    private JsonObject onlyEvent(List<String> lines, String event) {
        List<JsonObject> matches = new ArrayList<>();
        for (String line : lines) {
            JsonObject json = JsonParser.parseString(line).getAsJsonObject();
            if (event.equals(json.get(LogConstants.FIELD_EVENT).getAsString())) {
                matches.add(json);
            }
        }
        assertEquals(1, matches.size(), "種類 " + event + " は、ちょうど 1 件のはず: " + lines);
        return matches.get(0);
    }

    /**
     * A6: 出たログ行すべてが、(1) JSON として読める、(2) severity・event・time を持つ、
     * (3) AuditEventSchema.isKnownEvent が true、(4) detail の項目名が、すべて allowedFields の中にある。
     */
    private void assertEverySchemaCompliant(List<String> allLines) {
        for (String line : allLines) {
            JsonObject json = JsonParser.parseString(line).getAsJsonObject();
            assertTrue(json.has(LogConstants.FIELD_SEVERITY), line);
            assertTrue(json.has(LogConstants.FIELD_EVENT), line);
            assertTrue(json.has(LogConstants.FIELD_TIME), line);
            String event = json.get(LogConstants.FIELD_EVENT).getAsString();
            assertTrue(AuditEventSchema.isKnownEvent(event), "未知の種類: " + event);
            JsonObject detail = json.getAsJsonObject(LogConstants.FIELD_DETAIL);
            Set<String> allowed = AuditEventSchema.allowedFields(event);
            for (String key : detail.keySet()) {
                assertTrue(allowed.contains(key), event + " に、許可されていない項目: " + key);
            }
        }
    }

    // ---- A1: agent ログ ----

    @Test
    void agentLogRecordsAnAcceptedToolCallChosenByTheLlm() {
        List<String> agentLines = new ArrayList<>();
        AuditLogger agentLogger = new AuditLogger(clock, null, agentLines::add);
        ForbiddenDomainFilter filter = new ForbiddenDomainFilter();
        AppConfig config = AppConfig.defaults();
        GeminiNightAgent llmAgent = new GeminiNightAgent(
                request -> functionCallResponse("fade_out", "{\"minutes\":10,\"why\":\"下げます\"}"),
                new GeminiRequestBuilder(config), new GeminiResponseParser(filter, new PiiMasker()));
        ResilientNightAgent agent = new ResilientNightAgent(new RuleBasedNightAgent(config), llmAgent,
                new LlmCallLimiter(0, 1000, clock), new CircuitBreaker(3, Duration.ofSeconds(60), clock));
        NightSessionService service = createNightSessionService(agent, agentLogger, config,
                app.yurune.domain.value.UserSettings::defaults);
        service.startNight(createPlan(), NightMode.SOUND);

        clock.advance(Duration.ofSeconds(60));
        service.observe(86_000);

        JsonObject detail = onlyEvent(agentLines, LogConstants.EVENT_AGENT).getAsJsonObject(LogConstants.FIELD_DETAIL);
        assertEquals("fade_out", detail.get("tool").getAsString());
        assertTrue(detail.get("accepted").getAsBoolean());
        assertEquals(AgentSource.LLM.asId(), detail.get("source").getAsString());
        assertFalse(detail.has("rejectReason"));
        assertFalse(detail.has("fallbackReason"));
        assertEverySchemaCompliant(agentLines);
    }

    @Test
    void agentLogRecordsARejectedToolCallWithItsReasonCode() {
        List<String> agentLines = new ArrayList<>();
        AuditLogger agentLogger = new AuditLogger(clock, null, agentLines::add);
        ForbiddenDomainFilter filter = new ForbiddenDomainFilter();
        AppConfig config = AppConfig.defaults();
        GeminiNightAgent llmAgent = new GeminiNightAgent(
                request -> functionCallResponse("format_disk", "{\"why\":\"許可リスト外\"}"),
                new GeminiRequestBuilder(config), new GeminiResponseParser(filter, new PiiMasker()));
        ResilientNightAgent agent = new ResilientNightAgent(new RuleBasedNightAgent(config), llmAgent,
                new LlmCallLimiter(0, 1000, clock), new CircuitBreaker(3, Duration.ofSeconds(60), clock));
        NightSessionService service = createNightSessionService(agent, agentLogger, config,
                app.yurune.domain.value.UserSettings::defaults);
        service.startNight(createPlan(), NightMode.SOUND);

        clock.advance(Duration.ofSeconds(60));
        service.observe(86_000);

        JsonObject detail = onlyEvent(agentLines, LogConstants.EVENT_AGENT).getAsJsonObject(LogConstants.FIELD_DETAIL);
        assertFalse(detail.get("accepted").getAsBoolean());
        assertEquals("unknown", detail.get("tool").getAsString(), "許可リストにない道具名は、そのまま出さない（AT-06）");
        assertEquals(ToolRejectReason.NOT_IN_ALLOWLIST.asId(), detail.get("rejectReason").getAsString());
        assertEquals(AgentSource.LLM.asId(), detail.get("source").getAsString());
        assertEverySchemaCompliant(agentLines);
    }

    /**
     * 道具名は、LLM の応答から読んだ、検査前の文字列（ToolCall の toolId）。プロンプトに注入された発言が、
     * 道具名として返ることがある。許可リストにない名前は、長さを切るのではなく、固定の語に置き換えること（AT-06）。
     */
    @Test
    void agentLogReplacesAnUnallowedToolNameEvenWhenItLooksLikePersonalInformation() {
        List<String> agentLines = new ArrayList<>();
        AuditLogger agentLogger = new AuditLogger(clock, null, agentLines::add);
        ForbiddenDomainFilter filter = new ForbiddenDomainFilter();
        AppConfig config = AppConfig.defaults();
        String injected = "yamada_taro_090-1234-5678_please_call_me";
        GeminiNightAgent llmAgent = new GeminiNightAgent(
                request -> functionCallResponse(injected, "{\"why\":\"注入\"}"),
                new GeminiRequestBuilder(config), new GeminiResponseParser(filter, new PiiMasker()));
        ResilientNightAgent agent = new ResilientNightAgent(new RuleBasedNightAgent(config), llmAgent,
                new LlmCallLimiter(0, 1000, clock), new CircuitBreaker(3, Duration.ofSeconds(60), clock));
        NightSessionService service = createNightSessionService(agent, agentLogger, config,
                app.yurune.domain.value.UserSettings::defaults);
        service.startNight(createPlan(), NightMode.SOUND);

        clock.advance(Duration.ofSeconds(60));
        service.observe(86_000);

        JsonObject detail = onlyEvent(agentLines, LogConstants.EVENT_AGENT).getAsJsonObject(LogConstants.FIELD_DETAIL);
        assertEquals("unknown", detail.get("tool").getAsString());
        for (String line : agentLines) {
            assertFalse(line.contains("yamada"), "注入された文字列が、記録に出ている: " + line);
            assertFalse(line.contains("090-1234-5678"), "注入された文字列が、記録に出ている: " + line);
        }
        assertEverySchemaCompliant(agentLines);
    }

    @Test
    void agentLogRecordsAFallbackSourceWithItsClassifiedReason() {
        List<String> agentLines = new ArrayList<>();
        AuditLogger agentLogger = new AuditLogger(clock, null, agentLines::add);
        ForbiddenDomainFilter filter = new ForbiddenDomainFilter();
        AppConfig config = AppConfig.defaults();
        GeminiNightAgent llmAgent = new GeminiNightAgent(request -> {
            throw new LlmUnavailableException("llm timed out", FallbackReason.TIMEOUT);
        }, new GeminiRequestBuilder(config), new GeminiResponseParser(filter, new PiiMasker()));
        ResilientNightAgent agent = new ResilientNightAgent(new RuleBasedNightAgent(config), llmAgent,
                new LlmCallLimiter(0, 1000, clock), new CircuitBreaker(3, Duration.ofSeconds(60), clock));
        NightSessionService service = createNightSessionService(agent, agentLogger, config,
                app.yurune.domain.value.UserSettings::defaults);
        service.startNight(createPlan(), NightMode.TALK);

        clock.advance(Duration.ofSeconds(90));
        service.observe(86_000);

        JsonObject detail = onlyEvent(agentLines, LogConstants.EVENT_AGENT).getAsJsonObject(LogConstants.FIELD_DETAIL);
        assertEquals("switch_mode", detail.get("tool").getAsString());
        assertTrue(detail.get("accepted").getAsBoolean());
        assertEquals(AgentSource.FALLBACK.asId(), detail.get("source").getAsString());
        assertEquals(FallbackReason.TIMEOUT.asId(), detail.get("fallbackReason").getAsString());
        assertEverySchemaCompliant(agentLines);
    }

    @Test
    void agentLogDefaultsToRuleWhenNotGoingThroughDecide() {
        List<String> agentLines = new ArrayList<>();
        AuditLogger agentLogger = new AuditLogger(clock, null, agentLines::add);
        AppConfig config = AppConfig.defaults();
        app.yurune.domain.value.UserSettings[] current = { app.yurune.domain.value.UserSettings.defaults() };
        NightSessionService service = createNightSessionService(new RuleBasedNightAgent(config), agentLogger, config,
                () -> current[0]);
        service.startNight(createPlan(), NightMode.TALK);

        current[0] = current[0].withNightTalkEnabled(false);
        service.enforceSettings();

        JsonObject detail = onlyEvent(agentLines, LogConstants.EVENT_AGENT).getAsJsonObject(LogConstants.FIELD_DETAIL);
        assertEquals("switch_mode", detail.get("tool").getAsString());
        assertEquals(AgentSource.RULE.asId(), detail.get("source").getAsString());
        assertFalse(detail.has("fallbackReason"));
        assertEverySchemaCompliant(agentLines);
    }

    // ---- A2: llm ログ ----

    @Test
    void llmLogRecordsANightPurposeCallWithTokenCounts() {
        List<String> llmLines = new ArrayList<>();
        AuditLogger llmLogger = new AuditLogger(clock, null, llmLines::add);
        ForbiddenDomainFilter filter = new ForbiddenDomainFilter();
        AppConfig config = AppConfig.defaults();
        GeminiNightAgent agent = new GeminiNightAgent(
                request -> functionCallResponseWithUsage("fade_out", "{\"minutes\":10,\"why\":\"下げます\"}", 42, 7),
                new GeminiRequestBuilder(config), new GeminiResponseParser(filter, new PiiMasker()), llmLogger);

        agent.decide(new Observation(10, NightMode.SOUND, new VolumePercent(40), 60, 60),
                new app.yurune.domain.entity.NightSession(WakeDate.parse("2026-10-07"), NightMode.SOUND,
                        new VolumePercent(40), clock.instant(), false));

        JsonObject detail = onlyEvent(llmLines, LogConstants.EVENT_LLM).getAsJsonObject(LogConstants.FIELD_DETAIL);
        assertEquals(LlmOutcome.OK.asId(), detail.get("outcome").getAsString());
        assertEquals(LlmPurpose.NIGHT.asId(), detail.get("purpose").getAsString());
        assertEquals(42, detail.get("promptTokens").getAsInt());
        assertEquals(7, detail.get("candidateTokens").getAsInt());
        assertEverySchemaCompliant(llmLines);
    }

    @Test
    void llmLogRecordsANightPurposeFailureWithItsClassifiedOutcome() {
        List<String> llmLines = new ArrayList<>();
        AuditLogger llmLogger = new AuditLogger(clock, null, llmLines::add);
        ForbiddenDomainFilter filter = new ForbiddenDomainFilter();
        AppConfig config = AppConfig.defaults();
        GeminiNightAgent agent = new GeminiNightAgent(request -> {
            throw new LlmUnavailableException("parse failed", FallbackReason.PARSE_ERROR);
        }, new GeminiRequestBuilder(config), new GeminiResponseParser(filter, new PiiMasker()), llmLogger);

        try {
            agent.decide(new Observation(10, NightMode.SOUND, new VolumePercent(40), 60, 60),
                    new app.yurune.domain.entity.NightSession(WakeDate.parse("2026-10-07"), NightMode.SOUND,
                            new VolumePercent(40), clock.instant(), false));
        } catch (LlmUnavailableException expected) {
            // ResilientNightAgent が、これを捕まえて規則に切り替える。ここでは、記録だけを確かめる
        }

        JsonObject detail = onlyEvent(llmLines, LogConstants.EVENT_LLM).getAsJsonObject(LogConstants.FIELD_DETAIL);
        assertEquals(LlmOutcome.PARSE_ERROR.asId(), detail.get("outcome").getAsString());
        assertEverySchemaCompliant(llmLines);
    }

    @Test
    void llmLogRecordsATalkPurposeCallWithTokenCounts() {
        List<String> llmLines = new ArrayList<>();
        AuditLogger llmLogger = new AuditLogger(clock, null, llmLines::add);
        ForbiddenDomainFilter filter = new ForbiddenDomainFilter();
        PiiMasker masker = new PiiMasker();
        PromptSecrets secrets = new PromptSecrets();
        ConversationService service = new ConversationService(new InputGuard(masker, new InjectionDetector(), filter),
                new OutputGuard(filter, masker, secrets), new ConversationPromptBuilder(secrets),
                request -> "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + SECRET_REPLY + "\"}]}}],"
                        + "\"usageMetadata\":{\"promptTokenCount\":5,\"candidatesTokenCount\":3}}",
                new CircuitBreaker(3, Duration.ofSeconds(60), clock), new LlmCallLimiter(0, 100, clock),
                new DisabledArmorGateway(), event -> { }, llmLogger, null);

        service.respond("ねむれない" + SECRET_UTTERANCE, List.of(), new FactSheet(Map.of(), "雨の音"));

        JsonObject detail = onlyEvent(llmLines, LogConstants.EVENT_LLM).getAsJsonObject(LogConstants.FIELD_DETAIL);
        assertEquals(LlmOutcome.OK.asId(), detail.get("outcome").getAsString());
        assertEquals(LlmPurpose.TALK.asId(), detail.get("purpose").getAsString());
        assertEquals(5, detail.get("promptTokens").getAsInt());
        assertEquals(3, detail.get("candidateTokens").getAsInt());
        assertEverySchemaCompliant(llmLines);
        String joined = String.join("\n", llmLines);
        assertFalse(joined.contains("ZXQ9"), "発言の本文が、llm ログに出ている");
        assertFalse(joined.contains("KJWQ"), "返事の本文が、llm ログに出ている");
    }

    @Test
    void llmLogRecordsRejectedWhenTheOutputIsReplacedByTheFallbackTemplate() {
        List<String> llmLines = new ArrayList<>();
        AuditLogger llmLogger = new AuditLogger(clock, null, llmLines::add);
        ForbiddenDomainFilter filter = new ForbiddenDomainFilter();
        PiiMasker masker = new PiiMasker();
        PromptSecrets secrets = new PromptSecrets();
        ConversationService service = new ConversationService(new InputGuard(masker, new InjectionDetector(), filter),
                new OutputGuard(filter, masker, secrets), new ConversationPromptBuilder(secrets),
                request -> "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"睡眠薬を飲んでみては\"}]}}]}",
                new CircuitBreaker(3, Duration.ofSeconds(60), clock), new LlmCallLimiter(0, 100, clock),
                new DisabledArmorGateway(), event -> { }, llmLogger, null);

        service.respond("おはなししよう", List.of(), new FactSheet(Map.of(), "雨の音"));

        JsonObject detail = onlyEvent(llmLines, LogConstants.EVENT_LLM).getAsJsonObject(LogConstants.FIELD_DETAIL);
        assertEquals(LlmOutcome.REJECTED.asId(), detail.get("outcome").getAsString());
        assertEverySchemaCompliant(llmLines);
    }

    @Test
    void llmLogRecordsTheClassifiedFailureReasonForTalk() {
        List<String> llmLines = new ArrayList<>();
        AuditLogger llmLogger = new AuditLogger(clock, null, llmLines::add);
        ForbiddenDomainFilter filter = new ForbiddenDomainFilter();
        PiiMasker masker = new PiiMasker();
        PromptSecrets secrets = new PromptSecrets();
        ConversationService service = new ConversationService(new InputGuard(masker, new InjectionDetector(), filter),
                new OutputGuard(filter, masker, secrets), new ConversationPromptBuilder(secrets),
                request -> {
                    throw new LlmUnavailableException("timed out", FallbackReason.TIMEOUT);
                },
                new CircuitBreaker(3, Duration.ofSeconds(60), clock), new LlmCallLimiter(0, 100, clock),
                new DisabledArmorGateway(), event -> { }, llmLogger, null);

        service.respond("おはなししよう", List.of(), new FactSheet(Map.of(), "雨の音"));

        JsonObject detail = onlyEvent(llmLines, LogConstants.EVENT_LLM).getAsJsonObject(LogConstants.FIELD_DETAIL);
        assertEquals(LlmOutcome.TIMEOUT.asId(), detail.get("outcome").getAsString());
        assertEverySchemaCompliant(llmLines);
    }

    // ---- A3: control ログ（ブレーカー・呼び出しの上限は ResilienceTest で確かめる） ----

    @Test
    void controlLogRecordsTheStopSwitchAndArmorStateWithFixedWords() {
        List<String> controlLines = new ArrayList<>();
        AuditLogger controlLogger = new AuditLogger(clock, null, controlLines::add);

        controlLogger.control(app.yurune.domain.value.DegradationKind.AGENT_DISABLED, app.yurune.domain.value.ControlState.OFF);
        controlLogger.control(app.yurune.domain.value.DegradationKind.ARMOR_DOWN, app.yurune.domain.value.ControlState.DOWN);
        controlLogger.control(app.yurune.domain.value.DegradationKind.ARMOR_DOWN, app.yurune.domain.value.ControlState.UP);

        assertEquals(3, controlLines.size());
        assertEquals("WARNING", JsonParser.parseString(controlLines.get(0)).getAsJsonObject()
                .get(LogConstants.FIELD_SEVERITY).getAsString(), "停止は、目立たせる");
        assertEquals("WARNING", JsonParser.parseString(controlLines.get(1)).getAsJsonObject()
                .get(LogConstants.FIELD_SEVERITY).getAsString(), "Armor の停止は、目立たせる");
        assertEquals("INFO", JsonParser.parseString(controlLines.get(2)).getAsJsonObject()
                .get(LogConstants.FIELD_SEVERITY).getAsString(), "復帰は、INFO でよい");
        assertEverySchemaCompliant(controlLines);
    }

    @Test
    void armorGoingDownAndUpDuringAConversationIsReportedOnlyOnChange() {
        List<String> controlLines = new ArrayList<>();
        AuditLogger controlLogger = new AuditLogger(clock, null, controlLines::add);
        ForbiddenDomainFilter filter = new ForbiddenDomainFilter();
        PiiMasker masker = new PiiMasker();
        PromptSecrets secrets = new PromptSecrets();
        boolean[] down = { true };
        ArmorGateway flakyArmor = new ArmorGateway() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public ArmorVerdict screenPrompt(String text) {
                if (down[0]) {
                    throw new ArmorUnavailableException("down");
                }
                return ArmorVerdict.safe();
            }

            @Override
            public ArmorVerdict screenResponse(String text) {
                return ArmorVerdict.safe();
            }
        };
        ConversationService service = new ConversationService(new InputGuard(masker, new InjectionDetector(), filter),
                new OutputGuard(filter, masker, secrets), new ConversationPromptBuilder(secrets),
                request -> "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ここにいるよ\"}]}}]}",
                new CircuitBreaker(3, Duration.ofSeconds(60), clock), new LlmCallLimiter(0, 100, clock),
                flakyArmor, event -> { }, controlLogger, new app.yurune.service.AvailabilityTracker(
                        isDown -> controlLogger.control(app.yurune.domain.value.DegradationKind.ARMOR_DOWN,
                                isDown ? app.yurune.domain.value.ControlState.DOWN : app.yurune.domain.value.ControlState.UP)));

        service.respond("おはなし1", List.of(), new FactSheet(Map.of(), "雨の音"));
        service.respond("おはなし2", List.of(), new FactSheet(Map.of(), "雨の音"));
        down[0] = false;
        service.respond("おはなし3", List.of(), new FactSheet(Map.of(), "雨の音"));
        service.respond("おはなし4", List.of(), new FactSheet(Map.of(), "雨の音"));

        List<JsonObject> controlEvents = new ArrayList<>();
        for (String line : controlLines) {
            JsonObject json = JsonParser.parseString(line).getAsJsonObject();
            if (LogConstants.EVENT_CONTROL.equals(json.get(LogConstants.FIELD_EVENT).getAsString())) {
                controlEvents.add(json);
            }
        }
        assertEquals(2, controlEvents.size(), "down→up の、変化のときだけ（4 回のうち 2 回の変化）");
        assertEquals("down", controlEvents.get(0).getAsJsonObject(LogConstants.FIELD_DETAIL).get("state").getAsString());
        assertEquals("up", controlEvents.get(1).getAsJsonObject(LogConstants.FIELD_DETAIL).get("state").getAsString());
    }

    // ---- A4: TelemetryCounters と GuardStatistics ----

    @Test
    void telemetryCountersUnderlieGuardStatisticsWithoutChangingItsReadings() {
        TelemetryCounters counters = new TelemetryCounters();
        GuardStatistics statistics = new GuardStatistics(counters);

        statistics.accept(new GuardEvent("pass", Map.of(), false, ConversationReply.Source.LLM, null, null, 10));
        statistics.accept(new GuardEvent("medical", Map.of(), false, ConversationReply.Source.MEDICAL_TEMPLATE, null, null, 1));

        assertEquals(2L, statistics.snapshot().get("conversations"));
        assertEquals(2L, counters.get("conversations"), "GuardStatistics と、同じ数え上げを共有する");
        assertEquals(statistics.snapshot(), counters.snapshot());
    }

    // ---- A5: ログ量の制御（静かな経路は、通常は出さない。5xx は出す） ----

    @Test
    void healthCheckSuccessIsNotLogged() throws Exception {
        FakeHttp.Exchange exchange = new FakeHttp.Exchange();
        exchange.servletPath = "/healthz";
        exchange.status = 200;

        createFilter().doFilter(exchange.request(), exchange.response(), exchange.chain());

        assertTrue(lines.isEmpty());
    }

    @Test
    void healthCheckFailureIsStillLogged() throws Exception {
        FakeHttp.Exchange exchange = new FakeHttp.Exchange();
        exchange.servletPath = "/healthz";
        exchange.status = 500;

        createFilter().doFilter(exchange.request(), exchange.response(), exchange.chain());

        JsonObject detail = only().getAsJsonObject(LogConstants.FIELD_DETAIL);
        assertEquals(500, detail.get("status").getAsInt());
    }

    // ---- B2: span ログ ----

    @Test
    void spanRecordsTheNameDurationAndOutcomeOnly() {
        logger.span(app.yurune.domain.value.SpanName.GEMINI_GENERATE, 42, true);

        JsonObject detail = only().getAsJsonObject(LogConstants.FIELD_DETAIL);
        assertEquals("gemini.generate", detail.get("name").getAsString());
        assertEquals(42, detail.get("durationMs").getAsInt());
        assertEquals("ok", detail.get("outcome").getAsString());
        assertEquals("span", only().get(LogConstants.FIELD_EVENT).getAsString());
        assertEverySchemaCompliant(lines);
    }

    @Test
    void aFailedSpanIsRecordedAsAWarningWithAnErrorOutcome() {
        logger.span(app.yurune.domain.value.SpanName.FIRESTORE_REQUEST, 500, false);

        assertEquals("WARNING", only().get(LogConstants.FIELD_SEVERITY).getAsString(), "失敗は、目立たせる");
        assertEquals("error", only().getAsJsonObject(LogConstants.FIELD_DETAIL).get("outcome").getAsString());
        assertEverySchemaCompliant(lines);
    }

    @Test
    void staticFileFailureIsStillLogged() throws Exception {
        FakeHttp.Exchange exchange = new FakeHttp.Exchange();
        exchange.servletPath = "/js/yurune.js";
        exchange.status = 503;

        createFilter().doFilter(exchange.request(), exchange.response(), exchange.chain());

        assertEquals(1, lines.size());
    }
}
