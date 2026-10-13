package app.yurune.domain.value;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 見直しの状態。外すと、見送りと、よく選ぶの印は消える。日付は起きた日の、含む範囲で判定する。
 */
class PdcaStateTest {

    private static final ArmKey WAVE = new ArmKey(Dimension.SOUND, new OptionName("wave"));
    private static final WakeDate DAY = WakeDate.parse("2026-10-10");

    @Test
    void quarantineIncludesItsLastDayAndEndsTheDayAfter() {
        PdcaState state = PdcaState.empty().withQuarantine(WAVE, DAY);

        assertTrue(state.isQuarantined(WAVE, DAY));
        assertFalse(state.isQuarantined(WAVE, DAY.plusDays(1)));
        assertFalse(state.isQuarantined(new ArmKey(Dimension.SOUND, new OptionName("forest")), DAY));
    }

    @Test
    void quarantineClearsTheParkAndThePreference() {
        PdcaState state = PdcaState.empty().withPreferred(WAVE).withQuarantine(WAVE, DAY);

        assertFalse(state.isPreferred(WAVE));
        assertFalse(state.isParked(WAVE));
    }

    @Test
    void parkingAndPreferringReplaceEachOther() {
        PdcaState parked = PdcaState.empty().withPreferred(WAVE).withParked(WAVE, DAY);
        assertTrue(parked.isParked(WAVE));
        assertFalse(parked.isPreferred(WAVE));

        PdcaState preferred = parked.withPreferred(WAVE);
        assertTrue(preferred.isPreferred(WAVE));
        assertFalse(preferred.isParked(WAVE));
    }

    @Test
    void parkRetryIsDueOnlyFromTheRecordedDayOnward() {
        PdcaState state = PdcaState.empty().withParked(WAVE, DAY);

        assertFalse(state.isParkRetryDue(WAVE, DAY.minusDays(1)));
        assertTrue(state.isParkRetryDue(WAVE, DAY));
        assertTrue(state.isParkRetryDue(WAVE, DAY.plusDays(1)));
        assertFalse(state.isParkRetryDue(new ArmKey(Dimension.SOUND, new OptionName("forest")), DAY));
    }

    @Test
    void quarantineClearsASubjectiveDropMarkWhenReplacedByEvidence() {
        PdcaState soft = PdcaState.empty().withSubjectiveQuarantine(WAVE, DAY);
        assertTrue(soft.isSubjectivelyDropped(WAVE));

        PdcaState hard = soft.withQuarantine(WAVE, DAY.plusDays(1));
        assertFalse(hard.isSubjectivelyDropped(WAVE), "統計的な根拠による隔離に置き換わったら、朝の答えだけの印は消える");
        assertTrue(hard.isQuarantined(WAVE, DAY.plusDays(1)));
    }

    @Test
    void theHoldIncludesItsLastNight() {
        PdcaState state = PdcaState.empty().withRollback(WAVE, DAY);

        assertTrue(state.isHolding(DAY));
        assertFalse(state.isHolding(DAY.plusDays(1)));
        assertFalse(PdcaState.empty().isHolding(DAY));
    }

    @Test
    void stateIsNeverChangedInPlace() {
        PdcaState empty = PdcaState.empty();

        empty.withQuarantine(WAVE, DAY);

        assertFalse(empty.isQuarantined(WAVE, DAY));
    }

    @Test
    void theReferralDayRoundTripsAndStaysUntilHidden() {
        PdcaState state = PdcaState.empty().withReferral(DAY);

        assertEquals(DAY, state.findReferredOn().orElseThrow());
        assertTrue(state.findReferHiddenUntil().isEmpty(), "まだ「今は表示しない」を押していない");
    }

    @Test
    void hidingTheReferralKeepsTheReferredDayAndOtherState() {
        PdcaState state = PdcaState.empty().withPreferred(WAVE).withReferral(DAY).withReferHiddenUntil(DAY.plusDays(30));

        assertEquals(DAY.plusDays(30), state.findReferHiddenUntil().orElseThrow());
        assertEquals(DAY, state.findReferredOn().orElseThrow(), "「今は表示しない」は、受診のすすめた日を消さない");
        assertTrue(state.isPreferred(WAVE), "「今は表示しない」は、ほかの状態を変えない");
    }

    @Test
    void hidingTheReferralIsNeverChangedInPlace() {
        PdcaState state = PdcaState.empty().withReferral(DAY);

        state.withReferHiddenUntil(DAY.plusDays(30));

        assertTrue(state.findReferHiddenUntil().isEmpty());
    }
}
