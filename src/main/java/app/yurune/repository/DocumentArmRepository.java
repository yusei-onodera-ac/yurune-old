package app.yurune.repository;

import app.yurune.constant.StorageConstants;
import app.yurune.domain.entity.Arm;
import app.yurune.domain.value.ArmKey;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.OptionName;
import app.yurune.domain.value.TrialCount;
import app.yurune.domain.value.WinCount;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 選択肢ごとの集計を、users/{uid}/arms/{次元__選択肢} に置く。
 */
public final class DocumentArmRepository implements ArmRepository {

    private static final String DIMENSION = "dimension";
    private static final String OPTION = "option";
    private static final String TRIALS = "trials";
    private static final String WINS = "wins";

    private final DocumentStore store;
    private final UserPaths paths;

    public DocumentArmRepository(DocumentStore store, UserPaths paths) {
        this.store = store;
        this.paths = paths;
    }

    @Override
    public Optional<Arm> findArm(ArmKey key) {
        return store.get(paths.document(StorageConstants.ARMS, key.asDocumentId())).map(this::toArm);
    }

    @Override
    public List<Arm> findArmsByDimension(Dimension dimension) {
        return store.queryFieldEquals(paths.collection(StorageConstants.ARMS), DIMENSION, dimension.asText()).stream()
                .map(document -> toArm(document.getFields()))
                .toList();
    }

    @Override
    public void saveArm(Arm arm) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(DIMENSION, arm.getKey().getDimension().asText());
        fields.put(OPTION, arm.getKey().getOptionName().asText());
        fields.put(TRIALS, (long) arm.getTrialCount().asInt());
        fields.put(WINS, (long) arm.getWinCount().asInt());
        store.set(paths.document(StorageConstants.ARMS, arm.getKey().asDocumentId()), fields);
    }

    private Arm toArm(Map<String, Object> fields) {
        ArmKey key = new ArmKey(Dimension.fromText(DocumentFields.text(fields, DIMENSION)),
                new OptionName(DocumentFields.text(fields, OPTION)));
        return new Arm(key, new TrialCount((int) DocumentFields.number(fields, TRIALS)),
                new WinCount((int) DocumentFields.number(fields, WINS)));
    }
}
