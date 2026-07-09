package com.gmail.nossr50.skills.excavation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.RankConfig;
import com.gmail.nossr50.config.treasure.TreasureConfig;
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
 * Proves the Excavation Archaeology rank rewards and the treasure-table lookup against the real
 * bundled {@code treasures.yml} + {@code skillranks.yml}. The bundled {@code config.yml} ships
 * {@code RetroMode: Enabled: true}, so the wired ladder is the ×10 RetroMode one: Archaeology
 * unlocks Rank 1 at level 1, Rank 2 at 250, Rank 8 at 1000; orb reward == rank and chance == rank*2.
 */
class ExcavationManagerTest {

    private McMMOPlayer mmoPlayer;
    private ExcavationManager excavationManager;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));
        McMMOMod.setTreasureConfig(new TreasureConfig(dataFolder));

        final PlatformPlayer platformPlayer = mock(PlatformPlayer.class);
        when(platformPlayer.getUniqueId())
                .thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000e4"));

        mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        UserManager.track(mmoPlayer);

        excavationManager = new ExcavationManager(mmoPlayer);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
        McMMOMod.setTreasureConfig(null);
        UserManager.clearAll();
    }

    private void atExcavationLevel(int level) {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.EXCAVATION)).thenReturn(level);
    }

    @Test
    void archaeologyRankIsZeroBelowUnlock() {
        atExcavationLevel(0); // below Rank 1 (level 1)
        assertEquals(0, excavationManager.getArchaeologyRank());
        assertEquals(0, excavationManager.getExperienceOrbsReward());
        assertEquals(0.0, excavationManager.getArchaelogyExperienceOrbChance());
    }

    @Test
    void archaeologyRankScalesWithSkillLevel() {
        atExcavationLevel(1); // Rank 1
        assertEquals(1, excavationManager.getArchaeologyRank());
        assertEquals(1, excavationManager.getExperienceOrbsReward());
        assertEquals(2.0, excavationManager.getArchaelogyExperienceOrbChance());

        atExcavationLevel(250); // Rank 2 (RetroMode)
        assertEquals(2, excavationManager.getArchaeologyRank());

        atExcavationLevel(1000); // Rank 8 (max, RetroMode)
        assertEquals(8, excavationManager.getArchaeologyRank());
        assertEquals(8, excavationManager.getExperienceOrbsReward());
        assertEquals(16.0, excavationManager.getArchaelogyExperienceOrbChance());
    }

    @Test
    void getTreasuresMapsBlockRegistryPathToTable() {
        // "mud" -> config key "Mud"; heart_of_the_sea is a Mud-sourced treasure.
        assertFalse(excavationManager.getTreasures("mud").isEmpty());
        assertTrue(excavationManager.getTreasures("mud").stream()
                .anyMatch(t -> t.getDrop().getMaterialId().equals("heart_of_the_sea")));
        assertFalse(excavationManager.getTreasures("dirt").isEmpty());
        // A namespaced id resolves the same way.
        assertFalse(excavationManager.getTreasures("minecraft:dirt").isEmpty());
        // A block with no treasure table yields an empty list.
        assertTrue(excavationManager.getTreasures("bedrock").isEmpty());
    }

    @Test
    void treasureRollIsEmptyForBlockWithoutTable() {
        // Bedrock has no Excavation treasure table, so the roll short-circuits to the shared EMPTY
        // sentinel before any RNG — deterministic even for a maxed player.
        atExcavationLevel(1000);
        final ExcavationManager.ExcavationRewards rewards =
                excavationManager.rollTreasureRewards("bedrock");
        assertSame(ExcavationManager.ExcavationRewards.EMPTY, rewards,
                "no treasure table → shared EMPTY result");
        assertTrue(rewards.isEmpty());
        assertTrue(rewards.treasures().isEmpty());
        assertTrue(rewards.experienceOrbs().isEmpty());
        assertEquals(0, rewards.treasureXp());
    }
}
