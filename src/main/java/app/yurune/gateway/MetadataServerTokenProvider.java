package app.yurune.gateway;

import app.yurune.constant.HttpStatusConstants;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Cloud Run（Compute Engine）のメタデータサーバーから、サービスアカウントのアクセストークンを得る。
 * 期限の少し前まで使い回す。
 */
public final class MetadataServerTokenProvider implements AccessTokenProvider {

    private static final String DEFAULT_URL =
            "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token";
    private static final String FLAVOR_HEADER = "Metadata-Flavor";
    private static final String FLAVOR_VALUE = "Google";
    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    /** 期限ぎりぎりのトークンで呼び出して、拒否されないための余裕。 */
    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(60);

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final String url;
    private final Clock clock;
    private String cachedToken;
    private Instant expiresAt = Instant.EPOCH;

    public MetadataServerTokenProvider(Clock clock) {
        this(DEFAULT_URL, clock);
    }

    MetadataServerTokenProvider(String url, Clock clock) {
        this.url = url;
        this.clock = clock;
    }

    @Override
    public synchronized String getToken() {
        if (cachedToken != null && clock.instant().isBefore(expiresAt.minus(EXPIRY_MARGIN))) {
            return cachedToken;
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT)
                .header(FLAVOR_HEADER, FLAVOR_VALUE).GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != HttpStatusConstants.OK) {
                throw new IllegalStateException("metadata server returned " + response.statusCode());
            }
            JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
            cachedToken = body.get("access_token").getAsString();
            expiresAt = clock.instant().plusSeconds(body.get("expires_in").getAsLong());
            return cachedToken;
        } catch (IOException exception) {
            throw new IllegalStateException("metadata server is not reachable", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while asking the metadata server", exception);
        }
    }
}
