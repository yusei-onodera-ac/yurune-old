package app.yurune.service;

import app.yurune.constant.GentlePolicy;
import app.yurune.constant.PlanConstants;
import app.yurune.domain.entity.NightRecord;
import app.yurune.domain.value.ArmKey;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.MorningAnswer;
import app.yurune.domain.value.NightAssignment;
import app.yurune.domain.value.NightGoal;
import app.yurune.domain.value.NightRole;
import app.yurune.domain.value.OptionName;
import app.yurune.domain.value.PdcaState;
import app.yurune.domain.value.WakeDate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 今夜の役割（基準、対照、変える）と、変える選択肢を決める。規則だけで決め、LLM は使わない。
 * 時計と乱数は引数で受け取る（同じ入力には、同じ結果になる）。乱数の種は、起きた日の通し日にする。
 *
 * 規則（番号は、報告と監査の記録の ID に使う）:
 * 1. 好みを覚えないときは、役割を持たない（標準のやり方で始める）。
 * 2. 最初の数夜は、基準（標準のやり方）。寝つきの値のある夜が少なければ、上限まで延ばす。
 * 3. 基準のあとは、一定の周期で対照（標準のやり方）を混ぜる。
 * 4. 戻したあとの保留の夜は、対照（標準のやり方）。
 * 5. それ以外は、変える。要因（音か、音量の下げ方）は、いま試している 1 つだけ（R3: 要因は順番に試す。FactorSequencer）。
 *    標準と違う選択肢を、1 つだけ変える。
 * 6. 候補から外すもの: 隔離中のもの、直近の答えで「合わなかった」が多いもの、連続の上限に届いたもの。
 *    見送りのものは、他に候補がないときだけ使う。
 */
public final class NightRoleAssigner {

    private final Supplier<NightGoal> goalSupplier;
    private final OptionPicker picker;

    public NightRoleAssigner(Supplier<NightGoal> goalSupplier, OptionPicker picker) {
        this.goalSupplier = goalSupplier;
        this.picker = picker;
    }

    /**
     * 今夜の役割を決める。pastNights は、今夜を含まない過去の夜（起きた日の新しい順）。
     */
    public NightAssignment assign(WakeDate wakeDate, List<NightRecord> pastNights, PdcaState state, boolean learningOn) {
        if (!learningOn) {
            return NightAssignment.unassigned();
        }
        NightGoal goal = goalSupplier.get();
        if (isBaselinePending(goal, pastNights)) {
            return NightAssignment.standard(NightRole.BASELINE);
        }
        if (state.isHolding(wakeDate) || isControlDay(wakeDate)) {
            return NightAssignment.standard(NightRole.CONTROL);
        }
        return chooseVariant(wakeDate, pastNights, state).orElse(NightAssignment.standard(NightRole.CONTROL));
    }

    /**
     * 基準の夜がまだ必要か。基準の夜の数が足りないとき、または、寝つきの値のある夜が少なく、上限まで延ばせるとき。
     * 読める過去の夜が上限いっぱいなら、基準はとうに終わっている（基準は、最初の数夜だけ）ので、もう基準にしない。
     * ふりかえりの「いまのところ」（NoteSummaryBuilder）も、同じ規則で「基準の間」を判定するため、static で公開する。
     */
    static boolean isBaselinePending(NightGoal goal, List<NightRecord> pastNights) {
        if (pastNights.size() >= GentlePolicy.REVIEW_HISTORY_NIGHTS) {
            return false;
        }
        long baselineCount = pastNights.stream().filter(night -> NightMetricReader.hasRole(night, NightRole.BASELINE)).count();
        long validBaselineCount = pastNights.stream()
                .filter(night -> NightMetricReader.hasRole(night, NightRole.BASELINE))
                .filter(night -> NightMetricReader.isValidFor(goal, night))
                .count();
        if (baselineCount < GentlePolicy.BASELINE_NIGHTS) {
            return true;
        }
        return validBaselineCount < GentlePolicy.MIN_VALID_BASELINE_NIGHTS
                && baselineCount < GentlePolicy.MAX_BASELINE_NIGHTS;
    }

    /** 対照の日か。起きた日の通し日で決めるので、同じ日は同じ役割になる。 */
    private boolean isControlDay(WakeDate wakeDate) {
        return Math.floorMod(wakeDate.toEpochDay(), GentlePolicy.CONTROL_EVERY_NIGHTS) == 0;
    }

    /**
     * 変える夜の、要因と選択肢。要因は、いま順番に試している 1 つだけ（R3: FactorSequencer。音を先に、音量の下げ方をあとに）。
     * ほかの要因が終わるまで、この要因だけを変える（同時に複数の要因を見ないことで、多重比較を減らす）。
     */
    private Optional<NightAssignment> chooseVariant(WakeDate wakeDate, List<NightRecord> pastNights, PdcaState state) {
        long seed = wakeDate.toEpochDay();
        Dimension factor = FactorSequencer.activeFactor(pastNights, state);
        List<OptionName> pool = eligibleOptionsNarrowed(factor, wakeDate, pastNights, state, seed);
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        OptionName option = picker.pick(factor, pool, seed).getOptionName();
        return Optional.of(NightAssignment.variant(factor, option));
    }

    /**
     * 1 つの要因の候補。見送りのものを外したものを優先する。見送りしか残らないときだけ、見送りのものを使う。
     * ただし、見送ってから再挑戦の間隔（GentlePolicy.PARK_RETRY_DAYS）が過ぎたものは、もう見送りとしては除けず、
     * 通常の候補と同じに扱う（見送りには、もともと期限が無く、夜数が集まらないまま取りこぼす一因だったため）。
     * さらに、よく選ぶと決めたものがあれば、半分の日だけ、それに絞る。
     */
    private List<OptionName> eligibleOptionsNarrowed(Dimension factor, WakeDate wakeDate, List<NightRecord> pastNights,
            PdcaState state, long seed) {
        List<OptionName> eligible = eligibleOptions(factor, wakeDate, pastNights, state);
        List<OptionName> notParked = eligible.stream()
                .filter(option -> !isStillDeprioritizedAsParked(new ArmKey(factor, option), wakeDate, state))
                .toList();
        List<OptionName> pool = notParked.isEmpty() ? eligible : notParked;
        return narrowToPreferred(factor, pool, state, seed);
    }

    private static boolean isStillDeprioritizedAsParked(ArmKey key, WakeDate wakeDate, PdcaState state) {
        return state.isParked(key) && !state.isParkRetryDue(key, wakeDate);
    }

    private List<OptionName> eligibleOptions(Dimension factor, WakeDate wakeDate, List<NightRecord> pastNights, PdcaState state) {
        List<OptionName> options = new ArrayList<>();
        for (String name : candidatesOf(factor)) {
            if (name.equals(standardOf(factor))) {
                continue;
            }
            ArmKey key = new ArmKey(factor, new OptionName(name));
            if (!state.isQuarantined(key, wakeDate) && !isRejected(key, pastNights) && !isConsecutiveLimit(key, pastNights)) {
                options.add(key.getOptionName());
            }
        }
        return options;
    }

    /** 直近の答えのうち、「合わなかった」が多いものは、候補から外す。 */
    private boolean isRejected(ArmKey key, List<NightRecord> pastNights) {
        long notFitCount = pastNights.stream()
                .filter(night -> NightMetricReader.findVariantKey(night).filter(key::equals).isPresent())
                .filter(night -> night.findAnswer().isPresent())
                .limit(GentlePolicy.REJECT_RECENT_NIGHTS)
                .filter(night -> night.findAnswer().orElseThrow() == MorningAnswer.NOT_FIT)
                .count();
        return notFitCount >= GentlePolicy.REJECT_MAJORITY_COUNT;
    }

    /** 同じ選択肢の変える夜が、直近で連続の上限に届いていれば、その選択肢は一度休む。 */
    private boolean isConsecutiveLimit(ArmKey key, List<NightRecord> pastNights) {
        if (pastNights.size() < GentlePolicy.MAX_CONSECUTIVE_VARIANT) {
            return false;
        }
        return pastNights.subList(0, GentlePolicy.MAX_CONSECUTIVE_VARIANT).stream()
                .allMatch(night -> NightMetricReader.findVariantKey(night).filter(key::equals).isPresent());
    }

    /** よく選ぶと決めた選択肢は、半分の日だけ、優先して候補にする（ほかを試す日も残すため）。 */
    private List<OptionName> narrowToPreferred(Dimension factor, List<OptionName> pool, PdcaState state, long seed) {
        List<OptionName> preferred = pool.stream()
                .filter(option -> state.isPreferred(new ArmKey(factor, option)))
                .toList();
        boolean usePreferred = !preferred.isEmpty() && Math.floorMod(seed, GentlePolicy.PREFERRED_PICK_EVERY_DAYS) == 0;
        return usePreferred ? preferred : pool;
    }

    private static List<String> candidatesOf(Dimension factor) {
        return factor == Dimension.SOUND ? PlanConstants.SOUND_CANDIDATES : PlanConstants.FADE_CANDIDATES;
    }

    private static String standardOf(Dimension factor) {
        return factor == Dimension.SOUND ? PlanConstants.DEFAULT_SOUND : PlanConstants.DEFAULT_FADE;
    }
}
