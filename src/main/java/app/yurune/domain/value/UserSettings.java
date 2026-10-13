package app.yurune.domain.value;

import app.yurune.constant.SettingsConstants;

import java.util.Objects;

/**
 * 利用者が設定画面で決める 6 つの値（夜の会話、音量の上限、ぽてねの声、好みを覚える、夜のめやす、画面をつけたままにする）。
 * 値は変えず、変わるたびに新しい UserSettings を返す。選べない値（音量の段階にない数、知らない声）は、作れない。
 */
public final class UserSettings {

    private final boolean nightTalkEnabled;
    private final VolumePercent volumeCap;
    private final VoiceStyle voiceStyle;
    private final boolean learningEnabled;
    private final NightGoal nightGoal;
    private final boolean keepScreenOn;

    private UserSettings(boolean nightTalkEnabled, VolumePercent volumeCap, VoiceStyle voiceStyle,
            boolean learningEnabled, NightGoal nightGoal, boolean keepScreenOn) {
        this.nightTalkEnabled = nightTalkEnabled;
        this.volumeCap = Objects.requireNonNull(volumeCap);
        this.voiceStyle = Objects.requireNonNull(voiceStyle);
        this.learningEnabled = learningEnabled;
        this.nightGoal = Objects.requireNonNull(nightGoal);
        this.keepScreenOn = keepScreenOn;
    }

    /**
     * 仕様書の初期値。会話はオン、音量の上限は 60%、声はやわらかい、好みを覚えるはオン、夜のめやすは寝つき。
     * 画面をつけたままにするは、初期値オフ（仮置き。電池を余分に使うため、既定では控える）。
     */
    public static UserSettings defaults() {
        return new UserSettings(true, new VolumePercent(SettingsConstants.DEFAULT_VOLUME_CAP_PCT),
                VoiceStyle.findDefault(), true, NightGoal.DEFAULT, false);
    }

    /**
     * 設定を読めないとき（保存先が止まっていて、直前の値もない）に、守る側へ倒した値。
     * 会話は出さず、音量はいちばん小さい上限にし、好みは覚えない（利用者の選びを確かめられないまま、広げない）。
     */
    public static UserSettings cautious() {
        return new UserSettings(false, new VolumePercent(SettingsConstants.CAUTIOUS_VOLUME_CAP_PCT),
                VoiceStyle.findDefault(), false, NightGoal.DEFAULT, false);
    }

    /** 夜のめやすを持たない、古い保存データの読み方（夜のめやすは、寝つき）。 */
    public static UserSettings restore(Boolean storedNightTalk, Long storedVolumeCapPct, String storedVoiceId,
            Boolean storedLearning) {
        return restore(storedNightTalk, storedVolumeCapPct, storedVoiceId, storedLearning, null);
    }

    /** 画面をつけたままにする、を持たない、古い保存データの読み方（既定はオフ）。 */
    public static UserSettings restore(Boolean storedNightTalk, Long storedVolumeCapPct, String storedVoiceId,
            Boolean storedLearning, String storedNightGoalId) {
        return restore(storedNightTalk, storedVolumeCapPct, storedVoiceId, storedLearning, storedNightGoalId, null);
    }

    /**
     * 保存された値から作る。古い文書（その項目がない）と、読めない値（選べる値にない）は、その項目だけ初期値にする。
     * 保存先の値が壊れていても、画面と夜の制御を止めないため。
     */
    public static UserSettings restore(Boolean storedNightTalk, Long storedVolumeCapPct, String storedVoiceId,
            Boolean storedLearning, String storedNightGoalId, Boolean storedKeepScreenOn) {
        UserSettings initial = defaults();
        return new UserSettings(
                storedNightTalk == null ? initial.nightTalkEnabled : storedNightTalk,
                storedVolumeCapPct != null && isVolumeCapChoice(storedVolumeCapPct)
                        ? new VolumePercent(storedVolumeCapPct.intValue()) : initial.volumeCap,
                restoreVoice(storedVoiceId, initial.voiceStyle),
                storedLearning == null ? initial.learningEnabled : storedLearning,
                restoreNightGoal(storedNightGoalId, initial.nightGoal),
                storedKeepScreenOn == null ? initial.keepScreenOn : storedKeepScreenOn);
    }

    /**
     * 画面から届いた 1 項目の値で、新しい設定を作る。スイッチは on / off、音量は選べる段階の数、声は声の種類の ID。
     * 受け付けない値のときは、IllegalArgumentException（何も変えない）。
     */
    public UserSettings change(SettingItem item, String valueId) {
        return switch (item) {
            case NIGHT_TALK -> withNightTalkEnabled(parseSwitch(valueId));
            case VOLUME_CAP -> withVolumeCapPct(Integer.parseInt(valueId));
            case VOICE -> withVoiceStyle(VoiceStyle.fromId(valueId));
            case LEARNING -> withLearningEnabled(parseSwitch(valueId));
            case GOAL -> withNightGoal(NightGoal.fromId(valueId));
            case KEEP_SCREEN_ON -> withKeepScreenOnEnabled(parseSwitch(valueId));
        };
    }

    public UserSettings withNightTalkEnabled(boolean enabled) {
        return new UserSettings(enabled, volumeCap, voiceStyle, learningEnabled, nightGoal, keepScreenOn);
    }

    public UserSettings withVolumeCapPct(int percent) {
        if (!isVolumeCapChoice(percent)) {
            throw new IllegalArgumentException("volume cap is not a choice: " + percent);
        }
        return new UserSettings(nightTalkEnabled, new VolumePercent(percent), voiceStyle, learningEnabled, nightGoal,
                keepScreenOn);
    }

    public UserSettings withVoiceStyle(VoiceStyle style) {
        return new UserSettings(nightTalkEnabled, volumeCap, Objects.requireNonNull(style), learningEnabled, nightGoal,
                keepScreenOn);
    }

    public UserSettings withLearningEnabled(boolean enabled) {
        return new UserSettings(nightTalkEnabled, volumeCap, voiceStyle, enabled, nightGoal, keepScreenOn);
    }

    public UserSettings withNightGoal(NightGoal goal) {
        return new UserSettings(nightTalkEnabled, volumeCap, voiceStyle, learningEnabled, Objects.requireNonNull(goal),
                keepScreenOn);
    }

    /** 夜のあいだ、画面をつけたままにする（Wake Lock）かどうか。既定はオフ（電池を余分に使うため）。 */
    public UserSettings withKeepScreenOnEnabled(boolean enabled) {
        return new UserSettings(nightTalkEnabled, volumeCap, voiceStyle, learningEnabled, nightGoal, enabled);
    }

    /** 変えたあとの値を、記録に残す短い文字（on / off、音量の数、声の ID、夜のめやすの ID）にする。 */
    public String describeValue(SettingItem item) {
        return switch (item) {
            case NIGHT_TALK -> describeSwitch(nightTalkEnabled);
            case VOLUME_CAP -> String.valueOf(volumeCap.asInt());
            case VOICE -> voiceStyle.asId();
            case LEARNING -> describeSwitch(learningEnabled);
            case GOAL -> nightGoal.asId();
            case KEEP_SCREEN_ON -> describeSwitch(keepScreenOn);
        };
    }

    /** 「夜の会話」がオンのとき、夜に「ぽてねと話す」を選べる（仕様書 U-04）。 */
    public boolean isNightTalkEnabled() {
        return nightTalkEnabled;
    }

    /** 利用者が選んだ、音量の上限。実際の上限は、運用の上限（環境変数）と比べて、低い方。 */
    public VolumePercent getVolumeCap() {
        return volumeCap;
    }

    /** 運用の上限（環境変数の値）より大きくならない、実際の音量の上限（%）。 */
    public int getVolumeCapPctWithin(int operatorCapPct) {
        return Math.min(operatorCapPct, volumeCap.asInt());
    }

    public VoiceStyle getVoiceStyle() {
        return voiceStyle;
    }

    /** 「好みを覚える」がオンのとき、朝の答えを集計に反映し、夜のやり方に使う。 */
    public boolean isLearningEnabled() {
        return learningEnabled;
    }

    /** 夜のめやす。ふりかえりの「いまのところ」と、夜の見直しが、この値で比べる。 */
    public NightGoal getNightGoal() {
        return nightGoal;
    }

    /** 夜のあいだ、画面をつけたままにするか。オンのときだけ、夜の画面が Wake Lock を求める。 */
    public boolean isKeepScreenOnEnabled() {
        return keepScreenOn;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof UserSettings that && nightTalkEnabled == that.nightTalkEnabled
                && volumeCap.asInt() == that.volumeCap.asInt() && voiceStyle == that.voiceStyle
                && learningEnabled == that.learningEnabled && nightGoal == that.nightGoal
                && keepScreenOn == that.keepScreenOn;
    }

    @Override
    public int hashCode() {
        return Objects.hash(nightTalkEnabled, volumeCap.asInt(), voiceStyle, learningEnabled, nightGoal, keepScreenOn);
    }

    private static boolean isVolumeCapChoice(long percent) {
        for (int choice : SettingsConstants.VOLUME_CAP_CHOICES) {
            if (choice == percent) {
                return true;
            }
        }
        return false;
    }

    private static VoiceStyle restoreVoice(String storedVoiceId, VoiceStyle fallback) {
        try {
            return VoiceStyle.fromId(storedVoiceId);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    /** 読めない夜のめやす（知らない ID）は、初期値にする。項目がない古い文書も、同じ扱い。 */
    private static NightGoal restoreNightGoal(String storedNightGoalId, NightGoal fallback) {
        if (storedNightGoalId == null) {
            return fallback;
        }
        try {
            return NightGoal.fromId(storedNightGoalId);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private static boolean parseSwitch(String valueId) {
        if (SettingsConstants.VALUE_ON.equals(valueId)) {
            return true;
        }
        if (SettingsConstants.VALUE_OFF.equals(valueId)) {
            return false;
        }
        throw new IllegalArgumentException("switch value must be on or off: " + valueId);
    }

    private static String describeSwitch(boolean enabled) {
        return enabled ? SettingsConstants.VALUE_ON : SettingsConstants.VALUE_OFF;
    }
}
