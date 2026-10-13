package app.yurune.repository;

import app.yurune.domain.entity.Arm;
import app.yurune.domain.value.ArmKey;
import app.yurune.domain.value.Dimension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * メモリ上の保存先。テストと、審査用サンプルの計算に使う。
 */
public final class InMemoryArmRepository implements ArmRepository {

    private final Map<ArmKey, Arm> arms = new LinkedHashMap<>();

    @Override
    public Optional<Arm> findArm(ArmKey key) {
        return Optional.ofNullable(arms.get(key));
    }

    @Override
    public List<Arm> findArmsByDimension(Dimension dimension) {
        List<Arm> found = new ArrayList<>();
        for (Arm arm : arms.values()) {
            if (arm.getKey().getDimension() == dimension) {
                found.add(arm);
            }
        }
        return found;
    }

    @Override
    public void saveArm(Arm arm) {
        arms.put(arm.getKey(), arm);
    }
}
