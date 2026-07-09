package com.gmail.nossr50.skills.herbalism;

import java.util.Locale;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * MC-free Green Terra / Shroom Thumb block-conversion lookup tables (Phase 10.3 port). Legacy
 * mutated a live {@code BlockState} directly ({@code blockState.setType(...)}); the port instead
 * returns the target block's registry path so the caller (listener + platform adapter) performs
 * the actual mutation, mirroring {@link com.gmail.nossr50.skills.excavation.ExcavationManager}'s
 * "MC-free decision, live mutation deferred" convention.
 */
public final class Herbalism {

    private Herbalism() {}

    /**
     * Green Terra / Green Thumb block conversion target for the broken block's registry path.
     *
     * @param blockRegistryPath the broken block's vanilla registry path (e.g. {@code "cobblestone"})
     * @return the target block's registry path, or empty if this block doesn't convert
     */
    public static Optional<String> greenTerraConversionTarget(@NotNull String blockRegistryPath) {
        return switch (blockRegistryPath.toLowerCase(Locale.ENGLISH)) {
            case "cobblestone_wall" -> Optional.of("mossy_cobblestone_wall");
            case "stone_bricks" -> Optional.of("mossy_stone_bricks");
            case "dirt", "dirt_path" -> Optional.of("grass_block");
            case "cobblestone" -> Optional.of("mossy_cobblestone");
            default -> Optional.empty();
        };
    }

    /**
     * Shroom Thumb block conversion target for the broken block's registry path.
     *
     * @param blockRegistryPath the broken block's vanilla registry path
     * @return the target block's registry path, or empty if this block doesn't convert
     */
    public static Optional<String> shroomThumbConversionTarget(@NotNull String blockRegistryPath) {
        return switch (blockRegistryPath.toLowerCase(Locale.ENGLISH)) {
            case "dirt", "grass_block", "dirt_path" -> Optional.of("mycelium");
            default -> Optional.empty();
        };
    }
}
