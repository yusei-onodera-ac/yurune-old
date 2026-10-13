package app.yurune.storage;

import app.yurune.repository.DocumentStore;
import app.yurune.repository.StorageUnavailableException;
import app.yurune.repository.StoredDocument;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 疑似障害のための文書ストア。止めている間は、すべての呼び出しが、保存先が使えないときと同じ例外になる。
 */
public final class FlakyDocumentStore implements DocumentStore {

    private final DocumentStore delegate;
    private volatile boolean down;
    private volatile int writes;

    public FlakyDocumentStore(DocumentStore delegate) {
        this.delegate = delegate;
    }

    public void setDown(boolean down) {
        this.down = down;
    }

    /** 実際に書けた回数。 */
    public int countWrites() {
        return writes;
    }

    private void failIfDown() {
        if (down) {
            throw new StorageUnavailableException("injected failure");
        }
    }

    @Override
    public Optional<Map<String, Object>> get(String documentPath) {
        failIfDown();
        return delegate.get(documentPath);
    }

    @Override
    public void set(String documentPath, Map<String, Object> fields) {
        failIfDown();
        writes++;
        delegate.set(documentPath, fields);
    }

    @Override
    public void delete(String documentPath) {
        failIfDown();
        delegate.delete(documentPath);
    }

    @Override
    public List<StoredDocument> list(String collectionPath) {
        failIfDown();
        return delegate.list(collectionPath);
    }

    @Override
    public List<StoredDocument> queryOrderedByField(String collectionPath, String field, boolean descending, int limit) {
        failIfDown();
        return delegate.queryOrderedByField(collectionPath, field, descending, limit);
    }

    @Override
    public List<StoredDocument> queryFieldEquals(String collectionPath, String field, String value) {
        failIfDown();
        return delegate.queryFieldEquals(collectionPath, field, value);
    }

    @Override
    public void deleteTree(String documentPath) {
        failIfDown();
        delegate.deleteTree(documentPath);
    }
}
