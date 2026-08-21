package com.gmail.nossr50.platform;

import java.util.List;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Item-spawn adapter for block-break side effects (CONVERSION_TODO Phase 3). The legacy plugin added
 * mcMMO's bonus drops by tagging the broken block with {@code BonusDropMeta} and letting Bukkit's
 * {@code BlockDropItemEvent} re-emit the tagged drops. Fabric's {@code PlayerBlockBreakEvents.AFTER}
 * fires <i>after</i> the block is gone and its vanilla drops have already spawned, so instead of
 * multiplying an existing drop we roll fresh loot and scatter the extra copies at the block position.
 *
 * <p>Each extra round is an independent {@link Block#getDroppedStacks} loot roll with the real
 * breaking context (block state, block entity, breaker, tool), so Fortune / Silk Touch behave exactly
 * as they did on the original break — the only observable difference from legacy is that a Fortune
 * bonus copy gets its own Fortune roll rather than duplicating the first roll's exact count.
 *
 * <p>Kept out of the skill managers (which stay MC-free/unit-tested): the managers decide
 * <i>whether</i> and <i>how many</i> extra drops to spawn; this class performs the vanilla-typed
 * spawn and the enchantment lookup.
 */
public final class BlockDrops {

    private BlockDrops() {
    }

    /**
     * Whether the given tool carries Silk Touch. Needs the world's dynamic enchantment registry
     * (1.21 moved enchantments off a static enum), so it lives here rather than in a manager.
     *
     * @param world the server world (source of the enchantment registry)
     * @param tool the tool to inspect (an empty/null stack is never enchanted)
     * @return whether {@code tool} has Silk Touch at level ≥ 1
     */
    public static boolean hasSilkTouch(@NotNull ServerLevel world, @Nullable ItemStack tool) {
        if (tool == null || tool.isEmpty()) {
            return false;
        }
        final Holder<Enchantment> silkTouch = world.registryAccess()
                .getOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SILK_TOUCH);
        return EnchantmentHelper.getItemEnchantmentLevel(silkTouch, tool) > 0;
    }

    /**
     * Scatter {@code rounds} extra copies of a broken block's natural loot at its position. Each
     * round is a fresh loot roll using the real break context, so bonus drops respect the tool's
     * enchantments. A non-positive {@code rounds} is a no-op.
     *
     * @param world the server world the block was broken in
     * @param pos the block position (drops spawn at its centre)
     * @param state the broken block's state (captured before removal by the AFTER hook)
     * @param blockEntity the broken block's block entity, if any (needed for container/loot drops)
     * @param breaker the player who broke the block (loot context)
     * @param tool the tool used to break it (enchantment/loot context)
     * @param rounds how many extra copies of the loot to spawn
     */
    public static void dropBonusLoot(@NotNull ServerLevel world, @NotNull BlockPos pos,
            @NotNull BlockState state, @Nullable BlockEntity blockEntity,
            @NotNull ServerPlayer breaker, @Nullable ItemStack tool, int rounds) {
        for (int round = 0; round < rounds; round++) {
            final List<ItemStack> loot =
                    Block.getDrops(state, world, pos, blockEntity, breaker, tool);
            for (ItemStack stack : loot) {
                if (!stack.isEmpty()) {
                    Block.popResource(world, pos, stack);
                }
            }
        }
    }
}
