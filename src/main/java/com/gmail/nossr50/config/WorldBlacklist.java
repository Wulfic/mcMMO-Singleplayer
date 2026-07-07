package com.gmail.nossr50.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Blacklist certain features in certain worlds, backed by a plain {@code world_blacklist.txt} (one
 * world name per line) in the mod data folder.
 *
 * <p>Port notes: the dataFolder is injected as a {@link Path} (replacing {@code mcMMO#getDataFolder}),
 * and the lookup is keyed by <b>world name string</b> ({@link #isWorldBlacklisted(String)}) rather
 * than a Bukkit {@code World} — the Fabric world type wraps into {@code platform/} in Phase 10, and
 * the legacy body already compared against {@code world.getName()} anyway.
 */
public class WorldBlacklist {

    private static final Logger LOGGER = LoggerFactory.getLogger("mcMMO/Config");

    private static final String BLACKLIST_FILE_NAME = "world_blacklist.txt";

    private static final List<String> blacklist = new ArrayList<>();

    private final Path dataFolder;

    public WorldBlacklist(@NotNull Path dataFolder) {
        this.dataFolder = dataFolder;
        blacklist.clear();
        init();
    }

    /**
     * @param worldName the name of the world to check (case-insensitive)
     * @return true if the named world is on the blacklist
     */
    public static boolean isWorldBlacklisted(String worldName) {
        for (String s : blacklist) {
            if (s.equalsIgnoreCase(worldName)) {
                return true;
            }
        }

        return false;
    }

    public void init() {
        final Path blackListFile = dataFolder.resolve(BLACKLIST_FILE_NAME);

        try {
            //Make the blacklist file if it doesn't exist
            if (!Files.exists(blackListFile)) {
                Files.createDirectories(dataFolder);
                Files.createFile(blackListFile);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to create world blacklist file: {}", blackListFile, e);
        }

        //Load up the blacklist
        loadBlacklist(blackListFile);
    }

    private void loadBlacklist(@NotNull Path blackListFile) {
        try {
            for (String currentLine : Files.readAllLines(blackListFile, StandardCharsets.UTF_8)) {
                if (currentLine.isEmpty()) {
                    continue;
                }

                if (!blacklist.contains(currentLine)) {
                    blacklist.add(currentLine);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to read world blacklist file: {}", blackListFile, e);
        }

        if (!blacklist.isEmpty()) {
            LOGGER.info("{} entries in mcMMO World Blacklist", blacklist.size());
        }
    }
}
