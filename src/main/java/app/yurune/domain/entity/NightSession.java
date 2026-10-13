package app.yurune.domain.entity;

import app.yurune.constant.NightConstants;
import app.yurune.constant.PlanConstants;
import app.yurune.domain.value.NightMode;
import app.yurune.domain.value.VolumePercent;
import app.yurune.domain.value.WakeDate;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * いま進んでいる夜の状態（すごし方、音量、いまの曲、フェードの終わる時刻、始めた時刻）。
 * 値は変えず、変わるたびに新しい NightSession を返す。
 */
public final class NightSession {

    private final WakeDate wakeDate;
    private final NightMode mode;
    private final VolumePercent volume;
    private final String soundKey;
    private final Instant startedAt;
    private final Instant lastReactionAt;
    /** 音を下げきる時刻。フェード中でなければ null。 */
    private final Instant fadeEndsAt;
    private final boolean ended;
    /** 今夜の計画の FADE の分数（15 / 30 / 45）。PDCA の FADE 要因の、下げの速さに使う。 */
    private final int fadeMinutes;

    public NightSession(WakeDate wakeDate, NightMode mode, VolumePercent volume, String soundKey, Instant startedAt,
            Instant lastReactionAt, Instant fadeEndsAt, boolean ended, int fadeMinutes) {
        this.wakeDate = Objects.requireNonNull(wakeDate);
        this.mode = Objects.requireNonNull(mode);
        this.volume = Objects.requireNonNull(volume);
        this.soundKey = Objects.requireNonNull(soundKey);
        this.startedAt = Objects.requireNonNull(startedAt);
        this.lastReactionAt = Objects.requireNonNull(lastReactionAt);
        this.fadeEndsAt = fadeEndsAt;
        this.ended = ended;
        this.fadeMinutes = fadeMinutes;
    }

    /** 古い呼び出し元のための、互換の形。計画の FADE が分からないときは、標準の分数（30）を使う。 */
    public NightSession(WakeDate wakeDate, NightMode mode, VolumePercent volume, String soundKey, Instant startedAt,
            Instant lastReactionAt, Instant fadeEndsAt, boolean ended) {
        this(wakeDate, mode, volume, soundKey, startedAt, lastReactionAt, fadeEndsAt, ended,
                NightConstants.DEFAULT_FADE_MINUTES);
    }

    /** 曲は計画の初期値（既定の音）、フェードはしていない夜。 */
    public NightSession(WakeDate wakeDate, NightMode mode, VolumePercent volume, Instant startedAt,
            Instant lastReactionAt, boolean ended) {
        this(wakeDate, mode, volume, PlanConstants.DEFAULT_SOUND, startedAt, lastReactionAt, null, ended);
    }

    /** 最後の反応の時刻は、始めた時刻にする。 */
    public NightSession(WakeDate wakeDate, NightMode mode, VolumePercent volume, Instant startedAt, boolean ended) {
        this(wakeDate, mode, volume, startedAt, startedAt, ended);
    }

    public NightSession withMode(NightMode newMode) {
        return new NightSession(wakeDate, newMode, volume, soundKey, startedAt, lastReactionAt, fadeEndsAt, ended,
                fadeMinutes);
    }

    /** 音量を決め直したときは、進んでいたフェードをやめる。 */
    public NightSession withVolume(VolumePercent newVolume) {
        return new NightSession(wakeDate, mode, newVolume, soundKey, startedAt, lastReactionAt, null, ended,
                fadeMinutes);
    }

    public NightSession withSoundKey(String newSoundKey) {
        return new NightSession(wakeDate, mode, volume, newSoundKey, startedAt, lastReactionAt, fadeEndsAt, ended,
                fadeMinutes);
    }

    /** 音量を 0 にして、endsAt までに下げる。端末は、残りの時間をかけて下げる。 */
    public NightSession withFadeOut(Instant endsAt) {
        return new NightSession(wakeDate, mode, new VolumePercent(0), soundKey, startedAt, lastReactionAt, endsAt,
                ended, fadeMinutes);
    }

    public NightSession withLastReactionAt(Instant newLastReactionAt) {
        return new NightSession(wakeDate, mode, volume, soundKey, startedAt, newLastReactionAt, fadeEndsAt, ended,
                fadeMinutes);
    }

    public NightSession withEnded() {
        return new NightSession(wakeDate, mode, volume, soundKey, startedAt, lastReactionAt, fadeEndsAt, true,
                fadeMinutes);
    }

    public WakeDate getWakeDate() {
        return wakeDate;
    }

    public NightMode getMode() {
        return mode;
    }

    public VolumePercent getVolume() {
        return volume;
    }

    /** いまの曲（音源の候補のキー）。 */
    public String getSoundKey() {
        return soundKey;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    /** サーバーが知っている、最後の反応の時刻。端末の申告で、過去には戻せない。 */
    public Instant getLastReactionAt() {
        return lastReactionAt;
    }

    /** フェード中のときだけ、音を下げきる時刻。 */
    public Optional<Instant> findFadeEndsAt() {
        return Optional.ofNullable(fadeEndsAt);
    }

    public boolean isEnded() {
        return ended;
    }

    /** 今夜の計画の FADE の分数（15 / 30 / 45）。 */
    public int getFadeMinutes() {
        return fadeMinutes;
    }
}
