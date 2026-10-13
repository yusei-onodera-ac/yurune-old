package app.yurune.repository;

import app.yurune.constant.StorageConstants;
import app.yurune.domain.entity.Decision;
import app.yurune.domain.value.ClockTime;
import app.yurune.domain.value.DecisionPhase;
import app.yurune.domain.value.DisplayText;
import app.yurune.domain.value.WakeDate;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 判断の記録（見たこと、選んだこと、理由）を、users/{uid}/decisions/{夜}_{通し番号} に追加する。書き換えない。
 * 本文は持たない。文書の名前は、記録した順に並ぶようにしてある。
 */
public final class DocumentDecisionRepository implements DecisionRepository {

    private static final String NIGHT_ID = "nightId";
    private static final String AT = "at";
    private static final String PHASE = "phase";
    private static final String OBSERVED = "observed";
    private static final String CHOSEN = "chosen";
    private static final String REASON = "reason";
    private static final String ACCEPTED = "accepted";
    private static final String ID_FORMAT = "%s_%013d_%04d";
    private static final int SEQUENCE_WRAP = 10_000;

    private final DocumentStore store;
    private final UserPaths paths;
    private final Clock clock;
    private final AtomicLong sequence = new AtomicLong();

    public DocumentDecisionRepository(DocumentStore store, UserPaths paths, Clock clock) {
        this.store = store;
        this.paths = paths;
        this.clock = clock;
    }

    @Override
    public void saveDecision(Decision decision) {
        Map<String, Object> observed = new LinkedHashMap<>(decision.getObserved());
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(NIGHT_ID, decision.getNightId().asIsoText());
        fields.put(AT, decision.getAt().asText());
        fields.put(PHASE, decision.getPhase().asId());
        fields.put(OBSERVED, observed);
        fields.put(CHOSEN, decision.getChosen().asText());
        fields.put(REASON, decision.getReason().asText());
        fields.put(ACCEPTED, decision.isAccepted());
        String id = String.format(ID_FORMAT, decision.getNightId().asIsoText(), clock.millis(),
                sequence.getAndIncrement() % SEQUENCE_WRAP);
        store.set(paths.document(StorageConstants.DECISIONS, id), fields);
    }

    @Override
    public List<Decision> findDecisions(WakeDate nightId) {
        return store.queryFieldEquals(paths.collection(StorageConstants.DECISIONS), NIGHT_ID, nightId.asIsoText()).stream()
                .map(document -> toDecision(document.getFields()))
                .toList();
    }

    @Override
    public List<Decision> findLatestDecisions() {
        return store.queryOrderedByField(paths.collection(StorageConstants.DECISIONS), NIGHT_ID, true, 1).stream()
                .findFirst()
                .map(document -> findDecisions(WakeDate.parse(DocumentFields.text(document.getFields(), NIGHT_ID))))
                .orElse(List.of());
    }

    private Decision toDecision(Map<String, Object> fields) {
        Map<String, String> observed = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : DocumentFields.map(fields, OBSERVED).entrySet()) {
            observed.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return new Decision(WakeDate.parse(DocumentFields.text(fields, NIGHT_ID)),
                ClockTime.parse(DocumentFields.text(fields, AT)), phaseOf(DocumentFields.text(fields, PHASE)), observed,
                new DisplayText(DocumentFields.text(fields, CHOSEN)), new DisplayText(DocumentFields.text(fields, REASON)),
                DocumentFields.flag(fields, ACCEPTED));
    }

    private DecisionPhase phaseOf(String id) {
        for (DecisionPhase phase : DecisionPhase.values()) {
            if (phase.asId().equals(id)) {
                return phase;
            }
        }
        throw new IllegalStateException("unknown phase: " + id);
    }
}
