package com.gmail.nossr50.platform;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
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
        return BuiltInRegistries.ITEM.getId(handle.getItem());
    }

    /**
     * Registry <em>path</em> of the item, e.g. {@code diamond_pickaxe} — the key the MC-free
     * repair/salvage tables are built on, so callers can look a stack up without an {@link Identifier}.
     */
    public @NotNull String getTypePath() {
        return getTypeId().getPath();
    }

    // --- Amount -------------------------------------------------------------

    public int getAmount() {
        return handle.getCount();
    }

    public void setAmount(int amount) {
        handle.setCount(amount);
    }

    public int getMaxAmount() {
        return handle.getMaxStackSize();
    }

    /** Remove {@code amount} items from this stack, in place (vanilla {@code decrement}). */
    public void decrement(int amount) {
        handle.shrink(amount);
    }

    // --- Durability (Bukkit durability == vanilla damage) -------------------

    public boolean isDamageable() {
        return handle.isDamageableItem();
    }

    public int getDurability() {
        return handle.getDamageValue();
    }

    public void setDurability(int damage) {
        handle.setDamageValue(damage);
    }

    public int getMaxDurability() {
        return handle.getMaxDamage();
    }

    // --- Unbreakable / enchantments (Bukkit ItemMeta.isUnbreakable / getEnchantmentLevel) --------

    /**
     * Whether this stack carries the vanilla {@code UNBREAKABLE} data component (Bukkit
     * {@code ItemMeta.isUnbreakable()}). Unbreakable tools take no durability damage, so skill
     * durability changes are a no-op on them.
     */
    public boolean isUnbreakable() {
        return handle.has(DataComponents.UNBREAKABLE);
    }

    /**
     * Level of {@code enchantmentKey} on this stack, or {@code 0} if absent (Bukkit
     * {@code ItemStack.getEnchantmentLevel}). Resolves by iterating the stack's enchantment component
     * and matching the {@link RegistryKey} — no registry-manager access is needed, so this is callable
     * without a world context.
     */
    public int getEnchantmentLevel(@NotNull ResourceKey<Enchantment> enchantmentKey) {
        if (!handle.isEnchanted()) {
            return 0;
        }
        ItemEnchantments enchantments = handle.getEnchantments();
        for (Holder<Enchantment> entry : enchantments.keySet()) {
            if (entry.is(enchantmentKey)) {
                return enchantments.getLevel(entry);
            }
        }
        return 0;
    }

    /** Convenience: the {@code Unbreaking} enchantment level (durability-damage reduction divisor). */
    public int getUnbreakingLevel() {
        return getEnchantmentLevel(Enchantments.UNBREAKING);
    }

    // --- Similarity / copy --------------------------------------------------

    /**
     * Bukkit {@code isSimilar}: same item ignoring stack count. NOTE: this currently compares
     * item type only, not components/meta — refine once the ItemMeta adapter lands.
     */
    public boolean isSimilar(@NotNull PlatformItem other) {
        return ItemStack.isSameItem(handle, other.handle);
    }

    /**
     * Same item <em>and</em> same data components, ignoring stack count (vanilla
     * {@code areItemsAndComponentsEqual}). Stricter than {@link #isSimilar}: this is the comparison
     * that decides whether a brewing-stand ingredient is a configured one, where "sugar" and
     * "sugar with a custom name" must not be conflated.
     */
    public boolean matchesItemAndComponents(@NotNull PlatformItem other) {
        return ItemStack.isSameItemSameComponents(handle, other.handle);
    }

    /** Full equality including stack count (vanilla {@code ItemStack.areEqual}). */
    public boolean matchesExactly(@NotNull PlatformItem other) {
        return ItemStack.matches(handle, other.handle);
    }

    public @NotNull PlatformItem copy() {
        return new PlatformItem(handle.copy());
    }

    /** A fresh copy of this stack carrying {@code count} items. */
    public @NotNull PlatformItem copyWithCount(int count) {
        return new PlatformItem(handle.copyWithCount(count));
    }

    /**
     * Delegates to the wrapped stack, so a {@code PlatformItem} in a log line or a collection dump
     * reads like the item it is rather than {@code PlatformItem@1a2b3c}.
     */
    @Override
    public String toString() {
        return handle.toString();
    }
}
