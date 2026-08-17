package com.gmail.nossr50.datatypes.player;

import com.gmail.nossr50.datatypes.experience.FormulaType;
import com.gmail.nossr50.datatypes.experience.SkillXpGain;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.database.ProfileStore;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.util.LogUtils;
import com.gmail.nossr50.util.skills.SkillTools;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.DelayQueue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlayerProfile {
    private final String playerName;
    private @Nullable UUID uuid;
    private boolean loaded;
    private volatile boolean changed;

    /* HUDs */
    private int scoreboardTipsShown;
    private int saveAttempts = 0;

    private @Nullable Long lastLogin;

    /* Skill Data */
    private final Map<PrimarySkillType, Integer> skills = new EnumMap<>(
            PrimarySkillType.class);   // Skill & Level
    private final Map<PrimarySkillType, Float> skillsXp = new EnumMap<>(
            PrimarySkillType.class);     // Skill & XP
    private final Map<SuperAbilityType, Integer> abilityDATS = new EnumMap<>(
            SuperAbilityType.class); // Ability & Cooldown
    private final Map<UniqueDataType, Integer> uniquePlayerData = new EnumMap<>(
            UniqueDataType.class); //Misc data that doesn't fit into other categories (chimaera wing, etc..)

    // Store previous XP gains for diminished returns
    private final DelayQueue<SkillXpGain> gainedSkillsXp = new DelayQueue<>();
    private final Map<PrimarySkillType, Float> rollingSkillsXp = new EnumMap<>(
            PrimarySkillType.class);

    /**
     * Hunter's per-mob-type kill counters, keyed by the mob's <em>raw</em> registry id string
     * ({@code minecraft:zombie}) — see {@code plans/new-skills/hunter.md} D-HU2.
     *
     * <p><b>This is the only open-ended key space in the profile</b>, and that is the whole reason it
     * needed a ruling. Everything else here is an {@link EnumMap} over a closed enum, so
     * {@code FlatFileProfileStore} can write a fixed key set derived from {@code .values()}. A mob id
     * is neither closed nor ours: a mod adds entity types, and Mojang adds them too.
     *
     * <p>Three properties are load-bearing rather than tidy:
     * <ul>
     *   <li><b>The key stays a raw {@code String}, never resolved to a registry type here.</b> Resolving
     *       at load is the {@code isIn(TagKey)}-throws trap all over again, and a mob from a mod the
     *       player has since uninstalled must not take the whole profile down with it. Whoever needs a
     *       live entity type resolves it at use, where a miss costs one lookup.</li>
     *   <li><b>{@link #MAX_TRACKED_MOB_TYPES} bounds the map.</b> Vanilla has fewer than a hundred mobs,
     *       so the cap only ever binds on a heavily modded world or a corrupted file — which is exactly
     *       when an unbounded, disk-driven map is a memory bug rather than a feature.</li>
     *   <li><b>A {@link TreeMap}, so the persisted section is ordered.</b> A save file that reorders
     *       itself on every write is unreviewable, and this section is the one part of the profile a
     *       player might plausibly read by hand.</li>
     * </ul>
     */
    private final Map<String, Integer> mobKills = new TreeMap<>();

    /**
     * The most distinct mob types one profile will track kills for.
     *
     * <p>Generalises {@code [[placed-block-persistence]]}'s lesson — never let a number read off disk
     * size an allocation — from one number to a whole collection. 4,096 is roughly forty times the
     * vanilla mob roster, so no honest world reaches it.
     */
    public static final int MAX_TRACKED_MOB_TYPES = 4096;

    /** One WARN per profile when the cap refuses a new mob type; a per-kill log would be a flood. */
    private boolean mobKillCapWarned;

    @Deprecated
    public PlayerProfile(String playerName) {
        this(playerName, null, 0);
    }

    @Deprecated
    public PlayerProfile(String playerName, UUID uuid) {
        this(playerName, uuid, 0);
    }

    @Deprecated
    public PlayerProfile(String playerName, int startingLevel) {
        this(playerName, null, startingLevel);
    }

    public PlayerProfile(String playerName, @Nullable UUID uuid, int startingLevel) {
        this.uuid = uuid;
        this.playerName = playerName;

        scoreboardTipsShown = 0;

        for (SuperAbilityType superAbilityType : SuperAbilityType.values()) {
            abilityDATS.put(superAbilityType, 0);
        }

        for (PrimarySkillType primarySkillType : SkillTools.NON_CHILD_SKILLS) {
            skills.put(primarySkillType, startingLevel);
            skillsXp.put(primarySkillType, 0F);
        }

        // Misc per-player data (Chimaera Wing's cooldown, the pet combat stance, ...).
        //
        // ⚠️ Seeded from values(), NOT from a hand-written list. This used to name CHIMAERA_WING_DATS
        // alone, which made adding any second constant an NPE on the very next save: saveProfile
        // loops over values() calling getUniqueData, and that unboxed a null Map#get straight to
        // long. Zero is the same default FlatFileProfileStore hands back for an absent key, so a
        // freshly built profile and a profile loaded from a file written before the constant existed
        // now agree by construction rather than by coincidence.
        for (UniqueDataType uniqueDataType : UniqueDataType.values()) {
            uniquePlayerData.put(uniqueDataType, 0);
        }
        lastLogin = System.currentTimeMillis();
    }

    @Deprecated
    public PlayerProfile(@NotNull String playerName, boolean isLoaded, int startingLvl) {
        this(playerName, startingLvl);
        this.loaded = isLoaded;
    }

    public PlayerProfile(@NotNull String playerName, @Nullable UUID uuid, boolean isLoaded, int startingLvl) {
        this(playerName, uuid, startingLvl);
        this.loaded = isLoaded;
    }

    public PlayerProfile(@NotNull String playerName, @Nullable UUID uuid,
            Map<PrimarySkillType, Integer> levelData, Map<PrimarySkillType, Float> xpData,
            Map<SuperAbilityType, Integer> cooldownData, int scoreboardTipsShown,
            Map<UniqueDataType, Integer> uniqueProfileData, @Nullable Long lastLogin) {
        this(playerName, uuid, levelData, xpData, cooldownData, scoreboardTipsShown,
                uniqueProfileData, lastLogin, Map.of());
    }

    /**
     * The full loaded-from-disk constructor, including Hunter's per-mob kill counters.
     *
     * @param mobKillData kill counts by raw mob registry id; the caller has already validated and
     *                    bounded them (see {@code FlatFileProfileStore})
     */
    public PlayerProfile(@NotNull String playerName, @Nullable UUID uuid,
            Map<PrimarySkillType, Integer> levelData, Map<PrimarySkillType, Float> xpData,
            Map<SuperAbilityType, Integer> cooldownData, int scoreboardTipsShown,
            Map<UniqueDataType, Integer> uniqueProfileData, @Nullable Long lastLogin,
            @NotNull Map<String, Integer> mobKillData) {
        this.playerName = playerName;
        this.uuid = uuid;
        this.scoreboardTipsShown = scoreboardTipsShown;

        skills.putAll(levelData);
        skillsXp.putAll(xpData);
        abilityDATS.putAll(cooldownData);
        uniquePlayerData.putAll(uniqueProfileData);
        mobKills.putAll(mobKillData);

        loaded = true;

        if (lastLogin != null) {
            this.lastLogin = lastLogin;
        }
    }

    // Phase 5: profile persistence. The legacy save path scheduled a PlayerProfileSaveTask on the
    // (Folia) scheduler and wrote to the SQL/flatfile DatabaseManager. For the singleplayer port
    // the SQL backend and the async scheduler are cut: save() writes synchronously to the bound
    // per-world FlatFileProfileStore (integrated-server saves are already off the render thread via
    // the lifecycle/quit hooks that drive them). The async* helpers collapse onto the sync path.

    public void scheduleAsyncSave() {
        save(true);
    }

    public void scheduleAsyncSaveDelay() {
        save(true);
    }

    @Deprecated
    public void scheduleSyncSaveDelay() {
        save(true);
    }

    public void save(boolean useSync) {
        if (!changed || !loaded) {
            saveAttempts = 0;
            return;
        }

        final ProfileStore store = McMMOMod.getProfileStore();
        if (store == null) {
            // No store bound (outside a world session / unit tests without persistence): the
            // in-memory profile stays authoritative. Keep `changed` set so the write is retried
            // once a store is bound.
            LogUtils.debug("PlayerProfile.save skipped — no profile store bound: " + playerName);
            return;
        }

        try {
            store.saveProfile(this);
            changed = false;
            saveAttempts = 0;
        } catch (Exception e) {
            saveAttempts++;
            McMMOMod.LOGGER.warn("Failed to save mcMMO profile for {} (attempt {}); will retry.",
                    playerName, saveAttempts, e);
            // Leave `changed` set so the next save operation retries.
        }
    }

    /**
     * This user's last-login timestamp (epoch millis), or {@code -1} if unknown. Serialized by
     * {@link com.gmail.nossr50.database.FlatFileProfileStore}.
     *
     * @return the last login
     */
    public @NotNull Long getLastLogin() {
        return Objects.requireNonNullElse(lastLogin, -1L);
    }

    public void updateLastLogin() {
        this.lastLogin = System.currentTimeMillis();
    }

    public String getPlayerName() {
        return playerName;
    }

    public UUID getUniqueId() {
        return uuid;
    }

    public void setUniqueId(UUID uuid) {
        markProfileDirty();

        this.uuid = uuid;
    }

    public boolean isLoaded() {
        return loaded;
    }

    /**
     * Marks the profile as "dirty" which flags a profile to be saved in the next save operation
     */
    public void markProfileDirty() {
        changed = true;
    }

    public int getScoreboardTipsShown() {
        return scoreboardTipsShown;
    }

    public void setScoreboardTipsShown(int scoreboardTipsShown) {
        markProfileDirty();

        this.scoreboardTipsShown = scoreboardTipsShown;
    }

    public void increaseTipsShown() {
        setScoreboardTipsShown(getScoreboardTipsShown() + 1);
    }

    /*
     * Hunter — per-mob kill counters (D-HU2)
     */

    /**
     * How many of {@code mobId} this player has killed, {@code 0} for a mob they have never killed.
     *
     * @param mobId the mob's raw registry id, e.g. {@code minecraft:zombie}
     */
    public int getMobKills(@NotNull String mobId) {
        return mobKills.getOrDefault(mobId, 0);
    }

    /**
     * Count one kill of {@code mobId} and hand back the new total.
     *
     * <p>Marks the profile dirty on every increment. That is cheap — the save is already debounced by
     * {@code General.Save_Interval} — and the alternative is a player losing a session's worth of
     * mastery progress to a crash.
     *
     * @param mobId the mob's raw registry id
     * @return the count after this kill, or the unchanged count if the type cap refused a new entry
     */
    public int incrementMobKills(@NotNull String mobId) {
        final Integer existing = mobKills.get(mobId);
        if (existing == null && mobKills.size() >= MAX_TRACKED_MOB_TYPES) {
            // Refuse rather than grow without bound. Logged because a counter that silently stops
            // moving is indistinguishable from the whole feature being broken.
            if (!mobKillCapWarned) {
                mobKillCapWarned = true;
                McMMOMod.LOGGER.warn(
                        "{} already tracks the maximum of {} distinct mob types; kills of '{}' and any "
                                + "further new mob type will not be counted toward Hunter mastery.",
                        playerName, MAX_TRACKED_MOB_TYPES, mobId);
            }
            return 0;
        }

        markProfileDirty();
        final int updated = (existing == null ? 0 : existing) + 1;
        mobKills.put(mobId, updated);
        return updated;
    }

    /**
     * Every tracked mob type and its kill count, as an unmodifiable snapshot.
     *
     * <p>Used by the save path and by {@code /mcstats hunter}. Unmodifiable on purpose: a counter that
     * anything outside this class can rewrite is a counter that can move without dirtying the profile.
     */
    public @NotNull Map<String, Integer> getAllMobKills() {
        return Collections.unmodifiableMap(mobKills);
    }

    /*
     * Cooldowns
     */

    public int getChimaerWingDATS() {
        return uniquePlayerData.get(UniqueDataType.CHIMAERA_WING_DATS);
    }

    protected void setChimaeraWingDATS(int DATS) {
        markProfileDirty();
        uniquePlayerData.put(UniqueDataType.CHIMAERA_WING_DATS, DATS);
    }

    public void setUniqueData(UniqueDataType uniqueDataType, int newData) {
        markProfileDirty();
        uniquePlayerData.put(uniqueDataType, newData);
    }

    /**
     * This player's value for a miscellaneous data key, or {@code 0} when the profile carries none.
     *
     * <p>⚠️ {@code getOrDefault}, not {@code get}. The return type is a primitive, so a {@code get}
     * miss unboxes null and throws — and every caller of this method reaches it through a
     * {@code UniqueDataType.values()} loop ({@code FlatFileProfileStore#saveProfile}), so a single
     * unseeded constant took out saving for the whole profile rather than degrading one field.
     * Zero is the same value the store's read side substitutes for an absent key, which is what lets
     * a new constant be added without a migration.
     */
    public long getUniqueData(UniqueDataType uniqueDataType) {
        return uniquePlayerData.getOrDefault(uniqueDataType, 0);
    }

    /**
     * Get the current deactivation timestamp of an ability.
     *
     * @param ability The {@link SuperAbilityType} to get the DATS for
     * @return the deactivation timestamp for the ability
     */
    public long getAbilityDATS(SuperAbilityType ability) {
        return abilityDATS.get(ability);
    }

    /**
     * Set the current deactivation timestamp of an ability.
     *
     * @param ability The {@link SuperAbilityType} to set the DATS for
     * @param DATS the DATS of the ability
     */
    protected void setAbilityDATS(SuperAbilityType ability, long DATS) {
        markProfileDirty();

        abilityDATS.put(ability, (int) (DATS * .001D));
    }

    /**
     * Reset all ability cooldowns.
     */
    protected void resetCooldowns() {
        markProfileDirty();

        abilityDATS.replaceAll((a, v) -> 0);
    }

    /*
     * Xp Functions
     */

    public int getSkillLevel(PrimarySkillType skill) {
        return SkillTools.isChildSkill(skill) ? getChildSkillLevel(skill) : skills.get(skill);
    }

    public float getSkillXpLevelRaw(PrimarySkillType skill) {
        return skillsXp.get(skill);
    }

    public int getSkillXpLevel(PrimarySkillType skill) {
        if (SkillTools.isChildSkill(skill)) {
            return 0;
        }

        return (int) Math.floor(getSkillXpLevelRaw(skill));
    }

    public void setSkillXpLevel(PrimarySkillType skill, float xpLevel) {
        if (SkillTools.isChildSkill(skill)) {
            return;
        }

        markProfileDirty();

        skillsXp.put(skill, xpLevel);
    }

    protected float levelUp(PrimarySkillType skill) {
        float xpRemoved = getXpToLevel(skill);

        markProfileDirty();

        skills.put(skill, skills.get(skill) + 1);
        skillsXp.put(skill, skillsXp.get(skill) - xpRemoved);

        return xpRemoved;
    }

    /**
     * Remove Xp from a skill.
     *
     * @param skill Type of skill to modify
     * @param xp Amount of xp to remove
     */
    public void removeXp(PrimarySkillType skill, int xp) {
        if (SkillTools.isChildSkill(skill)) {
            return;
        }

        markProfileDirty();

        skillsXp.put(skill, skillsXp.get(skill) - xp);
    }

    public void removeXp(PrimarySkillType skill, float xp) {
        if (SkillTools.isChildSkill(skill)) {
            return;
        }

        markProfileDirty();

        skillsXp.put(skill, skillsXp.get(skill) - xp);
    }

    /**
     * Modify a skill level.
     *
     * @param skill Type of skill to modify
     * @param level New level value for the skill
     */
    public void modifySkill(PrimarySkillType skill, int level) {
        if (SkillTools.isChildSkill(skill)) {
            return;
        }

        markProfileDirty();

        //Don't allow levels to be negative
        if (level < 0) {
            level = 0;
        }

        skills.put(skill, level);
        skillsXp.put(skill, 0F);
    }

    /**
     * Add levels to a skill.
     *
     * @param skill Type of skill to add levels to
     * @param levels Number of levels to add
     */
    public void addLevels(PrimarySkillType skill, int levels) {
        modifySkill(skill, skills.get(skill) + levels);
    }

    /**
     * Add Experience to a skill.
     *
     * @param skill Type of skill to add experience to
     * @param xp Number of experience to add
     */
    public void addXp(PrimarySkillType skill, float xp) {
        markProfileDirty();

        if (SkillTools.isChildSkill(skill)) {
            var parentSkills = McMMOMod.getSkillTools().getChildSkillParents(skill);
            float dividedXP = (xp / parentSkills.size());

            for (PrimarySkillType parentSkill : parentSkills) {
                skillsXp.put(parentSkill, skillsXp.get(parentSkill) + dividedXP);
            }
        } else {
            skillsXp.put(skill, skillsXp.get(skill) + xp);
        }
    }

    /**
     * Get the registered amount of experience gained This is used for diminished XP returns
     *
     * @return xp Experience amount registered
     */
    public float getRegisteredXpGain(PrimarySkillType primarySkillType) {
        float xp = 0F;

        if (rollingSkillsXp.get(primarySkillType) != null) {
            xp = rollingSkillsXp.get(primarySkillType);
        }

        return xp;
    }

    /**
     * Register an experience gain This is used for diminished XP returns
     *
     * @param primarySkillType Skill being used
     * @param xp Experience amount to add
     */
    public void registerXpGain(PrimarySkillType primarySkillType, float xp) {
        gainedSkillsXp.add(new SkillXpGain(primarySkillType, xp));
        rollingSkillsXp.put(primarySkillType, getRegisteredXpGain(primarySkillType) + xp);
    }

    /**
     * Remove experience gains older than a given time This is used for diminished XP returns
     */
    public void purgeExpiredXpGains() {
        SkillXpGain gain;
        while ((gain = gainedSkillsXp.poll()) != null) {
            rollingSkillsXp.put(gain.getSkill(),
                    getRegisteredXpGain(gain.getSkill()) - gain.getXp());
        }
    }

    /**
     * Get the amount of Xp remaining before the next level.
     *
     * @param primarySkillType Type of skill to check
     * @return the total amount of Xp until next level
     */
    public int getXpToLevel(PrimarySkillType primarySkillType) {
        if (SkillTools.isChildSkill(primarySkillType)) {
            return 0;
        }

        final int level;
        if (McMMOMod.getExperienceConfig().getCumulativeCurveEnabled()) {
            // PORT Phase 10: the cumulative XP curve keys off the player's total power level, which
            // is resolved through UserManager (the online-player registry, not yet ported). The
            // default (non-cumulative) curve below is fully functional.
            throw new UnsupportedOperationException(
                    "Cumulative XP curve not yet ported (needs UserManager) — Phase 10");
        } else {
            level = skills.get(primarySkillType);
        }
        FormulaType formulaType = McMMOMod.getExperienceConfig().getFormulaType();

        return McMMOMod.getFormulaManager().getXPtoNextLevel(level, formulaType);
    }

    private int getChildSkillLevel(@NotNull PrimarySkillType primarySkillType)
            throws IllegalArgumentException {
        if (!SkillTools.isChildSkill(primarySkillType)) {
            throw new IllegalArgumentException(primarySkillType + " is not a child skill!");
        }

        ImmutableList<PrimarySkillType> parents = McMMOMod.getSkillTools()
                .getChildSkillParents(primarySkillType);
        // The per-parent cap is a refinement, not a requirement, and GeneralConfig is null between
        // world sessions and in unit tests that only exercise the profile. Reading a level must never
        // throw: since Agility became a child skill this runs behind every one of its rank gates, so
        // an NPE here would take out ten sub-skills rather than mis-clamp one number.
        final var generalConfig = McMMOMod.getGeneralConfig();
        int sum = 0;

        for (PrimarySkillType parent : parents) {
            final int level = getSkillLevel(parent);
            sum += generalConfig == null
                    ? level
                    : Math.min(level, McMMOMod.getSkillTools().getLevelCap(parent));
        }

        return sum / parents.size();
    }
}
