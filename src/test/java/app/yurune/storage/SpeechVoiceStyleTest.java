package app.yurune.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.constant.ConversationConstants;
import app.yurune.domain.value.VoiceStyle;
import app.yurune.gateway.GoogleTtsGateway;
import app.yurune.gateway.StaticTokenProvider;
import app.yurune.service.DisabledSpeechGateway;
import app.yurune.service.SpeechGateway;
import app.yurune.service.SpeechUnavailableException;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 設定の「ぽてねの声」が、サーバーの読み上げ（Text-to-Speech）の速さと高さとして送られること。
 * 声そのもの（言語、声の名前）と、夜に眩しくない小さめの音量は、どの種類でも変わらない。擬似のサーバーで確かめる。
 */
class SpeechVoiceStyleTest {

    private static final double EPSILON = 0.0001;

    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>();

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private GoogleTtsGateway startGateway() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = "{\"audioContent\":\"AQID\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        return new GoogleTtsGateway("http://127.0.0.1:" + server.getAddress().getPort(), new StaticTokenProvider("tok"));
    }

    private JsonObject sentAudioConfig() {
        return JsonParser.parseString(body.get()).getAsJsonObject().getAsJsonObject("audioConfig");
    }

    @Test
    void eachVoiceStyleIsSentAsItsOwnSpeedAndPitchWithTheSameVoiceAndVolume() throws IOException {
        GoogleTtsGateway gateway = startGateway();

        for (VoiceStyle style : VoiceStyle.values()) {
            gateway.synthesize("おやすみなさい", style);

            JsonObject sent = JsonParser.parseString(body.get()).getAsJsonObject();
            assertEquals(style.getSpeakingRate(), sentAudioConfig().get("speakingRate").getAsDouble(), EPSILON, style.asId());
            assertEquals(style.getPitch(), sentAudioConfig().get("pitch").getAsDouble(), EPSILON, style.asId());
            assertEquals(ConversationConstants.VOICE_NAME, sent.getAsJsonObject("voice").get("name").getAsString(), style.asId());
            assertEquals(ConversationConstants.VOLUME_GAIN_DB, sentAudioConfig().get("volumeGainDb").getAsDouble(), EPSILON, style.asId());
        }
    }

    @Test
    void theStylesReallyDifferSoTheSettingHasAnEffect() throws IOException {
        GoogleTtsGateway gateway = startGateway();

        gateway.synthesize("こんばんは", VoiceStyle.SLOW);
        double slow = sentAudioConfig().get("speakingRate").getAsDouble();
        gateway.synthesize("こんばんは", VoiceStyle.NORMAL);
        double normal = sentAudioConfig().get("speakingRate").getAsDouble();

        assertTrue(slow < normal);
    }

    @Test
    void withoutAStyleTheDefaultVoiceKeepsTheSpeedAndPitchOfTheSpecification() throws IOException {
        GoogleTtsGateway gateway = startGateway();

        gateway.synthesize("こんばんは");

        assertEquals(ConversationConstants.SPEAKING_RATE, sentAudioConfig().get("speakingRate").getAsDouble(), EPSILON);
        assertEquals(ConversationConstants.PITCH, sentAudioConfig().get("pitch").getAsDouble(), EPSILON);
        assertEquals(VoiceStyle.SOFT.getSpeakingRate(), ConversationConstants.SPEAKING_RATE, EPSILON, "「やわらかい」は、これまでの声");
    }

    @Test
    void aGatewayThatCannotSwitchVoicesStillWorksWithAStyle() {
        SpeechGateway disabled = new DisabledSpeechGateway();

        assertThrows(SpeechUnavailableException.class, () -> disabled.synthesize("こんばんは", VoiceStyle.SLOW));
    }
}
