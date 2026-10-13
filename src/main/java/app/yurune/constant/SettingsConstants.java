package app.yurune.constant;

import java.util.List;

/**
 * 設定画面の項目（夜の会話、音量の上限、ぽてねの声、好みを覚える、夜のめやす）の、経路と、選べる値と、既定の値。
 * 仕様書にあるのは、初期の値（音量の上限 60%、声はやわらかい、会話と学習はオン）だけで、
 * 音量の段階と、声の種類（速さ・高さ）は、仮置き。いちばん大きい音と、いちばん速い声を、初期の値より増やさない側に決めてある。
 */
public final class SettingsConstants {

    public static final String PATH_UPDATE = "/settings/update";
    public static final String PATH_VOLUME = "/settings/volume";
    public static final String PATH_VOICE = "/settings/voice";
    public static final String PATH_GOAL = "/settings/goal";

    public static final String VIEW_CHOICE = "/WEB-INF/views/settings-choice.jsp";

    public static final String PARAM_ITEM = "item";
    public static final String PARAM_VALUE = "value";

    public static final String ATTR_SETTINGS = "settings";
    public static final String ATTR_CHOICES = "choices";
    public static final String ATTR_CHOICE_TITLE = "choiceTitle";
    public static final String ATTR_CHOICE_FOOT = "choiceFoot";
    public static final String ATTR_CHOICE_ITEM = "choiceItem";
    public static final String ATTR_TALK_ENABLED = "talkEnabled";
    public static final String ATTR_NIGHT_VOLUME_CAP = "nightVolumeCap";
    public static final String ATTR_NIGHT_SPEECH_RATE = "nightSpeechRate";
    public static final String ATTR_NIGHT_SPEECH_PITCH = "nightSpeechPitch";
    /** 夜の画面が、Wake Lock を求めてよいか。 */
    public static final String ATTR_NIGHT_KEEP_SCREEN_ON = "nightKeepScreenOn";

    /** スイッチの値。画面から届く文字は、この 2 つだけを受け付ける。 */
    public static final String VALUE_ON = "on";
    public static final String VALUE_OFF = "off";

    /** 音量の上限として選べる値（%）。初期値の 60 より大きい値は、選べない（起こさないため）。 */
    public static final List<Integer> VOLUME_CAP_CHOICES = List.of(20, 30, 40, 50, 60);
    public static final int DEFAULT_VOLUME_CAP_PCT = NightConstants.DEFAULT_VOLUME_CAP_PCT;
    /** 保存先から設定が読めないときに使う音量の上限。選べる値のうち、いちばん小さい値。 */
    public static final int CAUTIOUS_VOLUME_CAP_PCT = VOLUME_CAP_CHOICES.get(0);

    public static final String VOLUME_CAP_TEXT_SUFFIX = "%";
    public static final String VOLUME_CAP_DEFAULT_HINT = "初期値";

    /** 声の種類ごとの、サーバーの読み上げ（速さ、高さ）と、端末の読み上げ（速さ、高さ）。「やわらかい」は、これまでの声と同じ。 */
    public static final double SOFT_SPEAKING_RATE = ConversationConstants.SPEAKING_RATE;
    public static final double SOFT_PITCH = ConversationConstants.PITCH;
    public static final double SOFT_BROWSER_RATE = 0.9;
    public static final double SOFT_BROWSER_PITCH = 0.8;

    public static final double SLOW_SPEAKING_RATE = 0.8;
    public static final double SLOW_PITCH = ConversationConstants.PITCH;
    public static final double SLOW_BROWSER_RATE = 0.8;
    public static final double SLOW_BROWSER_PITCH = 0.8;

    public static final double NORMAL_SPEAKING_RATE = 1.0;
    public static final double NORMAL_PITCH = 0.0;
    public static final double NORMAL_BROWSER_RATE = 1.0;
    public static final double NORMAL_BROWSER_PITCH = 1.0;

    public static final String TITLE_VOLUME = "音量の上限";
    public static final String FOOT_VOLUME = "夜の音と、ぽてねの声が、この大きさをこえないようにします。";
    public static final String TITLE_VOICE = "ぽてねの声";
    public static final String FOOT_VOICE = "夜の会話で、ぽてねが話すときの、速さと高さです。";

    private SettingsConstants() {
    }
}
