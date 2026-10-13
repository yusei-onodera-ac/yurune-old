package app.yurune.repository;

import app.yurune.domain.value.PdcaState;

/**
 * 見直しの状態（外しているもの、見送り、よく選ぶもの、戻しの保留、受診のすすめ）の保存先。
 * 1 人の利用者につき 1 つ。読めないときは、空の状態で始める。
 */
public interface PdcaStateRepository {

    PdcaState findState();

    void saveState(PdcaState state);
}
