package app.yurune.domain.value;

/**
 * LLM 呼び出し 1 回分の結果（運用の記録 llm ログ用）。
 */
public enum LlmOutcome {

    OK("ok"),
    ERROR("error"),
    TIMEOUT("timeout"),
    PARSE_ERROR("parse_error"),
    /** 呼び出し自体は返ったが、出力の検査で定型文に差し替えた。 */
    REJECTED("rejected");

    private final String id;

    LlmOutcome(String id) {
        this.id = id;
    }

    public String asId() {
        return id;
    }

    /**
     * LLM が使えなかった理由（FallbackReason）から、対応する結果を決める。
     * TIMEOUT・PARSE_ERROR 以外（ブレーカー開、呼び出し上限、停止、Armor 停止など）は、
     * この呼び出し自体の結果としては一般の失敗として ERROR にする。
     */
    public static LlmOutcome fromFailure(FallbackReason reason) {
        return switch (reason) {
            case TIMEOUT -> TIMEOUT;
            case PARSE_ERROR -> PARSE_ERROR;
            default -> ERROR;
        };
    }
}
