package app.yurune.domain.value;

import java.util.Optional;

/**
 * LLM の応答の usageMetadata から読める、送った・返ったトークンの数だけ（運用の記録 llm ログ用）。
 * 本文は持たない。読めなかった、または無いときは、両方とも空。
 */
public final class TokenUsage {

    private static final TokenUsage NONE = new TokenUsage(null, null);

    private final Integer promptTokens;
    private final Integer candidateTokens;

    public TokenUsage(Integer promptTokens, Integer candidateTokens) {
        this.promptTokens = promptTokens;
        this.candidateTokens = candidateTokens;
    }

    public static TokenUsage none() {
        return NONE;
    }

    public Optional<Integer> getPromptTokens() {
        return Optional.ofNullable(promptTokens);
    }

    public Optional<Integer> getCandidateTokens() {
        return Optional.ofNullable(candidateTokens);
    }
}
