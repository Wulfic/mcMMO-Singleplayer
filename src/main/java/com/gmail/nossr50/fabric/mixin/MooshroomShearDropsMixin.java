package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.HusbandryListener;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.MooshroomEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * {@code Bountiful Harvest} for the one shearable species that does not use the shared drop path.
 *
 * <p>A sheared mooshroom converts itself into a cow and then spawns its mushrooms as
 * {@code ItemEntity}s built by hand — it never calls {@code dropStack}, so
 * {@code EntityShearDropMixin} cannot see it. The seam is therefore the spawn itself.
 *
 * <h2>&#9888; Two spawns, not one</h2>
 * {@code sheared} calls {@code World#spawnEntity} <b>twice</b>: once for the replacement cow, and once
 * per mushroom. Hence {@code allow = 2} — and hence the {@code instanceof ItemEntity} test, which is
 * what keeps the cow out of it. A narrower {@code allow} would fail the ship gate rather than quietly
 * bind to whichever call the matcher happened to reach first.
 *
 * <p>The stack is doubled <em>before</em> the entity is spawned, so this stays one {@code ItemEntity}
 * carrying two mushrooms rather than two entities — the same shape
 * {@link HusbandryListener#onShearDropStack} produces for every other species, and it reads the same
 * window {@code ShearPayoutMixin} opens, so a mooshroom pays out on exactly the same roll as a sheep.
 *
 * <p>&#9888; A {@code @ModifyConstant} on the loop bound would have been shorter and is deliberately
 * <b>not</b> used: {@code scripts/mixin-allow-audit.py} cannot resolve constant injectors, so it
 * reports them as binding to nothing. An injector the ship gate is blind to is exactly what that gate
 * exists to prevent.
 */
@Mixin(MooshroomEntity.class)
public abstract class MooshroomShearDropsMixin {

    @ModifyArg(method = "sheared(Lnet/minecraft/sound/SoundCategory;)V", allow = 2, index = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z"))
    private Entity mcmmo$mooshroomBonusMushrooms(Entity spawned) {
        if (spawned instanceof ItemEntity mushroom) {
            mushroom.setStack(HusbandryListener.onShearDropStack(mushroom.getStack()));
        }
        return spawned;
    }
}
