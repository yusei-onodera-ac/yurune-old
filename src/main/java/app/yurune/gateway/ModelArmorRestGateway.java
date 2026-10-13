package app.yurune.gateway;

import app.yurune.constant.HttpStatusConstants;
import app.yurune.domain.value.SpanName;
import app.yurune.service.ArmorGateway;
import app.yurune.service.ArmorUnavailableException;
import app.yurune.service.ArmorVerdict;
import app.yurune.service.AuditLogger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Model Armor（Google Cloud の入出力の検査）の REST API を呼ぶ。自前の検査層に重ねる、もう 1 つの層。
 * 利用者の発言は sanitizeUserPrompt、LLM の返事は sanitizeModelResponse で検査する。地域のエンドポイントを使う。
 * 呼び出しは、サービスアカウントのトークンで行う。通信の失敗や、想定と違う応答は、ArmorUnavailableException にする。
 * 参照: https://docs.cloud.google.com/model-armor/sanitize-prompts-responses
 */
public final class ModelArmorRestGateway implements ArmorGateway {

    private static final Duration TIMEOUT = Duration.ofSeconds(4);
    private static final String MATCH_FOUND = "MATCH_FOUND";
    private static final String SUCCESS = "SUCCESS";
    private static final long NANOS_PER_MILLI = 1_000_000;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final String templateUrl;
    private final AccessTokenProvider tokenProvider;
    private final AuditLogger auditLogger;

    /** @param endpointBase 例: https://modelarmor.us-central1.rep.googleapis.com */
    public ModelArmorRestGateway(String endpointBase, String projectId, String location, String templateId,
            AccessTokenProvider tokenProvider) {
        this(endpointBase, projectId, location, templateId, tokenProvider, null);
    }

    /** @param auditLogger 呼び出し 1 回ごとの区間の記録（B2）の出し先。省略できる（省略時は記録しない） */
    public ModelArmorRestGateway(String endpointBase, String projectId, String location, String templateId,
            AccessTokenProvider tokenProvider, AuditLogger auditLogger) {
        this.templateUrl = endpointBase + "/v1/projects/" + projectId + "/locations/" + location + "/templates/" + templateId;
        this.tokenProvider = tokenProvider;
        this.auditLogger = auditLogger;
    }

    public static String createEndpointBase(String location) {
        return "https://modelarmor." + location + ".rep.googleapis.com";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public ArmorVerdict screenPrompt(String text) {
        return sanitize(":sanitizeUserPrompt", "userPromptData", text);
    }

    @Override
    public ArmorVerdict screenResponse(String text) {
        return sanitize(":sanitizeModelResponse", "modelResponseData", text);
    }

    private ArmorVerdict sanitize(String method, String dataName, String text) {
        long startedAt = System.nanoTime();
        try {
            ArmorVerdict verdict = sendSanitizeRequest(method, dataName, text);
            recordSpan(startedAt, true);
            return verdict;
        } catch (ArmorUnavailableException exception) {
            recordSpan(startedAt, false);
            throw exception;
        }
    }

    private ArmorVerdict sendSanitizeRequest(String method, String dataName, String text) {
        JsonObject data = new JsonObject();
        data.addProperty("text", text);
        JsonObject body = new JsonObject();
        body.add(dataName, data);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(templateUrl + method)).timeout(TIMEOUT)
                    .header("Content-Type", "application/json").header("Authorization", "Bearer " + tokenProvider.getToken())
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != HttpStatusConstants.OK) {
                throw new ArmorUnavailableException("model armor returned " + response.statusCode());
            }
            return toVerdict(JsonParser.parseString(response.body()).getAsJsonObject());
        } catch (IOException | JsonParseException | IllegalStateException exception) {
            throw new ArmorUnavailableException("model armor could not be used", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ArmorUnavailableException("interrupted while calling model armor", exception);
        }
    }

    private void recordSpan(long startedAtNanos, boolean ok) {
        if (auditLogger != null) {
            auditLogger.span(SpanName.ARMOR_SANITIZE, (System.nanoTime() - startedAtNanos) / NANOS_PER_MILLI, ok);
        }
    }

    /** 検査が成功したときだけ、結果を信じる。一部しか動かなかったときは、検査できなかったものとして扱う。 */
    private ArmorVerdict toVerdict(JsonObject json) {
        JsonObject result = json.getAsJsonObject("sanitizationResult");
        if (result == null) {
            throw new ArmorUnavailableException("model armor response has no result");
        }
        if (result.has("invocationResult") && !SUCCESS.equals(result.get("invocationResult").getAsString())) {
            throw new ArmorUnavailableException("model armor did not run every filter");
        }
        Set<String> matched = new TreeSet<>();
        JsonObject filters = result.getAsJsonObject("filterResults");
        if (filters != null) {
            for (Map.Entry<String, JsonElement> filter : filters.entrySet()) {
                if (hasMatch(filter.getValue())) {
                    matched.add(filter.getKey());
                }
            }
        }
        if (matched.isEmpty() && result.has("filterMatchState") && MATCH_FOUND.equals(result.get("filterMatchState").getAsString())) {
            matched.add("unknown");
        }
        return matched.isEmpty() ? ArmorVerdict.safe() : ArmorVerdict.blocked(matched);
    }

    /** 検査ごとの結果の中に、matchState が MATCH_FOUND のものがあるか（入れ子の深さに依らず探す）。 */
    private boolean hasMatch(JsonElement element) {
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                if ("matchState".equals(entry.getKey()) && entry.getValue().isJsonPrimitive()
                        && MATCH_FOUND.equals(entry.getValue().getAsString())) {
                    return true;
                }
                if (hasMatch(entry.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }
}
