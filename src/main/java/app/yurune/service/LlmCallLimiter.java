package app.yurune.service;

import app.yurune.constant.ObservabilityConstants;
import app.yurune.domain.value.ControlState;
import app.yurune.domain.value.DegradationKind;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

/**
 * LLM を呼ぶ頻度と回数を、サーバー側で制限する。端末の申告に左右されない。
 * 最小の間隔より早い呼び出しと、1 日の上限を超える呼び出しは、断る（請求の高騰と、サービス停止を防ぐ）。
 * 状態はメモリ上で、1 人分。保存先ができたら、ユーザーごとの状態に移す。
 */
public final class LlmCallLimiter {

    /** 日次の上限による拒否を、運用の記録で区別する名前。間隔による拒否は、purpose（NIGHT/TALK）を使う。 */
    private static final ControlState REASON_DAILY = ControlState.DAILY;

    private final Duration minInterval;
    private final int dailyCap;
    private final Clock clock;
    private final ControlState purpose;
    private final TelemetryCounters counters;
    private final AuditLogger auditLogger;
    private final Map<ControlState, Integer> rejectedInWindow = new EnumMap<>(ControlState.class);

    private Instant lastCallAt;
    private LocalDate countedDay;
    private int callsToday;
    private Instant windowStartedAt;

    public LlmCallLimiter(int minIntervalSec, int dailyCap, Clock clock) {
        this(minIntervalSec, dailyCap, clock, null, null, null);
    }

    /**
     * @param purpose 間隔での拒否を数えるときの名前（NIGHT／TALK）。A3 の集計をしないときは null
     * @param counters 件数の数え上げ（A4）。集計をしないときは null
     * @param auditLogger 拒否の総数を、60 秒に 1 回、control ログに出す先（A3）。集計をしないときは null
     */
    public LlmCallLimiter(int minIntervalSec, int dailyCap, Clock clock, ControlState purpose,
            TelemetryCounters counters, AuditLogger auditLogger) {
        this.minInterval = Duration.ofSeconds(minIntervalSec);
        this.dailyCap = dailyCap;
        this.clock = clock;
        this.purpose = purpose;
        this.counters = counters;
        this.auditLogger = auditLogger;
    }

    /** 呼んでよいときは、呼んだものとして数えて true。断るときは、数えずに false。 */
    public synchronized boolean tryAcquire() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(countedDay)) {
            countedDay = today;
            callsToday = 0;
        }
        if (callsToday >= dailyCap) {
            recordRejection(REASON_DAILY, now);
            return false;
        }
        if (lastCallAt != null && now.isBefore(lastCallAt.plus(minInterval))) {
            recordRejection(purpose == null ? REASON_DAILY : purpose, now);
            return false;
        }
        lastCallAt = now;
        callsToday++;
        return true;
    }

    /**
     * 拒否を数える。A4 の件数は毎回。A3 の control ログは、洪水にしないため、60 秒分をまとめて出す
     * （次の拒否のときに、窓が過ぎていたら、まとめて出す。厳密な定時ではない、簡単な近似）。
     */
    private void recordRejection(ControlState bucket, Instant now) {
        if (counters != null) {
            counters.increment("llm.limiter.rejected." + bucket.asId());
        }
        if (auditLogger == null) {
            return;
        }
        if (windowStartedAt == null) {
            windowStartedAt = now;
        }
        rejectedInWindow.merge(bucket, 1, Integer::sum);
        if (!now.isBefore(windowStartedAt.plusSeconds(ObservabilityConstants.RATE_LIMIT_AGGREGATION_SEC))) {
            flushRejections();
        }
    }

    private void flushRejections() {
        rejectedInWindow.forEach((bucket, count) ->
                auditLogger.control(DegradationKind.LLM_RATE_LIMIT, bucket, (long) count));
        rejectedInWindow.clear();
        windowStartedAt = null;
    }
}
