package app.yurune.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.yurune.domain.entity.Arm;
import app.yurune.domain.value.ArmKey;
import app.yurune.domain.value.Dimension;
import app.yurune.domain.value.OptionName;
import app.yurune.domain.value.SelectedOption;
import app.yurune.domain.value.TrialCount;
import app.yurune.domain.value.WinCount;
import app.yurune.repository.ArmRepository;
import app.yurune.repository.InMemoryArmRepository;

import java.util.List;

import org.junit.jupiter.api.Test;

class ThompsonOptionSelectorTest {

    private static final OptionName STRONG = new OptionName("rain");
    private static final OptionName WEAK = new OptionName("wave");
    private static final int HISTORY_SIZE = 30;
    private static final int SEED_COUNT = 1000;
    private static final int MIN_STRONG_PICKS = 990;
    private static final long FIXED_SEED = 42L;

    private ThompsonOptionSelector createSelector() {
        ArmRepository repository = new InMemoryArmRepository();
        repository.saveArm(new Arm(new ArmKey(Dimension.SOUND, STRONG),
                new TrialCount(HISTORY_SIZE), new WinCount(HISTORY_SIZE)));
        repository.saveArm(new Arm(new ArmKey(Dimension.SOUND, WEAK),
                new TrialCount(HISTORY_SIZE), new WinCount(0)));
        return new ThompsonOptionSelector(repository, new BetaSampler());
    }

    @Test
    void sameSeedGivesSameChoice() {
        ThompsonOptionSelector selector = createSelector();
        List<OptionName> candidates = List.of(STRONG, WEAK);

        SelectedOption first = selector.selectOption(Dimension.SOUND, candidates, FIXED_SEED);
        SelectedOption second = selector.selectOption(Dimension.SOUND, candidates, FIXED_SEED);

        assertEquals(first.getOptionName(), second.getOptionName());
        assertEquals(FIXED_SEED, first.getSeed());
    }

    @Test
    void clearlyBetterOptionIsAlmostAlwaysChosen() {
        ThompsonOptionSelector selector = createSelector();
        List<OptionName> candidates = List.of(WEAK, STRONG);

        int strongPicks = 0;
        for (long seed = 0; seed < SEED_COUNT; seed++) {
            if (selector.selectOption(Dimension.SOUND, candidates, seed).getOptionName().equals(STRONG)) {
                strongPicks++;
            }
        }

        assertTrue(strongPicks >= MIN_STRONG_PICKS, "strong picks: " + strongPicks);
    }
}
