package app.yurune.domain.value;

import java.util.Objects;

/**
 * 集計の単位（次元＋選択肢）。
 */
public final class ArmKey {

    /** 保存先の文書 ID で、次元と選択肢を区切る文字列。 */
    private static final String DOCUMENT_ID_SEPARATOR = "__";

    private final Dimension dimension;
    private final OptionName optionName;

    public ArmKey(Dimension dimension, OptionName optionName) {
        this.dimension = Objects.requireNonNull(dimension);
        this.optionName = Objects.requireNonNull(optionName);
    }

    public Dimension getDimension() {
        return dimension;
    }

    public OptionName getOptionName() {
        return optionName;
    }

    public String asDocumentId() {
        return dimension.asText() + DOCUMENT_ID_SEPARATOR + optionName.asText();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ArmKey that
                && dimension == that.dimension
                && optionName.equals(that.optionName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimension, optionName);
    }
}
