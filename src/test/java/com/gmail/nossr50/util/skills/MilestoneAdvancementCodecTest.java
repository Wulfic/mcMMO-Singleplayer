package com.gmail.nossr50.util.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.util.McTestRegistries;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.advancements.Advancement;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.RegistryOps;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * <b>GitHub #20.</b> Every bundled milestone advancement must decode through Minecraft's OWN
 * {@link Advancement#CODEC} on the version this band compiles against.
 *
 * <p>v1.5.1 shipped a milestone root with no tab background. Minecraft 26.3 validates advancements
 * as they load into the {@code minecraft:advancement} registry, refused that one file with
 * <i>"Visible advancement roots must have background"</i>, and failed the whole registry load — so
 * no 26.3 player could create or join a world. Every test in the suite was green, because the
 * existing guard ({@link MilestoneAdvancementResourcesTest}) parses these files as plain YAML and
 * checks the rules <em>we</em> knew about.
 *
 * <p>🔑 <b>This test does not restate Minecraft's rules — it runs them.</b> The same codec, with its
 * validation, is what the registry loader calls. So when a future Minecraft adds a rule of its own,
 * this goes red on the band that adopts it, at build time, rather than in a player's world.
 *
 * <p>⚠️ It reads the files off the <b>test runtime classpath</b> (the processed resources), never
 * {@code src/} by {@code Path.of}. A path Gradle does not know about is an undeclared input, and an
 * undeclared input gets a cached pass after the files change.
 */
class MilestoneAdvancementCodecTest {

    private static final String ROOT_RESOURCE = "/data/mcmmo/advancement/milestone/root.json";

    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void bootstrapMinecraft() {
        McTestRegistries.bootstrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, VanillaRegistries.createLookup());
    }

    @Test
    void everyBundledMilestoneAdvancementDecodesThroughMinecraftsOwnCodec() {
        final Path base = milestoneDirectoryOnClasspath();
        final List<String> failures = new ArrayList<>();
        int decoded = 0;
        try (Stream<Path> files = Files.walk(base)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                decoded++;
                final String id = base.relativize(file).toString().replace('\\', '/');
                decode(Files.readString(file, StandardCharsets.UTF_8)).error()
                        .ifPresent(error -> failures.add(id + ": " + error.message()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read milestone advancements under " + base, e);
        }

        // A directory that resolved to the wrong place decodes zero files and fails nothing.
        assertTrue(decoded > 300, "expected the whole milestone datapack, decoded only " + decoded
                + " file(s) under " + base);
        assertTrue(failures.isEmpty(),
                () -> failures.size() + " bundled advancement(s) are refused by Minecraft's own codec. "
                        + "The registry load fails on any one of them and no world can be created "
                        + "or joined (GitHub #20). Fix scripts/gen-milestone-advancements.sh and "
                        + "re-run it:\n  " + String.join("\n  ", failures));
    }

    /**
     * The root decodes <em>with</em> its background — so the codec really read the display, rather
     * than the file passing because some part of it was never looked at.
     */
    @Test
    void theRootDecodesWithItsTabBackground() throws IOException {
        final Advancement root = decode(readClasspath(ROOT_RESOURCE)).getOrThrow();
        assertTrue(root.parent().isEmpty(), "the milestone root must have no parent");
        assertTrue(root.display().orElseThrow().getBackground().isPresent(),
                "the milestone root decoded without a background; Minecraft 26.3 refuses that");
    }

    /**
     * Converse control: a decode error must actually surface as an error here. Without it, a harness
     * that swallowed every failure (or a lenient partial decode) would pass the test above forever.
     *
     * <p>An unknown icon item is refused on every Minecraft version in scope, so the control holds on
     * every band — unlike the background rule itself, which only 26.3 onwards enforces.
     */
    @Test
    void aBrokenAdvancementIsRefusedByTheSameDecode() throws IOException {
        final String broken = readClasspath(ROOT_RESOURCE)
                .replace("\"minecraft:nether_star\"", "\"minecraft:not_a_real_item_mcmmo\"");
        assertTrue(broken.contains("not_a_real_item_mcmmo"),
                "the control did not apply: the root's icon is no longer nether_star");
        final DataResult<Advancement> result = decode(broken);
        assertTrue(result.error().isPresent(),
                "an advancement with an unknown icon item decoded cleanly, so this harness cannot "
                        + "see a decode failure and the guard above proves nothing");
    }

    private static DataResult<Advancement> decode(String json) {
        return Advancement.CODEC.parse(ops, JsonParser.parseString(json));
    }

    private static String readClasspath(String resource) throws IOException {
        final URL url = MilestoneAdvancementCodecTest.class.getResource(resource);
        assertTrue(url != null, resource + " is not on the test classpath");
        try (var in = url.openStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Path milestoneDirectoryOnClasspath() {
        final URL root = MilestoneAdvancementCodecTest.class.getResource(ROOT_RESOURCE);
        assertTrue(root != null, ROOT_RESOURCE + " is not on the test classpath");
        assertEquals("file", root.getProtocol(),
                "expected the processed resources as a directory, got " + root);
        try {
            return Path.of(root.toURI()).getParent();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("unusable resource URL " + root, e);
        }
    }
}
