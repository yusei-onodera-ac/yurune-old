package app.yurune.domain.value;

/**
 * 入力や出力の検査に引っかかった理由の種類。記録するのはこの種類と件数だけで、本文は記録しない。
 */
public enum ViolationKind {
    MEDICAL_TOPIC("medical_topic"),
    EFFICACY_CLAIM("efficacy_claim"),
    SLEEP_STATE_CLAIM("sleep_state_claim"),
    EVALUATION_OR_CAUSALITY("evaluation_or_causality"),
    EVIDENCE_CLAIM("evidence_claim"),
    ROLE_BREAK("role_break"),
    PERSONAL_INFORMATION("personal_information"),
    FACT_SHEET("fact_sheet"),
    LENGTH("length"),
    SYSTEM_PROMPT_LEAK("system_prompt_leak"),
    INJECTION("injection"),
    EMPTY("empty");

    private final String id;

    ViolationKind(String id) {
        this.id = id;
    }

    public String asId() {
        return id;
    }
}
