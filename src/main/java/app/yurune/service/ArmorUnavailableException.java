package app.yurune.service;

/**
 * 入出力の検査サービス（Model Armor）が使えないとき（通信の失敗、時間切れ、想定と違う応答）に投げる。
 * 検査できないまま会話を続けない（個人情報の検出が弱くなるため）。呼び出し側は、会話を定型文 T-DOWN にする。
 */
public final class ArmorUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ArmorUnavailableException(String message) {
        super(message);
    }

    public ArmorUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
