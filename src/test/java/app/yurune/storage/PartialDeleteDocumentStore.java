package app.yurune.storage;

import app.yurune.repository.DocumentStore;
import app.yurune.repository.InMemoryDocumentStore;
import app.yurune.repository.StorageUnavailableException;
import app.yurune.repository.StoredDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * 消去の途中失敗を試すための文書ストア。Firestore の deleteTree と同じく、下位の文書を 1 件ずつ消す実装にし、
 * 指定した件数を消したところで StorageUnavailableException を投げられるようにする（A2）。
 * InMemoryDocumentStore の deleteTree は一括で消すため、途中失敗を再現できない。
 */
public final class PartialDeleteDocumentStore implements DocumentStore {

    private final InMemoryDocumentStore delegate = new InMemoryDocumentStore();
    private final TreeSet<String> paths = new TreeSet<>();
    private int failAfter = Integer.MAX_VALUE;

    /** この回数だけ文書を消したあと、次の1件で止める。回復したら Integer.MAX_VALUE に戻す。 */
    public void failAfterDeletes(int count) {
        this.failAfter = count;
    }

    @Override
    public Optional<Map<String, Object>> get(String documentPath) {
        return delegate.get(documentPath);
    }

    @Override
    public void set(String documentPath, Map<String, Object> fields) {
        delegate.set(documentPath, fields);
        paths.add(documentPath);
    }

    @Override
    public void delete(String documentPath) {
        delegate.delete(documentPath);
        paths.remove(documentPath);
    }

    @Override
    public List<StoredDocument> list(String collectionPath) {
        return delegate.list(collectionPath);
    }

    @Override
    public List<StoredDocument> queryOrderedByField(String collectionPath, String field, boolean descending, int limit) {
        return delegate.queryOrderedByField(collectionPath, field, descending, limit);
    }

    @Override
    public List<StoredDocument> queryFieldEquals(String collectionPath, String field, String value) {
        return delegate.queryFieldEquals(collectionPath, field, value);
    }

    /** Firestore と同じく、下位の文書を 1 件ずつ消す。途中で failAfter 件を超えると、残りを消さずに失敗する。 */
    @Override
    public void deleteTree(String documentPath) {
        String prefix = documentPath + "/";
        List<String> toDelete = new ArrayList<>();
        for (String path : paths) {
            if (path.equals(documentPath) || path.startsWith(prefix)) {
                toDelete.add(path);
            }
        }
        int deletedInThisCall = 0;
        for (String path : toDelete) {
            if (deletedInThisCall >= failAfter) {
                throw new StorageUnavailableException("injected failure after " + failAfter + " deletes");
            }
            delete(path);
            deletedInThisCall++;
        }
    }
}
