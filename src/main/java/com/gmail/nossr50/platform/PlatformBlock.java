package com.gmail.nossr50.platform;

import java.util.Collections;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

/**
 * Adapter over a world position, replacing {@code org.bukkit.block.Block} (~55 refs, 87
 * {@code getType()} calls — the heaviest block surface). Bukkit's {@code Block} is location-bound
 * (knows its world + coordinates), whereas Yarn splits identity ({@link Block}), snapshot
 * ({@link BlockState}) and location ({@link BlockPos}). This wrapper re-bundles them as
 * {@code (World, BlockPos)} so ported code keeps the Bukkit mental model.
 *
 * <p>Grounded in mined usage: getType/setType, getState, getRelative, getX/Y/Z, getWorld,
 * getDrops. Bukkit {@code getType()} returned a {@code Material}; here use {@link #getBlock()}
 * / {@link #getTypeId()} and compare against {@link Materials} lookups.
 */
public final class PlatformBlock {

    private final World world;
    private final BlockPos pos;

    public PlatformBlock(@NotNull World world, @NotNull BlockPos pos) {
        this.world = world;
        this.pos = pos;
    }

    public @NotNull World getWorld() {
        return world;
    }

    public @NotNull BlockPos getPos() {
        return pos;
    }

    public int getX() {
        return pos.getX();
    }

    public int getY() {
        return pos.getY();
    }

    public int getZ() {
        return pos.getZ();
    }

    // --- Type / state (Bukkit getType / getState / getBlockData) -------------

    /** Live {@link BlockState} at this position (Bukkit {@code getBlockData}/{@code getState}). */
    public @NotNull BlockState getState() {
        return world.getBlockState(pos);
    }

    /** The block identity at this position (Bukkit {@code getType()} intent). */
    public @NotNull Block getBlock() {
        return getState().getBlock();
    }

    /** Registry id of the current block, e.g. {@code minecraft:stone}. */
    public @NotNull Identifier getTypeId() {
        return Registries.BLOCK.getId(getBlock());
    }

    public boolean isAir() {
        return getState().isAir();
    }

    /** Bukkit {@code setType(Material)}: set to a block's default state. */
    public void setType(@NotNull Block block) {
        world.setBlockState(pos, block.getDefaultState());
    }

    /** Bukkit {@code setBlockData(BlockData)}: set to a specific state. */
    public void setState(@NotNull BlockState state) {
        world.setBlockState(pos, state);
    }

    // --- Navigation (Bukkit getRelative) ------------------------------------

    public @NotNull PlatformBlock getRelative(@NotNull Direction direction) {
        return new PlatformBlock(world, pos.offset(direction));
    }

    public @NotNull PlatformBlock getRelative(@NotNull Direction direction, int distance) {
        return new PlatformBlock(world, pos.offset(direction, distance));
    }

    // --- Drops (Bukkit getDrops) --------------------------------------------

    /**
     * Items this block would drop if broken now. Requires a server world (drops are
     * server-authoritative); returns empty on the logical client.
     */
    public @NotNull List<ItemStack> getDrops() {
        if (!(world instanceof ServerWorld serverWorld)) {
            return Collections.emptyList();
        }
        final BlockEntity blockEntity = world.getBlockEntity(pos);
        return Block.getDroppedStacks(getState(), serverWorld, pos, blockEntity);
    }
}
