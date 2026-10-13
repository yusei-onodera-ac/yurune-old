package app.yurune.service;

import app.yurune.constant.LearningConstants;
import app.yurune.domain.entity.Arm;
import app.yurune.domain.value.ArmKey;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.OptionName;
import app.yurune.domain.value.SelectedOption;
import app.yurune.repository.ArmRepository;

import java.util.List;
import java.util.Random;

/**
 * トンプソン抽出で、今夜試す選択肢を選ぶ。
 * 計算はコードで行い、LLM には計算させない（再現できて、理由を説明できるため）。
 */
public final class ThompsonOptionSelector {

    private final ArmRepository armRepository;
    private final BetaSampler betaSampler;

    public ThompsonOptionSelector(ArmRepository armRepository, BetaSampler betaSampler) {
        this.armRepository = armRepository;
        this.betaSampler = betaSampler;
    }

    public SelectedOption selectOption(Dimension dimension, List<OptionName> candidates, long seed) {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("candidates must not be empty");
        }
        Random random = new Random(seed);
        OptionName best = null;
        double bestSample = -1.0;
        for (OptionName candidate : candidates) {
            double sample = sampleCandidate(dimension, candidate, random);
            if (sample > bestSample) {
                bestSample = sample;
                best = candidate;
            }
        }
        return new SelectedOption(best, seed);
    }

    private double sampleCandidate(Dimension dimension, OptionName candidate, Random random) {
        ArmKey key = new ArmKey(dimension, candidate);
        Arm arm = armRepository.findArm(key).orElseGet(() -> Arm.untried(key));
        int winParameter = LearningConstants.PRIOR_WIN_COUNT + arm.getWinCount().asInt();
        int lossCount = arm.getTrialCount().asInt() - arm.getWinCount().asInt();
        int lossParameter = LearningConstants.PRIOR_LOSS_COUNT + lossCount;
        return betaSampler.sampleBeta(random, winParameter, lossParameter);
    }
}
