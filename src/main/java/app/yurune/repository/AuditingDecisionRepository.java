package app.yurune.repository;

import app.yurune.domain.entity.Decision;
import app.yurune.domain.value.WakeDate;

import java.util.List;
import java.util.function.Consumer;

/**
 * 判断の記録を保存するたびに、運用の記録（構造化ログ）にも、1 行を残すための包み。保存と読み込みは、そのまま任せる。
 * 運用の記録に何を出すかは、受け取る側が決める（段階と、受け入れたかどうかだけ）。
 */
public final class AuditingDecisionRepository implements DecisionRepository {

    private final DecisionRepository delegate;
    private final Consumer<Decision> onSaved;

    public AuditingDecisionRepository(DecisionRepository delegate, Consumer<Decision> onSaved) {
        this.delegate = delegate;
        this.onSaved = onSaved;
    }

    @Override
    public void saveDecision(Decision decision) {
        delegate.saveDecision(decision);
        onSaved.accept(decision);
    }

    @Override
    public List<Decision> findDecisions(WakeDate nightId) {
        return delegate.findDecisions(nightId);
    }

    @Override
    public List<Decision> findLatestDecisions() {
        return delegate.findLatestDecisions();
    }
}
