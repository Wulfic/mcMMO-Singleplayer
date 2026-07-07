package com.gmail.nossr50.skills.mining;

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
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.util.player.UserManager;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the Mining Blast-Mining numeric cores (Phase 10.3) against the real bundled configs.
 *
 * <p>RetroMode is on by default, so Blast Mining ranks 1..8 unlock at mining levels
 * {100, 250, 350, 500, 650, 750, 850, 1000} ({@code skillranks.yml}). The per-rank Blast Mining
 * tuning ({@code advanced.yml}): OreBonus % = {35,40,45,50,55,60,65,70}, BlastDamageDecrease % =
 * {0,0,0,25,25,50,50,100}, BlastRadiusModifier = {1,1,2,2,3,3,4,4}, DebrisReduction % =
 * {10,20,30,30,30,30,30,30}, config DropMultiplier = {1,1,1,1,2,2,3,3}.
 */
class MiningManagerTest {

    private McMMOPlayer mmoPlayer;
    private MiningManager miningManager;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));

        PlatformPlayer platformPlayer = mock(PlatformPlayer.class);
        when(platformPlayer.getUniqueId())
                .thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000d1"));

        mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        UserManager.track(mmoPlayer);

        miningManager = new MiningManager(mmoPlayer);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
        McMMOMod.setAdvancedConfig(null);
        UserManager.clearAll();
    }

    private void atMiningLevel(int level) {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.MINING)).thenReturn(level);
    }

    @Test
    void blastMiningTierLaddersWithMiningLevel() {
        atMiningLevel(0);
        assertEquals(0, miningManager.getBlastMiningTier(), "below rank 1 → tier 0");
        atMiningLevel(99);
        assertEquals(0, miningManager.getBlastMiningTier(), "99 < 100 → tier 0");
        atMiningLevel(100);
        assertEquals(1, miningManager.getBlastMiningTier(), "100 → tier 1");
        atMiningLevel(350);
        assertEquals(3, miningManager.getBlastMiningTier(), "350 → tier 3");
        atMiningLevel(999);
        assertEquals(7, miningManager.getBlastMiningTier(), "999 < 1000 → tier 7");
        atMiningLevel(1000);
        assertEquals(8, miningManager.getBlastMiningTier(), "1000 → tier 8 (max)");
    }

    @Test
    void oreBonusScalesWithTierAsAFraction() {
        atMiningLevel(100); // tier 1 → 35%
        assertEquals(0.35f, miningManager.getOreBonus(), 1.0e-6f);
        atMiningLevel(1000); // tier 8 → 70%
        assertEquals(0.70f, miningManager.getOreBonus(), 1.0e-6f);
    }

    @Test
    void dropMultiplierUsesHardcodedTierSwitch() {
        atMiningLevel(0); // tier 0
        assertEquals(0, miningManager.getDropMultiplier());
        atMiningLevel(100); // tier 1 → 1
        assertEquals(1, miningManager.getDropMultiplier());
        atMiningLevel(650); // tier 5 → 2
        assertEquals(2, miningManager.getDropMultiplier());
        atMiningLevel(850); // tier 7 → 3
        assertEquals(3, miningManager.getDropMultiplier());
    }

    @Test
    void staticConfigLookupsReadAdvancedYml() {
        assertEquals(35.0, MiningManager.getOreBonus(1), 1.0e-9);
        assertEquals(10.0, MiningManager.getDebrisReduction(1), 1.0e-9);
        assertEquals(1, MiningManager.getDropMultiplier(1), "config DropMultiplier rank 1");
        assertEquals(2, MiningManager.getDropMultiplier(5), "config DropMultiplier rank 5");
    }

    @Test
    void biggerBombsAddsTheRadiusModifier() {
        atMiningLevel(350); // tier 3 → radius modifier 2.0
        assertEquals(7.0f, miningManager.biggerBombs(5.0f), 1.0e-6f);
    }

    @Test
    void demolitionsExpertiseReducesDamageByTierPercent() {
        atMiningLevel(500); // tier 4 → 25% damage decrease
        assertEquals(75.0, miningManager.processDemolitionsExpertise(100.0), 1.0e-9);
        atMiningLevel(1000); // tier 8 → 100% decrease → no damage
        assertEquals(0.0, miningManager.processDemolitionsExpertise(100.0), 1.0e-9);
    }

    @Test
    void blastMiningUnlockLevelsDeriveFromFirstPositiveRank() {
        // BlastDamageDecrease first > 0 at rank 4 → unlock level 500 (RetroMode).
        assertEquals(500, BlastMining.getDemolitionExpertUnlockLevel());
        // BlastRadiusModifier > 0 from rank 1 → unlock level 100 (RetroMode).
        assertEquals(100, BlastMining.getBiggerBombsUnlockLevel());
    }

    @Test
    void eligibilityGatesFollowRankAndUnlockLevel() {
        atMiningLevel(99);
        assertFalse(miningManager.canUseBlastMining(), "no blast mining rank yet");
        assertFalse(miningManager.canUseBiggerBombs(), "bigger bombs needs level 100");
        assertFalse(miningManager.canUseDemolitionsExpertise(), "demo needs level 500");

        atMiningLevel(100);
        assertTrue(miningManager.canUseBlastMining(), "rank 1 unlocked at 100");
        assertTrue(miningManager.canUseBiggerBombs(), "bigger bombs unlocked at 100");
        assertFalse(miningManager.canUseDemolitionsExpertise(), "demo still gated (needs 500)");

        atMiningLevel(500);
        assertTrue(miningManager.canUseDemolitionsExpertise(), "demo unlocked at 500");

        // Double drops unlocks at mining level 1; MotherLode is permission-only (always allowed).
        atMiningLevel(0);
        assertFalse(miningManager.canDoubleDrop(), "double drops needs level 1");
        atMiningLevel(1);
        assertTrue(miningManager.canDoubleDrop(), "double drops unlocked at 1");
        assertTrue(miningManager.canMotherLode(), "mother lode is always permitted");
    }

    @Test
    void isDropIllegalGuardsUnobtainableBlocks() {
        assertTrue(miningManager.isDropIllegal("spawner"));
        assertTrue(miningManager.isDropIllegal("SPAWNER"), "case-insensitive");
        assertTrue(miningManager.isDropIllegal("budding_amethyst"));
        assertTrue(miningManager.isDropIllegal("infested_deepslate"));
        assertTrue(miningManager.isDropIllegal("infested_stone_bricks"));
        assertFalse(miningManager.isDropIllegal("stone"));
        assertFalse(miningManager.isDropIllegal("coal_ore"));
    }
}
