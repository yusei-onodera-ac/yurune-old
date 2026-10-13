package app.yurune.domain.value;

/**
 * 夜のエージェントの判断の出どころ（運用の記録 agent ログ用）。
 * LLM が決めた、規則だけで決めた、LLM が使えず規則に切り替えた、の 3 つを区別する。
 */
public enum AgentSource {

    LLM("llm"),
    RULE("rule"),
    FALLBACK("fallback");

    private final String id;

    AgentSource(String id) {
        this.id = id;
    }

    public String asId() {
        return id;
    }
}
