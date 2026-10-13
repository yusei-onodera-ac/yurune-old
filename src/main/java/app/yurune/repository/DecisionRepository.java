package app.yurune.repository;

import app.yurune.domain.entity.Decision;
import app.yurune.domain.value.WakeDate;

import java.util.List;

/**
 * 判断の記録の保存先。追加するだけで、書き換えない。
 */
public interface DecisionRepository {

    void saveDecision(Decision decision);

    /** その晩の判断を、記録した順に返す。 */
    List<Decision> findDecisions(WakeDate nightId);

    /** 判断が 1 件でもある晩のうち、起きた日が最も新しいもの。 */
    List<Decision> findLatestDecisions();
}
