package app.yurune.gateway;

import app.yurune.constant.LlmConstants;
import app.yurune.domain.value.FallbackReason;
import app.yurune.domain.value.SpanName;
import app.yurune.service.AuditLogger;
import app.yurune.service.LlmGateway;
import app.yurune.service.LlmUnavailableException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Gemini API（REST）を呼ぶ窓口。キーはヘッダーで送り、ログにも例外にも出さない。
 * 時間切れや、200 以外の応答は、LlmUnavailableException にして、呼び出し側が規則に切り替える。
 * 再試行はしない（次の観測で、もう一度呼ばれるため）。
 */
public final class GeminiRestGateway implements LlmGateway {

    private static final int HTTP_OK = 200;
    private static final long NANOS_PER_MILLI = 1_000_000;

    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final String endpoint;
    private final AuditLogger auditLogger;

    public GeminiRestGateway(String apiKey, String model) {
        this(apiKey, model, LlmConstants.ENDPOINT);
    }

    /** @param endpoint モデルの名前の手前までの URL（試験で、擬似のサーバーに向けるときに変える） */
    public GeminiRestGateway(String apiKey, String model, String endpoint) {
        this(apiKey, model, endpoint, null);
    }

    /** @param auditLogger 呼び出し 1 回ごとの区間の記録（B2）の出し先。省略できる（省略時は記録しない） */
    public GeminiRestGateway(String apiKey, String model, String endpoint, AuditLogger auditLogger) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(LlmConstants.TIMEOUT_SEC))
                .build();
        this.apiKey = apiKey;
        this.model = model;
        this.endpoint = endpoint;
        this.auditLogger = auditLogger;
    }

    @Override
    public String generate(String requestJson) {
        long startedAt = System.nanoTime();
        try {
            String response = sendGenerateRequest(requestJson);
            recordSpan(startedAt, true);
            return response;
        } catch (LlmUnavailableException exception) {
            recordSpan(startedAt, false);
            throw exception;
        }
    }

    private String sendGenerateRequest(String requestJson) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint + model + ":generateContent"))
                .timeout(Duration.ofSeconds(LlmConstants.TIMEOUT_SEC))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != HTTP_OK) {
                throw new LlmUnavailableException("llm returned HTTP " + response.statusCode(), FallbackReason.LLM_ERROR);
            }
            return response.body();
        } catch (HttpTimeoutException exception) {
            throw new LlmUnavailableException("llm call timed out", FallbackReason.TIMEOUT, exception);
        } catch (IOException exception) {
            throw new LlmUnavailableException("llm call failed: " + exception.getClass().getSimpleName(),
                    FallbackReason.LLM_ERROR, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LlmUnavailableException("llm call was interrupted", FallbackReason.LLM_ERROR, exception);
        }
    }

    private void recordSpan(long startedAtNanos, boolean ok) {
        if (auditLogger != null) {
            auditLogger.span(SpanName.GEMINI_GENERATE, (System.nanoTime() - startedAtNanos) / NANOS_PER_MILLI, ok);
        }
    }

    @Override
    public String getModel() {
        return model;
    }
}
