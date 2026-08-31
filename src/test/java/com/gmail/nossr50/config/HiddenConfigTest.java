package com.gmail.nossr50.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Exercises {@link HiddenConfig}, which reads the bundled read-only {@code hidden.yml} straight
 * from the classpath (no disk copy).
 */
class HiddenConfigTest {

    /**
     * The {@code Options} keys {@link HiddenConfig#load()} actually reads.
     *
     * <p>⚠️ Hand-kept, and deliberately so: it fails CLOSED. A knob added to {@code hidden.yml}
     * without a consumer reddens {@link #everyHiddenOptionIsRead}, which is the only thing that
     * would have caught {@code Chunklets} -- a switch that lived in the file and its own comment
     * and was referenced by nothing in the repository, `load()` included. Adding a row here without
     * wiring it up is the mistake this list exists to make loud.
     */
    private static final Set<String> READ_OPTIONS = Set.of("EnchantmentBuffs");

    @Test
    void readsBundledHiddenOptions() {
        final HiddenConfig config = new HiddenConfig("hidden.yml");
        assertTrue(config.useEnchantmentBuffs());
    }

    @Test
    void everyHiddenOptionIsRead() throws Exception {
        // hidden.yml is bundled-only -- there is no disk copy and no ConfigLoader back-fill, so a
        // dead row here is pure dead weight that reads as a working switch to anyone opening the
        // jar. Two of the three shipped knobs were exactly that until 2026-08-31: `Chunklets` was
        // never read by anything, and `ConversionRate` was read into a field whose accessor had no
        // caller anywhere in src/main -- pinned, until this rewrite, by an assertion in THIS class
        // that proved the plumbing worked while nothing consumed the value.
        final Map<String, Object> root;
        try (InputStream in = HiddenConfigTest.class.getResourceAsStream("/hidden.yml")) {
            assertNotNull(in, "hidden.yml is not on the test classpath");
            root = new Yaml().load(in);
        }

        final Object options = root.get("Options");
        assertTrue(options instanceof Map, "hidden.yml has no Options section");

        final Set<String> shipped = new TreeSet<>();
        for (Object key : ((Map<?, ?>) options).keySet()) {
            shipped.add(String.valueOf(key));
        }

        assertEquals(new TreeSet<>(READ_OPTIONS), shipped,
                "hidden.yml's Options and the knobs HiddenConfig#load reads have diverged --"
                        + " a shipped-but-unread key is a switch that does nothing, and an unshipped"
                        + " key means load() is relying on its hardcoded default");
    }
}
