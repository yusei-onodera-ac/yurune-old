package app.yurune.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.domain.entity.NightPlan;
import app.yurune.domain.entity.NightRecord;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.UserSettings;
import app.yurune.domain.value.WakeDate;
import app.yurune.repository.InMemoryArmRepository;
import app.yurune.repository.InMemoryNightRepository;
import app.yurune.repository.InMemoryPdcaStateRepository;
import app.yurune.repository.NightRepository;
import app.yurune.repository.StorageUnavailableException;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * 保存先が不調でも、夜を始めるための計画は作れること（前の夜の記録が読めないときは、変えずに標準のやり方にする）。
 */
class PlanServiceOutageTest {

    /** 過去の夜の読み出しだけが止まっている保存先。 */
    private static final class OutageOnRecentNights implements NightRepository {

        private final InMemoryNightRepository inner = new InMemoryNightRepository();

        @Override
        public void saveNight(NightRecord night) {
            inner.saveNight(night);
        }

        @Override
        public Optional<NightRecord> findNight(WakeDate wakeDate) {
            return inner.findNight(wakeDate);
        }

        @Override
        public Optional<NightRecord> findLatestNight() {
            return inner.findLatestNight();
        }

        @Override
        public List<NightRecord> findRecentNights(int limit) {
            throw new StorageUnavailableException("down");
        }

        @Override
        public Optional<NightRecord> findLatestUncheckedNight(WakeDate today) {
            return inner.findLatestUncheckedNight(today);
        }
    }

    @Test
    void thePlanIsMadeAsTheStandardPlanWhenTheHistoryCannotBeRead() {
        PlanService service = new PlanService(new OutageOnRecentNights(),
                new ThompsonOptionSelector(new InMemoryArmRepository(), new BetaSampler()),
                new InMemoryPdcaStateRepository(), new MutableClock("2026-10-06T13:10:00Z", "Asia/Tokyo"),
                UserSettings::defaults);

        NightPlan plan = service.proposeTonightPlan();

        assertEquals(0, plan.countChangedDimensions(), "読めないときは、変えない");
        assertEquals("rain", plan.getOption(Dimension.SOUND).asText());
        assertTrue(plan.findRole().isEmpty());
        assertTrue(plan.getReason().asText().contains("記録が、今は読めない"), plan.getReason().asText());
    }
}
