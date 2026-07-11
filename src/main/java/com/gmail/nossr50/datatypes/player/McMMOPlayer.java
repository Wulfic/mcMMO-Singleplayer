package com.gmail.nossr50.datatypes.player;

import static java.util.Objects.requireNonNull;

import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.datatypes.skills.ToolType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.runnables.skills.AbilityDisableTask;
import com.gmail.nossr50.runnables.skills.ToolLowerTask;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.skills.acrobatics.AcrobaticsManager;
import com.gmail.nossr50.skills.alchemy.AlchemyManager;
import com.gmail.nossr50.skills.archery.ArcheryManager;
import com.gmail.nossr50.skills.axes.AxesManager;
import com.gmail.nossr50.skills.crossbows.CrossbowsManager;
import com.gmail.nossr50.skills.excavation.ExcavationManager;
import com.gmail.nossr50.skills.fishing.FishingManager;
import com.gmail.nossr50.skills.herbalism.HerbalismManager;
import com.gmail.nossr50.skills.mining.MiningManager;
import com.gmail.nossr50.skills.repair.RepairManager;
import com.gmail.nossr50.skills.salvage.SalvageManager;
import com.gmail.nossr50.skills.maces.MacesManager;
import com.gmail.nossr50.skills.smelting.SmeltingManager;
import com.gmail.nossr50.skills.spears.SpearsManager;
import com.gmail.nossr50.skills.swords.SwordsManager;
import com.gmail.nossr50.skills.taming.TamingManager;
import com.gmail.nossr50.skills.tridents.TridentsManager;
import com.gmail.nossr50.skills.unarmed.UnarmedManager;
import com.gmail.nossr50.skills.woodcutting.WoodcuttingManager;
import com.gmail.nossr50.util.LogUtils;
import com.gmail.nossr50.util.Misc;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.skills.PerksUtils;
import com.gmail.nossr50.util.skills.RankUtils;
import com.gmail.nossr50.util.skills.SkillTools;
import com.gmail.nossr50.util.sounds.SoundManager;
import com.gmail.nossr50.util.sounds.SoundType;
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

    // Combat-captured attack-cooldown charge (0.0–1.0) at the moment of the hit that a combat
    // handler is processing. The vanilla attack-cooldown read that fills it lands with the combat
    // pipeline (PORT Phase 10.3+); until then it stays at the "fully charged" default so the
    // damage-math skills (Berserk, Critical Strikes, Rupture odds, …) read a neutral 1.0.
    private float attackStrength = 1.0f;

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
            case ACROBATICS -> new AcrobaticsManager(this);
            case ALCHEMY -> new AlchemyManager(this);
            case ARCHERY -> new ArcheryManager(this);
            case AXES -> new AxesManager(this);
            case CROSSBOWS -> new CrossbowsManager(this);
            case EXCAVATION -> new ExcavationManager(this);
            case FISHING -> new FishingManager(this);
            case HERBALISM -> new HerbalismManager(this);
            case MACES -> new MacesManager(this);
            case MINING -> new MiningManager(this);
            case REPAIR -> new RepairManager(this);
            case SALVAGE -> new SalvageManager(this);
            case SMELTING -> new SmeltingManager(this);
            case SPEARS -> new SpearsManager(this);   // 1.21.11 always has Spears (pinned)
            case SWORDS -> new SwordsManager(this);
            case TAMING -> new TamingManager(this);
            case TRIDENTS -> new TridentsManager(this);
            case UNARMED -> new UnarmedManager(this);
            case WOODCUTTING -> new WoodcuttingManager(this);
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

    public AcrobaticsManager getAcrobaticsManager() {
        return (AcrobaticsManager) skillManagers.get(PrimarySkillType.ACROBATICS);
    }

    public AlchemyManager getAlchemyManager() {
        return (AlchemyManager) skillManagers.get(PrimarySkillType.ALCHEMY);
    }

    public ArcheryManager getArcheryManager() {
        return (ArcheryManager) skillManagers.get(PrimarySkillType.ARCHERY);
    }

    public AxesManager getAxesManager() {
        return (AxesManager) skillManagers.get(PrimarySkillType.AXES);
    }

    public CrossbowsManager getCrossbowsManager() {
        return (CrossbowsManager) skillManagers.get(PrimarySkillType.CROSSBOWS);
    }

    public ExcavationManager getExcavationManager() {
        return (ExcavationManager) skillManagers.get(PrimarySkillType.EXCAVATION);
    }

    public FishingManager getFishingManager() {
        return (FishingManager) skillManagers.get(PrimarySkillType.FISHING);
    }

    public HerbalismManager getHerbalismManager() {
        return (HerbalismManager) skillManagers.get(PrimarySkillType.HERBALISM);
    }

    public MacesManager getMacesManager() {
        return (MacesManager) skillManagers.get(PrimarySkillType.MACES);
    }

    public MiningManager getMiningManager() {
        return (MiningManager) skillManagers.get(PrimarySkillType.MINING);
    }

    public RepairManager getRepairManager() {
        return (RepairManager) skillManagers.get(PrimarySkillType.REPAIR);
    }

    public SalvageManager getSalvageManager() {
        return (SalvageManager) skillManagers.get(PrimarySkillType.SALVAGE);
    }

    public SmeltingManager getSmeltingManager() {
        return (SmeltingManager) skillManagers.get(PrimarySkillType.SMELTING);
    }

    public SpearsManager getSpearsManager() {
        return (SpearsManager) skillManagers.get(PrimarySkillType.SPEARS);
    }

    public SwordsManager getSwordsManager() {
        return (SwordsManager) skillManagers.get(PrimarySkillType.SWORDS);
    }

    public TamingManager getTamingManager() {
        return (TamingManager) skillManagers.get(PrimarySkillType.TAMING);
    }

    public TridentsManager getTridentsManager() {
        return (TridentsManager) skillManagers.get(PrimarySkillType.TRIDENTS);
    }

    public UnarmedManager getUnarmedManager() {
        return (UnarmedManager) skillManagers.get(PrimarySkillType.UNARMED);
    }

    public WoodcuttingManager getWoodcuttingManager() {
        return (WoodcuttingManager) skillManagers.get(PrimarySkillType.WOODCUTTING);
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

        // PORT Phase 3/10: EventUtils.tryLevelChangeEvent(...) dropped — it fired the cancellable
        // McMMOPlayerLevelChangeEvent that could veto the level-up. No listeners in singleplayer, so
        // it never cancels; the level change is already committed to the profile above.

        if (levelsGained > 0) {
            LogUtils.debug(playerName + " leveled up " + primarySkillType + " x" + levelsGained
                    + " (now level " + profile.getSkillLevel(primarySkillType) + ")");

            // Player-facing feedback (legacy fired both here, in this order, on a real level-up).
            if (McMMOMod.getGeneralConfig().getLevelUpSoundsEnabled()) {
                SoundManager.sendSound(player, SoundType.LEVEL_UP);
            }

            NotificationManager.sendPlayerLevelUpNotification(this, primarySkillType, levelsGained,
                    profile.getSkillLevel(primarySkillType));
        }

        // PORT Phase 11: the skill-unlock notification sweep and the XP-bar update still ride with
        // the deferred experience-bar subsystem (processPostXpEvent).
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

    /**
     * Clears the active state of every super ability. Legacy ran a full {@code AbilityDisableTask}
     * per ability to fire the deactivation side effects (deactivate event, off-notification,
     * inventory ability-buff removal, chunk refresh, and the follow-up cooldown-refresh task). Those
     * side effects are gated on the still-unported {@code AbilityDisableTask}/{@code EventUtils}/
     * {@code NotificationManager}/{@code SkillUtils} (PORT Phase 11) and the interaction listener
     * that would ever set a mode true, so this port flips the transient mode/informed flags directly
     * — the correct, side-effect-free reset used by {@code /mcrefresh} and player logout.
     */
    public void resetAbilityMode() {
        for (SuperAbilityType ability : SuperAbilityType.values()) {
            setAbilityMode(ability, false);
        }
    }

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

    /**
     * The attack-cooldown charge (0.0–1.0) captured for the hit currently being processed. Combat
     * damage-math sub-skills scale their bonus by this. Defaults to {@code 1.0} until the combat
     * pipeline that reads the vanilla attack-cooldown lands (PORT Phase 10.3+).
     */
    public float getAttackStrength() {
        return attackStrength;
    }

    public void setAttackStrength(float attackStrength) {
        this.attackStrength = attackStrength;
    }

    /*
     * Super-ability cooldown / duration core (Phase 11.2)
     *
     * The MC-free numeric heart of the super-ability subsystem. The activation *trigger* itself
     * (checkAbilityActivation / processAbilityActivation / processAxeToolMessages) is still deferred
     * — it needs the interaction listener, held-item/tool detection, EventUtils' ability events,
     * NotificationManager, SoundManager, SkillUtils and the ability runnables (AbilityDisableTask /
     * ToolLowerTask), none of which are ported. These three methods are the pieces that don't touch
     * any of that: pure reads of the profile's deactivation timestamp (DATS) and the config-driven
     * ability length. They drive the cooldown display and are the exact math the eventual trigger
     * will call, so they land + get unit-tested now.
     */

    /**
     * Whether the given super ability is currently on cooldown (not active, and its deactivation
     * timestamp plus its cooldown is still in the future).
     */
    public boolean isAbilityOnCooldown(@NotNull SuperAbilityType ability) {
        return !getAbilityMode(ability) && calculateTimeRemaining(ability) > 0;
    }

    /**
     * Seconds remaining until the ability's cooldown expires (≤ 0 means ready). The profile stores
     * the deactivation timestamp (DATS) in whole seconds, so it is scaled back to millis here,
     * added to the (perk-adjusted) cooldown, and compared against wall-clock time.
     *
     * @param ability the ability whose cooldown to check
     * @return the number of seconds remaining before the cooldown expires
     */
    public int calculateTimeRemaining(@NotNull SuperAbilityType ability) {
        long deactivatedTimestamp = profile.getAbilityDATS(ability) * Misc.TIME_CONVERSION_FACTOR;
        return (int) (((deactivatedTimestamp
                + ((long) PerksUtils.handleCooldownPerks(ability.getCooldown())
                * Misc.TIME_CONVERSION_FACTOR)) - System.currentTimeMillis())
                / Misc.TIME_CONVERSION_FACTOR);
    }

    /**
     * The length, in ticks, that activating {@code superAbilityType} at this player's current level
     * of {@code primarySkillType} would run for. Extracted verbatim from legacy
     * {@code checkAbilityActivation} (the "buried pure decision" pattern): base {@code 2 + level /
     * Ability_Length}, capped to {@code Ability_Length_Cap} skill levels when that cap is positive,
     * then run through {@link PerksUtils#handleActivationPerks} for the per-ability {@code maxLength}
     * cap. Pure over {@code (skillLevel, AdvancedConfig, SuperAbilityType.getMaxLength)} so the
     * eventual activation trigger is a thin wrapper around it.
     */
    public int calculateAbilityActivationTicks(@NotNull PrimarySkillType primarySkillType,
            @NotNull SuperAbilityType superAbilityType) {
        // These values change depending on whether the server is in retro mode.
        int abilityLengthVar = McMMOMod.getAdvancedConfig().getAbilityLength();
        int abilityLengthCap = McMMOMod.getAdvancedConfig().getAbilityLengthCap();

        int baseTicks;
        // Ability cap of 0 or below means no cap.
        if (abilityLengthCap > 0) {
            baseTicks = 2 + (Math.min(abilityLengthCap, getSkillLevel(primarySkillType))
                    / abilityLengthVar);
        } else {
            baseTicks = 2 + (getSkillLevel(primarySkillType) / abilityLengthVar);
        }

        return PerksUtils.handleActivationPerks(baseTicks, superAbilityType.getMaxLength());
    }

    /*
     * Super-ability activation trigger (K6 / Phase 11)
     *
     * The interaction-driven half of the super-ability subsystem, ported from legacy
     * checkAbilityActivation / processAbilityActivation / processAxeToolMessages (git 811b50325
     * McMMOPlayer.java L907-1126). The Fabric interaction listener
     * ({@code fabric.listeners.SuperAbilityListener}) does the MC-typed block/tool gating and calls
     * these; the flow here stays MC-free by routing held-item and target-block reads through the
     * {@link PlatformPlayer} ({@link PlatformPlayer#isHoldingTool}/{@link PlatformPlayer#isLookingAtTree}).
     *
     * Deferred (breadcrumbs inline):
     *  - K5 EventUtils.callPlayerAbilityActivateEvent — no singleplayer cancel-listeners; dropped.
     *  - K3/K4 SkillUtils.removeAbilityBuff / handleAbilitySpeedIncrease — the Super/Giga Breaker
     *    held-tool haste-enchant boost. The ability mode still flips + schedules disable/cooldown and
     *    gates the effect bodies; the vanilla dig-speed increase lands with the item/enchant adapter.
     *  - sendAbilityNotificationToOtherPlayers — a multiplayer broadcast (cut, no other players).
     */

    /**
     * Activate the readied super ability for {@code primarySkillType} when the player acts on a valid
     * target (legacy {@code checkAbilityActivation}). No-op if the ability is already running, still
     * locked by rank, or on cooldown; otherwise flips the ability mode on, stamps the deactivation
     * timestamp, clears the tool-prep flag, and schedules the {@link AbilityDisableTask} that ends it.
     */
    public void checkAbilityActivation(@NotNull PrimarySkillType primarySkillType) {
        ToolType tool = McMMOMod.getSkillTools().getPrimarySkillToolType(primarySkillType);
        SuperAbilityType superAbilityType = McMMOMod.getSkillTools().getSuperAbility(primarySkillType);
        SubSkillType subSkillType = superAbilityType.getSubSkillTypeDefinition();

        // Singleplayer: super-ability permissions are always granted (legacy getPermissions(player)
        // gate dropped, Phase 6 by-design).
        if (getAbilityMode(superAbilityType)) {
            return;
        }

        if (!RankUtils.hasUnlockedSubskill(this, subSkillType)) {
            int diff = RankUtils.getSuperAbilityUnlockRequirement(superAbilityType)
                    - getSkillLevel(primarySkillType);

            // Inform the player they are not yet skilled enough.
            NotificationManager.sendPlayerInformation(this, NotificationType.ABILITY_COOLDOWN,
                    "Skills.AbilityGateRequirementFail", String.valueOf(diff),
                    McMMOMod.getSkillTools().getLocalizedSkillName(primarySkillType));
            return;
        }

        int timeRemaining = calculateTimeRemaining(superAbilityType);
        if (timeRemaining > 0) {
            // Axes and Woodcutting share a tool, so their "too tired" message is shown when they act.
            if (primarySkillType == PrimarySkillType.WOODCUTTING
                    || primarySkillType == PrimarySkillType.AXES) {
                NotificationManager.sendPlayerInformation(this, NotificationType.ABILITY_COOLDOWN,
                        "Skills.TooTired", String.valueOf(timeRemaining));
            }
            return;
        }

        // PORT K5: EventUtils.callPlayerAbilityActivateEvent — no singleplayer cancel-listeners; dropped.

        int ticks = calculateAbilityActivationTicks(primarySkillType, superAbilityType);

        if (useChatNotifications()) {
            NotificationManager.sendPlayerInformation(this, NotificationType.SUPER_ABILITY,
                    superAbilityType.getAbilityOn());
        }

        SoundManager.worldSendSound(player, SoundType.ABILITY_ACTIVATED_GENERIC);

        // PORT K3/K4: SkillUtils.removeAbilityBuff(mainHand) for SUPER_BREAKER/GIGA_DRILL_BREAKER —
        // clears a stale haste enchant before re-applying, so boosts don't stack. Needs the
        // item/enchant mutation adapter.

        // Enable the ability.
        profile.setAbilityDATS(superAbilityType,
                System.currentTimeMillis() + ((long) ticks * Misc.TIME_CONVERSION_FACTOR));
        setAbilityMode(superAbilityType, true);

        // PORT K3/K4: SkillUtils.handleAbilitySpeedIncrease(player) for SUPER_BREAKER/GIGA_DRILL_BREAKER
        // — the vanilla dig-speed (haste) boost. The mode is active + gates the effect bodies; the
        // actual mining-speed increase lands with the enchant adapter.

        setToolPreparationMode(tool, false);
        McMMOMod.getScheduler().runLater(new AbilityDisableTask(this, superAbilityType),
                (long) ticks * Misc.TICK_CONVERSION_FACTOR);
    }

    /**
     * Ready the super-ability tool for {@code primarySkillType} on interaction (legacy
     * {@code processAbilityActivation}). No-op while sneaking is required and the player isn't, while
     * ability use is toggled off, or while any super ability is already active. When the matching tool
     * is in hand and not yet prepared, flips tool-preparation mode on, sends the "tool ready" feedback,
     * and schedules the {@link ToolLowerTask} that lowers it after the readiness window.
     */
    public void processAbilityActivation(@NotNull PrimarySkillType primarySkillType) {
        if (McMMOMod.getGeneralConfig().getAbilitiesOnlyActivateWhenSneaking() && !player.isSneaking()) {
            return;
        }

        if (!getAbilityUse()) {
            return;
        }

        for (SuperAbilityType superAbilityType : SuperAbilityType.values()) {
            if (getAbilityMode(superAbilityType)) {
                return;
            }
        }

        SuperAbilityType ability = McMMOMod.getSkillTools().getSuperAbility(primarySkillType);
        ToolType tool = McMMOMod.getSkillTools().getPrimarySkillToolType(primarySkillType);

        /*
         * Woodcutting & Axes share the same tool. That tool always needs to be ready; the cooldown is
         * checked when the player takes action (checkAbilityActivation), not here.
         */
        if (player.isHoldingTool(tool) && !getToolPreparationMode(tool)) {
            if (primarySkillType != PrimarySkillType.WOODCUTTING
                    && primarySkillType != PrimarySkillType.AXES) {
                if (isAbilityOnCooldown(ability)) {
                    NotificationManager.sendPlayerInformation(this, NotificationType.ABILITY_COOLDOWN,
                            "Skills.TooTired", String.valueOf(calculateTimeRemaining(ability)));
                    return;
                }
            }

            if (McMMOMod.getGeneralConfig().getAbilityMessagesEnabled()) {
                if (tool == ToolType.AXE) {
                    processAxeToolMessages();
                } else {
                    NotificationManager.sendPlayerInformation(this, NotificationType.TOOL,
                            tool.getRaiseTool());
                }
                SoundManager.sendSound(player, SoundType.TOOL_READY);
            }

            setToolPreparationMode(tool, true);
            McMMOMod.getScheduler().runLater(new ToolLowerTask(this, tool),
                    4L * Misc.TICK_CONVERSION_FACTOR);
        }
    }

    /**
     * Choose the right "tool ready" message for the shared axe, which readies both Tree Feller
     * (Woodcutting) and Skull Splitter (Axes). Legacy {@code processAxeToolMessages}: when both are on
     * cooldown, or one is while the player looks at a tree, tell them the cooldown; otherwise the
     * generic axe-raise message.
     */
    public void processAxeToolMessages() {
        boolean lookingAtTree = player.isLookingAtTree();

        if (isAbilityOnCooldown(SuperAbilityType.TREE_FELLER)
                && isAbilityOnCooldown(SuperAbilityType.SKULL_SPLITTER)) {
            // Both Tree Feller and Skull Splitter are on cooldown.
            tooTiredMultiple(PrimarySkillType.WOODCUTTING, SubSkillType.WOODCUTTING_TREE_FELLER,
                    SuperAbilityType.TREE_FELLER, SubSkillType.AXES_SKULL_SPLITTER,
                    SuperAbilityType.SKULL_SPLITTER);
        } else if (isAbilityOnCooldown(SuperAbilityType.TREE_FELLER) && lookingAtTree) {
            // Tree Feller on cooldown and the player is looking at a tree.
            raiseToolWithCooldowns(SubSkillType.WOODCUTTING_TREE_FELLER, SuperAbilityType.TREE_FELLER);
        } else if (isAbilityOnCooldown(SuperAbilityType.SKULL_SPLITTER)) {
            raiseToolWithCooldowns(SubSkillType.AXES_SKULL_SPLITTER, SuperAbilityType.SKULL_SPLITTER);
        } else {
            NotificationManager.sendPlayerInformation(this, NotificationType.TOOL,
                    ToolType.AXE.getRaiseTool());
        }
    }

    private void tooTiredMultiple(PrimarySkillType primarySkillType, SubSkillType aSubSkill,
            SuperAbilityType aSuperAbility, SubSkillType bSubSkill,
            SuperAbilityType bSuperAbility) {
        String aSuperAbilityCD = LocaleLoader.getString("Skills.TooTired.Named",
                aSubSkill.getLocaleName(),
                String.valueOf(calculateTimeRemaining(aSuperAbility)));
        String bSuperAbilityCD = LocaleLoader.getString("Skills.TooTired.Named",
                bSubSkill.getLocaleName(),
                String.valueOf(calculateTimeRemaining(bSuperAbility)));
        String allCDStr = aSuperAbilityCD + ", " + bSuperAbilityCD;

        NotificationManager.sendPlayerInformation(this, NotificationType.TOOL,
                "Skills.TooTired.Extra",
                McMMOMod.getSkillTools().getLocalizedSkillName(primarySkillType),
                allCDStr);
    }

    private void raiseToolWithCooldowns(SubSkillType subSkillType,
            SuperAbilityType superAbilityType) {
        NotificationManager.sendPlayerInformation(this, NotificationType.TOOL,
                "Axes.Ability.Ready.Extra",
                subSkillType.getLocaleName(),
                String.valueOf(calculateTimeRemaining(superAbilityType)));
    }

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
