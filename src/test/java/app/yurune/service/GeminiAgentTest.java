package app.yurune.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.config.AppConfig;
import app.yurune.constant.LlmConstants;
import app.yurune.domain.entity.Decision;
import app.yurune.domain.entity.NightSession;
import app.yurune.domain.value.NightMode;
import app.yurune.domain.value.Observation;
import app.yurune.domain.value.ToolCall;
import app.yurune.domain.value.VolumePercent;
import app.yurune.domain.value.WakeDate;
import app.yurune.repository.InMemoryDecisionRepository;
import app.yurune.repository.InMemoryNightRepository;
import app.yurune.repository.InMemoryNightSessionRepository;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * LLM（Gemini）の接続。リクエストの中身、応答の読み取り、規則への切り替え、検証と記録を確かめる。
 * 応答の形は、Gemini API の関数呼び出しの形式に沿って作った見本。実際の応答での確認は、別に行う。
 */
class GeminiAgentTest {

    private static final int NO_REACTION_MIN = 15;
    private static final int VOLUME_CAP = 60;

    private final AppConfig config = new AppConfig(NO_REACTION_MIN, VOLUME_CAP, 30);
    private final ForbiddenDomainFilter filter = new ForbiddenDomainFilter();
    private final MutableClock clock = new MutableClock("2026-10-06T13:10:00Z", "Asia/Tokyo");

    private NightSession session(NightMode mode) {
        return new NightSession(WakeDate.parse("2026-10-07"), mode, new VolumePercent(40),
                Instant.parse("2026-10-06T13:10:00Z"), false);
    }

    /**
     * elapsedMin は 0（夜の始まり）にする。ここは LLM との組み合わせ（固定の枠、切り替え）を見るテストで、
     * PDCA の FADE（ResilientNightAgent が毎回先頭に差し込む音量下げ）の対象ではないため、
     * elapsedMin が 0 より大きいと、session() の音量（40%）がまだ下げ切られていない時間帯に入り、
     * 一覧の先頭に意図しない set_volume が混ざってしまう。
     */
    private Observation observation(NightMode mode, int gapSec, int silenceSec) {
        return new Observation(0, mode, new VolumePercent(40), gapSec, silenceSec);
    }

    private String functionCallResponse(String name, String argsJson) {
        return "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"functionCall\":{\"name\":\""
                + name + "\",\"args\":" + argsJson + "}}]},\"finishReason\":\"STOP\"}]}";
    }

    // ---- リクエスト ----

    @Test
    void requestOffersOnlyTheToolsTheLlmMayUseAndAlwaysAsksForAReason() {
        String request = new GeminiRequestBuilder(config).build(observation(NightMode.TALK, 5, 5), session(NightMode.TALK));

        JsonObject root = JsonParser.parseString(request).getAsJsonObject();
        JsonArray declarations = root.getAsJsonArray("tools").get(0).getAsJsonObject().getAsJsonArray("functionDeclarations");
        Set<String> names = new HashSet<>();
        for (int index = 0; index < declarations.size(); index++) {
            JsonObject declaration = declarations.get(index).getAsJsonObject();
            names.add(declaration.get("name").getAsString());
            assertTrue(declaration.getAsJsonObject("parameters").getAsJsonArray("required").toString().contains("\"why\""));
        }
        assertEquals(Set.of("set_volume", "fade_out", "switch_track", "set_talk_level", "switch_mode", "say"), names);
        assertFalse(names.contains("end_session"), "終了はコードの規則が行う");
    }

    @Test
    void requestCarriesOnlyNumbersAndStates() {
        String request = new GeminiRequestBuilder(config).build(observation(NightMode.TALK, 12, 34), session(NightMode.TALK));

        JsonObject root = JsonParser.parseString(request).getAsJsonObject();
        String observed = root.getAsJsonArray("contents").get(0).getAsJsonObject()
                .getAsJsonArray("parts").get(0).getAsJsonObject().get("text").getAsString();
        JsonObject values = JsonParser.parseString(observed).getAsJsonObject();
        // 自動終了の基準（noReactionMin）は渡さない。LLM が、実際の経過と取り違えたため
        assertEquals(Set.of("mode", "elapsedMin", "volumePct", "volumeCapPct", "responseGapSec", "silenceSec"),
                values.keySet());
        assertEquals(12, values.get("responseGapSec").getAsInt());
        assertEquals(VOLUME_CAP, values.get("volumeCapPct").getAsInt());
    }

    // ---- 応答の読み取り ----

    @Test
    void parserTurnsFunctionCallsIntoToolCalls() {
        String response = "{\"candidates\":[{\"content\":{\"parts\":["
                + "{\"functionCall\":{\"name\":\"set_volume\",\"args\":{\"levelPct\":30,\"rampSec\":20,\"why\":\"反応が減ったため、少し下げます。\"}}},"
                + "{\"functionCall\":{\"name\":\"switch_mode\",\"args\":{\"mode\":\"sound\",\"why\":\"応答の間隔が長いため。\"}}}"
                + "]}}]}";

        List<ToolCall> calls = new GeminiResponseParser(filter, new PiiMasker()).parse(response);

        assertEquals(2, calls.size());
        assertEquals("set_volume", calls.get(0).getToolId());
        assertEquals("30", calls.get(0).findArgument("levelPct"));
        assertEquals("20", calls.get(0).findArgument("rampSec"));
        assertEquals("反応が減ったため、少し下げます。", calls.get(0).getReason().asText());
        assertEquals("sound", calls.get(1).findArgument("mode"));
    }

    @Test
    void parserTreatsPlainTextOrNoCandidatesAsNoAction() {
        GeminiResponseParser parser = new GeminiResponseParser(filter, new PiiMasker());

        assertTrue(parser.parse("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"様子を見ます\"}]}}]}").isEmpty());
        assertTrue(parser.parse("{\"candidates\":[]}").isEmpty());
        assertTrue(parser.parse("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}").isEmpty());
    }

    @Test
    void parserCleansTheReason() {
        GeminiResponseParser parser = new GeminiResponseParser(filter, new PiiMasker());

        ToolCall missing = parser.parse(functionCallResponse("fade_out", "{\"minutes\":10}")).get(0);
        ToolCall forbidden = parser.parse(functionCallResponse("fade_out", "{\"minutes\":10,\"why\":\"睡眠薬の代わりに下げます\"}")).get(0);
        ToolCall longReason = parser.parse(functionCallResponse("fade_out", "{\"minutes\":10,\"why\":\"" + "あ".repeat(200) + "\"}")).get(0);

        assertEquals(LlmConstants.REASON_MISSING, missing.getReason().asText());
        assertEquals(LlmConstants.REASON_REPLACED, forbidden.getReason().asText());
        assertEquals(LlmConstants.REASON_MAX_CHARS, longReason.getReason().asText().length());
    }

    @Test
    void parserMasksPersonalInformationInTheReasonTheSameWaySayDoes() {
        GeminiResponseParser parser = new GeminiResponseParser(filter, new PiiMasker());

        ToolCall phone = parser.parse(functionCallResponse("fade_out",
                "{\"minutes\":10,\"why\":\"090-1234-5678に電話した様子のため\"}")).get(0);
        ToolCall email = parser.parse(functionCallResponse("fade_out",
                "{\"minutes\":10,\"why\":\"taro@example.comからの通知のため\"}")).get(0);

        assertEquals(LlmConstants.REASON_REPLACED, phone.getReason().asText());
        assertEquals(LlmConstants.REASON_REPLACED, email.getReason().asText());
    }

    @Test
    void parserRejectsAResponseThatIsNotJson() {
        assertThrows(LlmUnavailableException.class, () -> new GeminiResponseParser(filter, new PiiMasker()).parse("<html>error</html>"));
    }

    // ---- 固定の枠との組み合わせ ----

    private ResilientNightAgent createResilientAgent(LlmGateway gateway) {
        GeminiNightAgent llmAgent = new GeminiNightAgent(gateway, new GeminiRequestBuilder(config), new GeminiResponseParser(filter, new PiiMasker()));
        return new ResilientNightAgent(new RuleBasedNightAgent(config), llmAgent,
                new LlmCallLimiter(0, 1000, clock), new CircuitBreaker(3, java.time.Duration.ofSeconds(60), clock));
    }

    @Test
    void endingForNoReactionIsDecidedByCodeWithoutAskingTheLlm() {
        AtomicInteger calls = new AtomicInteger();
        ResilientNightAgent agent = createResilientAgent(request -> {
            calls.incrementAndGet();
            return "{}";
        });

        List<ToolCall> decided = agent.decide(observation(NightMode.SOUND, 0, NO_REACTION_MIN * 60), session(NightMode.SOUND));

        assertEquals(List.of("fade_out", "end_session"), decided.stream().map(ToolCall::getToolId).toList());
        assertEquals(0, calls.get());
    }

    @Test
    void fallsBackToRulesWhenTheLlmIsUnavailable() {
        ResilientNightAgent agent = createResilientAgent(request -> {
            throw new LlmUnavailableException("timeout");
        });

        List<ToolCall> decided = agent.decide(observation(NightMode.TALK, 90, 90), session(NightMode.TALK));

        assertEquals(List.of("switch_mode"), decided.stream().map(ToolCall::getToolId).toList());
    }

    @Test
    void doesNotAskTheLlmWhileTheUserIsStillActive() {
        AtomicInteger calls = new AtomicInteger();
        ResilientNightAgent agent = createResilientAgent(request -> {
            calls.incrementAndGet();
            return functionCallResponse("fade_out", "{\"minutes\":20,\"why\":\"下げます\"}");
        });

        List<ToolCall> decided = agent.decide(observation(NightMode.TALK, 3, 59), session(NightMode.TALK));

        assertTrue(decided.isEmpty());
        assertEquals(0, calls.get());
    }

    @Test
    void sleepStateClaimsInTheReasonAreReplaced() {
        ToolCall call = new GeminiResponseParser(filter, new PiiMasker()).parse(
                functionCallResponse("fade_out", "{\"minutes\":10,\"why\":\"利用者が眠っている可能性があるため\"}")).get(0);

        assertEquals(LlmConstants.REASON_REPLACED, call.getReason().asText());
    }

    @Test
    void llmChoiceIsUsedWhenAvailable() {
        ResilientNightAgent agent = createResilientAgent(
                request -> functionCallResponse("fade_out", "{\"minutes\":20,\"why\":\"静かに下げます。\"}"));

        List<ToolCall> decided = agent.decide(observation(NightMode.SOUND, 0, 60), session(NightMode.SOUND));

        assertEquals(List.of("fade_out"), decided.stream().map(ToolCall::getToolId).toList());
    }

    // ---- 検証と記録 ----

    @Test
    void whatTheLlmChoosesIsValidatedAndRecordedLikeAnyOtherCall() {
        List<String> responses = new ArrayList<>(List.of(
                functionCallResponse("set_volume", "{\"levelPct\":90,\"rampSec\":5,\"why\":\"もっと大きく\"}"),
                functionCallResponse("format_disk", "{\"why\":\"許可リスト外\"}"),
                functionCallResponse("set_volume", "{\"levelPct\":50,\"rampSec\":10,\"why\":\"少し上げます\"}")));
        InMemoryNightRepository nightRepository = new InMemoryNightRepository();
        InMemoryDecisionRepository decisionRepository = new InMemoryDecisionRepository();
        NightSessionService service = new NightSessionService(new InMemoryNightSessionRepository(), nightRepository,
                decisionRepository, createResilientAgent(request -> responses.remove(0)), new ToolGuard(config, NightFixtures.createOutputGuard()), config, clock);
        // FADE は、このテストの時間枠（数分）の外側で働くよう、十分大きい分数にする。ここは LLM の判断の検証・記録を
        // 見るテストで、PDCA の FADEの対象ではないため。
        service.startNight(NightFixtures.createPlan("rain", "none", "600"), NightMode.SOUND);

        // 反応のない時間（サーバーの時計で数える）が 60 秒に届いたときだけ、LLM が呼ばれる
        for (int attempt = 0; attempt < 3; attempt++) {
            clock.advance(java.time.Duration.ofSeconds(60));
            service.observe(86_000);
        }

        assertEquals(50, service.findActiveSession().orElseThrow().getVolume().asInt());
        List<Decision> decisions = decisionRepository.findDecisions(WakeDate.parse("2026-10-07"));
        assertEquals(4, decisions.size());
        assertEquals(List.of(true, false, false, true), decisions.stream().map(Decision::isAccepted).toList());
    }

    @Test
    void killSwitchConfigurationIsReadable() {
        assertTrue(new AppConfig(NO_REACTION_MIN, VOLUME_CAP, 30, true).isAgentEnabled());
        assertFalse(new AppConfig(NO_REACTION_MIN, VOLUME_CAP, 30, false).isAgentEnabled());
    }
}
