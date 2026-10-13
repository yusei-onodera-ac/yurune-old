package app.yurune.gateway;

import app.yurune.constant.HttpStatusConstants;
import app.yurune.service.AuthAccount;
import app.yurune.service.AuthException;
import app.yurune.service.AuthGateway;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

/**
 * Firebase Authentication（Identity Toolkit の REST API）で、メールとパスワードの登録とログインを行う。
 * 登録のあと、確認のメールを送る。ログインでは、メール確認が済んでいるかも調べて返す。
 * エミュレータでは、接続先だけを変えて、同じ呼び出しで動く。
 */
public final class FirebaseAuthGateway implements AuthGateway {

    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final Set<String> CREDENTIAL_ERRORS = Set.of(
            "INVALID_LOGIN_CREDENTIALS", "EMAIL_NOT_FOUND", "INVALID_PASSWORD", "USER_DISABLED", "MISSING_PASSWORD");

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final String accountsUrl;
    private final String apiKey;

    /**
     * @param baseUrl Identity Toolkit の URL の手前まで（本番は https://identitytoolkit.googleapis.com、
     *                エミュレータは http://ホスト:ポート/identitytoolkit.googleapis.com）
     */
    public FirebaseAuthGateway(String baseUrl, String apiKey) {
        this.accountsUrl = baseUrl + "/v1/accounts:";
        this.apiKey = apiKey;
    }

    @Override
    public AuthAccount register(String email, String password) {
        JsonObject created = call("signUp", credentialsBody(email, password));
        String idToken = created.get("idToken").getAsString();
        JsonObject verification = new JsonObject();
        verification.addProperty("requestType", "VERIFY_EMAIL");
        verification.addProperty("idToken", idToken);
        call("sendOobCode", verification);
        return new AuthAccount(created.get("localId").getAsString(), email, false);
    }

    @Override
    public AuthAccount signIn(String email, String password) {
        JsonObject signedIn = call("signInWithPassword", credentialsBody(email, password));
        JsonObject lookup = new JsonObject();
        lookup.addProperty("idToken", signedIn.get("idToken").getAsString());
        JsonArray users = call("lookup", lookup).getAsJsonArray("users");
        boolean verified = users != null && !users.isEmpty()
                && users.get(0).getAsJsonObject().has("emailVerified")
                && users.get(0).getAsJsonObject().get("emailVerified").getAsBoolean();
        return new AuthAccount(signedIn.get("localId").getAsString(), email, verified);
    }

    private JsonObject credentialsBody(String email, String password) {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);
        body.addProperty("returnSecureToken", true);
        return body;
    }

    private JsonObject call(String method, JsonObject body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(accountsUrl + method + "?key="
                        + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)))
                .timeout(TIMEOUT).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            if (response.statusCode() == HttpStatusConstants.OK) {
                return json;
            }
            throw toException(json);
        } catch (IOException | JsonParseException | IllegalStateException exception) {
            throw new AuthException(AuthException.Reason.UNAVAILABLE, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AuthException(AuthException.Reason.UNAVAILABLE, exception);
        }
    }

    /** Firebase のエラーの名前を、こちらの理由の種類に直す。 */
    private AuthException toException(JsonObject json) {
        String code = json.has("error") && json.getAsJsonObject("error").has("message")
                ? json.getAsJsonObject("error").get("message").getAsString() : "";
        String name = code.contains(":") ? code.substring(0, code.indexOf(':')).strip() : code;
        if (CREDENTIAL_ERRORS.contains(name)) {
            return new AuthException(AuthException.Reason.INVALID_CREDENTIALS);
        }
        return switch (name) {
            case "EMAIL_EXISTS" -> new AuthException(AuthException.Reason.EMAIL_ALREADY_USED);
            case "WEAK_PASSWORD" -> new AuthException(AuthException.Reason.WEAK_PASSWORD);
            case "INVALID_EMAIL" -> new AuthException(AuthException.Reason.INVALID_INPUT);
            case "TOO_MANY_ATTEMPTS_TRY_LATER" -> new AuthException(AuthException.Reason.TOO_MANY_ATTEMPTS);
            default -> new AuthException(AuthException.Reason.UNAVAILABLE);
        };
    }
}
