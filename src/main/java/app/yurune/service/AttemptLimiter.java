package app.yurune.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 失敗や試行の回数を、キーごとに、一定の期間だけ数えて、上限を超えたら止める。
 * パスワードの総当たりと、登録の作り放題を防ぐ。メモリ上のみで、サーバーごとに数える。
 * 数えるキーの数にも上限を置き、古いものから捨てる（でたらめなメールアドレスで、メモリを使い切らせないため）。
 */
public final class AttemptLimiter {

    private static final int MAX_TRACKED_KEYS = 10_000;

    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> attempts = new LinkedHashMap<>();

    public AttemptLimiter(int maxAttempts, Duration window, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.clock = clock;
    }

    /** 期間内の回数が上限に達していれば、true。 */
    public synchronized boolean isBlocked(String key) {
        Deque<Instant> times = attempts.get(key);
        if (times == null) {
            return false;
        }
        dropExpired(times);
        return times.size() >= maxAttempts;
    }

    public synchronized void record(String key) {
        Deque<Instant> times = attempts.remove(key);
        if (times == null) {
            times = new ArrayDeque<>();
        }
        dropExpired(times);
        times.addLast(clock.instant());
        attempts.put(key, times);
        evictOldestKeys();
    }

    public synchronized void reset(String key) {
        attempts.remove(key);
    }

    private void dropExpired(Deque<Instant> times) {
        Instant limit = clock.instant().minus(window);
        while (!times.isEmpty() && times.peekFirst().isBefore(limit)) {
            times.removeFirst();
        }
    }

    private void evictOldestKeys() {
        Iterator<String> oldest = attempts.keySet().iterator();
        while (attempts.size() > MAX_TRACKED_KEYS && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
    }
}
