package app.yurune.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.constant.StorageConstants;
import app.yurune.domain.entity.Decision;
import app.yurune.domain.entity.NightPlan;
import app.yurune.domain.entity.NightRecord;
import app.yurune.domain.entity.NightSession;
import app.yurune.domain.value.ClockTime;
import app.yurune.domain.value.DecisionPhase;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.DisplayText;
import app.yurune.domain.value.EndReason;
import app.yurune.domain.value.Minutes;
import app.yurune.domain.value.MorningAnswer;
import app.yurune.domain.value.NightMode;
import app.yurune.domain.value.OptionName;
import app.yurune.domain.value.VolumePercent;
import app.yurune.domain.value.WakeDate;
import app.yurune.repository.DocumentDecisionRepository;
import app.yurune.repository.DocumentNightRepository;
import app.yurune.repository.DocumentNightSessionRepository;
import app.yurune.repository.InMemoryDocumentStore;
import app.yurune.repository.ResilientDecisionRepository;
import app.yurune.repository.ResilientNightRepository;
import app.yurune.repository.ResilientNightSessionRepository;
import app.yurune.repository.StorageUnavailableException;
import app.yurune.repository.UserPaths;
import app.yurune.service.MutableClock;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 保存先（Firestore）が止まっても、夜の状態、判断の記録、夜の終わりを失わないこと（可用性の設計の表の動作）。
 */
class ResilientRepositoryTest {

    private final FlakyDocumentStore store = new FlakyDocumentStore(new InMemoryDocumentStore());
    private final UserPaths paths = new UserPaths("u1");
    private final MutableClock clock = new MutableClock("2026-10-07T01:00:00Z", "Asia/Tokyo");
    private final WakeDate night = WakeDate.parse("2026-10-07");

    private NightSession session(NightMode mode, int volume) {
        return new NightSession(night, mode, new VolumePercent(volume), Instant.parse("2026-10-06T14:00:00Z"), false);
    }

    private Decision decision(int index) {
        return new Decision(night, ClockTime.parse("23:00"), DecisionPhase.ACT, Map.of(), new DisplayText("選んだこと" + index),
                new DisplayText("理由"), true);
    }

    private NightRecord nightRecord() {
        Map<Dimension, OptionName> options = new EnumMap<>(Dimension.class);
        options.put(Dimension.SOUND, new OptionName("rain"));
        options.put(Dimension.TALK, new OptionName("none"));
        options.put(Dimension.FADE, new OptionName("30"));
        options.put(Dimension.DURATION, new OptionName("45"));
        return NightRecord.started(night, new NightPlan(options, EnumSet.noneOf(Dimension.class), new DisplayText("理由"), 1L));
    }

    // ---- 夜の状態 ----

    @Test
    void theNightKeepsRunningWhenTheSessionStoreIsDown() {
        ResilientNightSessionRepository repository = new ResilientNightSessionRepository(
                new DocumentNightSessionRepository(store, paths));
        repository.saveSession(session(NightMode.TALK, 40));

        store.setDown(true);
        repository.saveSession(session(NightMode.SOUND, 30));

        NightSession whileDown = repository.findCurrentSession().orElseThrow();
        assertEquals(NightMode.SOUND, whileDown.getMode(), "止まっている間も、最新の状態で動く");
        assertEquals(30, whileDown.getVolume().asInt());

        store.setDown(false);
        assertEquals(NightMode.SOUND, repository.findCurrentSession().orElseThrow().getMode());
        assertEquals(NightMode.SOUND, new DocumentNightSessionRepository(store, paths).findCurrentSession().orElseThrow().getMode(),
                "戻ったら、書けていなかった最新の状態が、保存先に書かれる");
    }

    @Test
    void aRestartedServerContinuesFromTheStoredSession() {
        new DocumentNightSessionRepository(store, paths).saveSession(session(NightMode.TALK, 40));

        ResilientNightSessionRepository afterRestart = new ResilientNightSessionRepository(
                new DocumentNightSessionRepository(store, paths));

        assertEquals(NightMode.TALK, afterRestart.findCurrentSession().orElseThrow().getMode());
    }

    // ---- 判断の記録 ----

    @Test
    void decisionsWrittenWhileTheStoreIsDownArePreservedInOrder() {
        DocumentDecisionRepository inner = new DocumentDecisionRepository(store, paths, clock);
        ResilientDecisionRepository repository = new ResilientDecisionRepository(inner);

        store.setDown(true);
        repository.saveDecision(decision(1));
        clock.advance(java.time.Duration.ofSeconds(1));
        repository.saveDecision(decision(2));
        assertEquals(2, repository.countPending());

        store.setDown(false);
        clock.advance(java.time.Duration.ofSeconds(1));
        repository.saveDecision(decision(3));

        assertEquals(0, repository.countPending());
        assertEquals(List.of("選んだこと1", "選んだこと2", "選んだこと3"),
                inner.findDecisions(night).stream().map(d -> d.getChosen().asText()).toList());
    }

    @Test
    void pendingDecisionsAreCappedSoMemoryIsNotExhausted() {
        ResilientDecisionRepository repository = new ResilientDecisionRepository(new DocumentDecisionRepository(store, paths, clock));
        store.setDown(true);

        for (int index = 0; index < StorageConstants.PENDING_WRITE_LIMIT + 50; index++) {
            repository.saveDecision(decision(index));
        }

        assertEquals(StorageConstants.PENDING_WRITE_LIMIT, repository.countPending());
    }

    // ---- 夜の記録 ----

    @Test
    void theEndOfTheNightIsNotLostWhenTheStoreIsDown() {
        DocumentNightRepository inner = new DocumentNightRepository(store, paths);
        ResilientNightRepository repository = new ResilientNightRepository(inner);
        repository.saveNight(nightRecord());

        store.setDown(true);
        repository.saveNight(nightRecord().withEnd(EndReason.NO_REACTION, new Minutes(60), ClockTime.parse("01:30"),
                new Minutes(45)));

        assertEquals(1, repository.countPending());
        assertEquals("01:30", repository.findNight(night).orElseThrow().findLastReactionTime().orElseThrow().asText(),
                "まだ書けていない記録のほうを、先に返す");
        assertEquals(night, repository.findLatestNight().orElseThrow().getWakeDate());

        store.setDown(false);
        repository.findNight(night);

        assertEquals(0, repository.countPending());
        assertEquals("01:30", inner.findNight(night).orElseThrow().findLastReactionTime().orElseThrow().asText());
    }

    @Test
    void readsFailLoudlyWhenTheStoreIsDownAndNothingIsPending() {
        ResilientNightRepository repository = new ResilientNightRepository(new DocumentNightRepository(store, paths));
        store.setDown(true);

        assertThrows(StorageUnavailableException.class, () -> repository.findNight(night));
        assertThrows(StorageUnavailableException.class, repository::findLatestNight);
    }

    @Test
    void recentNightsMixTheStoredOnesWithThePendingOnesAndPreferThePendingRecord() {
        DocumentNightRepository inner = new DocumentNightRepository(store, paths);
        ResilientNightRepository repository = new ResilientNightRepository(inner);
        repository.saveNight(nightRecord());
        store.setDown(true);
        repository.saveNight(nightRecord().withMorningResult(MorningAnswer.FIT, null));

        List<NightRecord> whileDown = repository.findRecentNights(7);

        assertEquals(1, whileDown.size());
        assertEquals(MorningAnswer.FIT, whileDown.get(0).findAnswer().orElseThrow(), "まだ書けていない新しい記録のほうを返す");

        store.setDown(false);
        assertEquals(MorningAnswer.FIT, repository.findRecentNights(7).get(0).findAnswer().orElseThrow());
        assertEquals(0, repository.countPending());
    }

    @Test
    void recentNightsFailLoudlyWhenTheStoreIsDownAndNothingIsPending() {
        ResilientNightRepository repository = new ResilientNightRepository(new DocumentNightRepository(store, paths));
        store.setDown(true);

        assertThrows(StorageUnavailableException.class, () -> repository.findRecentNights(7));
    }

    @Test
    void aPendingNightIsStillFoundAsTheUncheckedOneWhileTheStoreIsDown() {
        ResilientNightRepository repository = new ResilientNightRepository(new DocumentNightRepository(store, paths));
        store.setDown(true);
        repository.saveNight(nightRecord());

        assertTrue(repository.findLatestUncheckedNight(night).isPresent());
    }
}
