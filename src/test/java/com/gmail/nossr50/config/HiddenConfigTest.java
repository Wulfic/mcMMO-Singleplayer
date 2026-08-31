package com.gmail.nossr50.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
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

    /**
     * The header comment must not promise behaviour the loader does not have.
     *
     * <p>This is a caveat-expiry guard, not a style check. Until 2026-08-31 the file opened with
     * <em>"You will need to reset any values in this config every time you update mcMMO"</em> --
     * addressed to a player who has no copy to reset. {@link HiddenConfig#load()} calls
     * {@code getResourceAsStream}, so the only {@code hidden.yml} that exists is the one inside the
     * jar: no disk copy, no {@code ConfigLoader} back-fill, and nothing an update could overwrite.
     * A false caveat outlives the defect it describes unless something reddens when it comes back.
     *
     * <p>What this does NOT catch, stated rather than implied: a differently-worded false claim.
     * It pins the exact sentence that shipped and the fact that refutes it, which is a revert
     * detector -- not a proof that every future header is honest.
     */
    @Test
    void headerDoesNotPromiseAnEditableDiskCopy() throws Exception {
        final String source;
        try (InputStream in = HiddenConfigTest.class.getResourceAsStream("/hidden.yml")) {
            assertNotNull(in, "hidden.yml is not on the test classpath");
            source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        final StringBuilder header = new StringBuilder();
        for (String line : source.split("\\R")) {
            if (line.startsWith("###")) {
                break;
            }
            header.append(line).append('\n');
        }

        // Guard the guard: an empty slice satisfies both assertions below without reading a thing.
        assertTrue(header.length() > 0, "no header comment block found before the ### terminator");

        final String text = header.toString().toLowerCase(Locale.ROOT);

        assertFalse(text.contains("reset any values"),
                "hidden.yml's header tells the reader to reset values on update, which cannot"
                        + " happen -- HiddenConfig#load reads the classpath copy inside the jar and"
                        + " no disk copy exists. Header was:\n" + header);

        assertTrue(text.contains("classpath"),
                "hidden.yml's header must state the fact that makes the reset caveat wrong -- that"
                        + " the file is read off the jar classpath, not from disk. Header was:\n"
                        + header);
    }
}
