package app.yurune.service;

import app.yurune.domain.value.FallbackReason;

/**
 * LLM が使えないとき（通信できない、時間切れ、エラー応答）の例外。
 * 呼び出し側は、規則に切り替える。メッセージには、会話や応答の本文を入れない。
 *
 * 分類済みの理由（FallbackReason）を、型で持つ。自由な文（message）を読んで分類する作りにはしない。
 * 発生源（タイムアウト、パース失敗など）が、投げるときに、最初から分かっている値を渡す。
 */
public final class LlmUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final FallbackReason reason;

    /** 理由を指定しない、既存の呼び出し元のための形。理由は、一般の失敗（LLM_ERROR）として扱う。 */
    public LlmUnavailableException(String message) {
        this(message, FallbackReason.LLM_ERROR);
    }

    public LlmUnavailableException(String message, Throwable cause) {
        this(message, FallbackReason.LLM_ERROR, cause);
    }

    public LlmUnavailableException(String message, FallbackReason reason) {
        super(message);
        this.reason = reason;
    }

    public LlmUnavailableException(String message, FallbackReason reason, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    /** LLM に切り替えた理由（運用の記録用）。 */
    public FallbackReason getReason() {
        return reason;
    }
}
