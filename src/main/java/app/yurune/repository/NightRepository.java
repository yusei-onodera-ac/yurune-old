package app.yurune.repository;

import app.yurune.domain.entity.NightRecord;
import app.yurune.domain.value.WakeDate;

import java.util.List;
import java.util.Optional;

/**
 * 1 晩の記録の保存先。
 */
public interface NightRepository {

    void saveNight(NightRecord night);

    Optional<NightRecord> findNight(WakeDate wakeDate);

    /** 起きた日が最も新しい晩。次の計画で、前の計画として使う。 */
    Optional<NightRecord> findLatestNight();

    /** 起きた日が新しい順に、最大 limit 件。ふりかえりで、直近の晩の履歴を出すために使う。 */
    List<NightRecord> findRecentNights(int limit);

    /** 朝のひとことを、まだ確認していない晩のうち、起きた日が today 以前で最も新しいもの。 */
    Optional<NightRecord> findLatestUncheckedNight(WakeDate today);
}
