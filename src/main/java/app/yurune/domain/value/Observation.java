package app.yurune.domain.value;

import app.yurune.constant.NightConstants;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 夜の観測（端末とサーバーが見た、数値と状態だけ）。会話の本文は含めない。
 */
public final class Observation {

    private final int elapsedMin;
    private final NightMode mode;
    private final VolumePercent volume;
    private final int responseGapSec;
    private final int silenceSec;

    public Observation(int elapsedMin, NightMode mode, VolumePercent volume, int responseGapSec, int silenceSec) {
        if (elapsedMin < 0 || !isReportedSeconds(responseGapSec) || !isReportedSeconds(silenceSec)) {
            throw new IllegalArgumentException("observation values are out of range");
        }
        this.elapsedMin = elapsedMin;
        this.mode = Objects.requireNonNull(mode);
        this.volume = Objects.requireNonNull(volume);
        this.responseGapSec = responseGapSec;
        this.silenceSec = silenceSec;
    }

    private static boolean isReportedSeconds(int seconds) {
        return seconds >= 0 && seconds <= NightConstants.MAX_REPORTED_SECONDS;
    }

    public int getElapsedMin() {
        return elapsedMin;
    }

    public NightMode getMode() {
        return mode;
    }

    public VolumePercent getVolume() {
        return volume;
    }

    public int getResponseGapSec() {
        return responseGapSec;
    }

    public int getSilenceSec() {
        return silenceSec;
    }

    /** 判断の記録に残す形（名前と数値だけ）。 */
    public Map<String, String> asRecordedValues() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("elapsedMin", String.valueOf(elapsedMin));
        values.put("mode", mode.asId());
        values.put("volumePct", String.valueOf(volume.asInt()));
        values.put("responseGapSec", String.valueOf(responseGapSec));
        values.put("silenceSec", String.valueOf(silenceSec));
        return values;
    }
}
