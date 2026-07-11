package com.gmail.nossr50.util;

import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.fabric.McMMOMod;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import org.jetbrains.annotations.NotNull;

/**
 * Item classification helpers — the singleplayer port of the legacy Bukkit {@code ItemUtils}.
 *
 * <p>Every check here is a thin, MC-typed wrapper over the already-unit-tested, MC-free
 * {@link MaterialMapStore} (see memory {@code phase-10-9} keystone): it extracts the item's vanilla
 * registry-id path (e.g. {@code diamond_axe}) via {@link Registries#ITEM} and delegates the actual
 * set membership to the store. So the classification <em>logic</em> is proven MC-free in
 * {@link MaterialMapStoreTest}; this layer only bridges a live {@link ItemStack} to that logic. The
 * id-path extraction needs live registries, so these are exercised in {@code ItemUtilsTest} under the
 * {@code fabric-loader-junit} harness ({@code Bootstrap.initialize()} in a {@code @BeforeAll}).
 *
 * <p><b>Deliberately NOT ported here</b> (each needs an adapter mcMMO doesn't have yet, PORT
 * breadcrumbs left for when the consuming skill body lands): the inventory helpers
 * ({@code hasItemIncludingOffHand}/{@code removeItemIncludingOffHand}), the enchantment-inspection
 * helpers ({@code doesPlayerHaveEnchantmentOnArmor}/{@code hasEnchantment}), the item-spawn helpers
 * ({@code spawnItems*}), and the metadata/lore mutators ({@code addDigSpeedToItem},
 * {@code removeAbilityLore}, {@code customName}, {@code isMcMMOItem}/{@code isChimaeraWing}). The
 * drop-source classifiers ({@code isMiningDrop} etc.) are ExperienceConfig-driven and port with the
 * salvage/repair item configs.
 */
public final class ItemUtils {

    private ItemUtils() {}

    /**
     * The vanilla registry-id <em>path</em> of an item stack's item (e.g. {@code diamond_axe} for
     * {@code minecraft:diamond_axe}) — the key {@link MaterialMapStore} is keyed on. An empty stack
     * is {@code minecraft:air}, i.e. path {@code air}, which is in none of the tool/armor sets.
     */
    private static @NotNull String idPath(@NotNull ItemStack item) {
        return Registries.ITEM.getId(item.getItem()).getPath();
    }

    // --- Weapons ------------------------------------------------------------

    public static boolean isBow(@NotNull ItemStack item) {
        return McMMOMod.getMaterialMapStore().isBow(idPath(item));
    }

    public static boolean isCrossbow(@NotNull ItemStack item) {
        return McMMOMod.getMaterialMapStore().isCrossbow(idPath(item));
    }

    public static boolean isTrident(@NotNull ItemStack item) {
        return McMMOMod.getMaterialMapStore().isTrident(idPath(item));
    }

    public static boolean isMace(@NotNull ItemStack item) {
        return McMMOMod.getMaterialMapStore().isMace(idPath(item));
    }

    public static boolean isSpear(@NotNull ItemStack item) {
        return McMMOMod.getMaterialMapStore().isSpear(idPath(item));
    }

    public static boolean isSword(@NotNull ItemStack item) {
        return McMMOMod.getMaterialMapStore().isSword(idPath(item));
    }

    // --- Tools --------------------------------------------------------------

    public static boolean isHoe(@NotNull ItemStack item) {
        return McMMOMod.getMaterialMapStore().isHoe(idPath(item));
    }

    public static boolean isShovel(@NotNull ItemStack item) {
        return McMMOMod.getMaterialMapStore().isShovel(idPath(item));
    }

    public static boolean isAxe(@NotNull ItemStack item) {
        return McMMOMod.getMaterialMapStore().isAxe(idPath(item));
    }

    public static boolean isPickaxe(@NotNull ItemStack item) {
        return McMMOMod.getMaterialMapStore().isPickAxe(idPath(item));
    }

    /**
     * Whether the item counts as "unarmed" (drives the Unarmed skill's held-item gate). Faithful to
     * legacy: when the {@code Unarmed_Items_As_Unarmed} config toggle is on, any non-vanilla-tool
     * item counts as a fist; otherwise only a truly empty hand does. Null-safe on the config so the
     * check is usable before a server session has wired configs (returns the empty-hand semantics).
     */
    public static boolean isUnarmed(@NotNull ItemStack item) {
        GeneralConfig config = McMMOMod.getGeneralConfig();
        if (config != null && config.getUnarmedItemsAsUnarmed()) {
            return !isMinecraftTool(item);
        }
        return item.isEmpty();
    }

    /** A vanilla tool (any of the pick/axe/shovel/hoe/sword tiers) — Bukkit {@code isMinecraftTool}. */
    public static boolean isMinecraftTool(@NotNull ItemStack item) {
        return McMMOMod.getMaterialMapStore().isTool(idPath(item));
    }

    public static boolean isStoneTool(@NotNull ItemStack item) {
        return McMMOMod.getMaterialMapStore().isStoneTool(idPath(item));
    }

    public static boolean isWoodTool(@NotNull ItemStack item) {
        return McMMOMod.getMaterialMapStore().isWoodTool(idPath(item));
    }

    public static boolean isStringTool(@NotNull ItemStack item) {
        return McMMOMod.getMaterialMapStore().isStringTool(idPath(item));
    }

    public static boolean isPrismarineTool(@NotNull ItemStack item) {
        return McMMOMod.getMaterialMapStore().isPrismarineTool(idPath(item));
    }

    public static boolean isCopperTool(@NotNull ItemStack item) {
        return McMMOMod.getMaterialMapStore().isCopperTool(idPath(item));
    }

    public static boolean isGoldTool(@NotNull ItemStack item) {
        return McMMOMod.getMaterialMapStore().isGoldTool(idPath(item));
    }

    public static boolean isIronTool(@NotNull ItemStack item) {
        return McMMOMod.getMaterialMapStore().isIronTool(idPath(item));
    }

    public static boolean isDiamondTool(@NotNull ItemStack item) {
        return McMMOMod.getMaterialMapStore().isDiamondTool(idPath(item));
    }

    public static boolean isNetheriteTool(@NotNull ItemStack item) {
        return McMMOMod.getMaterialMapStore().isNetheriteTool(idPath(item));
    }

    // --- Armor --------------------------------------------------------------

    public static boolean isArmor(@NotNull ItemStack item) {
        return McMMOMod.getMaterialMapStore().isArmor(idPath(item));
    }

    public static boolean isLeatherArmor(@NotNull ItemStack item) {
        return McMMOMod.getMaterialMapStore().isLeatherArmor(idPath(item));
    }

    public static boolean isGoldArmor(@NotNull ItemStack item) {
        return McMMOMod.getMaterialMapStore().isGoldArmor(idPath(item));
    }

    public static boolean isIronArmor(@NotNull ItemStack item) {
        return McMMOMod.getMaterialMapStore().isIronArmor(idPath(item));
    }

    public static boolean isCopperArmor(@NotNull ItemStack item) {
        return McMMOMod.getMaterialMapStore().isCopperArmor(idPath(item));
    }

    public static boolean isDiamondArmor(@NotNull ItemStack item) {
        return McMMOMod.getMaterialMapStore().isDiamondArmor(idPath(item));
    }

    public static boolean isNetheriteArmor(@NotNull ItemStack item) {
        return McMMOMod.getMaterialMapStore().isNetheriteArmor(idPath(item));
    }

    public static boolean isChainmailArmor(@NotNull ItemStack item) {
        return McMMOMod.getMaterialMapStore().isChainmailArmor(idPath(item));
    }

    // --- Misc ---------------------------------------------------------------

    public static boolean isEnchantable(@NotNull ItemStack item) {
        return McMMOMod.getMaterialMapStore().isEnchantable(idPath(item));
    }
}
