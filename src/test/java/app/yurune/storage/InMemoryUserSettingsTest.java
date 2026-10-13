package app.yurune.storage;

import app.yurune.repository.DocumentStore;
import app.yurune.repository.InMemoryDocumentStore;

/** メモリ上の文書ストアの上で、設定の保存先の約束を確かめる。 */
class InMemoryUserSettingsTest extends UserSettingsContract {

    @Override
    protected DocumentStore createStore() {
        return new InMemoryDocumentStore();
    }
}
