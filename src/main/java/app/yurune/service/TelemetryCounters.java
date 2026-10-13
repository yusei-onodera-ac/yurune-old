package app.yurune.service;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * プロセス内の、名前付きの数え上げ。スレッドセーフ。個人の ID は、名前に入れない。
 * {@link GuardStatistics} が使う数字と、/ready（O2）や、制御の集計（A3）が使う数字を、1 つに持つ。
 */
public final class TelemetryCounters {

    private final Map<String, LongAdder> counters = new ConcurrentHashMap<>();

    public void increment(String name) {
        counters.computeIfAbsent(name, key -> new LongAdder()).increment();
    }

    public long get(String name) {
        LongAdder adder = counters.get(name);
        return adder == null ? 0L : adder.sum();
    }

    /** 名前の順に並べた、いまの数。 */
    public Map<String, Long> snapshot() {
        Map<String, Long> copy = new TreeMap<>();
        counters.forEach((name, adder) -> copy.put(name, adder.sum()));
        return copy;
    }
}
