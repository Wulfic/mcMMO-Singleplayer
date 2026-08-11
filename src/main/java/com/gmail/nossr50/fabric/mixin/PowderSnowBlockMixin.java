package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.PlayerMovementTracker;
import net.minecraft.block.PowderSnowBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Parkour's <b>Snow Walker</b>: a skilled runner crosses powder snow instead of sinking into it.
 *
 * <p>{@code canWalkOnPowderSnow} is the whole of vanilla's decision — bytecode-verified,
 * {@code getCollisionShape} returns a full cube when it is true and an empty shape when it is false,
 * and nothing else consults it. So granting exactly what a pair of leather boots grants is one
 * injection, with no separate handling for the sinking, the freezing or the fall damage: none of
 * them happen to a player who never enters the block.
 *
 * <p><b>Why this is a common mixin rather than a server-only one.</b> Collision shapes are computed
 * independently on the client and on the integrated server. Injecting only server-side would leave
 * the client still simulating a fall, and the disagreement shows up as the player sinking and
 * snapping back rather than as a clean walk. Both sides run this, both reach the same answer, and
 * there is nothing to synchronise.
 *
 * <p>That is also why the gate is a published flag rather than a live rank check: this runs on two
 * threads and many times per tick, and {@code RankUtils}' rank cache is a plain {@code HashMap}. See
 * {@link PlayerMovementTracker#canWalkOnPowderSnow(java.util.UUID)}, which the server tick sweep
 * keeps current.
 */
@Mixin(PowderSnowBlock.class)
public class PowderSnowBlockMixin {

    /**
     * Let a ranked player walk on powder snow, exactly as leather boots do.
     *
     * <p>Only ever turns a {@code false} into a {@code true}: injecting at HEAD and returning early
     * on the negative case would be a behaviour change for every other entity vanilla allows through
     * (the {@code POWDER_SNOW_WALKABLE_MOBS} tag, and anyone actually wearing the boots).
     */
    @Inject(method = "canWalkOnPowderSnow", allow = 1, at = @At("HEAD"), cancellable = true)
    private static void mcmmo$parkourSnowWalker(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof PlayerEntity player
                && PlayerMovementTracker.canWalkOnPowderSnow(player.getUuid())) {
            cir.setReturnValue(true);
        }
    }
}
