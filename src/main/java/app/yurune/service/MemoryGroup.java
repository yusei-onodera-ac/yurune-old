package app.yurune.service;

import app.yurune.domain.value.Dimension;

import java.util.List;
import java.util.Objects;

/**
 * 「覚えたこと」の 1 かたまり（1 つの次元と、その選択肢ごとの集計）。
 */
public final class MemoryGroup {

    private final Dimension dimension;
    private final List<NoteRow> rows;

    public MemoryGroup(Dimension dimension, List<NoteRow> rows) {
        this.dimension = Objects.requireNonNull(dimension);
        this.rows = List.copyOf(rows);
    }

    public Dimension getDimension() {
        return dimension;
    }

    public List<NoteRow> getRows() {
        return rows;
    }
}
