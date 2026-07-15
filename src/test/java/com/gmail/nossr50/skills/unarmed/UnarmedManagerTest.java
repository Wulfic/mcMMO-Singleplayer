package com.gmail.nossr50.skills.unarmed;

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
 * Proves the numeric core of the Unarmed manager (Phase 10.3) against the real bundled configs.
 *
 * <p>Berserk is pure arithmetic ({@code damage * 1.5 * attackStrength - damage}). Steel Arm Style is
 * rank arithmetic with the config {@code Damage_Override} off (the bundled default): the bonus is
 * {@code 0.5 + rank/2} below rank 18. With RetroMode on, Steel Arm Style unlocks at level 1 and
 * climbs 1 → 100 → 150 (ranks 1 → 3) in {@code skillranks.yml}.
 */
class UnarmedManagerTest {

    private PlatformPlayer platformPlayer;
    private McMMOPlayer mmoPlayer;
    private UnarmedManager unarmedManager;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));

        platformPlayer = mock(PlatformPlayer.class);
        when(platformPlayer.getUniqueId())
                .thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000b1"));

        mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        UserManager.track(mmoPlayer);

        unarmedManager = new UnarmedManager(mmoPlayer);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
        McMMOMod.setAdvancedConfig(null);
        UserManager.clearAll();
    }

    private void atUnarmedLevel(int level) {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.UNARMED)).thenReturn(level);
    }

    @Test
    void berserkAddsHalfAgainAtFullCharge() {
        when(mmoPlayer.getAttackStrength()).thenReturn(1.0f);
        // (10 * 1.5 * 1.0) - 10 = 5
        assertEquals(5.0D, unarmedManager.berserkDamage(10.0D), 1e-9,
                "full-charge Berserk adds +50% as bonus damage");
    }

    @Test
    void berserkScalesWithAttackCharge() {
        when(mmoPlayer.getAttackStrength()).thenReturn(0.5f);
        // (10 * 1.5 * 0.5) - 10 = -2.5
        assertEquals(-2.5D, unarmedManager.berserkDamage(10.0D), 1e-9,
                "a half-charged swing scales the Berserk bonus down");
    }

    @Test
    void steelArmStyleScalesWithRank() {
        atUnarmedLevel(1); // rank 1 → 0.5 + 1/2 = 1.0
        assertEquals(1.0D, unarmedManager.getSteelArmStyleDamage(), 1e-9, "rank 1 → 1.0");

        atUnarmedLevel(150); // rank 3 → 0.5 + 3/2 = 2.0
        assertEquals(2.0D, unarmedManager.getSteelArmStyleDamage(), 1e-9, "rank 3 → 2.0");
    }

    /**
     * Arrow Deflect unlocks at Unarmed 200 in RetroMode ({@code skillranks.yml} ArrowDeflect
     * Rank_1), and legacy checks the rank before ever looking at the held item.
     */
    @Test
    void arrowDeflectGateNeedsUnlock() {
        when(platformPlayer.isUnarmed()).thenReturn(true);

        atUnarmedLevel(199); // one short of the RetroMode Rank_1 unlock
        assertFalse(unarmedManager.canDeflect(), "locked below rank 1");

        atUnarmedLevel(200); // ArrowDeflect Rank_1
        assertTrue(unarmedManager.canDeflect(), "level 200 + bare-handed → Arrow Deflect available");
    }

    /**
     * You cannot swat an arrow away with a weapon in your hand: legacy gates {@code canDeflect} on
     * {@code ItemUtils.isUnarmed(mainHand)}, which this port reads through the platform adapter.
     */
    @Test
    void arrowDeflectGateNeedsAnEmptyHand() {
        atUnarmedLevel(1000); // well past the unlock, so only the held item can decide this

        when(platformPlayer.isUnarmed()).thenReturn(false);
        assertFalse(unarmedManager.canDeflect(), "holding a tool → nothing to deflect with");

        when(platformPlayer.isUnarmed()).thenReturn(true);
        assertTrue(unarmedManager.canDeflect(), "bare-handed → deflect available");
    }
}
