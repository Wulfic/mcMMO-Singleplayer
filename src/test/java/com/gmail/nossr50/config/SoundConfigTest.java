package com.gmail.nossr50.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.util.sounds.SoundType;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises {@link SoundConfig} against the real bundled {@code sounds.yml} on the test classpath,
 * with a temp data folder.
 */
class SoundConfigTest {

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
}
