package com.gmail.nossr50.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

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

    /**
     * Every {@link SubSkillType} must have a DECLARED entry in {@code skillranks.yml}.
     *
     * <p>GitHub #17.4 found six that did not: {@code PARKOUR_ROLL}, {@code ARCHERY_DAZE},
     * {@code HERBALISM_HYLIAN_LUCK}, {@code HERBALISM_SHROOM_THUMB}, {@code SMELTING_SECOND_SMELT}
     * and {@code UNARMED_BLOCK_CRACKER}. None of them failed anything — and that is the point.
     * {@code RankConfig.getSubSkillUnlockLevel} resolves a missing key through
     * {@code config.getInt(key, defaultConfig.getInt(key))}, and a missing key answers <b>0</b>, so
     * each of them silently unlocked at level 0. A sub-skill that is free from the first second of a
     * new world, because nobody wrote a line in a YAML file, is indistinguishable from one that was
     * deliberately made free.
     *
     * <p>They are now declared at 0 — their existing effective value, so behaviour is unchanged —
     * and this case stops the next one being added without an entry. Driven from
     * {@code values()}, never a transcribed list, because an enum constant added tomorrow is exactly
     * what an incremental edit cannot see.
     */
    @Test
    void everySubSkillDeclaresItsUnlockLevel(@TempDir Path dataFolder) throws Exception {
        final Map<String, Object> yaml;
        try (InputStream in = RankConfigTest.class.getResourceAsStream("/skillranks.yml")) {
            assertNotNull(in, "bundled skillranks.yml missing from the test classpath");
            yaml = new Yaml().load(in);
        }

        final RankConfig config = new RankConfig(dataFolder);
        final Set<String> undeclared = new TreeSet<>();
        for (SubSkillType subSkill : SubSkillType.values()) {
            // getRankAddressKey builds "<Skill>.<SubSkill>.<Mode>.Rank_<n>" -- the same address the
            // reader uses, so this cannot pass by looking somewhere the runtime never looks.
            final String[] parts = config.getRankAddressKey(subSkill, 1, false).split("\\.");
            final Object skillSection = yaml.get(parts[0]);
            final Object subSection = skillSection instanceof Map<?, ?> m ? m.get(parts[1]) : null;
            if (subSection == null) {
                undeclared.add(subSkill.name());
            }
        }
        assertTrue(undeclared.isEmpty(),
                "these sub-skills have no skillranks.yml entry and so silently unlock at level 0: "
                        + undeclared);
    }

    /**
     * GitHub #17.4 — the five re-spread skills must each reach level 100.
     *
     * <p>Parkour and Stealth used to finish at 25 and Swimming at 50, so levels past that paid those
     * players nothing at all, while all eight established skills spread to 100. This pins the
     * decision: a revert fails here rather than being noticed by a player a year later.
     */
    @Test
    void theRespreadSkillsReachTheTopOfTheRange(@TempDir Path dataFolder) {
        final RankConfig config = new RankConfig(dataFolder);
        for (PrimarySkillType skill : List.of(PrimarySkillType.PARKOUR, PrimarySkillType.FLYING,
                PrimarySkillType.STEALTH, PrimarySkillType.SWIMMING, PrimarySkillType.UNARMORED)) {
            int highest = 0;
            for (SubSkillType subSkill : SubSkillType.values()) {
                if (subSkill.getParentSkill() != skill) {
                    continue;
                }
                for (int rank = 1; rank <= subSkill.getNumRanks(); rank++) {
                    highest = Math.max(highest, config.getSubSkillUnlockLevel(subSkill, rank, false));
                }
            }
            assertEquals(100, highest,
                    skill + " must have a capstone at level 100 (GitHub #17.4); highest was "
                            + highest);
        }
    }

    /**
     * RetroMode is 10x Standard, with one documented exception: a value of 0 or 1 stays put, because
     * it means "available from the start" rather than "at level 1 of 100".
     *
     * <p>Measured before it was asserted — 27 of 27 {@code Standard: 1} entries in the shipped file
     * use 1 in RetroMode too. A first pass at #17.4 read those as mismatches; "fixing" them would
     * have been a silent gameplay change to every skill in the mod.
     */
    @Test
    void retroModeIsTenTimesStandardExceptForImmediateUnlocks(@TempDir Path dataFolder) {
        final RankConfig config = new RankConfig(dataFolder);
        for (SubSkillType subSkill : SubSkillType.values()) {
            for (int rank = 1; rank <= subSkill.getNumRanks(); rank++) {
                final int standard = config.getSubSkillUnlockLevel(subSkill, rank, false);
                final int retro = config.getSubSkillUnlockLevel(subSkill, rank, true);
                final int expected = standard <= 1 ? standard : standard * 10;
                assertEquals(expected, retro,
                        subSkill + " rank " + rank + ": RetroMode must be 10x Standard (" + standard
                                + "), or unchanged when Standard is 0 or 1");
            }
        }
    }

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

    @Test
    void everyShippedRankSectionMapsToALiveSubSkill() throws Exception {
        // ⚠️ THE CONVERSE DIRECTION, and the one nothing else in the build covers.
        //
        // RankConfig#checkConfig walks SubSkillType.values() and asks the yml — enum → yml. That
        // catches a sub-skill whose section is missing. It is completely blind the other way: a
        // section for a sub-skill that no longer exists just sits there, read by nobody, and looks
        // exactly like a live one to anyone reading the file. That is how `Unarmed.Disarm` and
        // `Unarmed.IronGrip` outlived the mechanics they configured (TODO.md item 1.1), and it is
        // the same one-directional-completeness trap as Herdsmans_Call and
        // Diminished_Returns.Threshold — a hand-kept table keyed by skill name needs its converse
        // guard the day it is written, not the day someone notices.
        final Map<String, Object> root;
        try (InputStream in = RankConfigTest.class.getResourceAsStream("/skillranks.yml")) {
            assertNotNull(in, "skillranks.yml is not on the test classpath");
            root = new Yaml().load(in);
        }

        final Set<String> live = new TreeSet<>();
        for (SubSkillType subSkill : SubSkillType.values()) {
            live.add(subSkill.getRankConfigAddress());
        }

        final Set<String> orphans = new TreeSet<>();
        for (Map.Entry<String, Object> skill : root.entrySet()) {
            if (!(skill.getValue() instanceof Map<?, ?> subSkills)) {
                continue;
            }
            for (Object subSkill : subSkills.keySet()) {
                final String address = skill.getKey() + "." + subSkill;
                if (!live.contains(address)) {
                    orphans.add(address);
                }
            }
        }

        assertTrue(orphans.isEmpty(),
                "skillranks.yml configures sub-skills that no longer exist: " + orphans);
    }
}
