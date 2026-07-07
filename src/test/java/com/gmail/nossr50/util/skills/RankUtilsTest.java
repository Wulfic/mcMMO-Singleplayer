package com.gmail.nossr50.util.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.RankConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
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
 * Exercises the Phase 10.2 {@link RankUtils} port MC-free. The rank ladder is read from the real
 * bundled {@code skillranks.yml}; the bundled {@code config.yml} enables RetroMode, so the active
 * {@code Tridents.Impale} unlock levels are the RetroMode table (50, 150, 250 … 1000).
 *
 * <p>Both {@link GeneralConfig} (drives the retro-vs-standard scaling pick) and {@link RankConfig}
 * are wired through {@link McMMOMod} and reset to {@code null} afterwards, and the lazily-built rank
 * cache is cleared per-test so scaling stays deterministic regardless of suite ordering.
 */
class RankUtilsTest {

    private static final SubSkillType IMPALE = SubSkillType.TRIDENTS_IMPALE;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));
        RankUtils.resetRankCache();
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
        RankUtils.resetRankCache();
        UserManager.clearAll();
    }

    private McMMOPlayer playerAtTridentsLevel(int level) {
        final McMMOPlayer mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getSkillLevel(PrimarySkillType.TRIDENTS)).thenReturn(level);
        return mmoPlayer;
    }

    @Test
    void unlockLevelsMatchBundledRetroTable() {
        assertEquals(50, RankUtils.getUnlockLevel(IMPALE), "rank 1 unlocks at 50 in RetroMode");
        assertEquals(50, RankUtils.getRankUnlockLevel(IMPALE, 1));
        assertEquals(150, RankUtils.getRankUnlockLevel(IMPALE, 2));
        assertEquals(1000, RankUtils.getRankUnlockLevel(IMPALE, 10));
    }

    @Test
    void getRankClimbsWithParentLevel() {
        assertEquals(0, RankUtils.getRank(playerAtTridentsLevel(49), IMPALE), "below rank 1 → 0");
        assertEquals(1, RankUtils.getRank(playerAtTridentsLevel(50), IMPALE), "exactly rank 1");
        assertEquals(1, RankUtils.getRank(playerAtTridentsLevel(149), IMPALE), "just below rank 2");
        assertEquals(2, RankUtils.getRank(playerAtTridentsLevel(150), IMPALE), "exactly rank 2");
        assertEquals(10, RankUtils.getRank(playerAtTridentsLevel(1000), IMPALE), "capped at max rank");
        assertEquals(10, RankUtils.getRank(playerAtTridentsLevel(5000), IMPALE), "never exceeds max");
    }

    @Test
    void getRankReturnsZeroForNullPlayer() {
        assertEquals(0, RankUtils.getRank((McMMOPlayer) null, IMPALE),
                "no player data → rank 0 (legacy parity)");
    }

    @Test
    void platformOverloadResolvesTrackedPlayer() {
        final UUID uuid = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
        final PlatformPlayer platformPlayer = mock(PlatformPlayer.class);
        when(platformPlayer.getUniqueId()).thenReturn(uuid);

        final McMMOPlayer mmoPlayer = playerAtTridentsLevel(150);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        UserManager.track(mmoPlayer);

        assertEquals(2, RankUtils.getRank(platformPlayer, IMPALE),
                "platform overload resolves the tracked McMMOPlayer and returns its rank");
    }

    @Test
    void platformOverloadReturnsZeroForUntrackedPlayer() {
        final PlatformPlayer platformPlayer = mock(PlatformPlayer.class);
        when(platformPlayer.getUniqueId()).thenReturn(UUID.randomUUID());

        assertEquals(0, RankUtils.getRank(platformPlayer, IMPALE),
                "untracked player has no data → rank 0");
    }

    @Test
    void hasReachedRankAndUnlocked() {
        final McMMOPlayer atRank2 = playerAtTridentsLevel(150);
        assertTrue(RankUtils.hasUnlockedSubskill(atRank2, IMPALE));
        assertTrue(RankUtils.hasReachedRank(2, atRank2, IMPALE));
        assertFalse(RankUtils.hasReachedRank(3, atRank2, IMPALE));

        assertFalse(RankUtils.hasUnlockedSubskill(playerAtTridentsLevel(0), IMPALE),
                "no rank yet → not unlocked");
    }

    @Test
    void highestRankAndMaxRankDetection() {
        assertEquals(10, RankUtils.getHighestRank(IMPALE));
        assertEquals("10", RankUtils.getHighestRankStr(IMPALE));
        assertTrue(RankUtils.isPlayerMaxRankInSubSkill(playerAtTridentsLevel(1000), IMPALE));
        assertFalse(RankUtils.isPlayerMaxRankInSubSkill(playerAtTridentsLevel(50), IMPALE));
    }

    @Test
    void superAbilityUnlockRequirementReadsRankOneOfItsSubskill() {
        final SubSkillType def = SuperAbilityType.SUPER_BREAKER.getSubSkillTypeDefinition();
        final int expected = McMMOMod.getRankConfig().getSubSkillUnlockLevel(def, 1, true);

        assertEquals(expected, RankUtils.getSuperAbilityUnlockRequirement(SuperAbilityType.SUPER_BREAKER),
                "super-ability unlock == rank 1 of its defining subskill under the active scaling");
    }
}
