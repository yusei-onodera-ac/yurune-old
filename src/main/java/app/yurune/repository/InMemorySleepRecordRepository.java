package app.yurune.repository;

import app.yurune.domain.entity.SleepRecord;
import app.yurune.domain.value.WakeDate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * メモリ上の保存先。
 */
public final class InMemorySleepRecordRepository implements SleepRecordRepository {

    private final Map<WakeDate, SleepRecord> records = new HashMap<>();

    @Override
    public Optional<SleepRecord> findRecord(WakeDate wakeDate) {
        return Optional.ofNullable(records.get(wakeDate));
    }

    @Override
    public void saveRecord(SleepRecord record) {
        records.put(record.getWakeDate(), record);
    }
}
