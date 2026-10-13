package app.yurune.gateway;

import app.yurune.constant.HttpStatusConstants;
import app.yurune.constant.ObservabilityConstants;
import app.yurune.constant.StorageConstants;
import app.yurune.domain.value.SpanName;
import app.yurune.repository.DocumentStore;
import app.yurune.repository.StorageUnavailableException;
import app.yurune.repository.StoredDocument;
import app.yurune.service.AuditLogger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Firestore の REST API で、文書を読み書きする。
 * 本番は、サービスアカウントのトークンで呼ぶ。エミュレータは、トークンの代わりに固定の値で呼ぶ。
 * 通信の失敗、時間切れ、サーバーの不調は、StorageUnavailableException にする（呼び出し側が縮退を決める）。
 */
public final class FirestoreDocumentStore implements DocumentStore {

    private static final String SEPARATOR = "/";
    private static final long NANOS_PER_MILLI = 1_000_000;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(StorageConstants.REQUEST_TIMEOUT_SEC)).build();
    private final FirestoreValueCodec codec = new FirestoreValueCodec();
    private final String documentsUrl;
    private final AccessTokenProvider tokenProvider;
    private final AuditLogger auditLogger;

    /**
     * @param documentsUrl Firestore の documents までの URL（例: https://firestore.googleapis.com/v1/projects/P/databases/(default)/documents）
     */
    public FirestoreDocumentStore(String documentsUrl, AccessTokenProvider tokenProvider) {
        this(documentsUrl, tokenProvider, null);
    }

    /** @param auditLogger 呼び出しの区間の記録（B2）の出し先。遅いもの・失敗だけを記録する。省略できる（省略時は記録しない） */
    public FirestoreDocumentStore(String documentsUrl, AccessTokenProvider tokenProvider, AuditLogger auditLogger) {
        this.documentsUrl = documentsUrl;
        this.tokenProvider = tokenProvider;
        this.auditLogger = auditLogger;
    }

    public static String createDocumentsUrl(String baseUrl, String projectId) {
        return baseUrl + "/v1/projects/" + projectId + "/databases/(default)/documents";
    }

    @Override
    public Optional<Map<String, Object>> get(String documentPath) {
        JsonObject response = send("GET", urlOf(documentPath), null, true);
        if (response == null) {
            return Optional.empty();
        }
        return Optional.of(codec.decodeFields(response.getAsJsonObject("fields")));
    }

    @Override
    public void set(String documentPath, Map<String, Object> fields) {
        JsonObject body = new JsonObject();
        body.add("fields", codec.encodeFields(fields));
        send("PATCH", urlOf(documentPath), body.toString(), false);
    }

    @Override
    public void delete(String documentPath) {
        send("DELETE", urlOf(documentPath), null, true);
    }

    @Override
    public List<StoredDocument> list(String collectionPath) {
        return listDocuments(collectionPath, false);
    }

    @Override
    public List<StoredDocument> queryOrderedByField(String collectionPath, String field, boolean descending, int limit) {
        JsonObject query = createQuery(collectionPath);
        JsonObject order = new JsonObject();
        order.add("field", fieldReference(field));
        order.addProperty("direction", descending ? "DESCENDING" : "ASCENDING");
        JsonArray orderBy = new JsonArray();
        orderBy.add(order);
        query.add("orderBy", orderBy);
        query.addProperty("limit", limit);
        return runQuery(collectionPath, query);
    }

    @Override
    public List<StoredDocument> queryFieldEquals(String collectionPath, String field, String value) {
        JsonObject query = createQuery(collectionPath);
        JsonObject filter = new JsonObject();
        filter.add("field", fieldReference(field));
        filter.addProperty("op", "EQUAL");
        filter.add("value", codec.encodeValue(value));
        JsonObject where = new JsonObject();
        where.add("fieldFilter", filter);
        query.add("where", where);
        return runQuery(collectionPath, query);
    }

    /** REST には再帰の削除がないので、下のコレクションを順にたどって、1 件ずつ消す。 */
    @Override
    public void deleteTree(String documentPath) {
        for (String collectionId : listCollectionIds(documentPath)) {
            String collectionPath = documentPath + SEPARATOR + collectionId;
            for (StoredDocument child : listDocuments(collectionPath, true)) {
                deleteTree(collectionPath + SEPARATOR + child.getId());
            }
        }
        delete(documentPath);
    }

    // ---- 内部 ----

    private List<StoredDocument> listDocuments(String collectionPath, boolean showMissing) {
        List<StoredDocument> result = new ArrayList<>();
        String pageToken = null;
        do {
            String url = urlOf(collectionPath) + "?pageSize=" + StorageConstants.LIST_PAGE_SIZE
                    + (showMissing ? "&showMissing=true" : "")
                    + (pageToken == null ? "" : "&pageToken=" + encode(pageToken));
            JsonObject response = send("GET", url, null, true);
            if (response == null) {
                return result;
            }
            JsonArray documents = response.getAsJsonArray("documents");
            if (documents != null) {
                for (JsonElement document : documents) {
                    result.add(toStoredDocument(document.getAsJsonObject()));
                }
            }
            pageToken = response.has("nextPageToken") ? response.get("nextPageToken").getAsString() : null;
        } while (pageToken != null);
        return result;
    }

    private List<String> listCollectionIds(String documentPath) {
        List<String> ids = new ArrayList<>();
        JsonObject response = send("POST", urlOf(documentPath) + ":listCollectionIds", "{\"pageSize\":100}", true);
        if (response != null && response.has("collectionIds")) {
            for (JsonElement id : response.getAsJsonArray("collectionIds")) {
                ids.add(id.getAsString());
            }
        }
        return ids;
    }

    private JsonObject createQuery(String collectionPath) {
        JsonObject from = new JsonObject();
        from.addProperty("collectionId", collectionPath.substring(collectionPath.lastIndexOf(SEPARATOR) + 1));
        JsonArray fromArray = new JsonArray();
        fromArray.add(from);
        JsonObject query = new JsonObject();
        query.add("from", fromArray);
        return query;
    }

    private JsonObject fieldReference(String field) {
        JsonObject reference = new JsonObject();
        reference.addProperty("fieldPath", field);
        return reference;
    }

    private List<StoredDocument> runQuery(String collectionPath, JsonObject structuredQuery) {
        int lastSeparator = collectionPath.lastIndexOf(SEPARATOR);
        String parentPath = lastSeparator < 0 ? "" : collectionPath.substring(0, lastSeparator);
        JsonObject body = new JsonObject();
        body.add("structuredQuery", structuredQuery);
        String url = (parentPath.isEmpty() ? documentsUrl : urlOf(parentPath)) + ":runQuery";
        String text = sendForText("POST", url, body.toString(), false);
        List<StoredDocument> result = new ArrayList<>();
        try {
            for (JsonElement entry : JsonParser.parseString(text).getAsJsonArray()) {
                JsonObject item = entry.getAsJsonObject();
                if (item.has("document")) {
                    result.add(toStoredDocument(item.getAsJsonObject("document")));
                }
            }
        } catch (JsonParseException | IllegalStateException exception) {
            throw new StorageUnavailableException("unexpected query response", exception);
        }
        return result;
    }

    private StoredDocument toStoredDocument(JsonObject document) {
        String name = document.get("name").getAsString();
        return new StoredDocument(name.substring(name.lastIndexOf(SEPARATOR) + 1),
                codec.decodeFields(document.getAsJsonObject("fields")));
    }

    private String urlOf(String path) {
        StringBuilder url = new StringBuilder(documentsUrl);
        for (String segment : path.split(SEPARATOR)) {
            url.append(SEPARATOR).append(encode(segment));
        }
        return url.toString();
    }

    private String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** 本文を JSON として返す。許す場合に限り、404 は null（なかった）にする。 */
    private JsonObject send(String method, String url, String body, boolean notFoundIsNull) {
        String text = sendForText(method, url, body, notFoundIsNull);
        if (text == null) {
            return null;
        }
        try {
            return JsonParser.parseString(text.isBlank() ? "{}" : text).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException exception) {
            throw new StorageUnavailableException("unexpected response", exception);
        }
    }

    private String sendForText(String method, String url, String body, boolean notFoundIsNull) {
        long startedAt = System.nanoTime();
        try {
            String result = sendForTextWithRetry(method, url, body, notFoundIsNull);
            recordSpanIfSlow(startedAt, true);
            return result;
        } catch (StorageUnavailableException exception) {
            recordSpanIfSlow(startedAt, false);
            throw exception;
        }
    }

    /** 失敗、または、しきい値を超える遅さのときだけ記録する（B2。量が多いため）。 */
    private void recordSpanIfSlow(long startedAtNanos, boolean ok) {
        if (auditLogger == null) {
            return;
        }
        long elapsedMillis = (System.nanoTime() - startedAtNanos) / NANOS_PER_MILLI;
        if (!ok || elapsedMillis >= ObservabilityConstants.FIRESTORE_SPAN_SLOW_MS) {
            auditLogger.span(SpanName.FIRESTORE_REQUEST, elapsedMillis, ok);
        }
    }

    private String sendForTextWithRetry(String method, String url, String body, boolean notFoundIsNull) {
        StorageUnavailableException lastFailure = null;
        for (int attempt = 1; attempt <= StorageConstants.REQUEST_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(buildRequest(method, url, body),
                        HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status == HttpStatusConstants.OK) {
                    return response.body();
                }
                if (status == HttpStatusConstants.NOT_FOUND && notFoundIsNull) {
                    return null;
                }
                lastFailure = new StorageUnavailableException("firestore returned " + status);
                if (status != HttpStatusConstants.TOO_MANY_REQUESTS && status < HttpStatusConstants.SERVER_ERROR) {
                    throw lastFailure;
                }
            } catch (IOException exception) {
                lastFailure = new StorageUnavailableException("firestore is not reachable", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new StorageUnavailableException("interrupted while calling firestore", exception);
            } catch (IllegalStateException exception) {
                throw new StorageUnavailableException("no access token for firestore", exception);
            }
            pauseBeforeRetry(attempt);
        }
        throw lastFailure;
    }

    private HttpRequest buildRequest(String method, String url, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(StorageConstants.REQUEST_TIMEOUT_SEC))
                .header("Authorization", "Bearer " + tokenProvider.getToken());
        if (body == null) {
            return builder.method(method, HttpRequest.BodyPublishers.noBody()).build();
        }
        return builder.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
    }

    private void pauseBeforeRetry(int attempt) {
        if (attempt >= StorageConstants.REQUEST_ATTEMPTS) {
            return;
        }
        try {
            Thread.sleep(StorageConstants.RETRY_BACKOFF_MS * attempt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
