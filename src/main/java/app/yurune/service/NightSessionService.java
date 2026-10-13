package app.yurune.service;

import app.yurune.config.AppConfig;
import app.yurune.constant.GuardConstants;
import app.yurune.constant.NightConstants;
import app.yurune.constant.OptionLabelConstants;
import app.yurune.constant.TimeConstants;
import app.yurune.domain.entity.Decision;
import app.yurune.domain.entity.NightPlan;
import app.yurune.domain.entity.NightRecord;
import app.yurune.domain.entity.NightSession;
import app.yurune.domain.value.ClockTime;
import app.yurune.domain.value.DecisionPhase;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.DisplayText;
import app.yurune.domain.value.EndReason;
import app.yurune.domain.value.FactSheet;
import app.yurune.domain.value.Minutes;
import app.yurune.domain.value.NightMode;
import app.yurune.domain.value.Observation;
import app.yurune.domain.value.OptionName;
import app.yurune.domain.value.ToolCall;
import app.yurune.domain.value.ToolName;
import app.yurune.domain.value.UserSettings;
import app.yurune.domain.value.VolumePercent;
import app.yurune.domain.value.WakeDate;
import app.yurune.repository.DecisionRepository;
import app.yurune.repository.NightRepository;
import app.yurune.repository.NightSessionRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 夜の制御ループ（計画 → 実行 → 終了）を進める。
 * 観測を受け取り、エージェントが選んだツールを、コードで検証してから実行する。
 * すべての呼び出し（拒否したものを含む）を、判断の記録に残す。
 * 利用者の設定のうち、夜の会話（オフなら会話にしない）と、音量の上限（超えない）は、ここでコードが守る。
 */
public final class NightSessionService {

    private static final String ARG_LEVEL_PCT = "levelPct";
    private static final String ARG_RAMP_SEC = "rampSec";
    private static final String ARG_MINUTES = "minutes";
    private static final String ARG_SOUND_KEY = "soundKey";
    /** 上限にそろえるときは、下げる方向なので、時間をかけずにそろえる。 */
    private static final String IMMEDIATE_RAMP_SEC = "0";

    private final NightSessionRepository sessionRepository;
    private final NightRepository nightRepository;
    private final DecisionRepository decisionRepository;
    private final NightAgent nightAgent;
    private final ToolGuard toolGuard;
    private final AppConfig config;
    private final Clock clock;
    private final Supplier<UserSettings> settings;
    private final Consumer<WakeDate> onNightEnded;
    private final AuditLogger auditLogger;

    /** 設定は、初期値（会話あり、音量の上限は運用の上限まで）で動く。 */
    public NightSessionService(NightSessionRepository sessionRepository, NightRepository nightRepository,
            DecisionRepository decisionRepository, NightAgent nightAgent, ToolGuard toolGuard,
            AppConfig config, Clock clock) {
        this(sessionRepository, nightRepository, decisionRepository, nightAgent, toolGuard, config, clock,
                UserSettings::defaults);
    }

    /** @param settings 利用者の設定の読み出し。夜の間も、そのつど読むので、画面で変えた値が、次の観測から効く */
    public NightSessionService(NightSessionRepository sessionRepository, NightRepository nightRepository,
            DecisionRepository decisionRepository, NightAgent nightAgent, ToolGuard toolGuard,
            AppConfig config, Clock clock, Supplier<UserSettings> settings) {
        this(sessionRepository, nightRepository, decisionRepository, nightAgent, toolGuard, config, clock, settings,
                wakeDate -> { });
    }

    /**
     * @param onNightEnded 夜が終わったあとに、その夜の起きた日で呼ぶ（見直しのため）。
     *                     終わりの記録は、夜の制御が保存したあとに呼ぶので、呼ばれた側は、保存された夜を読める
     */
    public NightSessionService(NightSessionRepository sessionRepository, NightRepository nightRepository,
            DecisionRepository decisionRepository, NightAgent nightAgent, ToolGuard toolGuard,
            AppConfig config, Clock clock, Supplier<UserSettings> settings, Consumer<WakeDate> onNightEnded) {
        this(sessionRepository, nightRepository, decisionRepository, nightAgent, toolGuard, config, clock, settings,
                onNightEnded, null);
    }

    /**
     * @param auditLogger 道具の呼び出し 1 回ごとの記録（A1）の出し先。省略できる
     *                     （既存の呼び出し元と互換を保つため。省略時は記録しない）
     */
    public NightSessionService(NightSessionRepository sessionRepository, NightRepository nightRepository,
            DecisionRepository decisionRepository, NightAgent nightAgent, ToolGuard toolGuard,
            AppConfig config, Clock clock, Supplier<UserSettings> settings, Consumer<WakeDate> onNightEnded,
            AuditLogger auditLogger) {
        this.sessionRepository = sessionRepository;
        this.nightRepository = nightRepository;
        this.decisionRepository = decisionRepository;
        this.nightAgent = nightAgent;
        this.toolGuard = toolGuard;
        this.config = config;
        this.clock = clock;
        this.settings = settings;
        this.onNightEnded = onNightEnded;
        this.auditLogger = auditLogger;
    }

    /**
     * 計画どおりに夜を始め、計画の判断を記録する。会話がオフの設定のときは、会話が求められても、環境音だけで始める。
     * 同じ起きた日の記録が既にあるとき（夜中にまた始めたとき）は、最初の夜の計画と指標を残し、再開の回数だけを数える。
     * そのため、再開では、計画は新しく決めず、保存された計画の音で始める（判断の記録も、最初の夜にだけ残す）。
     */
    public NightSession startNight(NightPlan plan, NightMode mode) {
        WakeDate wakeDate = decideWakeDate();
        UserSettings current = settings.get();
        NightMode startMode = allowMode(mode, current);
        Optional<NightRecord> existing = nightRepository.findNight(wakeDate);
        nightRepository.saveNight(createStartRecord(wakeDate, plan, existing, startMode));

        NightPlan nightPlan = existing.map(NightRecord::getPlan).orElse(plan);
        int startVolume = Math.min(NightConstants.START_VOLUME_PCT, capOf(current));
        Instant now = clock.instant();
        int fadeMinutes = Integer.parseInt(nightPlan.getOption(Dimension.FADE).asText());
        NightSession session = new NightSession(wakeDate, startMode, new VolumePercent(startVolume),
                nightPlan.getOption(Dimension.SOUND).asText(), now, now, null, false, fadeMinutes);
        sessionRepository.saveSession(session);

        if (existing.isEmpty()) {
            saveDecision(wakeDate, DecisionPhase.PLAN, Map.of(), describePlan(plan), plan.getReason().asText(), true);
        }
        return session;
    }

    /** 最初の夜は新しく作り、再開は最初の夜の記録に数えるだけにする。会話で始めた夜は、利用者が変えた夜になる。 */
    private NightRecord createStartRecord(WakeDate wakeDate, NightPlan plan, Optional<NightRecord> existing,
            NightMode startMode) {
        NightRecord record = existing.map(NightRecord::withRestart)
                .orElseGet(() -> NightRecord.started(wakeDate, plan, plan.findRole().orElse(null),
                        plan.findChangedFactor().orElse(null)));
        return startMode == NightMode.TALK ? record.withUserChanged() : record;
    }

    /**
     * 進んでいる夜。終わった夜は返さない。端末が消えて、終わりの知らせが届かなかった夜が、いつまでも「進んでいる」ままにならないよう、
     * 始まってから一定の時間（12 時間）より長いものも、返さない。
     */
    public Optional<NightSession> findActiveSession() {
        Instant oldest = clock.instant().minus(Duration.ofHours(NightConstants.MAX_SESSION_HOURS));
        return sessionRepository.findCurrentSession()
                .filter(session -> !session.isEnded())
                .filter(session -> session.getStartedAt().isAfter(oldest));
    }

    /** 進んでいる夜の計画。夜を始めたときに記録した計画を返す。 */
    public Optional<NightPlan> findActivePlan() {
        return findActiveSession()
                .flatMap(session -> nightRepository.findNight(session.getWakeDate()))
                .map(NightRecord::getPlan);
    }

    /** 利用者が話したとき。最後の反応の時刻を、いまにする（反応のない時間の数え直し）。進んでいる夜がなければ、何もしない。 */
    public void recordUserReaction() {
        findActiveSession().ifPresent(session -> sessionRepository.saveSession(session.withLastReactionAt(clock.instant())));
    }

    /**
     * 会話のときだけ、LLM に渡してよい事実（今夜の音、残りの分、いまの時刻）を、コードが作る。
     * 会話のときでない、進んでいる夜がない、または、設定で夜の会話がオフのときは、空。
     */
    public Optional<FactSheet> findTalkFacts() {
        if (!settings.get().isNightTalkEnabled()) {
            return Optional.empty();
        }
        Optional<NightSession> session = findActiveSession().filter(active -> active.getMode() == NightMode.TALK);
        Optional<NightPlan> plan = findActivePlan();
        if (session.isEmpty() || plan.isEmpty()) {
            return Optional.empty();
        }
        int totalMinutes = Integer.parseInt(plan.get().getOption(Dimension.DURATION).asText());
        long elapsedMinutes = Duration.between(session.get().getStartedAt(), clock.instant()).toMinutes();
        Map<String, String> markers = Map.of(
                GuardConstants.MARKER_MINUTES_LEFT, String.valueOf(Math.max(0L, totalMinutes - elapsedMinutes)),
                GuardConstants.MARKER_CLOCK_TIME, ClockTime.of(LocalTime.now(clock)).asText());
        return Optional.of(new FactSheet(markers, OptionLabelConstants.labelOf(Dimension.SOUND, plan.get().getOption(Dimension.SOUND))));
    }

    /**
     * 利用者が、すごし方を切り替える（環境音から会話へは、利用者だけができる）。
     * 会話がオフの設定のときは、会話が求められても（古い画面、作られた要求）、環境音のままにする。
     */
    public void changeMode(NightMode mode) {
        NightSession session = requireActiveSession();
        NightMode next = allowMode(mode, settings.get());
        if (next == NightMode.TALK && session.getMode() != NightMode.TALK) {
            markUserChanged(session.getWakeDate());
        }
        sessionRepository.saveSession(session.withMode(next));
    }

    /** 利用者が会話へ切り替えた夜、と記録する。基準と比較からは外すが、記録は残す。 */
    private void markUserChanged(WakeDate wakeDate) {
        NightRecord night = nightRepository.findNight(wakeDate).orElseThrow();
        nightRepository.saveNight(night.withUserChanged());
    }

    /** 設定で、夜の会話がオンか。 */
    public boolean isTalkEnabled() {
        return settings.get().isNightTalkEnabled();
    }

    /** 設定の音量の上限（%）。運用の上限（環境変数）と、利用者の設定の、低い方。 */
    public int findVolumeCapPct() {
        return capOf(settings.get());
    }

    /** 進んでいる夜の、端末が鳴らす音の状態。夜がなければ空。 */
    public Optional<NightSoundState> findSoundState() {
        return findActiveSession().map(session -> NightSoundState.of(session, clock.instant()));
    }

    /**
     * 設定に合わない夜の状態を、コードが直す（会話がオフなのに会話になっている、音量が上限を超えている）。
     * 直しは、エージェントの呼び出しと同じ道（検証と記録）を通す。直した呼び出しの一覧を返す（何もなければ空）。
     */
    public List<AppliedCall> enforceSettings() {
        Optional<NightSession> active = findActiveSession();
        if (active.isEmpty()) {
            return List.of();
        }
        List<AppliedCall> appliedCalls = new ArrayList<>();
        NightSession session = applySettingCalls(active.get(), settings.get(), appliedCalls);
        if (!appliedCalls.isEmpty()) {
            sessionRepository.saveSession(session);
        }
        return appliedCalls;
    }

    /**
     * 利用者が「終わる」を押したとき（USER）と、端末のタイマーが無反応を検出したとき（NO_REACTION）の終了。
     * エージェントの終了と同じ道（検証と記録）を通す。
     * 端末のタイマーによる終了は、サーバーに届かなくても端末が確実に終えるための備えで、
     * 申告された「反応のない秒数」が基準に届いていないときは、受け付けない。
     */
    public void endNight(EndReason reason, int lastReactionAgoSec) {
        // decide() を経ない呼び出しなので、前の判断の経路を持ち込まない
        AgentDecisionContext.clear();
        NightSession session = requireActiveSession();
        int silenceSec = reason == EndReason.NO_REACTION ? requireReportedSeconds(lastReactionAgoSec) : 0;
        if (reason == EndReason.NO_REACTION && silenceSec < config.getNoReactionMin() * TimeConstants.SECONDS_PER_MINUTE) {
            throw new IllegalArgumentException("no reaction time is shorter than the limit");
        }
        Observation observation = createObservation(session, silenceSec);
        String why = reason == EndReason.USER ? "利用者が終わらせたため。" : "端末のタイマーが、無反応の基準を超えたため。";
        ToolCall call = new ToolCall(ToolName.END_SESSION.asId(), Map.of("reason", reason.asId()), new DisplayText(why));
        NightSession result = applyCall(call, session, observation, findVolumeCapPct()).session;
        sessionRepository.saveSession(result);
        if (result.isEnded()) {
            onNightEnded.accept(session.getWakeDate());
        }
    }

    /**
     * 端末から観測を受け取り、エージェントの判断を検証して実行する。
     * 反応のない時間は、サーバーが持つ最後の反応の時刻から数える。端末は「何秒前に反応したか」を知らせるだけで、
     * その申告は、反応の時刻を新しくする方向にしか効かない（長く見せかけて、LLM を呼ばせることはできない）。
     */
    public NightObservationResult observe(int lastReactionAgoSec) {
        NightSession session = recordReaction(requireActiveSession(), requireReportedSeconds(lastReactionAgoSec));
        int silenceSec = elapsedSecondsSince(session.getLastReactionAt());
        UserSettings current = settings.get();
        int volumeCapPct = capOf(current);

        List<AppliedCall> appliedCalls = new ArrayList<>();
        session = applySettingCalls(session, current, appliedCalls);
        Observation observation = createObservation(session, silenceSec);
        // nightAgent が、判断の経路を記録できない実装（LLM が無効のときの、規則だけの実装）でも、
        // 前の呼び出しの記録を持ち込まないため、ここで一度消す
        AgentDecisionContext.clear();
        for (ToolCall call : nightAgent.decide(observation, session)) {
            if (session.isEnded()) {
                break;
            }
            CallOutcome outcome = applyCall(call, session, observation, volumeCapPct);
            appliedCalls.add(new AppliedCall(call.getToolId(), outcome.accepted));
            session = outcome.session;
        }
        sessionRepository.saveSession(session);
        if (session.isEnded()) {
            onNightEnded.accept(session.getWakeDate());
        }
        return new NightObservationResult(appliedCalls, session.isEnded(), session.getMode(),
                NightSoundState.of(session, clock.instant()));
    }

    // ---- 内部 ----

    private int requireReportedSeconds(int seconds) {
        if (seconds < 0 || seconds > NightConstants.MAX_REPORTED_SECONDS) {
            throw new IllegalArgumentException("seconds out of range");
        }
        return seconds;
    }

    /** 端末の申告が、いまの最後の反応より新しいときだけ、反応の時刻を新しくする。 */
    private NightSession recordReaction(NightSession session, int lastReactionAgoSec) {
        Instant reportedAt = clock.instant().minusSeconds(lastReactionAgoSec);
        return reportedAt.isAfter(session.getLastReactionAt()) ? session.withLastReactionAt(reportedAt) : session;
    }

    private int elapsedSecondsSince(Instant since) {
        long seconds = Duration.between(since, clock.instant()).getSeconds();
        return (int) Math.max(0, Math.min(seconds, NightConstants.MAX_REPORTED_SECONDS));
    }

    private int capOf(UserSettings current) {
        return current.getVolumeCapPctWithin(config.getVolumeCapPct());
    }

    /** 会話は、設定でオンのときだけ選べる。 */
    private NightMode allowMode(NightMode requested, UserSettings current) {
        return requested == NightMode.TALK && !current.isNightTalkEnabled() ? NightMode.SOUND : requested;
    }

    /** 設定に合わせるための呼び出し（会話をやめる、音量を上限にそろえる）を、検証と記録を通して実行する。 */
    private NightSession applySettingCalls(NightSession session, UserSettings current, List<AppliedCall> appliedCalls) {
        // decide() を経ない呼び出しなので、前の判断の経路を持ち込まない
        AgentDecisionContext.clear();
        int volumeCapPct = capOf(current);
        Observation observation = createObservation(session, elapsedSecondsSince(session.getLastReactionAt()));
        NightSession result = session;
        for (ToolCall call : createSettingCalls(session, current, volumeCapPct)) {
            CallOutcome outcome = applyCall(call, result, observation, volumeCapPct);
            appliedCalls.add(new AppliedCall(call.getToolId(), outcome.accepted));
            result = outcome.session;
        }
        return result;
    }

    private List<ToolCall> createSettingCalls(NightSession session, UserSettings current, int volumeCapPct) {
        List<ToolCall> calls = new ArrayList<>();
        if (session.getMode() == NightMode.TALK && !current.isNightTalkEnabled()) {
            calls.add(new ToolCall(ToolName.SWITCH_MODE.asId(), Map.of("mode", NightMode.SOUND.asId()),
                    new DisplayText("設定で、夜の会話がオフになっているため、環境音だけにします。")));
        }
        if (session.getVolume().asInt() > volumeCapPct) {
            calls.add(new ToolCall(ToolName.SET_VOLUME.asId(),
                    Map.of(ARG_LEVEL_PCT, String.valueOf(volumeCapPct), ARG_RAMP_SEC, IMMEDIATE_RAMP_SEC),
                    new DisplayText("音量が、設定の上限（" + volumeCapPct + "%）を超えていたため、上限にそろえます。")));
        }
        return calls;
    }

    /** 1 件を検証し、判断として記録し、受け入れられたときだけ実行する。 */
    private CallOutcome applyCall(ToolCall call, NightSession session, Observation observation, int volumeCapPct) {
        GuardResult guardResult = toolGuard.validate(call, session, observation, volumeCapPct);
        DecisionPhase phase = ToolName.END_SESSION.asId().equals(call.getToolId())
                ? DecisionPhase.END
                : DecisionPhase.ACT;
        recordAgentLog(call, guardResult);
        if (!guardResult.isAccepted()) {
            saveDecision(session.getWakeDate(), phase, observation.asRecordedValues(),
                    "「" + shorten(call.getToolId()) + "」の呼び出しを拒否", guardResult.getRejectReason(), false);
            return new CallOutcome(session, false);
        }
        saveDecision(session.getWakeDate(), phase, observation.asRecordedValues(),
                describeCall(call), call.getReason().asText(), true);
        return new CallOutcome(execute(call, session, observation), true);
    }

    /**
     * 道具の呼び出し 1 回ごとの記録。判断の経路（規則・LLM・切り替え）は、AgentDecisionContext に
     * あれば使う。無いとき（enforceSettings、endNight など、decide() を経ていない呼び出し）は、規則とする。
     */
    private void recordAgentLog(ToolCall call, GuardResult guardResult) {
        if (auditLogger == null) {
            return;
        }
        AgentDecisionContext.Entry decision = AgentDecisionContext.current().orElseGet(AgentDecisionContext.Entry::rule);
        auditLogger.agent(call.getToolId(), guardResult.isAccepted(), guardResult.getRejectReasonCode().orElse(null),
                decision.getSource(), decision.getLatencyMillis(), decision.getFallbackReason().orElse(null));
    }

    /** 1 件を処理したあとの夜の状態と、受け入れられたか。 */
    private static final class CallOutcome {

        private final NightSession session;
        private final boolean accepted;

        private CallOutcome(NightSession session, boolean accepted) {
            this.session = session;
            this.accepted = accepted;
        }
    }

    private NightSession execute(ToolCall call, NightSession session, Observation observation) {
        return switch (ToolName.fromId(call.getToolId())) {
            case SET_VOLUME -> session.withVolume(new VolumePercent(Integer.parseInt(call.findArgument(ARG_LEVEL_PCT))));
            case FADE_OUT -> session.withFadeOut(clock.instant().plus(
                    Duration.ofMinutes(Integer.parseInt(call.findArgument(ARG_MINUTES)))));
            case SWITCH_TRACK -> session.withSoundKey(call.findArgument(ARG_SOUND_KEY));
            case SWITCH_MODE -> session.withMode(NightMode.SOUND);
            case END_SESSION -> finishNight(call, session, observation);
            default -> session;
        };
    }

    private NightSession finishNight(ToolCall call, NightSession session, Observation observation) {
        EndReason reason = EndReason.fromId(call.findArgument("reason"));
        LocalTime lastReaction = LocalTime.ofInstant(
                clock.instant().minusSeconds(observation.getSilenceSec()), clock.getZone());
        Minutes onset = reason == EndReason.NO_REACTION ? measureOnsetProxy(session, observation) : null;
        NightRecord night = nightRepository.findNight(session.getWakeDate()).orElseThrow();
        nightRepository.saveNight(night.withEnd(reason, new Minutes(observation.getElapsedMin()),
                ClockTime.of(lastReaction), onset));
        return session.withEnded();
    }

    /**
     * 寝つきのめやす（夜の開始から、最後の反応までの分）。利用者が終えた夜は、端末の触れ方から寝たかどうかが分からないので、使わない。
     * 反応の時刻が夜の範囲の外にならないよう、0 から、夜の長さの中に収める。
     */
    private Minutes measureOnsetProxy(NightSession session, Observation observation) {
        long untilLastReaction = Duration.between(session.getStartedAt(), session.getLastReactionAt()).toMinutes();
        return new Minutes((int) Math.max(0L, Math.min(untilLastReaction, observation.getElapsedMin())));
    }

    private NightSession requireActiveSession() {
        return findActiveSession().orElseThrow(NoActiveNightException::new);
    }

    /** 音声がつながるまでは、応答の間隔にも、反応のない時間と同じ値を使う。 */
    private Observation createObservation(NightSession session, int silenceSec) {
        long elapsed = Duration.between(session.getStartedAt(), clock.instant()).toMinutes();
        return new Observation((int) Math.max(0, elapsed), session.getMode(), session.getVolume(),
                silenceSec, silenceSec);
    }

    /** 昼前に始めた夜は今日、それ以外は明日起きる夜として数える。 */
    private WakeDate decideWakeDate() {
        return WakeDate.forNightStartingNow(clock);
    }

    private void saveDecision(WakeDate nightId, DecisionPhase phase, Map<String, String> observed,
            String chosen, String reason, boolean accepted) {
        decisionRepository.saveDecision(new Decision(nightId, ClockTime.of(LocalTime.now(clock)), phase, observed,
                new DisplayText(chosen), new DisplayText(reason), accepted));
    }

    private String describePlan(NightPlan plan) {
        List<String> changed = new ArrayList<>();
        for (Dimension dimension : List.of(Dimension.SOUND, Dimension.FADE)) {
            if (plan.isChanged(dimension)) {
                changed.add(OptionLabelConstants.dimensionLabelOf(dimension));
            }
        }
        String change = changed.isEmpty() ? "変更なし" : String.join("と", changed) + "を変更";
        return label(Dimension.SOUND, plan.getOption(Dimension.SOUND)) + "・"
                + label(Dimension.TALK, plan.getOption(Dimension.TALK)) + "・" + change;
    }

    /** 判断の記録に残す、選んだことの説明。話した文の本文は、残さない。 */
    private String describeCall(ToolCall call) {
        return switch (ToolName.fromId(call.getToolId())) {
            case SET_VOLUME -> "音量を" + call.findArgument(ARG_LEVEL_PCT) + "%にした";
            case FADE_OUT -> "音を" + call.findArgument("minutes") + "分かけて下げた";
            case SWITCH_TRACK -> "音を「" + label(Dimension.SOUND, new OptionName(call.findArgument("soundKey"))) + "」に切り替えた";
            case SET_TALK_LEVEL -> "話す量を" + call.findArgument("level") + "にした";
            case SWITCH_MODE -> "会話を止めて、環境音だけにした";
            case SAY -> "ぽてねが話した（本文は保存しません）";
            case END_SESSION -> describeEnd(EndReason.fromId(call.findArgument("reason")));
        };
    }

    private String describeEnd(EndReason reason) {
        if (reason == EndReason.NO_REACTION) {
            return "終了（" + config.getNoReactionMin() + "分間、反応なし）";
        }
        return "終了（利用者が終わらせた）";
    }

    private String label(Dimension dimension, OptionName optionName) {
        return OptionLabelConstants.labelOf(dimension, optionName);
    }

    /** 許可リストにない名前など、利用者や LLM が決めた文字列は、長いまま記録しない。 */
    private String shorten(String text) {
        int limit = TimeConstants.SECONDS_PER_MINUTE / 2;
        return text.length() <= limit ? text : text.substring(0, limit);
    }
}
