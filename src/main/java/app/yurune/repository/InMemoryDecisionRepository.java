package app.yurune.repository;

import app.yurune.domain.entity.Decision;
import app.yurune.domain.value.WakeDate;

import java.util.ArrayList;
import java.util.List;

/**
 * メモリ上の保存先。
 */
public final class InMemoryDecisionRepository implements DecisionRepository {

    private final List<Decision> decisions = new ArrayList<>();

    @Override
    public void saveDecision(Decision decision) {
        decisions.add(decision);
    }

    @Override
    public List<Decision> findDecisions(WakeDate nightId) {
        List<Decision> found = new ArrayList<>();
        for (Decision decision : decisions) {
            if (decision.getNightId().equals(nightId)) {
                found.add(decision);
            }
        }
        return found;
    }

    @Override
    public List<Decision> findLatestDecisions() {
        WakeDate latest = null;
        for (Decision decision : decisions) {
            if (latest == null || decision.getNightId().compareTo(latest) > 0) {
                latest = decision.getNightId();
            }
        }
        return latest == null ? List.of() : findDecisions(latest);
    }
}
