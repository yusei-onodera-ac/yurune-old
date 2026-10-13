package app.yurune.service;

import java.util.List;

/**
 * ふりかえり「目標」の「いまのところ」。文は 3 行以内で、受診のすすめが出ているときだけ、「今は表示しない」を出す。
 */
public record NoteSummary(List<String> lines, boolean referralShown) {

    public NoteSummary {
        lines = List.copyOf(lines);
    }
}
