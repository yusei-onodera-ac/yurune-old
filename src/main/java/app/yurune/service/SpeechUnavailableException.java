package app.yurune.service;

/**
 * 読み上げ（音声合成）が使えないときに投げる。会話の文は画面に出るので、会話そのものは止めない。
 */
public final class SpeechUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SpeechUnavailableException(String message) {
        super(message);
    }

    public SpeechUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
