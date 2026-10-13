package app.yurune.storage;

import app.yurune.repository.DocumentStore;
import app.yurune.repository.InMemoryDocumentStore;

/** メモリ上の文書ストアが、約束を満たすこと。 */
class InMemoryDocumentStoreTest extends DocumentStoreContract {

    @Override
    protected DocumentStore createStore() {
        return new InMemoryDocumentStore();
    }
}
