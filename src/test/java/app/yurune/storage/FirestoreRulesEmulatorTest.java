package app.yurune.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.yurune.gateway.FirestoreDocumentStore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 受け入れ条件 AT-14: Firestore のセキュリティルールが、未認証の読み取り、他の利用者のデータの読み取り、
 * メール未確認の読み取り、クライアントからの書き込みを、すべて断ること。サーバー（サービスアカウント）だけが、読み書きできること。
 * 実際のルール（firebase/firestore.rules）を読み込んだエミュレータに、利用者のトークンでつないで確かめる。
 */
class FirestoreRulesEmulatorTest {

    private static final String PASSWORD = "password-12345";
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private String aliceUid;
    private String aliceToken;
    private String bobToken;
    private String carolToken;

    @BeforeEach
    void setUp() throws Exception {
        EmulatorSupport.assumeFirestoreEmulator();
        EmulatorSupport.assumeAuthEmulator();
        EmulatorSupport.clearFirestore();
        EmulatorSupport.clearAuth();

        JsonObject alice = signUp("alice@example.com");
        aliceUid = alice.get("localId").getAsString();
        aliceToken = alice.get("idToken").getAsString();
        FirebaseAuthEmulatorTest.verifyEmail("alice@example.com");
        aliceToken = signIn("alice@example.com");

        signUp("bob@example.com");
        FirebaseAuthEmulatorTest.verifyEmail("bob@example.com");
        bobToken = signIn("bob@example.com");

        signUp("carol@example.com");
        carolToken = signIn("carol@example.com");

        FirestoreDocumentStore admin = EmulatorSupport.createAdminStore();
        admin.set("users/" + aliceUid + "/arms/sound__rain", Map.of("trials", 3L));
        admin.set("secrets/key", Map.of("value", "hidden"));
    }

    /** 登録して、確認のメールを送らせる（エミュレータは、送った案内を覚えておく）。 */
    private JsonObject signUp(String email) throws IOException, InterruptedException {
        JsonObject created = post("signUp", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"returnSecureToken\":true}");
        post("sendOobCode", "{\"requestType\":\"VERIFY_EMAIL\",\"idToken\":\"" + created.get("idToken").getAsString() + "\"}");
        return created;
    }

    private String signIn(String email) throws IOException, InterruptedException {
        return post("signInWithPassword", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"returnSecureToken\":true}")
                .get("idToken").getAsString();
    }

    private JsonObject post(String method, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + EmulatorSupport.authHost()
                        + "/identitytoolkit.googleapis.com/v1/accounts:" + method + "?key=fake"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return JsonParser.parseString(CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body()).getAsJsonObject();
    }

    /** 利用者のトークン（なければ、未認証）で、文書を読む。 */
    private int read(String token, String documentPath) throws IOException, InterruptedException {
        return call("GET", token, documentPath, null);
    }

    private int call(String method, String token, String documentPath, String body) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://" + EmulatorSupport.firestoreHost()
                + "/v1/projects/" + EmulatorSupport.PROJECT_ID + "/databases/(default)/documents/" + documentPath));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    @Test
    void anonymousVisitorsCannotReadAnyUserData() throws Exception {
        assertEquals(403, read(null, "users/" + aliceUid + "/arms/sound__rain"));
    }

    @Test
    void aVerifiedUserCanReadOwnData() throws Exception {
        assertEquals(200, read(aliceToken, "users/" + aliceUid + "/arms/sound__rain"));
    }

    @Test
    void anotherVerifiedUserCannotReadSomeoneElsesData() throws Exception {
        assertEquals(403, read(bobToken, "users/" + aliceUid + "/arms/sound__rain"));
    }

    @Test
    void aUserWhoNeverVerifiedTheEmailCannotReadEvenOwnData() throws Exception {
        FirestoreDocumentStore admin = EmulatorSupport.createAdminStore();
        String carolUid = post("signInWithPassword", "{\"email\":\"carol@example.com\",\"password\":\"" + PASSWORD
                + "\",\"returnSecureToken\":true}").get("localId").getAsString();
        admin.set("users/" + carolUid + "/arms/sound__rain", Map.of("trials", 1L));

        assertEquals(403, read(carolToken, "users/" + carolUid + "/arms/sound__rain"));
    }

    @Test
    void nobodyCanWriteFromTheClientSide() throws Exception {
        String body = "{\"fields\":{\"trials\":{\"integerValue\":\"99\"}}}";

        assertEquals(403, call("PATCH", aliceToken, "users/" + aliceUid + "/arms/sound__rain", body), "本人でも、書けない");
        assertEquals(403, call("PATCH", aliceToken, "users/" + aliceUid + "/arms/new", body));
        assertEquals(403, call("PATCH", null, "users/" + aliceUid + "/arms/new", body));
        assertEquals(403, call("DELETE", aliceToken, "users/" + aliceUid + "/arms/sound__rain", null));
    }

    @Test
    void placesOutsideTheUsersFolderAreClosedToEveryone() throws Exception {
        assertEquals(403, read(aliceToken, "secrets/key"));
        assertEquals(403, read(null, "secrets/key"));
    }

    @Test
    void theServerWithServiceAccountPowerCanReadAndWrite() {
        FirestoreDocumentStore admin = EmulatorSupport.createAdminStore();

        admin.set("users/" + aliceUid + "/arms/sound__wave", Map.of("trials", 1L));

        assertEquals(1L, admin.get("users/" + aliceUid + "/arms/sound__wave").orElseThrow().get("trials"));
    }
}
