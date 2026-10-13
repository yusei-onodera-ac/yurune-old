package app.yurune.service;

/**
 * 進んでいる夜がないときの例外。端末の終了と観測が同時に届くと、確認のあとに夜が終わることがあるため、
 * 呼び出し側が「すでに終わった」として扱えるよう、ほかの状態の誤りと区別する。
 */
public final class NoActiveNightException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public NoActiveNightException() {
        super("no active night");
    }
}
