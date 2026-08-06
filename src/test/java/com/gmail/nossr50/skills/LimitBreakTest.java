package com.gmail.nossr50.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.RankConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link LimitBreak} — the flat per-rank combat damage bonus, ported for all eight weapons
 * (TODO.md item 3.1). Covers the rank arithmetic, the {@code AllowPVE} gate, and the fact that every
 * one of the eight {@code *_LIMIT_BREAK} constants is actually reachable.
 */
class LimitBreakTest {

    /** The eight, one per combat skill. Hand-listed so a missing wiring shows up as a gap. */
    private static final List<SubSkillType> ALL_EIGHT = List.of(
            SubSkillType.ARCHERY_ARCHERY_LIMIT_BREAK,
            SubSkillType.AXES_AXES_LIMIT_BREAK,
            SubSkillType.CROSSBOWS_CROSSBOWS_LIMIT_BREAK,
            SubSkillType.MACES_MACES_LIMIT_BREAK,
            SubSkillType.SPEARS_SPEARS_LIMIT_BREAK,
            SubSkillType.SWORDS_SWORDS_LIMIT_BREAK,
            SubSkillType.TRIDENTS_TRIDENTS_LIMIT_BREAK,
            SubSkillType.UNARMED_UNARMED_LIMIT_BREAK);

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
        McMMOMod.setAdvancedConfig(null);
    }

    private static McMMOPlayer playerAtLevel(int level) {
        final McMMOPlayer player = mock(McMMOPlayer.class);
        for (PrimarySkillType skill : PrimarySkillType.values()) {
            when(player.getSkillLevel(skill)).thenReturn(level);
        }
        return player;
    }

    /** Limit Break ships OFF, so a test that wants damage from it must opt in, exactly as a player does. */
    private static void enableLimitBreak(Path dataFolder) throws Exception {
        Files.writeString(dataFolder.resolve("advanced.yml"),
                "Skills:\n    General:\n        LimitBreak:\n            AllowPVE: true\n");
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));
    }

    @Test
    void theShippedDefaultLeavesLimitBreakOff() {
        // ⚠️ Deliberate, and the opposite of what "implement it" first suggested. Against mobs the
        // bonus is un-nerfed, which is a large power increase, so it is the player's call. What
        // makes OFF honest rather than a relocation of the original defect is that off is also
        // silent -- see limitBreakIsInvisibleWhileDisabled below and the plaque gate in McMMOPlayer.
        assertFalse(McMMOMod.getAdvancedConfig().canApplyLimitBreakPVE(),
                "Skills.General.LimitBreak.AllowPVE must ship false -- it is opt-in");
        assertFalse(LimitBreak.isEnabled(), "and the gate must agree with the shipped default");
    }

    @Test
    void theEightAreExactlyWhatIsLimitBreakMatches() {
        // isLimitBreak drives the /mcstats and plaque suppression, so a sub-skill it wrongly claims
        // would vanish from the UI, and one it misses would keep advertising while disabled. Checked
        // against the whole enum in both directions rather than against the hand-kept list alone.
        for (SubSkillType subSkill : SubSkillType.values()) {
            assertEquals(ALL_EIGHT.contains(subSkill), LimitBreak.isLimitBreak(subSkill),
                    subSkill + " misclassified by isLimitBreak");
        }
        assertFalse(LimitBreak.isLimitBreak(null), "null is not a Limit Break");
    }

    @Test
    void bonusIsTheRankAndTheRankLaddersEveryHundredLevels(@TempDir Path dataFolder)
            throws Exception {
        enableLimitBreak(dataFolder);
        // Rank N grants exactly +N raw damage: legacy's getLimitBreakDamageAgainstQuality returns
        // (int) rank once the armour-quality nerfs are skipped, which is the only reachable path
        // here (see the class javadoc on the collapse).
        //
        // ⚠️ The ladder is the RETROMODE one -- 100, 200, ... 1000 -- because this port ships
        // General.RetroMode.Enabled: true, so skills scale 1-1000. RankConfig's no-scaling-arg
        // getter picks the section off GeneralConfig, so reading the Standard column (10, 20, ...)
        // out of skillranks.yml and assuming it applies is wrong by a factor of ten. That mistake
        // makes Limit Break look ten times more aggressive than it is: +1 at level 100, not +10.
        assertEquals(0, LimitBreak.bonusDamage(playerAtLevel(99),
                SubSkillType.SWORDS_SWORDS_LIMIT_BREAK), "rank 0 below level 100");
        assertEquals(1, LimitBreak.bonusDamage(playerAtLevel(100),
                SubSkillType.SWORDS_SWORDS_LIMIT_BREAK), "rank 1 at level 100");
        assertEquals(5, LimitBreak.bonusDamage(playerAtLevel(500),
                SubSkillType.SWORDS_SWORDS_LIMIT_BREAK), "rank 5 at level 500");
        assertEquals(10, LimitBreak.bonusDamage(playerAtLevel(1000),
                SubSkillType.SWORDS_SWORDS_LIMIT_BREAK), "rank 10 at level 1000");
        assertEquals(10, LimitBreak.bonusDamage(playerAtLevel(5000),
                SubSkillType.SWORDS_SWORDS_LIMIT_BREAK), "rank 10 is the ceiling");
    }

    @Test
    void mobsTakeTheFullUnNerfedBonus(@TempDir Path dataFolder) throws Exception {
        enableLimitBreak(dataFolder);
        // 🔑 Records the balance consequence of the collapse deliberately, because it is the single
        // most load-bearing number in this item. Legacy nerfs Limit Break by up to 75% against a
        // lightly-armoured PLAYER and passes a sentinel quality of 1000 for everything else, which
        // skips all three tiers. Every target in singleplayer is "everything else", so the bonus is
        // always the un-nerfed rank: +10 per hit at the 1000 level cap, against a diamond sword's
        // base 7. If someone later ports the armour table "for completeness", this should stop them.
        assertEquals(10, LimitBreak.bonusDamage(playerAtLevel(1000),
                SubSkillType.AXES_AXES_LIMIT_BREAK));
    }

    @Test
    void allEightWeaponsAreWiredAndNoneIsStuckAtZero(@TempDir Path dataFolder) throws Exception {
        enableLimitBreak(dataFolder);
        // ⚠️ Seven of these eight had ZERO production references before this item while all eight
        // shipped a rank plaque. A per-weapon typo (two arms pointing at the same constant, say)
        // would leave one silently dead again, so every one is asserted rather than a representative.
        final McMMOPlayer player = playerAtLevel(1000);
        final List<String> dead = new ArrayList<>();
        for (SubSkillType limitBreak : ALL_EIGHT) {
            if (LimitBreak.bonusDamage(player, limitBreak) != 10) {
                dead.add(limitBreak.name());
            }
        }
        assertTrue(dead.isEmpty(), "these Limit Breaks pay nothing at rank 10: " + dead);
        assertEquals(8, ALL_EIGHT.size(), "there are eight combat skills");
    }

    @Test
    void allowPveFalseShutsOffEveryWeapon() {
        // No opt-in: this is the shipped configuration installed by setUp.
        final McMMOPlayer player = playerAtLevel(1000);
        for (SubSkillType limitBreak : ALL_EIGHT) {
            assertFalse(LimitBreak.canUse(player, limitBreak), limitBreak + " must be gated off");
            assertEquals(0, LimitBreak.bonusDamage(player, limitBreak),
                    limitBreak + " must pay nothing when AllowPVE is false");
        }
    }

    @Test
    void aNullProfileContributesNothingRatherThanThrowing() {
        // The damage seam runs on every hit; a player whose data has not loaded must not crash it.
        assertFalse(LimitBreak.canUse(null, SubSkillType.SWORDS_SWORDS_LIMIT_BREAK));
        assertEquals(0, LimitBreak.bonusDamage(null, SubSkillType.SWORDS_SWORDS_LIMIT_BREAK));
    }
}
