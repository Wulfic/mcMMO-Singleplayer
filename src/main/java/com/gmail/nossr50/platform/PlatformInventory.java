package com.gmail.nossr50.platform;

import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.jetbrains.annotations.NotNull;

/**
 * Adapter over a slot-indexed vanilla inventory (a {@link DefaultedList} of {@link ItemStack}),
 * replacing Bukkit's {@code Inventory}/{@code BrewerInventory}.
 *
 * <p>This is a <em>view</em>, not a copy: {@link #set} writes straight into the list the caller
 * handed over, and the {@link PlatformItem} {@link #get} returns wraps the live stack, so mutating it
 * (e.g. {@link PlatformItem#decrement}) mutates the inventory. That is load-bearing — the Alchemy
 * brew path is handed the block entity's own slot list by the {@code BrewingStandBlockEntityMixin}
 * and is expected to transform it in place, exactly as vanilla's {@code craft} does.
 */
public final class PlatformInventory {

    private final DefaultedList<ItemStack> slots;

    public PlatformInventory(@NotNull DefaultedList<ItemStack> slots) {
        this.slots = slots;
    }

    public @NotNull DefaultedList<ItemStack> unwrap() {
        return slots;
    }

    public int size() {
        return slots.size();
    }

    /** The stack in {@code slot}, as a live view — never null; an empty slot yields an empty item. */
    public @NotNull PlatformItem get(int slot) {
        return new PlatformItem(slots.get(slot));
    }

    public void set(int slot, @NotNull PlatformItem item) {
        slots.set(slot, item.unwrap());
    }

    /** Empty {@code slot} out (vanilla's {@code ItemStack.EMPTY} sentinel). */
    public void clear(int slot) {
        slots.set(slot, ItemStack.EMPTY);
    }
}
