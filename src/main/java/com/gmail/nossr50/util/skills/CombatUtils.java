package com.gmail.nossr50.util.skills;

import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformLivingEntity;
import com.gmail.nossr50.skills.CombatXp;
import com.gmail.nossr50.util.ItemUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.NotNull;

/**
 * The MC-typed combat helpers, ported from legacy {@code util/skills/CombatUtils}. Only the pieces
 * the ported combat bodies need are here: {@link #safeDealDamage} (mcMMO dealing damage of its own),
 * {@link #applyAbilityAoE} (the Serrated Strikes / Skull Splitter area-of-effect),
 * {@link #canCombatSkillsTrigger} (the operator's PVE/PVP switches) and {@link #processCombatXP}
 * (per-hit combat XP).
 *
 * <p>Legacy's damage-routing half of this class ({@code processCombatAttack} and its
 * {@code processSwordCombat}/{@code processAxeCombat}/… branches) is <em>not</em> ported here: that
 * dispatch is this port's {@code fabric.listeners.EntityDamageListener} sitting on the K1
 * {@code modifyAppliedDamage} mixin seam. This class holds only what those bodies call into.
 */
public final class CombatUtils {

    /**
     * Set for the duration of an mcMMO-dealt {@link #safeDealDamage} call, so the K1 damage hook can
     * tell mcMMO's own damage apart from a player's swing and decline to process it again — see
     * {@link #isProcessingMcMMODamage()}.
     *
     * <p>Legacy needed <em>two</em> mechanisms for this: this same {@code ThreadLocal} (guarding
     * {@code safeDealDamage} against re-entering itself) plus a {@code METADATA_KEY_CUSTOM_DAMAGE}
     * marker on the target, because its damage hook was a Bukkit event handler that could only read
     * state off the entity. Our hook is a direct call made from inside {@code damage()}, on the same
     * thread and within this exact window, so the {@code ThreadLocal} covers both roles and the
     * metadata marker is dropped as redundant.
     */
    private static final ThreadLocal<Boolean> IN_MCMMO_DAMAGE = ThreadLocal.withInitial(() -> false);

    /** Bukkit {@code getNearbyEntities(2.5, 2.5, 2.5)}: the AoE reach, in blocks, on each axis. */
    private static final double ABILITY_AOE_RANGE = 2.5D;

    private CombatUtils() {
    }

    /**
     * Whether the current thread is inside an mcMMO-dealt {@link #safeDealDamage} call. The K1 damage
     * hook checks this first and passes the damage straight through: mcMMO must not re-apply its
     * on-hit bonuses (or, worse, re-trigger the very AoE that is dealing this damage) to damage it
     * dealt itself.
     */
    public static boolean isProcessingMcMMODamage() {
        return IN_MCMMO_DAMAGE.get();
    }

    /**
     * Deal damage to {@code target} on {@code attacker}'s behalf, without mcMMO re-processing it as a
     * player swing. Ports legacy {@code CombatUtils#safeDealDamage}.
     *
     * <p>Legacy's no-attacker overload ({@code safeDealDamage(target, amount)} → Bukkit's generic
     * {@code CUSTOM} damage) is deliberately not ported: every mcMMO body that deals damage —
     * this AoE, and Counter Attack when it lands — attributes it to the player. Porting an
     * unreachable overload is how dead branches get in (CONVERSION_TODO §F).
     *
     * @param target the entity to damage
     * @param amount the damage to deal
     * @param attacker the player the damage is attributed to
     */
    public static void safeDealDamage(@NotNull LivingEntity target, double amount,
            @NotNull ServerPlayerEntity attacker) {
        if (IN_MCMMO_DAMAGE.get() || !target.isAlive()) {
            return;
        }
        if (!(target.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return; // damage is server-side only.
        }

        try {
            IN_MCMMO_DAMAGE.set(true);
            final DamageSource source = serverWorld.getDamageSources().playerAttack(attacker);
            target.damage(serverWorld, source, (float) amount);
        } finally {
            IN_MCMMO_DAMAGE.set(false);
        }
    }

    /**
     * Whether a combat skill is allowed to act on {@code target} at all, per the operator's
     * {@code Enabled_For_PVP} / {@code Enabled_For_PVE} switches. Restores legacy
     * {@code SkillTools#canCombatSkillsTrigger}, which was dropped at Phase 10 for want of an entity
     * adapter (see the breadcrumb in {@link SkillTools}); it lives here rather than back on the
     * MC-free {@code SkillTools} because deciding "player or tamed" needs the MC types.
     *
     * <p>{@code target} is the entity the skill <em>acts upon</em> — the victim of the player's swing
     * on the attacker side, and the counter-attacked assailant on the defender side. Passing the
     * wrong one silently swaps which config switch applies; see the §F note on
     * {@code EntityDamageListener#maybeProcessCounterAttack}.
     *
     * <p>Legacy tests tamed-ness with {@code Tameable#isTamed()}; the modern interface exposes it as a
     * non-null {@code getOwnerReference()}. {@code getOwner()} is deliberately not used here — it
     * resolves the reference and yields null for an owner who is not currently loaded, which would
     * misreport a tamed animal as wild.
     */
    public static boolean canCombatSkillsTrigger(@NotNull PrimarySkillType primarySkillType,
            @NotNull Entity target) {
        final boolean isPlayerOrTamed = target instanceof PlayerEntity
                || (target instanceof Tameable tameable && tameable.getOwnerReference() != null);

        return isPlayerOrTamed
                ? McMMOMod.getSkillTools().getPVPEnabled(primarySkillType)
                : McMMOMod.getSkillTools().getPVEEnabled(primarySkillType);
    }

    /**
     * Apply a super ability's area-of-effect: damage up to {@code tier} nearby entities around the
     * one that was just hit. Ports legacy {@code CombatUtils#applyAbilityAoE}, backing both Swords'
     * Serrated Strikes and Axes' Skull Splitter.
     *
     * <p>The number of extra entities struck is the tier of the weapon in hand (see
     * {@link #weaponTier}), so a netherite sword cleaves five and a wooden one just the single
     * neighbour. Each struck entity takes {@code damage} (floored at 1); for Swords they also get a
     * chance at Rupture, exactly as the primary target does.
     *
     * <p>Dropped from the legacy body, all dead in singleplayer:
     * <ul>
     *   <li>the {@code Swords.Combat.SS.Struck} / {@code Axes.Combat.SS.Struck} notifications — both
     *       only fire {@code if (entity instanceof Player)}, and the only player present is the
     *       attacker, whom {@link #shouldBeAffected} excludes;</li>
     *   <li>the {@code isNPCInteractionPrevented} / {@code Misc.isNPCEntityExcludingVillagers} skip —
     *       NPC support came from third-party plugins (removed in Phase 9); {@code Misc}'s NPC
     *       helpers were not ported.</li>
     * </ul>
     *
     * <p>AoE damage pays no combat XP, matching legacy: it deals its damage through
     * {@link #safeDealDamage}, and the K1 seam passes anything dealt inside that window straight
     * through untouched (see {@link #isProcessingMcMMODamage()}) — which is exactly the role legacy's
     * {@code METADATA_KEY_CUSTOM_DAMAGE} marker played for its own per-hit XP handler.
     *
     * @param attacker the attacking player
     * @param mmoPlayer the attacking player's mcMMO profile
     * @param target the entity that was hit (the AoE spreads from it, and it is never re-struck)
     * @param damage the per-entity AoE damage, already divided by the ability's modifier
     * @param type the skill whose ability is firing
     */
    public static void applyAbilityAoE(@NotNull ServerPlayerEntity attacker,
            @NotNull McMMOPlayer mmoPlayer, @NotNull LivingEntity target, double damage,
            @NotNull PrimarySkillType type) {
        // The higher the weapon tier, the more targets you hit.
        int numberOfTargets = weaponTier(attacker.getMainHandStack());
        final double damageAmount = Math.max(damage, 1);

        // Bukkit's Entity#getNearbyEntities(x, y, z) inflates the entity's own bounding box by that
        // much on each axis and returns everything else inside it — getOtherEntities is the same
        // query, and likewise excludes the entity it is centred on (so the primary target, which the
        // triggering hit already damaged, is never struck twice).
        for (Entity entity : target.getEntityWorld().getOtherEntities(target,
                target.getBoundingBox().expand(ABILITY_AOE_RANGE), e -> true)) {
            if (numberOfTargets <= 0) {
                break;
            }
            if (!(entity instanceof LivingEntity livingEntity)
                    || !shouldBeAffected(attacker, entity)) {
                continue;
            }

            if (type == PrimarySkillType.SWORDS) {
                mmoPlayer.getSwordsManager().processRupture(new PlatformLivingEntity(livingEntity),
                        mmoPlayer.getAttackStrength());
            }

            safeDealDamage(livingEntity, damageAmount, attacker);
            numberOfTargets--;
        }
    }

    /**
     * Whether an entity caught in an AoE should actually be struck. Ports legacy
     * {@code CombatUtils#shouldBeAffected}, which collapses sharply in singleplayer.
     *
     * <p>Legacy's player arm resolved a pile of PvP questions — world PvP flag, god mode, party
     * membership/allies, vanish, spectator — before deciding, but its very first check is
     * {@code defender == player → false}. The attacker is the only player in singleplayer (and
     * {@code getOtherEntities} hands them to us, since the box is centred on the target, not on
     * them), so that check answers the whole arm and the rest is unreachable. Hence: no player is
     * ever affected.
     *
     * <p>Legacy's pet arm ({@code isFriendlyPet} → {@code friendlyFire(player) &&
     * friendlyFire(owner)}) collapses the same way. {@code isFriendlyPet} is true when the pet is
     * tamed and its owner is the attacker <em>or</em> a party member/ally — parties were cut in
     * §1.5, so only "tamed by the attacker" survives. The {@code mcmmo.party.friendlyfire} node then
     * decides, and per the Phase 6 rule opt-in perk nodes are never granted in singleplayer (which
     * also matches legacy's default: friendly fire off). So it resolves to {@code false} and the
     * player's own pets are spared — the correct outcome, and why {@code Permissions.friendlyFire}
     * is not ported rather than being an oversight.
     */
    private static boolean shouldBeAffected(@NotNull ServerPlayerEntity attacker,
            @NotNull Entity entity) {
        if (entity instanceof PlayerEntity) {
            return false;
        }

        // Tameable covers wolves/cats/parrots and the horse family alike, as Bukkit's Tameable did.
        // getOwner() is null unless the animal is tamed, so this is the whole isFriendlyPet check.
        if (entity instanceof Tameable pet && pet.getOwner() == attacker) {
            return false;
        }

        return true;
    }

    /**
     * Award a player the combat XP for a single hit. Ports legacy
     * {@code CombatUtils#processCombatXP} plus the {@code AwardCombatXpTask} it scheduled; the
     * arithmetic lives MC-free in {@link CombatXp}, and this owns the MC-typed reads — the victim's
     * registry id, its category, and the health it has left.
     *
     * <p>Called from each attacker arm of {@code fabric.listeners.EntityDamageListener} exactly where
     * legacy called it: at the end of the corresponding {@code processXCombat}, once the boosted damage
     * is settled, with that boosted damage. This runs inside {@code modifyAppliedDamage}, before
     * vanilla writes the new health, so {@link LivingEntity#getHealth()} is the pre-hit health legacy's
     * task captured at construction — no next-tick health diff needed.
     *
     * <p>Two deliberate deviations, both benign and flagged for the tuning pass (CONVERSION_TODO §F):
     * <ul>
     *   <li><b>Absorption.</b> Vanilla subtracts absorption <em>after</em> this seam returns, so a hit
     *       fully soaked by a mob's absorption hearts pays XP here where legacy's health diff would
     *       have measured nothing. Vanilla mobs do not have absorption.</li>
     *   <li><b>Mob-origin multipliers.</b> Legacy scales the base by where the mob came from (spawner /
     *       nether portal / egg / bred / tamed) and zeroes it for a Call-of-the-Wild summon. Those
     *       ride {@code MobMetaFlagType}, which is unported (the summon path itself is still deferred),
     *       so the multipliers do nothing yet. A pre-existing §B gap this arm inherits, not one it
     *       introduces.</li>
     * </ul>
     *
     * <p>Dropped: legacy's PvP arm (there is no second player to hit) and its
     * {@code runAtEntity}/{@code runAtEntity} scheduler hops, which existed to defer the health diff
     * and to satisfy Folia's region threading.
     *
     * @param mmoPlayer  the attacking player's mcMMO profile
     * @param target     the entity being hit
     * @param skill      the skill the hit trains
     * @param damage     the damage this hit is about to land, after mcMMO's own bonuses
     * @param multiplier the skill's per-hit XP multiplier (legacy's {@code processCombatXP} 4th arg)
     */
    public static void processCombatXP(@NotNull McMMOPlayer mmoPlayer, @NotNull LivingEntity target,
            @NotNull PrimarySkillType skill, double damage, double multiplier) {
        // Legacy's `type == IRON_GOLEM && !ironGolem.isPlayerCreated()` gate: a village-spawned golem
        // pays its configured 2.0 multiplier, a player-built one pays nothing. Without it a golem farm
        // is an XP exploit — which is the whole reason upstream singles the check out.
        if (target instanceof IronGolemEntity golem && golem.isPlayerCreated()) {
            return;
        }

        final int xp = CombatXp.xpForHit(
                Registries.ENTITY_TYPE.getId(target.getType()).toString(), categoryOf(target),
                damage, target.getHealth(), multiplier);
        if (xp <= 0) {
            return;
        }
        mmoPlayer.beginXpGain(skill, xp, XPGainReason.PVE, XPGainSource.SELF);
    }

    /** {@link #processCombatXP} with legacy's default multiplier of {@code 1.0}. */
    public static void processCombatXP(@NotNull McMMOPlayer mmoPlayer, @NotNull LivingEntity target,
            @NotNull PrimarySkillType skill, double damage) {
        processCombatXP(mmoPlayer, target, skill, damage, 1.0);
    }

    /**
     * The victim's coarse category, mirroring legacy's {@code instanceof Animals} /
     * {@code instanceof Monster} / else branch in {@code processCombatXP}.
     */
    private static CombatXp.MobCategory categoryOf(@NotNull LivingEntity entity) {
        if (entity instanceof HostileEntity) {
            return CombatXp.MobCategory.MONSTER;
        }
        if (entity instanceof AnimalEntity) {
            return CombatXp.MobCategory.ANIMAL;
        }
        return CombatXp.MobCategory.OTHER;
    }

    /**
     * The upgrade tier of the item in hand, which is how many entities an ability AoE may strike.
     * Ports legacy {@code CombatUtils#getTier}.
     *
     * <p>Gold ranking alongside wood at 1 (below stone) is upstream's, and intentional — gold tools
     * are fast and cheap but weak — not a transcription slip. Anything unrecognised (including a
     * bare fist) is tier 0, i.e. no AoE at all.
     */
    private static int weaponTier(@NotNull ItemStack inHand) {
        if (ItemUtils.isWoodTool(inHand)) {
            return 1;
        } else if (ItemUtils.isStoneTool(inHand)) {
            return 2;
        } else if (ItemUtils.isIronTool(inHand)) {
            return 3;
        } else if (ItemUtils.isGoldTool(inHand)) {
            return 1;
        } else if (ItemUtils.isDiamondTool(inHand)) {
            return 4;
        } else if (ItemUtils.isNetheriteTool(inHand)) {
            return 5;
        }

        return 0;
    }
}
