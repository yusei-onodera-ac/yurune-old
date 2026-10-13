package app.yurune.service;

import java.util.Set;

/**
 * 入出力の検査サービス（Model Armor）の判定。止めるべきものがあったときは、どの検査が見つけたか（名前だけ）を持つ。
 */
public final class ArmorVerdict {

    /** 憎悪、嫌がらせ、性的な表現などの、言葉の中身を見る検査の名前（Model Armor の rai）。 */
    public static final String FILTER_CONTENT = "rai";

    private static final ArmorVerdict SAFE = new ArmorVerdict(Set.of());

    private final Set<String> matchedFilters;

    private ArmorVerdict(Set<String> matchedFilters) {
        this.matchedFilters = Set.copyOf(matchedFilters);
    }

    public static ArmorVerdict safe() {
        return SAFE;
    }

    public static ArmorVerdict blocked(Set<String> matchedFilters) {
        return new ArmorVerdict(matchedFilters);
    }

    public boolean isBlocked() {
        return !matchedFilters.isEmpty();
    }

    /** 見つけたのが、言葉の中身の検査だけか。指示の無視、URL、機密情報などの検査が見つけたときは、false。 */
    public boolean isContentOnly() {
        return isBlocked() && matchedFilters.stream().allMatch(FILTER_CONTENT::equals);
    }

    public Set<String> getMatchedFilters() {
        return matchedFilters;
    }
}
