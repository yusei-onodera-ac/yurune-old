package app.yurune.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.domain.entity.Arm;
import app.yurune.domain.value.ArmKey;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.OptionName;
import app.yurune.domain.value.TrialCount;
import app.yurune.domain.value.WinCount;
import app.yurune.repository.ArmRepository;
import app.yurune.repository.DocumentUserStorage;
import app.yurune.repository.InMemoryArmRepository;
import app.yurune.repository.InMemoryDocumentStore;
import app.yurune.repository.UserStorage;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 「覚えたことを見る」の中身。構造化した集計だけを、本人の分だけ、決まった並びで返す。
 */
class MemoryServiceTest {

    private static final int FEW_TRIALS = 2;
    private static final int ENOUGH_TRIALS = 4;

    private static Arm arm(Dimension dimension, String option, int trials, int wins) {
        return new Arm(new ArmKey(dimension, new OptionName(option)), new TrialCount(trials), new WinCount(wins));
    }

    private static MemoryService serviceOver(ArmRepository arms) {
        return new MemoryService(new NoteService(arms));
    }

    @Test
    void aNewUserHasNothingRemembered() {
        assertTrue(serviceOver(new InMemoryArmRepository()).buildMemory().isEmpty());
    }

    @Test
    void groupsFollowTheLearningDimensionsAndSkipTheOnesWithoutCounts() {
        InMemoryArmRepository arms = new InMemoryArmRepository();
        arms.saveArm(arm(Dimension.FADE, "30", ENOUGH_TRIALS, 2));
        arms.saveArm(arm(Dimension.SOUND, "rain", ENOUGH_TRIALS, 3));

        List<MemoryGroup> groups = serviceOver(arms).buildMemory();

        assertEquals(List.of(Dimension.SOUND, Dimension.FADE), groups.stream().map(MemoryGroup::getDimension).toList());
    }

    @Test
    void rowsAreOrderedByTrialCountThenByName() {
        InMemoryArmRepository arms = new InMemoryArmRepository();
        arms.saveArm(arm(Dimension.SOUND, "wave", FEW_TRIALS, 1));
        arms.saveArm(arm(Dimension.SOUND, "rain", ENOUGH_TRIALS, 3));
        arms.saveArm(arm(Dimension.SOUND, "forest", FEW_TRIALS, 0));

        List<String> options = serviceOver(arms).buildMemory().get(0).getRows().stream()
                .map(row -> row.getArm().getKey().getOptionName().asText()).toList();

        assertEquals(List.of("rain", "forest", "wave"), options);
    }

    @Test
    void labelsFollowTheNoteRules() {
        InMemoryArmRepository arms = new InMemoryArmRepository();
        arms.saveArm(arm(Dimension.SOUND, "rain", ENOUGH_TRIALS, 3));
        arms.saveArm(arm(Dimension.SOUND, "wave", FEW_TRIALS, 2));

        List<NoteRow> rows = serviceOver(arms).buildMemory().get(0).getRows();

        assertEquals(NoteLabel.TOP_CANDIDATE, rows.get(0).getLabel());
        assertEquals(NoteLabel.TRY_MORE, rows.get(1).getLabel());
    }

    @Test
    void anotherUsersCountsAreNeverShown() {
        UserStorage storage = new DocumentUserStorage(new InMemoryDocumentStore(), new MutableClock("2026-10-07T01:00:00Z", "Asia/Tokyo"));
        storage.openFor("alice").getArmRepository().saveArm(arm(Dimension.SOUND, "rain", ENOUGH_TRIALS, 3));

        assertEquals(1, serviceOver(storage.openFor("alice").getArmRepository()).buildMemory().size());
        assertTrue(serviceOver(storage.openFor("bob").getArmRepository()).buildMemory().isEmpty());
    }
}
