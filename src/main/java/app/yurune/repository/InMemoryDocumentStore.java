package app.yurune.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * メモリ上の文書ストア。開発・テスト用で、Firestore と同じパスの形で保存する。
 * 再起動すると消える。
 */
public final class InMemoryDocumentStore implements DocumentStore {

    private static final String SEPARATOR = "/";

    private final ConcurrentSkipListMap<String, Map<String, Object>> documents = new ConcurrentSkipListMap<>();

    @Override
    public Optional<Map<String, Object>> get(String documentPath) {
        return Optional.ofNullable(documents.get(documentPath));
    }

    @Override
    public void set(String documentPath, Map<String, Object> fields) {
        documents.put(documentPath, Collections.unmodifiableMap(new HashMap<>(fields)));
    }

    @Override
    public void delete(String documentPath) {
        documents.remove(documentPath);
    }

    @Override
    public List<StoredDocument> list(String collectionPath) {
        List<StoredDocument> result = new ArrayList<>();
        String prefix = collectionPath + SEPARATOR;
        for (Map.Entry<String, Map<String, Object>> entry : documents.subMap(prefix, prefix + Character.MAX_VALUE).entrySet()) {
            String rest = entry.getKey().substring(prefix.length());
            if (!rest.contains(SEPARATOR)) {
                result.add(new StoredDocument(rest, entry.getValue()));
            }
        }
        return result;
    }

    @Override
    public List<StoredDocument> queryOrderedByField(String collectionPath, String field, boolean descending, int limit) {
        Comparator<StoredDocument> byField = Comparator.comparing(document -> String.valueOf(document.getFields().get(field)));
        return list(collectionPath).stream()
                .filter(document -> document.getFields().containsKey(field))
                .sorted(descending ? byField.reversed() : byField)
                .limit(limit)
                .toList();
    }

    @Override
    public List<StoredDocument> queryFieldEquals(String collectionPath, String field, String value) {
        return list(collectionPath).stream()
                .filter(document -> value.equals(document.getFields().get(field)))
                .toList();
    }

    @Override
    public void deleteTree(String documentPath) {
        documents.remove(documentPath);
        String prefix = documentPath + SEPARATOR;
        documents.subMap(prefix, prefix + Character.MAX_VALUE).clear();
    }
}
