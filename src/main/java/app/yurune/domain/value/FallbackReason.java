package app.yurune.domain.value;

/**
 * 夜のエージェント（または会話）が、LLM の代わりに規則・定型文に切り替えた理由（運用の記録用）。
 *
 * LLM_ERROR は、当初の設計にあった 6 つ（BREAKER_OPEN、LIMITER、TIMEOUT、PARSE_ERROR、
 * LLM_DISABLED、ARMOR_DOWN）には無い。HTTP のエラー応答や通信断など、タイムアウトでもパース失敗でもない、
 * LLM からの一般的な失敗を表す値が無かったため、設計に 1 つ追加した。
 */
public enum FallbackReason {

    /** サーキットブレーカーが開いていて、呼ばなかった。 */
    BREAKER_OPEN("breaker_open"),
    /** 呼び出しの間隔・日次の上限に引っかかり、呼ばなかった。 */
    LIMITER("limiter"),
    /** LLM の応答が、時間内に返らなかった。 */
    TIMEOUT("timeout"),
    /** LLM の応答が、想定の形で読めなかった。 */
    PARSE_ERROR("parse_error"),
    /** 停止スイッチ、または鍵の未設定で、そもそも LLM を使わない構成だった。 */
    LLM_DISABLED("llm_disabled"),
    /** 入出力の検査サービス（Model Armor）が使えなかった。 */
    ARMOR_DOWN("armor_down"),
    /** タイムアウト・パース失敗のどちらでもない、LLM からの一般的な失敗（設計からの変更。上のコメント参照）。 */
    LLM_ERROR("llm_error");

    private final String id;

    FallbackReason(String id) {
        this.id = id;
    }

    public String asId() {
        return id;
    }
}
