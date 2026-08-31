package com.gmail.nossr50.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.util.sounds.SoundType;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

/**
 * Exercises {@link SoundConfig} against the real bundled {@code sounds.yml} on the test classpath,
 * with a temp data folder.
 */
class SoundConfigTest {

    /** The one {@code Sounds:} child that is a scalar tuning value rather than a per-sound section. */
    private static final String MASTER_VOLUME = "MasterVolume";

    @Test
    void writesDefaultToDiskWhenMissing(@TempDir Path dataFolder) {
        new SoundConfig(dataFolder);
        assertTrue(Files.exists(dataFolder.resolve("sounds.yml")));
    }

    @Test
    void readsMasterVolumeAndEnableFlags(@TempDir Path dataFolder) {
        final SoundConfig config = new SoundConfig(dataFolder);
        assertEquals(1.0f, config.getMasterVolume(), 0.0001f);
        assertTrue(config.getIsEnabled(SoundType.ANVIL));
    }

    @Test
    void readsPerSoundVolumeAndPitch(@TempDir Path dataFolder) {
        final SoundConfig config = new SoundConfig(dataFolder);
        // ANVIL: Volume 1.0, Pitch 0.3 in the bundled default.
        assertEquals(1.0f, config.getVolume(SoundType.ANVIL), 0.0001f);
        assertEquals(0.3f, config.getPitch(SoundType.ANVIL), 0.0001f);
    }

    @Test
    void customSoundIdDefaultsEmpty(@TempDir Path dataFolder) {
        final SoundConfig config = new SoundConfig(dataFolder);
        // CustomSoundId is '' in the default -> empty string.
        assertEquals("", config.getSound(SoundType.GLASS));
    }

    @Test
    void everySoundTypeHasASection() throws Exception {
        // ENUM -> YML. SoundConfig#validateKeys already walks SoundType.values(), and it LOOKS like
        // this check without being it: it reads `config.getDouble("Sounds." + type + ".Volume")`
        // with NO default, so a missing section yields 0, `0 < 0` is false, and validation PASSES.
        //
        // 🔑 Same mechanism as coreskills.yml's `getBoolean(path, true)` and as the config-id gate's
        // unlisted monsters, which resolved through getDouble and paid ZERO combat XP for the life
        // of the port: a missing key answered by a default is invisible to the walk that reads it.
        // A SoundType with no section is not an error anywhere -- it just plays at whatever the
        // getters' fallbacks happen to be, with no volume, pitch or enable switch a player can find.
        final Map<?, ?> sounds = loadBundledSounds();

        final Set<String> missing = new TreeSet<>();
        for (SoundType soundType : SoundType.values()) {
            if (!(sounds.get(soundType.toString()) instanceof Map<?, ?>)) {
                missing.add(soundType.toString());
            }
        }

        assertTrue(missing.isEmpty(),
                "sounds.yml has no section for: " + missing
                        + " -- those sounds are unconfigurable and validateKeys passes them anyway");
    }

    @Test
    void everySoundsSectionMapsToALiveSoundType() throws Exception {
        // YML -> ENUM, the converse. A section for a renamed or deleted SoundType is read by
        // nobody and looks exactly like a live one to anyone editing the file.
        //
        // ⚠️ MasterVolume is excluded BY NAME, not by shape. Skipping every non-Map child would
        // also skip a SoundType section that had been flattened to a scalar -- the malformed case
        // this is partly here to catch -- so the exclusion has to name the one legitimate scalar.
        final Map<?, ?> sounds = loadBundledSounds();

        final Set<String> live = new TreeSet<>();
        for (SoundType soundType : SoundType.values()) {
            live.add(soundType.toString());
        }

        final Set<String> orphans = new TreeSet<>();
        for (Object key : sounds.keySet()) {
            final String name = String.valueOf(key);
            if (!MASTER_VOLUME.equals(name) && !live.contains(name)) {
                orphans.add(name);
            }
        }

        assertTrue(orphans.isEmpty(),
                "sounds.yml configures sounds that no longer exist: " + orphans);
    }

    /**
     * The {@code Sounds:} root of the bundled default, which is what ships.
     *
     * <p>Returned as a wildcard map so the section can be read without an unchecked cast; callers
     * only ever ask for its key names and whether a value is a section.
     */
    private static Map<?, ?> loadBundledSounds() throws Exception {
        try (InputStream in = SoundConfigTest.class.getResourceAsStream("/sounds.yml")) {
            assertNotNull(in, "sounds.yml is not on the test classpath");
            final Map<String, Object> root = new Yaml().load(in);
            final Object sounds = root.get("Sounds");
            assertTrue(sounds instanceof Map, "sounds.yml has no Sounds section");
            return (Map<?, ?>) sounds;
        }
    }
}
