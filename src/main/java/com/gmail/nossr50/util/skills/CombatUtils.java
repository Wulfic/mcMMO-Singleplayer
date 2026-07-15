package com.gmail.nossr50.util.skills;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.platform.PlatformLivingEntity;
import com.gmail.nossr50.util.ItemUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.NotNull;

/**
 * The MC-typed combat helpers, ported from legacy {@code util/skills/CombatUtils}. Only the two
 * pieces the ported combat bodies need are here: {@link #safeDealDamage} (mcMMO dealing damage of
 * its own) and {@link #applyAbilityAoE} (the Serrated Strikes / Skull Splitter area-of-effect).
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
     * <p>DEVIATION (CONVERSION_TODO §F): legacy awards combat XP <em>per hit</em> and its
     * custom-damage marker kept AoE-dealt damage from paying any, whereas this port awards combat XP
     * <em>per kill</em> ({@code fabric.listeners.CombatListener} on {@code AFTER_DEATH}). The AoE
     * attributes its damage to the player, so an entity finished off by the AoE pays kill XP here
     * where legacy paid none. Flagged for the tuning pass rather than patched: the per-kill XP model
     * is a deliberate, pre-existing port decision (Phase 3) and paying for an AoE kill is consistent
     * with it.
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
