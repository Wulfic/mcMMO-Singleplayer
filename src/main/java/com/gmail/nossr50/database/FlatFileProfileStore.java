package com.gmail.nossr50.database;

import com.gmail.nossr50.config.YamlConfiguration;
import com.gmail.nossr50.datatypes.player.PlayerProfile;
import com.gmail.nossr50.datatypes.player.UniqueDataType;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.util.LogUtils;
import com.gmail.nossr50.util.skills.SkillRenames;
import com.gmail.nossr50.util.skills.SkillTools;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
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
 * back-fill contract the configs use. A skill that has been <em>renamed</em> since the file was
 * written is additionally read back from its old key — see {@link #savedKeyFor}.
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
        boolean migrated = false;
        for (PrimarySkillType skill : SkillTools.NON_CHILD_SKILLS) {
            final String key = savedKeyFor(yc, skill, playerName);
            migrated |= !key.equals(skill.name());
            levels.put(skill, yc.getInt("skills." + key, startingLevel));
            xp.put(skill, (float) yc.getDouble("experience." + key, 0.0));
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

        final PlayerProfile profile = new PlayerProfile(playerName, uuid, levels, xp, cooldowns,
                tipsShown, uniqueData, lastLogin);
        if (migrated) {
            // Force the rewrite. PlayerProfile#save is a no-op on a clean profile, so without this
            // the legacy key survives until the player happens to earn XP — leaving the file in the
            // both-keys state indefinitely for anyone who logs in and does nothing. Marking it dirty
            // makes the migration settle on the very next save instead of "eventually".
            profile.markProfileDirty();
        }
        return profile;
    }

    /**
     * Resolve which on-disk key holds this skill's saved level/XP, transparently falling back to the
     * name the skill was persisted under before it was renamed.
     *
     * <p>A skill's {@link PrimarySkillType#name()} <em>is</em> its save key, so renaming a constant
     * orphans every profile written before the rename: {@code skills.AGILITY} is absent,
     * {@link com.gmail.nossr50.config.YamlConfiguration#getInt(String, int)} hands back the default,
     * and the player silently restarts the skill at the starting level with nothing logged. The
     * legacy key is therefore read <b>only when the current key is absent</b> — a profile carrying
     * both (one written by a mixed-version setup) prefers the current one, which is the authoritative
     * copy — and the write path always emits the current name, so the orphaned key disappears of its
     * own accord on the next save.
     *
     * @param yc         the loaded profile document
     * @param skill      the skill being read
     * @param playerName the owner, for the migration log line
     * @return the key suffix to read {@code skills.}/{@code experience.} under
     */
    private @NotNull String savedKeyFor(@NotNull YamlConfiguration yc,
            @NotNull PrimarySkillType skill, @NotNull String playerName) {
        return savedKeyFor(yc, skill.name(), SkillRenames.legacyEnumName(skill), playerName);
    }

    /**
     * The name-only core of {@link #savedKeyFor(YamlConfiguration, PrimarySkillType, String)}.
     *
     * <p>Split out so the migration contract can be tested against an <em>explicit</em> legacy name
     * rather than against whichever skill happens to be renamed today. {@link SkillRenames} is
     * currently empty — Agility's alias was retired when it became a child skill — so a test routed
     * through a real {@code PrimarySkillType} would now pass by doing nothing at all, and would keep
     * passing if this method were deleted outright.
     *
     * @param yc         the loaded profile document
     * @param current    the skill's present-day save key
     * @param legacy     the key it was persisted under before a rename, or {@code null} if never
     *                   renamed
     * @param playerName the owner, for the migration log line
     * @return the key suffix to read {@code skills.}/{@code experience.} under
     */
    @VisibleForTesting
    static @NotNull String savedKeyFor(@NotNull YamlConfiguration yc, @NotNull String current,
            @Nullable String legacy, @NotNull String playerName) {
        if (yc.contains("skills." + current)) {
            return current;
        }
        if (legacy != null && yc.contains("skills." + legacy)) {
            LOGGER.info("Migrating {}'s saved {} data from the legacy key '{}'.",
                    playerName, current, legacy);
            return legacy;
        }
        return current;
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
