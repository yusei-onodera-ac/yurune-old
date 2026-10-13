package app.yurune.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.constant.GentlePolicy;
import app.yurune.domain.value.ClockTime;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.DisplayText;
import app.yurune.domain.value.EndReason;
import app.yurune.domain.value.Minutes;
import app.yurune.domain.value.MorningAnswer;
import app.yurune.domain.value.NightRole;
import app.yurune.domain.value.OptionName;
import app.yurune.domain.value.WakeDate;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 夜の指標の項目（役割、変えた要因、会話で変えた夜、寝つきのめやす、再開の回数、規則の版）の決まり。
 * 値は変えず、終わりは最初の一度だけを残すことも確かめる。
 */
class NightRecordTest {

    private static final WakeDate NIGHT = WakeDate.parse("2026-10-07");
    private static final ClockTime LAST_REACTION = ClockTime.parse("01:15");

    private static NightPlan plan() {
        Map<Dimension, OptionName> options = new EnumMap<>(Dimension.class);
        options.put(Dimension.SOUND, new OptionName("rain"));
        options.put(Dimension.TALK, new OptionName("none"));
        options.put(Dimension.FADE, new OptionName("30"));
        options.put(Dimension.DURATION, new OptionName("45"));
        return new NightPlan(options, EnumSet.noneOf(Dimension.class), new DisplayText("理由"), 1L);
    }

    @Test
    void aNewRecordHasNoRoleAndTheCurrentPolicyVersion() {
        NightRecord night = NightRecord.started(NIGHT, plan());

        assertTrue(night.findRole().isEmpty());
        assertTrue(night.findChangedFactor().isEmpty());
        assertFalse(night.isUserChanged());
        assertTrue(night.findOnsetProxyMin().isEmpty());
        assertEquals(0, night.getRestartCount());
        assertEquals(GentlePolicy.POLICY_VERSION, night.getPolicyVersion());
    }

    @Test
    void theRoleAndTheChangedFactorAreKept() {
        NightRecord night = NightRecord.started(NIGHT, plan(), NightRole.VARIANT, Dimension.FADE);

        assertEquals(NightRole.VARIANT, night.findRole().orElseThrow());
        assertEquals(Dimension.FADE, night.findChangedFactor().orElseThrow());
    }

    @Test
    void onlySoundAndFadeCanBeTheChangedFactor() {
        assertEquals(Dimension.SOUND, NightRecord.started(NIGHT, plan(), NightRole.VARIANT, Dimension.SOUND)
                .findChangedFactor().orElseThrow());
        assertThrows(IllegalArgumentException.class,
                () -> NightRecord.started(NIGHT, plan(), NightRole.VARIANT, Dimension.DURATION));
        assertThrows(IllegalArgumentException.class,
                () -> NightRecord.started(NIGHT, plan(), NightRole.VARIANT, Dimension.TALK));
        assertThrows(IllegalArgumentException.class,
                () -> NightRecord.started(NIGHT, plan(), NightRole.VARIANT, Dimension.NO_REACTION));
    }

    @Test
    void theUserChangedMarkIsSetOnceAndIsKept() {
        NightRecord night = NightRecord.started(NIGHT, plan()).withUserChanged().withUserChanged();

        assertTrue(night.isUserChanged());
        assertEquals("rain", night.getPlan().getOption(Dimension.SOUND).asText());
    }

    @Test
    void restartsAreCountedAndTheFirstNightsPlanAndEndStay() {
        NightRecord night = NightRecord.started(NIGHT, plan())
                .withEnd(EndReason.NO_REACTION, new Minutes(30), LAST_REACTION, new Minutes(20))
                .withMorningResult(MorningAnswer.FIT, null)
                .withRestart()
                .withRestart();

        assertEquals(2, night.getRestartCount());
        assertEquals("rain", night.getPlan().getOption(Dimension.SOUND).asText());
        assertEquals(EndReason.NO_REACTION, night.findEndReason().orElseThrow());
        assertEquals(30, night.findEndedAfter().orElseThrow().asInt());
        assertEquals(20, night.findOnsetProxyMin().orElseThrow().asInt());
        assertEquals(MorningAnswer.FIT, night.findAnswer().orElseThrow());
    }

    @Test
    void theFirstEndIsTheOneKept() {
        NightRecord night = NightRecord.started(NIGHT, plan())
                .withEnd(EndReason.NO_REACTION, new Minutes(30), LAST_REACTION, new Minutes(20))
                .withEnd(EndReason.USER, new Minutes(60), ClockTime.parse("02:00"), null);

        assertEquals(EndReason.NO_REACTION, night.findEndReason().orElseThrow());
        assertEquals(30, night.findEndedAfter().orElseThrow().asInt());
        assertEquals("01:15", night.findLastReactionTime().orElseThrow().asText());
        assertEquals(20, night.findOnsetProxyMin().orElseThrow().asInt());
    }

    @Test
    void aNoReactionEndNeedsTheOnsetProxyAndAUserEndHasNone() {
        NightRecord started = NightRecord.started(NIGHT, plan());

        assertThrows(IllegalArgumentException.class,
                () -> started.withEnd(EndReason.NO_REACTION, new Minutes(30), LAST_REACTION, null));
        assertThrows(IllegalArgumentException.class,
                () -> started.withEnd(EndReason.NO_REACTION, new Minutes(30), LAST_REACTION));
        assertThrows(IllegalArgumentException.class,
                () -> started.withEnd(EndReason.USER, new Minutes(60), LAST_REACTION, new Minutes(20)));

        assertTrue(started.withEnd(EndReason.USER, new Minutes(60), LAST_REACTION).findOnsetProxyMin().isEmpty());
        assertEquals(20, started.withEnd(EndReason.NO_REACTION, new Minutes(30), LAST_REACTION, new Minutes(20))
                .findOnsetProxyMin().orElseThrow().asInt());
    }

    @Test
    void aRestoredRecordMayLackTheOnsetProxyOfAnOldNoReactionEnd() {
        NightRecord restored = NightRecord.restored(NIGHT, plan(), false, null, null, EndReason.NO_REACTION,
                new Minutes(30), LAST_REACTION, null, null, false, null, 0, 0);

        assertEquals(EndReason.NO_REACTION, restored.findEndReason().orElseThrow());
        assertTrue(restored.findOnsetProxyMin().isEmpty());
        assertEquals(0, restored.getPolicyVersion());
    }

    @Test
    void negativeCountsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> NightRecord.restored(NIGHT, plan(), false, null, null,
                null, null, null, null, null, false, null, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> NightRecord.restored(NIGHT, plan(), false, null, null,
                null, null, null, null, null, false, null, 0, -1));
    }
}
