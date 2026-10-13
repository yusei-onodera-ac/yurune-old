package app.yurune.domain.value;

import java.util.Objects;

/**
 * 画面に出す短い文（実験の名前、計画の理由など）。
 */
public final class DisplayText {

    private final String text;

    public DisplayText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("display text must not be blank");
        }
        this.text = text;
    }

    public String asText() {
        return text;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DisplayText that && text.equals(that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text);
    }
}
