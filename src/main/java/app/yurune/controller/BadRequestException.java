package app.yurune.controller;

/**
 * 画面から届いた値が正しくないときの例外。Servlet が 400 にして返す。
 */
public final class BadRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }

    public BadRequestException(String message) {
        super(message);
    }
}
