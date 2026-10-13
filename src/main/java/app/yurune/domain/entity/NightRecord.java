package app.yurune.domain.entity;

import app.yurune.constant.GentlePolicy;
import app.yurune.domain.value.ArmKey;
import app.yurune.domain.value.ClockTime;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.EndReason;
import app.yurune.domain.value.Minutes;
import app.yurune.domain.value.MorningAnswer;
import app.yurune.domain.value.MorningMood;
import app.yurune.domain.value.NightRole;
import app.yurune.domain.value.WakeDate;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 1 晩の記録（使った計画、終わり方、朝の答え、夜の指標）。起きた日で数える。
 * 値は変えず、結果が分かるたびに新しい NightRecord を返す。
 */
public final class NightRecord {

    /** 変える要因として許すのは、音と音量の下げ方だけ。会話と長さは、利用者の領分なので、自動では変えない。 */
    private static final List<Dimension> ALLOWED_CHANGED_FACTORS = List.of(Dimension.SOUND, Dimension.FADE);

    private final WakeDate wakeDate;
    private final NightPlan plan;
    private final boolean morningChecked;
    private final MorningAnswer answer;
    private final MorningMood mood;
    private final EndReason endReason;
    private final Minutes endedAfter;
    private final ClockTime lastReactionTime;
    private final NightRole role;
    private final Dimension changedFactor;
    private final boolean userChanged;
    private final Minutes onsetProxy;
    private final int restartCount;
    private final int policyVersion;

    private NightRecord(WakeDate wakeDate, NightPlan plan, boolean morningChecked, MorningAnswer answer,
            MorningMood mood, EndReason endReason, Minutes endedAfter, ClockTime lastReactionTime,
            NightRole role, Dimension changedFactor, boolean userChanged, Minutes onsetProxy,
            int restartCount, int policyVersion) {
        this.wakeDate = Objects.requireNonNull(wakeDate);
        this.plan = Objects.requireNonNull(plan);
        this.morningChecked = morningChecked;
        this.answer = answer;
        this.mood = mood;
        this.endReason = endReason;
        this.endedAfter = endedAfter;
        this.lastReactionTime = lastReactionTime;
        this.role = role;
        this.changedFactor = requireAllowedChangedFactor(changedFactor);
        this.userChanged = userChanged;
        this.onsetProxy = onsetProxy;
        this.restartCount = requireNonNegative(restartCount);
        this.policyVersion = requireNonNegative(policyVersion);
    }

    /** 夜を始めた直後の記録。役割は決めず、変えた要因もない。朝のひとことは、まだ確認していない。 */
    public static NightRecord started(WakeDate wakeDate, NightPlan plan) {
        return started(wakeDate, plan, null, null);
    }

    /** 役割と変えた要因を決めて始めた記録。今の規則の版を残す。 */
    public static NightRecord started(WakeDate wakeDate, NightPlan plan, NightRole role, Dimension changedFactor) {
        return new NightRecord(wakeDate, plan, false, null, null, null, null, null, role, changedFactor,
                false, null, 0, GentlePolicy.POLICY_VERSION);
    }

    /**
     * 保存された値から、記録を読み戻す。古い文書には、後から足した項目がないので、既定の値（役割なし、版 0 など）で読む。
     * 無反応の終わりでも、古い記録は寝つきのめやすがないため、終わりの検査はここでは通さない。
     */
    public static NightRecord restored(WakeDate wakeDate, NightPlan plan, boolean morningChecked, MorningAnswer answer,
            MorningMood mood, EndReason endReason, Minutes endedAfter, ClockTime lastReactionTime,
            NightRole role, Dimension changedFactor, boolean userChanged, Minutes onsetProxy,
            int restartCount, int policyVersion) {
        return new NightRecord(wakeDate, plan, morningChecked, answer, mood, endReason, endedAfter, lastReactionTime,
                role, changedFactor, userChanged, onsetProxy, restartCount, policyVersion);
    }

    /**
     * 寝つきのめやすのない終わり（利用者が終えた夜の形）。無反応の終わりには、寝つきのめやすが要るので、4 引数の版を使う。
     */
    public NightRecord withEnd(EndReason reason, Minutes after, ClockTime lastReaction) {
        return withEnd(reason, after, lastReaction, null);
    }

    /**
     * 夜の終わりを記録する。終わりは最初の一度だけを残す（同じ日に再開した夜の終わりで、最初の夜の指標を上書きしないため）。
     * 寝つきのめやすは、無反応の終わりだけにあり、利用者が終えた夜にはない（端末の触れ方から、寝たかどうかは分からないため）。
     */
    public NightRecord withEnd(EndReason reason, Minutes after, ClockTime lastReaction, Minutes onset) {
        Objects.requireNonNull(after);
        requireOnsetMatchesReason(reason, onset);
        if (endReason != null) {
            return this;
        }
        return new NightRecord(wakeDate, plan, morningChecked, answer, mood, reason, after, lastReaction,
                role, changedFactor, userChanged, onset, restartCount, policyVersion);
    }

    /** 朝のひとことを確認した記録。答えと気分は、答えなかったときは null。 */
    public NightRecord withMorningResult(MorningAnswer newAnswer, MorningMood newMood) {
        return new NightRecord(wakeDate, plan, true, newAnswer, newMood, endReason, endedAfter, lastReactionTime,
                role, changedFactor, userChanged, onsetProxy, restartCount, policyVersion);
    }

    /** 利用者が自分で変えた夜、と記録する。基準と比較から外すため。何度呼んでも同じ。 */
    public NightRecord withUserChanged() {
        return new NightRecord(wakeDate, plan, morningChecked, answer, mood, endReason, endedAfter, lastReactionTime,
                role, changedFactor, true, onsetProxy, restartCount, policyVersion);
    }

    /**
     * 同じ起きた日の中で、夜をもう一度始めたことを数える。計画、終わり、朝の答えは、最初の夜のまま残す。
     */
    public NightRecord withRestart() {
        return new NightRecord(wakeDate, plan, morningChecked, answer, mood, endReason, endedAfter, lastReactionTime,
                role, changedFactor, userChanged, onsetProxy, restartCount + 1, policyVersion);
    }

    public WakeDate getWakeDate() {
        return wakeDate;
    }

    public NightPlan getPlan() {
        return plan;
    }

    public boolean isMorningChecked() {
        return morningChecked;
    }

    public Optional<MorningAnswer> findAnswer() {
        return Optional.ofNullable(answer);
    }

    public Optional<MorningMood> findMood() {
        return Optional.ofNullable(mood);
    }

    public Optional<EndReason> findEndReason() {
        return Optional.ofNullable(endReason);
    }

    public Optional<Minutes> findEndedAfter() {
        return Optional.ofNullable(endedAfter);
    }

    /** 最後に反応があった時刻。朝に、就寝時刻の初期値として使う。 */
    public Optional<ClockTime> findLastReactionTime() {
        return Optional.ofNullable(lastReactionTime);
    }

    /** この晩の役割。まだ決めていない記録は空。 */
    public Optional<NightRole> findRole() {
        return Optional.ofNullable(role);
    }

    /** 変えた要因（音か、音量の下げ方）。変えていない夜は空。 */
    public Optional<Dimension> findChangedFactor() {
        return Optional.ofNullable(changedFactor);
    }

    public boolean isUserChanged() {
        return userChanged;
    }

    /**
     * 寝つきのめやす（夜の開始から最後の反応まで、分）。無反応で終わった夜だけにある。
     * 利用者が終えた夜と、まだ終わっていない夜は空（欠測）。
     */
    public Optional<Minutes> findOnsetProxyMin() {
        return Optional.ofNullable(onsetProxy);
    }

    /** 同じ起きた日の中での、2 回目以降の夜の開始の数。最初の夜は 0。 */
    public int getRestartCount() {
        return restartCount;
    }

    /** 記録したときのループの規則の版。古い記録は 0（不明）。 */
    public int getPolicyVersion() {
        return policyVersion;
    }

    /** この晩に試した選択肢（学習の対象になる次元だけ）。 */
    public List<ArmKey> getTriedKeys() {
        return List.of(
                new ArmKey(Dimension.SOUND, plan.getOption(Dimension.SOUND)),
                new ArmKey(Dimension.TALK, plan.getOption(Dimension.TALK)),
                new ArmKey(Dimension.FADE, plan.getOption(Dimension.FADE)));
    }

    /** 無反応の終わりには寝つきのめやすが要り、利用者の終わりには要らない（測れないものを、値にしないため）。 */
    private static void requireOnsetMatchesReason(EndReason reason, Minutes onset) {
        Objects.requireNonNull(reason);
        if (reason == EndReason.NO_REACTION && onset == null) {
            throw new IllegalArgumentException("a night ended without reaction needs an onset proxy");
        }
        if (reason == EndReason.USER && onset != null) {
            throw new IllegalArgumentException("a night ended by the user has no onset proxy");
        }
    }

    private static Dimension requireAllowedChangedFactor(Dimension factor) {
        if (factor == null || ALLOWED_CHANGED_FACTORS.contains(factor)) {
            return factor;
        }
        throw new IllegalArgumentException("changed factor is not allowed: " + factor);
    }

    private static int requireNonNegative(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must not be negative");
        }
        return value;
    }
}
