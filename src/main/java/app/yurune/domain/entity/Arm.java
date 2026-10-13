package app.yurune.domain.entity;

import app.yurune.domain.value.ArmKey;
import app.yurune.domain.value.MorningAnswer;
import app.yurune.domain.value.TrialCount;
import app.yurune.domain.value.WinCount;
import app.yurune.domain.value.WinRate;

import java.util.Objects;

/**
 * 選択肢ごとの集計（試した回数と、合っていた回数）。
 * 値は変えず、結果を足すたびに新しい Arm を返す。
 */
public final class Arm {

    private final ArmKey key;
    private final TrialCount trialCount;
    private final WinCount winCount;

    public Arm(ArmKey key, TrialCount trialCount, WinCount winCount) {
        this.key = Objects.requireNonNull(key);
        this.trialCount = Objects.requireNonNull(trialCount);
        this.winCount = Objects.requireNonNull(winCount);
        if (winCount.asInt() > trialCount.asInt()) {
            throw new IllegalArgumentException("wins must not exceed trials");
        }
    }

    /** まだ一度も試していない選択肢。 */
    public static Arm untried(ArmKey key) {
        return new Arm(key, new TrialCount(0), new WinCount(0));
    }

    /** 朝の答えを反映した、新しい集計を返す。（朝の答えは、いまは見直しの「合わなかった」の数に使い、勝ちには使わない。審査用サンプルの再生だけが使う。） */
    public Arm withResult(MorningAnswer answer) {
        WinCount nextWinCount = answer.isFit() ? winCount.increase() : winCount;
        return new Arm(key, trialCount.increase(), nextWinCount);
    }

    /**
     * 夜の指標による 1 回の試行を反映した、新しい集計を返す。
     * win は、変えた夜の値が、標準の夜の中央値より、めやすの向きによいとき。
     */
    public Arm withMetricResult(boolean win) {
        WinCount nextWinCount = win ? winCount.increase() : winCount;
        return new Arm(key, trialCount.increase(), nextWinCount);
    }

    public ArmKey getKey() {
        return key;
    }

    public TrialCount getTrialCount() {
        return trialCount;
    }

    public WinCount getWinCount() {
        return winCount;
    }

    public WinRate getWinRate() {
        return new WinRate(winCount, trialCount);
    }
}
