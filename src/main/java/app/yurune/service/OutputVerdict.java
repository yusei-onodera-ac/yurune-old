package app.yurune.service;

import app.yurune.domain.value.ViolationKind;

import java.util.Optional;

/**
 * LLM の出力を検査した結果。通ったときは、画面や音声に出してよい文（印を数字に置き換え済み）。
 * 通らなかったときは、違反の種類だけを持ち、出力の本文は持ち出さない。
 */
public final class OutputVerdict {

    private final String text;
    private final ViolationKind violationKind;

    private OutputVerdict(String text, ViolationKind violationKind) {
        this.text = text;
        this.violationKind = violationKind;
    }

    public static OutputVerdict accepted(String text) {
        return new OutputVerdict(text, null);
    }

    public static OutputVerdict rejected(ViolationKind kind) {
        return new OutputVerdict(null, kind);
    }

    public boolean isAccepted() {
        return violationKind == null;
    }

    /** 通ったときだけ使う。 */
    public String getText() {
        if (text == null) {
            throw new IllegalStateException("rejected output has no text");
        }
        return text;
    }

    public Optional<ViolationKind> getViolationKind() {
        return Optional.ofNullable(violationKind);
    }
}
