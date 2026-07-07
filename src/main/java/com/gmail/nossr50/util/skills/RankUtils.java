package com.gmail.nossr50.util.skills;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.util.player.UserManager;
import java.util.HashMap;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves a player's <em>rank</em> in a {@link SubSkillType}. A subskill's ranks unlock at rising
 * parent-skill levels (configured in {@code skillranks.yml} via {@link com.gmail.nossr50.config.RankConfig}),
 * so a player's current rank is a pure function of their parent-skill level. The unlock-level lookups
 * are cached in {@link #subSkillRanks} on first use.
 *
 * <p>Port notes (Phase 10.2):
 * <ul>
 *   <li>The legacy {@code Player}-typed API is retargeted: the core {@link #getRank(McMMOPlayer, SubSkillType)}
 *       takes the {@link McMMOPlayer} directly (a rank is level-only), with a {@link PlatformPlayer}
 *       overload that resolves the {@link McMMOPlayer} via {@link UserManager} and preserves the legacy
 *       "no data → rank 0" guard.</li>
 *   <li>{@code RankConfig.getInstance()} → {@link McMMOMod#getRankConfig()} (the service-locator wiring
 *       that replaced the singleton in Phase 8).</li>
 *   <li><b>Dropped:</b> {@code executeSkillUnlockNotifications} / {@code resetUnlockDelayTimer} — they
 *       rode the Folia scheduler + {@code SkillUnlockNotificationTask} + {@code Permissions} +
 *       {@code NotificationManager}, none of which are ported. PORT Phase 11 (notifications).</li>
 *   <li><b>Dropped:</b> every {@code AbstractSubSkill} overload and the {@code InteractionManager}
 *       subskill-list loop in {@link #populateRanks()} — {@code AbstractSubSkill}/{@code InteractionManager}
 *       aren't ported. PORT Phase 10.3. The enum-driven {@link SubSkillType} ranks cover every ported skill.</li>
 * </ul>
 */
public final class RankUtils {
    private static HashMap<String, HashMap<Integer, Integer>> subSkillRanks;

    private RankUtils() {
    }

    /**
     * Visible for testing: drops the lazily-built unlock-level cache so a test can re-wire the config
     * scaling ({@code RankConfig} / {@code GeneralConfig} retro mode) between runs and see fresh values.
     * The cache is otherwise keyed only by subskill name and is safe to keep for a session.
     */
    static void resetRankCache() {
        subSkillRanks = null;
    }

    /**
     * Populates the ranks for every subskill we know about.
     */
    public static void populateRanks() {
        for (SubSkillType subSkillType : SubSkillType.values()) {
            addRanks(subSkillType);
        }

        // PORT Phase 10.3: the second legacy loop over InteractionManager.getSubSkillList()
        // (AbstractSubSkill ranks) is dropped — AbstractSubSkill / InteractionManager aren't ported.
    }

    private static void addRanks(SubSkillType subSkillType) {
        // Fill out the rank array, highest rank first (mirrors legacy insertion order).
        for (int i = 0; i < subSkillType.getNumRanks(); i++) {
            addRank(subSkillType, subSkillType.getNumRanks() - i);
        }
    }

    /**
     * Returns whether the player has unlocked the first rank in the target subskill.
     *
     * @param mmoPlayer the player
     * @param subSkillType the target subskill
     * @return true if the player has at least one rank in the skill (or the skill has no ranks)
     */
    public static boolean hasUnlockedSubskill(McMMOPlayer mmoPlayer, SubSkillType subSkillType) {
        int curRank = getRank(mmoPlayer, subSkillType);

        // -1 means the skill has no unlockable levels and is therefore always unlocked
        return curRank == -1 || curRank >= 1;
    }

    /**
     * Returns whether the player has reached the specified rank in the target subskill.
     *
     * @param rank the target rank
     * @param mmoPlayer the player
     * @param subSkillType the target subskill
     * @return true if the player is at least that rank in this subskill
     */
    public static boolean hasReachedRank(int rank, McMMOPlayer mmoPlayer, SubSkillType subSkillType) {
        return getRank(mmoPlayer, subSkillType) >= rank;
    }

    /**
     * Gets the current rank of the subskill for the player, resolving the {@link McMMOPlayer} from a
     * {@link PlatformPlayer} via {@link UserManager}. An untracked player yields rank 0 (legacy parity).
     *
     * @param player the player in question
     * @param subSkillType target subskill
     * @return the rank the player currently has achieved. -1 for skills without ranks.
     */
    public static int getRank(PlatformPlayer player, SubSkillType subSkillType) {
        return getRank(UserManager.getPlayer(player), subSkillType);
    }

    /**
     * Gets the current rank of the subskill for the player.
     *
     * @param mmoPlayer the player in question (may be {@code null} — e.g. no data yet)
     * @param subSkillType target subskill
     * @return the rank the player currently has achieved. -1 for skills without ranks.
     */
    public static int getRank(@Nullable McMMOPlayer mmoPlayer, SubSkillType subSkillType) {
        String skillName = subSkillType.toString();
        int numRanks = subSkillType.getNumRanks();

        if (subSkillRanks == null) {
            subSkillRanks = new HashMap<>();
        }

        if (numRanks == 0) {
            return -1; // -1 means the skill doesn't have ranks
        }

        if (subSkillRanks.get(skillName) == null) {
            addRanks(subSkillType);
        }

        // Legacy resolved the McMMOPlayer from a Bukkit Player via UserManager and returned 0 when it
        // was null (offline / not tracked). The port takes the McMMOPlayer directly, so the same guard
        // is a plain null check.
        if (mmoPlayer == null) {
            return 0;
        }

        // Skill level of the parent skill
        int currentSkillLevel = mmoPlayer.getSkillLevel(subSkillType.getParentSkill());

        for (int i = 0; i < numRanks; i++) {
            // Compare against the highest to lowest rank in that order
            int rank = numRanks - i;
            int unlockLevel = getRankUnlockLevel(subSkillType, rank);

            // If we check all ranks and still cannot unlock the skill, we return rank 0
            if (rank == 0) {
                return 0;
            }

            // True if our skill level can unlock the current rank
            if (currentSkillLevel >= unlockLevel) {
                return rank;
            }
        }

        return 0; // We should never reach this
    }

    /**
     * Adds ranks to our map.
     *
     * @param subSkillType the subskill to add ranks for
     * @param rank the rank to add
     */
    private static void addRank(SubSkillType subSkillType, int rank) {
        initMaps(subSkillType.toString());

        HashMap<Integer, Integer> rankMap = subSkillRanks.get(subSkillType.toString());

        rankMap.put(rank, getRankUnlockLevel(subSkillType, rank));
    }

    private static void initMaps(String s) {
        if (subSkillRanks == null) {
            subSkillRanks = new HashMap<>();
        }

        subSkillRanks.computeIfAbsent(s, k -> new HashMap<>());
    }

    /**
     * Gets the unlock level for a specific rank in a subskill.
     *
     * @param subSkillType the target subskill
     * @param rank the target rank
     * @return the level at which this rank unlocks
     */
    public static int getRankUnlockLevel(SubSkillType subSkillType, int rank) {
        return McMMOMod.getRankConfig().getSubSkillUnlockLevel(subSkillType, rank);
    }

    /**
     * Get the level at which a skill is unlocked for a player (this is the first rank of a skill).
     *
     * @param subSkillType target subskill
     * @return the unlock requirement for rank 1 in this skill
     */
    public static int getUnlockLevel(SubSkillType subSkillType) {
        return McMMOMod.getRankConfig().getSubSkillUnlockLevel(subSkillType, 1);
    }

    /**
     * Get the highest rank of a subskill.
     *
     * @param subSkillType target subskill
     * @return the last rank of a subskill
     */
    public static int getHighestRank(SubSkillType subSkillType) {
        return subSkillType.getNumRanks();
    }

    public static String getHighestRankStr(SubSkillType subSkillType) {
        return String.valueOf(subSkillType.getNumRanks());
    }

    public static int getSuperAbilityUnlockRequirement(SuperAbilityType superAbilityType) {
        return getRankUnlockLevel(superAbilityType.getSubSkillTypeDefinition(), 1);
    }

    public static boolean isPlayerMaxRankInSubSkill(McMMOPlayer mmoPlayer, SubSkillType subSkillType) {
        return getRank(mmoPlayer, subSkillType) == getHighestRank(subSkillType);
    }
}
