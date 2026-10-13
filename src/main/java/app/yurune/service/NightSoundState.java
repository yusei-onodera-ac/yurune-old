package app.yurune.service;

import app.yurune.domain.entity.NightSession;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 端末が鳴らす音の状態。いまの曲、音量の目標、音を下げきるまでの残りの秒数（フェード中のときだけ正）。
 * 値は、夜の状態から作る。端末は、これに従って鳴らし、通信できないときは、いま鳴っている音をそのまま鳴らす。
 */
public final class NightSoundState {

    private final String soundKey;
    private final int volumePct;
    private final int fadeRemainingSec;

    public NightSoundState(String soundKey, int volumePct, int fadeRemainingSec) {
        this.soundKey = Objects.requireNonNull(soundKey);
        this.volumePct = volumePct;
        this.fadeRemainingSec = Math.max(0, fadeRemainingSec);
    }

    /** 夜の状態と、いまの時刻から作る。フェードの終わりを過ぎていれば、残りは 0。 */
    public static NightSoundState of(NightSession session, Instant now) {
        long remaining = session.findFadeEndsAt()
                .map(endsAt -> Duration.between(now, endsAt).getSeconds())
                .orElse(0L);
        return new NightSoundState(session.getSoundKey(), session.getVolume().asInt(),
                (int) Math.min(remaining, Integer.MAX_VALUE));
    }

    public String getSoundKey() {
        return soundKey;
    }

    public int getVolumePct() {
        return volumePct;
    }

    public int getFadeRemainingSec() {
        return fadeRemainingSec;
    }
}
