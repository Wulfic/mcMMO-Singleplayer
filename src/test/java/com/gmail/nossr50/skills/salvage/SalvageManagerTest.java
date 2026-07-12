package com.gmail.nossr50.skills.salvage;

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
 * Proves the Salvage numeric cores (Phase 10.3) against the real bundled configs.
 *
 * <p>RetroMode is on by default (skillranks.yml): Scrap Collector ranks unlock at Salvage levels
 * {1,100,150,200,250,300,350,400}; Arcane Salvage ranks at {100,250,350,500,650,750,850,1000}.
 * advanced.yml: Arcane Salvage ExtractFullEnchant Rank_1 2.5.
 */
class SalvageManagerTest {

    private McMMOPlayer mmoPlayer;
    private PlatformPlayer platformPlayer;
    private SalvageManager salvageManager;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));

        platformPlayer = mock(PlatformPlayer.class);
        when(platformPlayer.getUniqueId())
                .thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000c1"));

        mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        UserManager.track(mmoPlayer);

        salvageManager = new SalvageManager(mmoPlayer);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
        McMMOMod.setAdvancedConfig(null);
        UserManager.clearAll();
    }

    private void atSalvageLevel(int level) {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.SALVAGE)).thenReturn(level);
    }

    @Test
    void salvageableAmountScalesWithDamage() {
        // pristine (0 damage) -> full base yield
        assertEquals(5, SalvageManager.calculateSalvageableAmount(0, (short) 100, 5));
        // half-damaged -> floor(5 * 0.5) = 2
        assertEquals(2, SalvageManager.calculateSalvageableAmount(50, (short) 100, 5));
        // fully damaged -> nothing
        assertEquals(0, SalvageManager.calculateSalvageableAmount(100, (short) 100, 5));
    }

    @Test
    void salvageLimitIsOneAtRankOneThenDoublesPerRank() {
        atSalvageLevel(1); // Scrap Collector rank 1
        assertEquals(1, SalvageManager.getSalvageLimit(platformPlayer), "rank 1 -> exactly 1");
        atSalvageLevel(100); // rank 2 (RetroMode)
        assertEquals(4, SalvageManager.getSalvageLimit(platformPlayer), "rank 2 -> 4");
        atSalvageLevel(400); // rank 8 (max)
        assertEquals(16, SalvageManager.getSalvageLimit(platformPlayer), "rank 8 -> 16");
    }

    @Test
    void arcaneSalvageRankAndFullEnchantChanceLadder() {
        atSalvageLevel(0);
        assertEquals(0, salvageManager.getArcaneSalvageRank(), "no Arcane Salvage below 100");
        assertEquals(0.0, salvageManager.getExtractFullEnchantChance(), 1.0e-9);

        atSalvageLevel(100); // Arcane Salvage rank 1 (RetroMode)
        assertEquals(1, salvageManager.getArcaneSalvageRank());
        assertEquals(2.5, salvageManager.getExtractFullEnchantChance(), 1.0e-9);
    }

    @Test
    void failedAllEnchantsWhenEveryExtractFailed() {
        assertEquals(true, salvageManager.failedAllEnchants(3, 3));
        assertEquals(false, salvageManager.failedAllEnchants(2, 3));
    }

    @Test
    void checkConfirmationArmsOnFirstClickThenProceedsOnSecond() {
        // Confirm_Required defaults true. A stale last-use is expired -> first click only arms
        // (returns false); recording "now" makes the next click within the 3s window proceed.
        salvageManager.setLastAnvilUse(0);
        assertFalse(salvageManager.checkConfirmation(true), "first click merely arms the salvage");

        salvageManager.setLastAnvilUse((int) (System.currentTimeMillis() / 1000L));
        assertTrue(salvageManager.checkConfirmation(true),
                "a second click within the window proceeds");
    }

    @Test
    void anvilPlacementAndUsageStateRoundTrips() {
        assertEquals(false, salvageManager.getPlacedAnvil());
        salvageManager.togglePlacedAnvil();
        assertEquals(true, salvageManager.getPlacedAnvil());

        salvageManager.setLastAnvilUse(99);
        assertEquals(99, salvageManager.getLastAnvilUse());
    }
}
