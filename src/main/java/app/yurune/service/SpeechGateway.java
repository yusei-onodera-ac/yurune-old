package app.yurune.service;

import app.yurune.domain.value.VoiceStyle;

/**
 * 読み上げ（音声合成）の窓口。検査を通した文だけを渡す。使えないときは SpeechUnavailableException。
 */
public interface SpeechGateway {

    /** 設定されていて、使う状態か。使わないときは、端末の読み上げに任せる。 */
    boolean isEnabled();

    /** 文を、音声（MP3）にして返す。音声は保存しない。 */
    byte[] synthesize(String text);

    /**
     * 声の種類（速さ、高さ）を指定して、文を音声にする。声の種類を使い分けられない実装は、その実装の声で作る。
     */
    default byte[] synthesize(String text, VoiceStyle style) {
        return synthesize(text);
    }
}
