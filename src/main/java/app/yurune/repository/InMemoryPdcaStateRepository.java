package app.yurune.repository;

import app.yurune.domain.value.PdcaState;

/**
 * メモリ上の見直しの状態。テストと、保存先を使わない計算に使う。
 */
public final class InMemoryPdcaStateRepository implements PdcaStateRepository {

    private PdcaState state = PdcaState.empty();

    @Override
    public PdcaState findState() {
        return state;
    }

    @Override
    public void saveState(PdcaState next) {
        state = next;
    }
}
