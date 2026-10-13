package app.yurune.domain.value;

/**
 * LLM を呼んだ目的（運用の記録 llm ログ用）。夜のエージェントの判断か、会話の応答か。
 */
public enum LlmPurpose {

    NIGHT("night"),
    TALK("talk");

    private final String id;

    LlmPurpose(String id) {
        this.id = id;
    }

    public String asId() {
        return id;
    }
}
