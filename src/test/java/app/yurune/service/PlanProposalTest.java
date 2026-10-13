package app.yurune.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.domain.entity.NightPlan;
import app.yurune.domain.value.Dimension;
import app.yurune.repository.ArmRepository;
import app.yurune.repository.InMemoryArmRepository;
import app.yurune.repository.InMemoryNightRepository;
import app.yurune.repository.InMemoryPdcaStateRepository;
import app.yurune.sample.SampleNightData;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class PlanProposalTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-10-06T00:00:00Z"), ZoneId.of("Asia/Tokyo"));

    private PlanService createPlanService() {
        ArmRepository armRepository = new InMemoryArmRepository();
        new SampleReplayService(new LearningService(armRepository)).replayNights(SampleNightData.USER_A);
        return new PlanService(new InMemoryNightRepository(), new ThompsonOptionSelector(armRepository, new BetaSampler()),
                new InMemoryPdcaStateRepository(), FIXED_CLOCK);
    }

    @Test
    void samePlanOnTheSameDay() {
        PlanService planService = createPlanService();

        NightPlan first = planService.proposeTonightPlan();
        NightPlan second = planService.proposeTonightPlan();

        assertEquals(first.getOption(Dimension.SOUND), second.getOption(Dimension.SOUND));
        assertEquals(first.getOption(Dimension.FADE), second.getOption(Dimension.FADE));
        assertEquals(first.getSeed(), second.getSeed());
    }

    @Test
    void planChangesAtMostOneDimensionAndExplainsWhy() {
        NightPlan plan = createPlanService().proposeTonightPlan();

        // 本物の制約: NightRecord の changedFactor は、Set でなく単一の Dimension（null 許容）なので、1 を超えられない。
        assertTrue(plan.countChangedDimensions() <= 1);
        assertFalse(plan.getReason().asText().isBlank());
    }
}
