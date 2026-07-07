package com.gmail.nossr50.database;

import com.gmail.nossr50.config.YamlConfiguration;
import com.gmail.nossr50.datatypes.player.PlayerProfile;
import com.gmail.nossr50.datatypes.player.UniqueDataType;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.util.LogUtils;
import com.gmail.nossr50.util.skills.SkillTools;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-world flatfile {@link ProfileStore}: one {@code <uuid>.yml} per player under an injected
 * directory (in game, {@code <worldRoot>/mcmmo/players/}). Replaces the legacy
 * {@code FlatFileDatabaseManager}'s single tab-delimited {@code mcmmo.users} file with a
 * self-describing per-player YAML document, reusing the ported {@link YamlConfiguration}.
 *
 * <p>File layout:
 * <pre>
 * uuid: 069a79f4-...
 * name: Steve
 * lastLogin: 1720310400000
 * scoreboardTipsShown: 0
 * skills:   { MINING: 5, WOODCUTTING: 3, ... }
 * experience: { MINING: 123.0, ... }
 * cooldowns: { BERSERK: 0, ... }
 * data:     { CHIMAERA_WING_DATS: 0 }
 * </pre>
 *
 * <p>Levels/xp/cooldowns/unique-data are written per enum constant; on load, any constant absent
 * from the file (e.g. a skill added since the file was written) falls back to its default
 * (starting level for skills, 0 otherwise), so old saves stay forward-compatible — the same
 * back-fill contract the configs use.
 */
public final class FlatFileProfileStore implements ProfileStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("mcMMO/ProfileStore");

    private final @NotNull Path playersDir;

    /**
     * @param playersDir the directory that holds the {@code <uuid>.yml} files. Created on demand.
     */
    public FlatFileProfileStore(@NotNull Path playersDir) {
        this.playersDir = playersDir;
        try {
            Files.createDirectories(playersDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create mcMMO player-data directory: " + playersDir, e);
        }
    }

    private @NotNull Path fileFor(@NotNull UUID uuid) {
        return playersDir.resolve(uuid + ".yml");
    }

    @Override
    public boolean hasProfile(@NotNull UUID uuid) {
        return Files.isRegularFile(fileFor(uuid));
    }

    @Override
    public @NotNull PlayerProfile loadProfile(@NotNull UUID uuid, @NotNull String playerName,
            int startingLevel) {
        final Path file = fileFor(uuid);
        if (!Files.exists(file)) {
            // No stored data: hand back a fresh, loaded profile at the configured starting level.
            return newProfile(uuid, playerName, startingLevel);
        }

        final YamlConfiguration yc;
        try {
            yc = YamlConfiguration.loadConfiguration(file);
        } catch (IOException e) {
            LOGGER.error("Failed to read mcMMO profile {}; starting a fresh profile for {}.",
                    file, playerName, e);
            return newProfile(uuid, playerName, startingLevel);
        }

        final Map<PrimarySkillType, Integer> levels = new EnumMap<>(PrimarySkillType.class);
        final Map<PrimarySkillType, Float> xp = new EnumMap<>(PrimarySkillType.class);
        for (PrimarySkillType skill : SkillTools.NON_CHILD_SKILLS) {
            levels.put(skill, yc.getInt("skills." + skill.name(), startingLevel));
            xp.put(skill, (float) yc.getDouble("experience." + skill.name(), 0.0));
        }

        final Map<SuperAbilityType, Integer> cooldowns = new EnumMap<>(SuperAbilityType.class);
        for (SuperAbilityType ability : SuperAbilityType.values()) {
            cooldowns.put(ability, yc.getInt("cooldowns." + ability.name(), 0));
        }

        final Map<UniqueDataType, Integer> uniqueData = new EnumMap<>(UniqueDataType.class);
        for (UniqueDataType type : UniqueDataType.values()) {
            uniqueData.put(type, yc.getInt("data." + type.name(), 0));
        }

        final int tipsShown = yc.getInt("scoreboardTipsShown", 0);
        final Long lastLogin = yc.contains("lastLogin") ? yc.getLong("lastLogin") : null;

        return new PlayerProfile(playerName, uuid, levels, xp, cooldowns, tipsShown, uniqueData,
                lastLogin);
    }

    private @NotNull PlayerProfile newProfile(@NotNull UUID uuid, @NotNull String playerName,
            int startingLevel) {
        final Map<PrimarySkillType, Integer> levels = new EnumMap<>(PrimarySkillType.class);
        final Map<PrimarySkillType, Float> xp = new EnumMap<>(PrimarySkillType.class);
        for (PrimarySkillType skill : SkillTools.NON_CHILD_SKILLS) {
            levels.put(skill, startingLevel);
            xp.put(skill, 0F);
        }
        final Map<SuperAbilityType, Integer> cooldowns = new EnumMap<>(SuperAbilityType.class);
        for (SuperAbilityType ability : SuperAbilityType.values()) {
            cooldowns.put(ability, 0);
        }
        final Map<UniqueDataType, Integer> uniqueData = new EnumMap<>(UniqueDataType.class);
        for (UniqueDataType type : UniqueDataType.values()) {
            uniqueData.put(type, 0);
        }
        return new PlayerProfile(playerName, uuid, levels, xp, cooldowns, 0, uniqueData,
                System.currentTimeMillis());
    }

    @Override
    public void saveProfile(@NotNull PlayerProfile profile) {
        final UUID uuid = profile.getUniqueId();
        if (uuid == null) {
            LOGGER.warn("Refusing to save mcMMO profile for {} — no UUID.", profile.getPlayerName());
            return;
        }

        final YamlConfiguration yc = YamlConfiguration.empty();
        yc.set("uuid", uuid.toString());
        yc.set("name", profile.getPlayerName());
        yc.set("lastLogin", profile.getLastLogin());
        yc.set("scoreboardTipsShown", profile.getScoreboardTipsShown());

        for (PrimarySkillType skill : SkillTools.NON_CHILD_SKILLS) {
            yc.set("skills." + skill.name(), profile.getSkillLevel(skill));
            yc.set("experience." + skill.name(), (double) profile.getSkillXpLevelRaw(skill));
        }
        for (SuperAbilityType ability : SuperAbilityType.values()) {
            yc.set("cooldowns." + ability.name(), (int) profile.getAbilityDATS(ability));
        }
        for (UniqueDataType type : UniqueDataType.values()) {
            yc.set("data." + type.name(), (int) profile.getUniqueData(type));
        }

        try {
            yc.save(fileFor(uuid));
            LogUtils.debug("Saved mcMMO profile for " + profile.getPlayerName());
        } catch (IOException e) {
            LOGGER.error("Failed to save mcMMO profile for {} ({})",
                    profile.getPlayerName(), uuid, e);
            throw new UncheckedIOException(e);
        }
    }
}
