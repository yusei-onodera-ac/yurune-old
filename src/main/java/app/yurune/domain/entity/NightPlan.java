package app.yurune.domain.entity;

import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.DisplayText;
import app.yurune.domain.value.NightRole;
import app.yurune.domain.value.OptionName;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 今夜のやり方（エージェントの計画）。
 * 何を変えたか、なぜそうしたか、乱数の種、今夜の役割を一緒に持ち、後から説明と再現ができるようにする。
 */
public final class NightPlan {

    private final Map<Dimension, OptionName> options;
    private final Set<Dimension> changedDimensions;
    private final DisplayText reason;
    private final long seed;
    private final NightRole role;

    public NightPlan(Map<Dimension, OptionName> options, Set<Dimension> changedDimensions,
            DisplayText reason, long seed) {
        this(options, changedDimensions, reason, seed, null);
    }

    /** @param role 今夜の役割。好みを覚えないときは空（null）。 */
    public NightPlan(Map<Dimension, OptionName> options, Set<Dimension> changedDimensions,
            DisplayText reason, long seed, NightRole role) {
        this.options = new EnumMap<>(Objects.requireNonNull(options));
        this.changedDimensions = changedDimensions.isEmpty()
                ? EnumSet.noneOf(Dimension.class)
                : EnumSet.copyOf(changedDimensions);
        this.reason = Objects.requireNonNull(reason);
        this.seed = seed;
        this.role = role;
    }

    public OptionName getOption(Dimension dimension) {
        OptionName optionName = options.get(dimension);
        if (optionName == null) {
            throw new IllegalArgumentException("plan has no option for " + dimension);
        }
        return optionName;
    }

    /** 計画にない次元は、空を返す（保存のとき、ある次元だけを書くため）。 */
    public Optional<OptionName> findOption(Dimension dimension) {
        return Optional.ofNullable(options.get(dimension));
    }

    public boolean isChanged(Dimension dimension) {
        return changedDimensions.contains(dimension);
    }

    public int countChangedDimensions() {
        return changedDimensions.size();
    }

    /** 今夜の役割。好みを覚えないときと、まだ決めていない計画は空。 */
    public Optional<NightRole> findRole() {
        return Optional.ofNullable(role);
    }

    /** 変える夜の、変えた要因（1 つ）。変える夜でなければ空。 */
    public Optional<Dimension> findChangedFactor() {
        if (role != NightRole.VARIANT || changedDimensions.size() != 1) {
            return Optional.empty();
        }
        return changedDimensions.stream().findFirst();
    }

    public DisplayText getReason() {
        return reason;
    }

    public long getSeed() {
        return seed;
    }
}
