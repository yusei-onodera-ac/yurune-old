package app.yurune.service;

/**
 * 実行を試みたツール呼び出し 1 件（名前と、受け入れられたか）。端末が、画面や音に反映するために使う。
 */
public final class AppliedCall {

    private final String toolId;
    private final boolean accepted;

    public AppliedCall(String toolId, boolean accepted) {
        this.toolId = toolId;
        this.accepted = accepted;
    }

    public String getToolId() {
        return toolId;
    }

    public boolean isAccepted() {
        return accepted;
    }
}
