import app.yurune.config.AppConfig;
import app.yurune.constant.LlmConstants;
import app.yurune.domain.entity.NightSession;
import app.yurune.domain.value.NightMode;
import app.yurune.domain.value.Observation;
import app.yurune.domain.value.ToolCall;
import app.yurune.domain.value.VolumePercent;
import app.yurune.domain.value.WakeDate;
import app.yurune.gateway.GeminiRestGateway;
import app.yurune.service.ForbiddenDomainFilter;
import app.yurune.service.GeminiNightAgent;
import app.yurune.service.GeminiRequestBuilder;
import app.yurune.service.GeminiResponseParser;
import app.yurune.service.LlmUnavailableException;
import app.yurune.service.OutputGuard;
import app.yurune.service.PiiMasker;
import app.yurune.service.PromptSecrets;
import app.yurune.service.ToolGuard;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 実際の Gemini に、夜の観測を送り、ツールの選び方を確かめる確認ツール。
 * 送るのは数値と状態だけで、個人情報や会話は含まない。
 * LLM は毎回同じ答えとは限らないので、1 つの場面を、3 回ずつ試す。
 * 実際のアプリでは、反応のない時間が 60 秒未満のときは、LLM に聞かない。ここでは、約束の守り方を見るために、直接聞く。
 *
 * 使い方（コマンドプロンプト、プロジェクトのフォルダで。先に mvn package などで target\classes を作る）:
 *   set GEMINI_API_KEY=（キー）
 *   set GEMINI_MODEL=gemini-3.5-flash-lite
 *   "C:\pleiades\2026-06\java\21\bin\java.exe" -cp target\classes;%USERPROFILE%\.m2\repository\com\google\code\gson\gson\2.11.0\gson-2.11.0.jar tools\AgentProbe.java
 *
 * 見るところ: 各行の PASS / FAIL と、そのあとに出る、選んだツールと理由（何も呼ばなかったときは、その旨）。
 */
public final class AgentProbe {

    private static final int NO_REACTION_MIN = 15;
    private static final int VOLUME_CAP = 60;
    private static final int TRIES = 3;

    /** 場面ごとの、期待。null は、特に決めていない。 */
    private static final class Scenario {
        private final String title;
        private final NightMode mode;
        private final int seconds;
        private final Set<String> acceptableTools;
        private final boolean mustDoNothing;

        private Scenario(String title, NightMode mode, int seconds, Set<String> acceptableTools, boolean mustDoNothing) {
            this.title = title;
            this.mode = mode;
            this.seconds = seconds;
            this.acceptableTools = acceptableTools;
            this.mustDoNothing = mustDoNothing;
        }
    }

    public static void main(String[] args) {
        String key = System.getenv("GEMINI_API_KEY");
        String model = System.getenv("GEMINI_MODEL");
        if (key == null || key.isBlank()) {
            System.err.println("GEMINI_API_KEY を、環境変数に設定してください。");
            System.exit(1);
        }
        AppConfig config = new AppConfig(NO_REACTION_MIN, VOLUME_CAP, 30);
        ForbiddenDomainFilter filter = new ForbiddenDomainFilter();
        PiiMasker piiMasker = new PiiMasker();
        GeminiNightAgent agent = new GeminiNightAgent(
                new GeminiRestGateway(key, model == null || model.isBlank() ? LlmConstants.DEFAULT_MODEL : model),
                new GeminiRequestBuilder(config), new GeminiResponseParser(filter, piiMasker));
        ToolGuard guard = new ToolGuard(config, new OutputGuard(filter, piiMasker, new PromptSecrets()));

        List<Scenario> scenarios = List.of(
                new Scenario("会話中。いま返事があった（3 秒）→ 何もしない", NightMode.TALK, 3, Set.of(), true),
                new Scenario("環境音だけ。操作があった（20 秒）→ 何もしない", NightMode.SOUND, 20, Set.of(), true),
                new Scenario("会話中。45 秒、返事がない → 環境音へ、または話す量を 0 に", NightMode.TALK, 45,
                        Set.of("switch_mode", "set_talk_level"), false),
                new Scenario("環境音だけ。2 分、反応がない → 静かに下げるか、何もしない", NightMode.SOUND, 120,
                        Set.of("fade_out", "set_volume"), false),
                new Scenario("環境音だけ。8 分、反応がない → 理由に「15分」と書かない", NightMode.SOUND, 480,
                        Set.of("fade_out", "set_volume"), false));

        int failures = 0;
        for (Scenario scenario : scenarios) {
            for (int attempt = 1; attempt <= TRIES; attempt++) {
                failures += run(scenario, attempt, agent, guard, filter);
            }
        }
        System.out.println(failures == 0 ? "すべて PASS" : "FAIL: " + failures + " 件");
    }

    private static int run(Scenario scenario, int attempt, GeminiNightAgent agent, ToolGuard guard,
            ForbiddenDomainFilter filter) {
        NightSession session = new NightSession(WakeDate.parse("2026-10-07"), scenario.mode, new VolumePercent(40),
                Instant.parse("2026-10-06T13:10:00Z"), false);
        Observation observation = new Observation(30, scenario.mode, new VolumePercent(40), scenario.seconds, scenario.seconds);
        List<ToolCall> calls;
        try {
            calls = agent.decide(observation, session);
        } catch (LlmUnavailableException exception) {
            System.out.println("FAIL  [" + scenario.title + " #" + attempt + "] LLM が使えなかった: " + exception.getMessage());
            return 1;
        }

        String problem = findProblem(scenario, calls, guard, session, observation, filter);
        System.out.println((problem == null ? "PASS  " : "FAIL  ") + "[" + scenario.title + " #" + attempt + "] "
                + (problem == null ? "" : problem));
        if (calls.isEmpty()) {
            System.out.println("        何も呼ばなかった");
        }
        for (ToolCall call : calls) {
            System.out.println("        選んだツール: " + call.getToolId() + " / 理由: " + call.getReason().asText());
        }
        return problem == null ? 0 : 1;
    }

    /** 期待に合わないところを、1 つ返す。問題がなければ null。 */
    private static String findProblem(Scenario scenario, List<ToolCall> calls, ToolGuard guard, NightSession session,
            Observation observation, ForbiddenDomainFilter filter) {
        if (scenario.mustDoNothing && !calls.isEmpty()) {
            return "何もしないはずが、ツールを呼んだ";
        }
        for (ToolCall call : calls) {
            if (!scenario.acceptableTools.contains(call.getToolId())) {
                return "場面に合わないツール: " + call.getToolId();
            }
            if (!guard.validate(call, session, observation).isAccepted()) {
                return "検証で拒否された: " + guard.validate(call, session, observation).getRejectReason();
            }
            String reason = call.getReason().asText();
            if (filter.containsForbiddenTerm(reason) || reason.equals(LlmConstants.REASON_REPLACED)) {
                return "理由に、睡眠の推測などの禁止表現があった";
            }
            if (scenario.seconds < NO_REACTION_MIN * 60 && reason.contains(NO_REACTION_MIN + "分")) {
                return "観測にない「" + NO_REACTION_MIN + "分」を、理由に書いた";
            }
        }
        return null;
    }
}
