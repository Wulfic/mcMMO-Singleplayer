package com.gmail.nossr50.datatypes.skills;

import org.jetbrains.annotations.Nullable;

/**
 * The material family of a repairable/salvageable item, used to pick a default repair/salvage
 * material and (in Bukkit) to gate permissions. Ported MC-free: {@link #getDefaultMaterial()}
 * returns a vanilla registry <em>path</em> string (e.g. {@code "iron_ingot"}) instead of an
 * {@code org.bukkit.Material}, so this enum carries no Minecraft types and the repair/salvage
 * config can resolve the default through {@link com.gmail.nossr50.platform.Materials}.
 */
public enum MaterialType {
    STRING,
    LEATHER,
    WOOD,
    STONE,
    IRON,
    COPPER,
    GOLD,
    DIAMOND,
    NETHERITE,
    PRISMARINE,
    OTHER;

    /**
     * The vanilla registry path of the material used, by default, to repair/salvage an item of this
     * family when the config does not name one explicitly. {@code null} for {@link #OTHER} (which
     * has no sensible default — those items must name their material in the config).
     *
     * @return the default material's registry path, or {@code null} for {@link #OTHER}
     */
    public @Nullable String getDefaultMaterial() {
        return switch (this) {
            case STRING -> "string";
            case LEATHER -> "leather";
            case WOOD -> "oak_planks";
            case STONE -> "cobblestone";
            case IRON -> "iron_ingot";
            case GOLD -> "gold_ingot";
            case DIAMOND -> "diamond";
            // 1.21 always ships netherite_scrap; legacy fell back to diamond on older versions.
            case NETHERITE -> "netherite_scrap";
            case PRISMARINE -> "prismarine_crystals";
            case COPPER -> "copper_ingot";
            case OTHER -> null;
        };
    }
}
