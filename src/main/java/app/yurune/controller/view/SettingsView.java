package app.yurune.controller.view;

/**
 * 設定画面に出す、6 項目の現在の値（スイッチの状態と、› の項目の値の文字）。
 */
public final class SettingsView {

    private final boolean nightTalkEnabled;
    private final String volumeCapText;
    private final String voiceLabel;
    private final boolean learningEnabled;
    private final String goalLabel;
    private final boolean keepScreenOnEnabled;

    public SettingsView(boolean nightTalkEnabled, String volumeCapText, String voiceLabel, boolean learningEnabled,
            String goalLabel, boolean keepScreenOnEnabled) {
        this.nightTalkEnabled = nightTalkEnabled;
        this.volumeCapText = volumeCapText;
        this.voiceLabel = voiceLabel;
        this.learningEnabled = learningEnabled;
        this.goalLabel = goalLabel;
        this.keepScreenOnEnabled = keepScreenOnEnabled;
    }

    public boolean isNightTalkEnabled() {
        return nightTalkEnabled;
    }

    public String getVolumeCapText() {
        return volumeCapText;
    }

    public String getVoiceLabel() {
        return voiceLabel;
    }

    public boolean isLearningEnabled() {
        return learningEnabled;
    }

    public String getGoalLabel() {
        return goalLabel;
    }

    public boolean isKeepScreenOnEnabled() {
        return keepScreenOnEnabled;
    }
}
