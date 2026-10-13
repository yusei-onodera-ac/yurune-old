package app.yurune.domain.value;

import app.yurune.constant.NightConstants;

/**
 * 音量（%）。
 */
public final class VolumePercent {

    private final int percent;

    public VolumePercent(int percent) {
        if (percent < 0 || percent > NightConstants.PERCENT_MAX) {
            throw new IllegalArgumentException("volume must be 0..100");
        }
        this.percent = percent;
    }

    public int asInt() {
        return percent;
    }
}
