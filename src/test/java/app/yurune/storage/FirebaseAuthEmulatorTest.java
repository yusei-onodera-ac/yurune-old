package app.yurune.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.gateway.FirebaseAuthGateway;
import app.yurune.service.AuthAccount;
import app.yurune.service.AuthException;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Firebase Authentication（エミュレータ）に対して、登録、メール確認、ログイン、失敗の種類を確かめる。
 * 審査用アカウントが、メールを受け取らずにログインできること（受け入れ条件 AT-14 の後半）も確かめる。
 */
class FirebaseAuthEmulatorTest {

    private static final String PASSWORD = "password-12345";

    private FirebaseAuthGateway gateway;

    @BeforeEach
    void setUp() throws Exception {
        EmulatorSupport.assumeAuthEmulator();
        EmulatorSupport.clearAuth();
        gateway = new FirebaseAuthGateway("http://" + EmulatorSupport.authHost() + "/identitytoolkit.googleapis.com", "fake-key");
    }

    /** エミュレータが「送った」ことにしている確認メールの案内を開いて、メールの確認を済ませる。 */
    static void verifyEmail(String email) throws IOException, InterruptedException {
        HttpResponse<String> codes = EmulatorSupport.send("GET",
                "http://" + EmulatorSupport.authHost() + "/emulator/v1/projects/" + EmulatorSupport.PROJECT_ID + "/oobCodes", null);
        JsonArray list = JsonParser.parseString(codes.body()).getAsJsonObject().getAsJsonArray("oobCodes");
        String link = null;
        for (JsonElement element : list) {
            JsonObject code = element.getAsJsonObject();
            if (email.equals(code.get("email").getAsString()) && "VERIFY_EMAIL".equals(code.get("requestType").getAsString())) {
                link = code.get("oobLink").getAsString();
            }
        }
        assertTrue(link != null, "確認メールの案内が、見つからない");
        assertEquals(200, EmulatorSupport.send("GET", link, null).statusCode());
    }

    @Test
    void registrationSendsAVerificationMailAndLoginWaitsForIt() throws Exception {
        AuthAccount registered = gateway.register("a@example.com", PASSWORD);

        assertFalse(registered.isEmailVerified());
        assertFalse(gateway.signIn("a@example.com", PASSWORD).isEmailVerified());

        verifyEmail("a@example.com");

        AuthAccount verified = gateway.signIn("a@example.com", PASSWORD);
        assertTrue(verified.isEmailVerified());
        assertEquals(registered.getUid(), verified.getUid());
    }

    @Test
    void failuresAreReportedByKind() {
        gateway.register("a@example.com", PASSWORD);

        assertEquals(AuthException.Reason.INVALID_CREDENTIALS,
                assertThrows(AuthException.class, () -> gateway.signIn("a@example.com", "wrong-password")).getReason());
        assertEquals(AuthException.Reason.INVALID_CREDENTIALS,
                assertThrows(AuthException.class, () -> gateway.signIn("nobody@example.com", PASSWORD)).getReason());
        assertEquals(AuthException.Reason.EMAIL_ALREADY_USED,
                assertThrows(AuthException.class, () -> gateway.register("a@example.com", PASSWORD)).getReason());
        assertEquals(AuthException.Reason.WEAK_PASSWORD,
                assertThrows(AuthException.class, () -> gateway.register("b@example.com", "123")).getReason());
    }

    @Test
    void theReviewAccountCanLogInWithoutReceivingAnyEmail() throws Exception {
        String body = "{\"email\":\"demo@example.com\",\"password\":\"" + PASSWORD + "\",\"emailVerified\":true}";
        HttpResponse<String> created = adminCreate(body);
        assertEquals(200, created.statusCode(), created.body());

        AuthAccount account = gateway.signIn("demo@example.com", PASSWORD);

        assertTrue(account.isEmailVerified());
    }

    @Test
    void anUnreachableAuthServerIsReportedAsUnavailable() {
        FirebaseAuthGateway unreachable = new FirebaseAuthGateway("http://127.0.0.1:1/identitytoolkit.googleapis.com", "k");

        assertEquals(AuthException.Reason.UNAVAILABLE,
                assertThrows(AuthException.class, () -> unreachable.signIn("a@example.com", PASSWORD)).getReason());
    }

    /** 管理者の権限（エミュレータでは、固定の値）で、確認済みのアカウントを作る。審査用アカウントの作り方と同じ。 */
    static HttpResponse<String> adminCreate(String body) throws IOException, InterruptedException {
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                        "http://" + EmulatorSupport.authHost() + "/identitytoolkit.googleapis.com/v1/projects/"
                                + EmulatorSupport.PROJECT_ID + "/accounts"))
                .header("Content-Type", "application/json").header("Authorization", "Bearer owner")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build();
        return java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
