package app.yurune.service;

import app.yurune.domain.value.AgentSource;
import app.yurune.domain.value.FallbackReason;

import java.util.Optional;

/**
 * 夜のエージェントの、直前の {@code decide()} 呼び出しの経路（規則か、LLM か、LLM が使えず切り替えたか）を、
 * スレッドローカルで持つ（{@link TraceContext} と同じ作り方）。
 * {@link ResilientNightAgent} が、判断の直前に記録し、{@link NightSessionService} が、それを見て agent ログに残す。
 * decide() を経ない呼び出し（enforceSettings、endNight など）のために、記録が無いときの既定値も持つ。
 */
public final class AgentDecisionContext {

    private static final ThreadLocal<Entry> CURRENT = new ThreadLocal<>();

    private AgentDecisionContext() {
    }

    /** @param fallbackReason source が FALLBACK のときだけ渡す（それ以外は null） */
    public static void record(AgentSource source, FallbackReason fallbackReason, long latencyMillis) {
        CURRENT.set(new Entry(source, fallbackReason, latencyMillis));
    }

    public static Optional<Entry> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** 次の判断、または次のリクエストに、前の記録を持ち込まないため。 */
    public static void clear() {
        CURRENT.remove();
    }

    /** 1 回分の判断の経路。 */
    public static final class Entry {

        private final AgentSource source;
        private final FallbackReason fallbackReason;
        private final long latencyMillis;

        private Entry(AgentSource source, FallbackReason fallbackReason, long latencyMillis) {
            this.source = source;
            this.fallbackReason = fallbackReason;
            this.latencyMillis = latencyMillis;
        }

        /** decide() を経ていない呼び出し（enforceSettings、endNight など）が使う既定値。 */
        public static Entry rule() {
            return new Entry(AgentSource.RULE, null, 0);
        }

        public AgentSource getSource() {
            return source;
        }

        public Optional<FallbackReason> getFallbackReason() {
            return Optional.ofNullable(fallbackReason);
        }

        public long getLatencyMillis() {
            return latencyMillis;
        }
    }
}
