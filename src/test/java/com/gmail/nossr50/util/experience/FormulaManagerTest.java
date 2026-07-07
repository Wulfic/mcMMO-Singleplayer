package com.gmail.nossr50.util.experience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.experience.FormulaType;
import com.gmail.nossr50.fabric.McMMOMod;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises the ported {@link FormulaManager} XP-curve math against the real bundled
 * {@code experience.yml} (wired through {@link McMMOMod#getExperienceConfig()}).
 *
 * <p>{@link McMMOMod#getGeneralConfig()} is intentionally un-wired, so RetroMode is off and every
 * lookup runs the Standard (1–100) path — a Standard level is the sum of the ten Retro levels it
 * spans. The {@code formula.yml} previous-curve cache is deferred (Phase 5), so a fresh manager
 * reports {@link FormulaType#UNKNOWN}. Expected values are recomputed from the config rather than
 * hard-coded, so the assertions validate the formula logic, not the bundled constants.
 */
class FormulaManagerTest {

    private ExperienceConfig config;

    @BeforeEach
    void wireExperienceConfig(@TempDir Path dataFolder) {
        config = new ExperienceConfig(dataFolder);
        McMMOMod.setExperienceConfig(config);
    }

    @Test
    void previousFormulaIsUnknownUntilPersistenceLands() {
        // loadFormula() is a Phase 5 stub that always reports UNKNOWN (no cached curve yet).
        assertEquals(FormulaType.UNKNOWN, new FormulaManager().getPreviousFormulaType());
    }

    @Test
    void previousFormulaTypeRoundTrips() {
        final FormulaManager formulaManager = new FormulaManager();
        formulaManager.setPreviousFormulaType(FormulaType.EXPONENTIAL);
        assertEquals(FormulaType.EXPONENTIAL, formulaManager.getPreviousFormulaType());
    }

    @Test
    void standardLinearXpIsSumOfTenRetroLevels() {
        final FormulaManager formulaManager = new FormulaManager();
        assertEquals(expectedStandardLinear(0), formulaManager.getXPtoNextLevel(0, FormulaType.LINEAR));
        assertEquals(expectedStandardLinear(1), formulaManager.getXPtoNextLevel(1, FormulaType.LINEAR));
        // Higher levels cost strictly more under a positive linear multiplier.
        assertTrue(formulaManager.getXPtoNextLevel(1, FormulaType.LINEAR)
                > formulaManager.getXPtoNextLevel(0, FormulaType.LINEAR));
    }

    @Test
    void unknownFormulaFallsBackToLinear() {
        final FormulaManager formulaManager = new FormulaManager();
        assertEquals(formulaManager.getXPtoNextLevel(2, FormulaType.LINEAR),
                formulaManager.getXPtoNextLevel(2, FormulaType.UNKNOWN));
    }

    @Test
    void calculateTotalExperienceSumsEachLevelPlusRemainderXp() {
        final FormulaManager formulaManager = new FormulaManager();
        formulaManager.setPreviousFormulaType(FormulaType.LINEAR);

        final int levels = 3;
        final int remainderXp = 7;
        int expected = remainderXp;
        for (int level = 0; level < levels; level++) {
            expected += formulaManager.getXPtoNextLevel(level, FormulaType.LINEAR);
        }

        assertEquals(expected, formulaManager.calculateTotalExperience(levels, remainderXp));
    }

    /** Standard scaling: XP for level {@code L} sums Retro levels {@code L*10+1 .. L*10+10}. */
    private int expectedStandardLinear(int standardLevel) {
        final int base = config.getBase(FormulaType.LINEAR);
        final double multiplier = config.getMultiplier(FormulaType.LINEAR);
        final int retroIndex = (standardLevel * 10) + 1;
        int sum = 0;
        for (int x = retroIndex; x < retroIndex + 10; x++) {
            sum += (int) Math.floor(base + x * multiplier);
        }
        return sum;
    }
}
