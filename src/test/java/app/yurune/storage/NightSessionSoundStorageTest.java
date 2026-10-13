package app.yurune.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.constant.NightConstants;
import app.yurune.constant.PlanConstants;
import app.yurune.constant.StorageConstants;
import app.yurune.domain.entity.NightSession;
import app.yurune.domain.value.NightMode;
import app.yurune.domain.value.VolumePercent;
import app.yurune.domain.value.WakeDate;
import app.yurune.repository.DocumentNightSessionRepository;
import app.yurune.repository.InMemoryDocumentStore;
import app.yurune.repository.UserPaths;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 夜の音（いまの曲、フェードの終わる時刻）が、保存先を通っても残ること。
 * 後から足した項目がない古い文書も、既定の値で読めること。
 */
class NightSessionSoundStorageTest {

    private static final Instant STARTED = Instant.parse("2026-10-06T13:10:00Z");
    private static final Instant FADE_ENDS = Instant.parse("2026-10-06T13:25:00Z");

    private final InMemoryDocumentStore store = new InMemoryDocumentStore();
    private final UserPaths paths = new UserPaths("u1");

    @Test
    void theTrackAndTheFadeSurviveAStoreRoundTrip() {
        NightSession session = new NightSession(WakeDate.parse("2026-10-07"), NightMode.SOUND,
                new VolumePercent(40), "wave", STARTED, STARTED, null, false).withFadeOut(FADE_ENDS);

        new DocumentNightSessionRepository(store, paths).saveSession(session);
        NightSession read = new DocumentNightSessionRepository(store, paths).findCurrentSession().orElseThrow();

        assertEquals("wave", read.getSoundKey());
        assertEquals(0, read.getVolume().asInt());
        assertEquals(FADE_ENDS, read.findFadeEndsAt().orElseThrow());
    }

    @Test
    void aSessionWithoutFadeReadsBackWithoutOne() {
        NightSession session = new NightSession(WakeDate.parse("2026-10-07"), NightMode.SOUND,
                new VolumePercent(40), "forest", STARTED, STARTED, null, false);

        new DocumentNightSessionRepository(store, paths).saveSession(session);
        NightSession read = new DocumentNightSessionRepository(store, paths).findCurrentSession().orElseThrow();

        assertEquals("forest", read.getSoundKey());
        assertTrue(read.findFadeEndsAt().isEmpty());
    }

    @Test
    void anOldDocumentWithoutTheSoundFieldsReadsWithTheDefaults() {
        Map<String, Object> oldFields = new LinkedHashMap<>();
        oldFields.put("wakeDate", "2026-10-07");
        oldFields.put("mode", NightMode.SOUND.asId());
        oldFields.put("volumePct", 40L);
        oldFields.put("startedAt", STARTED.toString());
        oldFields.put("lastReactionAt", STARTED.toString());
        oldFields.put("ended", false);
        store.set(paths.document(StorageConstants.SESSIONS, StorageConstants.CURRENT_SESSION_ID), oldFields);

        NightSession read = new DocumentNightSessionRepository(store, paths).findCurrentSession().orElseThrow();

        assertEquals(PlanConstants.DEFAULT_SOUND, read.getSoundKey());
        assertTrue(read.findFadeEndsAt().isEmpty());
        assertEquals(40, read.getVolume().asInt());
        assertEquals(NightConstants.DEFAULT_FADE_MINUTES, read.getFadeMinutes(),
                "FADE の分数の項目も無い古い文書は、標準の分数で読む");
    }

    // ---- PDCA の FADE 要因: fadeMinutes の往復と、古い文書の既定値 ----

    @Test
    void theFadeMinutesSurviveAStoreRoundTrip() {
        NightSession session = new NightSession(WakeDate.parse("2026-10-07"), NightMode.SOUND,
                new VolumePercent(40), "rain", STARTED, STARTED, null, false, 15);

        new DocumentNightSessionRepository(store, paths).saveSession(session);
        NightSession read = new DocumentNightSessionRepository(store, paths).findCurrentSession().orElseThrow();

        assertEquals(15, read.getFadeMinutes());
    }

    @Test
    void anOldDocumentWithoutTheFadeMinutesFieldReadsWithTheDefault() {
        Map<String, Object> oldFields = new LinkedHashMap<>();
        oldFields.put("wakeDate", "2026-10-07");
        oldFields.put("mode", NightMode.SOUND.asId());
        oldFields.put("volumePct", 40L);
        oldFields.put("soundKey", "rain");
        oldFields.put("startedAt", STARTED.toString());
        oldFields.put("lastReactionAt", STARTED.toString());
        oldFields.put("ended", false);
        store.set(paths.document(StorageConstants.SESSIONS, StorageConstants.CURRENT_SESSION_ID), oldFields);

        NightSession read = new DocumentNightSessionRepository(store, paths).findCurrentSession().orElseThrow();

        assertEquals(NightConstants.DEFAULT_FADE_MINUTES, read.getFadeMinutes());
    }
}
