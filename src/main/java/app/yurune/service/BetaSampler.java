package app.yurune.service;

import app.yurune.constant.LearningConstants;

import java.util.Random;

/**
 * ベータ分布から値を 1 つ取り出す。
 * 形のパラメータが整数のときは、指数分布の和でガンマ分布を作れるため、外部ライブラリを使わない。
 */
public final class BetaSampler {

    public double sampleBeta(Random random, int winParameter, int lossParameter) {
        double winSide = sampleGamma(random, winParameter);
        double lossSide = sampleGamma(random, lossParameter);
        return winSide / (winSide + lossSide);
    }

    private double sampleGamma(Random random, int shape) {
        double sum = 0.0;
        for (int index = 0; index < shape; index++) {
            sum += sampleExponential(random);
        }
        return sum;
    }

    private double sampleExponential(Random random) {
        // 一様乱数が 0 のとき対数が発散するため、下限を設ける
        double uniform = Math.max(random.nextDouble(), LearningConstants.MIN_UNIFORM_VALUE);
        return -Math.log(uniform);
    }
}
