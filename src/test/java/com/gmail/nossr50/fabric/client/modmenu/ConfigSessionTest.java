package com.gmail.nossr50.fabric.client.modmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.config.YamlConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises the on-disk read → edit → save flow that backs the ModMenu config screen, proving that
 * edits round-trip, that a never-yet-written config falls back to the bundled defaults, and — the
 * important safety property — that saving one edited key rewrites the <em>whole</em> document rather
 * than a stub, so unrelated settings survive.
 */
class ConfigSessionTest {

    private static final String GLOBAL_XP = "Experience_Formula.Multiplier.Global";
    private static final String MASTER_VOLUME = "Sounds.MasterVolume";
    private static final String SAVE_INTERVAL = "General.Save_Interval";

    private static ConfigSetting byPath(String path) {
        return McMMOSettings.all().stream()
                .filter(s -> s.path().equals(path))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no catalogue entry for " + path));
    }

    @Test
    void readsBundledDefaultsWhenNoFileOnDisk(@TempDir Path dir) {
        final ConfigSession session = new ConfigSession(dir);
        // No config.yml / experience.yml written yet: values come from the jar's bundled defaults.
        assertEquals(1.0, session.readDouble(byPath(GLOBAL_XP)), 1.0e-9);
        assertEquals(10, session.readInt(byPath(SAVE_INTERVAL)));
        assertFalse(Files.exists(dir.resolve(McMMOSettings.EXPERIENCE_YML)),
                "reading must not create files");
    }

    @Test
    void editsRoundTripAcrossSessions(@TempDir Path dir) throws IOException {
        final ConfigSession first = new ConfigSession(dir);
        first.write(byPath(GLOBAL_XP), 2.5);
        assertTrue(first.hasPendingChanges());
        assertEquals(1, first.saveAll(), "exactly one file (experience.yml) should be written");

        // A fresh session must read the persisted value straight off disk.
        final ConfigSession second = new ConfigSession(dir);
        assertEquals(2.5, second.readDouble(byPath(GLOBAL_XP)), 1.0e-9);
    }

    @Test
    void unchangedWriteIsANoOp(@TempDir Path dir) throws IOException {
        final ConfigSession session = new ConfigSession(dir);
        // Write the same value the bundled default already holds.
        session.write(byPath(GLOBAL_XP), 1.0);
        assertFalse(session.hasPendingChanges(), "writing the current value should not mark dirty");
        assertEquals(0, session.saveAll());
        assertFalse(Files.exists(dir.resolve(McMMOSettings.EXPERIENCE_YML)),
                "a no-op save must not write a file");
    }

    @Test
    void savingOneKeyPreservesTheRestOfTheFile(@TempDir Path dir) throws IOException {
        final ConfigSession session = new ConfigSession(dir);
        session.write(byPath(MASTER_VOLUME), 0.5);
        assertEquals(1, session.saveAll());

        // Re-read the raw file: the edited key changed, and an unrelated key survived intact.
        final YamlConfiguration written =
                YamlConfiguration.loadConfiguration(dir.resolve(McMMOSettings.CONFIG_YML));
        assertEquals(0.5, written.getDouble(MASTER_VOLUME), 1.0e-9);
        assertEquals(10, written.getInt(SAVE_INTERVAL),
                "an unrelated key must survive a single-key save");
        assertTrue(written.contains("Skills.Mining.Level_Cap"),
                "deep unrelated sections must survive a single-key save");
    }
}
