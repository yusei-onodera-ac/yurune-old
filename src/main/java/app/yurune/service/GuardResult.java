package app.yurune.service;

import app.yurune.domain.value.ToolRejectReason;

import java.util.Optional;

/**
 * ツール呼び出しの検証結果。拒否のときは、理由（文と、運用の記録用のコード）を持つ。
 */
public final class GuardResult {

    private static final GuardResult ACCEPTED = new GuardResult(true, "", null);

    private final boolean accepted;
    private final String rejectReason;
    private final ToolRejectReason rejectReasonCode;

    private GuardResult(boolean accepted, String rejectReason, ToolRejectReason rejectReasonCode) {
        this.accepted = accepted;
        this.rejectReason = rejectReason;
        this.rejectReasonCode = rejectReasonCode;
    }

    public static GuardResult accept() {
        return ACCEPTED;
    }

    /**
     * 理由の文だけで拒否する（コードを決められない、既存の呼び出し元のための形）。
     * 運用の記録では、理由のコードは OTHER になる。新しく書くところは、下のオーバーロードを使う。
     */
    public static GuardResult reject(String reason) {
        return new GuardResult(false, reason, ToolRejectReason.OTHER);
    }

    /** 理由のコードと、判断の記録用の文の、両方で拒否する。 */
    public static GuardResult reject(ToolRejectReason code, String reason) {
        return new GuardResult(false, reason, code);
    }

    public boolean isAccepted() {
        return accepted;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    /** 拒否の理由のコード（運用の記録 agent ログ用）。受理のときは呼ばない前提（空）。 */
    public Optional<ToolRejectReason> getRejectReasonCode() {
        return Optional.ofNullable(rejectReasonCode);
    }
}
