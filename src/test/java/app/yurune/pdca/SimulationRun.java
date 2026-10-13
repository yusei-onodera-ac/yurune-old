package app.yurune.pdca;

import app.yurune.config.AppConfig;
import app.yurune.constant.GentlePolicy;
import app.yurune.domain.entity.Decision;
import app.yurune.domain.entity.NightPlan;
import app.yurune.domain.entity.NightRecord;
import app.yurune.domain.entity.NightSession;
import app.yurune.domain.value.ArmKey;
import app.yurune.domain.value.DecisionPhase;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.EndReason;
import app.yurune.domain.value.NightMode;
import app.yurune.domain.value.NightGoal;
import app.yurune.domain.value.NightRole;
import app.yurune.domain.value.OptionName;
import app.yurune.domain.value.PdcaState;
import app.yurune.domain.value.ReviewVerdict;
import app.yurune.domain.value.UserSettings;
import app.yurune.domain.value.WakeDate;
import app.yurune.repository.InMemoryArmRepository;
import app.yurune.repository.InMemoryDecisionRepository;
import app.yurune.repository.InMemoryNightRepository;
import app.yurune.repository.InMemoryNightSessionRepository;
import app.yurune.repository.InMemoryPdcaStateRepository;
import app.yurune.repository.InMemorySleepRecordRepository;
import app.yurune.service.AuditLogger;
import app.yurune.service.BetaSampler;
import app.yurune.service.ForbiddenDomainFilter;
import app.yurune.service.LearningService;
import app.yurune.service.MorningCheckService;
import app.yurune.service.MutableClock;
import app.yurune.service.NightMetricReader;
import app.yurune.service.NightObservationResult;
import app.yurune.service.NightReviewService;
import app.yurune.service.NightSessionService;
import app.yurune.service.OutputGuard;
import app.yurune.service.PairedNightDiffs;
import app.yurune.service.PiiMasker;
import app.yurune.service.PlanService;
import app.yurune.service.PromptSecrets;
import app.yurune.service.RecordService;
import app.yurune.service.RuleBasedNightAgent;
import app.yurune.service.ThompsonOptionSelector;
import app.yurune.service.ToolGuard;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 合成の利用者 1 人を、実際のサービス（計画、夜の制御、朝の答え、確認と見直し）で、日数ぶん回す。
 * 時計は、夜の 22:10 と、翌朝の 06:40 へ進める。端末の反応は observe、会話への切り替えは changeMode という、アプリの入口を通す。
 * 夜の終わりは、無反応の基準の時間が過ぎたあとの observe で起こす（アプリが自分で終えるのと同じ道）。
 * 寝つきの値は SimulatedSleeper が決め、最後の反応の時刻として届ける（アプリは、寝つきを直接は知らない）。
 */
final class SimulationRun {

    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
    private static final LocalDate FIRST_DAY = LocalDate.of(2026, 10, 1);
    private static final LocalTime NIGHT_START = LocalTime.of(22, 10);
    private static final LocalTime MORNING_TIME = LocalTime.of(6, 40);
    private static final int NO_REACTION_MIN = 15;
    private static final int VOLUME_CAP_PCT = 60;
    private static final int OBSERVATION_INTERVAL_SEC = 30;
    /** 利用者が話す（端末に触れる）間隔。仮置き。寝つくまでの反応の回数を抑えるため、5 分にする。 */
    private static final int REACTION_EVERY_MIN = 5;
    /** 会話へ切り替える時刻（夜の開始からの分）。夜の始めに切り替える想定（仮置き）。 */
    private static final int CHANGE_AT_MIN = 1;
    private static final int SECONDS_PER_MINUTE = 60;
    private static final int REPORTED_NOW_SEC = 0;
    private static final Pattern COUNT_PATTERN = Pattern.compile("比べた夜 (\\d+) 夜");
    private static final String KEY_RULE = "rule";
    private static final String KEY_VERDICT = "verdict";
    private static final String KEY_KEY = "key";
    private static final String RULE_CHECK_FEW = "C1";
    private static final String RULE_CHECK_JUDGED = "C2";
    /** 確認の理由文から、比べた夜数が読めなかったときの印（診断の履歴には、読めた値だけを使う）。 */
    private static final int NO_PAIRED_NIGHTS_PARSED = -1;

    private final MutableClock clock;
    private final InMemoryNightRepository nights;
    private final InMemoryPdcaStateRepository states;
    private final InMemoryDecisionRepository decisions;
    private final PlanService planService;
    private final NightSessionService sessionService;
    private final MorningCheckService morningService;

    SimulationRun() {
        Instant first = ZonedDateTime.of(FIRST_DAY, NIGHT_START, ZONE).toInstant();
        this.clock = new MutableClock(first.toString(), ZONE.getId());
        this.nights = new InMemoryNightRepository();
        this.states = new InMemoryPdcaStateRepository();
        this.decisions = new InMemoryDecisionRepository();
        InMemoryArmRepository arms = new InMemoryArmRepository();
        AppConfig config = new AppConfig(NO_REACTION_MIN, VOLUME_CAP_PCT, OBSERVATION_INTERVAL_SEC);
        NightReviewService review = new NightReviewService(nights, arms, states, decisions,
                new AuditLogger(clock, null, line -> { }), clock, UserSettings::defaults,
                () -> NightGoal.DEFAULT);
        this.planService = new PlanService(nights, new ThompsonOptionSelector(arms, new BetaSampler()), states, clock);
        OutputGuard outputGuard = new OutputGuard(new ForbiddenDomainFilter(), new PiiMasker(), new PromptSecrets());
        this.sessionService = new NightSessionService(new InMemoryNightSessionRepository(), nights, decisions,
                new RuleBasedNightAgent(config), new ToolGuard(config, outputGuard), config, clock,
                UserSettings::defaults, review::reviewEndedNight);
        this.morningService = new MorningCheckService(nights, decisions, new LearningService(arms),
                new RecordService(new InMemorySleepRecordRepository(), clock), clock, review::reviewAfterMorning);
    }

    /** 利用者 1 人を、days 夜ぶん回し、結果を返す。 */
    PersonOutcome run(SimulatedSleeper sleeper, int days) {
        PersonOutcome outcome = new PersonOutcome();
        for (int day = 0; day < days; day++) {
            playNight(day, sleeper, outcome);
        }
        summarize(outcome);
        return outcome;
    }

    /**
     * 診断のための、読み取り専用の窓。走らせ終えたあとの、最終的な夜の記録をそのまま返す。
     * 判定のロジックは呼ばない・変えない（PairedNightDiffs など、既存の公開ユーティリティに、そのまま渡すためだけに使う）。
     */
    List<NightRecord> finalNights() {
        return nights.findRecentNights(GentlePolicy.REVIEW_HISTORY_NIGHTS);
    }

    /** 診断のための、読み取り専用の窓。走らせ終えたあとの、最終的な PdcaState（隔離・見送り・よく使う・受診の記録）。 */
    PdcaState finalState() {
        return states.findState();
    }

    private void playNight(int day, SimulatedSleeper sleeper, PersonOutcome outcome) {
        moveTo(nightStartOf(day));
        NightPlan plan = planService.proposeTonightPlan();
        NightSession session = sessionService.startNight(plan, NightMode.SOUND);
        Instant start = clock.instant();
        int onsetMin = sleeper.onsetMinutes(session.getWakeDate(), day, plan);
        if (sleeper.changesModeTonight()) {
            moveTo(start.plus(Duration.ofMinutes(CHANGE_AT_MIN)));
            sessionService.changeMode(NightMode.TALK);
        }
        touchUntil(start, onsetMin);
        if (sleeper.endsNightThemselvesTonight()) {
            sessionService.endNight(EndReason.USER, 0);
        } else {
            moveTo(start.plus(Duration.ofMinutes(onsetMin + NO_REACTION_MIN)));
            NightObservationResult result = sessionService.observe(NO_REACTION_MIN * SECONDS_PER_MINUTE);
            if (!result.isEnded()) {
                throw new IllegalStateException("the night did not end on " + session.getWakeDate().asIsoText());
            }
        }
        moveTo(morningOf(day));
        morningService.completeMorning(sleeper.morningInputFor(onsetMin));
        collectNight(day, session.getWakeDate(), outcome);
    }

    /** 寝つくまで、一定の間隔で端末に触れる。最後の反応は、寝つきの時刻になる。 */
    private void touchUntil(Instant start, int onsetMin) {
        for (int minute = REACTION_EVERY_MIN; minute < onsetMin; minute += REACTION_EVERY_MIN) {
            moveTo(start.plus(Duration.ofMinutes(minute)));
            sessionService.observe(REPORTED_NOW_SEC);
        }
        moveTo(start.plus(Duration.ofMinutes(onsetMin)));
        sessionService.observe(REPORTED_NOW_SEC);
    }

    /** その夜の結果を、判断の記録と、進んだ状態から集める。 */
    private void collectNight(int day, WakeDate wakeDate, PersonOutcome outcome) {
        for (Decision decision : decisions.findDecisions(wakeDate)) {
            if (decision.getPhase() == DecisionPhase.CHECK) {
                collectCheck(day, wakeDate, decision, outcome);
            } else if (decision.getPhase() == DecisionPhase.REVIEW) {
                collectReview(day, decision, outcome);
            }
        }
        states.findState().getQuarantine().keySet().forEach(key -> outcome.markQuarantined(key, day));
    }

    /**
     * 見直しの判断。R6 は、M2r で「あまり役に立っていない（停滞）」の 1 種類だけになった（悪化は R4 で扱う）。
     * 診断のため、どの選択肢に対して、どの規則（R1〜R4）が働いたかも、選択肢ごとに記録する
     * （R6 は選択肢に紐づかないので、key が無いことがある）。
     */
    private static void collectReview(int day, Decision decision, PersonOutcome outcome) {
        String rule = decision.getObserved().get(KEY_RULE);
        outcome.markRule(rule, day);
        String keyId = decision.getObserved().get(KEY_KEY);
        if (keyId != null) {
            outcome.addKeyAction(parseKey(keyId), day, rule);
        }
    }

    /**
     * 週に 1 回の本検定（C1、C2）だけを集める。毎晩の集計（C3）は、判定のつじつま合わせには数えない。
     * 選択肢は、決定の observed["key"] から読む（今夜の役割ではなく、見直した選択肢そのものを見るため。
     * 見直しの日は、今夜が対照夜でも、ほかの選択肢を見直すことがある）。
     * 診断のため、選択肢ごとの判定の履歴（日・判定・比べた夜数）も、合わせて記録する。
     */
    private static void collectCheck(int day, WakeDate wakeDate, Decision decision, PersonOutcome outcome) {
        String rule = decision.getObserved().get(KEY_RULE);
        if (!RULE_CHECK_FEW.equals(rule) && !RULE_CHECK_JUDGED.equals(rule)) {
            return;
        }
        String keyId = decision.getObserved().get(KEY_KEY);
        if (keyId == null) {
            return;
        }
        ArmKey key = parseKey(keyId);
        String verdict = decision.getObserved().get(KEY_VERDICT);
        outcome.markVerdict(verdict, key, day);
        Matcher matcher = COUNT_PATTERN.matcher(decision.getReason().asText());
        int pairedNights = matcher.find() ? Integer.parseInt(matcher.group(1)) : NO_PAIRED_NIGHTS_PARSED;
        if (pairedNights != NO_PAIRED_NIGHTS_PARSED) {
            outcome.addCheck(new CheckNote(wakeDate, key, pairedNights));
        }
        outcome.addKeyCheck(key, day, verdict, pairedNights);
    }

    private static ArmKey parseKey(String documentId) {
        String[] parts = documentId.split("__", 2);
        return new ArmKey(Dimension.fromText(parts[0]), new OptionName(parts[1]));
    }

    /** 全体の集計。判定に使った数（確認の欄の夜数）が、規則どおりに数えた値と合うかも、確かめる。 */
    private void summarize(PersonOutcome outcome) {
        List<NightRecord> all = nights.findRecentNights(GentlePolicy.REVIEW_HISTORY_NIGHTS);
        all.forEach(outcome::countNight);
        for (CheckNote note : outcome.checks()) {
            if (!matchesCountedNights(all, note)) {
                outcome.addMismatch();
            }
        }
    }

    /**
     * 規則どおりの、近い対照とペアにできた夜の数（R2）を、記録から数え直す。
     * 見直しは、その夜までに読めた記録だけを使うので、確かめる側も、その夜より後の記録は入れない
     * （あとから起きる夜の対照が、先に行った見直しのペアに混ざらないようにする）。
     */
    private static boolean matchesCountedNights(List<NightRecord> all, CheckNote note) {
        List<NightRecord> upToThatNight = all.stream().filter(night -> !night.getWakeDate().isAfter(note.tonight())).toList();
        int recomputed = PairedNightDiffs.diffsFor(NightGoal.DEFAULT, upToThatNight, note.key()).size();
        return recomputed == note.reportedPairedNights();
    }

    private void moveTo(Instant target) {
        Duration gap = Duration.between(clock.instant(), target);
        if (gap.isNegative()) {
            throw new IllegalStateException("the clock must move forward");
        }
        clock.advance(gap);
    }

    private static Instant nightStartOf(int day) {
        return ZonedDateTime.of(FIRST_DAY.plusDays(day), NIGHT_START, ZONE).toInstant();
    }

    private static Instant morningOf(int day) {
        return ZonedDateTime.of(FIRST_DAY.plusDays(day + 1), MORNING_TIME, ZONE).toInstant();
    }

    /** 確認の 1 回ぶん（ペアにできた夜の数を、報告の文から読み戻したもの）。 */
    record CheckNote(WakeDate tonight, ArmKey key, int reportedPairedNights) {
    }

    /**
     * 診断用: ある選択肢に対する、確認（CHECK）1 回ぶんの判定の履歴。
     * day は夜の番号（0 始まり）。pairedNights が NO_PAIRED_NIGHTS_PARSED のときは、理由文から夜数が読めなかった回（まれ）。
     */
    record KeyCheck(int day, String verdict, int pairedNights) {
    }

    /** 診断用: ある選択肢に対する、見直し（REVIEW）1 回ぶんの行動（規則 R1〜R4 のいずれか）の履歴。 */
    record KeyAction(int day, String rule) {
    }

    /** 利用者 1 人の結果。日は、夜の番号（0 始まり）。見つからないものは NO_DAY。 */
    static final class PersonOutcome {

        static final int NO_DAY = Integer.MAX_VALUE;

        private final Map<ArmKey, Integer> firstBetter = new HashMap<>();
        private final Map<ArmKey, Integer> firstQuarantine = new HashMap<>();
        private final Map<String, Integer> firstRule = new HashMap<>();
        private final Map<String, Integer> ruleCounts = new HashMap<>();
        private final Map<String, Integer> verdictCounts = new HashMap<>();
        private final Map<ArmKey, Integer> variantNights = new HashMap<>();
        private final List<CheckNote> checks = new ArrayList<>();
        private final Map<ArmKey, List<KeyCheck>> keyChecks = new HashMap<>();
        private final Map<ArmKey, List<KeyAction>> keyActions = new HashMap<>();
        private int endedNights;
        private int missingAnswers;
        private int answeredNights;
        private int fitAnswers;
        private int userChangedNights;
        private int excludedVariantNights;
        private int baselineNights;
        private int validBaselineNights;
        private int controlNights;
        private int checkMismatches;

        void markVerdict(String verdict, ArmKey key, int day) {
            verdictCounts.merge(verdict, 1, Integer::sum);
            if (ReviewVerdict.BETTER.asId().equals(verdict)) {
                firstBetter.putIfAbsent(key, day);
            }
        }

        void markRule(String rule, int day) {
            firstRule.putIfAbsent(rule, day);
            ruleCounts.merge(rule, 1, Integer::sum);
        }

        void markQuarantined(ArmKey key, int day) {
            firstQuarantine.putIfAbsent(key, day);
        }

        void addCheck(CheckNote note) {
            checks.add(note);
        }

        void addMismatch() {
            checkMismatches++;
        }

        /** 診断用: ある選択肢に対する、確認（CHECK）の履歴を 1 件足す。 */
        void addKeyCheck(ArmKey key, int day, String verdict, int pairedNights) {
            keyChecks.computeIfAbsent(key, ignoredKey -> new ArrayList<>()).add(new KeyCheck(day, verdict, pairedNights));
        }

        /** 診断用: ある選択肢に対する、見直し（REVIEW）の行動を 1 件足す。 */
        void addKeyAction(ArmKey key, int day, String rule) {
            keyActions.computeIfAbsent(key, ignoredKey -> new ArrayList<>()).add(new KeyAction(day, rule));
        }

        void countNight(NightRecord record) {
            if (record.findEndReason().isPresent()) {
                endedNights++;
            }
            if (record.findAnswer().isEmpty()) {
                missingAnswers++;
            }
            record.findAnswer().ifPresent(answer -> countAnswer(answer.isFit()));
            if (record.isUserChanged()) {
                userChangedNights++;
            }
            NightMetricReader.findVariantKey(record).ifPresent(key -> countVariant(key, record));
            if (NightMetricReader.hasRole(record, NightRole.BASELINE)) {
                baselineNights++;
                if (NightMetricReader.isValidFor(NightGoal.DEFAULT, record)) {
                    validBaselineNights++;
                }
            }
            if (NightMetricReader.hasRole(record, NightRole.CONTROL)) {
                controlNights++;
            }
        }

        private void countAnswer(boolean fit) {
            answeredNights++;
            if (fit) {
                fitAnswers++;
            }
        }

        private void countVariant(ArmKey key, NightRecord record) {
            variantNights.merge(key, 1, Integer::sum);
            if (record.isUserChanged()) {
                excludedVariantNights++;
            }
        }

        List<CheckNote> checks() {
            return checks;
        }

        /** 診断用: ある選択肢の、確認（CHECK）の履歴（夜の番号順）。無ければ空リスト。 */
        List<KeyCheck> keyChecksOf(ArmKey key) {
            return keyChecks.getOrDefault(key, List.of());
        }

        /** 診断用: ある選択肢の、見直し（REVIEW）の行動の履歴（夜の番号順）。無ければ空リスト。 */
        List<KeyAction> keyActionsOf(ArmKey key) {
            return keyActions.getOrDefault(key, List.of());
        }

        int betterDay(ArmKey key) {
            return firstBetter.getOrDefault(key, NO_DAY);
        }

        int anyBetterDay() {
            return firstBetter.values().stream().mapToInt(Integer::intValue).min().orElse(NO_DAY);
        }

        int quarantineDay(ArmKey key) {
            return firstQuarantine.getOrDefault(key, NO_DAY);
        }

        int ruleDay(String rule) {
            return firstRule.getOrDefault(rule, NO_DAY);
        }

        int ruleCount(String rule) {
            return ruleCounts.getOrDefault(rule, 0);
        }

        int verdictCount(String verdict) {
            return verdictCounts.getOrDefault(verdict, 0);
        }

        int variantNightsOf(ArmKey key) {
            return variantNights.getOrDefault(key, 0);
        }

        int variantNightsTotal() {
            return variantNights.values().stream().mapToInt(Integer::intValue).sum();
        }

        int endedNights() {
            return endedNights;
        }

        int missingAnswers() {
            return missingAnswers;
        }

        int answeredNights() {
            return answeredNights;
        }

        int fitAnswers() {
            return fitAnswers;
        }

        int userChangedNights() {
            return userChangedNights;
        }

        int excludedVariantNights() {
            return excludedVariantNights;
        }

        int baselineNights() {
            return baselineNights;
        }

        int validBaselineNights() {
            return validBaselineNights;
        }

        int controlNights() {
            return controlNights;
        }

        int checkMismatches() {
            return checkMismatches;
        }
    }
}
