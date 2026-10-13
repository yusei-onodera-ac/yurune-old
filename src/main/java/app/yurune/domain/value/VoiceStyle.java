package app.yurune.domain.value;

import app.yurune.constant.SettingsConstants;

/**
 * ぽてねの声の種類（仮置き）。声そのものは変えず、読み上げの速さと高さだけを変える。
 * 仕様書の「日本語の標準の声、遅め・低め」が、既定の「やわらかい」。ほかの 2 つも、これより速く・高くはしない側に置いてある。
 */
public enum VoiceStyle {

    SOFT("soft", "やわらかい", "ゆっくり、低めの声",
            SettingsConstants.SOFT_SPEAKING_RATE, SettingsConstants.SOFT_PITCH,
            SettingsConstants.SOFT_BROWSER_RATE, SettingsConstants.SOFT_BROWSER_PITCH),
    SLOW("slow", "ゆっくり", "さらにゆっくり、低めの声",
            SettingsConstants.SLOW_SPEAKING_RATE, SettingsConstants.SLOW_PITCH,
            SettingsConstants.SLOW_BROWSER_RATE, SettingsConstants.SLOW_BROWSER_PITCH),
    NORMAL("normal", "ふつう", "ふつうの速さと高さ",
            SettingsConstants.NORMAL_SPEAKING_RATE, SettingsConstants.NORMAL_PITCH,
            SettingsConstants.NORMAL_BROWSER_RATE, SettingsConstants.NORMAL_BROWSER_PITCH);

    private final String id;
    private final String label;
    private final String hint;
    private final double speakingRate;
    private final double pitch;
    private final double browserRate;
    private final double browserPitch;

    VoiceStyle(String id, String label, String hint, double speakingRate, double pitch, double browserRate,
            double browserPitch) {
        this.id = id;
        this.label = label;
        this.hint = hint;
        this.speakingRate = speakingRate;
        this.pitch = pitch;
        this.browserRate = browserRate;
        this.browserPitch = browserPitch;
    }

    /** 設定がまだないときと、保存された値が読めないときの声。 */
    public static VoiceStyle findDefault() {
        return SOFT;
    }

    /** 画面から届いた ID から作る。ないときと、知らない名前のときは、IllegalArgumentException。 */
    public static VoiceStyle fromId(String id) {
        for (VoiceStyle style : values()) {
            if (style.id.equals(id)) {
                return style;
            }
        }
        throw new IllegalArgumentException("unknown voice style: " + id);
    }

    public String asId() {
        return id;
    }

    public String asLabel() {
        return label;
    }

    /** 選ぶ画面で、名前の下に出す短い説明。 */
    public String asHint() {
        return hint;
    }

    /** サーバーの読み上げ（Cloud Text-to-Speech の speakingRate）。 */
    public double getSpeakingRate() {
        return speakingRate;
    }

    /** サーバーの読み上げ（Cloud Text-to-Speech の pitch。半音の単位）。 */
    public double getPitch() {
        return pitch;
    }

    /** 端末の読み上げ（ブラウザの rate）。 */
    public double getBrowserRate() {
        return browserRate;
    }

    /** 端末の読み上げ（ブラウザの pitch）。 */
    public double getBrowserPitch() {
        return browserPitch;
    }
}
