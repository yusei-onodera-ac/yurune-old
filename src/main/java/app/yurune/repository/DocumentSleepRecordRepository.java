package app.yurune.repository;

import app.yurune.constant.StorageConstants;
import app.yurune.domain.entity.SleepRecord;
import app.yurune.domain.value.ClockTime;
import app.yurune.domain.value.WakeDate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 睡眠の記録を、users/{uid}/sleepRecords/{起きた日} に置く。記録のない日は、時刻の項目が空。
 */
public final class DocumentSleepRecordRepository implements SleepRecordRepository {

    private static final String WAKE_DATE = "wakeDate";
    private static final String BED_TIME = "bedTime";
    private static final String WAKE_TIME = "wakeTime";

    private final DocumentStore store;
    private final UserPaths paths;

    public DocumentSleepRecordRepository(DocumentStore store, UserPaths paths) {
        this.store = store;
        this.paths = paths;
    }

    @Override
    public Optional<SleepRecord> findRecord(WakeDate wakeDate) {
        return store.get(paths.document(StorageConstants.SLEEP_RECORDS, wakeDate.asIsoText())).map(this::toRecord);
    }

    @Override
    public void saveRecord(SleepRecord record) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(WAKE_DATE, record.getWakeDate().asIsoText());
        fields.put(BED_TIME, record.isRecorded() ? record.getBedTime().asText() : null);
        fields.put(WAKE_TIME, record.isRecorded() ? record.getWakeTime().asText() : null);
        store.set(paths.document(StorageConstants.SLEEP_RECORDS, record.getWakeDate().asIsoText()), fields);
    }

    private SleepRecord toRecord(Map<String, Object> fields) {
        WakeDate wakeDate = WakeDate.parse(DocumentFields.text(fields, WAKE_DATE));
        Optional<String> bed = DocumentFields.optionalText(fields, BED_TIME);
        Optional<String> wake = DocumentFields.optionalText(fields, WAKE_TIME);
        if (bed.isEmpty() || wake.isEmpty()) {
            return SleepRecord.unrecorded(wakeDate);
        }
        return SleepRecord.recorded(wakeDate, ClockTime.parse(bed.get()), ClockTime.parse(wake.get()));
    }
}
