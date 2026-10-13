package app.yurune.service;

import app.yurune.domain.value.ViolationKind;

import java.util.Optional;

/**
 * 会話の 1 回分の返事。文は、LLM が書いたもの（検査を通ったもの）か、コードの定型文のどちらか。
 * 検査に引っかかった種類だけを持ち、利用者の発言や、捨てた出力の本文は持たない。
 */
public final class ConversationReply {

    /** 返事の出どころ。 */
    public enum Source {
        /** LLM が書き、出力の検査を通った文。 */
        LLM("llm"),
        /** 入力に医療領域の語があったので、LLM を呼ばずに返した定型文（T-MED）。 */
        MEDICAL_TEMPLATE("medical"),
        /** 検査に引っかかったので、差し替えた定型文（T-FALLBACK）。 */
        FALLBACK_TEMPLATE("fallback"),
        /** LLM・音声・検査の仕組みが使えないときの定型文（T-DOWN）。 */
        DOWN_TEMPLATE("down"),
        /** 無言。何も返さない。 */
        SILENCE("silence");

        private final String id;

        Source(String id) {
            this.id = id;
        }

        public String asId() {
            return id;
        }
    }

    private final String text;
    private final Source source;
    private final ViolationKind violationKind;

    private ConversationReply(String text, Source source, ViolationKind violationKind) {
        this.text = text;
        this.source = source;
        this.violationKind = violationKind;
    }

    public static ConversationReply of(String text, Source source) {
        return new ConversationReply(text, source, null);
    }

    public static ConversationReply replaced(String text, ViolationKind kind) {
        return new ConversationReply(text, Source.FALLBACK_TEMPLATE, kind);
    }

    public static ConversationReply silence() {
        return new ConversationReply("", Source.SILENCE, null);
    }

    /** 返事をせずに、無視する。理由の種類だけを記録に残す（発言の本文は持たない）。 */
    public static ConversationReply ignored(ViolationKind kind) {
        return new ConversationReply("", Source.SILENCE, kind);
    }

    public String getText() {
        return text;
    }

    public Source getSource() {
        return source;
    }

    public boolean shouldSpeak() {
        return source != Source.SILENCE;
    }

    /** 差し替えの理由（検査に引っかかった種類）。差し替えでないときは空。 */
    public Optional<ViolationKind> getViolationKind() {
        return Optional.ofNullable(violationKind);
    }
}
