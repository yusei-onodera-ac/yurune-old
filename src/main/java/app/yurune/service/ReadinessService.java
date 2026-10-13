package app.yurune.service;

import app.yurune.constant.ObservabilityConstants;
import app.yurune.repository.DocumentStore;
import app.yurune.repository.StorageUnavailableException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * 稼働の準備ができているかを、保存先（Firestore）に読み書きできるかで判断する。
 * LLM の停止や Model Armor の停止は見ない（規則だけで動く縮退運転は、「動いている」とみなす）。
 * 短い間だけ結果を覚え、叩かれても保存先を叩きすぎない。
 */
public final class ReadinessService {

    private static final String PROBE_DOCUMENT_PATH = "system/ready";
    private static final String PROBE_FIELD_CHECKED_AT = "checkedAt";

    private final DocumentStore store;
    private final Clock clock;
    private final AvailabilityTracker availability;
    private Instant checkedAt;
    private boolean ready = true;

    public ReadinessService(DocumentStore store, Clock clock, AvailabilityTracker availability) {
        this.store = store;
        this.clock = clock;
        this.availability = availability;
    }

    public synchronized boolean isReady() {
        Instant now = clock.instant();
        if (checkedAt != null && Duration.between(checkedAt, now).toMillis() < ObservabilityConstants.READY_CACHE_MS) {
            return ready;
        }
        checkedAt = now;
        ready = probe(now);
        return ready;
    }

    private boolean probe(Instant now) {
        try {
            store.set(PROBE_DOCUMENT_PATH, Map.of(PROBE_FIELD_CHECKED_AT, now.toString()));
            store.get(PROBE_DOCUMENT_PATH);
            availability.recordSuccess();
            return true;
        } catch (StorageUnavailableException unavailable) {
            availability.recordFailure();
            return false;
        }
    }
}
