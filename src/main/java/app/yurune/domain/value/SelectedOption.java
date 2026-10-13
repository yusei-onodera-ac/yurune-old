package app.yurune.domain.value;

import java.util.Objects;

/**
 * 選ばれた選択肢と、そのときの乱数の種。
 * 種を残すのは、後から同じ選び方を再現して説明できるようにするため。
 */
public final class SelectedOption {

    private final OptionName optionName;
    private final long seed;

    public SelectedOption(OptionName optionName, long seed) {
        this.optionName = Objects.requireNonNull(optionName);
        this.seed = seed;
    }

    public OptionName getOptionName() {
        return optionName;
    }

    public long getSeed() {
        return seed;
    }
}
