package com.gmail.nossr50.skills.unarmed;

import java.util.Locale;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * MC-free Block Cracker conversion lookup table, the Unarmed sibling of
 * {@link com.gmail.nossr50.skills.herbalism.Herbalism}. Legacy {@code UnarmedManager#blockCrackerCheck}
 * switched on a live {@code Material} and mutated the block in place ({@code block.setType(...)});
 * the port returns the target block's registry path instead and leaves the mutation to the listener,
 * keeping the decision unit-testable without a world.
 */
public final class Unarmed {

    private Unarmed() {}

    /**
     * Block Cracker conversion target for the struck block's registry path — the intact brick/tile
     * block Berserk's fists crack.
     *
     * <p>Every path handled here must also be block-cracker-whitelisted in
     * {@link com.gmail.nossr50.util.MaterialMapStore}, since that whitelist gates the call: an entry
     * here without one there is dead code. {@code UnarmedTest} asserts both directions.
     *
     * @param blockRegistryPath the struck block's vanilla registry path (e.g. {@code "stone_bricks"})
     * @return the cracked block's registry path, or empty if this block doesn't crack
     */
    public static Optional<String> blockCrackerConversionTarget(@NotNull String blockRegistryPath) {
        return switch (blockRegistryPath.toLowerCase(Locale.ENGLISH)) {
            case "stone_bricks" -> Optional.of("cracked_stone_bricks");
            case "infested_stone_bricks" -> Optional.of("infested_cracked_stone_bricks");
            case "deepslate_bricks" -> Optional.of("cracked_deepslate_bricks");
            case "deepslate_tiles" -> Optional.of("cracked_deepslate_tiles");
            case "polished_blackstone_bricks" -> Optional.of("cracked_polished_blackstone_bricks");
            case "nether_bricks" -> Optional.of("cracked_nether_bricks");
            default -> Optional.empty();
        };
    }
}
