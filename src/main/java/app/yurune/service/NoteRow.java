package app.yurune.service;

import app.yurune.domain.entity.Arm;

import java.util.Objects;

/**
 * 「ぽてねが覚えていること」の 1 行（集計と目印）。
 */
public final class NoteRow {

    private final Arm arm;
    private final NoteLabel label;

    public NoteRow(Arm arm, NoteLabel label) {
        this.arm = Objects.requireNonNull(arm);
        this.label = Objects.requireNonNull(label);
    }

    public Arm getArm() {
        return arm;
    }

    public NoteLabel getLabel() {
        return label;
    }
}
