package app.yurune.storage;

import app.yurune.repository.DocumentStore;

/** Firestore（エミュレータ）の上で、保存先の約束を確かめる。エミュレータがないときは、飛ばす。 */
class FirestoreEmulatorRepositoryTest extends RepositoryContract {

    @Override
    protected DocumentStore createStore() throws Exception {
        DocumentStore created = EmulatorSupport.createAdminStore();
        EmulatorSupport.clearFirestore();
        return created;
    }
}
