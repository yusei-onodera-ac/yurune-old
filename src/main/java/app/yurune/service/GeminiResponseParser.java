package app.yurune.service;

import app.yurune.constant.LlmConstants;
import app.yurune.domain.value.DisplayText;
import app.yurune.domain.value.FallbackReason;
import app.yurune.domain.value.TokenUsage;
import app.yurune.domain.value.ToolCall;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gemini の応答（JSON）から、ツール呼び出しを取り出す。
 * ツールを呼ばない応答（文だけ）は、「何もしない」と扱う。許可リストにない名前や範囲外の引数も、
 * ここでは取り除かず、そのまま渡す（拒否して記録するのは ToolGuard の役目）。
 * 理由（why）は、LLM の出力なので、say と同じ検査（個人情報、禁止領域の語）を通し、長さを切る。
 */
public final class GeminiResponseParser {

    private static final String REASON_ARGUMENT = "why";

    private final ForbiddenDomainFilter forbiddenDomainFilter;
    private final PiiMasker piiMasker;

    public GeminiResponseParser(ForbiddenDomainFilter forbiddenDomainFilter, PiiMasker piiMasker) {
        this.forbiddenDomainFilter = forbiddenDomainFilter;
        this.piiMasker = piiMasker;
    }

    /** 応答の形が想定と違うときは、LlmUnavailableException にする（規則に切り替えるため）。 */
    public List<ToolCall> parse(String responseJson) {
        JsonObject root;
        try {
            root = JsonParser.parseString(responseJson).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException exception) {
            throw new LlmUnavailableException("response is not a JSON object", FallbackReason.PARSE_ERROR, exception);
        }
        JsonArray parts = findParts(root);
        List<ToolCall> calls = new ArrayList<>();
        for (JsonElement part : parts) {
            JsonObject functionCall = part.getAsJsonObject().getAsJsonObject("functionCall");
            if (functionCall != null) {
                calls.add(toToolCall(functionCall));
            }
        }
        return calls;
    }

    /** 候補がない（安全のために止められた、など）ときは、何もしない。 */
    private JsonArray findParts(JsonObject root) {
        JsonArray candidates = root.getAsJsonArray("candidates");
        if (candidates == null || candidates.isEmpty()) {
            return new JsonArray();
        }
        JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
        if (content == null || content.getAsJsonArray("parts") == null) {
            return new JsonArray();
        }
        return content.getAsJsonArray("parts");
    }

    private ToolCall toToolCall(JsonObject functionCall) {
        String name = functionCall.has("name") ? functionCall.get("name").getAsString() : "";
        JsonObject args = functionCall.has("args") ? functionCall.getAsJsonObject("args") : new JsonObject();

        Map<String, String> arguments = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : args.entrySet()) {
            if (!REASON_ARGUMENT.equals(entry.getKey()) && entry.getValue().isJsonPrimitive()) {
                arguments.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return new ToolCall(name, arguments, new DisplayText(cleanReason(args.get(REASON_ARGUMENT))));
    }

    private String cleanReason(JsonElement reasonElement) {
        if (reasonElement == null || !reasonElement.isJsonPrimitive() || reasonElement.getAsString().isBlank()) {
            return LlmConstants.REASON_MISSING;
        }
        String reason = reasonElement.getAsString().strip();
        if (forbiddenDomainFilter.containsForbiddenTerm(reason) || piiMasker.mask(reason).hasMasked()) {
            return LlmConstants.REASON_REPLACED;
        }
        return reason.length() <= LlmConstants.REASON_MAX_CHARS ? reason : reason.substring(0, LlmConstants.REASON_MAX_CHARS);
    }

    /**
     * 応答の usageMetadata にある、送った・返ったトークンの数だけを読む（運用の記録 llm ログ用）。
     * 無い、または読めないときは、両方とも空（LlmUnavailableException にはしない。付加の情報だから）。
     */
    public static TokenUsage readTokenUsage(String responseJson) {
        try {
            JsonObject usage = JsonParser.parseString(responseJson).getAsJsonObject().getAsJsonObject("usageMetadata");
            return usage == null ? TokenUsage.none()
                    : new TokenUsage(readTokenCount(usage, "promptTokenCount"), readTokenCount(usage, "candidatesTokenCount"));
        } catch (JsonParseException | IllegalStateException exception) {
            return TokenUsage.none();
        }
    }

    private static Integer readTokenCount(JsonObject usage, String field) {
        return usage.has(field) && usage.get(field).isJsonPrimitive() ? usage.get(field).getAsInt() : null;
    }
}
