package app.yurune.domain.value;

import java.util.Objects;

/**
 * 選択肢の名前（例: rain、30）。
 */
public final class OptionName {

    private final String text;

    public OptionName(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("option name must not be blank");
        }
        this.text = text;
    }

    public String asText() {
        return text;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof OptionName that && text.equals(that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text);
    }
}
