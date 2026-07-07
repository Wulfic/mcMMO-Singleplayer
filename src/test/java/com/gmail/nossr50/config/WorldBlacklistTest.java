package com.gmail.nossr50.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises {@link WorldBlacklist} name-keyed lookups against a temp data folder.
 */
class WorldBlacklistTest {

    @Test
    void createsEmptyBlacklistFileWhenMissing(@TempDir Path dataFolder) throws Exception {
        new WorldBlacklist(dataFolder);
        assertTrue(Files.exists(dataFolder.resolve("world_blacklist.txt")));
        assertFalse(WorldBlacklist.isWorldBlacklisted("overworld"));
    }

    @Test
    void matchesListedWorldsCaseInsensitively(@TempDir Path dataFolder) throws Exception {
        Files.writeString(dataFolder.resolve("world_blacklist.txt"),
                "the_nether\nMyWorld\n", StandardCharsets.UTF_8);
        new WorldBlacklist(dataFolder);

        assertTrue(WorldBlacklist.isWorldBlacklisted("the_nether"));
        assertTrue(WorldBlacklist.isWorldBlacklisted("MYWORLD"));
        assertFalse(WorldBlacklist.isWorldBlacklisted("overworld"));
    }
}
