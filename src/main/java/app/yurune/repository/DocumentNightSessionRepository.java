package app.yurune.repository;

import app.yurune.constant.NightConstants;
import app.yurune.constant.PlanConstants;
import app.yurune.constant.StorageConstants;
import app.yurune.domain.entity.NightSession;
import app.yurune.domain.value.NightMode;
import app.yurune.domain.value.VolumePercent;
import app.yurune.domain.value.WakeDate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * いま進んでいる夜の状態を、users/{uid}/sessions/current に置く。
 * メモリでなく保存先に置くのは、サーバーが再起動や縮小をしても、夜の続きから動けるようにするため。
 */
public final class DocumentNightSessionRepository implements NightSessionRepository {

    private static final String WAKE_DATE = "wakeDate";
    private static final String MODE = "mode";
    private static final String VOLUME_PCT = "volumePct";
    private static final String SOUND_KEY = "soundKey";
    private static final String FADE_ENDS_AT = "fadeEndsAt";
    private static final String STARTED_AT = "startedAt";
    private static final String LAST_REACTION_AT = "lastReactionAt";
    private static final String ENDED = "ended";
    private static final String FADE_MINUTES = "fadeMinutes";
    /** フェードしていないことを、文字の空で表す（項目を消さずに置けるように）。 */
    private static final String NO_FADE = "";

    private final DocumentStore store;
    private final UserPaths paths;

    public DocumentNightSessionRepository(DocumentStore store, UserPaths paths) {
        this.store = store;
        this.paths = paths;
    }

    @Override
    public Optional<NightSession> findCurrentSession() {
        return store.get(paths.document(StorageConstants.SESSIONS, StorageConstants.CURRENT_SESSION_ID)).map(this::toSession);
    }

    @Override
    public void saveSession(NightSession session) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(WAKE_DATE, session.getWakeDate().asIsoText());
        fields.put(MODE, session.getMode().asId());
        fields.put(VOLUME_PCT, (long) session.getVolume().asInt());
        fields.put(SOUND_KEY, session.getSoundKey());
        fields.put(FADE_ENDS_AT, session.findFadeEndsAt().map(Instant::toString).orElse(NO_FADE));
        fields.put(STARTED_AT, session.getStartedAt().toString());
        fields.put(LAST_REACTION_AT, session.getLastReactionAt().toString());
        fields.put(ENDED, session.isEnded());
        fields.put(FADE_MINUTES, (long) session.getFadeMinutes());
        store.set(paths.document(StorageConstants.SESSIONS, StorageConstants.CURRENT_SESSION_ID), fields);
    }

    /**
     * 曲とフェードの項目、FADE の分数は、後から足したもの。古い文書にはないので、既定の値
     * （計画の初期値の音、フェードなし、標準の FADE の分数）で読む。
     */
    private NightSession toSession(Map<String, Object> fields) {
        String soundKey = DocumentFields.optionalText(fields, SOUND_KEY).orElse(PlanConstants.DEFAULT_SOUND);
        Optional<Instant> fadeEndsAt = DocumentFields.optionalText(fields, FADE_ENDS_AT)
                .filter(text -> !NO_FADE.equals(text))
                .map(Instant::parse);
        int fadeMinutes = DocumentFields.optionalNumber(fields, FADE_MINUTES)
                .map(Long::intValue)
                .orElse(NightConstants.DEFAULT_FADE_MINUTES);
        return new NightSession(WakeDate.parse(DocumentFields.text(fields, WAKE_DATE)),
                NightMode.fromId(DocumentFields.text(fields, MODE)),
                new VolumePercent((int) DocumentFields.number(fields, VOLUME_PCT)),
                soundKey,
                Instant.parse(DocumentFields.text(fields, STARTED_AT)),
                Instant.parse(DocumentFields.text(fields, LAST_REACTION_AT)),
                fadeEndsAt.orElse(null),
                DocumentFields.flag(fields, ENDED),
                fadeMinutes);
    }
}
