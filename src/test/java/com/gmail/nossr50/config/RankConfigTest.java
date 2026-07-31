package com.gmail.nossr50.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.skills.SubSkillType;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises {@link RankConfig} against the real bundled {@code skillranks.yml} on the test
 * classpath, with a temp data folder.
 *
 * <p>Only the explicit-{@code retroMode} getters and {@link RankConfig#getRankAddressKey} are
 * asserted here: the no-scaling-arg overload routes through {@code McMMOMod.getGeneralConfig()},
 * which is intentionally un-wired in unit tests (a runtime-only path). Constructing the config still
 * exercises full validation over every {@link SubSkillType} via {@code McMMOMod.getSkillTools()}.
 */
class RankConfigTest {

    @Test
    void writesDefaultToDiskWhenMissing(@TempDir Path dataFolder) {
        new RankConfig(dataFolder);
        assertTrue(Files.exists(dataFolder.resolve("skillranks.yml")));
    }

    @Test
    void rankAddressKeyFormatsStandardAndRetro(@TempDir Path dataFolder) {
        final RankConfig config = new RankConfig(dataFolder);
        assertEquals("Archery.ArcheryLimitBreak.Standard.Rank_1",
                config.getRankAddressKey(SubSkillType.ARCHERY_ARCHERY_LIMIT_BREAK, 1, false));
        assertEquals("Archery.ArcheryLimitBreak.RetroMode.Rank_3",
                config.getRankAddressKey(SubSkillType.ARCHERY_ARCHERY_LIMIT_BREAK, 3, true));
    }

    @Test
    void readsStandardUnlockLevels(@TempDir Path dataFolder) {
        final RankConfig config = new RankConfig(dataFolder);
        // Archery.ArcheryLimitBreak Standard ranks step 10, 20, 30... in the bundled default.
        assertEquals(10,
                config.getSubSkillUnlockLevel(SubSkillType.ARCHERY_ARCHERY_LIMIT_BREAK, 1, false));
        assertEquals(20,
                config.getSubSkillUnlockLevel(SubSkillType.ARCHERY_ARCHERY_LIMIT_BREAK, 2, false));
    }

    @Test
    void trophyHunterShipsFourRanksOnePerMobTier(@TempDir Path dataFolder) {
        final RankConfig config = new RankConfig(dataFolder);

        // Hunter's rank number IS the mob tier it unlocks (livestock → ordinary monsters →
        // dangerous monsters → bosses), so there are exactly four and no fifth is meaningful.
        assertEquals(4, SubSkillType.HUNTER_TROPHY_HUNTER.getNumRanks());

        final int[] retro = {100, 300, 600, 900};
        final int[] standard = {10, 30, 60, 90};
        for (int rank = 1; rank <= 4; rank++) {
            assertEquals(retro[rank - 1], config.getSubSkillUnlockLevel(
                    SubSkillType.HUNTER_TROPHY_HUNTER, rank, true), "RetroMode rank " + rank);
            assertEquals(standard[rank - 1], config.getSubSkillUnlockLevel(
                    SubSkillType.HUNTER_TROPHY_HUNTER, rank, false), "Standard rank " + rank);
        }
    }

    @Test
    void quarrySenseShipsOneRankAtLevelOneInBothModes(@TempDir Path dataFolder) {
        final RankConfig config = new RankConfig(dataFolder);

        assertEquals(1, SubSkillType.HUNTER_QUARRY_SENSE.getNumRanks());
        // ⚠️ Level 1, not 0, and the difference is the whole assertion. A missing section reads as 0
        // (see the test below), which RankUtils treats as unlocked — so an accidentally deleted
        // Quarry Sense block would behave identically in-game to the shipped one and this is the
        // only thing that would notice. The shipped value mirrors Taming's Beast Lore exactly:
        // an inspection readout is not something to make a player earn twice.
        assertEquals(1, config.getSubSkillUnlockLevel(SubSkillType.HUNTER_QUARRY_SENSE, 1, true),
                "RetroMode");
        assertEquals(1, config.getSubSkillUnlockLevel(SubSkillType.HUNTER_QUARRY_SENSE, 1, false),
                "Standard");
        assertEquals(config.getSubSkillUnlockLevel(SubSkillType.TAMING_BEAST_LORE, 1, true),
                config.getSubSkillUnlockLevel(SubSkillType.HUNTER_QUARRY_SENSE, 1, true),
                "Quarry Sense is the same kind of thing as Beast Lore and unlocks with it");
    }

    @Test
    void aRankAddressNoConfigCarriesReadsAsZero(@TempDir Path dataFolder) {
        // ⚠️ Documents a failure DIRECTION that is dangerous and easy to misread, so it is worth
        // being exact about which config it applies to.
        //
        // getSubSkillUnlockLevel reads config.getInt(key, defaultConfig.getInt(key)) — so an
        // operator who deletes a section from their own skillranks.yml still gets the bundled
        // values, and nothing breaks. The hole is only reachable if the BUNDLED RESOURCE loses the
        // section: then every rank answers 0, RankUtils hands a level-0 player the TOP rank, and
        // Hunter would pay boss trophies from the first kill. checkKeys cannot catch it (0 is not
        // negative and 0,0,0,0 is not descending), so the guard has to be a test, and the four that
        // actually assert Hunter's ladder are what redden — this one just pins the mechanism.
        //
        // Rank 5 is absent from both the on-disk copy and the bundled default, which is the only
        // way to observe the raw behaviour without breaking the shipped file.
        final RankConfig config = new RankConfig(dataFolder);
        assertEquals(0,
                config.getSubSkillUnlockLevel(SubSkillType.HUNTER_TROPHY_HUNTER, 5, true),
                "there is no rank 5; if this ever answers a real level the ladder grew silently");
    }
}
