package app.yurune.service;

import app.yurune.constant.GentlePolicy;
import app.yurune.constant.OptionLabelConstants;
import app.yurune.constant.PlanConstants;
import app.yurune.domain.entity.NightPlan;
import app.yurune.domain.entity.NightRecord;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.DisplayText;
import app.yurune.domain.value.NightAssignment;
import app.yurune.domain.value.NightRole;
import app.yurune.domain.value.OptionName;
import app.yurune.domain.value.UserSettings;
import app.yurune.domain.value.WakeDate;
import app.yurune.repository.NightRepository;
import app.yurune.repository.PdcaStateRepository;
import app.yurune.repository.StorageUnavailableException;

import java.time.Clock;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 今夜のやり方を決める。役割（基準、対照、変える）と、変える選択肢は、NightRoleAssigner の規則で決める（LLM は使わない）。
 * 音と音量の下げ方は、標準のやり方（PlanConstants.DEFAULT_*）を基本にし、変える夜だけ、どちらか 1 つを変える。
 * 長さと会話は、前の晩の値を引き継ぐ（利用者の領分。自動では変えない）。
 * 設定の「好みを覚える」がオフのときは、役割を持たず、標準のやり方で始める（仮置き）。
 */
public final class PlanService {

    private static final String REASON_LEARNING_OFF = "好みを覚えない設定のため、今夜は、標準のすごし方にするね。";
    private static final String REASON_BASELINE = "夜のようすを見るために、今夜は、標準のすごし方にするね。";
    private static final String REASON_CONTROL = "比べるために、今夜は、標準のすごし方にするね。";
    private static final String REASON_VARIANT_SUFFIX = "1晩に変えるのは、1項目だけだよ。";
    private static final String REASON_HISTORY_UNAVAILABLE = "前の晩までの記録が、今は読めないため、今夜は、標準のすごし方にするね。";

    private final NightRepository nightRepository;
    private final PdcaStateRepository stateRepository;
    private final NightRoleAssigner roleAssigner;
    private final Clock clock;
    private final Supplier<UserSettings> settings;

    /** 設定を見ない（いつも好みを使う）。 */
    public PlanService(NightRepository nightRepository, ThompsonOptionSelector optionSelector,
            PdcaStateRepository stateRepository, Clock clock) {
        this(nightRepository, optionSelector, stateRepository, clock, UserSettings::defaults);
    }

    public PlanService(NightRepository nightRepository, ThompsonOptionSelector optionSelector,
            PdcaStateRepository stateRepository, Clock clock, Supplier<UserSettings> settings) {
        this.nightRepository = nightRepository;
        this.stateRepository = stateRepository;
        this.roleAssigner = new NightRoleAssigner(() -> settings.get().getNightGoal(), optionSelector::selectOption);
        this.clock = clock;
        this.settings = settings;
    }

    /** 同じ日は同じ計画になる（役割も、乱数の種も、起きた日から決まるため）。 */
    public NightPlan proposeTonightPlan() {
        NightPlan previous = nightRepository.findLatestNight()
                .map(NightRecord::getPlan)
                .orElseGet(this::createDefaultPlan);
        WakeDate wakeDate = WakeDate.forNightStartingNow(clock);
        boolean learningOn = settings.get().isLearningEnabled();
        NightAssignment assignment = assignTonight(wakeDate, learningOn);

        Map<Dimension, OptionName> options = new EnumMap<>(Dimension.class);
        options.put(Dimension.SOUND, new OptionName(PlanConstants.DEFAULT_SOUND));
        options.put(Dimension.FADE, new OptionName(PlanConstants.DEFAULT_FADE));
        options.put(Dimension.DURATION, previous.getOption(Dimension.DURATION));
        options.put(Dimension.TALK, previous.getOption(Dimension.TALK));
        Set<Dimension> changed = EnumSet.noneOf(Dimension.class);
        if (assignment.findChangedFactor().isPresent()) {
            Dimension factor = assignment.findChangedFactor().orElseThrow();
            options.put(factor, assignment.findChangedOption().orElseThrow());
            changed.add(factor);
        }
        return new NightPlan(options, changed, new DisplayText(buildReason(assignment, options, learningOn)),
                wakeDate.toEpochDay(), assignment.findRole().orElse(null));
    }

    /**
     * 今夜の役割。前の夜の記録が読めないときは、変えない（役割なしの、標準のやり方）。
     * 夜を始めるための計画が、保存先の不調で止まらないようにするため。
     */
    private NightAssignment assignTonight(WakeDate wakeDate, boolean learningOn) {
        try {
            return roleAssigner.assign(wakeDate, findPastNights(wakeDate), stateRepository.findState(), learningOn);
        } catch (StorageUnavailableException exception) {
            return NightAssignment.unassigned();
        }
    }

    /** 今夜より前の夜（起きた日の新しい順）。今夜の分は、数えない。 */
    private List<NightRecord> findPastNights(WakeDate wakeDate) {
        return nightRepository.findRecentNights(GentlePolicy.REVIEW_HISTORY_NIGHTS).stream()
                .filter(night -> !night.getWakeDate().equals(wakeDate))
                .toList();
    }

    private NightPlan createDefaultPlan() {
        Map<Dimension, OptionName> options = new EnumMap<>(Dimension.class);
        options.put(Dimension.SOUND, new OptionName(PlanConstants.DEFAULT_SOUND));
        options.put(Dimension.DURATION, new OptionName(PlanConstants.DEFAULT_DURATION));
        options.put(Dimension.TALK, new OptionName(PlanConstants.DEFAULT_TALK));
        options.put(Dimension.FADE, new OptionName(PlanConstants.DEFAULT_FADE));
        return new NightPlan(options, EnumSet.noneOf(Dimension.class), new DisplayText("初期設定"), 0L);
    }

    private String buildReason(NightAssignment assignment, Map<Dimension, OptionName> options, boolean learningOn) {
        if (!learningOn) {
            return REASON_LEARNING_OFF;
        }
        if (assignment.findRole().isEmpty()) {
            return REASON_HISTORY_UNAVAILABLE;
        }
        NightRole role = assignment.findRole().orElseThrow();
        if (role == NightRole.BASELINE) {
            return REASON_BASELINE;
        }
        if (assignment.findChangedFactor().isEmpty()) {
            return REASON_CONTROL;
        }
        Dimension factor = assignment.findChangedFactor().orElseThrow();
        return OptionLabelConstants.dimensionLabelOf(factor) + "を「"
                + OptionLabelConstants.labelOf(factor, options.get(factor)) + "」にしてみるね。" + REASON_VARIANT_SUFFIX;
    }
}
