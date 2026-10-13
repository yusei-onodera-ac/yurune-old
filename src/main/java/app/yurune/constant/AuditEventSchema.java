package app.yurune.constant;

import java.util.Map;
import java.util.Set;

/**
 * 運用の記録（構造化ログ）の種類ごとに、detail に入れてよい項目名の一覧。
 * 単体テストが、ここに無い項目名が記録に出ていないことを確かめる（AT-06）。
 * 入れ子の masked（個人情報の種類別の件数）は、detail 側では "masked" という項目名だけを許す。
 *
 * 新しい種類を足すときは、ENTRIES に Map.entry(...) を 1 行足すだけでよい。
 * あとで pdca の種類を足すときも同じ（1 行足す）。
 */
public final class AuditEventSchema {

    private static final Map<String, Set<String>> ENTRIES = Map.ofEntries(
            Map.entry(LogConstants.EVENT_REQUEST, Set.of("method", "path", "status", "latencyMs", "user")),
            Map.entry(LogConstants.EVENT_CONVERSATION,
                    Set.of("input", "reply", "truncated", "latencyMs", "masked", "outputViolation", "down")),
            Map.entry(LogConstants.EVENT_DECISION, Set.of("phase", "accepted")),
            Map.entry(LogConstants.EVENT_AUTH, Set.of("action", "outcome")),
            Map.entry(LogConstants.EVENT_ERASE, Set.of("user", "success")),
            Map.entry(LogConstants.EVENT_SETTING, Set.of("item", "value")),
            Map.entry(LogConstants.EVENT_PDCA, Set.of("rule", "verdict", "nights")),
            Map.entry(LogConstants.EVENT_AGENT,
                    Set.of("tool", "accepted", "rejectReason", "source", "latencyMs", "fallbackReason")),
            Map.entry(LogConstants.EVENT_LLM,
                    Set.of("outcome", "latencyMs", "model", "purpose", "promptTokens", "candidateTokens")),
            Map.entry(LogConstants.EVENT_CONTROL, Set.of("kind", "state", "rejectedCount")),
            Map.entry(LogConstants.EVENT_SPAN, Set.of("name", "durationMs", "outcome")));

    private AuditEventSchema() {
    }

    public static boolean isKnownEvent(String event) {
        return ENTRIES.containsKey(event);
    }

    /** 知らない種類のときは、空の一覧（何も許さない）。 */
    public static Set<String> allowedFields(String event) {
        return ENTRIES.getOrDefault(event, Set.of());
    }
}
