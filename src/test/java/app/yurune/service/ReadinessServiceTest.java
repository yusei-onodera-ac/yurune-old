package app.yurune.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.constant.ObservabilityConstants;
import app.yurune.repository.InMemoryDocumentStore;
import app.yurune.storage.FlakyDocumentStore;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 保存先に読み書きできるかで、/ready の判断が決まること。
 * LLM や Model Armor の状態は見ない（ReadinessService は、保存先しか知らない）。
 */
class ReadinessServiceTest {

    private final MutableClock clock = new MutableClock("2026-10-07T01:00:00Z", "Asia/Tokyo");

    @Test
    void readyWhenTheStoreCanBeWrittenToAndReadFrom() {
        ReadinessService service = new ReadinessService(new InMemoryDocumentStore(), clock, new AvailabilityTracker(null));

        assertTrue(service.isReady());
    }

    @Test
    void notReadyWhenTheStoreIsDown() {
        FlakyDocumentStore store = new FlakyDocumentStore(new InMemoryDocumentStore());
        store.setDown(true);
        ReadinessService service = new ReadinessService(store, clock, new AvailabilityTracker(null));

        assertFalse(service.isReady());
    }

    @Test
    void theCachedResultIsReusedWithinTheCacheWindow() {
        FlakyDocumentStore store = new FlakyDocumentStore(new InMemoryDocumentStore());
        ReadinessService service = new ReadinessService(store, clock, new AvailabilityTracker(null));
        assertTrue(service.isReady());

        store.setDown(true);

        assertTrue(service.isReady(), "キャッシュの間は、保存先を叩き直さず、前の結果を使う");
    }

    @Test
    void recoversAfterTheCacheWindowPasses() {
        FlakyDocumentStore store = new FlakyDocumentStore(new InMemoryDocumentStore());
        store.setDown(true);
        ReadinessService service = new ReadinessService(store, clock, new AvailabilityTracker(null));
        assertFalse(service.isReady());

        store.setDown(false);
        clock.advance(Duration.ofMillis(ObservabilityConstants.READY_CACHE_MS + 1));

        assertTrue(service.isReady());
    }

    @Test
    void notifiesTheAvailabilityTrackerOnlyWhenTheStateChanges() {
        FlakyDocumentStore store = new FlakyDocumentStore(new InMemoryDocumentStore());
        List<Boolean> changes = new ArrayList<>();
        ReadinessService service = new ReadinessService(store, clock, new AvailabilityTracker(changes::add));

        service.isReady();
        store.setDown(true);
        clock.advance(Duration.ofMillis(ObservabilityConstants.READY_CACHE_MS + 1));
        service.isReady();

        assertEquals(List.of(true), changes, "最初の成功は、もともと down でないので知らせない。落ちた変化だけ知らせる");
    }
}
