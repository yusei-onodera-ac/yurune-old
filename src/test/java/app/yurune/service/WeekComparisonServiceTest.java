package app.yurune.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.domain.entity.SleepRecord;
import app.yurune.domain.value.ClockTime;
import app.yurune.domain.value.WakeDate;
import app.yurune.repository.InMemorySleepRecordRepository;
import app.yurune.repository.SleepRecordRepository;
import app.yurune.sample.SampleRecordData;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class WeekComparisonServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-10-06T00:00:00Z"), ZoneId.of("Asia/Tokyo"));
    private static final int EXPECTED_THIS_AVERAGE = 434;
    private static final int EXPECTED_PREVIOUS_AVERAGE = 414;
    private static final int EXPECTED_GAIN = 20;

    private RecordService createRecordService(boolean withSample) {
        SleepRecordRepository repository = new InMemorySleepRecordRepository();
        if (withSample) {
            for (SleepRecord record : SampleRecordData.build(WakeDate.today(FIXED_CLOCK))) {
                repository.saveRecord(record);
            }
        }
        return new RecordService(repository, FIXED_CLOCK);
    }

    @Test
    void sampleShowsTwentyMinuteGain() {
        WeekComparison comparison = new WeekComparisonService(createRecordService(true)).compare();

        assertTrue(comparison.isShown());
        assertEquals(EXPECTED_THIS_AVERAGE, comparison.getThisWeekAverage().asInt());
        assertEquals(EXPECTED_PREVIOUS_AVERAGE, comparison.getPreviousWeekAverage().asInt());
        assertEquals(EXPECTED_GAIN, comparison.getGain().asInt());
    }

    @Test
    void comparisonIsHiddenWithoutEnoughRecords() {
        WeekComparison comparison = new WeekComparisonService(createRecordService(false)).compare();

        assertFalse(comparison.isShown());
    }

    @Test
    void durationAcrossMidnightIsCorrect() {
        SleepRecord record = SleepRecord.recorded(
                WakeDate.today(FIXED_CLOCK), ClockTime.parse("23:30"), ClockTime.parse("06:40"));

        assertEquals("7時間10分", record.getDuration().asHourMinuteText());
    }

    @Test
    void futureRecordIsRejected() {
        RecordService recordService = createRecordService(false);
        WakeDate tomorrow = recordService.findToday().plusDays(1);

        assertThrows(IllegalArgumentException.class,
                () -> recordService.saveRecord(tomorrow, ClockTime.parse("23:00"), ClockTime.parse("06:00")));
    }
}
