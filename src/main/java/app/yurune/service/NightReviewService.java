package app.yurune.service;

import app.yurune.constant.GentlePolicy;
import app.yurune.constant.OptionLabelConstants;
import app.yurune.constant.ReviewTextConstants;
import app.yurune.domain.entity.Arm;
import app.yurune.domain.entity.Decision;
import app.yurune.domain.entity.NightRecord;
import app.yurune.domain.value.ArmKey;
import app.yurune.domain.value.ClockTime;
import app.yurune.domain.value.DecisionPhase;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.DisplayText;
import app.yurune.domain.value.MorningAnswer;
import app.yurune.domain.value.NightGoal;
import app.yurune.domain.value.NightRole;
import app.yurune.domain.value.PdcaState;
import app.yurune.domain.value.ReviewAction;
import app.yurune.domain.value.ReviewVerdict;
import app.yurune.domain.value.UserSettings;
import app.yurune.domain.value.WakeDate;
import app.yurune.repository.ArmRepository;
import app.yurune.repository.DecisionRepository;
import app.yurune.repository.NightRepository;
import app.yurune.repository.PdcaStateRepository;
import app.yurune.repository.StorageUnavailableException;

import java.time.Clock;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 夜ごとの確認（CHECK）と見直し（REVIEW）。夜の終わりと、朝の答えのあとに動く。判定と決めたことは、すべてコードで行う（LLM は使わない）。
 *
 * R1: 本検定（選択肢の判定と、よく使う・見送る・外す・受診のすすめ）は、週に 1 回（見直しの日）だけ行う。
 * 毎晩動くのは、次の 2 つだけ: (1) 選択肢の配分の集計の更新（OptionPicker が使う Arm の勝ち負け）、
 * (2) 戻す（ROLLBACK。直近 3 夜の連続判定）。見直しの日は、基準の最終夜から、GentlePolicy.REVIEW_EVERY_NIGHTS 夜ごと
 * （日付から決まるので、同じ日は同じ判定になる）。
 *
 * 夜のめやす（NightGoal）は、Supplier から、そのつど読む（設定で切り替えられるため）。切り替えたときは、
 * 古いめやすでの判定・配分が、新しいめやすに混ざらないよう、学習の状態をいったん作り直す（resetForGoalSwitch）。
 */
public final class NightReviewService {

    private static final String RULE_CHECK_FEW = "C1";
    private static final String RULE_CHECK_JUDGED = "C2";
    private static final String RULE_NIGHTLY_COUNT = "C3";
    private static final String RULE_REVIEW_PREFER = "R1";
    private static final String RULE_REVIEW_PARK = "R2";
    private static final String RULE_REVIEW_DROP = "R3";
    private static final String RULE_REVIEW_ROLLBACK = "R4";
    private static final String RULE_REVIEW_REFER = "R6";
    private static final String COUNTED_VERDICT = "counted";
    private static final String REFERRED_VERDICT = "refer";
    private static final String OBSERVED_RULE = "rule";
    private static final String OBSERVED_VERDICT = "verdict";
    private static final String OBSERVED_ACTION = "action";
    private static final String OBSERVED_KEY = "key";
    private static final String OBSERVED_GOAL = "goal";
    private static final List<Dimension> FACTORS = List.of(Dimension.SOUND, Dimension.FADE);

    private final NightRepository nightRepository;
    private final ArmRepository armRepository;
    private final PdcaStateRepository stateRepository;
    private final DecisionRepository decisionRepository;
    private final AuditLogger auditLogger;
    private final Clock clock;
    private final Supplier<UserSettings> settings;
    private final Supplier<NightGoal> goal;

    public NightReviewService(NightRepository nightRepository, ArmRepository armRepository,
            PdcaStateRepository stateRepository, DecisionRepository decisionRepository, AuditLogger auditLogger,
            Clock clock, Supplier<UserSettings> settings, Supplier<NightGoal> goal) {
        this.nightRepository = nightRepository;
        this.armRepository = armRepository;
        this.stateRepository = stateRepository;
        this.decisionRepository = decisionRepository;
        this.auditLogger = auditLogger;
        this.clock = clock;
        this.settings = settings;
        this.goal = goal;
    }

    /** 夜が終わったあとに呼ぶ。 */
    public void reviewEndedNight(WakeDate wakeDate) {
        reviewSafely(wakeDate);
    }

    /**
     * 朝の答えを確認したあとに呼ぶ。めやすが朝のようすのときだけ動く。
     * 寝つきと夜中のめやすは、夜が終わった時点で決まるので、朝には見直さない。
     */
    public void reviewAfterMorning(WakeDate wakeDate) {
        if (goal.get() == NightGoal.MORNING) {
            reviewSafely(wakeDate);
        }
    }

    /** 保存先が止まっていても、夜は止めない。次の夜に、もう一度行う。 */
    private void reviewSafely(WakeDate tonight) {
        try {
            review(tonight);
        } catch (StorageUnavailableException exception) {
            // 次の夜に、もう一度行う（規則の判断は、変わったときだけ残すため、二重にはならない）
        }
    }

    private void review(WakeDate tonight) {
        NightGoal currentGoal = goal.get();
        List<NightRecord> nights = nightRepository.findRecentNights(GentlePolicy.REVIEW_HISTORY_NIGHTS);
        PdcaState state = resetForGoalSwitchIfNeeded(currentGoal, stateRepository.findState());
        if (settings.get().isLearningEnabled()) {
            updateNightlyAggregation(tonight, nights, currentGoal);
            state = checkRollback(tonight, nights, currentGoal, state);
            if (isReviewDay(tonight, nights)) {
                state = weeklyReview(tonight, nights, currentGoal, state);
            }
            state = reviewReferral(tonight, nights, currentGoal, state);
        }
        stateRepository.saveState(state);
    }

    // ---- めやすの切り替え ----

    /**
     * めやすが前回と違うときは、学習の状態を作り直し、選択肢の配分（Arm の勝ち負け）も、未試行からやり直す。
     * そうしないと、古いめやすでの判定（よく使う・見送る・外す）や配分が、新しいめやすの判定に残り続けてしまう。
     * 初めての見直し（前回のめやすがまだ無い）は、切り替えではないので、ただ記録するだけ。
     */
    private PdcaState resetForGoalSwitchIfNeeded(NightGoal currentGoal, PdcaState state) {
        Optional<NightGoal> previous = state.findLastGoal();
        if (previous.isEmpty()) {
            return state.withLastGoal(currentGoal);
        }
        if (previous.get() == currentGoal) {
            return state;
        }
        resetArmAllocation();
        return state.resetForNewGoal(currentGoal);
    }

    /** 変える要因（音、音量の下げ方）の選択肢を、すべて未試行に戻す（Thompson の事前分布に近い状態から再出発する）。 */
    private void resetArmAllocation() {
        for (Dimension factor : FACTORS) {
            for (ArmKey key : FactorSequencer.nonStandardCandidatesOf(factor)) {
                armRepository.saveArm(Arm.untried(key));
            }
        }
    }

    // ---- 毎晩（規則 C3、R4） ----

    /** 今夜が、めやすの値のある変えた夜なら、近い標準の夜との差で、配分の集計に 1 回ぶんを足す。 */
    private void updateNightlyAggregation(WakeDate tonight, List<NightRecord> nights, NightGoal currentGoal) {
        Optional<NightRecord> record = nightRepository.findNight(tonight);
        if (record.isEmpty()) {
            return;
        }
        NightRecord night = record.get();
        Optional<ArmKey> key = NightMetricReader.findVariantKey(night);
        if (key.isEmpty() || !NightMetricReader.isValidFor(currentGoal, night)
                || alreadyRecorded(tonight, RULE_NIGHTLY_COUNT, key.get())) {
            return;
        }
        List<NightRecord> standards = PairedNightDiffs.standardNightsOf(currentGoal, nights);
        Optional<Double> diff = PairedNightDiffs.diffOf(currentGoal, night, standards);
        if (diff.isEmpty()) {
            return;
        }
        boolean win = diff.orElseThrow() > 0;
        Arm current = armRepository.findArm(key.get()).orElseGet(() -> Arm.untried(key.get()));
        armRepository.saveArm(current.withMetricResult(win));
        saveSilentDecision(tonight, RULE_NIGHTLY_COUNT, key.get(), currentGoal, COUNTED_VERDICT);
        auditLogger.pdca(RULE_NIGHTLY_COUNT, COUNTED_VERDICT, 1);
    }

    /** 今夜、変えた選択肢の直近 3 夜（近い標準の夜とのペアの差）が、すべて、最小の差以上わるいときだけ、戻す。 */
    private PdcaState checkRollback(WakeDate tonight, List<NightRecord> nights, NightGoal currentGoal, PdcaState state) {
        Optional<NightRecord> record = nightRepository.findNight(tonight);
        if (record.isEmpty()) {
            return state;
        }
        Optional<ArmKey> key = NightMetricReader.findVariantKey(record.get());
        if (key.isEmpty() || !NightMetricReader.isValidFor(currentGoal, record.get()) || state.isQuarantined(key.get(), tonight)) {
            return state;
        }
        List<NightRecord> standards = PairedNightDiffs.standardNightsOf(currentGoal, nights);
        List<Double> lastDiffs = recentDiffsOf(key.get(), nights, currentGoal, standards, GentlePolicy.ROLLBACK_NIGHTS);
        double minDiff = NightMetricReader.minDiffOf(currentGoal);
        boolean rolledBack = lastDiffs.size() == GentlePolicy.ROLLBACK_NIGHTS
                && lastDiffs.stream().allMatch(diff -> diff <= -minDiff);
        if (!rolledBack) {
            return state;
        }
        return rollBack(tonight, key.get(), currentGoal, lastDiffs.size(), state);
    }

    /** ある選択肢の、直近 limit 夜ぶんの差（近い標準の夜がない夜はスキップし、見つかるまでさかのぼる）。 */
    private List<Double> recentDiffsOf(ArmKey key, List<NightRecord> nights, NightGoal currentGoal,
            List<NightRecord> standards, int limit) {
        List<Double> diffs = new ArrayList<>();
        for (NightRecord night : nights) {
            if (diffs.size() >= limit) {
                break;
            }
            if (!isVariantOf(night, key) || !NightMetricReader.isValidFor(currentGoal, night)) {
                continue;
            }
            PairedNightDiffs.diffOf(currentGoal, night, standards).ifPresent(diffs::add);
        }
        return diffs;
    }

    private PdcaState rollBack(WakeDate tonight, ArmKey key, NightGoal currentGoal, int nightCount, PdcaState state) {
        PdcaState next = state.withQuarantine(key, tonight.plusDays(GentlePolicy.QUARANTINE_DAYS))
                .withRollback(key, tonight.plusDays(GentlePolicy.ROLLBACK_HOLD_NIGHTS));
        String chosen = "「" + labelOf(key) + "」は、いったん外すね。次の" + GentlePolicy.ROLLBACK_HOLD_NIGHTS + "夜は、標準のすごし方にするね。";
        String reason = "規則 " + RULE_REVIEW_ROLLBACK + "（変えた夜 " + nightCount + " 夜）。" + ReviewTextConstants.WORSE_STREAK_SENTENCE;
        saveReviewDecision(tonight, RULE_REVIEW_ROLLBACK, key, currentGoal, ReviewAction.ROLLBACK, chosen, reason, nightCount);
        return next;
    }

    // ---- 週に 1 回（規則 C1、C2、R1、R2、R3） ----

    /** 見直しの日（基準の最終夜から、REVIEW_EVERY_NIGHTS 夜ごと）。基準がまだ終わっていないときは、見直しの日にしない。 */
    private boolean isReviewDay(WakeDate tonight, List<NightRecord> nights) {
        Optional<WakeDate> baselineEnd = latestBaselineNight(nights);
        if (baselineEnd.isEmpty()) {
            return false;
        }
        int daysSince = baselineEnd.orElseThrow().daysUntil(tonight);
        return daysSince > 0 && daysSince % GentlePolicy.REVIEW_EVERY_NIGHTS == 0;
    }

    private static Optional<WakeDate> latestBaselineNight(List<NightRecord> nights) {
        return nights.stream()
                .filter(night -> NightMetricReader.hasRole(night, NightRole.BASELINE))
                .map(NightRecord::getWakeDate)
                .max(Comparator.naturalOrder());
    }

    /**
     * 週に 1 回の本検定。いま順番に試している要因（FactorSequencer）の選択肢だけを見る（R3）。
     * 有意水準は、同じ要因の選択肢の数 k で割る（Bonferroni）。
     *
     * 仮置きの検討（直す作業で試して、採用を見送ったもの）: Holm 法（段階的な多重比較補正）に替えると、
     * S1 の取りこぼしは減った（64.0%→67.0%）が、S2・S4・保留 HO4（どれも「効果なし／ドリフトだけ」の場面）の
     * 誤検出が、同時に増えた（S2 13.0%→16.0%、S4 18.5%→21.5%、HO4 6%→11%）。両側（見つける力と、誤って
     * 見つけない力）の原則に反するため、平らな Bonferroni に戻した。
     */
    private PdcaState weeklyReview(WakeDate tonight, List<NightRecord> nights, NightGoal currentGoal, PdcaState state) {
        Dimension factor = FactorSequencer.activeFactor(nights, state);
        List<ArmKey> keys = FactorSequencer.nonStandardCandidatesOf(factor);
        double alpha = GentlePolicy.SIGNIFICANCE_ALPHA / keys.size();
        double minDiff = NightMetricReader.minDiffOf(currentGoal);
        PdcaState next = state;
        for (ArmKey key : keys) {
            next = reviewOption(tonight, nights, currentGoal, key, minDiff, alpha, next);
        }
        return next;
    }

    private PdcaState reviewOption(WakeDate tonight, List<NightRecord> nights, NightGoal currentGoal, ArmKey key,
            double minDiff, double alpha, PdcaState state) {
        if (state.isQuarantined(key, tonight)) {
            return state;
        }
        List<Double> diffs = PairedNightDiffs.diffsFor(currentGoal, nights, key);
        ReviewVerdict verdict = VerdictJudge.judgePaired(diffs, minDiff, alpha);
        saveCheckDecision(tonight, key, currentGoal, verdict, diffs.size());
        boolean mostlyRejected = isMostlyRejected(key, nights, currentGoal);
        if (verdict == ReviewVerdict.WORSE || mostlyRejected) {
            return drop(tonight, key, currentGoal, verdict, mostlyRejected, diffs.size(), state);
        }
        return switch (verdict) {
            case BETTER -> maybePrefer(tonight, key, currentGoal, diffs.size(), state);
            case SIMILAR -> park(tonight, key, currentGoal, diffs.size(), state);
            case NEED_MORE, WORSE -> state;
        };
    }

    private void saveCheckDecision(WakeDate tonight, ArmKey key, NightGoal currentGoal, ReviewVerdict verdict,
            int nightCount) {
        if (alreadyRecorded(tonight, verdict == ReviewVerdict.NEED_MORE ? RULE_CHECK_FEW : RULE_CHECK_JUDGED, key)) {
            return;
        }
        String label = labelOf(key);
        String subject = ReviewTextConstants.subjectOf(currentGoal);
        String rule = verdict == ReviewVerdict.NEED_MORE ? RULE_CHECK_FEW : RULE_CHECK_JUDGED;
        String comparison = "規則 " + rule + "、比べた夜 " + nightCount + " 夜（時間の近い対照とのペア）";
        String reason = verdict == ReviewVerdict.NEED_MORE
                ? comparison + "。" + ReviewTextConstants.FEW_NIGHTS_SENTENCE
                : comparison + "。" + sentenceOf(currentGoal, verdict) + ReviewTextConstants.HEDGE_SENTENCE;
        decisionRepository.saveDecision(new Decision(tonight, now(), DecisionPhase.CHECK,
                Map.of(OBSERVED_RULE, rule, OBSERVED_VERDICT, verdict.asId(), OBSERVED_KEY, key.asDocumentId(),
                        OBSERVED_GOAL, currentGoal.asId()),
                new DisplayText("「" + label + "」を変えた夜の、" + subject + "の比べ"), new DisplayText(reason), true));
        auditLogger.pdca(rule, verdict.asId(), nightCount);
    }

    private String sentenceOf(NightGoal currentGoal, ReviewVerdict verdict) {
        return switch (verdict) {
            case BETTER -> ReviewTextConstants.betterSentenceOf(currentGoal);
            case WORSE -> ReviewTextConstants.worseSentenceOf(currentGoal);
            case SIMILAR, NEED_MORE -> ReviewTextConstants.SIMILAR_SENTENCE;
        };
    }

    private PdcaState drop(WakeDate tonight, ArmKey key, NightGoal currentGoal, ReviewVerdict verdict,
            boolean mostlyRejected, int nightCount, PdcaState state) {
        String chosen = "「" + labelOf(key) + "」は、" + GentlePolicy.QUARANTINE_DAYS + "日のあいだ、選ばないことにするね。";
        String why = verdict == ReviewVerdict.WORSE && !mostlyRejected
                ? ReviewTextConstants.worseSentenceOf(currentGoal)
                : ReviewTextConstants.NOT_FIT_MAJORITY_SENTENCE;
        String reason = "規則 " + RULE_REVIEW_DROP + "（比べた夜 " + nightCount + " 夜）。" + why;
        saveReviewDecision(tonight, RULE_REVIEW_DROP, key, currentGoal, ReviewAction.DROP, chosen, reason, nightCount);
        WakeDate until = tonight.plusDays(GentlePolicy.QUARANTINE_DAYS);
        // verdict が WORSE でないのに、ここへ来るのは、朝の答えだけが理由（mostlyRejected）のとき。
        // 統計的な判定（NEED_MORE・SIMILAR のまま）を、FactorSequencer.isJudged に「終わった」と数えさせないため、
        // 印を分ける（診断で見つけた、R3 と FactorSequencer の意図しない相互作用の修正）。
        boolean subjectiveOnly = verdict != ReviewVerdict.WORSE;
        return subjectiveOnly ? state.withSubjectiveQuarantine(key, until) : state.withQuarantine(key, until);
    }

    /** よく使う（R3）には、今週と、前回の見直し（1 週前）の両方で BETTER が要る（偶然の 1 回で決めないため）。 */
    private PdcaState maybePrefer(WakeDate tonight, ArmKey key, NightGoal currentGoal, int nightCount, PdcaState state) {
        if (state.isPreferred(key) || !wasBetterAtPreviousReview(key, tonight, currentGoal)) {
            return state;
        }
        String chosen = "「" + labelOf(key) + "」を、ときどき多めに選ぶね。";
        String reason = "規則 " + RULE_REVIEW_PREFER + "（比べた夜 " + nightCount + " 夜、2 回続けて見直し済み）。"
                + ReviewTextConstants.betterSentenceOf(currentGoal);
        saveReviewDecision(tonight, RULE_REVIEW_PREFER, key, currentGoal, ReviewAction.PREFER, chosen, reason, nightCount);
        return state.withPreferred(key);
    }

    private boolean wasBetterAtPreviousReview(ArmKey key, WakeDate tonight, NightGoal currentGoal) {
        WakeDate previousReview = tonight.minusDays(GentlePolicy.REVIEW_EVERY_NIGHTS);
        return decisionRepository.findDecisions(previousReview).stream()
                .filter(decision -> decision.getPhase() == DecisionPhase.CHECK)
                .filter(decision -> key.asDocumentId().equals(decision.getObserved().get(OBSERVED_KEY)))
                .filter(decision -> currentGoal.asId().equals(decision.getObserved().get(OBSERVED_GOAL)))
                .anyMatch(decision -> ReviewVerdict.BETTER.asId().equals(decision.getObserved().get(OBSERVED_VERDICT)));
    }

    private PdcaState park(WakeDate tonight, ArmKey key, NightGoal currentGoal, int nightCount, PdcaState state) {
        if (state.isParked(key)) {
            return state;
        }
        String chosen = "「" + labelOf(key) + "」は、今のところ選ぶ回数を減らすね。";
        String reason = "規則 " + RULE_REVIEW_PARK + "（比べた夜 " + nightCount + " 夜）。" + ReviewTextConstants.SIMILAR_SENTENCE;
        saveReviewDecision(tonight, RULE_REVIEW_PARK, key, currentGoal, ReviewAction.PARK, chosen, reason, nightCount);
        return state.withParked(key, tonight.plusDays(GentlePolicy.PARK_RETRY_DAYS));
    }

    /** 答えのあった変えた夜のうち、「合わなかった」が多数（半分より多く）。答えが無いときは、多数とは言わない。 */
    private boolean isMostlyRejected(ArmKey key, List<NightRecord> nights, NightGoal currentGoal) {
        List<MorningAnswer> answers = nights.stream()
                .filter(night -> NightMetricReader.isValidFor(currentGoal, night))
                .filter(night -> isVariantOf(night, key))
                .map(night -> night.findAnswer().orElse(null))
                .filter(answer -> answer != null)
                .toList();
        if (answers.isEmpty()) {
            return false;
        }
        long notFit = answers.stream().filter(answer -> answer == MorningAnswer.NOT_FIT).count();
        return notFit > answers.size() * GentlePolicy.MAJORITY_RATIO;
    }

    private void saveReviewDecision(WakeDate tonight, String rule, ArmKey key, NightGoal currentGoal, ReviewAction action,
            String chosen, String reason, int nightCount) {
        decisionRepository.saveDecision(new Decision(tonight, now(), DecisionPhase.REVIEW,
                Map.of(OBSERVED_RULE, rule, OBSERVED_ACTION, action.asId(), OBSERVED_KEY, key.asDocumentId(),
                        OBSERVED_GOAL, currentGoal.asId()),
                new DisplayText(chosen), new DisplayText(reason), true));
        auditLogger.pdca(rule, action.asId(), nightCount);
    }

    // ---- 受診のすすめ（規則 R6） ----

    /**
     * 「よく使う」選択肢が、8 週間以上見つからず、かつ、直近の対照夜の寝つきのめやすが、基準から、最小の差ほども
     * よくなっていないときだけ、状態へ記録する（画面に出すのは、ふりかえり「目標」の「いまのところ」だけ）。
     * 悪くなっていることそのものの検出は、ここでは行わない（ROLLBACK で扱う。受診のすすめと悪化の検出を分ける）。
     *
     * 仮置きの検討（直す作業で試して、採用を見送ったもの）: 「まだ判定は届いていないが、
     * どれかの選択肢の夜が、既に基準より minDiff 以上よい」ときも「よくなっている」とみなす案を試したところ、
     * 狙った数字（S1 の利用者への誤出）は減った（46.5%→27.5%）が、停滞そのものの検出も一緒に弱まり、
     * S5 本来の基準（受診のすすめが出た人 >= 80%）が、87.0%→69.5% で、満たせなくなった（効果がない場面でも、
     * 4 つの選択肢のどれかが、偶然 minDiff を超えやすいため）。両側の原則に反するため、元の条件に戻した。
     */
    private PdcaState reviewReferral(WakeDate tonight, List<NightRecord> nights, NightGoal currentGoal, PdcaState state) {
        if (isHidden(state, tonight) || isInCooldown(state, tonight) || !state.getPreferred().isEmpty()) {
            return state;
        }
        Optional<WakeDate> baselineEnd = latestBaselineNight(nights);
        if (baselineEnd.isEmpty() || baselineEnd.orElseThrow().daysUntil(tonight) < GentlePolicy.REFER_NO_PREFERRED_NIGHTS) {
            return state;
        }
        List<Double> baseline = scoresOf(nights, currentGoal, night -> NightMetricReader.hasRole(night, NightRole.BASELINE));
        if (baseline.size() < GentlePolicy.MIN_VALID_BASELINE_NIGHTS) {
            return state;
        }
        List<Double> recentControl = recentScoresOf(nights, currentGoal, GentlePolicy.REFER_RECENT_NIGHTS);
        if (recentControl.size() < GentlePolicy.MIN_NIGHTS_PER_GROUP) {
            return state;
        }
        double minDiff = NightMetricReader.minDiffOf(currentGoal);
        boolean notImproved = VerdictJudge.median(recentControl) < VerdictJudge.median(baseline) + minDiff;
        if (!notImproved) {
            return state;
        }
        String chosen = "受診のすすめの状態を、記録したよ。";
        String reason = "規則 " + RULE_REVIEW_REFER + "（対照夜 " + recentControl.size() + " 夜）。" + ReviewTextConstants.STAGNANT_SENTENCE;
        decisionRepository.saveDecision(new Decision(tonight, now(), DecisionPhase.REVIEW,
                Map.of(OBSERVED_RULE, RULE_REVIEW_REFER, OBSERVED_ACTION, ReviewAction.REFER.asId(),
                        OBSERVED_GOAL, currentGoal.asId()),
                new DisplayText(chosen), new DisplayText(reason), true));
        auditLogger.pdca(RULE_REVIEW_REFER, REFERRED_VERDICT, recentControl.size());
        return state.withReferral(tonight);
    }

    /** 直近の対照夜（標準のやり方で過ごした、基準のあとの夜）の、めやすの値。新しい順に、limit 夜ぶん。 */
    private List<Double> recentScoresOf(List<NightRecord> nights, NightGoal currentGoal, int limit) {
        return scoresOf(nights, currentGoal, night -> NightMetricReader.hasRole(night, NightRole.CONTROL)).stream()
                .limit(limit)
                .toList();
    }

    private static boolean isHidden(PdcaState state, WakeDate tonight) {
        return state.findReferHiddenUntil().filter(until -> !tonight.isAfter(until)).isPresent();
    }

    private static boolean isInCooldown(PdcaState state, WakeDate tonight) {
        return state.findReferredOn().filter(day -> day.daysUntil(tonight) < GentlePolicy.REFER_COOLDOWN_DAYS).isPresent();
    }

    // ---- 共通の道具 ----

    private static boolean isVariantOf(NightRecord night, ArmKey key) {
        return NightMetricReader.findVariantKey(night).filter(key::equals).isPresent();
    }

    private List<Double> scoresOf(List<NightRecord> nights, NightGoal currentGoal, Predicate<NightRecord> condition) {
        return nights.stream()
                .filter(night -> NightMetricReader.isValidFor(currentGoal, night))
                .filter(condition)
                .map(night -> NightMetricReader.goodnessOf(currentGoal, NightMetricReader.valueFor(currentGoal, night).orElseThrow()))
                .toList();
    }

    /** 夜ごとの集計（C3）を、二重に数えない印として、同じ規則・同じ選択肢の記録が、今夜にすでにあるか。 */
    private boolean alreadyRecorded(WakeDate tonight, String rule, ArmKey key) {
        return decisionRepository.findDecisions(tonight).stream()
                .anyMatch(decision -> rule.equals(decision.getObserved().get(OBSERVED_RULE))
                        && key.asDocumentId().equals(decision.getObserved().get(OBSERVED_KEY)));
    }

    /** 画面には出さない、集計だけの記録（CHECK。監査と、二重実行の防止に使う）。 */
    private void saveSilentDecision(WakeDate tonight, String rule, ArmKey key, NightGoal currentGoal, String verdict) {
        decisionRepository.saveDecision(new Decision(tonight, now(), DecisionPhase.CHECK,
                Map.of(OBSERVED_RULE, rule, OBSERVED_VERDICT, verdict, OBSERVED_KEY, key.asDocumentId(),
                        OBSERVED_GOAL, currentGoal.asId()),
                new DisplayText("集計を更新したよ。"), new DisplayText("規則 " + rule + "（近い標準の夜との差）。"), true));
    }

    private static String labelOf(ArmKey key) {
        return OptionLabelConstants.labelOf(key.getDimension(), key.getOptionName());
    }

    private ClockTime now() {
        return ClockTime.of(LocalTime.now(clock));
    }
}
