package app.yurune.repository;

import app.yurune.domain.entity.NightRecord;
import app.yurune.domain.value.WakeDate;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * メモリ上の保存先。
 */
public final class InMemoryNightRepository implements NightRepository {

    private final Map<WakeDate, NightRecord> nights = new HashMap<>();

    @Override
    public void saveNight(NightRecord night) {
        nights.put(night.getWakeDate(), night);
    }

    @Override
    public Optional<NightRecord> findNight(WakeDate wakeDate) {
        return Optional.ofNullable(nights.get(wakeDate));
    }

    @Override
    public Optional<NightRecord> findLatestNight() {
        return nights.values().stream().max(Comparator.comparing(NightRecord::getWakeDate));
    }

    @Override
    public List<NightRecord> findRecentNights(int limit) {
        return nights.values().stream()
                .sorted(Comparator.comparing(NightRecord::getWakeDate).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public Optional<NightRecord> findLatestUncheckedNight(WakeDate today) {
        return nights.values().stream()
                .filter(night -> !night.isMorningChecked())
                .filter(night -> night.getWakeDate().compareTo(today) <= 0)
                .max(Comparator.comparing(NightRecord::getWakeDate));
    }
}
