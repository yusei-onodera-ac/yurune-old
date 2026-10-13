package app.yurune.repository;

import java.time.Clock;

/**
 * 文書ストア（Firestore、またはメモリ）の上に、利用者ごとの保存先を作る。
 * 夜の状態、判断の記録、夜の記録は、保存先が止まっても夜を止めないよう、包んで渡す。
 */
public final class DocumentUserStorage implements UserStorage {

    private final DocumentStore store;
    private final Clock clock;

    public DocumentUserStorage(DocumentStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Override
    public UserRepositories openFor(String uid) {
        UserPaths paths = new UserPaths(uid);
        return new UserRepositories(
                new DocumentArmRepository(store, paths),
                new DocumentSleepRecordRepository(store, paths),
                new ResilientNightRepository(new DocumentNightRepository(store, paths)),
                new ResilientNightSessionRepository(new DocumentNightSessionRepository(store, paths)),
                new ResilientDecisionRepository(new DocumentDecisionRepository(store, paths, clock)),
                new DocumentUserProfileRepository(store, paths),
                new DocumentPdcaStateRepository(store, paths));
    }

    @Override
    public void eraseAll(String uid) {
        store.deleteTree(new UserPaths(uid).userDocument());
    }
}
