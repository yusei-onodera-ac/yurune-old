package app.yurune.service;

import app.yurune.domain.value.AuthenticatedUser;
import app.yurune.repository.UserStorage;

import java.util.function.BiConsumer;

/**
 * 「データを消す」。パスワードをもう一度確かめてから、利用者のデータ（users/{uid} 以下）をすべて消す。
 * エージェントからは呼べない（ツールの許可リストにない）。認証のアカウント自体は、消さない。
 */
public final class DataEraseService {

    private final AuthService authService;
    private final UserStorage userStorage;
    private final BiConsumer<String, Boolean> afterErase;

    /**
     * @param afterErase 消去を試みたあとに、必ず呼ぶ（成功したかどうかを第2引数で渡す）。
     * 失敗したときも、その利用者について持っているメモリ上の状態（まだ書けていない記録の控えなど）を捨てるために呼ぶ。
     * そうしないと、保存先が復旧したときに、消去より前の控えが書き戻ってしまう。
     */
    public DataEraseService(AuthService authService, UserStorage userStorage, BiConsumer<String, Boolean> afterErase) {
        this.authService = authService;
        this.userStorage = userStorage;
        this.afterErase = afterErase;
    }

    /**
     * @throws AuthException パスワードが違う、または試行が多すぎるとき。このとき、何も消さない（afterErase も呼ばない）。
     * @throws StorageUnavailableException 保存先が止まっていて、消去が途中までしか終わらなかったとき。もう一度呼べば、残りを消せる。
     */
    public void erase(AuthenticatedUser user, String password) {
        authService.confirmPassword(user.getEmail(), password);
        boolean erased = false;
        try {
            userStorage.eraseAll(user.getUid());
            erased = true;
        } finally {
            afterErase.accept(user.getUid(), erased);
        }
    }
}
