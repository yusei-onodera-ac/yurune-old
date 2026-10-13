package app.yurune.controller.view;

import java.util.List;

/**
 * 「ぽてねが覚えていること」の 1 つの見出しと、その選択肢の行。
 */
public final class NoteGroupView {

    private final String heading;
    private final List<NoteRowView> rows;

    public NoteGroupView(String heading, List<NoteRowView> rows) {
        this.heading = heading;
        this.rows = List.copyOf(rows);
    }

    public String getHeading() {
        return heading;
    }

    public List<NoteRowView> getRows() {
        return rows;
    }
}
