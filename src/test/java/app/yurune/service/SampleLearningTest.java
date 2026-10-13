package app.yurune.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.yurune.domain.entity.Arm;
import app.yurune.domain.value.ArmKey;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.OptionName;
import app.yurune.repository.ArmRepository;
import app.yurune.repository.InMemoryArmRepository;
import app.yurune.sample.SampleNight;
import app.yurune.sample.SampleNightData;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 受け入れ条件 AT-09: サンプルの 7 晩を通した集計が、仕様書の期待値と一致する。
 */
class SampleLearningTest {

    private ArmRepository replay(List<SampleNight> nights) {
        ArmRepository repository = new InMemoryArmRepository();
        new SampleReplayService(new LearningService(repository)).replayNights(nights);
        return repository;
    }

    private void assertTally(ArmRepository repository, Dimension dimension, String option, int wins, int trials) {
        Arm arm = repository.findArm(new ArmKey(dimension, new OptionName(option))).orElseThrow();
        assertEquals(wins, arm.getWinCount().asInt(), dimension.asText() + " " + option + " wins");
        assertEquals(trials, arm.getTrialCount().asInt(), dimension.asText() + " " + option + " trials");
    }

    @Test
    void userAHasExpectedTallies() {
        ArmRepository repository = replay(SampleNightData.USER_A);

        assertTally(repository, Dimension.SOUND, "rain", 3, 5);
        assertTally(repository, Dimension.SOUND, "wave", 1, 2);
        assertTally(repository, Dimension.TALK, "on", 0, 1);
        assertTally(repository, Dimension.TALK, "none", 4, 6);
        assertTally(repository, Dimension.FADE, "30", 2, 4);
        assertTally(repository, Dimension.FADE, "45", 2, 3);
    }

    @Test
    void userBHasExpectedTallies() {
        ArmRepository repository = replay(SampleNightData.USER_B);

        assertTally(repository, Dimension.SOUND, "rain", 1, 3);
        assertTally(repository, Dimension.SOUND, "forest", 3, 4);
        assertTally(repository, Dimension.TALK, "on", 4, 5);
        assertTally(repository, Dimension.TALK, "none", 0, 2);
        assertTally(repository, Dimension.FADE, "30", 3, 5);
        assertTally(repository, Dimension.FADE, "45", 1, 2);
    }

    @Test
    void notePutsTopCandidateOnlyAfterEnoughTrials() {
        ArmRepository repository = replay(SampleNightData.USER_A);

        List<NoteRow> rows = new NoteService(repository).buildNoteRows(Dimension.SOUND);

        assertEquals(NoteLabel.TOP_CANDIDATE, labelOf(rows, "rain"));
        assertEquals(NoteLabel.TRY_MORE, labelOf(rows, "wave"));
    }

    @Test
    void noteForTalkMatchesSpecSentenceOfUserB() {
        ArmRepository repository = replay(SampleNightData.USER_B);

        List<NoteRow> rows = new NoteService(repository).buildNoteRows(Dimension.TALK);

        assertEquals(NoteLabel.TOP_CANDIDATE, labelOf(rows, "on"));
        assertEquals(NoteLabel.TRY_MORE, labelOf(rows, "none"));
    }

    private NoteLabel labelOf(List<NoteRow> rows, String option) {
        return rows.stream()
                .filter(row -> row.getArm().getKey().getOptionName().asText().equals(option))
                .findFirst()
                .orElseThrow()
                .getLabel();
    }
}
