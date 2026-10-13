package app.yurune.service;

import app.yurune.constant.TimeConstants;
import app.yurune.constant.WeekConstants;
import app.yurune.domain.entity.SleepRecord;
import app.yurune.domain.value.SleepMinutes;
import app.yurune.domain.value.WakeDate;

import java.util.ArrayList;
import java.util.List;

/**
 * 直近 7 日と、その前の 7 日を比べる。
 * 今回は「平均睡眠時間が長い」条件だけを実装している（時刻のばらつきの条件は未実装）。
 */
public final class WeekComparisonService {

    private final RecordService recordService;

    public WeekComparisonService(RecordService recordService) {
        this.recordService = recordService;
    }

    public WeekComparison compare() {
        WakeDate today = recordService.findToday();
        int lastOffset = TimeConstants.DAYS_PER_WEEK - 1;
        List<SleepRecord> thisWeek = recordService.findDailyRecords(today.minusDays(lastOffset), today);
        List<SleepRecord> previousWeek = recordService.findDailyRecords(
                today.minusDays(lastOffset + TimeConstants.DAYS_PER_WEEK),
                today.minusDays(TimeConstants.DAYS_PER_WEEK));

        SleepMinutes thisAverage = averageOf(thisWeek);
        SleepMinutes previousAverage = averageOf(previousWeek);
        boolean enoughRecords = countRecorded(thisWeek) >= WeekConstants.MIN_RECORDED_DAYS_PER_PERIOD
                && countRecorded(previousWeek) >= WeekConstants.MIN_RECORDED_DAYS_PER_PERIOD;
        boolean longerEnough = thisAverage.asInt() - previousAverage.asInt()
                >= WeekConstants.MIN_AVERAGE_GAIN_MINUTES;

        return new WeekComparison(enoughRecords && longerEnough, thisAverage, previousAverage,
                pairByWeekday(thisWeek, previousWeek));
    }

    private List<WeekdayPair> pairByWeekday(List<SleepRecord> thisWeek, List<SleepRecord> previousWeek) {
        List<WeekdayPair> pairs = new ArrayList<>();
        for (int index = 0; index < thisWeek.size(); index++) {
            pairs.add(new WeekdayPair(thisWeek.get(index), previousWeek.get(index)));
        }
        return pairs;
    }

    private int countRecorded(List<SleepRecord> records) {
        int count = 0;
        for (SleepRecord record : records) {
            if (record.isRecorded()) {
                count++;
            }
        }
        return count;
    }

    /** 記録のある日だけで平均を出す。記録が 1 日もないときは 0 分。 */
    private SleepMinutes averageOf(List<SleepRecord> records) {
        int count = countRecorded(records);
        if (count == 0) {
            return new SleepMinutes(0);
        }
        int total = 0;
        for (SleepRecord record : records) {
            if (record.isRecorded()) {
                total += record.getDuration().asInt();
            }
        }
        return new SleepMinutes(Math.round((float) total / count));
    }
}
