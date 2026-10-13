package app.yurune.service;

import app.yurune.domain.entity.Arm;
import app.yurune.domain.value.ArmKey;
import app.yurune.domain.value.MorningAnswer;
import app.yurune.domain.value.UserSettings;
import app.yurune.repository.ArmRepository;

import java.util.List;
import java.util.function.Supplier;

/**
 * 試した選択肢の集計（学習の on/off）。
 * 朝の答えは、いまは集計の「勝ち」に使わない（勝ちは、夜の指標で決める。NightReviewService）。
 * recordAnsweredNight は、審査用サンプルの再生だけが使う（サンプルの数字を、保存された答えから作るため）。
 * 設定の「好みを覚える」がオフの利用者は、集計を更新しない（すでに覚えたものは、ここでは消さない）。
 */
public final class LearningService {

    private final ArmRepository armRepository;
    private final Supplier<UserSettings> settings;

    /** 設定を見ない（いつも覚える）。審査用サンプルの再生など、利用者の設定と関わらない計算に使う。 */
    public LearningService(ArmRepository armRepository) {
        this(armRepository, UserSettings::defaults);
    }

    public LearningService(ArmRepository armRepository, Supplier<UserSettings> settings) {
        this.armRepository = armRepository;
        this.settings = settings;
    }

    /** この利用者の設定で、好みを覚える状態か。 */
    public boolean isEnabled() {
        return settings.get().isLearningEnabled();
    }

    public void recordAnsweredNight(List<ArmKey> triedKeys, MorningAnswer answer) {
        if (!isEnabled()) {
            return;
        }
        for (ArmKey key : triedKeys) {
            Arm current = armRepository.findArm(key).orElseGet(() -> Arm.untried(key));
            armRepository.saveArm(current.withResult(answer));
        }
    }
}
