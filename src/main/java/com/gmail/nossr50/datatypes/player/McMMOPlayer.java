package com.gmail.nossr50.datatypes.player;

import static java.util.Objects.requireNonNull;

import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.datatypes.skills.ToolType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.skills.tridents.TridentsManager;
import com.gmail.nossr50.util.LogUtils;
import com.gmail.nossr50.util.skills.SkillTools;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Per-player mcMMO state: the {@link PlatformPlayer} handle, the persistent {@link PlayerProfile},
 * the player's skill managers, and the transient ability/tool/flag state. It is also the entry
 * point for awarding experience ({@link #beginXpGain}).
 *
 * <h2>Phase 10.1 strip</h2>
 * The legacy {@code McMMOPlayer} was a 1282-line god-object entangled with cut multiplayer systems
 * and not-yet-ported subsystems. This port keeps the singleplayer core and drops the rest with
 * {@code // PORT} breadcrumbs (same convention as {@link SkillTools}). Specifically:
 * <ul>
 *   <li><b>Retargeted</b> — the Bukkit {@code Player} field/return becomes {@link PlatformPlayer};
 *       {@code mcMMO.p.*} service lookups become {@link McMMOMod} statics.</li>
 *   <li><b>Kept & functional</b> — the XP-gain pipeline ({@link #beginXpGain} →
 *       {@link #beginUnsharedXpGain} → {@link #applyXpGain} → {@link #checkXp} /
 *       {@link #modifyXpGain}), power-level / level-cap logic, ability & tool mode state, the
 *       profile skill wrappers.</li>
 *   <li><b>Dropped (cut)</b> — party, chat channels/spy, scoreboards, the Adventure identity, the
 *       Bukkit metadata handle.</li>
 *   <li><b>Deferred</b> — the skill managers themselves (Phase 10.2/10.3), super-ability activation
 *       (needs EventUtils/NotificationManager/SoundManager/RankUtils/PerksUtils — Phase 10/11),
 *       the experience bar (Phase 11), exploit-prevention/teleport timestamps (Phase 10.3+),
 *       persistence/logout (Phase 5), and the mcMMO API events (Phase 3).</li>
 * </ul>
 */
public class McMMOPlayer {

    private final PlatformPlayer player;
    private final PlayerProfile profile;
    private final String playerName;

    private final Map<PrimarySkillType, SkillManager> skillManagers = new EnumMap<>(
            PrimarySkillType.class);

    private boolean displaySkillNotifications = true;
    private boolean debugMode;

    private boolean abilityUse = true;
    private boolean godMode;

    private final Map<SuperAbilityType, Boolean> abilityMode = new EnumMap<>(
            SuperAbilityType.class);
    private final Map<SuperAbilityType, Boolean> abilityInformed = new EnumMap<>(
            SuperAbilityType.class);

    private final Map<ToolType, Boolean> toolMode = new EnumMap<>(ToolType.class);

    private boolean isUsingUnarmed;

    public McMMOPlayer(@NotNull PlatformPlayer player, @NotNull PlayerProfile profile) {
        requireNonNull(player, "player cannot be null");
        requireNonNull(profile, "profile cannot be null");

        this.playerName = player.getName();
        this.player = player;
        this.profile = profile;

        final UUID uuid = player.getUniqueId();
        if (profile.getUniqueId() == null) {
            profile.setUniqueId(uuid);
        }

        initSkillManagers();

        for (SuperAbilityType superAbilityType : SuperAbilityType.values()) {
            abilityMode.put(superAbilityType, false);
            abilityInformed.put(superAbilityType, true); // This is intended
        }

        for (ToolType toolType : ToolType.values()) {
            toolMode.put(toolType, false);
        }

        debugMode = false; //Debug mode helps solve support issues, players can toggle it on or off
    }

    private void initSkillManagers() {
        for (PrimarySkillType primarySkillType : PrimarySkillType.values()) {
            initManager(primarySkillType);
        }
    }

    /**
     * Factory for a skill's {@link SkillManager}. As each manager class ports (Phase 10.2/10.3),
     * uncomment its case here and its typed getter in the accessor block below. Until a case is
     * enabled the skill has no manager and its getter returns {@code null}.
     */
    private void initManager(PrimarySkillType primarySkillType) {
        final SkillManager manager = switch (primarySkillType) {
            // PORT Phase 10.2/10.3: uncomment each case as the manager class ports.
            //   case ACROBATICS -> new AcrobaticsManager(this);
            //   case ALCHEMY    -> new AlchemyManager(this);
            //   case ARCHERY    -> new ArcheryManager(this);
            //   case AXES       -> new AxesManager(this);
            //   case CROSSBOWS  -> new CrossbowsManager(this);
            //   case EXCAVATION -> new ExcavationManager(this);
            //   case FISHING    -> new FishingManager(this);
            //   case HERBALISM  -> new HerbalismManager(this);
            //   case MACES      -> new MacesManager(this);
            //   case MINING     -> new MiningManager(this);
            //   case REPAIR     -> new RepairManager(this);
            //   case SALVAGE    -> new SalvageManager(this);
            //   case SMELTING   -> new SmeltingManager(this);
            //   case SPEARS     -> new SpearsManager(this);   // 1.21.11 always has Spears (pinned)
            //   case SWORDS     -> new SwordsManager(this);
            //   case TAMING     -> new TamingManager(this);
            case TRIDENTS -> new TridentsManager(this);
            //   case UNARMED    -> new UnarmedManager(this);
            //   case WOODCUTTING-> new WoodcuttingManager(this);
            default -> null;
        };

        if (manager != null) {
            skillManagers.put(primarySkillType, manager);
        }
    }

    /*
     * Skill-manager accessors (Phase 10.2/10.3). Each is the one-liner:
     *     public XxxManager getXxxManager() { return (XxxManager) skillManagers.get(PrimarySkillType.XXX); }
     * Uncomment/add one per manager as it ports, alongside its initManager() case above. The
     * manager ↔ skill mapping is the commented switch above (AcrobaticsManager↔ACROBATICS, …).
     */

    public TridentsManager getTridentsManager() {
        return (TridentsManager) skillManagers.get(PrimarySkillType.TRIDENTS);
    }

    public String getPlayerName() {
        return playerName;
    }

    /*
     * Experience
     */

    public double getProgressInCurrentSkillLevel(PrimarySkillType primarySkillType) {
        if (SkillTools.isChildSkill(primarySkillType)) {
            return 1.0D;
        }

        double currentXP = profile.getSkillXpLevel(primarySkillType);
        double maxXP = profile.getXpToLevel(primarySkillType);

        return (currentXP / maxXP);
    }

    /**
     * Begins an experience gain. Child-skill gains are split across their parents; everything else
     * flows to {@link #beginUnsharedXpGain}.
     *
     * @param skill Skill being used
     * @param xp Experience amount to process
     */
    public void beginXpGain(PrimarySkillType skill, float xp, XPGainReason xpGainReason,
            XPGainSource xpGainSource) {
        if (xp <= 0) {
            return;
        }

        if (SkillTools.isChildSkill(skill)) {
            var parentSkills = McMMOMod.getSkillTools().getChildSkillParents(skill);
            float splitXp = xp / parentSkills.size();

            for (PrimarySkillType parentSkill : parentSkills) {
                // PORT Phase 6: skill permission gate dropped — singleplayer always permits.
                beginXpGain(parentSkill, splitXp, xpGainReason, xpGainSource);
            }

            return;
        }

        // PORT Phase 6/10: party XP-share (ShareHandler) dropped — party is a multiplayer system cut
        // from the singleplayer port. Legacy short-circuited here when the gain was shared.
        beginUnsharedXpGain(skill, xp, xpGainReason, xpGainSource);
    }

    /**
     * Begins an experience gain that is not shared with a party (the only kind in singleplayer).
     *
     * @param skill Skill being used
     * @param xp Experience amount to process
     */
    public void beginUnsharedXpGain(PrimarySkillType skill, float xp, XPGainReason xpGainReason,
            XPGainSource xpGainSource) {
        if (player.isCreative()) {
            return;
        }

        applyXpGain(skill, modifyXpGain(skill, xp), xpGainReason, xpGainSource);

        // PORT Phase 6/10: the trailing party XP-application block dropped with the party system.
    }

    /**
     * Applies an experience gain to the profile after modifiers, checking for level-ups.
     *
     * @param primarySkillType Skill being used
     * @param xp Experience amount to add
     */
    public void applyXpGain(PrimarySkillType primarySkillType, float xp, XPGainReason xpGainReason,
            XPGainSource xpGainSource) {
        // PORT Phase 6: skill permission gate dropped — singleplayer always permits.

        // PORT Phase 3: McMMOPlayerPreXpGainEvent (a cancellable/mutable pre-gain API event fired
        // via Bukkit's PluginManager) dropped. It let other plugins adjust the gain; singleplayer
        // has none. Re-home onto the internal EventBus if a pre-gain hook is ever needed.

        if (SkillTools.isChildSkill(primarySkillType)) {
            var parentSkills = McMMOMod.getSkillTools().getChildSkillParents(primarySkillType);

            for (PrimarySkillType parentSkill : parentSkills) {
                applyXpGain(parentSkill, xp / parentSkills.size(), xpGainReason, xpGainSource);
            }

            return;
        }

        // Legacy applied the gain inside EventUtils.handleXpGainEvent: it fired the cancellable
        // McMMOPlayerXpGainEvent and, when not cancelled, ran addXp + registerXpGain. Only the event
        // firing is deferred (PORT Phase 3 — no listeners in singleplayer, so it never cancels); the
        // application itself is retained here, otherwise the gain would never reach the profile.
        addXp(primarySkillType, xp);
        profile.registerXpGain(primarySkillType, xp);

        isUsingUnarmed = (primarySkillType == PrimarySkillType.UNARMED);
        checkXp(primarySkillType, xpGainReason, xpGainSource);
    }

    /**
     * Adds the (already-modified) XP to the profile and applies any resulting level-ups.
     *
     * @param primarySkillType The skill to check
     */
    private void checkXp(PrimarySkillType primarySkillType, XPGainReason xpGainReason,
            XPGainSource xpGainSource) {
        if (hasReachedLevelCap(primarySkillType)) {
            return;
        }

        if (getSkillXpLevelRaw(primarySkillType) < getXpToLevel(primarySkillType)) {
            // PORT Phase 11: processPostXpEvent(...) drove the XP boss-bar / action-bar update,
            // deferred with the experience-bar subsystem.
            return;
        }

        int levelsGained = 0;

        while (getSkillXpLevelRaw(primarySkillType) >= getXpToLevel(primarySkillType)) {
            if (hasReachedLevelCap(primarySkillType)) {
                setSkillXpLevel(primarySkillType, 0);
                break;
            }

            profile.levelUp(primarySkillType);
            levelsGained++;
        }

        if (levelsGained > 0) {
            LogUtils.debug(playerName + " leveled up " + primarySkillType + " x" + levelsGained
                    + " (now level " + profile.getSkillLevel(primarySkillType) + ")");
        }

        // PORT Phase 3/10: EventUtils.tryLevelChangeEvent(...) dropped — it fired the cancellable
        // McMMOPlayerLevelChangeEvent that could veto the level-up. No listeners in singleplayer.
        // PORT Phase 11: level-up sound (SoundManager), level-up notification + skill-unlock
        // notifications (NotificationManager), and the XP-bar update dropped with the feedback
        // subsystems. The level change itself is already committed to the profile above.
    }

    /**
     * Modifies an experience gain using the skill modifier and global rate. Returns 0 when the
     * skill or power-level cap has been reached.
     *
     * @param primarySkillType Skill being used
     * @param xp Experience amount to process
     * @return Modified experience
     */
    @VisibleForTesting
    float modifyXpGain(PrimarySkillType primarySkillType, float xp) {
        if ((McMMOMod.getSkillTools().getLevelCap(primarySkillType) <= getSkillLevel(
                primarySkillType))
                || (McMMOMod.getGeneralConfig().getPowerLevelCap() <= getPowerLevel())) {
            return 0;
        }

        xp = (float) (
                (xp * McMMOMod.getExperienceConfig().getFormulaSkillModifier(primarySkillType))
                        * McMMOMod.getExperienceConfig().getExperienceGainsGlobalMultiplier());

        // PORT Phase 6: PerksUtils.handleXpPerks(...) dropped — perks are permission-driven XP
        // multipliers with no singleplayer analogue, so the modified xp passes through unchanged.
        return xp;
    }

    /**
     * Gets the power level of this player (sum of non-child skill levels).
     *
     * @return the power level of the player
     */
    public int getPowerLevel() {
        int powerLevel = 0;

        for (PrimarySkillType primarySkillType : SkillTools.NON_CHILD_SKILLS) {
            // PORT Phase 6: skill permission gate dropped (always permitted in singleplayer).
            powerLevel += getSkillLevel(primarySkillType);
        }

        return powerLevel;
    }

    /**
     * Whether a player is level capped. If they are at the power level cap this returns true,
     * otherwise it checks their skill level against the per-skill cap.
     */
    public boolean hasReachedLevelCap(PrimarySkillType primarySkillType) {
        if (hasReachedPowerLevelCap()) {
            return true;
        }

        return getSkillLevel(primarySkillType) >= McMMOMod.getSkillTools()
                .getLevelCap(primarySkillType);
    }

    /**
     * Whether a player has reached the power level cap.
     *
     * @return true if they have reached the power level cap
     */
    public boolean hasReachedPowerLevelCap() {
        return this.getPowerLevel() >= McMMOMod.getGeneralConfig().getPowerLevelCap();
    }

    /*
     * Ability mode / informed state
     */

    public boolean getAbilityMode(@NotNull SuperAbilityType superAbilityType) {
        requireNonNull(superAbilityType, "superAbilityType cannot be null");
        return abilityMode.get(superAbilityType);
    }

    public void setAbilityMode(SuperAbilityType ability, boolean isActive) {
        abilityMode.put(ability, isActive);
    }

    public boolean getAbilityInformed(SuperAbilityType ability) {
        return abilityInformed.get(ability);
    }

    public void setAbilityInformed(SuperAbilityType ability, boolean isInformed) {
        abilityInformed.put(ability, isInformed);
    }

    public boolean getAbilityUse() {
        return abilityUse;
    }

    public void toggleAbilityUse() {
        abilityUse = !abilityUse;
    }

    // PORT Phase 10/11: resetAbilityMode() dropped — it ran an AbilityDisableTask (a runnable, not
    // yet ported) per ability to fire deactivation side effects. Re-add with the super-ability
    // activation subsystem.

    /*
     * Tool preparation mode
     */

    public boolean getToolPreparationMode(ToolType tool) {
        return toolMode.get(tool);
    }

    public void setToolPreparationMode(ToolType tool, boolean isPrepared) {
        toolMode.put(tool, isPrepared);
    }

    public void resetToolPrepMode() {
        for (ToolType tool : ToolType.values()) {
            setToolPreparationMode(tool, false);
        }
    }

    /*
     * Flags
     */

    public boolean getGodMode() {
        return godMode;
    }

    public void toggleGodMode() {
        godMode = !godMode;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public void toggleDebugMode() {
        debugMode = !debugMode;
    }

    public boolean useChatNotifications() {
        return displaySkillNotifications;
    }

    public void toggleChatNotifications() {
        displaySkillNotifications = !displaySkillNotifications;
    }

    public boolean isUsingUnarmed() {
        return isUsingUnarmed;
    }

    /*
     * Players & Profiles
     */

    public @NotNull PlatformPlayer getPlayer() {
        return player;
    }

    public @NotNull PlayerProfile getProfile() {
        return profile;
    }

    /*
     * Profile skill wrappers — so callers don't have to hold the PlayerProfile alongside this.
     */

    public int getSkillLevel(PrimarySkillType skill) {
        return profile.getSkillLevel(skill);
    }

    public float getSkillXpLevelRaw(PrimarySkillType skill) {
        return profile.getSkillXpLevelRaw(skill);
    }

    public int getSkillXpLevel(PrimarySkillType skill) {
        return profile.getSkillXpLevel(skill);
    }

    public void setSkillXpLevel(PrimarySkillType skill, float xpLevel) {
        profile.setSkillXpLevel(skill, xpLevel);
    }

    public int getXpToLevel(PrimarySkillType skill) {
        return profile.getXpToLevel(skill);
    }

    public void removeXp(PrimarySkillType skill, int xp) {
        profile.removeXp(skill, xp);
    }

    public void modifySkill(PrimarySkillType skill, int level) {
        profile.modifySkill(skill, level);
    }

    public void addLevels(PrimarySkillType skill, int levels) {
        profile.addLevels(skill, levels);
    }

    public void addXp(PrimarySkillType skill, float xp) {
        profile.addXp(skill, xp);
    }

    public void setAbilityDATS(SuperAbilityType ability, long DATS) {
        profile.setAbilityDATS(ability, DATS);
    }

    public void resetCooldowns() {
        profile.resetCooldowns();
    }

    // PORT Phase 10.3: getAttackStrength() dropped — read the Bukkit attack-cooldown; needs a
    // PlatformPlayer attack-cooldown accessor, re-added with the combat skills.

    // PORT Phase 10/11: the super-ability activation cluster (checkAbilityActivation,
    // processAbilityActivation, processAxeToolMessages, calculateTimeRemaining, isAbilityOnCooldown)
    // dropped — it depends on EventUtils, NotificationManager, SoundManager, SkillUtils, PerksUtils,
    // RankUtils, BlockUtils, the Folia scheduler, and the ability runnables, none of which are
    // ported yet. Re-add with the super-ability subsystem.

    // PORT Phase 10.3+: exploit-prevention / teleport / Chimaera-wing timestamps (recentlyHurt,
    // respawnATS, teleportATS, databaseATS, teleportCommence, Chimaera-wing DATS) dropped — the
    // teleport ones carried a Bukkit Location and none are needed by the leaf skills. Re-add with
    // the systems that read them.

    // PORT Phase 5 / Phase 11: logout()/cleanup() dropped — profile save (persistence, Phase 5),
    // UserManager de-registration + taming-summon cleanup (Phase 10), rupture-task teardown
    // (Phase 11), and the cut scoreboard/party/database paths. The registry removal will be driven
    // from the ported player-quit listener.

    // DROPPED (cut systems): the Adventure identity() (no Adventure in the Fabric port), the party
    // cluster (setupPartyData/getParty/…/checkParty and the item-share modifier), chat channels &
    // party-chat spy (getChatChannel/setChatMode/isPartyChatSpying), the scoreboard "last skill
    // shown" tracker, and the Bukkit FixedMetadataValue handle (getPlayerMetadata).
}
