package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.subskills.acrobatics.DodgeResult;
import com.gmail.nossr50.datatypes.skills.subskills.acrobatics.RollResult;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.MetadataStore;
import com.gmail.nossr50.skills.MeleeDamageBonus;
import com.gmail.nossr50.skills.MeleeDamageBonus.MeleeWeapon;
import com.gmail.nossr50.skills.acrobatics.AcrobaticsManager;
import com.gmail.nossr50.util.ItemUtils;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.sounds.SoundManager;
import com.gmail.nossr50.util.sounds.SoundType;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;

/**
 * The K1/K2 damage hook: mcMMO's window into the vanilla damage pipeline. Driven by a mixin on
 * {@link LivingEntity#modifyAppliedDamage(DamageSource, float)} (see
 * {@code fabric.mixin.LivingEntityDamageMixin}) rather than a Fabric event, because mcMMO needs to
 * <em>modify</em> the applied damage (Acrobatics Roll reduces fall damage) and Fabric's
 * {@code ServerLivingEntityEvents.ALLOW_DAMAGE} is a cancel-only veto.
 *
 * <p>Currently wired: <b>K2 — fall damage → Acrobatics Roll</b>, the defender half of <b>K1 — combat
 * damage → Acrobatics Dodge</b> (attacker resolved via {@link DamageSource#getAttacker()}), and the
 * attacker half of <b>K1 — melee weapon on-hit damage bonuses</b> (Swords Stab / Axe Mastery /
 * Unarmed Steel Arm + Berserk, composed MC-free in {@link MeleeDamageBonus}). The effect-only on-hit
 * sub-skills (Counter Attack, Armor Impact, Rupture DoT, Taming damage modifiers, projectile skills,
 * …) attach to this same entry point as their entity/metadata/DoT adapters land.
 */
public final class EntityDamageListener {

    private EntityDamageListener() {
    }

    /**
     * Invoked from the {@code modifyAppliedDamage} mixin for every living-entity hit. Returns the
     * (possibly reduced) damage to apply. Only server players landing fall damage are affected today;
     * everything else passes through untouched.
     *
     * @param entity the entity taking damage
     * @param source the damage source
     * @param amount the vanilla post-armor/enchantment damage that would be applied
     * @return the damage mcMMO wants applied instead (equal to {@code amount} when it does not act)
     */
    public static float onModifyAppliedDamage(LivingEntity entity, DamageSource source, float amount) {
        if (amount <= 0) {
            return amount;
        }

        float result = amount;

        // K1 attacker branch: a player landing a *melee* hit adds their weapon skill's on-hit damage
        // bonus. Runs first so a PvP defender's Dodge (below) reduces the already-boosted damage.
        result = applyAttackerWeaponBonus(entity, source, result);

        // K1 defender / K2 branch: the entity *taking* damage is a player — fall damage feeds
        // Acrobatics Roll, an incoming entity hit feeds Acrobatics Dodge.
        if (entity instanceof ServerPlayerEntity serverPlayer) {
            if (source.isIn(DamageTypeTags.IS_FALL)) {
                result = handleFallDamage(serverPlayer, result);
            } else if (canReduceOwnBlast(serverPlayer, source)) {
                // Blast Mining self-damage. Legacy returns out of its combat handler once
                // Demolitions Expertise has taken the hit, so this must pre-empt Dodge below —
                // a player is not "dodging" their own charge.
                result = handleOwnBlastDamage(serverPlayer, result);
            } else {
                final Entity attacker = source.getAttacker();
                if (attacker != null) {
                    result = handleDodge(serverPlayer, attacker, result);
                }
            }
        }
        return result;
    }

    /**
     * Whether this hit is the player's own Blast Mining charge going off <i>and</i> they have
     * Demolitions Expertise unlocked. Mirrors the gates in legacy
     * {@code BlastMining#processBlastMiningExplosion} that decide whether it handles the hit
     * (returns true) or lets normal combat processing continue (returns false).
     *
     * <p>Legacy's other branch — capping the damage another player's charge deals at 24 — is dropped
     * with the rest of PvP (see {@code BlastMining}'s javadoc): the only player a blast can hit here
     * is the one who set it off.
     */
    private static boolean canReduceOwnBlast(ServerPlayerEntity serverPlayer, DamageSource source) {
        final UUID detonator = BlastMiningListener.detonatorUuid(source.getSource());
        if (detonator == null || !detonator.equals(serverPlayer.getUuid())) {
            return false; // not an mcMMO charge, or not this player's.
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(serverPlayer.getUuid());
        return mmoPlayer != null && mmoPlayer.getMiningManager().canUseDemolitionsExpertise();
    }

    /**
     * Demolitions Expertise: reduce the damage the player's own Blast Mining charge deals to them,
     * by their rank's percentage (legacy {@code MiningManager#processDemolitionsExpertise}).
     */
    private static float handleOwnBlastDamage(ServerPlayerEntity serverPlayer, float amount) {
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(serverPlayer.getUuid());
        if (mmoPlayer == null) {
            return amount;
        }
        // TUNING (CONVERSION_TODO §F): as with the melee bonuses above, modifyAppliedDamage is
        // POST-armor, so the reduction compounds with armor rather than preceding it as in legacy.
        // Legacy additionally cancelled the hit outright when the reduction took it to <= 0; a
        // returned 0 here is equivalent in effect (no health lost).
        return (float) Math.max(mmoPlayer.getMiningManager().processDemolitionsExpertise(amount), 0.0D);
    }

    /**
     * K1 attacker branch: when a player lands a direct melee swing on a living entity, add the on-hit
     * damage bonus for the weapon in their main hand (Swords Stab / Axe Mastery / Unarmed Steel Arm +
     * Berserk). The bonus arithmetic lives MC-free in {@link MeleeDamageBonus}; this method owns the
     * MC-typed gating: attacker identity, the direct-melee check, and held-item classification.
     */
    private static float applyAttackerWeaponBonus(LivingEntity target, DamageSource source,
            float amount) {
        if (!(source.getAttacker() instanceof ServerPlayerEntity attacker)) {
            return amount; // environmental / mob-dealt damage.
        }
        // Only a direct melee swing: the *direct* source of the damage is the player themselves. A
        // ranged hit's direct source is the projectile; reflected Thorns damage is not a weapon swing.
        if (source.getSource() != attacker || source.isOf(DamageTypes.THORNS)) {
            return amount;
        }
        if (target instanceof ArmorStandEntity) {
            return amount; // legacy skips armor stands.
        }

        final McMMOPlayer mmoPlayer = UserManager.getPlayer(attacker.getUuid());
        if (mmoPlayer == null) {
            return amount; // data not loaded (e.g. mid-join).
        }

        final MeleeWeapon weapon = classifyMainHand(attacker.getMainHandStack());
        if (weapon == MeleeWeapon.OTHER) {
            return amount;
        }
        // TUNING (CONVERSION_TODO §F): modifyAppliedDamage is POST-armor, so these bonuses bypass the
        // target's armor mitigation — a discrepancy vs legacy, which boosted the pre-armor damage.
        // Flagged for the tuning pass; the correct seam is a pre-armor hook once one exists.
        return MeleeDamageBonus.applyBonus(mmoPlayer, weapon, amount);
    }

    /** Classify a held main-hand stack into the melee weapon whose bonus applies (legacy order). */
    private static MeleeWeapon classifyMainHand(ItemStack held) {
        if (ItemUtils.isSword(held)) {
            return MeleeWeapon.SWORD;
        }
        if (ItemUtils.isAxe(held)) {
            return MeleeWeapon.AXE;
        }
        if (ItemUtils.isUnarmed(held)) {
            return MeleeWeapon.UNARMED;
        }
        return MeleeWeapon.OTHER;
    }

    private static float handleFallDamage(ServerPlayerEntity serverPlayer, float amount) {
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(serverPlayer.getUuid());
        if (mmoPlayer == null) {
            return amount; // data not loaded (e.g. mid-join).
        }
        final AcrobaticsManager acrobatics = mmoPlayer.getAcrobaticsManager();
        if (acrobatics == null) {
            return amount;
        }

        // The manager awards XP + tracks the landing block internally; it hands back the outcome so we
        // can apply the damage reduction + feedback (the MC-typed half) here.
        final RollResult result = acrobatics.processFallDamage(amount);
        if (result == null || !result.isRollSuccess()) {
            return amount;
        }

        NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_MESSAGE,
                result.isGraceful() ? "Acrobatics.Ability.Proc" : "Acrobatics.Roll.Text");
        SoundManager.sendCategorizedSound(mmoPlayer.getPlayer(), SoundType.ROLL_ACTIVATED,
                SoundCategory.PLAYERS, 0.5F);
        return (float) result.getModifiedDamage();
    }

    /** Transient per-mob counter of how many dodge-XP awards it has handed out (anti-farm cap). */
    private static final String DODGE_TRACKER_KEY = "mcmmo:dodge_tracker";
    /** Legacy cap: a single mob only pays out dodge XP six times (count 0..5 inclusive). */
    private static final int DODGE_XP_MAX_AWARDS = 5;

    /**
     * K1 defender branch: a player taking a hit from an entity may Dodge, reducing the damage and
     * (against an eligible mob) gaining Acrobatics XP. Mirrors legacy
     * {@code CombatUtils.processCombatAttack}'s dodge path. (The attacker-side melee weapon bonuses
     * are handled separately in {@link #applyAttackerWeaponBonus}.)
     */
    private static float handleDodge(ServerPlayerEntity serverPlayer, Entity attacker, float amount) {
        // Lightning dodge can be excluded by config (legacy Acrobatics.dodgeLightningDisabled).
        if (attacker instanceof LightningEntity
                && McMMOMod.getGeneralConfig().getDodgeLightningDisabled()) {
            return amount;
        }

        final McMMOPlayer mmoPlayer = UserManager.getPlayer(serverPlayer.getUuid());
        if (mmoPlayer == null) {
            return amount;
        }
        final AcrobaticsManager acrobatics = mmoPlayer.getAcrobaticsManager();
        if (acrobatics == null) {
            return amount;
        }

        // Only mobs grant dodge XP, and only up to the per-mob cap; the manager still reduces damage
        // when the attacker is XP-ineligible, it just pays nothing.
        final boolean xpEligible = attacker instanceof MobEntity && dodgeXpUncapped((MobEntity) attacker);

        final DodgeResult result = acrobatics.processDodge(amount, xpEligible);
        if (result == null) {
            return amount; // no dodge — leave the hit untouched.
        }

        if (result.getXpGain() > 0) {
            incrementDodgeTracker((MobEntity) attacker);
        }
        if (mmoPlayer.useChatNotifications()) {
            NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_MESSAGE,
                    "Acrobatics.Combat.Proc");
        }
        // PORT: legacy also spawned ParticleEffectUtils.playDodgeEffect + scheduled MobDodgeMetaCleanup
        // to expire the tracker after a minute. Particles need a PlatformPlayer particle adapter; the
        // cleanup task is a refinement (without it the transient tracker just persists for the mob's
        // session lifetime, which is a stricter — still correct — anti-farm cap). Both deferred.
        return (float) result.getModifiedDamage();
    }

    /** Whether {@code mob} has not yet hit the dodge-XP award cap. */
    private static boolean dodgeXpUncapped(MobEntity mob) {
        if (!McMMOMod.getExperienceConfig().isAcrobaticsExploitingPrevented()) {
            return true; // exploit prevention off → uncapped.
        }
        final Integer count = MetadataStore.get(mob, DODGE_TRACKER_KEY, Integer.class);
        return count == null || count <= DODGE_XP_MAX_AWARDS;
    }

    /** Bump the per-mob dodge-XP counter after a successful, XP-paying dodge. */
    private static void incrementDodgeTracker(MobEntity mob) {
        if (!McMMOMod.getExperienceConfig().isAcrobaticsExploitingPrevented()) {
            return;
        }
        final Integer count = MetadataStore.get(mob, DODGE_TRACKER_KEY, Integer.class);
        MetadataStore.set(mob, DODGE_TRACKER_KEY, count == null ? 1 : count + 1);
    }
}
