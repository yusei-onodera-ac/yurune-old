package app.yurune.service;

import app.yurune.constant.LogConstants;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 1 つのリクエストを、入力の検査、LLM、ツール、出力の検査の記録まで、1 本につなぐトレース ID を持つ。
 * Cloud Run が付けるヘッダー（X-Cloud-Trace-Context）があればそれを使い、なければ作る。
 * リクエストを処理している間だけ、そのスレッドで読める。
 */
public final class TraceContext {

    private static final Pattern TRACE_ID = Pattern.compile("[0-9a-f]{" + LogConstants.TRACE_ID_HEX_CHARS + "}");
    private static final int BYTES = LogConstants.TRACE_ID_HEX_CHARS / 2;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TraceContext() {
    }

    /** ヘッダー（TRACE_ID/SPAN_ID;o=1）から、トレース ID を取り出す。形が正しくなければ、新しく作る。 */
    public static String resolve(String headerValue) {
        if (headerValue != null) {
            String candidate = headerValue.split("/", 2)[0].toLowerCase(Locale.ROOT);
            if (TRACE_ID.matcher(candidate).matches()) {
                return candidate;
            }
        }
        byte[] bytes = new byte[BYTES];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static void begin(String traceId) {
        CURRENT.set(traceId);
    }

    public static Optional<String> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static void end() {
        CURRENT.remove();
    }
}
