package com.gmail.nossr50.skills.maces;

import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformLivingEntity;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Maces skill manager. Carries the Crush damage math (composed into the melee hit total by
 * {@link com.gmail.nossr50.skills.MeleeDamageBonus}) and the Cripple on-hit Slowness effect (driven
 * from {@code fabric.listeners.EntityDamageListener} after the hit is settled, alongside Rupture).
 *
 * <p>Legacy's {@code mockSpigotMatch} / {@code slowEffectType} registry-lookup plumbing is dropped:
 * it existed to resolve the Slowness {@code PotionEffectType} across Bukkit versions, whereas the
 * vanilla constant {@link net.minecraft.entity.effect.StatusEffects#SLOWNESS} is fixed. The Slowness
 * application itself lives behind {@link PlatformLivingEntity#applySlowness}, keeping this class
 * MC-free and unit-testable. The Cripple activation particle is deferred with the other combat
 * particles (Dodge/Rupture) until a particle adapter lands.
 */
public class MacesManager extends SkillManager {
    public MacesManager(McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.MACES);
    }

    /**
     * Get the Crush damage bonus.
     *
     * @return the Crush damage bonus.
     */
    public double getCrushDamage() {
        if (!Permissions.canUseSubSkill(getPlayer(), SubSkillType.MACES_CRUSH)) {
            return 0;
        }

        int rank = RankUtils.getRank(getPlayer(), SubSkillType.MACES_CRUSH);

        if (rank > 0) {
            return (0.5D + (rank * 1.D));
        }

        return 0;
    }

    /**
     * Process a Cripple attempt against a target the player just hit (and did not kill). Ports legacy
     * {@code MacesManager#processCripple}: if the target is not already slowed, roll the rank's
     * Chance_To_Apply_On_Hit (scaled by attack strength) and, on success, apply Slowness.
     *
     * <p>⚠️ FIXED UPSTREAM BUG (CONVERSION_TODO §F #9 — rank-0 array-index landmine): legacy gates
     * this on {@code Permissions.canUseSubSkill(MACES_CRIPPLE)}, a permission node that does <em>not</em>
     * imply a rank. At rank 0 the odds lookup {@code getCrippleChanceToApplyOnHit(0)} indexes
     * {@code defaultCrippleValues[-1]} — evaluated eagerly as the {@code getDouble} default argument —
     * and throws {@link ArrayIndexOutOfBoundsException}. The vendored {@code AdvancedConfig} carries the
     * identical pattern, and this port's permission check is unconditionally {@code true}, so a
     * mace-swinging player below the Cripple unlock would crash on hit. Gated here on
     * {@link RankUtils#hasUnlockedSubskill} (rank &ge; 1) instead — both crash-safe and the check the
     * permission node was standing in for (you cannot Cripple without having unlocked Cripple).
     *
     * <p>The player-target branch of {@link #getCrippleTickDuration}/{@link #getCrippleStrength} is
     * dead in singleplayer (the only player is the attacker), so the mob values are used unconditionally
     * — the same honest collapse the rest of the port applies to PvP arms.
     *
     * @param target         the entity that was hit (already confirmed to have survived the swing)
     * @param attackStrength the attacker's captured attack-cooldown charge, scaling the roll
     */
    public void processCripple(@NotNull PlatformLivingEntity target, float attackStrength) {
        // Don't stack Cripple on an already-slowed target (legacy getPotionEffect(SLOWNESS) != null).
        if (target.hasSlowness()) {
            return;
        }
        if (!RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.MACES_CRIPPLE)) {
            return; // see §F #9 above — also the faithful "have you unlocked Cripple?" gate.
        }

        int crippleRank = RankUtils.getRank(getPlayer(), SubSkillType.MACES_CRIPPLE);
        double crippleOdds =
                McMMOMod.getAdvancedConfig().getCrippleChanceToApplyOnHit(crippleRank) * attackStrength;

        if (!ProbabilityUtil.isStaticSkillRNGSuccessful(PrimarySkillType.MACES, mmoPlayer,
                crippleOdds)) {
            return;
        }

        if (mmoPlayer.useChatNotifications()) {
            NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_MESSAGE,
                    "Maces.SubSkill.Cripple.Activated");
        }
        target.applySlowness(getCrippleTickDuration(false), getCrippleStrength(false));
        // PORT: legacy also played ParticleEffectUtils.playCrippleEffect(target) — deferred with the
        // other combat particles until a particle adapter exists.
    }

    public static int getCrippleTickDuration(boolean isPlayerTarget) {
        // TODO: Make configurable
        if (isPlayerTarget) {
            return 20;
        } else {
            return 30;
        }
    }

    public static int getCrippleStrength(boolean isPlayerTarget) {
        // TODO: Make configurable
        return isPlayerTarget ? 1 : 2;
    }
}
