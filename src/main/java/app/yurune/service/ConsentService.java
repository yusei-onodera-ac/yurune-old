package app.yurune.service;

import app.yurune.constant.AuthConstants;
import app.yurune.repository.UserRepositories;
import app.yurune.repository.UserStorage;

import java.time.Clock;

/**
 * 登録のとき、18 歳以上であることと、規約・プライバシーへの同意を、利用者ごとの記録に残す（同意した版と時刻だけ）。
 */
public final class ConsentService {

    private final UserStorage userStorage;
    private final Clock clock;

    public ConsentService(UserStorage userStorage, Clock clock) {
        this.userStorage = userStorage;
        this.clock = clock;
    }

    public void recordConsent(String uid) {
        UserRepositories repositories = userStorage.openFor(uid);
        repositories.getUserProfileRepository().saveProfile(
                repositories.getUserProfileRepository().findProfile()
                        .withConsent(AuthConstants.TERMS_VERSION, clock.instant()));
    }
}
