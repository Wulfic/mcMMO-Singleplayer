package com.gmail.nossr50.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Regression guard for the {@code fabric-loader-junit} registry harness (see {@link McTestRegistries}
 * and {@code build.gradle}): confirms the vanilla item registry populates and resolves real id paths
 * inside the test JVM. If this fails, the MC-typed {@code ItemUtils}/{@code BlockUtils} wrapper tests
 * can't run — the launcher/access-widener setup regressed.
 */
class McRegistryBootstrapProbeTest {

    @BeforeAll
    static void bootstrap() {
        McTestRegistries.bootstrap();
    }

    @Test
    void itemRegistryResolvesVanillaIdPaths() {
        assertEquals("diamond_axe", Registries.ITEM.getId(Items.DIAMOND_AXE).getPath());
        assertEquals("netherite_pickaxe", Registries.ITEM.getId(Items.NETHERITE_PICKAXE).getPath());
        assertEquals("air", Registries.ITEM.getId(Items.AIR).getPath());
    }
}
