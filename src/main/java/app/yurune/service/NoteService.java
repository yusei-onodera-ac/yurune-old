package app.yurune.service;

import app.yurune.constant.LearningConstants;
import app.yurune.domain.entity.Arm;
import app.yurune.domain.value.Dimension;
import app.yurune.repository.ArmRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * 「ぽてねが覚えていること」の画面に出す、次元ごとの集計と目印を作る。
 */
public final class NoteService {

    private final ArmRepository armRepository;

    public NoteService(ArmRepository armRepository) {
        this.armRepository = armRepository;
    }

    public List<NoteRow> buildNoteRows(Dimension dimension) {
        List<Arm> arms = armRepository.findArmsByDimension(dimension);
        Arm topArm = findTopCandidate(arms);

        List<NoteRow> rows = new ArrayList<>();
        for (Arm arm : arms) {
            rows.add(new NoteRow(arm, decideLabel(arm, topArm)));
        }
        return rows;
    }

    private NoteLabel decideLabel(Arm arm, Arm topArm) {
        if (!arm.getTrialCount().isAtLeast(LearningConstants.MIN_TRIALS_FOR_TOP_CANDIDATE)) {
            return NoteLabel.TRY_MORE;
        }
        return arm == topArm ? NoteLabel.TOP_CANDIDATE : NoteLabel.NONE;
    }

    /** 試した回数が足りている選択肢のうち、割合が最も高いもの。同率は試した回数が多い方。 */
    private Arm findTopCandidate(List<Arm> arms) {
        Arm top = null;
        for (Arm arm : arms) {
            if (!arm.getTrialCount().isAtLeast(LearningConstants.MIN_TRIALS_FOR_TOP_CANDIDATE)) {
                continue;
            }
            if (top == null || isBetter(arm, top)) {
                top = arm;
            }
        }
        return top;
    }

    private boolean isBetter(Arm challenger, Arm current) {
        int compared = challenger.getWinRate().compareTo(current.getWinRate());
        if (compared != 0) {
            return compared > 0;
        }
        return challenger.getTrialCount().asInt() > current.getTrialCount().asInt();
    }
}
