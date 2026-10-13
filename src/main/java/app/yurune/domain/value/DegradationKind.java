package app.yurune.domain.value;

/**
 * 制御が縮退した種類（運用の記録 control ログ用）。状態が変わったときだけ記録するために使う。
 * 実際の state の語は、kind ごとに決める（固定の短い語。例: breaker は open/half_open/closed、
 * 停止スイッチは on/off、Armor は down/up、呼び出しの上限は night/talk/daily）。
 */
public enum DegradationKind {

    /** サーキットブレーカー（LLM）の開閉。 */
    LLM_BREAKER("llm_breaker"),
    /** LLM の呼び出しの間隔・日次の上限による拒否。 */
    LLM_RATE_LIMIT("llm_rate_limit"),
    /** 停止スイッチ（YURUNE_AGENT_ENABLED）。 */
    AGENT_DISABLED("agent_disabled"),
    /** 入出力の検査サービス（Model Armor）の停止・復帰。 */
    ARMOR_DOWN("armor_down"),
    /** 保存先（Firestore）への読み書きの、/ready による確認の停止・復帰。 */
    STORAGE_DOWN("storage_down");

    private final String id;

    DegradationKind(String id) {
        this.id = id;
    }

    public String asId() {
        return id;
    }
}
