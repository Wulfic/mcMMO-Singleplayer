package com.gmail.nossr50.fabric.listeners;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.RankConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.ToolType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.skills.MeleeDamageBonus.MeleeWeapon;
import com.gmail.nossr50.skills.axes.AxesManager;
import com.gmail.nossr50.skills.swords.SwordsManager;
import com.gmail.nossr50.skills.unarmed.UnarmedManager;
import com.gmail.nossr50.util.player.UserManager;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers the <b>combat</b> half of the super-ability activation trigger
 * ({@link EntityDamageListener#maybeActivateSuperAbility}), which ports the
 * {@code if (manager.canActivateAbility()) mmoPlayer.checkAbilityActivation(...)} guard that opens
 * legacy's {@code CombatUtils#processSwordCombat} / {@code processAxeCombat} /
 * {@code processUnarmedCombat}.
 *
 * <p><b>Why this exists.</b> That guard was never ported. Serrated Strikes and Skull Splitter have no
 * block-strike path (unlike the five abilities in {@code SuperAbilityListener#onAttackBlock}), so
 * without it they could be <i>readied</i> but never <i>activated</i> — the AoE effect bodies already
 * wired in {@link EntityDamageListener} were unreachable, and Berserk lost its punch-a-mob activation.
 *
 * <p><b>What this proves, and what it does not.</b> It pins the weapon→skill dispatch and the
 * {@code canActivateAbility()} gate. It does <i>not</i> prove that
 * {@code applyAttackerWeaponBonus} still calls the dispatch — a missing call site is exactly the
 * defect this fixes, and re-deleting it would leave these tests green. That half is a live-swing
 * check (PLAYTEST_G.md session 2, items SS/SK).
 *
 * <p>{@code checkAbilityActivation} is verified as an interaction on a mocked {@link McMMOPlayer}
 * rather than executed: its success path needs {@code SkillTools}, the tick scheduler and a real held
 * item, which is the same boundary {@code SuperAbilityActivationTest} documents for the block half.
 */
class MeleeSuperAbilityActivationTest {

    private McMMOPlayer mmoPlayer;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));

        final PlatformPlayer platformPlayer = mock(PlatformPlayer.class);
        when(platformPlayer.getUniqueId())
                .thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000c7"));

        mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        UserManager.track(mmoPlayer);

        // Real managers over the mocked player: canActivateAbility() is the gate under test, and it
        // reads tool-preparation mode straight off the McMMOPlayer.
        when(mmoPlayer.getSwordsManager()).thenReturn(new SwordsManager(mmoPlayer));
        when(mmoPlayer.getAxesManager()).thenReturn(new AxesManager(mmoPlayer));
        when(mmoPlayer.getUnarmedManager()).thenReturn(new UnarmedManager(mmoPlayer));
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
        McMMOMod.setAdvancedConfig(null);
        UserManager.clearAll();
    }

    private void ready(ToolType toolType) {
        when(mmoPlayer.getToolPreparationMode(toolType)).thenReturn(true);
    }

    @Test
    void readiedSwordActivatesSwords() {
        ready(ToolType.SWORD);

        EntityDamageListener.maybeActivateSuperAbility(mmoPlayer, MeleeWeapon.SWORD);

        verify(mmoPlayer).checkAbilityActivation(PrimarySkillType.SWORDS);
    }

    @Test
    void readiedAxeActivatesAxes() {
        ready(ToolType.AXE);

        EntityDamageListener.maybeActivateSuperAbility(mmoPlayer, MeleeWeapon.AXE);

        verify(mmoPlayer).checkAbilityActivation(PrimarySkillType.AXES);
    }

    @Test
    void readiedFistsActivateUnarmed() {
        // Berserk's combat activation — legacy activates Unarmed off a punched mob as well as a
        // punched block, and only the block path was ported.
        ready(ToolType.FISTS);

        EntityDamageListener.maybeActivateSuperAbility(mmoPlayer, MeleeWeapon.UNARMED);

        verify(mmoPlayer).checkAbilityActivation(PrimarySkillType.UNARMED);
    }

    @Test
    void unreadiedToolDoesNotActivate() {
        // Nothing readied: canActivateAbility() is false for all three, so a swing must not activate
        // anything. This is the guard legacy wraps the call in.
        EntityDamageListener.maybeActivateSuperAbility(mmoPlayer, MeleeWeapon.SWORD);
        EntityDamageListener.maybeActivateSuperAbility(mmoPlayer, MeleeWeapon.AXE);
        EntityDamageListener.maybeActivateSuperAbility(mmoPlayer, MeleeWeapon.UNARMED);

        verify(mmoPlayer, never()).checkAbilityActivation(PrimarySkillType.SWORDS);
        verify(mmoPlayer, never()).checkAbilityActivation(PrimarySkillType.AXES);
        verify(mmoPlayer, never()).checkAbilityActivation(PrimarySkillType.UNARMED);
    }

    @Test
    void readiedToolOnlyActivatesItsOwnSkill() {
        // A readied sword must not activate Axes just because an axe swing came through — the
        // dispatch keys on the weapon in hand, not merely on what is prepared.
        ready(ToolType.SWORD);

        EntityDamageListener.maybeActivateSuperAbility(mmoPlayer, MeleeWeapon.AXE);

        verify(mmoPlayer, never()).checkAbilityActivation(PrimarySkillType.AXES);
        verify(mmoPlayer, never()).checkAbilityActivation(PrimarySkillType.SWORDS);
    }

    @Test
    void weaponsWithoutASuperAbilityNeverActivate() {
        // Maces and Tridents have no super ability; legacy has no activation call in their combat
        // paths either. Every tool readied, to prove the arms are inert by shape and not by gate.
        ready(ToolType.SWORD);
        ready(ToolType.AXE);
        ready(ToolType.FISTS);

        EntityDamageListener.maybeActivateSuperAbility(mmoPlayer, MeleeWeapon.MACE);
        EntityDamageListener.maybeActivateSuperAbility(mmoPlayer, MeleeWeapon.TRIDENT);
        EntityDamageListener.maybeActivateSuperAbility(mmoPlayer, MeleeWeapon.OTHER);

        verify(mmoPlayer, never()).checkAbilityActivation(PrimarySkillType.SWORDS);
        verify(mmoPlayer, never()).checkAbilityActivation(PrimarySkillType.AXES);
        verify(mmoPlayer, never()).checkAbilityActivation(PrimarySkillType.UNARMED);
    }
}
