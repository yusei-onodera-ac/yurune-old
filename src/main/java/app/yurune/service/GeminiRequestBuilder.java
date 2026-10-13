package app.yurune.service;

import app.yurune.config.AppConfig;
import app.yurune.constant.LlmConstants;
import app.yurune.constant.NightConstants;
import app.yurune.constant.PlanConstants;
import app.yurune.domain.entity.NightSession;
import app.yurune.domain.value.Observation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Gemini に送るリクエスト（JSON）を組み立てる。
 * 送るのは、数値と状態だけの観測と、使えるツールの定義。会話の本文は含めない。
 * 終了（end_session）と「おやすみ」は、コードの規則が行うため、ツールとして見せない。
 */
public final class GeminiRequestBuilder {

    /** Gemini が読む、役割と約束。病気や薬、効果には触れない（定型文の範囲だけで応じるため）。 */
    private static final String SYSTEM_INSTRUCTION =
            "あなたは、眠る前の人に寄り添う「ぽてね」の、夜の制御係です。"
            + "届くのは、数値と状態だけの観測です。会話の中身は届きません。"
            + "【基本】原則として、何も呼びません。観測が次のどれかに当てはまるときだけ、ツールを呼びます。"
            + "(1) mode が talk で、responseGapSec が 30 以上: 反応を求めない方向へ。switch_mode（sound）か、set_talk_level（0）を選ぶ。"
            + "(2) silenceSec が 60 以上で、まだ長く続いていない: 静かな方向へ。fade_out や、set_volume で少し下げる。"
            + "(3) それ以外は、何も呼ばない。responseGapSec や silenceSec が小さいときは、利用者は操作しているので、何も呼ばない。"
            + "【理由】ツールを呼ぶときは、必ず why に、観測の数値にもとづく理由を、日本語で1行（80文字以内）で書く。"
            + "観測にない数値や出来事を、理由に書かない。秒数は、観測にある値をそのまま使う。"
            + "利用者の睡眠の状態（眠っている、など）を、推測して書かない。"
            + "【約束】音量は、観測の volumeCapPct を超えない。話すのは talk のときだけで、60文字以内の短い一言。"
            + "病気、薬、診断、効果には、触れない。終了は別の仕組みが行うので、あなたは終了しない。";

    private final AppConfig config;
    private final String sealedInstruction;

    public GeminiRequestBuilder(AppConfig config, PromptSecrets promptSecrets) {
        this.config = config;
        this.sealedInstruction = promptSecrets.seal(SYSTEM_INSTRUCTION);
    }

    /** 指示を守る必要がない場面（テストなど）では、使い捨ての合言葉で作る。 */
    public GeminiRequestBuilder(AppConfig config) {
        this(config, new PromptSecrets());
    }

    public String build(Observation observation, NightSession session) {
        JsonObject request = new JsonObject();
        request.add("systemInstruction", textContent(sealedInstruction, null));
        request.add("contents", wrapInArray(textContent(describeObservation(observation, session), "user")));
        request.add("tools", wrapInArray(createToolDeclarations()));
        request.add("toolConfig", createToolConfig());
        request.add("generationConfig", createGenerationConfig());
        return request.toString();
    }

    /** 観測を、数値と状態だけの JSON 文字列にする。 */
    private String describeObservation(Observation observation, NightSession session) {
        JsonObject values = new JsonObject();
        values.addProperty("mode", observation.getMode().asId());
        values.addProperty("elapsedMin", observation.getElapsedMin());
        values.addProperty("volumePct", observation.getVolume().asInt());
        values.addProperty("volumeCapPct", config.getVolumeCapPct());
        values.addProperty("responseGapSec", observation.getResponseGapSec());
        values.addProperty("silenceSec", observation.getSilenceSec());
        return values.toString();
    }

    private JsonObject createToolDeclarations() {
        JsonArray declarations = new JsonArray();
        declarations.add(declare("set_volume", "音量を変える。levelPct は 0 から観測の volumeCapPct まで。",
                integer("levelPct", "音量（%）"), integer("rampSec", "変えるのにかける秒数（0〜"
                        + NightConstants.SET_VOLUME_RAMP_MAX_SEC + "）")));
        declarations.add(declare("fade_out", "音をゆっくり下げる。",
                integer("minutes", "かけて下げる分（" + NightConstants.FADE_OUT_MIN_MINUTES + "〜"
                        + NightConstants.FADE_OUT_MAX_MINUTES + "）")));
        declarations.add(declare("switch_track", "音の種類を切り替える。",
                string("soundKey", "音源のキー。" + String.join("、", PlanConstants.SOUND_CANDIDATES) + " のどれか")));
        declarations.add(declare("set_talk_level", "話す量を変える。0 は話さない、1 は短め、2 はふつう。",
                integer("level", "0〜" + NightConstants.TALK_LEVEL_MAX)));
        declarations.add(declare("switch_mode", "会話から、環境音だけに切り替える。反応を求めない状態にしたいときに使う。",
                string("mode", "sound だけ指定できる")));
        declarations.add(declare("say", "ぽてねが短く話す。会話のときだけ使える。",
                string("text", NightConstants.SAY_MAX_CHARS + "文字以内")));
        JsonObject tool = new JsonObject();
        tool.add("functionDeclarations", declarations);
        return tool;
    }

    /** 1 つのツールの定義。すべてのツールに、判断の理由 why を必須で付ける。 */
    private JsonObject declare(String name, String description, JsonObject... parameters) {
        JsonObject properties = new JsonObject();
        JsonArray required = new JsonArray();
        for (JsonObject parameter : parameters) {
            String parameterName = parameter.remove("name").getAsString();
            properties.add(parameterName, parameter);
            required.add(parameterName);
        }
        properties.add("why", string("why", "判断の理由。日本語で1行、" + LlmConstants.REASON_MAX_CHARS + "文字以内"));
        properties.getAsJsonObject("why").remove("name");
        required.add("why");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "OBJECT");
        schema.add("properties", properties);
        schema.add("required", required);

        JsonObject declaration = new JsonObject();
        declaration.addProperty("name", name);
        declaration.addProperty("description", description);
        declaration.add("parameters", schema);
        return declaration;
    }

    private JsonObject integer(String name, String description) {
        return parameter(name, "INTEGER", description);
    }

    private JsonObject string(String name, String description) {
        return parameter(name, "STRING", description);
    }

    private JsonObject parameter(String name, String type, String description) {
        JsonObject parameter = new JsonObject();
        parameter.addProperty("name", name);
        parameter.addProperty("type", type);
        parameter.addProperty("description", description);
        return parameter;
    }

    /** ツールを呼ぶか呼ばないかを、モデルに任せる（AUTO）。呼ばないことも、正しい判断。 */
    private JsonObject createToolConfig() {
        JsonObject mode = new JsonObject();
        mode.addProperty("mode", "AUTO");
        JsonObject config = new JsonObject();
        config.add("functionCallingConfig", mode);
        return config;
    }

    private JsonObject createGenerationConfig() {
        JsonObject generation = new JsonObject();
        generation.addProperty("temperature", LlmConstants.TEMPERATURE);
        generation.addProperty("maxOutputTokens", LlmConstants.MAX_OUTPUT_TOKENS);
        return generation;
    }

    private JsonObject textContent(String text, String role) {
        JsonObject part = new JsonObject();
        part.addProperty("text", text);
        JsonObject content = new JsonObject();
        if (role != null) {
            content.addProperty("role", role);
        }
        content.add("parts", wrapInArray(part));
        return content;
    }

    private JsonArray wrapInArray(JsonObject element) {
        JsonArray array = new JsonArray();
        array.add(element);
        return array;
    }
}
