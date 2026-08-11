package com.gmail.nossr50.platform;

import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.datatypes.skills.ToolType;
import com.gmail.nossr50.fabric.McMMOMod;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import org.jetbrains.annotations.NotNull;

/**
 * Item classification helpers — the singleplayer port of the legacy Bukkit {@code ItemUtils}.
 *
 * <p>Every check here is a thin, MC-typed wrapper over the already-unit-tested, MC-free
 * {@link com.gmail.nossr50.util.MaterialMapStore} (see memory {@code phase-10-9} keystone): it extracts the item's vanilla
 * registry-id path (e.g. {@code diamond_axe}) via {@link Registries#ITEM} and delegates the actual
 * set membership to the store. So the classification <em>logic</em> is proven MC-free in
 * {@code MaterialMapStoreTest}; this layer only bridges a live {@link ItemStack} to that logic. The
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
     * {@code minecraft:diamond_axe}) — the key {@link com.gmail.nossr50.util.MaterialMapStore} is keyed on. An empty stack
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

    // --- Super-ability tool prep --------------------------------------------

    /**
     * Whether {@code itemStack} is the tool that {@code toolType}'s super-ability prep expects — step
     * 1 of the 2-step super-ability activation (raise the right tool, then interact).
     *
     * <p>Lives here rather than on {@link ToolType} itself (Phase 2): the enum is otherwise pure data
     * — a pair of locale keys per constant — and this was the single method dragging
     * {@code net.minecraft} into {@code datatypes/}. The classification it performs is exactly what
     * this class exists to do.
     *
     * <p>Faithful to upstream mcMMO: {@link ToolType#FISTS} is a bare empty hand
     * ({@link ItemStack#isEmpty()}, upstream's {@code Material.AIR} check), and {@link ToolType#BOW}
     * has no tool-raise (upstream's switch had no BOW case, so it fell to {@code false}) because
     * Archery has no super ability to prime this way.
     */
    public static boolean isToolInHand(@NotNull ToolType toolType, @NotNull ItemStack itemStack) {
        return switch (toolType) {
            case AXE -> isAxe(itemStack);
            case FISTS -> itemStack.isEmpty();
            case HOE -> isHoe(itemStack);
            case PICKAXE -> isPickaxe(itemStack);
            case SHOVEL -> isShovel(itemStack);
            case SWORD -> isSword(itemStack);
            case CROSSBOW -> isCrossbow(itemStack);
            case TRIDENTS -> isTrident(itemStack);
            case MACES -> isMace(itemStack);
            case BOW -> false;
        };
    }
}
