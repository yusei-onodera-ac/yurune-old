package app.yurune.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.yurune.domain.entity.NightSession;
import app.yurune.domain.value.NightMode;
import app.yurune.domain.value.VolumePercent;
import app.yurune.domain.value.WakeDate;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * 端末が鳴らす音の状態。フェードの残りは、いまの時刻から数え、0 より少なくならない。
 */
class NightSoundStateTest {

    private static final Instant NOW = Instant.parse("2026-10-06T13:10:00Z");

    private static NightSession sessionAt(int volumePct) {
        return new NightSession(WakeDate.parse("2026-10-07"), NightMode.SOUND, new VolumePercent(volumePct),
                "rain", NOW, NOW, null, false);
    }

    @Test
    void aSessionWithoutFadeReportsNoRemainingFade() {
        NightSoundState state = NightSoundState.of(sessionAt(40), NOW);

        assertEquals("rain", state.getSoundKey());
        assertEquals(40, state.getVolumePct());
        assertEquals(0, state.getFadeRemainingSec());
    }

    @Test
    void theRemainingFadeIsCountedInWholeSecondsAndNeverNegative() {
        NightSession fading = sessionAt(40).withFadeOut(NOW.plusSeconds(90));

        assertEquals(0, NightSoundState.of(fading, NOW).getVolumePct());
        assertEquals(90, NightSoundState.of(fading, NOW).getFadeRemainingSec());
        assertEquals(0, NightSoundState.of(fading, NOW.plusSeconds(120)).getFadeRemainingSec());
    }

    @Test
    void aNegativeRemainingFadeIsTreatedAsNone() {
        assertEquals(0, new NightSoundState("rain", 0, -5).getFadeRemainingSec());
    }
}
