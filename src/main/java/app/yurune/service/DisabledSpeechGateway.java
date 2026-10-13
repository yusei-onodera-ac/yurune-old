package app.yurune.service;

/**
 * サーバーでの読み上げを使わない設定のときの窓口。端末（ブラウザ）の読み上げに任せる。
 */
public final class DisabledSpeechGateway implements SpeechGateway {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public byte[] synthesize(String text) {
        throw new SpeechUnavailableException("server speech is not configured");
    }
}
