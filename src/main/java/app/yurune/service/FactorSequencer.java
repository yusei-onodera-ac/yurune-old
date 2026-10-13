package app.yurune.service;

import app.yurune.constant.GentlePolicy;
import app.yurune.constant.PlanConstants;
import app.yurune.domain.entity.NightRecord;
import app.yurune.domain.value.ArmKey;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.OptionName;
import app.yurune.domain.value.PdcaState;

import java.util.List;

/**
 * 変える要因を、同時にではなく、順番に試す（R3）。音（SOUND）を先に、音量の下げ方（FADE）をあとに。
 * 多重比較を減らすため、見直しの対象にする要因は、いつも 1 つだけ（NightRoleAssigner の変える夜も、同じ要因だけを使う）。
 *
 * ある要因が「終わった」とするのは、次のどちらか: その要因のすべての選択肢が、見直しで判定（よく使う、見送り、外す。様子見は含まない）
 * に達したとき、または、その要因の変えた夜が、上限（FACTOR_MAX_VARIANT_NIGHTS）に届いたとき（判定が出ていない選択肢が残っていても、進む）。
 */
final class FactorSequencer {

    /** 要因を試す順番。音を先に。 */
    private static final List<Dimension> ORDER = List.of(Dimension.SOUND, Dimension.FADE);

    private FactorSequencer() {
    }

    /** いま試している要因。順番の先頭から見て、まだ終わっていない最初の要因（最後まで終わっていたら、最後の要因のまま）。 */
    static Dimension activeFactor(List<NightRecord> nights, PdcaState state) {
        for (Dimension factor : ORDER) {
            if (!isResolved(factor, nights, state)) {
                return factor;
            }
        }
        return ORDER.get(ORDER.size() - 1);
    }

    /** その要因が、終わっているか。 */
    static boolean isResolved(Dimension factor, List<NightRecord> nights, PdcaState state) {
        long triedNights = nights.stream()
                .filter(night -> night.findChangedFactor().filter(changed -> changed == factor).isPresent())
                .count();
        if (triedNights >= GentlePolicy.FACTOR_MAX_VARIANT_NIGHTS) {
            return true;
        }
        return nonStandardCandidatesOf(factor).stream().allMatch(key -> isJudged(key, state));
    }

    /**
     * その選択肢が、見直しで、動かない判定に達したか。よく使う（PREFER）と、外す（DROP、戻すも同じ隔離の記録を使う）は、
     * 以後その選択肢を試さない決まった判定。見送る（PARK、SIMILAR）は、まだ暫定（夜数が少ないと、ノイズで SIMILAR に
     * なりやすい）なので、要因を終える判定には数えない。様子見（NEED_MORE）も、もちろん数えない。
     * 隔離の記録は、期間が切れても消えないので、「一度は外された」印として使える。
     *
     * ただし、隔離が、統計的な判定（符号反転検定の WORSE）や戻し（ROLLBACK）ではなく、朝の「合わなかった」という
     * 答えが多数だったことだけが理由（R3 の mostlyRejected 経路。PdcaState.isSubjectivelyDropped）のときは、
     * ここでは「終わった」と数えない。診断（FailureDiagnosisTest）で見つけた、朝の答えによる隔離と、この判定の
     * 意図しない相互作用の修正: 統計的な判定が NEED_MORE のままの選択肢を、もう一方の候補が片付いたことだけで
     * 要因ごと手放し、二度と見直さなくなっていた。R3 自体の意図（合わなかったとはっきり言った選択肢を、しばらく
     * 出さない）は、quarantine の記録（isQuarantined、NightRoleAssigner の除外）がそのまま守る。
     */
    static boolean isJudged(ArmKey key, PdcaState state) {
        return state.isPreferred(key)
                || (state.getQuarantine().containsKey(key) && !state.isSubjectivelyDropped(key));
    }

    /** ある要因の、標準ではない選択肢（変えてよい選択肢）。 */
    static List<ArmKey> nonStandardCandidatesOf(Dimension factor) {
        String standard = standardOf(factor);
        return candidatesOf(factor).stream()
                .filter(name -> !name.equals(standard))
                .map(name -> new ArmKey(factor, new OptionName(name)))
                .toList();
    }

    static List<String> candidatesOf(Dimension factor) {
        return factor == Dimension.SOUND ? PlanConstants.SOUND_CANDIDATES : PlanConstants.FADE_CANDIDATES;
    }

    static String standardOf(Dimension factor) {
        return factor == Dimension.SOUND ? PlanConstants.DEFAULT_SOUND : PlanConstants.DEFAULT_FADE;
    }
}
