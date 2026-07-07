package com.gmail.nossr50.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Exercises {@link HiddenConfig}, which reads the bundled read-only {@code hidden.yml} straight
 * from the classpath (no disk copy).
 */
class HiddenConfigTest {

    @Test
    void readsBundledHiddenOptions() {
        final HiddenConfig config = new HiddenConfig("hidden.yml");
        assertEquals(1, config.getConversionRate());
        assertTrue(config.useEnchantmentBuffs());
    }
}
