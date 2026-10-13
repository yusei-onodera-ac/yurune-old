package app.yurune.service;

import app.yurune.domain.entity.NightRecord;
import app.yurune.domain.value.ArmKey;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.PdcaState;

import java.util.List;

/**
 * 診断のための、読み取り専用の橋渡し。
 * {@link FactorSequencer} はパッケージ非公開（本体の規則）なので、シミュレータの診断テスト（app.yurune.pdca パッケージ）から
 * 直接は呼べない。ここでは、同じ app.yurune.service パッケージの「テストの側」に置いた橋渡しだけを足し、
 * FactorSequencer の判定そのものには、手を加えない（そのまま委譲するだけ）。
 *
 * 本体のロジック（src/main/java）は、一切変更していない。これはテストツリー（src/test/java）のクラスである。
 */
public final class FactorSequencerProbe {

    private FactorSequencerProbe() {
    }

    /** いま順番に試している要因（FactorSequencer.activeFactor のそのままの委譲）。 */
    public static Dimension activeFactor(List<NightRecord> nights, PdcaState state) {
        return FactorSequencer.activeFactor(nights, state);
    }

    /** その要因が、終わっているか（FactorSequencer.isResolved のそのままの委譲）。 */
    public static boolean isResolved(Dimension factor, List<NightRecord> nights, PdcaState state) {
        return FactorSequencer.isResolved(factor, nights, state);
    }

    /** その選択肢が、見直しで動かない判定（よく使う・外す）に達したか（FactorSequencer.isJudged のそのままの委譲）。 */
    public static boolean isJudged(ArmKey key, PdcaState state) {
        return FactorSequencer.isJudged(key, state);
    }

    /** ある要因の、標準ではない選択肢（FactorSequencer.nonStandardCandidatesOf のそのままの委譲）。 */
    public static List<ArmKey> nonStandardCandidatesOf(Dimension factor) {
        return FactorSequencer.nonStandardCandidatesOf(factor);
    }
}
