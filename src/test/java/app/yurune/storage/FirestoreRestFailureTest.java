package app.yurune.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.constant.LogConstants;
import app.yurune.gateway.FirestoreDocumentStore;
import app.yurune.gateway.StaticTokenProvider;
import app.yurune.repository.StorageUnavailableException;
import app.yurune.service.AuditLogger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Firestore の通信が失敗したときの動き（再試行、時間切れ、壊れた応答）を、擬似のサーバーで確かめる。
 */
class FirestoreRestFailureTest {

    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** 呼ばれた回数（1 から）に応じて、状態コードと本文を返す擬似の Firestore。 */
    private FirestoreDocumentStore startServer(IntFunction<Map.Entry<Integer, String>> responder) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            Map.Entry<Integer, String> response = responder.apply(calls.incrementAndGet());
            byte[] body = response.getValue().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(response.getKey(), body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        return new FirestoreDocumentStore("http://127.0.0.1:" + server.getAddress().getPort() + "/docs",
                new StaticTokenProvider("t"));
    }

    private static final String DOCUMENT = "{\"name\":\"projects/p/databases/(default)/documents/users/u1/arms/a\","
            + "\"fields\":{\"n\":{\"integerValue\":\"5\"}}}";

    @Test
    void aSingleServerErrorIsRetriedAndThenSucceeds() throws IOException {
        FirestoreDocumentStore store = startServer(count -> count == 1 ? Map.entry(503, "{}") : Map.entry(200, DOCUMENT));

        assertEquals(5L, store.get("users/u1/arms/a").orElseThrow().get("n"));
        assertEquals(2, calls.get());
    }

    @Test
    void aServerThatKeepsFailingBecomesStorageUnavailable() throws IOException {
        FirestoreDocumentStore store = startServer(count -> Map.entry(500, "{}"));

        assertThrows(StorageUnavailableException.class, () -> store.get("users/u1/arms/a"));
        assertEquals(2, calls.get(), "試す回数には上限がある");
    }

    @Test
    void aRejectionIsNotRetried() throws IOException {
        FirestoreDocumentStore store = startServer(count -> Map.entry(403, "{\"error\":{\"status\":\"PERMISSION_DENIED\"}}"));

        assertThrows(StorageUnavailableException.class, () -> store.set("users/u1/arms/a", Map.of("n", 1L)));
        assertEquals(1, calls.get());
    }

    @Test
    void notFoundMeansNoDocumentNotAnError() throws IOException {
        FirestoreDocumentStore store = startServer(count -> Map.entry(404, "{}"));

        assertTrue(store.get("users/u1/arms/a").isEmpty());
    }

    @Test
    void aBrokenResponseIsReportedAsUnavailable() throws IOException {
        FirestoreDocumentStore store = startServer(count -> Map.entry(200, "<html>oops</html>"));

        assertThrows(StorageUnavailableException.class, () -> store.get("users/u1/arms/a"));
    }

    @Test
    void anUnreachableServerBecomesStorageUnavailable() {
        FirestoreDocumentStore store = new FirestoreDocumentStore("http://127.0.0.1:1/docs", new StaticTokenProvider("t"));

        assertThrows(StorageUnavailableException.class, () -> store.get("users/u1/arms/a"));
    }

    @Test
    void aMissingAccessTokenBecomesStorageUnavailable() throws IOException {
        startServer(count -> Map.entry(200, DOCUMENT));
        FirestoreDocumentStore store = new FirestoreDocumentStore("http://127.0.0.1:" + server.getAddress().getPort() + "/docs",
                () -> {
                    throw new IllegalStateException("no token");
                });

        assertThrows(StorageUnavailableException.class, () -> store.get("users/u1/arms/a"));
    }

    @Test
    void theRequestCarriesTheBearerTokenAndNoSecretsInTheUrl() throws IOException {
        String[] seen = new String[2];
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            seen[0] = exchange.getRequestHeaders().getFirst("Authorization");
            seen[1] = exchange.getRequestURI().toString();
            byte[] body = DOCUMENT.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        FirestoreDocumentStore store = new FirestoreDocumentStore("http://127.0.0.1:" + server.getAddress().getPort() + "/docs",
                new StaticTokenProvider("secret-token"));

        store.get("users/u1/arms/a");

        assertEquals("Bearer secret-token", seen[0]);
        assertTrue(!seen[1].contains("secret-token"));
    }

    // ---- span ----

    @Test
    void aFastSuccessIsNotRecordedAsASpan() throws IOException {
        startServer(count -> Map.entry(200, DOCUMENT));
        List<String> lines = new ArrayList<>();
        AuditLogger logger = new AuditLogger(Clock.systemUTC(), null, lines::add);
        FirestoreDocumentStore store = new FirestoreDocumentStore("http://127.0.0.1:" + server.getAddress().getPort() + "/docs",
                new StaticTokenProvider("t"), logger);

        store.get("users/u1/arms/a");

        assertTrue(lines.isEmpty(), "速い成功は、量を減らすため span に出さない");
    }

    @Test
    void aFailureAfterRetriesIsRecordedAsASpanWithAnErrorOutcome() throws IOException {
        startServer(count -> Map.entry(500, "{}"));
        List<String> lines = new ArrayList<>();
        AuditLogger logger = new AuditLogger(Clock.systemUTC(), null, lines::add);
        FirestoreDocumentStore store = new FirestoreDocumentStore("http://127.0.0.1:" + server.getAddress().getPort() + "/docs",
                new StaticTokenProvider("t"), logger);

        assertThrows(StorageUnavailableException.class, () -> store.get("users/u1/arms/a"));

        assertEquals(1, lines.size());
        JsonObject detail = JsonParser.parseString(lines.get(0)).getAsJsonObject().getAsJsonObject(LogConstants.FIELD_DETAIL);
        assertEquals("firestore.request", detail.get("name").getAsString());
        assertEquals("error", detail.get("outcome").getAsString());
    }
}
