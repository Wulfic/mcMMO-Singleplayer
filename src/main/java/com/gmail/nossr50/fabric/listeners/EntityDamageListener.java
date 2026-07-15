package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.subskills.acrobatics.DodgeResult;
import com.gmail.nossr50.datatypes.skills.subskills.acrobatics.RollResult;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.MetadataStore;
import com.gmail.nossr50.platform.PlatformLivingEntity;
import com.gmail.nossr50.skills.MeleeDamageBonus;
import com.gmail.nossr50.skills.MeleeDamageBonus.MeleeWeapon;
import com.gmail.nossr50.skills.acrobatics.AcrobaticsManager;
import com.gmail.nossr50.skills.axes.AxesManager;
import com.gmail.nossr50.skills.swords.SwordsManager;
import com.gmail.nossr50.util.ItemUtils;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.skills.CombatUtils;
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
 * Unarmed Steel Arm + Berserk, composed MC-free in {@link MeleeDamageBonus}), and the on-hit
 * <em>effect</em> sub-skills: <b>Swords Rupture</b> (bleed DoT — see {@link #maybeProcessRupture})
 * and the two combat super abilities, <b>Serrated Strikes</b> and <b>Skull Splitter</b> (AoE — see
 * {@link #maybeProcessSerratedStrikes} / {@link #maybeProcessSkullSplitter}), and — on the defender
 * side again — <b>Swords Counter Attack</b> (see {@link #maybeProcessCounterAttack}). The Axes
 * target-inspecting sub-skills (<b>Armor Impact</b> / <b>Greater Impact</b> / <b>Critical
 * Strikes</b>) ride the attacker branch inside {@link MeleeDamageBonus}, since they feed the same
 * damage total. The remaining effect-only sub-skills (Disarm, Taming damage modifiers, projectile
 * skills, …) attach to this same entry point as their entity/metadata adapters land.
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
        // Damage mcMMO is dealing itself (a Serrated Strikes / Skull Splitter AoE) must not be fed
        // back through mcMMO's own on-hit processing: the AoE attributes its damage to the player,
        // so without this it would read as a fresh swing and re-fire the very ability that is
        // dealing it. Legacy guards its damage handlers the same way, via a custom-damage marker on
        // the target (see CombatUtils#isProcessingMcMMODamage for why a ThreadLocal replaces it).
        if (CombatUtils.isProcessingMcMMODamage()) {
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
                // Counter Attack reflects damage but does not change what the player takes, so it
                // runs last and returns nothing. Legacy's ordering, preserved: it reads the damage
                // back *after* Dodge has written to it, so a dodged hit counters for less.
                maybeProcessCounterAttack(serverPlayer, source, result);
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
        // Legacy gates each weapon's branch on the skill's Enabled_For_PVE/PVP switch before doing
        // anything. That gate was dropped when SkillTools was ported without an entity adapter, so
        // until now these switches did nothing on the attacker side; restored with the adapter.
        if (!CombatUtils.canCombatSkillsTrigger(skillOf(weapon), target)) {
            return amount;
        }
        // TUNING (CONVERSION_TODO §F): modifyAppliedDamage is POST-armor, so these bonuses bypass the
        // target's armor mitigation — a discrepancy vs legacy, which boosted the pre-armor damage.
        // Flagged for the tuning pass; the correct seam is a pre-armor hook once one exists.
        final PlatformLivingEntity platformTarget = new PlatformLivingEntity(target);
        final float boostedDamage = MeleeDamageBonus.applyBonus(mmoPlayer, weapon, amount,
                platformTarget);

        // Legacy's per-weapon ordering, preserved: the super-ability AoE fires after the damage
        // bonus is computed but before it is committed, and is passed the *unboosted* damage
        // (legacy hands it `event.getDamage()`, which it only overwrites via setDamage afterwards).
        //
        // PORT: legacy sequences the Axes AoE *between* Greater Impact and Critical Strikes rather
        // than after the whole chain as here. Equivalent: the AoE neither reads nor writes the
        // damage total (it is handed the unboosted amount either way) and it never touches the
        // primary target, so only the order of the player's own chat notifications differs.
        if (weapon == MeleeWeapon.SWORD) {
            maybeProcessSerratedStrikes(mmoPlayer, attacker, target, amount);
            maybeProcessRupture(mmoPlayer, target, boostedDamage);
        } else if (weapon == MeleeWeapon.AXE) {
            maybeProcessSkullSplitter(mmoPlayer, attacker, platformTarget, target, amount);
        }
        return boostedDamage;
    }

    /**
     * Swords Serrated Strikes: while the super ability is active, a sword hit also strikes nearby
     * entities for a fraction of the damage. Mirrors legacy {@code CombatUtils#processSwordCombat}'s
     * {@code canUseSerratedStrike} arm.
     */
    private static void maybeProcessSerratedStrikes(McMMOPlayer mmoPlayer,
            ServerPlayerEntity attacker, LivingEntity target, float damage) {
        final SwordsManager swords = mmoPlayer.getSwordsManager();
        if (swords == null || !swords.canUseSerratedStrike()) {
            return;
        }
        CombatUtils.applyAbilityAoE(attacker, mmoPlayer, target,
                swords.serratedStrikesDamage(damage), PrimarySkillType.SWORDS);
    }

    /**
     * Axes Skull Splitter: while the super ability is active, an axe hit also strikes nearby entities
     * for a fraction of the damage. Mirrors legacy {@code CombatUtils#processAxeCombat}'s
     * {@code canUseSkullSplitter} arm.
     */
    private static void maybeProcessSkullSplitter(McMMOPlayer mmoPlayer, ServerPlayerEntity attacker,
            PlatformLivingEntity platformTarget, LivingEntity target, float damage) {
        final AxesManager axes = mmoPlayer.getAxesManager();
        if (axes == null || !axes.canUseSkullSplitter(platformTarget)) {
            return;
        }
        CombatUtils.applyAbilityAoE(attacker, mmoPlayer, target, axes.skullSplitterDamage(damage),
                PrimarySkillType.AXES);
    }

    /**
     * Swords Rupture: a sword hit that leaves the target alive may start a bleed. Mirrors legacy
     * {@code CombatUtils#processSwordCombat}, which calls {@code processRupture} only once the
     * boosted damage is settled and only when the target survives the hit — there is no point
     * bleeding something this swing already kills, and legacy's Rupture can never land a killing
     * blow anyway.
     *
     * <p>{@code modifyAppliedDamage} runs before vanilla writes the new health, so reading
     * {@link LivingEntity#getHealth()} here gives the pre-hit health — the same value legacy's
     * {@code target.getHealth() - event.getFinalDamage()} check saw.
     */
    private static void maybeProcessRupture(McMMOPlayer mmoPlayer, LivingEntity target,
            float boostedDamage) {
        if (target.getHealth() - boostedDamage <= 0) {
            return; // the swing itself is lethal.
        }
        mmoPlayer.getSwordsManager().processRupture(new PlatformLivingEntity(target),
                mmoPlayer.getAttackStrength());
    }

    /**
     * Swords Counter Attack: a player hit while holding a sword may reflect a fraction of the damage
     * back at their assailant. Ports legacy {@code CombatUtils#processCombatAttack}'s defender arm
     * plus {@code SwordsManager#counterAttackChecks}.
     *
     * <p>Only a <em>living, direct</em> damager can be countered — legacy passes {@code painSource}
     * (the damager itself, not the projectile's shooter) and its {@code canUseCounterAttack} requires
     * {@code instanceof LivingEntity}, so an arrow or a Blast Mining charge counters nothing.
     *
     * <p>⚠️ FIXED UPSTREAM BUG (CONVERSION_TODO §F #5, a new shape — <b>role inversion</b>): legacy
     * gates this on {@code canCombatSkillsTrigger(SWORDS, target)}, but in the defender arm
     * {@code target} is the <em>player</em>, not the entity being acted upon. That makes
     * {@code isPlayerOrTamed} unconditionally true, so a PvE counter against a mob is decided by
     * {@code Enabled_For_PVP} — an operator disabling Swords for PvP silently kills counter-attacks
     * against mobs, and one disabling it for PvE does not. Every other call site passes the entity the
     * skill acts upon; this one is ported to that intent ({@code assailant}). Both switches default to
     * {@code true}, so the shipped config behaves identically either way.
     *
     * @param damage the damage the player is taking, after any Dodge reduction
     */
    private static void maybeProcessCounterAttack(ServerPlayerEntity serverPlayer,
            DamageSource source, float damage) {
        // The *direct* damager, matching legacy's painSource (not painSourceRoot).
        if (!(source.getSource() instanceof LivingEntity assailant)) {
            return;
        }
        if (!ItemUtils.isSword(serverPlayer.getMainHandStack())) {
            return;
        }
        if (!CombatUtils.canCombatSkillsTrigger(PrimarySkillType.SWORDS, assailant)) {
            return;
        }

        final McMMOPlayer mmoPlayer = UserManager.getPlayer(serverPlayer.getUuid());
        if (mmoPlayer == null) {
            return;
        }
        final SwordsManager swords = mmoPlayer.getSwordsManager();
        if (swords == null || !swords.canUseCounterAttack() || !swords.rollCounterAttack()) {
            return;
        }

        CombatUtils.safeDealDamage(assailant, swords.counterAttackDamage(damage), serverPlayer);
        NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_MESSAGE,
                "Swords.Combat.Countered");
        // PORT: legacy also notified the countered attacker ("Swords.Combat.Counter.Hit"), which only
        // fires `if (attacker instanceof Player)` — dead in singleplayer, where the only player is the
        // one countering. Dropped with the rest of PvP.
    }

    /** The primary skill a melee weapon's on-hit bonuses belong to (legacy's per-weapon dispatch). */
    private static PrimarySkillType skillOf(MeleeWeapon weapon) {
        return switch (weapon) {
            case SWORD -> PrimarySkillType.SWORDS;
            case AXE -> PrimarySkillType.AXES;
            case UNARMED -> PrimarySkillType.UNARMED;
            case OTHER -> throw new IllegalArgumentException("OTHER has no skill; gate it first");
        };
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
