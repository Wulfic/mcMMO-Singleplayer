package com.gmail.nossr50.skills.herbalism;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Proves the MC-free Green Terra / Shroom Thumb block-conversion lookup tables, which drive the
 * Green Terra super-ability effect wired in
 * {@link com.gmail.nossr50.fabric.listeners.SuperAbilityListener}. Pure lookups — no registry, no
 * config, no player.
 */
class HerbalismTest {

    @Test
    void greenTerraConvertsEveryMossifiableBlock() {
        assertEquals(Optional.of("mossy_cobblestone"),
                Herbalism.greenTerraConversionTarget("cobblestone"));
        assertEquals(Optional.of("mossy_cobblestone_wall"),
                Herbalism.greenTerraConversionTarget("cobblestone_wall"));
        assertEquals(Optional.of("mossy_stone_bricks"),
                Herbalism.greenTerraConversionTarget("stone_bricks"));
        assertEquals(Optional.of("grass_block"), Herbalism.greenTerraConversionTarget("dirt"));
        assertEquals(Optional.of("grass_block"), Herbalism.greenTerraConversionTarget("dirt_path"));
    }

    @Test
    void greenTerraConversionIsCaseInsensitive() {
        assertEquals(Optional.of("mossy_cobblestone"),
                Herbalism.greenTerraConversionTarget("COBBLESTONE"));
    }

    @Test
    void greenTerraDoesNotConvertUnlistedBlocks() {
        assertTrue(Herbalism.greenTerraConversionTarget("mossy_cobblestone").isEmpty());
        assertTrue(Herbalism.greenTerraConversionTarget("wheat").isEmpty());
        assertTrue(Herbalism.greenTerraConversionTarget("").isEmpty());
    }

    /**
     * Every block on the mossify whitelist must have a conversion target, otherwise a Green Terra
     * strike on it consumes nothing and silently does nothing. The whitelist's pre-1.17
     * {@code grass_path} alias is excluded — it matches no block in a modern registry (see
     * {@code MaterialMapStore#fillMossyWhiteList}).
     */
    @Test
    void everyMossifiableBlockHasAConversionTarget() {
        for (String mossifiable : new String[]{"cobblestone", "dirt", "dirt_path", "stone_bricks",
                "cobblestone_wall"}) {
            assertTrue(Herbalism.greenTerraConversionTarget(mossifiable).isPresent(),
                    mossifiable + " is mossify-whitelisted but has no conversion target");
        }
    }

    @Test
    void shroomThumbConvertsDirtLikeBlocksToMycelium() {
        assertEquals(Optional.of("mycelium"), Herbalism.shroomThumbConversionTarget("dirt"));
        assertEquals(Optional.of("mycelium"), Herbalism.shroomThumbConversionTarget("grass_block"));
        assertEquals(Optional.of("mycelium"), Herbalism.shroomThumbConversionTarget("dirt_path"));
        assertTrue(Herbalism.shroomThumbConversionTarget("cobblestone").isEmpty());
    }

    @Test
    void greenTerraSeedIsWheatSeeds() {
        assertEquals("wheat_seeds", Herbalism.GREEN_TERRA_SEED);
    }
}
