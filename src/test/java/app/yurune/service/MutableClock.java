package app.yurune.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * テスト用の時計。時間を進められる（夜の経過や、翌朝の確認を再現するため）。
 */
public final class MutableClock extends Clock {

    private final ZoneId zone;
    private Instant now;

    public MutableClock(String isoInstant, String zoneId) {
        this.now = Instant.parse(isoInstant);
        this.zone = ZoneId.of(zoneId);
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
