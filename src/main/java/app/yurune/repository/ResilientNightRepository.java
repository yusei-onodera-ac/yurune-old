package app.yurune.repository;

import app.yurune.constant.StorageConstants;
import app.yurune.domain.entity.NightRecord;
import app.yurune.domain.value.WakeDate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * 1 晩の記録の保存先が止まっても、夜の終わり（反応がなくなった時刻の保存）を失わないための包み。
 * 書けなかった記録は、起きた日ごとに最新の 1 件を持っておき、次の読み書きで書き直す。
 * 読み込みでは、まだ書けていない記録を、保存先の記録より優先する（新しいのは、こちらだから）。
 * 夜の途中で保存先が止まっても、その夜の記録（計画）を読めるよう、直近に保存・読み込みした記録も、手元に控えておく。
 */
public final class ResilientNightRepository implements NightRepository {

    private static final System.Logger LOGGER = System.getLogger(ResilientNightRepository.class.getName());

    private final NightRepository primary;
    private final Map<WakeDate, NightRecord> pending = new TreeMap<>();
    /** 直近に保存・読み込みした夜の記録。保存先が止まっているときの、読み込みの代わり。 */
    private final Map<WakeDate, NightRecord> recent = new LinkedHashMap<>();
    private boolean reportedDown;

    public ResilientNightRepository(NightRepository primary) {
        this.primary = primary;
    }

    @Override
    public synchronized void saveNight(NightRecord night) {
        pending.put(night.getWakeDate(), night);
        remember(night);
        flushPending();
    }

    private void remember(NightRecord night) {
        recent.remove(night.getWakeDate());
        recent.put(night.getWakeDate(), night);
        while (recent.size() > StorageConstants.RECENT_NIGHT_LIMIT) {
            recent.remove(recent.keySet().iterator().next());
        }
    }

    private void flushPending() {
        for (NightRecord night : new ArrayList<>(pending.values())) {
            try {
                primary.saveNight(night);
                pending.remove(night.getWakeDate());
                reportedDown = false;
            } catch (StorageUnavailableException exception) {
                if (!reportedDown) {
                    LOGGER.log(System.Logger.Level.WARNING, "night store is down; keeping writes in memory");
                    reportedDown = true;
                }
                return;
            }
        }
    }

    @Override
    public synchronized Optional<NightRecord> findNight(WakeDate wakeDate) {
        flushPending();
        NightRecord waiting = pending.get(wakeDate);
        if (waiting != null) {
            return Optional.of(waiting);
        }
        try {
            Optional<NightRecord> stored = primary.findNight(wakeDate);
            stored.ifPresent(this::remember);
            return stored;
        } catch (StorageUnavailableException exception) {
            NightRecord known = recent.get(wakeDate);
            if (known == null) {
                throw exception;
            }
            return Optional.of(known);
        }
    }

    @Override
    public synchronized Optional<NightRecord> findLatestNight() {
        flushPending();
        List<NightRecord> candidates = new ArrayList<>(pending.values());
        findInPrimary(primary::findLatestNight).ifPresent(candidates::add);
        return candidates.stream().max(Comparator.comparing(NightRecord::getWakeDate));
    }

    /** まだ書けていない記録は、保存先の同じ日の記録より新しいので、そちらを優先して混ぜる。 */
    @Override
    public synchronized List<NightRecord> findRecentNights(int limit) {
        flushPending();
        Map<WakeDate, NightRecord> merged = new TreeMap<>();
        try {
            for (NightRecord stored : primary.findRecentNights(limit)) {
                merged.put(stored.getWakeDate(), stored);
            }
        } catch (StorageUnavailableException exception) {
            if (pending.isEmpty()) {
                throw exception;
            }
        }
        merged.putAll(pending);
        return merged.values().stream()
                .sorted(Comparator.comparing(NightRecord::getWakeDate).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized Optional<NightRecord> findLatestUncheckedNight(WakeDate today) {
        flushPending();
        List<NightRecord> candidates = new ArrayList<>(pending.values().stream()
                .filter(night -> !night.isMorningChecked())
                .filter(night -> night.getWakeDate().compareTo(today) <= 0)
                .toList());
        findInPrimary(() -> primary.findLatestUncheckedNight(today))
                .filter(night -> !pending.containsKey(night.getWakeDate()))
                .ifPresent(candidates::add);
        return candidates.stream().max(Comparator.comparing(NightRecord::getWakeDate));
    }

    /** 書けていない記録があるときだけ、保存先が止まっていても、読み込みを続ける。ないときは、失敗をそのまま返す。 */
    private Optional<NightRecord> findInPrimary(Supplier<Optional<NightRecord>> lookup) {
        try {
            return lookup.get();
        } catch (StorageUnavailableException exception) {
            if (pending.isEmpty()) {
                throw exception;
            }
            return Optional.empty();
        }
    }

    public synchronized int countPending() {
        return pending.size();
    }
}
