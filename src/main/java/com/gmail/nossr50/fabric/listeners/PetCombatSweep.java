package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.subskills.taming.PetCombatMode;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.SkillAttributeService;
import com.gmail.nossr50.skills.taming.PetTargeting;
import com.gmail.nossr50.skills.taming.TamingManager;
import com.gmail.nossr50.util.player.UserManager;
import java.util.List;
import java.util.Optional;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The per-player pet sweep: what an aggressive pet picks a fight with, and how far <em>any</em> pet
 * will chase what it is already fighting.
 *
 * <h2>Two jobs, and only one of them is about the stance</h2>
 * <ul>
 *   <li><b>The reach fix applies in both stances, and needs no profile.</b> "My pets ignore what I
 *       shoot" was never a mode question and was never about the weapon — see
 *       {@link SkillAttributeService.Managed#TAMING_PET_ENGAGE_RANGE} for the bytecode. A pet you
 *       sicced by shooting something is the reported bug, and that happens in {@code PASSIVE}. It
 *       therefore sits <em>above</em> the profile lookup, the same way Taming's pet-follow does in
 *       {@link PlayerMovementTracker}: making a vanilla-pathing override wait on a loaded profile
 *       means it silently stops working during exactly the window (a fresh join, a failed load)
 *       where it is least recoverable.</li>
 *   <li><b>Acquiring a new target is the only part the stance gates</b>, and it needs the profile.</li>
 * </ul>
 *
 * <h2>🔑 Candidates are {@code Monster}, NOT {@code HostileEntity}</h2>
 * {@code HostileEntity} is what {@code CombatUtils.categoryOf} uses and it <b>silently omits slime,
 * magma cube, ghast, phantom and hoglin</b> — every one of which implements the {@link Monster}
 * interface without extending {@code HostileEntity}. That omission produces no error anywhere; the
 * pack would simply never react to a slime, and nothing would say why. {@code Monster} is a bare
 * interface rather than an {@code Entity} subtype, which is why the query goes through
 * {@link MobEntity} and filters — {@code getEntitiesByClass} needs a {@code Class<T extends Entity>}.
 *
 * <h2>Exclusions are delegated, not re-listed</h2>
 * {@code WolfEntity#canAttackWithOwner(target, owner)} already refuses {@code CreeperEntity},
 * {@code GhastEntity}, {@code ArmorStandEntity} and the owner's own tamed wolves
 * (bytecode-verified). Re-implementing that list here would mean maintaining a copy that goes stale
 * the first time Mojang edits the rules, silently and in the direction of "your pets now suicide
 * into creepers". The warden is the one addition, and it is explicit below.
 *
 * <p>⚠️ <b>Cost.</b> Two box queries per player per sweep, at the configured interval (1 s by
 * default) rather than per tick. Both are bounded by the config, and the engage range is
 * hard-capped, because path-search cost grows with the cube of that number.
 */
public final class PetCombatSweep {

    /**
     * A wolf's natural {@code FOLLOW_RANGE}, used only as the fallback when an entity somehow has no
     * such attribute instance.
     *
     * <p>⚠️ The live value is read from the entity every sweep and this constant is <em>not</em> the
     * number the boost is computed against in the normal path. That is deliberate: hard-coding 16
     * would silently stop being right the moment anything else — a mod, a datapack, a future vanilla
     * retune — touches the attribute, and the failure would be a wrong boost with nothing to notice
     * it. This is a fallback for an impossible state, not a shortcut.
     */
    private static final double ASSUMED_BASE_FOLLOW_RANGE = 16.0D;

    private PetCombatSweep() {
    }

    /**
     * One player's pet sweep. Called from {@link PlayerMovementTracker#tickPlayer}, which is the
     * mod's only per-tick per-player pass.
     *
     * <p>Package-private so the test can drive the whole body rather than the predicates alone.
     */
    static void tick(@NotNull ServerPlayerEntity player) {
        final GeneralConfig config = McMMOMod.getGeneralConfig();
        if (config == null || !config.isPetCombatModeEnabled()) {
            // ⚠️ Returns without teardown, and that is safe rather than sloppy: every modifier this
            // class applies is TEMPORARY, so it dies with the entity and can never reach the save
            // file. The only way to observe a stranded boost is to edit config.yml mid-session and
            // then keep playing without a restart, which the config layer does not support anyway.
            return;
        }
        if (player.age % config.getPetSweepIntervalTicks() != 0) {
            return;
        }

        final World world = player.getEntityWorld();
        if (world == null) {
            return;
        }

        final double engageRange = config.getPetEngageRange();
        final double aggressiveRadius = config.getPetAggressiveRadius();

        // One query for the pack. Sized by the LARGER of the two ranges: a pet that is off chasing
        // something at the engage range still has to be found, or its boost would never be taken
        // back off.
        final double petSearch = Math.max(engageRange, aggressiveRadius);
        final List<WolfEntity> pets = world.getEntitiesByClass(WolfEntity.class,
                player.getBoundingBox().expand(petSearch),
                wolf -> wolf.isTamed() && wolf.isOwner(player));
        if (pets.isEmpty()) {
            return; // Nothing to do, and no reason to pay for the candidate query.
        }

        final PetCombatMode mode = resolveMode(player);

        // The candidate query is paid for once for the whole pack, and only when it can be used:
        // in PASSIVE nothing acquires, and a pack that is already fully engaged acquires nothing.
        List<MobEntity> candidates = null;

        for (WolfEntity pet : pets) {
            if (pet.isSitting()) {
                // "Sit" is an explicit order to stay. A sitting pet fights nothing and chases
                // nothing, so it gets no boost either.
                SkillAttributeService.set(pet, SkillAttributeService.Managed.TAMING_PET_ENGAGE_RANGE,
                        0.0D);
                continue;
            }

            final LivingEntity target = pet.getTarget();
            if (target != null && target.isAlive()) {
                applyEngageBoost(pet, engageRange);
                continue;
            }

            // No live target: take the boost back off. Re-derived every sweep rather than tracked,
            // which is what makes a missed removal self-heal on the next pass instead of persisting.
            SkillAttributeService.set(pet, SkillAttributeService.Managed.TAMING_PET_ENGAGE_RANGE,
                    0.0D);

            if (!mode.acquiresOwnTargets()) {
                continue; // PASSIVE: a pet fights only what its owner fights.
            }
            if (candidates == null) {
                candidates = findCandidates(world, player, aggressiveRadius);
            }
            acquire(pet, player, candidates);
        }
    }

    /**
     * Raises this pet's follow range to {@code engageRange} for as long as it has a target.
     *
     * <p>The amount is derived from the entity's own <em>base</em> value every sweep, never cached
     * and never assumed: {@code getBaseValue()} excludes modifiers, so re-deriving is idempotent and
     * a pet whose base range differs (a mod, a datapack) still lands on the configured range rather
     * than on the configured range plus its own head start.
     *
     * <p>An engage range at or below the pet's natural one removes the modifier instead of applying
     * a zero or negative one — a "boost" that de-buffs would be a config edit quietly making pets
     * worse than vanilla.
     */
    private static void applyEngageBoost(@NotNull WolfEntity pet, double engageRange) {
        final EntityAttributeInstance instance =
                pet.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);
        final double base = instance == null ? ASSUMED_BASE_FOLLOW_RANGE : instance.getBaseValue();
        SkillAttributeService.set(pet, SkillAttributeService.Managed.TAMING_PET_ENGAGE_RANGE,
                Math.max(0.0D, engageRange - base));
    }

    /**
     * The hostiles near the <em>player</em> that this player's pets may attack (ruling R-5).
     *
     * <p>Measured from the player, so one query serves the whole pack and a pet that lagged behind
     * cannot drag something home from where it happens to be standing.
     */
    private static @NotNull List<MobEntity> findCandidates(@NotNull World world,
            @NotNull ServerPlayerEntity player, double radius) {
        return world.getEntitiesByClass(MobEntity.class,
                player.getBoundingBox().expand(radius),
                mob -> mob instanceof Monster && mob.isAlive() && !isWarden(mob));
    }

    /**
     * The warden, excluded explicitly and on purpose.
     *
     * <p>It is a {@link Monster}, so nothing above filters it out. It is also unkillable by a wolf
     * pack, and the noise of them trying summons it onto the player who never chose the fight. This
     * is the one exclusion {@code canAttackWithOwner} does not make for us.
     */
    private static boolean isWarden(@NotNull MobEntity mob) {
        return mob instanceof WardenEntity;
    }

    /**
     * Points one pet at the eligible candidate nearest its owner.
     *
     * <p>⚠️ Eligibility is asked <b>per pet</b> even though the candidate list is shared, because
     * {@code canAttackWithOwner} is a question about this wolf and this owner, not about the mob
     * alone.
     *
     * <p>⚠️ A candidate that is <em>already</em> attacking something is deliberately NOT excluded.
     * The draft plan called for that, and it is backwards: the single most important candidate is
     * the zombie currently hitting the player, and that zombie has a target by definition. Excluding
     * it would make aggressive mode ignore precisely the threat the player most wants handled.
     */
    private static void acquire(@NotNull WolfEntity pet, @NotNull ServerPlayerEntity player,
            @NotNull List<MobEntity> candidates) {
        final List<MobEntity> eligible = candidates.stream()
                .filter(candidate -> pet.canAttackWithOwner(candidate, player))
                .toList();

        final Optional<MobEntity> chosen =
                PetTargeting.nearestToPlayer(eligible, player::squaredDistanceTo);
        chosen.ifPresent(pet::setTarget);
    }

    /**
     * This player's stance, defaulting to {@link PetCombatMode#PASSIVE} when the profile has not
     * loaded.
     *
     * <p>Failing closed here matters for the same reason it does in {@code PetCombatMode}: a pack
     * that starts fights during a join window the player cannot yet control is a way to lose pets,
     * while a pack that waits a tick for its profile is invisible.
     */
    private static @NotNull PetCombatMode resolveMode(@NotNull ServerPlayerEntity player) {
        final @Nullable McMMOPlayer mmoPlayer = UserManager.getPlayer(player.getUuid());
        if (mmoPlayer == null) {
            return PetCombatMode.PASSIVE;
        }
        final TamingManager taming = mmoPlayer.getTamingManager();
        return taming == null ? PetCombatMode.PASSIVE : taming.getPetCombatMode();
    }
}
