package com.gmail.nossr50.skills.agility;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.gmail.nossr50.util.random.ProbabilityUtil;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The odds behind GitHub <b>#4</b> — "rolling never procs; holding shift on landing awards Sneak XP
 * instead" — pinned against the <em>real</em> shipped {@code advanced.yml} rather than a mocked one.
 *
 * <h2>What #4 actually was</h2>
 * Not a wiring bug. The seam fires, {@code canRoll()} passes ({@link SubSkillType#PARKOUR_ROLL} has
 * zero ranks, so it is always unlocked), and {@code isGraceful} reads real server-side sneak state.
 * The defect was the <b>gate</b>: Roll's odds are {@code skillLevel / MaxBonusLevel * ChanceMax}, and
 * while Roll was an {@code AGILITY_*} sub-skill that {@code skillLevel} was the <em>mean</em> of
 * Parkour, Swimming and Flying — even though the fall XP that earns it is paid to Parkour alone
 * ({@link AgilityManager#EPISODIC_XP_SKILL}). The reporter's save read
 * {@code PARKOUR 126 / SWIMMING 8 / FLYING 0}, i.e. Agility 44, i.e. a 4.4% roll and an 8.8% graceful
 * roll. Legacy Acrobatics had no such gap: it earned XP from the very falls Roll gates on.
 *
 * <p>{@link #theRollGateReadsParkourNotAgilitysThreeSkillMean()} is the regression: re-parent Roll
 * back under {@code AGILITY} and it, alone, goes red.
 */
class RollProbabilityTest {

    /** The reporter's live save, 2026-08-03 — the numbers the bug was measured on. */
    private static final int REPORTER_PARKOUR = 126;
    private static final int REPORTER_AGILITY_MEAN = 44; // (126 + 8 + 0) / 3

    private McMMOPlayer mmoPlayer;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));
        // SkillTools needs no wiring — McMMOMod builds it lazily on first access, and it is what
        // resolves PARKOUR_ROLL's parent skill.

        mmoPlayer = mock(McMMOPlayer.class);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
        McMMOMod.setAdvancedConfig(null);

    }

    /**
     * The odds of a plain (non-sneaking) Roll for the currently stubbed player, as a percentage.
     *
     * <p>⚠️ {@code Probability#getValue()} is on a <b>0–1</b> scale, not 0–100, despite
     * {@code ProbabilityUtil.chanceOfSuccessPercentage} being named for the latter and carrying a
     * comment about "transforming" to it — the display layer gets away with it because
     * {@code DecimalFormat("##0.00%")} does the ×100 itself. Converted here so the assertions below
     * can be read against {@code advanced.yml} directly.
     */
    private double rollPercent() {
        return ProbabilityUtil.getSubSkillProbability(SubSkillType.PARKOUR_ROLL, mmoPlayer)
                .getValue() * 100.0;
    }

    @Test
    void theRollGateReadsParkourNotAgilitysThreeSkillMean() {
        // Both levels are stubbed and they differ, so the assertion can only pass if the probability
        // resolved its parent skill to PARKOUR. Under the pre-#4 AGILITY parenting this reads 4.4.
        when(mmoPlayer.getSkillLevel(PrimarySkillType.PARKOUR)).thenReturn(REPORTER_PARKOUR);
        when(mmoPlayer.getSkillLevel(PrimarySkillType.AGILITY)).thenReturn(REPORTER_AGILITY_MEAN);

        assertEquals(12.6, rollPercent(), 1e-9,
                "126 Parkour / 1000 MaxBonusLevel * 100 ChanceMax");
    }

    @Test
    void gracefulRollIsExactlyTwiceThePlainOdds() {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.PARKOUR)).thenReturn(REPORTER_PARKOUR);

        assertEquals(2 * rollPercent(),
                ProbabilityUtil.getGracefulRollProbability(mmoPlayer).getValue() * 100.0, 1e-9,
                "sneaking on landing doubles the odds — the one thing the player controls");
    }

    @Test
    void theReparentingNearlyTriplesTheReportersOdds() {
        // The measured before/after for the exact save that produced the bug report. Kept as a single
        // explicit assertion because "we improved it" is not a spec — 4.4 -> 12.6 is.
        when(mmoPlayer.getSkillLevel(PrimarySkillType.PARKOUR)).thenReturn(REPORTER_PARKOUR);
        final double before = REPORTER_AGILITY_MEAN / 1000.0 * 100.0; // the old AGILITY-gated value

        assertEquals(4.4, before, 1e-9, "what the reporter actually had");
        assertTrue(rollPercent() > 2.8 * before,
                "re-parenting must be worth substantially more than a rounding change");
    }

    @Test
    void aMaxedParkourPlayerAlwaysRolls() {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.PARKOUR)).thenReturn(1000);

        assertEquals(100.0, rollPercent(), 1e-9, "ChanceMax is reached at MaxBonusLevel");
    }

    @Test
    void aLevelZeroPlayerNeverRolls() {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.PARKOUR)).thenReturn(0);

        assertEquals(0.0, rollPercent(), 1e-9,
                "the curve has no floor; Roll is earned, not granted");
    }

    @Test
    void rollsConfigAndLocaleAddressesFollowedItToParkour() {
        // These four strings are built by concatenation from the enum name, so a stale one is
        // invisible to grep and surfaces in-game as a literal "!Agility.SubSkill.Roll.Stat!" or as a
        // config read that silently answers with the hardcoded default.
        assertEquals("Skills.Parkour.Roll", SubSkillType.PARKOUR_ROLL.getAdvConfigAddress());
        assertEquals("Parkour.Roll", SubSkillType.PARKOUR_ROLL.getRankConfigAddress());
        assertEquals("Parkour.SubSkill.Roll", SubSkillType.PARKOUR_ROLL.getLocaleKeyRoot());
        assertEquals(PrimarySkillType.PARKOUR, SubSkillType.PARKOUR_ROLL.getParentSkill());
    }

    @Test
    void theShippedAdvancedYmlActuallyCarriesTheNewAddress() {
        // Guards the half of the move that Java cannot: the enum can point at Skills.Parkour.Roll
        // while advanced.yml still only defines Skills.Agility.Roll, in which case every read below
        // falls back to its hardcoded default and the YAML becomes a knob that does not turn.
        final AdvancedConfig advanced = McMMOMod.getAdvancedConfig();

        assertEquals(100.0, advanced.getMaximumProbability(SubSkillType.PARKOUR_ROLL), 1e-9);
        assertEquals(1000, advanced.getMaxBonusLevel(SubSkillType.PARKOUR_ROLL));
        assertEquals(7.0, advanced.getRollDamageThreshold(), 1e-9);
    }
}
