package app.yurune.service;

import app.yurune.sample.SampleNight;

import java.util.List;

/**
 * サンプルの晩を、実際の学習の計算に通す。
 * 結果の数字を直接書かず、同じ処理を通すことで、見せる値と実際の動きを一致させる。
 */
public final class SampleReplayService {

    private final LearningService learningService;

    public SampleReplayService(LearningService learningService) {
        this.learningService = learningService;
    }

    public void replayNights(List<SampleNight> nights) {
        for (SampleNight night : nights) {
            learningService.recordAnsweredNight(night.getTriedKeys(), night.getAnswer());
        }
    }
}
