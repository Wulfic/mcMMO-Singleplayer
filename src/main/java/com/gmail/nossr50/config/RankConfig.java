package com.gmail.nossr50.config;

import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.util.LogUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * {@code skillranks.yml} — per-subskill rank unlock levels, split into {@code Standard} and
 * {@code RetroMode} scaling sections. Ported onto {@link ConfigLoader}.
 *
 * <p>Retargets from the Bukkit base:
 * <ul>
 *   <li>{@code mcMMO.p.getGeneralConfig().getIsRetroMode()} → {@link McMMOMod#getGeneralConfig()}.
 *       Only the {@link #getSubSkillUnlockLevel(SubSkillType, int)} convenience overload touches
 *       it (a runtime path); the explicit-{@code retroMode} overloads and constructor-time
 *       validation don't, so this stays unit-testable with the config service un-wired.</li>
 *   <li>{@code defaultYamlConfig} → the base {@code defaultConfig}; {@code updateFile()} →
 *       {@link #saveConfig()}; debug logging → SLF4J via {@link LogUtils}.</li>
 * </ul>
 *
 * <p>The {@code AbstractSubSkill} overloads are dropped ({@code // PORT Phase 10}) — they need the
 * not-yet-ported subskill types; every current caller keys off {@link SubSkillType}, whose
 * {@code getRankConfigAddress()} is the same root address those overloads rebuilt by hand.
 */
public class RankConfig extends ConfigLoader {

    public RankConfig(Path dataFolder) {
        super("skillranks.yml", dataFolder);
        loadKeys();
        validateKeys();
    }

    @Override
    protected void loadKeys() {
        // Values are read lazily through the getters; nothing to pre-compute.
    }

    protected boolean validateKeys() {
        List<String> reason = new ArrayList<>();

        /*
         * In the future this method will check keys for all skills, but for now it only checks
         * overhauled skills
         */
        checkKeys(reason);

        return noErrorsInConfig(reason);
    }

    /**
     * Returns the unlock level for a subskill, using the globally-configured scaling mode.
     *
     * @param subSkillType target subskill
     * @param rank the rank we are checking
     * @return the level requirement for a subskill at this particular rank
     */
    public int getSubSkillUnlockLevel(SubSkillType subSkillType, int rank) {
        String key = subSkillType.getRankConfigAddress();

        return findRankByRootAddress(rank, key);
    }

    /**
     * Returns the unlock level for a subskill in the requested scaling mode.
     *
     * @param subSkillType target subskill
     * @param rank the rank we are checking
     * @param retroMode whether to read the RetroMode section instead of Standard
     * @return the level requirement for a subskill at this particular rank
     */
    public int getSubSkillUnlockLevel(SubSkillType subSkillType, int rank, boolean retroMode) {
        String key = getRankAddressKey(subSkillType, rank, retroMode);
        return config.getInt(key, defaultConfig.getInt(key));
    }

    // PORT Phase 10: getSubSkillUnlockLevel(AbstractSubSkill, int) — dropped. AbstractSubSkill isn't
    // ported; re-add with the subskill types. Its body rebuilt the same root address as
    // SubSkillType.getRankConfigAddress() from getPrimaryKeyName()/getConfigKeyName().

    private int findRankByRootAddress(int rank, String key) {
        String scalingKey =
                McMMOMod.getGeneralConfig().getIsRetroMode() ? ".RetroMode." : ".Standard.";

        String targetRank = "Rank_" + rank;

        key += scalingKey;
        key += targetRank;

        return config.getInt(key);
    }

    public String getRankAddressKey(SubSkillType subSkillType, int rank, boolean retroMode) {
        String key = subSkillType.getRankConfigAddress();
        String scalingKey = retroMode ? ".RetroMode." : ".Standard.";

        String targetRank = "Rank_" + rank;

        key += scalingKey;
        key += targetRank;

        return key;
    }

    // PORT Phase 10: getRankAddressKey(AbstractSubSkill, int, boolean) — dropped alongside the
    // AbstractSubSkill unlock-level overload above.

    private void resetRankValue(@NotNull SubSkillType subSkillType, int rank, boolean retroMode) {
        String key = getRankAddressKey(subSkillType, rank, retroMode);
        int defaultValue = defaultConfig.getInt(key);
        config.set(key, defaultValue);
        LogUtils.debug(key + " SET -> " + defaultValue);
    }

    /**
     * Checks for valid keys for subskill ranks
     */
    private void checkKeys(@NotNull List<String> reasons) {
        HashSet<SubSkillType> badSkillSetup = new HashSet<>();

        //For now we will only check ranks of stuff I've overhauled
        checkConfig(reasons, badSkillSetup, true);
        checkConfig(reasons, badSkillSetup, false);

        //Fix bad entries
        if (badSkillSetup.isEmpty()) {
            return;
        }

        LogUtils.debug(
                "(FIXING CONFIG) mcMMO is correcting a few mistakes found in your skill rank config setup");

        for (SubSkillType subSkillType : badSkillSetup) {
            LogUtils.debug(
                    "(FIXING CONFIG) Resetting rank config settings for skill named - "
                            + subSkillType.toString());
            fixBadEntries(subSkillType);
        }
    }

    private void checkConfig(@NotNull List<String> reasons,
            @NotNull HashSet<SubSkillType> badSkillSetup,
            boolean retroMode) {
        for (SubSkillType subSkillType : SubSkillType.values()) {
            //Keeping track of the rank requirements and making sure there are no logical errors
            int curRank = 0;
            int prevRank = 0;

            for (int x = 0; x < subSkillType.getNumRanks(); x++) {
                int index = x + 1;

                if (curRank > 0) {
                    prevRank = curRank;
                }

                curRank = getSubSkillUnlockLevel(subSkillType, index, retroMode);

                //Do we really care if its below 0? Probably not
                if (curRank < 0) {
                    reasons.add(
                            "(CONFIG ISSUE) " + subSkillType
                                    + " should not have any ranks that require a negative level!");
                    badSkillSetup.add(subSkillType);
                    continue;
                }

                if (prevRank > curRank) {
                    //We're going to allow this but we're going to warn them
                    LogUtils.debug(
                            "(CONFIG ISSUE) You have the ranks for the subskill " + subSkillType
                                    + " set up poorly, sequential ranks should have ascending requirements");
                    badSkillSetup.add(subSkillType);
                }
            }
        }
    }

    private void fixBadEntries(@NotNull SubSkillType subSkillType) {
        for (int x = 0; x < subSkillType.getNumRanks(); x++) {
            int index = x + 1;

            //Reset Retromode entries
            resetRankValue(subSkillType, index, true);
            //Reset Standard Entries
            resetRankValue(subSkillType, index, false);
        }

        saveConfig();
    }

    /** Persists the in-memory config back to disk (replaces the Bukkit {@code updateFile()}). */
    private void saveConfig() {
        try {
            config.save(getFile());
        } catch (IOException e) {
            LOGGER.error("Failed to save config file: {}", fileName, e);
        }
    }

    /** Logs any collected validation issues and reports whether the config is clean. */
    private boolean noErrorsInConfig(List<String> issues) {
        for (String issue : issues) {
            LOGGER.warn(issue);
        }

        return issues.isEmpty();
    }
}
