package app.yurune.domain.value;

import java.util.Optional;

/**
 * 今夜の役割と、変えた要因（音か、音量の下げ方）と、変えた選択肢。
 * 変えていない夜は、要因と選択肢を持たない。好みを覚えないときは、役割も持たない。
 */
public final class NightAssignment {

    private final NightRole role;
    private final Dimension changedFactor;
    private final OptionName changedOption;

    private NightAssignment(NightRole role, Dimension changedFactor, OptionName changedOption) {
        this.role = role;
        this.changedFactor = changedFactor;
        this.changedOption = changedOption;
    }

    /** 好みを覚えない設定の夜。役割はなく、標準のやり方で始める。 */
    public static NightAssignment unassigned() {
        return new NightAssignment(null, null, null);
    }

    /** 標準のやり方の夜（基準か、対照）。 */
    public static NightAssignment standard(NightRole role) {
        return new NightAssignment(role, null, null);
    }

    /** 変える夜。要因は 1 つだけ（音か、音量の下げ方）。 */
    public static NightAssignment variant(Dimension changedFactor, OptionName changedOption) {
        return new NightAssignment(NightRole.VARIANT, changedFactor, changedOption);
    }

    public Optional<NightRole> findRole() {
        return Optional.ofNullable(role);
    }

    public Optional<Dimension> findChangedFactor() {
        return Optional.ofNullable(changedFactor);
    }

    public Optional<OptionName> findChangedOption() {
        return Optional.ofNullable(changedOption);
    }
}
