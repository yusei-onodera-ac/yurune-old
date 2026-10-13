package app.yurune.service;

import app.yurune.constant.GentlePolicy;
import app.yurune.constant.NightGoalConstants;
import app.yurune.constant.NoteSummaryConstants;
import app.yurune.constant.OptionLabelConstants;
import app.yurune.constant.ReviewTextConstants;
import app.yurune.domain.entity.NightRecord;
import app.yurune.domain.value.ArmKey;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.NightGoal;
import app.yurune.domain.value.PdcaState;
import app.yurune.domain.value.ReviewVerdict;
import app.yurune.domain.value.WakeDate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 「いまのところ」の文を、事実から作る（純粋な計算。時計は、今日の日付を引数で受け取る）。
 * 行の並びは、基準の間（標準の夜の中央値、または、比べの結果か、様子見）、受診のすすめ、の順。3 行を超えない。
 * 比べの判定は、夜の見直し（NightReviewService）と同じ規則（同じ窓、同じ判定）で行う。
 */
public final class NoteSummaryBuilder {

    private final NightGoal goal;

    public NoteSummaryBuilder(NightGoal goal) {
        this.goal = goal;
    }

    /**
     * @param newestFirst 起きた日の新しい順の夜（今夜を含む）
     */
    public NoteSummary build(WakeDate today, List<NightRecord> newestFirst, PdcaState state) {
        List<String> lines = new ArrayList<>();
        boolean pending = NightRoleAssigner.isBaselinePending(goal, newestFirst);
        List<NightRecord> standard = validStandardNights(newestFirst);
        if (pending) {
            lines.add(NoteSummaryConstants.BASELINE_LINE);
        }
        if (!standard.isEmpty()) {
            lines.add(standardLine(standard));
        }
        if (!pending) {
            lines.add(compareLine(newestFirst, state));
        }
        boolean referral = isReferralShown(today, state);
        if (referral) {
            lines.add(referralLine());
        }
        return new NoteSummary(lines, referral);
    }

    /** 「今は表示しない」が効いていない、受診のすすめの窓の中か（すすめた日から、クールダウンの日数のあいだ）。 */
    public static boolean isReferralShown(WakeDate today, PdcaState state) {
        Optional<WakeDate> referredOn = state.findReferredOn();
        if (referredOn.isEmpty()) {
            return false;
        }
        int daysSince = referredOn.get().daysUntil(today);
        if (daysSince < 0 || daysSince >= GentlePolicy.REFER_COOLDOWN_DAYS) {
            return false;
        }
        return state.findReferHiddenUntil().filter(until -> !today.isAfter(until)).isEmpty();
    }

    private String standardLine(List<NightRecord> standard) {
        List<Double> values = standard.stream()
                .map(night -> NightMetricReader.valueFor(goal, night).orElseThrow())
                .toList();
        long median = Math.round(VerdictJudge.median(values));
        String count = NoteSummaryConstants.NIGHT_COUNT_HEAD + standard.size() + NoteSummaryConstants.NIGHT_COUNT_TAIL;
        return switch (goal) {
            case ONSET -> NoteSummaryConstants.STANDARD_ONSET_HEAD + median + NoteSummaryConstants.STANDARD_ONSET_UNIT + count;
            case WAKE -> NoteSummaryConstants.STANDARD_WAKE_HEAD + median + NoteSummaryConstants.STANDARD_WAKE_UNIT + count;
            // 朝のようすは、画面に点数を出さない。集まった夜数だけを言う
            case MORNING -> NoteSummaryConstants.STANDARD_MORNING_HEAD + standard.size() + NoteSummaryConstants.STANDARD_MORNING_TAIL;
        };
    }

    /**
     * 比べの一行。いま順番に試している要因（FactorSequencer。R3）の選択肢だけを見る（見直しと同じ対象）。
     * よい方向に寄った選択肢があれば、それを言う。なければ、わるい方向。どちらもなければ、様子見。
     * 夜の見直し（NightReviewService）と、同じ比べ方（近い標準の夜とのペアの差。PairedNightDiffs、VerdictJudge.judgePaired）で行う。
     */
    private String compareLine(List<NightRecord> newestFirst, PdcaState state) {
        Dimension factor = FactorSequencer.activeFactor(newestFirst, state);
        List<ArmKey> keys = FactorSequencer.nonStandardCandidatesOf(factor);
        double alpha = GentlePolicy.SIGNIFICANCE_ALPHA / keys.size();
        double minDiff = NightMetricReader.minDiffOf(goal);
        Finding better = null;
        Finding worse = null;
        for (ArmKey key : keys) {
            List<Double> diffs = PairedNightDiffs.diffsFor(goal, newestFirst, key);
            ReviewVerdict verdict = VerdictJudge.judgePaired(diffs, minDiff, alpha);
            if (verdict == ReviewVerdict.BETTER) {
                better = mostNights(better, new Finding(key, verdict, diffs.size()));
            } else if (verdict == ReviewVerdict.WORSE) {
                worse = mostNights(worse, new Finding(key, verdict, diffs.size()));
            }
        }
        Finding chosen = better != null ? better : worse;
        if (chosen == null) {
            return NoteSummaryConstants.WATCHING_LINE;
        }
        String phrase = chosen.verdict() == ReviewVerdict.BETTER
                ? NightGoalConstants.betterPhraseOf(goal) : NightGoalConstants.worsePhraseOf(goal);
        String label = OptionLabelConstants.labelOf(chosen.key().getDimension(), chosen.key().getOptionName());
        return NoteSummaryConstants.COMPARE_HEAD + label + NoteSummaryConstants.COMPARE_LABEL_TAIL + phrase
                + NoteSummaryConstants.NIGHT_COUNT_HEAD + chosen.nights() + NoteSummaryConstants.NIGHT_COUNT_TAIL
                + NoteSummaryConstants.HEDGE_LINE;
    }

    /**
     * 受診のすすめの文。悪化そのものを検出しての受診のすすめはしない（悪化は、戻す（ROLLBACK）で扱う）。
     * 「見つけたものが長くない」という、停滞の言い方だけを使う。
     */
    private String referralLine() {
        return NoteSummaryConstants.REFER_STAGNANT_LINE + ReviewTextConstants.REFERRAL_SENTENCE;
    }

    private List<NightRecord> validStandardNights(List<NightRecord> newestFirst) {
        return newestFirst.stream()
                .filter(night -> NightMetricReader.isValidFor(goal, night))
                .filter(NightMetricReader::isStandardNight)
                .toList();
    }

    private static Finding mostNights(Finding current, Finding candidate) {
        if (current == null || candidate.nights() > current.nights()) {
            return candidate;
        }
        return current;
    }

    /** 比べの結果の 1 つ。どの選択肢か、どちらの方向か、変えた夜の数。 */
    private record Finding(ArmKey key, ReviewVerdict verdict, int nights) {
    }
}
