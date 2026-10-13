package app.yurune.service;

import app.yurune.domain.value.ClockTime;

import java.util.Objects;
import java.util.Optional;

/**
 * 朝のひとことに出す、推定した時刻。就寝時刻は、記録か、最後に反応があった時刻から。
 * 起床時刻は、記録か、アプリを開いた時刻から。就寝時刻が分からないときは空。
 */
public final class MorningSheet {

    private final ClockTime bedTime;
    private final ClockTime wakeTime;

    public MorningSheet(ClockTime bedTime, ClockTime wakeTime) {
        this.bedTime = bedTime;
        this.wakeTime = Objects.requireNonNull(wakeTime);
    }

    public Optional<ClockTime> findBedTime() {
        return Optional.ofNullable(bedTime);
    }

    public ClockTime getWakeTime() {
        return wakeTime;
    }
}
