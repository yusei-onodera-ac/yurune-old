package app.yurune.repository;

import app.yurune.constant.StorageConstants;
import app.yurune.domain.entity.NightPlan;
import app.yurune.domain.entity.NightRecord;
import app.yurune.domain.value.ClockTime;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.DisplayText;
import app.yurune.domain.value.EndReason;
import app.yurune.domain.value.Minutes;
import app.yurune.domain.value.MorningAnswer;
import app.yurune.domain.value.MorningMood;
import app.yurune.domain.value.NightRole;
import app.yurune.domain.value.OptionName;
import app.yurune.domain.value.WakeDate;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 1 晩の記録（計画、朝の答え、終わり方）を、users/{uid}/nights/{起きた日} に置く。
 * 会話の本文は、どの項目にもない。
 */
public final class DocumentNightRepository implements NightRepository {

    private static final String WAKE_DATE = "wakeDate";
    private static final String PLAN_OPTIONS = "planOptions";
    private static final String PLAN_CHANGED = "planChanged";
    private static final String PLAN_REASON = "planReason";
    private static final String PLAN_SEED = "planSeed";
    private static final String MORNING_CHECKED = "morningChecked";
    private static final String ANSWER = "answer";
    private static final String MOOD = "mood";
    private static final String END_REASON = "endReason";
    private static final String ENDED_AFTER_MIN = "endedAfterMin";
    private static final String LAST_REACTION_TIME = "lastReactionTime";
    private static final String ROLE = "role";
    private static final String CHANGED_FACTOR = "changedFactor";
    private static final String USER_CHANGED = "userChanged";
    private static final String ONSET_PROXY_MIN = "onsetProxyMin";
    private static final String RESTART_COUNT = "restartCount";
    private static final String POLICY_VERSION = "policyVersion";

    private final DocumentStore store;
    private final UserPaths paths;

    public DocumentNightRepository(DocumentStore store, UserPaths paths) {
        this.store = store;
        this.paths = paths;
    }

    @Override
    public void saveNight(NightRecord night) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(WAKE_DATE, night.getWakeDate().asIsoText());
        fields.putAll(encodePlan(night.getPlan()));
        fields.put(MORNING_CHECKED, night.isMorningChecked());
        fields.put(ANSWER, night.findAnswer().map(MorningAnswer::asId).orElse(null));
        fields.put(MOOD, night.findMood().map(MorningMood::asId).orElse(null));
        fields.put(END_REASON, night.findEndReason().map(EndReason::asId).orElse(null));
        fields.put(ENDED_AFTER_MIN, night.findEndedAfter().map(minutes -> (long) minutes.asInt()).orElse(null));
        fields.put(LAST_REACTION_TIME, night.findLastReactionTime().map(ClockTime::asText).orElse(null));
        fields.put(ROLE, night.findRole().map(NightRole::asId).orElse(null));
        fields.put(CHANGED_FACTOR, night.findChangedFactor().map(Dimension::asText).orElse(null));
        fields.put(USER_CHANGED, night.isUserChanged());
        fields.put(ONSET_PROXY_MIN, night.findOnsetProxyMin().map(minutes -> (long) minutes.asInt()).orElse(null));
        fields.put(RESTART_COUNT, (long) night.getRestartCount());
        fields.put(POLICY_VERSION, (long) night.getPolicyVersion());
        store.set(paths.document(StorageConstants.NIGHTS, night.getWakeDate().asIsoText()), fields);
    }

    @Override
    public Optional<NightRecord> findNight(WakeDate wakeDate) {
        return store.get(paths.document(StorageConstants.NIGHTS, wakeDate.asIsoText())).map(this::toNight);
    }

    @Override
    public Optional<NightRecord> findLatestNight() {
        return store.queryOrderedByField(paths.collection(StorageConstants.NIGHTS), WAKE_DATE, true, 1).stream()
                .findFirst().map(document -> toNight(document.getFields()));
    }

    @Override
    public List<NightRecord> findRecentNights(int limit) {
        return store.queryOrderedByField(paths.collection(StorageConstants.NIGHTS), WAKE_DATE, true, limit).stream()
                .map(document -> toNight(document.getFields()))
                .toList();
    }

    /** 朝のひとことの対象は最近の夜にしかないので、新しい順に一定の件数だけ調べる（索引を増やさないため）。 */
    @Override
    public Optional<NightRecord> findLatestUncheckedNight(WakeDate today) {
        return store.queryOrderedByField(paths.collection(StorageConstants.NIGHTS), WAKE_DATE, true,
                StorageConstants.RECENT_NIGHT_LIMIT).stream()
                .map(document -> toNight(document.getFields()))
                .filter(night -> !night.isMorningChecked())
                .filter(night -> night.getWakeDate().compareTo(today) <= 0)
                .findFirst();
    }

    private Map<String, Object> encodePlan(NightPlan plan) {
        Map<String, Object> options = new LinkedHashMap<>();
        List<Object> changed = new ArrayList<>();
        for (Dimension dimension : Dimension.values()) {
            plan.findOption(dimension).ifPresent(option -> options.put(dimension.asText(), option.asText()));
            if (plan.isChanged(dimension)) {
                changed.add(dimension.asText());
            }
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(PLAN_OPTIONS, options);
        fields.put(PLAN_CHANGED, changed);
        fields.put(PLAN_REASON, plan.getReason().asText());
        fields.put(PLAN_SEED, plan.getSeed());
        return fields;
    }

    /**
     * 保存された文書から記録を読み戻す。後から足した項目がない古い文書は、既定の値で読む
     * （役割なし、変えた要因なし、会話で変えていない、寝つきのめやすなし、再開 0 回、規則の版 0）。
     */
    private NightRecord toNight(Map<String, Object> fields) {
        Optional<String> endReason = DocumentFields.optionalText(fields, END_REASON);
        Optional<Long> endedAfter = DocumentFields.optionalNumber(fields, ENDED_AFTER_MIN);
        boolean ended = endReason.isPresent() && endedAfter.isPresent();
        Optional<ClockTime> lastReaction = DocumentFields.optionalText(fields, LAST_REACTION_TIME).map(ClockTime::parse);
        Optional<Minutes> onsetProxy = DocumentFields.optionalNumber(fields, ONSET_PROXY_MIN)
                .map(minutes -> new Minutes(minutes.intValue()));
        return NightRecord.restored(
                WakeDate.parse(DocumentFields.text(fields, WAKE_DATE)),
                decodePlan(fields),
                DocumentFields.flag(fields, MORNING_CHECKED),
                DocumentFields.optionalText(fields, ANSWER).map(MorningAnswer::fromId).orElse(null),
                DocumentFields.optionalText(fields, MOOD).map(MorningMood::fromId).orElse(null),
                ended ? EndReason.fromId(endReason.get()) : null,
                ended ? new Minutes(endedAfter.get().intValue()) : null,
                ended ? lastReaction.orElse(null) : null,
                DocumentFields.optionalText(fields, ROLE).map(NightRole::fromId).orElse(null),
                DocumentFields.optionalText(fields, CHANGED_FACTOR).map(Dimension::fromText).orElse(null),
                DocumentFields.optionalFlag(fields, USER_CHANGED).orElse(false),
                onsetProxy.orElse(null),
                DocumentFields.optionalNumber(fields, RESTART_COUNT).map(Long::intValue).orElse(0),
                DocumentFields.optionalNumber(fields, POLICY_VERSION).map(Long::intValue).orElse(0));
    }

    private NightPlan decodePlan(Map<String, Object> fields) {
        Map<Dimension, OptionName> options = new EnumMap<>(Dimension.class);
        for (Map.Entry<String, Object> entry : DocumentFields.map(fields, PLAN_OPTIONS).entrySet()) {
            options.put(Dimension.fromText(entry.getKey()), new OptionName((String) entry.getValue()));
        }
        Set<Dimension> changed = EnumSet.noneOf(Dimension.class);
        for (Object dimension : DocumentFields.list(fields, PLAN_CHANGED)) {
            changed.add(Dimension.fromText((String) dimension));
        }
        return new NightPlan(options, changed, new DisplayText(DocumentFields.text(fields, PLAN_REASON)),
                DocumentFields.number(fields, PLAN_SEED));
    }
}
