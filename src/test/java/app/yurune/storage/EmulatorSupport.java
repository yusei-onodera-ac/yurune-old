package app.yurune.storage;

import app.yurune.gateway.FirestoreDocumentStore;
import app.yurune.gateway.StaticTokenProvider;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Assumptions;

/**
 * Firestore と Authentication のエミュレータにつなぐ、テスト用の支え。
 * 環境変数 FIRESTORE_EMULATOR_HOST と FIREBASE_AUTH_EMULATOR_HOST があるときだけ動く（なければ、そのテストは飛ばす）。
 * エミュレータは、firebase/ のフォルダで、firebase emulators:start --only firestore,auth --project yurune-test で起動する。
 */
public final class EmulatorSupport {

    public static final String PROJECT_ID = "yurune-test";

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private EmulatorSupport() {
    }

    public static String firestoreHost() {
        return System.getenv("FIRESTORE_EMULATOR_HOST");
    }

    public static String authHost() {
        return System.getenv("FIREBASE_AUTH_EMULATOR_HOST");
    }

    public static void assumeFirestoreEmulator() {
        Assumptions.assumeTrue(firestoreHost() != null && !firestoreHost().isBlank(),
                "FIRESTORE_EMULATOR_HOST がないので、エミュレータのテストを飛ばす");
    }

    public static void assumeAuthEmulator() {
        Assumptions.assumeTrue(authHost() != null && !authHost().isBlank(),
                "FIREBASE_AUTH_EMULATOR_HOST がないので、エミュレータのテストを飛ばす");
    }

    /** サーバーと同じ、全権限（ルールを通らない）でつなぐ文書ストア。 */
    public static FirestoreDocumentStore createAdminStore() {
        assumeFirestoreEmulator();
        return new FirestoreDocumentStore(
                FirestoreDocumentStore.createDocumentsUrl("http://" + firestoreHost(), PROJECT_ID),
                new StaticTokenProvider(StaticTokenProvider.EMULATOR_OWNER));
    }

    /** エミュレータの全データを消す（テストごとに、まっさらにするため）。 */
    public static void clearFirestore() throws IOException, InterruptedException {
        send("DELETE", "http://" + firestoreHost() + "/emulator/v1/projects/" + PROJECT_ID + "/databases/(default)/documents", null);
    }

    public static void clearAuth() throws IOException, InterruptedException {
        send("DELETE", "http://" + authHost() + "/emulator/v1/projects/" + PROJECT_ID + "/accounts", null);
    }

    public static HttpResponse<String> send(String method, String url, String body) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
