package app.yurune.service;

import app.yurune.domain.value.ClockTime;
import app.yurune.domain.value.MorningAnswer;
import app.yurune.domain.value.MorningMood;

import java.util.Optional;

/**
 * 朝のひとことで、利用者が送った内容。気分と答えは任意（答えなくても保存できる）。
 */
public final class MorningInput {

    private final ClockTime bedTime;
    private final ClockTime wakeTime;
    private final MorningAnswer answer;
    private final MorningMood mood;

    private MorningInput(ClockTime bedTime, ClockTime wakeTime, MorningAnswer answer, MorningMood mood) {
        this.bedTime = bedTime;
        this.wakeTime = wakeTime;
        this.answer = answer;
        this.mood = mood;
    }

    /** 「保存する」: 確認した時刻と、答えた気分・答えを保存する（答えなかった項目は空）。 */
    public static MorningInput saved(ClockTime bedTime, ClockTime wakeTime, MorningAnswer answer, MorningMood mood) {
        return new MorningInput(bedTime, wakeTime, answer, mood);
    }

    /** 「答えずに閉じる」: 推定の時刻だけを保存し、気分と答えは残さない。 */
    public static MorningInput dismissed(ClockTime bedTime, ClockTime wakeTime) {
        return new MorningInput(bedTime, wakeTime, null, null);
    }

    public Optional<ClockTime> findBedTime() {
        return Optional.ofNullable(bedTime);
    }

    public Optional<ClockTime> findWakeTime() {
        return Optional.ofNullable(wakeTime);
    }

    public Optional<MorningAnswer> findAnswer() {
        return Optional.ofNullable(answer);
    }

    public Optional<MorningMood> findMood() {
        return Optional.ofNullable(mood);
    }
}
