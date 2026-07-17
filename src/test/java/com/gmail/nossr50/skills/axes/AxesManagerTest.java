package com.gmail.nossr50.skills.axes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.RankConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformItem;
import com.gmail.nossr50.platform.PlatformLivingEntity;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.util.player.UserManager;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the Axes damage-math cores (Phase 10.3) against the real bundled configs. Axe Mastery bonus
 * is {@code rank * RankDamageMultiplier(1.0)}; Armor Impact durability damage is
 * {@code rank * DamagePerRank(6.5)}. With RetroMode on, Axe Mastery unlocks at level 50 (rank 1) and
 * Armor Impact at level 1 (rank 1), each reaching rank 2 at level 100 in {@code skillranks.yml}.
 */
class AxesManagerTest {

    private PlatformPlayer platformPlayer;
    private McMMOPlayer mmoPlayer;
    private AxesManager axesManager;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));

        platformPlayer = mock(PlatformPlayer.class);
        when(platformPlayer.getUniqueId())
                .thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000b3"));

        mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        UserManager.track(mmoPlayer);

        axesManager = new AxesManager(mmoPlayer);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
        McMMOMod.setAdvancedConfig(null);
        UserManager.clearAll();
    }

    private void atAxesLevel(int level) {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.AXES)).thenReturn(level);
    }

    @Test
    void axeMasteryBonusScalesWithRank() {
        atAxesLevel(0); // rank 0 → 0 bonus
        assertEquals(0.0D, Axes.getAxeMasteryBonusDamage(platformPlayer), 1e-9, "rank 0 → 0");

        atAxesLevel(50); // rank 1 → 1 * 1.0 = 1.0
        assertEquals(1.0D, Axes.getAxeMasteryBonusDamage(platformPlayer), 1e-9, "rank 1 → 1.0");

        atAxesLevel(100); // rank 2 → 2 * 1.0 = 2.0
        assertEquals(2.0D, Axes.getAxeMasteryBonusDamage(platformPlayer), 1e-9, "rank 2 → 2.0");
    }

    /**
     * Skull Splitter's AoE damage is {@code (damage / DamageModifier(2.0)) * attackStrength}. The
     * attack-strength scaling is the documented asymmetry with Serrated Strikes, which is not scaled.
     */
    @Test
    void skullSplitterDamageIsHalvedAndScaledByAttackStrength() {
        when(mmoPlayer.getAttackStrength()).thenReturn(1.0F); // fully charged swing
        assertEquals(5.0D, axesManager.skullSplitterDamage(10.0D), 1e-9,
                "full-strength swing → damage / 2.0");

        when(mmoPlayer.getAttackStrength()).thenReturn(0.5F); // half-charged (spam-clicked) swing
        assertEquals(2.5D, axesManager.skullSplitterDamage(10.0D), 1e-9,
                "half-strength swing → half the AoE damage");
    }

    @Test
    void skullSplitterGateNeedsUnlockActiveAbilityAndLiveTarget() {
        final PlatformLivingEntity target = mock(PlatformLivingEntity.class);
        when(target.isValid()).thenReturn(true);
        when(mmoPlayer.getAbilityMode(SuperAbilityType.SKULL_SPLITTER)).thenReturn(true);

        atAxesLevel(49); // one short of the RetroMode Rank_1 unlock
        assertFalse(axesManager.canUseSkullSplitter(target), "locked below rank 1");

        atAxesLevel(50); // Skull Splitter Rank_1
        assertTrue(axesManager.canUseSkullSplitter(target), "unlocked + ability active → fires");

        when(mmoPlayer.getAbilityMode(SuperAbilityType.SKULL_SPLITTER)).thenReturn(false);
        assertFalse(axesManager.canUseSkullSplitter(target), "unlocked but ability not active");

        when(mmoPlayer.getAbilityMode(SuperAbilityType.SKULL_SPLITTER)).thenReturn(true);
        when(target.isValid()).thenReturn(false);
        assertFalse(axesManager.canUseSkullSplitter(target), "dead/removed target is never struck");
    }

    /**
     * The RNG-driven Axes sub-skills roll {@code odds * attackStrength} (see CONVERSION_TODO §F #7),
     * and {@code isSuccessfulRoll} succeeds when the value is {@code >= nextDouble(1.0)} — so an
     * exaggerated attack strength saturates any non-zero chance and a zero one can never win. That
     * makes these bodies deterministic without injecting a fake RNG.
     */
    private void forceProcs() {
        when(mmoPlayer.getAttackStrength()).thenReturn(100.0F);
    }

    private void forceNoProcs() {
        when(mmoPlayer.getAttackStrength()).thenReturn(0.0F);
    }

    /** A live target wearing {@code armor} (empty varargs → an unarmored target). */
    private PlatformLivingEntity targetWearing(PlatformItem... armor) {
        final PlatformLivingEntity target = mock(PlatformLivingEntity.class);
        when(target.isValid()).thenReturn(true);
        when(target.getArmorPieces()).thenReturn(List.of(armor));
        return target;
    }

    /** A pristine, undamaged, unenchanted armor piece. */
    private PlatformItem intactArmor() {
        final PlatformItem armor = mock(PlatformItem.class);
        when(armor.isUnbreakable()).thenReturn(false);
        when(armor.getDurability()).thenReturn(0);
        when(armor.getMaxDurability()).thenReturn(100);
        when(armor.getUnbreakingLevel()).thenReturn(0);
        return armor;
    }

    /**
     * Armor Impact and Greater Impact are deliberately mutually exclusive: {@code hasArmor} routes an
     * armored target to the durability effect and an unarmored one to the knockback effect. Both are
     * unlocked here (RetroMode: ArmorImpact Rank_1 = 1, GreaterImpact Rank_1 = 250).
     */
    @Test
    void armorImpactAndGreaterImpactSplitOnWhetherTheTargetIsArmored() {
        atAxesLevel(250);

        final PlatformLivingEntity armored = targetWearing(intactArmor());
        assertTrue(Axes.hasArmor(armored), "a worn armor piece counts as armor");
        assertTrue(axesManager.canImpact(armored), "armored target → Armor Impact");
        assertFalse(axesManager.canGreaterImpact(armored), "armored target → never Greater Impact");

        final PlatformLivingEntity naked = targetWearing();
        assertFalse(Axes.hasArmor(naked), "no worn armor → not armored");
        assertFalse(axesManager.canImpact(naked), "unarmored target → never Armor Impact");
        assertTrue(axesManager.canGreaterImpact(naked), "unarmored target → Greater Impact");
    }

    @Test
    void hasArmorIsFalseForADeadTarget() {
        final PlatformLivingEntity target = targetWearing(intactArmor());
        when(target.isValid()).thenReturn(false);

        assertFalse(Axes.hasArmor(target), "a dead/removed target is never armored");
    }

    /**
     * Armor Impact rolls per piece and wears down the ones that proc. At Axes rank 1 the durability
     * damage is 6.5, which {@code handleArmorDurabilityChange} truncates to 6 on an unenchanted
     * piece.
     */
    @Test
    void impactCheckDamagesEachArmorPieceThatProcs() {
        atAxesLevel(1);
        forceProcs();
        final PlatformItem helmet = intactArmor();
        final PlatformItem boots = intactArmor();

        axesManager.impactCheck(targetWearing(helmet, boots));

        verify(helmet).setDurability(6);
        verify(boots).setDurability(6);
    }

    @Test
    void impactCheckLeavesArmorAloneWhenTheRollFails() {
        atAxesLevel(1);
        forceNoProcs();
        final PlatformItem helmet = intactArmor();

        axesManager.impactCheck(targetWearing(helmet));

        verify(helmet, never()).setDurability(anyInt());
    }

    /**
     * Greater Impact returns its flat bonus damage (2.0) and flings the target along the player's
     * look direction by the configured knockback modifier (1.5).
     */
    @Test
    void greaterImpactKnocksTheTargetBackAndReturnsBonusDamage() {
        atAxesLevel(250);
        forceProcs();
        final PlatformLivingEntity target = targetWearing();

        assertEquals(2.0D, axesManager.greaterImpact(target), 1e-9, "proc → BonusDamage");
        verify(target).setVelocityAlongLookDirection(platformPlayer, 1.5D);
    }

    @Test
    void greaterImpactDoesNothingWhenTheRollFails() {
        atAxesLevel(250);
        forceNoProcs();
        final PlatformLivingEntity target = targetWearing();

        assertEquals(0.0D, axesManager.greaterImpact(target), 1e-9, "no proc → no bonus damage");
        verify(target, never()).setVelocityAlongLookDirection(any(PlatformPlayer.class), anyDouble());
    }

    /**
     * Critical Strikes returns the <em>delta</em> the crit adds, not the new total: legacy computed
     * {@code (damage * modifier) - damage}. Singleplayer always takes the PVE modifier (2.0), so a
     * crit doubles the hit — a delta equal to the incoming damage.
     */
    @Test
    void criticalHitReturnsThePveModifierDelta() {
        atAxesLevel(1000); // Critical Strikes ChanceMax (37.5%) is reached at RetroMode level 1000
        forceProcs();

        assertEquals(10.0D, axesManager.criticalHit(10.0D), 1e-9, "PVE 2.0× → delta == damage");
    }

    @Test
    void criticalHitContributesNothingWhenTheRollFails() {
        atAxesLevel(1000);
        forceNoProcs();

        assertEquals(0.0D, axesManager.criticalHit(10.0D), 1e-9, "no proc → no bonus damage");
    }

    @Test
    void impactDurabilityDamageScalesWithRank() {
        atAxesLevel(0); // Armor Impact rank 0 → 0
        assertEquals(0.0D, axesManager.getImpactDurabilityDamage(), 1e-9, "rank 0 → 0");

        atAxesLevel(1); // Armor Impact rank 1 → 1 * 6.5
        assertEquals(6.5D, axesManager.getImpactDurabilityDamage(), 1e-9, "rank 1 → 6.5");

        atAxesLevel(100); // Armor Impact rank 2 → 2 * 6.5
        assertEquals(13.0D, axesManager.getImpactDurabilityDamage(), 1e-9, "rank 2 → 13.0");
    }
}
