package app.yurune.domain.value;

import app.yurune.constant.LearningConstants;

/**
 * 合っていた割合（合っていた回数 ÷ 試した回数）。
 */
public final class WinRate implements Comparable<WinRate> {

    private final double rate;

    public WinRate(WinCount winCount, TrialCount trialCount) {
        // 試した回数が 0 のときの割り算を避けるため、決めた値を使う
        this.rate = trialCount.asInt() == 0
                ? LearningConstants.NO_TRIAL_WIN_RATE
                : (double) winCount.asInt() / trialCount.asInt();
    }

    public double asDouble() {
        return rate;
    }

    @Override
    public int compareTo(WinRate other) {
        return Double.compare(rate, other.rate);
    }
}
