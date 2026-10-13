package app.yurune.domain.value;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 見直しの結果として、いまも効いている決まり（外しているもの、見送りのもの、よく選ぶもの、戻しの保留、受診のすすめ）。
 * 値は変えず、変えるたびに新しい状態を返す。日付は、起きた日で持つ（夜の数え方と同じにするため）。
 */
public final class PdcaState {

    private final Map<ArmKey, WakeDate> quarantine;
    private final Set<ArmKey> subjectiveDrop;
    private final Map<ArmKey, WakeDate> parked;
    private final Set<ArmKey> preferred;
    private final WakeDate rollbackHoldUntil;
    private final ArmKey rolledBackKey;
    private final WakeDate referredOn;
    private final WakeDate referHiddenUntil;
    private final NightGoal lastGoal;

    private PdcaState(Map<ArmKey, WakeDate> quarantine, Set<ArmKey> subjectiveDrop, Map<ArmKey, WakeDate> parked,
            Set<ArmKey> preferred, WakeDate rollbackHoldUntil, ArmKey rolledBackKey, WakeDate referredOn,
            WakeDate referHiddenUntil, NightGoal lastGoal) {
        this.quarantine = Map.copyOf(quarantine);
        this.subjectiveDrop = Set.copyOf(subjectiveDrop);
        this.parked = Map.copyOf(parked);
        this.preferred = Set.copyOf(preferred);
        this.rollbackHoldUntil = rollbackHoldUntil;
        this.rolledBackKey = rolledBackKey;
        this.referredOn = referredOn;
        this.referHiddenUntil = referHiddenUntil;
        this.lastGoal = lastGoal;
    }

    /** まだ何も決めていない状態。読めないときも、この状態で始める。 */
    public static PdcaState empty() {
        return new PdcaState(Map.of(), Set.of(), Map.of(), Set.of(), null, null, null, null, null);
    }

    /**
     * 候補から外す（隔離）。統計的な判定（符号反転検定で WORSE）か、戻し（ROLLBACK）の、確かな根拠にもとづく隔離。
     * 見送りとよく選ぶの印は、外すので消す。すでに「朝の答えだけの隔離」（withSubjectiveQuarantine）だった場合は、
     * 確かな根拠による隔離に置き換わるので、その印も消す（FactorSequencer.isJudged が、以後、この選択肢を正しく「終わった」と数えるため）。
     */
    public PdcaState withQuarantine(ArmKey key, WakeDate until) {
        Map<ArmKey, WakeDate> next = new HashMap<>(quarantine);
        next.put(key, until);
        return new PdcaState(next, withoutKey(subjectiveDrop, key), withoutKey(parked, key), withoutKey(preferred, key),
                rollbackHoldUntil, rolledBackKey, referredOn, referHiddenUntil, lastGoal);
    }

    /**
     * 候補から外す（隔離）。ただし、統計的な判定ではなく、朝の「合わなかった」という答えが多数だったことだけが理由
     * （規則 R3 の mostlyRejected 経路。まだ統計的な判定は NEED_MORE や SIMILAR のまま）。
     * この印がある隔離は、{@link #isSubjectivelyDropped(ArmKey)} が true を返す。FactorSequencer.isJudged は、
     * この印がある隔離を「要因が終わった」判定に数えない（統計的な判定がつくまで、要因を終えないため。
     * 診断で見つけた、R3 と FactorSequencer の意図しない相互作用の修正。利用者が明確に「合わなかった」と言った
     * 選択肢をしばらく出さない、という R3 自体の意図は、quarantine の記録を使うことで、そのまま守る）。
     */
    public PdcaState withSubjectiveQuarantine(ArmKey key, WakeDate until) {
        Map<ArmKey, WakeDate> next = new HashMap<>(quarantine);
        next.put(key, until);
        return new PdcaState(next, withKey(subjectiveDrop, key), withoutKey(parked, key), withoutKey(preferred, key),
                rollbackHoldUntil, rolledBackKey, referredOn, referHiddenUntil, lastGoal);
    }

    public PdcaState withPreferred(ArmKey key) {
        return new PdcaState(quarantine, withoutKey(subjectiveDrop, key), withoutKey(parked, key), withKey(preferred, key),
                rollbackHoldUntil, rolledBackKey, referredOn, referHiddenUntil, lastGoal);
    }

    /**
     * 見送る（PARK）。retryFrom は、見送りのまま、ほかに候補がなくなるのを待たずに、通常の候補と同じ扱いに戻してよい
     * 日（GentlePolicy.PARK_RETRY_DAYS 後。R3 改訂: 見送りに再挑戦の間隔を設ける。見送りには、もともと期限が無く、
     * 他方の候補が片付くまで夜数が集まらないことが、取りこぼしの一因だったため）。
     */
    public PdcaState withParked(ArmKey key, WakeDate retryFrom) {
        Map<ArmKey, WakeDate> next = new HashMap<>(parked);
        next.put(key, retryFrom);
        return new PdcaState(quarantine, subjectiveDrop, next, withoutKey(preferred, key),
                rollbackHoldUntil, rolledBackKey, referredOn, referHiddenUntil, lastGoal);
    }

    /** 戻すとき。その選択肢を外し、保留の終わりの日までは、標準のやり方にする。 */
    public PdcaState withRollback(ArmKey key, WakeDate holdUntil) {
        return new PdcaState(quarantine, subjectiveDrop, parked, preferred, holdUntil, key, referredOn, referHiddenUntil,
                lastGoal);
    }

    /** 受診のすすめを出した日。 */
    public PdcaState withReferral(WakeDate day) {
        return new PdcaState(quarantine, subjectiveDrop, parked, preferred, rollbackHoldUntil, rolledBackKey, day,
                referHiddenUntil, lastGoal);
    }

    /** 「今は表示しない」の終わりの日。画面は、後の段階で使う。 */
    public PdcaState withReferHiddenUntil(WakeDate until) {
        return new PdcaState(quarantine, subjectiveDrop, parked, preferred, rollbackHoldUntil, rolledBackKey, referredOn,
                until, lastGoal);
    }

    /**
     * 直前に見直しを行ったときの、夜のめやす。見直しのたびに記録し、前回と違うめやすに変わっていたら、
     * 見直す側（NightReviewService）が resetForNewGoal で、学習の状態を作り直す（めやすをまたいで判定や配分が混ざらないため）。
     */
    public PdcaState withLastGoal(NightGoal goal) {
        return new PdcaState(quarantine, subjectiveDrop, parked, preferred, rollbackHoldUntil, rolledBackKey, referredOn,
                referHiddenUntil, goal);
    }

    /**
     * めやすを切り替えたときの、学習のやり直し。外す・見送る・よく選ぶ・戻しの保留・受診の記録を、すべて消す
     * （古いめやすでの判定が、新しいめやすの判定や配分に混ざらないため）。「今は表示しない」の指定は、利用者自身の指示なので残す。
     */
    public PdcaState resetForNewGoal(NightGoal goal) {
        return new PdcaState(Map.of(), Set.of(), Map.of(), Set.of(), null, null, null, referHiddenUntil, goal);
    }

    public boolean isQuarantined(ArmKey key, WakeDate day) {
        WakeDate until = quarantine.get(key);
        return until != null && !day.isAfter(until);
    }

    /**
     * 隔離が、統計的な判定（符号反転検定の WORSE）や戻し（ROLLBACK）ではなく、朝の答えだけが理由か。
     * FactorSequencer.isJudged が使う（統計的な判定がつくまで、要因を終えないため）。
     */
    public boolean isSubjectivelyDropped(ArmKey key) {
        return subjectiveDrop.contains(key);
    }

    /** 隔離のうち、朝の答えだけが理由のもの（保存のために、丸ごと読み出す）。 */
    public Set<ArmKey> getSubjectiveDrop() {
        return subjectiveDrop;
    }

    public boolean isParked(ArmKey key) {
        return parked.containsKey(key);
    }

    /** 見送ってから、再挑戦の間隔（GentlePolicy.PARK_RETRY_DAYS）が過ぎたか。過ぎていたら、通常の候補と同じに扱ってよい。 */
    public boolean isParkRetryDue(ArmKey key, WakeDate day) {
        WakeDate retryFrom = parked.get(key);
        return retryFrom != null && !retryFrom.isAfter(day);
    }

    public boolean isPreferred(ArmKey key) {
        return preferred.contains(key);
    }

    /** 戻しの保留の夜か。保留の終わりの日まで（その日を含む）、標準のやり方にする。 */
    public boolean isHolding(WakeDate day) {
        return rollbackHoldUntil != null && !day.isAfter(rollbackHoldUntil);
    }

    public Map<ArmKey, WakeDate> getQuarantine() {
        return quarantine;
    }

    /** 見送っている選択肢と、それぞれの再挑戦の日（isParkRetryDue が使う）。 */
    public Map<ArmKey, WakeDate> getParked() {
        return parked;
    }

    public Set<ArmKey> getPreferred() {
        return preferred;
    }

    public Optional<WakeDate> findRollbackHoldUntil() {
        return Optional.ofNullable(rollbackHoldUntil);
    }

    public Optional<ArmKey> findRolledBackKey() {
        return Optional.ofNullable(rolledBackKey);
    }

    public Optional<WakeDate> findReferredOn() {
        return Optional.ofNullable(referredOn);
    }

    public Optional<WakeDate> findReferHiddenUntil() {
        return Optional.ofNullable(referHiddenUntil);
    }

    /** 直前に見直しを行ったときの、夜のめやす。まだ一度も見直していないときは空。 */
    public Optional<NightGoal> findLastGoal() {
        return Optional.ofNullable(lastGoal);
    }

    private static Set<ArmKey> withKey(Set<ArmKey> keys, ArmKey key) {
        Set<ArmKey> next = new HashSet<>(keys);
        next.add(key);
        return next;
    }

    private static Set<ArmKey> withoutKey(Set<ArmKey> keys, ArmKey key) {
        Set<ArmKey> next = new HashSet<>(keys);
        next.remove(key);
        return next;
    }

    private static Map<ArmKey, WakeDate> withoutKey(Map<ArmKey, WakeDate> keys, ArmKey key) {
        Map<ArmKey, WakeDate> next = new HashMap<>(keys);
        next.remove(key);
        return next;
    }
}
