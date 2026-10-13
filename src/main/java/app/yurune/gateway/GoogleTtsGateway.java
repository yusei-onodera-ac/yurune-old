package app.yurune.gateway;

import app.yurune.constant.ConversationConstants;
import app.yurune.constant.HttpStatusConstants;
import app.yurune.domain.value.SpanName;
import app.yurune.domain.value.VoiceStyle;
import app.yurune.service.AuditLogger;
import app.yurune.service.SpeechGateway;
import app.yurune.service.SpeechUnavailableException;

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
import java.util.Base64;

/**
 * Cloud Text-to-Speech の REST API で、ぽてねの声を作る（日本語の標準の声、小さめ。速さと高さは、設定の声の種類で決まる。既定は遅め・低め）。
 * 検査を通した文だけを渡す。音声は、受け取ってすぐ端末へ渡し、保存しない。
 * 呼び出しは、サービスアカウントのトークンで行う。失敗は SpeechUnavailableException（端末の読み上げに切り替わる）。
 * 参照: https://docs.cloud.google.com/text-to-speech/docs/reference/rest/v1/text/synthesize
 */
public final class GoogleTtsGateway implements SpeechGateway {

    private static final Duration TIMEOUT = Duration.ofSeconds(6);
    private static final String DEFAULT_ENDPOINT = "https://texttospeech.googleapis.com";
    private static final long NANOS_PER_MILLI = 1_000_000;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final String synthesizeUrl;
    private final AccessTokenProvider tokenProvider;
    private final AuditLogger auditLogger;

    public GoogleTtsGateway(AccessTokenProvider tokenProvider) {
        this(DEFAULT_ENDPOINT, tokenProvider);
    }

    public GoogleTtsGateway(AccessTokenProvider tokenProvider, AuditLogger auditLogger) {
        this(DEFAULT_ENDPOINT, tokenProvider, auditLogger);
    }

    public GoogleTtsGateway(String endpoint, AccessTokenProvider tokenProvider) {
        this(endpoint, tokenProvider, null);
    }

    /** @param auditLogger 呼び出し 1 回ごとの区間の記録（B2）の出し先。省略できる（省略時は記録しない） */
    public GoogleTtsGateway(String endpoint, AccessTokenProvider tokenProvider, AuditLogger auditLogger) {
        this.synthesizeUrl = endpoint + "/v1/text:synthesize";
        this.tokenProvider = tokenProvider;
        this.auditLogger = auditLogger;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public byte[] synthesize(String text) {
        return synthesize(text, VoiceStyle.findDefault());
    }

    @Override
    public byte[] synthesize(String text, VoiceStyle style) {
        if (text == null || text.isBlank() || text.length() > ConversationConstants.SPEAK_MAX_CHARS) {
            throw new SpeechUnavailableException("text is empty or too long");
        }
        long startedAt = System.nanoTime();
        try {
            byte[] audio = sendSynthesizeRequest(text, style);
            recordSpan(startedAt, true);
            return audio;
        } catch (SpeechUnavailableException exception) {
            recordSpan(startedAt, false);
            throw exception;
        }
    }

    private byte[] sendSynthesizeRequest(String text, VoiceStyle style) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(synthesizeUrl)).timeout(TIMEOUT)
                    .header("Content-Type", "application/json").header("Authorization", "Bearer " + tokenProvider.getToken())
                    .POST(HttpRequest.BodyPublishers.ofString(createBody(text, style).toString(), StandardCharsets.UTF_8)).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != HttpStatusConstants.OK) {
                throw new SpeechUnavailableException("text-to-speech returned " + response.statusCode());
            }
            JsonElement audio = JsonParser.parseString(response.body()).getAsJsonObject().get("audioContent");
            if (audio == null || !audio.isJsonPrimitive()) {
                throw new SpeechUnavailableException("text-to-speech response has no audio");
            }
            return Base64.getDecoder().decode(audio.getAsString());
        } catch (IOException | JsonParseException | IllegalStateException | IllegalArgumentException exception) {
            throw new SpeechUnavailableException("text-to-speech could not be used", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SpeechUnavailableException("interrupted while calling text-to-speech", exception);
        }
    }

    private void recordSpan(long startedAtNanos, boolean ok) {
        if (auditLogger != null) {
            auditLogger.span(SpanName.TTS_SYNTHESIZE, (System.nanoTime() - startedAtNanos) / NANOS_PER_MILLI, ok);
        }
    }

    private JsonObject createBody(String text, VoiceStyle style) {
        JsonObject input = new JsonObject();
        input.addProperty("text", text);
        JsonObject voice = new JsonObject();
        voice.addProperty("languageCode", ConversationConstants.VOICE_LANGUAGE);
        voice.addProperty("name", ConversationConstants.VOICE_NAME);
        JsonObject audioConfig = new JsonObject();
        audioConfig.addProperty("audioEncoding", "MP3");
        audioConfig.addProperty("speakingRate", style.getSpeakingRate());
        audioConfig.addProperty("pitch", style.getPitch());
        audioConfig.addProperty("volumeGainDb", ConversationConstants.VOLUME_GAIN_DB);
        JsonObject body = new JsonObject();
        body.add("input", input);
        body.add("voice", voice);
        body.add("audioConfig", audioConfig);
        return body;
    }
}
