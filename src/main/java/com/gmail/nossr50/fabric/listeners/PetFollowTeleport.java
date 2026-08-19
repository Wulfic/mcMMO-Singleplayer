package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.fabric.McMMOMod;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Taming quality-of-life (GitHub #2): tamed pets come with you through a long jump inside one world.
 *
 * <h2>What is actually broken, and it is not mcMMO</h2>
 * Vanilla already intends pets to keep up over any distance. {@code FollowOwnerGoal#tick} asks
 * {@code TameableEntity#shouldTryTeleportToOwner()} — which is nothing but
 * {@code squaredDistanceTo(owner) >= 144} — and on a yes calls {@code tryTeleportToOwner()}, with
 * <b>no upper bound at all</b> (bytecode-verified). The goal simply never runs: a pet outside the
 * player's simulation distance stops being ticked, so after a long teleport it sits dormant where it
 * was left and nothing ever re-evaluates the goal. The distance is not the gate; <em>being ticked</em>
 * is.
 *
 * <p>So this is a deliberate override of vanilla behaviour rather than a port defect, and it is
 * therefore behind {@code Skills.Taming.Pets_Follow_Teleport} (default on). Cross-world moves are
 * explicitly out of scope per the issue.
 *
 * <h2>Why it rides the movement sweep instead of a teleport mixin</h2>
 * A player can be relocated by a command, an ender pearl, a chorus fruit, a portal, a respawn or
 * another mod, and those do not share one method — {@code ServerPlayerEntity#teleportTo} and
 * {@code requestTeleport} both bottom out in the network handler by different routes. A per-tick
 * position delta cannot be bypassed by any of them, and {@link PlayerMovementTracker} already keeps
 * the previous position and world for exactly this shape of question. One tick of latency costs
 * nothing: chunk (and therefore entity) unloading is queued, so the pets left behind are still loaded
 * on the tick after the jump.
 *
 * <h2>Vanilla's own placement, then a bounded fallback</h2>
 * The first attempt is {@code tryTeleportToOwner()} — vanilla's own search, which tries ten random
 * spots within ±3 blocks horizontally and ±1 vertically and accepts only a {@code WALKABLE} node with
 * room for the pet's hitbox. Reusing it means pets land beside the player rather than inside them, and
 * that the answer stays right when Mojang edits the rules.
 *
 * <p>That search <em>can</em> come up empty (a small ledge, a one-block alcove), and when it does the
 * pet is stranded for good — no later tick will retry, which is the very bug being fixed. Hence the
 * fallback onto the owner's exact position. ⚠️ It is refused while the owner is airborne: dropping a
 * wolf out of an elytra flight trades "your pet was left behind" for "your pet died on landing", which
 * is a strictly worse bug than the one being fixed. In that case the pet is left where it is, which is
 * exactly the pre-existing behaviour and so never a regression.
 */
public final class PetFollowTeleport {

    private PetFollowTeleport() {
    }

    /** {@code Skills.Taming.Pets_Follow_Teleport} default, mirrored for a missing config. */
    static final boolean DEFAULT_ENABLED = true;

    /**
     * {@code Skills.Taming.Pets_Follow_Teleport_Radius} default, mirrored for a missing config.
     *
     * <p>Raised from 32 to 128 for GitHub #12. 32 assumed the pet was at the owner's heels, which is
     * only true when the owner is walking: a wolf pathing after a sprinting or elytra-borne player is
     * routinely further back than that when the teleport lands, and it was then simply not collected.
     * 128 covers the whole band in which a pet could still have been ticking (vanilla's default
     * simulation distance is 10 chunks), which is the real boundary — beyond it the pet stopped being
     * ticked, which is the very condition this class exists to work around.
     *
     * <p>It is deliberately still bounded. Distance is not what keeps a pet you parked at your base
     * from being yanked across the world — {@code cannotFollowOwner()} is, because such a pet is sat.
     * The bound is about predictability: past the loaded region the answer would depend on which
     * chunks happen to be resident, and a feature that works intermittently is worse than one with a
     * limit a player can learn.
     */
    static final double DEFAULT_RADIUS = 128.0;

    /**
     * Detect a teleport and bring this player's pets along.
     *
     * <p>Called from {@link PlayerMovementTracker#tickPlayer} with the baseline it already keeps.
     * Takes no {@code McMMOPlayer}: nothing here is level-gated or per-profile, and requiring one
     * would make the feature depend on an unrelated load having completed — the ordering trap that
     * class carries four warnings about.
     *
     * @param player    the player who has just moved
     * @param previous  their position on the previous tick, or {@code null} if there is no baseline
     * @param current   their position now
     * @param sameWorld whether {@code previous} was measured in the world they are in now
     */
    public static void onPlayerMoved(@NotNull ServerPlayerEntity player, @Nullable Vec3d previous,
            @NotNull Vec3d current, boolean sameWorld) {
        if (previous == null || !sameWorld || !isTeleport(previous, current)) {
            return;
        }
        if (!isEnabled()) {
            return;
        }
        bringPetsFrom(player, previous, radius());
    }

    /**
     * Whether a single tick's movement is a teleport rather than travel.
     *
     * <p>Shares {@link PlayerMovementTracker#TELEPORT_DELTA} with the movement sweep on purpose: that
     * constant answers "how far is impossible in one tick", and two independent answers to that would
     * eventually disagree about the same jump. Measured in three dimensions here, where the tracker
     * measures horizontally only — the tracker is billing horizontal travel, whereas a vertical
     * {@code /tp} is every bit as much a teleport.
     */
    static boolean isTeleport(@NotNull Vec3d previous, @NotNull Vec3d current) {
        final double limit = PlayerMovementTracker.TELEPORT_DELTA;
        return current.squaredDistanceTo(previous) > limit * limit;
    }

    /**
     * Teleport every eligible pet within {@code radius} of {@code from} to the player.
     *
     * @return how many pets were moved, for the tests to assert on
     */
    static int bringPetsFrom(@NotNull ServerPlayerEntity player, @NotNull Vec3d from, double radius) {
        // ⚠️ The cast is load-bearing on the older bands — do NOT "simplify" it away.
        // ServerPlayerEntity's covariant ServerWorld override of getEntityWorld() was added in
        // 1.21.9. On 1.21 – 1.21.5 only Entity's World-returning form exists, so the bare
        // assignment does not compile there; the cast is a no-op from 1.21.9 up. Read off the
        // merged jars with javap, checking Entity as well as ServerPlayerEntity because javap
        // never lists inherited members.
        // ⚠️ This is NOT one expression for every band. 1.21.6 – 1.21.8 has no
        // Entity#getEntityWorld() at all — it spells the accessor getWorld() — so that band cannot
        // take this line and carries its own. Every other band can, which is why the cast is
        // written here instead of being rediscovered at each cut.
        final ServerWorld world = (ServerWorld) player.getEntityWorld();
        if (world == null) {
            return 0;
        }
        final Box searchBox = Box.of(from, radius * 2, radius * 2, radius * 2);
        final List<TameableEntity> pets = world.getEntitiesByClass(TameableEntity.class, searchBox,
                pet -> isFollower(pet, player));

        int moved = 0;
        for (TameableEntity pet : pets) {
            if (bring(pet, player)) {
                moved++;
            }
        }
        return moved;
    }

    /**
     * Whether this pet is one that would have followed the player had it still been ticking.
     *
     * <p>{@code cannotFollowOwner()} is vanilla's own predicate and covers sitting, being ridden,
     * being leashed and a spectating owner. Reusing it rather than re-listing those conditions is what
     * makes "sit means stay" hold here without a second implementation to keep in step — a pet told to
     * wait somewhere must not be yanked across the world by its owner's ender pearl.
     */
    static boolean isFollower(@NotNull TameableEntity pet, @NotNull ServerPlayerEntity player) {
        return pet.isAlive() && pet.isTamed() && pet.isOwner(player) && !pet.cannotFollowOwner();
    }

    /**
     * Move one pet to its owner. Returns whether it ended up there.
     *
     * <p>{@code shouldTryTeleportToOwner()} is re-asked <em>after</em> vanilla's attempt precisely
     * because it is the same question vanilla asked before it: a lingering yes means the placement
     * search found nowhere to put the pet, not that the pet did not need moving.
     */
    private static boolean bring(@NotNull TameableEntity pet, @NotNull ServerPlayerEntity player) {
        pet.tryTeleportToOwner();
        if (!pet.shouldTryTeleportToOwner()) {
            return true; // Vanilla found it a spot beside the player.
        }

        if (!player.isOnGround() && !player.isTouchingWater()) {
            // See the class doc: a fallback drop onto an airborne owner can kill the pet, so the pet
            // stays put — the vanilla outcome, and never worse than it. Logged because "sometimes my
            // wolf follows and sometimes it doesn't" is otherwise unexplainable from the outside.
            McMMOMod.LOGGER.debug(
                    "Pet follow: no landing spot for {} near airborne owner {}; left behind.",
                    pet.getType().toString(), player.getName().getString());
            return false;
        }

        final Vec3d destination = player.getPos();
        // Cast load-bearing below 1.21.9, and not usable on 1.21.6 – 1.21.8 — see bringPetsFrom.
        final boolean placed = pet.teleport((ServerWorld) player.getEntityWorld(), destination.x, destination.y,
                destination.z, EnumSet.noneOf(PositionFlag.class), pet.getYaw(), pet.getPitch());
        if (!placed) {
            McMMOMod.LOGGER.warn("Pet follow: could not place {} on owner {} at {}; left behind.",
                    pet.getType().toString(), player.getName().getString(), destination);
        }
        return placed;
    }

    private static boolean isEnabled() {
        final GeneralConfig config = McMMOMod.getGeneralConfig();
        return config == null ? DEFAULT_ENABLED : config.arePetsFollowingTeleports();
    }

    private static double radius() {
        final GeneralConfig config = McMMOMod.getGeneralConfig();
        return config == null ? DEFAULT_RADIUS : config.getPetFollowTeleportRadius();
    }
}
