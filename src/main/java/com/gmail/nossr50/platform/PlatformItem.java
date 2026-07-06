package com.gmail.nossr50.platform;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

/**
 * Adapter over a vanilla {@link ItemStack}, replacing {@code org.bukkit.inventory.ItemStack}
 * (64 refs, 72 {@code getType()} calls). Covers the tractable mined surface: type identity,
 * amount, damage/durability, emptiness, similarity.
 *
 * <p>DELIBERATELY DEFERRED (needs more design — see memory {@code phase-2-adapter-layer}):
 * Bukkit {@code ItemMeta} ({@code get/setItemMeta}, 36+16 refs) -> 1.21 DataComponents for
 * display name / lore; and enchantment access ({@code getEnchantmentLevel},
 * {@code add/removeEnchantment}) which in 1.21 needs the dynamic enchantment registry
 * ({@code world.getRegistryManager()} -> {@code RegistryEntry<Enchantment>}), not a static enum.
 * Those get their own adapter once a consumer (a repair/salvage skill) is ported to validate
 * the shape. Use {@link #unwrap()} meanwhile.
 *
 * <p>Bukkit durability == vanilla damage value; {@link #getDurability()}/{@link #setDurability}
 * map to {@code getDamage}/{@code setDamage}.
 */
public final class PlatformItem {

    private final ItemStack handle;

    public PlatformItem(@NotNull ItemStack handle) {
        this.handle = handle;
    }

    public @NotNull ItemStack unwrap() {
        return handle;
    }

    public boolean isEmpty() {
        return handle.isEmpty();
    }

    // --- Type identity (Bukkit getType) -------------------------------------

    public @NotNull Item getItem() {
        return handle.getItem();
    }

    /** Registry id of the item, e.g. {@code minecraft:diamond_pickaxe}. */
    public @NotNull Identifier getTypeId() {
        return Registries.ITEM.getId(handle.getItem());
    }

    // --- Amount -------------------------------------------------------------

    public int getAmount() {
        return handle.getCount();
    }

    public void setAmount(int amount) {
        handle.setCount(amount);
    }

    public int getMaxAmount() {
        return handle.getMaxCount();
    }

    // --- Durability (Bukkit durability == vanilla damage) -------------------

    public boolean isDamageable() {
        return handle.isDamageable();
    }

    public int getDurability() {
        return handle.getDamage();
    }

    public void setDurability(int damage) {
        handle.setDamage(damage);
    }

    public int getMaxDurability() {
        return handle.getMaxDamage();
    }

    // --- Similarity / copy --------------------------------------------------

    /**
     * Bukkit {@code isSimilar}: same item ignoring stack count. NOTE: this currently compares
     * item type only, not components/meta — refine once the ItemMeta adapter lands.
     */
    public boolean isSimilar(@NotNull PlatformItem other) {
        return ItemStack.areItemsEqual(handle, other.handle);
    }

    public @NotNull PlatformItem copy() {
        return new PlatformItem(handle.copy());
    }
}
