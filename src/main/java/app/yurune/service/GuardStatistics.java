package app.yurune.service;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 守りの実測の数字（入力で止めた、出力で差し替えた、使えなかった、の件数）。種類ごとに数えるだけで、中身は持たない。
 * アプリが動き出してからの、このサーバーの分。運用の記録（構造化ログ）と並べて、守りが働いていることを示す。
 *
 * 数え上げそのものは、{@link TelemetryCounters}に持たせ、/ready や制御の集計と共有する。
 * snapshot() が返す項目名と値の意味は、これまでと同じ（既存の呼び出し元・テストを壊さない）。
 */
public final class GuardStatistics implements Consumer<GuardEvent> {

    private final TelemetryCounters counters;

    public GuardStatistics() {
        this(new TelemetryCounters());
    }

    /** @param counters 全員で共有する数え上げ（A4）。AppComponents が、/ready などと共有するために渡す */
    public GuardStatistics(TelemetryCounters counters) {
        this.counters = counters;
    }

    @Override
    public void accept(GuardEvent event) {
        counters.increment("conversations");
        counters.increment("input." + event.getInputOutcome());
        if (!event.getMaskedCounts().isEmpty()) {
            counters.increment("input.masked");
        }
        if (event.wasInputTruncated()) {
            counters.increment("input.truncated");
        }
        counters.increment("reply." + event.getSource().asId());
        event.findViolationKind().ifPresent(kind -> counters.increment("output.replaced." + kind));
        event.findDownReason().ifPresent(reason -> counters.increment("down." + reason));
    }

    /** 名前の順に並べた、いまの数。 */
    public Map<String, Long> snapshot() {
        return counters.snapshot();
    }
}
