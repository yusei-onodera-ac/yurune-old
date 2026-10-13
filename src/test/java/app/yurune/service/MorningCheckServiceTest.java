package app.yurune.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.domain.entity.Arm;
import app.yurune.domain.entity.NightRecord;
import app.yurune.domain.value.ArmKey;
import app.yurune.domain.value.ClockTime;
import app.yurune.domain.value.DecisionPhase;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.EndReason;
import app.yurune.domain.value.Minutes;
import app.yurune.domain.value.MorningAnswer;
import app.yurune.domain.value.MorningMood;
import app.yurune.domain.value.OptionName;
import app.yurune.domain.value.WakeDate;
import app.yurune.repository.ArmRepository;
import app.yurune.repository.DecisionRepository;
import app.yurune.repository.InMemoryArmRepository;
import app.yurune.repository.InMemoryDecisionRepository;
import app.yurune.repository.InMemoryNightRepository;
import app.yurune.repository.InMemorySleepRecordRepository;
import app.yurune.repository.NightRepository;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 朝のひとこと。受け入れ条件 AT-01（出る条件）、AT-02（保存の内容）、AT-12（答えなしは学習しない）を確かめる。
 */
class MorningCheckServiceTest {

    private static final WakeDate THIS_MORNING = WakeDate.parse("2026-10-07");

    private MutableClock clock;
    private ArmRepository armRepository;
    private NightRepository nightRepository;
    private DecisionRepository decisionRepository;
    private RecordService recordService;
    private MorningCheckService service;

    @BeforeEach
    void setUp() {
        // 日本時間の 2026-10-07 06:40（UTC では 10-06 21:40）に、アプリを開く
        clock = new MutableClock("2026-10-06T21:40:00Z", "Asia/Tokyo");
        armRepository = new InMemoryArmRepository();
        nightRepository = new InMemoryNightRepository();
        decisionRepository = new InMemoryDecisionRepository();
        recordService = new RecordService(new InMemorySleepRecordRepository(), clock);
        service = new MorningCheckService(nightRepository, decisionRepository,
                new LearningService(armRepository), recordService, clock);
    }

    private void saveNight(WakeDate wakeDate) {
        nightRepository.saveNight(NightRecord.started(wakeDate, NightFixtures.createPlan("rain", "none", "30")));
    }

    private Optional<Arm> findArm(Dimension dimension, String option) {
        return armRepository.findArm(new ArmKey(dimension, new OptionName(option)));
    }

    @Test
    void sheetAppearsOnlyForANightThatWasUsed() {
        assertTrue(service.findPendingSheet().isEmpty());

        saveNight(THIS_MORNING);

        assertTrue(service.findPendingSheet().isPresent());
    }

    @Test
    void sheetDoesNotAppearBeforeTheMorningOfThatNight() {
        saveNight(THIS_MORNING.plusDays(1));

        assertTrue(service.findPendingSheet().isEmpty());
    }

    @Test
    void sheetDoesNotAppearTwiceForTheSameNight() {
        saveNight(THIS_MORNING);

        service.completeMorning(MorningInput.dismissed(null, null));

        assertTrue(service.findPendingSheet().isEmpty());
    }

    @Test
    void bedTimeIsEstimatedFromTheLastReaction() {
        nightRepository.saveNight(NightRecord.started(THIS_MORNING, NightFixtures.createPlan("rain", "none", "30"))
                .withEnd(EndReason.NO_REACTION, new Minutes(31), ClockTime.parse("22:26"), new Minutes(20)));

        MorningSheet sheet = service.findPendingSheet().orElseThrow();

        assertEquals("22:26", sheet.findBedTime().orElseThrow().asText());
        assertEquals("06:40", sheet.getWakeTime().asText());
    }

    @Test
    void answeredMorningSavesEverythingAndDoesNotMoveTheWins() {
        saveNight(THIS_MORNING);

        service.completeMorning(MorningInput.saved(ClockTime.parse("22:30"), ClockTime.parse("06:35"),
                MorningAnswer.FIT, MorningMood.CALM));

        NightRecord night = nightRepository.findNight(THIS_MORNING).orElseThrow();
        assertTrue(night.isMorningChecked());
        assertEquals(MorningAnswer.FIT, night.findAnswer().orElseThrow());
        assertEquals(MorningMood.CALM, night.findMood().orElseThrow());
        assertEquals("22:30", recordService.findRecord(THIS_MORNING).getBedTime().asText());
        assertTrue(findArm(Dimension.SOUND, "rain").isEmpty(), "朝の答えでは、勝ちも試した回数も増やさない（夜の指標で決める）");
        assertTrue(findArm(Dimension.TALK, "none").isEmpty());
        assertTrue(findArm(Dimension.FADE, "30").isEmpty());
        assertEquals(DecisionPhase.UPDATE, decisionRepository.findDecisions(THIS_MORNING).get(0).getPhase());
    }

    @Test
    void notFitIsKeptForTheNextReviewAndDoesNotCountAsATrial() {
        saveNight(THIS_MORNING);

        service.completeMorning(MorningInput.saved(null, null, MorningAnswer.NOT_FIT, null));

        assertTrue(findArm(Dimension.SOUND, "rain").isEmpty());
        assertEquals(MorningAnswer.NOT_FIT, nightRepository.findNight(THIS_MORNING).orElseThrow().findAnswer().orElseThrow());
        assertTrue(decisionRepository.findDecisions(THIS_MORNING).get(0).getReason().asText().contains("見直し"));
    }

    @Test
    void dismissedMorningSavesOnlyTimesAndDoesNotLearn() {
        saveNight(THIS_MORNING);

        service.completeMorning(MorningInput.dismissed(ClockTime.parse("22:40"), ClockTime.parse("06:40")));

        NightRecord night = nightRepository.findNight(THIS_MORNING).orElseThrow();
        assertTrue(night.isMorningChecked());
        assertTrue(night.findAnswer().isEmpty());
        assertTrue(night.findMood().isEmpty());
        assertEquals("22:40", recordService.findRecord(THIS_MORNING).getBedTime().asText());
        assertTrue(armRepository.findArmsByDimension(Dimension.SOUND).isEmpty());
        assertTrue(decisionRepository.findDecisions(THIS_MORNING).isEmpty());
    }

    @Test
    void sendingTwiceDoesNotCountTwice() {
        saveNight(THIS_MORNING);
        MorningInput input = MorningInput.saved(null, null, MorningAnswer.FIT, null);

        service.completeMorning(input);
        clock.advance(Duration.ofMinutes(1));
        service.completeMorning(input);

        assertEquals(1, decisionRepository.findDecisions(THIS_MORNING).size(), "二度目の送信では、判断を増やさない");
        Optional<NightRecord> night = nightRepository.findNight(THIS_MORNING);
        assertFalse(night.orElseThrow().findAnswer().isEmpty());
    }
}
