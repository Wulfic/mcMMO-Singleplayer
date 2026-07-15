package com.gmail.nossr50.skills.unarmed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.util.MaterialMapStore;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Proves the MC-free Block Cracker conversion lookup table, which drives the Berserk block-cracking
 * effect wired in {@link com.gmail.nossr50.fabric.listeners.SuperAbilityListener}. Pure lookups —
 * no registry, no config, no player.
 */
class UnarmedTest {

    /** Every block Block Cracker cracks, and what it cracks into. */
    private static final String[][] CRACK_PAIRS = {
            {"stone_bricks", "cracked_stone_bricks"},
            {"infested_stone_bricks", "infested_cracked_stone_bricks"},
            {"deepslate_bricks", "cracked_deepslate_bricks"},
            {"deepslate_tiles", "cracked_deepslate_tiles"},
            {"polished_blackstone_bricks", "cracked_polished_blackstone_bricks"},
            {"nether_bricks", "cracked_nether_bricks"},
    };

    @Test
    void blockCrackerCracksEveryIntactBrickVariant() {
        for (String[] pair : CRACK_PAIRS) {
            assertEquals(Optional.of(pair[1]), Unarmed.blockCrackerConversionTarget(pair[0]));
        }
    }

    @Test
    void blockCrackerConversionIsCaseInsensitive() {
        assertEquals(Optional.of("cracked_stone_bricks"),
                Unarmed.blockCrackerConversionTarget("STONE_BRICKS"));
    }

    @Test
    void blockCrackerDoesNotConvertUnlistedBlocks() {
        assertTrue(Unarmed.blockCrackerConversionTarget("cracked_stone_bricks").isEmpty());
        assertTrue(Unarmed.blockCrackerConversionTarget("stone").isEmpty());
        assertTrue(Unarmed.blockCrackerConversionTarget("").isEmpty());
    }

    /**
     * The whitelist gates the conversion, so a table entry that isn't whitelisted is dead code. This
     * invariant is what exposed the upstream bug: only the two stone-brick variants were whitelisted,
     * leaving the deepslate/blackstone/nether arms of the conversion switch unreachable (see
     * {@code MaterialMapStore#fillBlockCrackerWhiteList}).
     */
    @Test
    void everyCrackableBlockIsBlockCrackerWhitelisted() {
        final MaterialMapStore materialMapStore = new MaterialMapStore();

        for (String[] pair : CRACK_PAIRS) {
            assertTrue(materialMapStore.isBlockCrackerWhiteListed(pair[0]),
                    pair[0] + " has a Block Cracker conversion target but is not whitelisted, so the"
                            + " conversion can never fire");
        }
    }

    /** The reverse invariant: a whitelisted block with no target would crack into nothing. */
    @Test
    void everyBlockCrackerWhitelistedBlockHasACrackTarget() {
        for (String[] pair : CRACK_PAIRS) {
            assertTrue(Unarmed.blockCrackerConversionTarget(pair[0]).isPresent(),
                    pair[0] + " is block-cracker-whitelisted but has no conversion target");
        }
    }

    /** Cracked blocks must not re-crack — the conversion has to be a one-way trip. */
    @Test
    void crackedBlocksAreNotThemselvesCrackable() {
        final MaterialMapStore materialMapStore = new MaterialMapStore();

        for (String[] pair : CRACK_PAIRS) {
            assertTrue(Unarmed.blockCrackerConversionTarget(pair[1]).isEmpty(),
                    pair[1] + " is already cracked but has a further conversion target");
            assertTrue(!materialMapStore.isBlockCrackerWhiteListed(pair[1]),
                    pair[1] + " is already cracked but is block-cracker-whitelisted");
        }
    }
}
