package com.gmail.nossr50.fabric.mixin;

import net.minecraft.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read/write access to {@code BrewingStandBlockEntity#brewTime}, which the Catalysis brew-speed
 * sub-skill has to shorten (CONVERSION_TODO §B/§E).
 *
 * <p>The field is package-private with no getter or setter, and vanilla's {@code tick} — the only
 * place a brew's timer is touched — is {@code static}, so a {@code @Shadow} on the sibling
 * {@link BrewingStandBlockEntityMixin} would not be reachable from its static injection handlers.
 * An accessor mixin sidesteps both problems: Mixin generates the two methods onto the target class,
 * so {@code fabric/listeners/AlchemyListener} can cast the block entity it is handed and adjust the
 * timer without any of this leaking into the mixin's injection handlers.
 */
@Mixin(BrewingStandBlockEntity.class)
public interface BrewingStandBrewTimeAccessor {

    /** Ticks left on the current brew, or {@code 0} when no brew is running. */
    @Accessor("brewTime")
    int getBrewTime();

    /** Overwrite the ticks left on the current brew. */
    @Accessor("brewTime")
    void setBrewTime(int brewTime);
}
