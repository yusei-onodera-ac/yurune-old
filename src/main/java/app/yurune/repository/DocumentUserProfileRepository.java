package app.yurune.repository;

import app.yurune.constant.StorageConstants;
import app.yurune.domain.entity.UserProfile;
import app.yurune.domain.value.UserSettings;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 利用に関する記録を、users/{uid}/profile/main に置く。設定（夜の会話、音量の上限、ぽてねの声、好みを覚える）も、同じ文書に置く。
 * 設定の項目がない古い文書は、その項目を初期値として読む（保存し直さなくても、動く）。
 */
public final class DocumentUserProfileRepository implements UserProfileRepository {

    private static final String CONSENT_VERSION = "consentVersion";
    private static final String CONSENTED_AT = "consentedAt";
    private static final String SEEDED = "seeded";
    private static final String NIGHT_TALK_ENABLED = "nightTalkEnabled";
    private static final String VOLUME_CAP_PCT = "volumeCapPct";
    private static final String VOICE_STYLE = "voiceStyle";
    private static final String LEARNING_ENABLED = "learningEnabled";
    private static final String NIGHT_GOAL = "nightGoal";
    private static final String KEEP_SCREEN_ON = "keepScreenOn";

    private final DocumentStore store;
    private final UserPaths paths;

    public DocumentUserProfileRepository(DocumentStore store, UserPaths paths) {
        this.store = store;
        this.paths = paths;
    }

    @Override
    public UserProfile findProfile() {
        return store.get(paths.document(StorageConstants.PROFILE, StorageConstants.PROFILE_ID))
                .map(fields -> UserProfile.restore(
                        DocumentFields.optionalText(fields, CONSENT_VERSION).orElse(null),
                        DocumentFields.optionalText(fields, CONSENTED_AT).map(Instant::parse).orElse(null),
                        DocumentFields.flag(fields, SEEDED),
                        restoreSettings(fields)))
                .orElseGet(UserProfile::empty);
    }

    @Override
    public void saveProfile(UserProfile profile) {
        UserSettings settings = profile.getSettings();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(CONSENT_VERSION, profile.findConsentVersion().orElse(null));
        fields.put(CONSENTED_AT, profile.findConsentedAt().map(Instant::toString).orElse(null));
        fields.put(SEEDED, profile.isSeeded());
        fields.put(NIGHT_TALK_ENABLED, settings.isNightTalkEnabled());
        fields.put(VOLUME_CAP_PCT, (long) settings.getVolumeCap().asInt());
        fields.put(VOICE_STYLE, settings.getVoiceStyle().asId());
        fields.put(LEARNING_ENABLED, settings.isLearningEnabled());
        fields.put(NIGHT_GOAL, settings.getNightGoal().asId());
        fields.put(KEEP_SCREEN_ON, settings.isKeepScreenOnEnabled());
        store.set(paths.document(StorageConstants.PROFILE, StorageConstants.PROFILE_ID), fields);
    }

    private UserSettings restoreSettings(Map<String, Object> fields) {
        return UserSettings.restore(
                DocumentFields.optionalFlag(fields, NIGHT_TALK_ENABLED).orElse(null),
                DocumentFields.optionalNumber(fields, VOLUME_CAP_PCT).orElse(null),
                DocumentFields.optionalText(fields, VOICE_STYLE).orElse(null),
                DocumentFields.optionalFlag(fields, LEARNING_ENABLED).orElse(null),
                DocumentFields.optionalText(fields, NIGHT_GOAL).orElse(null),
                DocumentFields.optionalFlag(fields, KEEP_SCREEN_ON).orElse(null));
    }
}
