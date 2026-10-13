package app.yurune.repository;

import app.yurune.constant.StorageConstants;
import app.yurune.domain.entity.Decision;
import app.yurune.domain.value.WakeDate;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * 判断の記録の保存先が止まっても、夜の制御ループを止めないための包み。
 * 書けなかった記録は、一定の件数まで持っておき、次の書き込みで、古いものから書き直す。
 * 上限を超えたときは、古いものから捨てる（メモリを使い切らないため）。読み込みは、保存先に任せる。
 */
public final class ResilientDecisionRepository implements DecisionRepository {

    private static final System.Logger LOGGER = System.getLogger(ResilientDecisionRepository.class.getName());

    private final DecisionRepository primary;
    private final Deque<Decision> pending = new ArrayDeque<>();
    private boolean reportedDown;

    public ResilientDecisionRepository(DecisionRepository primary) {
        this.primary = primary;
    }

    @Override
    public synchronized void saveDecision(Decision decision) {
        pending.addLast(decision);
        while (pending.size() > StorageConstants.PENDING_WRITE_LIMIT) {
            pending.removeFirst();
        }
        flushPending();
    }

    /** 先頭から順に書き、失敗したところで止める（記録の順番を保つため）。 */
    private void flushPending() {
        while (!pending.isEmpty()) {
            try {
                primary.saveDecision(pending.peekFirst());
                pending.removeFirst();
                reportedDown = false;
            } catch (StorageUnavailableException exception) {
                if (!reportedDown) {
                    LOGGER.log(System.Logger.Level.WARNING, "decision log store is down; keeping writes in memory");
                    reportedDown = true;
                }
                return;
            }
        }
    }

    @Override
    public synchronized List<Decision> findDecisions(WakeDate nightId) {
        flushPending();
        return primary.findDecisions(nightId);
    }

    @Override
    public synchronized List<Decision> findLatestDecisions() {
        flushPending();
        return primary.findLatestDecisions();
    }

    /** まだ書けていない件数（テストと、運用の確認用）。 */
    public synchronized int countPending() {
        return pending.size();
    }
}
