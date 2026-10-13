package app.yurune.repository;

import app.yurune.domain.entity.Arm;
import app.yurune.domain.value.ArmKey;
import app.yurune.domain.value.Dimension;

import java.util.List;
import java.util.Optional;

/**
 * 選択肢ごとの集計の保存先。
 * Service は保存先（Firestore など）を知らず、この窓口だけを使う。
 */
public interface ArmRepository {

    Optional<Arm> findArm(ArmKey key);

    List<Arm> findArmsByDimension(Dimension dimension);

    void saveArm(Arm arm);
}
