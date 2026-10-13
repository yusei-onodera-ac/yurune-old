package app.yurune.service;

import app.yurune.constant.InfoConstants;
import app.yurune.domain.value.Dimension;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 「覚えたことを見る」のための、その人の集計（選択肢ごとの試した回数と、合っていた回数）をまとめる。
 * 保存しているのは構造化した集計だけなので、会話の本文や自由な文章は、ここには出てこない。
 * 読む先は、その人の保存先だけに結びついた {@link NoteService} なので、ほかの人の集計は見えない。
 */
public final class MemoryService {

    /** 試した回数が多い順。同じなら、選択肢の名前の順（並びが、読み込みのたびに変わらないように）。 */
    private static final Comparator<NoteRow> ROW_ORDER = Comparator
            .comparingInt((NoteRow row) -> row.getArm().getTrialCount().asInt()).reversed()
            .thenComparing(row -> row.getArm().getKey().getOptionName().asText());

    private final NoteService noteService;

    public MemoryService(NoteService noteService) {
        this.noteService = noteService;
    }

    /** まだ何も集計がない次元は、含めない。何もなければ、空の一覧を返す。 */
    public List<MemoryGroup> buildMemory() {
        List<MemoryGroup> groups = new ArrayList<>();
        for (Dimension dimension : InfoConstants.MEMORY_DIMENSIONS) {
            List<NoteRow> rows = new ArrayList<>(noteService.buildNoteRows(dimension));
            if (rows.isEmpty()) {
                continue;
            }
            rows.sort(ROW_ORDER);
            groups.add(new MemoryGroup(dimension, rows));
        }
        return groups;
    }
}
