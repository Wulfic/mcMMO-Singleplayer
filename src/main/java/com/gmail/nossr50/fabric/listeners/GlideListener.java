package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.skills.movement.MovementManager;
import com.gmail.nossr50.skills.movement.Medium;
import com.gmail.nossr50.util.player.UserManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;

/**
 * Agility's air-domain velocity maths, split out of
 * {@link com.gmail.nossr50.fabric.mixin.LivingEntityGlideMixin} so the mixin stays a one-line seam.
 *
 * <p>Two sub-skills share this one hook because they modify the same vector on the same tick:
 * <b>Fleet Footed (air)</b> scales the horizontal components, and <b>Glide</b> scales the downward
 * component. Splitting them into separate injectors would mean two passes over the same value for no
 * gain.
 *
 * <p>Runs on <em>both</em> logical sides. That is the point rather than an oversight: the client
 * simulates its own flight, so applying the identical factor on both sides is what makes the boost
 * visible without sending a velocity packet every tick. In singleplayer both sides share a JVM and
 * resolve the same {@link McMMOPlayer} out of {@link UserManager} by UUID, so the two computations
 * agree by construction.
 */
public final class GlideListener {

    private GlideListener() {
    }

    /**
     * Apply the player's Agility glide bonuses to the velocity vanilla just computed.
     *
     * @param entity        the gliding entity — mobs can glide too, and have no mcMMO data
     * @param glideVelocity vanilla's computed velocity for this tick
     * @return the modified velocity, or {@code glideVelocity} unchanged when nothing applies
     */
    public static @NotNull Vec3d modifyGlideVelocity(@NotNull LivingEntity entity,
            @NotNull Vec3d glideVelocity) {
        if (!(entity instanceof PlayerEntity)) {
            return glideVelocity;
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(entity.getUuid());
        if (mmoPlayer == null) {
            return glideVelocity;
        }
        final MovementManager agility = mmoPlayer.getMovementManager();
        if (agility == null) {
            return glideVelocity;
        }

        final double forwardBonus = agility.getFleetFootedBonus(Medium.AIR);
        final double descentReduction = agility.getGlideDescentReduction();
        if (forwardBonus <= 0 && descentReduction <= 0) {
            return glideVelocity; // Nothing unlocked — the common case; don't allocate a Vec3d.
        }

        final double scale = 1.0 + forwardBonus;
        // Only soften *downward* motion. Scaling upward motion too would let a maxed player climb on
        // a rising thermal indefinitely, and the sub-skill is "descend slower", not "fly upward".
        final double y = glideVelocity.y < 0
                ? glideVelocity.y * (1.0 - descentReduction)
                : glideVelocity.y;
        return new Vec3d(glideVelocity.x * scale, y, glideVelocity.z * scale);
    }
}
