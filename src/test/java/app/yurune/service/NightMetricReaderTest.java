package app.yurune.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.domain.entity.NightRecord;
import app.yurune.domain.value.ArmKey;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.MorningAnswer;
import app.yurune.domain.value.MorningMood;
import app.yurune.domain.value.NightGoal;
import app.yurune.domain.value.NightRole;
import app.yurune.domain.value.OptionName;

import org.junit.jupiter.api.Test;

/**
 * 夜の記録から読むめやすの値。欠測は空、利用者が変えた夜は比べない、気分の点は画面に出さない仮置き。
 */
class NightMetricReaderTest {

    private static final double DELTA = 1e-9;

    @Test
    void onsetIsOnlyPresentForANightEndedWithoutReaction() {
        NightRecord endedByUser = ReviewFixtures.night(ReviewFixtures.day(1), NightRole.CONTROL, Dimension.SOUND, "rain",
                null, null);
        NightRecord endedWithoutReaction = ReviewFixtures.standard(ReviewFixtures.day(2), NightRole.CONTROL, 40, null);

        assertTrue(NightMetricReader.valueFor(NightGoal.ONSET, endedByUser).isEmpty(), "利用者が終えた夜は、欠測");
        assertEquals(40.0, NightMetricReader.valueFor(NightGoal.ONSET, endedWithoutReaction).orElseThrow(), DELTA);
    }

    @Test
    void theWakeGoalIsAlwaysPresentAndCountsRestarts() {
        NightRecord night = ReviewFixtures.standard(ReviewFixtures.day(3), NightRole.CONTROL, null, null);

        assertEquals(0.0, NightMetricReader.valueFor(NightGoal.WAKE, night).orElseThrow(), DELTA);
        assertEquals(1.0, NightMetricReader.valueFor(NightGoal.WAKE, night.withRestart()).orElseThrow(), DELTA);
    }

    @Test
    void theMorningGoalIsAPointFromTheMoodOnlyAfterTheAnswerIsChecked() {
        NightRecord notChecked = ReviewFixtures.standard(ReviewFixtures.day(4), NightRole.CONTROL, 40, null);
        NightRecord calm = notChecked.withMorningResult(MorningAnswer.FIT, MorningMood.CALM);
        NightRecord heavy = notChecked.withMorningResult(MorningAnswer.NOT_FIT, MorningMood.HEAVY);

        assertTrue(NightMetricReader.valueFor(NightGoal.MORNING, notChecked).isEmpty());
        assertEquals(2.0, NightMetricReader.valueFor(NightGoal.MORNING, calm).orElseThrow(), DELTA);
        assertEquals(0.0, NightMetricReader.valueFor(NightGoal.MORNING, heavy).orElseThrow(), DELTA);
    }

    @Test
    void onlyOnsetAndWakeAreLowerIsBetterAndTheMorningIsHigherIsBetter() {
        assertTrue(NightMetricReader.lowerIsBetter(NightGoal.ONSET));
        assertTrue(NightMetricReader.lowerIsBetter(NightGoal.WAKE));
        assertFalse(NightMetricReader.lowerIsBetter(NightGoal.MORNING));
        assertEquals(-40.0, NightMetricReader.goodnessOf(NightGoal.ONSET, 40.0), DELTA);
        assertEquals(2.0, NightMetricReader.goodnessOf(NightGoal.MORNING, 2.0), DELTA);
    }

    @Test
    void aNightChangedByTheUserIsNotValidForComparison() {
        NightRecord night = ReviewFixtures.standard(ReviewFixtures.day(5), NightRole.CONTROL, 40, null);

        assertTrue(NightMetricReader.isValidFor(NightGoal.ONSET, night));
        assertFalse(NightMetricReader.isValidFor(NightGoal.ONSET, night.withUserChanged()));
    }

    @Test
    void theVariantKeyIsTheChangedFactorAndItsOption() {
        NightRecord variant = ReviewFixtures.soundVariant(ReviewFixtures.day(6), "wave", 10, null);

        assertEquals(new ArmKey(Dimension.SOUND, new OptionName("wave")),
                NightMetricReader.findVariantKey(variant).orElseThrow());
        assertTrue(NightMetricReader.findVariantKey(ReviewFixtures.standard(ReviewFixtures.day(7), NightRole.CONTROL, 40, null))
                .isEmpty());
    }
}
