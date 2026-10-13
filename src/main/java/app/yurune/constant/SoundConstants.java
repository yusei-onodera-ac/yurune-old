package app.yurune.constant;

/**
 * 夜の音の状態を、画面（属性）と端末（JSON の項目）へ渡すときの名前。
 * 音は端末が合成して鳴らす。サーバーは、曲と音量と、フェードの残りだけを持つ。
 */
public final class SoundConstants {

    public static final String ATTR_SOUND_KEY = "nightSoundKey";
    public static final String ATTR_SOUND_VOLUME_PCT = "nightSoundVolumePct";
    public static final String ATTR_FADE_REMAINING_SEC = "nightFadeRemainingSec";

    public static final String JSON_SOUND = "sound";
    public static final String JSON_TRACK = "track";
    public static final String JSON_VOLUME_PCT = "volumePct";
    public static final String JSON_FADE_REMAINING_SEC = "fadeRemainingSec";

    private SoundConstants() {
    }
}
