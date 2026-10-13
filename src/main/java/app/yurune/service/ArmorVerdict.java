package app.yurune.service;

import java.util.Set;

/**
 * 入出力の検査サービス（Model Armor）の判定。止めるべきものがあったときは、どの検査が見つけたか（名前だけ）を持つ。
 */
public final class ArmorVerdict {

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

    public Set<String> getMatchedFilters() {
        return matchedFilters;
    }
}
