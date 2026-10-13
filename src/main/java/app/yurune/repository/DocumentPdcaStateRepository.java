package app.yurune.repository;

import app.yurune.constant.StorageConstants;
import app.yurune.domain.value.ArmKey;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.NightGoal;
import app.yurune.domain.value.OptionName;
import app.yurune.domain.value.PdcaState;
import app.yurune.domain.value.WakeDate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 見直しの状態を、users/{uid}/learning/pdca に置く。項目の名前は英語で、選択肢の名前は「次元:選択肢」の文字列にする。
 * 読めないとき（保存先が止まっている、中身が壊れている）は、空の状態で始める。夜を止めないため。
 * 書けないときも、夜は止めない。次の夜に、同じ判定をもう一度行う（判断は、変わったときだけ記録するため、二重にはならない）。
 */
public final class DocumentPdcaStateRepository implements PdcaStateRepository {

    private static final String QUARANTINE = "quarantine";
    private static final String SUBJECTIVE_DROP = "subjectiveDrop";
    private static final String PARKED = "parked";
    private static final String PREFERRED = "preferred";
    private static final String ROLLBACK_HOLD_UNTIL = "rollbackHoldUntil";
    private static final String ROLLED_BACK_KEY = "rolledBackKey";
    private static final String REFERRED_ON = "referredOn";
    private static final String REFER_HIDDEN_UNTIL = "referHiddenUntil";
    private static final String LAST_GOAL = "lastGoal";
    private static final String KEY_SEPARATOR = ":";
    private static final int KEY_PARTS = 2;

    private final DocumentStore store;
    private final UserPaths paths;

    public DocumentPdcaStateRepository(DocumentStore store, UserPaths paths) {
        this.store = store;
        this.paths = paths;
    }

    @Override
    public PdcaState findState() {
        try {
            return store.get(documentPath()).map(this::toState).orElseGet(PdcaState::empty);
        } catch (StorageUnavailableException | IllegalArgumentException | IllegalStateException exception) {
            return PdcaState.empty();
        }
    }

    @Override
    public void saveState(PdcaState state) {
        try {
            store.set(documentPath(), toFields(state));
        } catch (StorageUnavailableException exception) {
            // 保存先が止まっていても、夜は止めない（次の夜に、同じ見直しをもう一度行う）
        }
    }

    private String documentPath() {
        return paths.document(StorageConstants.LEARNING, StorageConstants.PDCA_STATE_ID);
    }

    private Map<String, Object> toFields(PdcaState state) {
        Map<String, Object> quarantine = new LinkedHashMap<>();
        state.getQuarantine().forEach((key, until) -> quarantine.put(keyText(key), until.asIsoText()));
        Map<String, Object> parked = new LinkedHashMap<>();
        state.getParked().forEach((key, retryFrom) -> parked.put(keyText(key), retryFrom.asIsoText()));
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(QUARANTINE, quarantine);
        fields.put(SUBJECTIVE_DROP, keyTexts(state.getSubjectiveDrop()));
        fields.put(PARKED, parked);
        fields.put(PREFERRED, keyTexts(state.getPreferred()));
        state.findRollbackHoldUntil().ifPresent(until -> fields.put(ROLLBACK_HOLD_UNTIL, until.asIsoText()));
        state.findRolledBackKey().ifPresent(key -> fields.put(ROLLED_BACK_KEY, keyText(key)));
        state.findReferredOn().ifPresent(day -> fields.put(REFERRED_ON, day.asIsoText()));
        state.findReferHiddenUntil().ifPresent(day -> fields.put(REFER_HIDDEN_UNTIL, day.asIsoText()));
        state.findLastGoal().ifPresent(goal -> fields.put(LAST_GOAL, goal.asId()));
        return fields;
    }

    private PdcaState toState(Map<String, Object> fields) {
        PdcaState state = PdcaState.empty();
        for (Map.Entry<String, Object> entry : optionalMap(fields, QUARANTINE).entrySet()) {
            state = state.withQuarantine(parseKey(entry.getKey()), WakeDate.parse(String.valueOf(entry.getValue())));
        }
        for (ArmKey key : keysOf(fields, SUBJECTIVE_DROP)) {
            WakeDate until = state.getQuarantine().get(key);
            if (until != null) {
                state = state.withSubjectiveQuarantine(key, until);
            }
        }
        for (Map.Entry<String, Object> entry : optionalMap(fields, PARKED).entrySet()) {
            state = state.withParked(parseKey(entry.getKey()), WakeDate.parse(String.valueOf(entry.getValue())));
        }
        for (ArmKey key : keysOf(fields, PREFERRED)) {
            state = state.withPreferred(key);
        }
        Optional<WakeDate> holdUntil = optionalDate(fields, ROLLBACK_HOLD_UNTIL);
        Optional<ArmKey> rolledBackKey = DocumentFields.optionalText(fields, ROLLED_BACK_KEY).map(DocumentPdcaStateRepository::parseKey);
        if (holdUntil.isPresent() && rolledBackKey.isPresent()) {
            state = state.withRollback(rolledBackKey.get(), holdUntil.get());
        }
        Optional<WakeDate> referredOn = optionalDate(fields, REFERRED_ON);
        if (referredOn.isPresent()) {
            state = state.withReferral(referredOn.get());
        }
        Optional<WakeDate> hiddenUntil = optionalDate(fields, REFER_HIDDEN_UNTIL);
        if (hiddenUntil.isPresent()) {
            state = state.withReferHiddenUntil(hiddenUntil.get());
        }
        Optional<String> lastGoal = DocumentFields.optionalText(fields, LAST_GOAL);
        if (lastGoal.isPresent()) {
            state = state.withLastGoal(NightGoal.fromId(lastGoal.get()));
        }
        return state;
    }

    private static Optional<WakeDate> optionalDate(Map<String, Object> fields, String name) {
        return DocumentFields.optionalText(fields, name).map(WakeDate::parse);
    }

    /** 項目がない（まだ何も決めていない）ときは、空。 */
    private static Map<String, Object> optionalMap(Map<String, Object> fields, String name) {
        Object value = fields.get(name);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copied = new LinkedHashMap<>();
            map.forEach((key, item) -> copied.put(String.valueOf(key), item));
            return copied;
        }
        return Map.of();
    }

    private static Set<ArmKey> keysOf(Map<String, Object> fields, String name) {
        Set<ArmKey> keys = new LinkedHashSet<>();
        Object value = fields.get(name);
        if (value instanceof List<?> list) {
            for (Object item : list) {
                keys.add(parseKey(String.valueOf(item)));
            }
        }
        return keys;
    }

    /** 並びを決めてから書く（同じ状態は、同じ文書になるように）。 */
    private static List<Object> keyTexts(Set<ArmKey> keys) {
        List<String> texts = new ArrayList<>();
        for (ArmKey key : keys) {
            texts.add(keyText(key));
        }
        Collections.sort(texts);
        List<Object> items = new ArrayList<>();
        items.addAll(texts);
        return items;
    }

    private static String keyText(ArmKey key) {
        return key.getDimension().asText() + KEY_SEPARATOR + key.getOptionName().asText();
    }

    private static ArmKey parseKey(String text) {
        String[] parts = text.split(KEY_SEPARATOR, KEY_PARTS);
        if (parts.length != KEY_PARTS) {
            throw new IllegalArgumentException("invalid key: " + text);
        }
        return new ArmKey(Dimension.fromText(parts[0]), new OptionName(parts[1]));
    }
}
