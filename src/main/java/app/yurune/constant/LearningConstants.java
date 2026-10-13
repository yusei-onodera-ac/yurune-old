package app.yurune.constant;

/**
 * 学習（選択肢の選び方と表示ルール）で使う固定値。
 */
public final class LearningConstants {

    /** 事前分布の「合っていた」側の仮想回数。まだ試していない選択肢も、候補から外れないようにするため 1 とする。 */
    public static final int PRIOR_WIN_COUNT = 1;

    /** 事前分布の「合っていなかった」側の仮想回数。 */
    public static final int PRIOR_LOSS_COUNT = 1;

    /** 「いまの第一候補」を出すために必要な、試した回数の下限。少ない回数で断定しないため。 */
    public static final int MIN_TRIALS_FOR_TOP_CANDIDATE = 3;

    /** 一様乱数が 0 になると対数が発散するため、下限に使う値。 */
    public static final double MIN_UNIFORM_VALUE = Double.MIN_VALUE;

    /** 勝率の計算で、試した回数が 0 のときに返す値。 */
    public static final double NO_TRIAL_WIN_RATE = 0.0;

    private LearningConstants() {
    }
}
