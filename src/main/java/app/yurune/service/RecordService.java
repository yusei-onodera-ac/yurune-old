package app.yurune.service;

import app.yurune.domain.entity.SleepRecord;
import app.yurune.domain.value.ClockTime;
import app.yurune.domain.value.WakeDate;
import app.yurune.repository.SleepRecordRepository;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 睡眠の記録を、日ごとにそろえて渡す。記録のない日も、空の記録として並べる。
 */
public final class RecordService {

    private final SleepRecordRepository sleepRecordRepository;
    private final Clock clock;

    public RecordService(SleepRecordRepository sleepRecordRepository, Clock clock) {
        this.sleepRecordRepository = sleepRecordRepository;
        this.clock = clock;
    }

    public WakeDate findToday() {
        return WakeDate.today(clock);
    }

    public SleepRecord findLastNight() {
        WakeDate today = findToday();
        return sleepRecordRepository.findRecord(today).orElseGet(() -> SleepRecord.unrecorded(today));
    }

    /** その日の記録。ないときは、空の記録を返す。 */
    public SleepRecord findRecord(WakeDate wakeDate) {
        return sleepRecordRepository.findRecord(wakeDate).orElseGet(() -> SleepRecord.unrecorded(wakeDate));
    }

    /** from から to まで（両端を含む）を、古い日から順に返す。 */
    public List<SleepRecord> findDailyRecords(WakeDate from, WakeDate to) {
        List<SleepRecord> records = new ArrayList<>();
        int dayCount = from.daysUntil(to) + 1;
        for (int offset = 0; offset < dayCount; offset++) {
            WakeDate wakeDate = from.plusDays(offset);
            records.add(sleepRecordRepository.findRecord(wakeDate)
                    .orElseGet(() -> SleepRecord.unrecorded(wakeDate)));
        }
        return records;
    }

    /** 今日から数えて dayCount 日分を、新しい日から順に返す。 */
    public List<SleepRecord> findRecentRecordsNewestFirst(int dayCount) {
        WakeDate today = findToday();
        List<SleepRecord> records = new ArrayList<>(findDailyRecords(today.minusDays(dayCount - 1), today));
        Collections.reverse(records);
        return records;
    }

    /** 未来の日の記録は受け付けない（まだ起きていない日の時刻は、存在しないため）。 */
    public void saveRecord(WakeDate wakeDate, ClockTime bedTime, ClockTime wakeTime) {
        if (findToday().daysUntil(wakeDate) > 0) {
            throw new IllegalArgumentException("future date: " + wakeDate.asIsoText());
        }
        sleepRecordRepository.saveRecord(SleepRecord.recorded(wakeDate, bedTime, wakeTime));
    }
}
