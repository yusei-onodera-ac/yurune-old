package app.yurune.storage;

import app.yurune.repository.DocumentStore;

/** Firestore（エミュレータ）の文書ストアが、メモリ上のものと同じ約束を満たすこと。エミュレータがないときは、飛ばす。 */
class FirestoreEmulatorDocumentStoreTest extends DocumentStoreContract {

    @Override
    protected DocumentStore createStore() throws Exception {
        DocumentStore created = EmulatorSupport.createAdminStore();
        EmulatorSupport.clearFirestore();
        return created;
    }
}
