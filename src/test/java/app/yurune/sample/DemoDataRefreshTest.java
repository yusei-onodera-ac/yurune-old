package app.yurune.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.domain.entity.SleepRecord;
import app.yurune.domain.value.ClockTime;
import app.yurune.domain.value.WakeDate;
import app.yurune.repository.DocumentUserStorage;
import app.yurune.repository.InMemoryDocumentStore;
import app.yurune.repository.UserRepositories;
import app.yurune.service.MutableClock;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 審査用アカウントのデモのデータは、審査員が開く日がいつでも、その日の日付で埋まっている。
 */
class DemoDataRefreshTest {

    private final MutableClock clock = new MutableClock("2026-10-13T00:00:00Z", "Asia/Tokyo");
    private UserRepositories repositories;
    private DemoDataSeeder seeder;

    @BeforeEach
    void setUp() {
        repositories = new DocumentUserStorage(new InMemoryDocumentStore(), clock).openFor("uid-demo");
        seeder = new DemoDataSeeder(clock);
    }

    @Test
    void theFirstOpeningFillsTheLastFourteenDaysAndOnePendingNight() {
        seeder.seedIfNeeded(repositories);

        WakeDate today = WakeDate.today(clock);
        assertTrue(repositories.getSleepRecordRepository().findRecord(today).isPresent());
        assertTrue(repositories.getSleepRecordRepository().findRecord(today.minusDays(13)).isPresent());
        assertTrue(repositories.getNightRepository().findNight(today).isPresent());
        assertTrue(repositories.getUserProfileRepository().findProfile().isSeeded());
    }

    @Test
    void openingTwentyDaysLaterFillsTheNewWeekAndThePendingNightOfThatDay() {
        seeder.seedIfNeeded(repositories);
        clock.advance(Duration.ofDays(20));

        seeder.seedIfNeeded(repositories);

        WakeDate later = WakeDate.today(clock);
        assertTrue(repositories.getSleepRecordRepository().findRecord(later).isPresent(), "今日の記録がある");
        assertTrue(repositories.getSleepRecordRepository().findRecord(later.minusDays(7)).isPresent(), "先週の記録がある");
        assertTrue(repositories.getNightRepository().findNight(later).isPresent(), "その日の、答え待ちの 1 晩がある");
    }

    @Test
    void aRecordTheUserAlreadyHasIsNotOverwritten() {
        seeder.seedIfNeeded(repositories);
        WakeDate today = WakeDate.today(clock);
        SleepRecord edited = SleepRecord.recorded(today, ClockTime.parse("22:00"), ClockTime.parse("05:00"));
        repositories.getSleepRecordRepository().saveRecord(edited);

        seeder.seedIfNeeded(repositories);

        SleepRecord kept = repositories.getSleepRecordRepository().findRecord(today).orElseThrow();
        assertEquals(edited.getBedTime().asText(), kept.getBedTime().asText());
        assertEquals(edited.getWakeTime().asText(), kept.getWakeTime().asText());
    }

    @Test
    void theSameDayDoesNotAddAnotherPendingNight() {
        seeder.seedIfNeeded(repositories);
        WakeDate today = WakeDate.today(clock);
        repositories.getNightRepository().saveNight(
                repositories.getNightRepository().findNight(today).orElseThrow().withUserChanged());

        seeder.seedIfNeeded(repositories);

        assertTrue(repositories.getNightRepository().findNight(today).orElseThrow().isUserChanged(), "その日の晩を、置き直さない");
    }
}
