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
import com.gmail.nossr50.skills.taming.TamingManager;
import com.gmail.nossr50.skills.unarmed.UnarmedManager;
import com.gmail.nossr50.util.ItemUtils;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.skills.CombatUtils;
import com.gmail.nossr50.util.sounds.SoundManager;
import com.gmail.nossr50.util.sounds.SoundType;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.projectile.ArrowEntity;
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
 * damage total. <b>Taming</b>'s damage modifiers ride both branches: a tamed wolf's bite carries its
 * owner's Gore / Sharpened Claws / Fast Food Service (see {@link #applyWolfAttackBonus}), and a hit
 * on that wolf is softened by Thick Fur / Shock Proof / Holy Hound (see {@link #handleWolfDamage}).
 * The remaining effect-only sub-skills (projectile skills, …) attach to this same entry point as
 * their entity/metadata adapters land.
 *
 * <p>One branch does <em>not</em> ride the mixin: Unarmed's <b>Arrow Deflect</b> ({@link
 * #onAllowDamage}) has to cancel the hit outright, so it rides Fabric's cancel-only
 * {@code ServerLivingEntityEvents.ALLOW_DAMAGE} veto — hence this class has a {@link #register()}
 * as well as a mixin entry point.
 */
public final class EntityDamageListener {

    private EntityDamageListener() {
    }

    /**
     * Subscribe the one branch of this listener that needs to <em>veto</em> a hit outright rather
     * than reduce it: Unarmed's Arrow Deflect (see {@link #onAllowDamage}). Everything else here is
     * driven by the {@code modifyAppliedDamage} mixin, which cannot cancel.
     */
    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(EntityDamageListener::onAllowDamage);
    }

    /**
     * Unarmed Arrow Deflect: a bare-handed player may swat an incoming arrow out of the air. Ports
     * legacy {@code EntityListener#onEntityDamageByEntity}'s deflect arm plus
     * {@code UnarmedManager#deflectCheck}.
     *
     * <p>This is the one mcMMO damage branch that <b>cancels</b> instead of reducing, so it rides
     * Fabric's cancel-only {@code ALLOW_DAMAGE} veto rather than the {@code modifyAppliedDamage}
     * seam the rest of this class uses. That is not a workaround but the faithful seam: legacy
     * called {@code event.setCancelled(true)}, and like Bukkit's cancel this fires before knockback,
     * i-frames and the hurt sound. Returning {@code 0} from {@code modifyAppliedDamage} would zero
     * the damage but still knock the player back, burn their invulnerability window and consume the
     * arrow — a deflected arrow instead bounces off, which is vanilla's own behaviour when
     * {@code damage()} returns false.
     *
     * <p>It also lands earlier than Dodge, matching legacy: the deflect arm sits in the damage
     * handler ahead of {@code processCombatAttack}, so a deflected arrow is never also dodged.
     *
     * @return {@code false} to cancel the hit (the arrow was deflected), {@code true} to allow it
     */
    private static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayerEntity serverPlayer)) {
            return true; // legacy's `defender instanceof Player` — deflect is a player-only defence.
        }
        // Legacy checks the *direct* damager (`event.getDamager()`) for `instanceof Arrow`, which in
        // Bukkit is specifically a regular/tipped arrow — its sibling types (SpectralArrow, Trident)
        // implement AbstractArrow, not Arrow, so they were never deflectable. ArrowEntity draws that
        // same line here: its siblings extend PersistentProjectileEntity alongside it, not from it.
        if (!(source.getSource() instanceof ArrowEntity)) {
            return true;
        }

        final McMMOPlayer mmoPlayer = UserManager.getPlayer(serverPlayer.getUuid());
        if (mmoPlayer == null) {
            return true; // data not loaded (e.g. mid-join).
        }
        final UnarmedManager unarmed = mmoPlayer.getUnarmedManager();
        if (unarmed == null || !unarmed.canDeflect() || !unarmed.rollArrowDeflect()) {
            return true;
        }

        NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_MESSAGE,
                "Combat.ArrowDeflect");
        return false;
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
        // ...and the other half of legacy's attacker dispatch: the damager is the player's *wolf*,
        // which adds the owner's Taming bonuses. Legacy branches on the damager's type in one
        // if/else-if chain, so at most one of these two can ever fire.
        result = applyWolfAttackBonus(entity, source, result);

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
        } else if (entity instanceof WolfEntity wolf) {
            // Legacy's sibling `else if (livingEntity instanceof Tameable pet)` arm: the player's own
            // wolf is taking damage, and Taming may soften or undo it.
            result = handleWolfDamage(wolf, source, result);
        }
        return result;
    }

    /**
     * K1 attacker branch, Taming half: a tamed wolf's bite carries its owner's Taming bonuses. Ports
     * legacy {@code CombatUtils#processTamingCombat} (reached from the {@code entityType ==
     * EntityType.WOLF} arm of {@code processCombatAttack}).
     *
     * <p>Order is legacy's: Fast Food Service heals the wolf for the <em>unboosted</em> damage it just
     * dealt, then Sharpened Claws adds its flat bonus, then Gore multiplies the <em>initial</em>
     * damage and contributes only the difference. Gore reading {@code amount} rather than the running
     * total is why the two are additive rather than compounding.
     *
     * <p>{@code getOwner()} is used deliberately here, unlike in {@link
     * CombatUtils#canCombatSkillsTrigger} where it is avoided: that method only needs to know
     * <em>whether</em> an animal is tamed (so an unloaded owner must not read as "wild"), whereas this
     * one needs the owner themselves and has nothing to do if they are not present — exactly what
     * legacy's {@code wolf.getOwner() instanceof Player} did.
     *
     * <p>Dropped from the legacy body:
     * <ul>
     *   <li>{@code master.isOnline() && master.isValid()} — the {@link UserManager} lookup below is
     *       the singleplayer equivalent (no profile loaded, nothing to do);</li>
     *   <li>{@code Misc.isNPCEntityExcludingVillagers(master)} and
     *       {@code doesPlayerHaveSkillPermission} — the NPC helpers were not ported (Phase 9) and the
     *       skill-permission check was dropped at Phase 6/10 (see the breadcrumb in {@link
     *       com.gmail.nossr50.util.skills.SkillTools}), as on every other attacker arm here.</li>
     * </ul>
     *
     * <p>Deferred (breadcrumbs, CONVERSION_TODO §C): {@code pummel} (needs a
     * velocity-along-a-non-player's-look-direction adapter plus particles) and legacy's
     * {@code processCombatXP(mmoPlayer, target, TAMING, 3)} — this port awards combat XP per *kill*
     * ({@code CombatListener}), not per hit, and that listener only pays out when the killer is a
     * player, so wolf-assisted Taming XP is a pre-existing §B gap rather than something this arm can
     * drop in without picking an XP model for it.
     */
    private static float applyWolfAttackBonus(LivingEntity target, DamageSource source,
            float amount) {
        // Legacy keys off painSource (the *direct* damager), so a wolf's own bite — not, say, an
        // arrow that happens to have a wolf as its owner — is what counts.
        if (!(source.getSource() instanceof WolfEntity wolf)) {
            return amount;
        }
        if (!(wolf.getOwner() instanceof ServerPlayerEntity master)) {
            return amount; // wild wolf, or one whose owner is not this player.
        }
        if (!CombatUtils.canCombatSkillsTrigger(PrimarySkillType.TAMING, target)) {
            return amount;
        }

        final McMMOPlayer mmoPlayer = UserManager.getPlayer(master.getUuid());
        if (mmoPlayer == null) {
            return amount; // data not loaded (e.g. mid-join).
        }
        final TamingManager taming = mmoPlayer.getTamingManager();
        if (taming == null) {
            return amount;
        }

        if (taming.canUseFastFoodService()) {
            taming.fastFoodService(new PlatformLivingEntity(wolf), amount);
        }

        double boostedDamage = amount;
        if (taming.canUseSharpenedClaws()) {
            boostedDamage += taming.sharpenedClaws();
        }
        if (taming.canUseGore()) {
            boostedDamage += taming.gore(amount);
        }
        return (float) boostedDamage;
    }

    /**
     * K1 defender branch, Taming half: the player's wolf is taking damage, and Taming may soften,
     * heal back or shrug it off depending on what hurt it. Ports the {@code Tameable} arm of legacy
     * {@code EntityListener#onEntityDamage}, including {@code Taming.canPreventDamage}'s
     * {@code isTamed() && owner instanceof Player && pet instanceof Wolf} gate — {@code getOwner()}
     * is null unless tamed, so matching {@link WolfEntity} and a {@link ServerPlayerEntity} owner is
     * that whole check.
     *
     * <p>Legacy switches on Bukkit's {@code DamageCause}, which has no modern counterpart; each arm is
     * mapped to the vanilla damage types Bukkit derived that cause from (see the helpers below). The
     * arms are mutually exclusive and every one of them {@code return}s, exactly as legacy's
     * {@code switch} did.
     *
     * <p>Deferred (breadcrumb, CONVERSION_TODO §C): Environmentally Aware, whose two arms
     * ({@code CONTACT}/{@code FIRE}/{@code HOT_FLOOR}/{@code LAVA} → teleport the wolf to its owner,
     * and {@code FALL} → cancel outright) need an entity-teleport adapter and a cancel-shaped seam
     * ({@code ALLOW_DAMAGE}, as Arrow Deflect uses) respectively. Those causes currently fall through
     * untouched.
     */
    private static float handleWolfDamage(WolfEntity wolf, DamageSource source, float amount) {
        if (!(wolf.getOwner() instanceof ServerPlayerEntity owner)) {
            return amount; // wild wolf (getOwner() is null unless tamed).
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(owner.getUuid());
        if (mmoPlayer == null) {
            return amount;
        }
        final TamingManager taming = mmoPlayer.getTamingManager();
        if (taming == null) {
            return amount;
        }

        // ENTITY_ATTACK / PROJECTILE -> Thick Fur halves the hit.
        if (isEntityAttack(source) || source.isIn(DamageTypeTags.IS_PROJECTILE)) {
            if (taming.canUseThickFur()) {
                // Legacy additionally cancelled the event when the reduction bottomed out at 0; a
                // returned 0 is equivalent in effect (no health lost), as with Demolitions Expertise.
                return (float) Math.max(taming.processThickFur(amount), 0.0D);
            }
            return amount;
        }

        // FIRE_TICK -> Thick Fur snuffs the flames. Note this is vanilla ON_FIRE (*burning*), not the
        // IS_FIRE tag: that tag also covers IN_FIRE/CAMPFIRE, which are Bukkit's FIRE cause and
        // belong to the deferred Environmentally Aware arm, not to this one.
        if (source.isOf(DamageTypes.ON_FIRE)) {
            if (taming.canUseThickFur()) {
                new PlatformLivingEntity(wolf).extinguish();
            }
            return amount;
        }

        // MAGIC / POISON / WITHER -> Holy Hound heals the wolf for what it took.
        if (isHolyHoundCause(source)) {
            if (taming.canUseHolyHound()) {
                taming.processHolyHound(new PlatformLivingEntity(wolf), amount);
            }
            return amount;
        }

        // BLOCK_EXPLOSION / ENTITY_EXPLOSION / LIGHTNING -> Shock Proof divides the hit down.
        if (source.isIn(DamageTypeTags.IS_EXPLOSION) || source.isIn(DamageTypeTags.IS_LIGHTNING)) {
            if (taming.canUseShockProof()) {
                return (float) Math.max(taming.processShockProof(amount), 0.0D);
            }
            return amount;
        }

        return amount;
    }

    /**
     * Bukkit's {@code ENTITY_ATTACK}: a melee blow from a mob or a player. Bukkit derived that cause
     * from these damage types; the projectile ones it mapped to {@code PROJECTILE} instead, which the
     * caller tests separately via {@code IS_PROJECTILE}.
     */
    private static boolean isEntityAttack(DamageSource source) {
        return source.isIn(DamageTypeTags.IS_PLAYER_ATTACK)
                || source.isOf(DamageTypes.MOB_ATTACK)
                || source.isOf(DamageTypes.MOB_ATTACK_NO_AGGRO);
    }

    /**
     * Bukkit's {@code MAGIC}, {@code POISON} and {@code WITHER} causes, which Holy Hound treats
     * alike. Note the three collapse to two tests here: vanilla deals Poison's damage as
     * {@link DamageTypes#MAGIC}, so Bukkit's separate {@code POISON} cause has no distinct damage
     * type to match on and is already covered.
     */
    private static boolean isHolyHoundCause(DamageSource source) {
        return source.isOf(DamageTypes.MAGIC)
                || source.isOf(DamageTypes.INDIRECT_MAGIC)
                || source.isOf(DamageTypes.WITHER);
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
