package app.yurune.sample;

import app.yurune.domain.entity.SleepRecord;
import app.yurune.domain.value.ClockTime;
import app.yurune.domain.value.WakeDate;

import java.util.ArrayList;
import java.util.List;

/**
 * 審査用サンプルの睡眠記録（直近 14 日）。日付は「今日」からの日数で決める。
 * 今週の平均は、先週より約 20 分長くなるように置いた。画面には「サンプル」と明記する。
 * 並びは、今日から何日前に起きたか、就寝時刻、起床時刻。空欄は未記録の日。
 */
public final class SampleRecordData {

    private static final String[][] ROWS = {
        {"0", "23:42", "06:22"},
        {"1", "23:10", "06:30"},
        {"2", "00:20", "08:10"},
        {"3", "23:50", "07:30"},
        {"4", "", ""},
        {"5", "00:05", "06:50"},
        {"6", "23:30", "06:40"},
        {"7", "23:45", "06:15"},
        {"8", "23:30", "06:30"},
        {"9", "00:10", "07:00"},
        {"10", "23:20", "06:30"},
        {"11", "23:40", "07:00"},
        {"12", "", ""},
        {"13", "00:30", "07:04"},
    };

    public static List<SleepRecord> build(WakeDate today) {
        List<SleepRecord> records = new ArrayList<>();
        for (String[] row : ROWS) {
            WakeDate wakeDate = today.minusDays(Integer.parseInt(row[0]));
            if (!row[1].isEmpty()) {
                records.add(SleepRecord.recorded(wakeDate, ClockTime.parse(row[1]), ClockTime.parse(row[2])));
            }
        }
        return records;
    }

    private SampleRecordData() {
    }
}
