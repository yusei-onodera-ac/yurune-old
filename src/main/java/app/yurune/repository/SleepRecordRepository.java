package app.yurune.repository;

import app.yurune.domain.entity.SleepRecord;
import app.yurune.domain.value.WakeDate;

import java.util.Optional;

/**
 * 睡眠の記録の保存先。
 */
public interface SleepRecordRepository {

    Optional<SleepRecord> findRecord(WakeDate wakeDate);

    void saveRecord(SleepRecord record);
}
