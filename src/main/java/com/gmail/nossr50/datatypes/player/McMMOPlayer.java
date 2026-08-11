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
import com.gmail.nossr50.skills.LimitBreak;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.skills.agility.AgilityManager;
import com.gmail.nossr50.skills.alchemy.AlchemyManager;
import com.gmail.nossr50.skills.archery.ArcheryManager;
import com.gmail.nossr50.skills.axes.AxesManager;
import com.gmail.nossr50.skills.crossbows.CrossbowsManager;
import com.gmail.nossr50.skills.excavation.ExcavationManager;
import com.gmail.nossr50.skills.fishing.FishingManager;
import com.gmail.nossr50.skills.herbalism.HerbalismManager;
import com.gmail.nossr50.skills.cooking.CookingManager;
import com.gmail.nossr50.skills.hunter.HunterManager;
import com.gmail.nossr50.skills.husbandry.HusbandryManager;
import com.gmail.nossr50.skills.mining.MiningManager;
import com.gmail.nossr50.skills.repair.RepairManager;
import com.gmail.nossr50.skills.salvage.SalvageManager;
import com.gmail.nossr50.skills.maces.MacesManager;
import com.gmail.nossr50.skills.smelting.SmeltingManager;
import com.gmail.nossr50.skills.spears.SpearsManager;
import com.gmail.nossr50.skills.stealth.StealthManager;
import com.gmail.nossr50.skills.swords.SwordsManager;
import com.gmail.nossr50.skills.taming.TamingManager;
import com.gmail.nossr50.skills.tridents.TridentsManager;
import com.gmail.nossr50.skills.unarmed.UnarmedManager;
import com.gmail.nossr50.skills.unarmored.UnarmoredManager;
import com.gmail.nossr50.skills.woodcutting.WoodcuttingManager;
import com.gmail.nossr50.util.LogUtils;
import com.gmail.nossr50.util.Misc;
import com.gmail.nossr50.util.experience.ExperienceBarManager;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.player.PlayerLevelUtils;
import com.gmail.nossr50.util.skills.Milestones;
import com.gmail.nossr50.platform.ParticleEffectUtils;
import com.gmail.nossr50.util.skills.PerksUtils;
import com.gmail.nossr50.util.skills.RankUtils;
import com.gmail.nossr50.util.skills.SkillGating;
import com.gmail.nossr50.util.skills.SkillTools;
import com.gmail.nossr50.util.skills.SkillUtils;
import com.gmail.nossr50.util.sounds.SoundManager;
import com.gmail.nossr50.util.sounds.SoundType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
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

    /** How long the off-hand hint stays quiet after being shown. See {@link #claimOffhandBlockedHint}. */
    private static final long OFFHAND_HINT_INTERVAL_MILLIS = 300_000L;

    /**
     * Wall-clock millis before which the "your off hand blocked the ready" hint stays silent.
     * {@code 0} — the initial value — means it has never been shown, so the first blocked ready
     * always speaks.
     */
    private long nextOffhandBlockedHintMillis;

    private boolean isUsingUnarmed;

    /**
     * Time of the player's last respawn, as a UNIX timestamp in <b>seconds</b> (legacy stored the
     * same {@code int} seconds value, and {@link com.gmail.nossr50.util.skills.SkillUtils#cooldownExpired}
     * expects seconds — do not switch this to millis). Read by the post-respawn exploit grace
     * period; see {@link #actualizeRespawnATS()}.
     */
    private int respawnATS;

    // Combat-captured attack-cooldown charge (0.0–1.0) at the moment of the hit that a combat
    // handler is processing. The vanilla attack-cooldown read that fills it lands with the combat
    // pipeline (PORT Phase 10.3+); until then it stays at the "fully charged" default so the
    // damage-math skills (Berserk, Critical Strikes, Rupture odds, …) read a neutral 1.0.
    private float attackStrength = 1.0f;

    /**
     * Lazily-built on-screen XP-bar controller (legacy created it eagerly in the constructor). Held
     * per player; created on the first XP gain that reaches {@link #processPostXpEvent}, so a player
     * who never gains XP — and every headless/unit-test McMMOPlayer — pays nothing for it.
     */
    private ExperienceBarManager experienceBarManager;

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

        // Legacy stamped this from PlayerProfileLoadingTask immediately after constructing the
        // McMMOPlayer, so a fresh login carries the same post-respawn grace period a respawn does.
        actualizeRespawnATS();

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
            case AGILITY -> new AgilityManager(this);
            case ALCHEMY -> new AlchemyManager(this);
            case ARCHERY -> new ArcheryManager(this);
            case AXES -> new AxesManager(this);
            case COOKING -> new CookingManager(this);
            case CROSSBOWS -> new CrossbowsManager(this);
            case EXCAVATION -> new ExcavationManager(this);
            case FISHING -> new FishingManager(this);
            case HERBALISM -> new HerbalismManager(this);
            case HUNTER -> new HunterManager(this);
            case HUSBANDRY -> new HusbandryManager(this);
            case MACES -> new MacesManager(this);
            case MINING -> new MiningManager(this);
            case REPAIR -> new RepairManager(this);
            case SALVAGE -> new SalvageManager(this);
            case SMELTING -> new SmeltingManager(this);
            case SPEARS -> new SpearsManager(this);   // 1.21.11 always has Spears (pinned)
            case STEALTH -> new StealthManager(this);
            case SWORDS -> new SwordsManager(this);
            case TAMING -> new TamingManager(this);
            case TRIDENTS -> new TridentsManager(this);
            case UNARMED -> new UnarmedManager(this);
            case UNARMORED -> new UnarmoredManager(this);
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
     * manager ↔ skill mapping is the commented switch above (AgilityManager↔AGILITY, …).
     */

    public AgilityManager getAgilityManager() {
        return (AgilityManager) skillManagers.get(PrimarySkillType.AGILITY);
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

    public CookingManager getCookingManager() {
        return (CookingManager) skillManagers.get(PrimarySkillType.COOKING);
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

    public HunterManager getHunterManager() {
        return (HunterManager) skillManagers.get(PrimarySkillType.HUNTER);
    }

    public HusbandryManager getHusbandryManager() {
        return (HusbandryManager) skillManagers.get(PrimarySkillType.HUSBANDRY);
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

    public StealthManager getStealthManager() {
        return (StealthManager) skillManagers.get(PrimarySkillType.STEALTH);
    }

    public UnarmoredManager getUnarmoredManager() {
        return (UnarmoredManager) skillManagers.get(PrimarySkillType.UNARMORED);
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

    /**
     * How far through its current level a skill is, as a fraction — what the XP bar fills to.
     *
     * <p><b>A child skill has no XP of its own</b>, so there is no "current level" progress to read
     * off its profile; its level is the mean of its parents'. Its progress is therefore the mean of
     * <em>their</em> progress, which is the only reading that makes the bar move at the rate the
     * skill's own level actually advances. Legacy returned a flat {@code 1.0} here, which was
     * harmless while every child-skill bar was hidden — but Agility became a child skill in Pass 2
     * and shows a bar, and a permanently full bar is worse than no bar at all.
     */
    public double getProgressInCurrentSkillLevel(PrimarySkillType primarySkillType) {
        if (SkillTools.isChildSkill(primarySkillType)) {
            return childSkillProgress(primarySkillType);
        }

        double currentXP = profile.getSkillXpLevel(primarySkillType);
        double maxXP = profile.getXpToLevel(primarySkillType);

        return (currentXP / maxXP);
    }

    /**
     * Mean progress across a child skill's parents.
     *
     * <p>Falls back to a full bar when the skill somehow has no parents — the same answer legacy
     * always gave, so a misconfigured family tree degrades to the old behaviour rather than dividing
     * by zero inside the XP pipeline.
     */
    private double childSkillProgress(PrimarySkillType childSkill) {
        final var parents = McMMOMod.getSkillTools().getChildSkillParents(childSkill);
        if (parents == null || parents.isEmpty()) {
            return 1.0D;
        }
        double total = 0.0D;
        for (PrimarySkillType parent : parents) {
            total += getProgressInCurrentSkillLevel(parent);
        }
        return total / parents.size();
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

        // GitHub #10. ⚠️ This gate is NOT redundant with the one in applyXpGain, and a test proves it:
        // the child-skill split below recurses into *this* method, so a disabled child's gain would be
        // divided among its parents and paid in full before anything reached applyXpGain. Switching
        // Agility off would have quietly redirected its XP into Parkour, Swimming and Flying.
        //
        // Both gates are load-bearing because both methods are public entry points that each own a
        // copy of the child split. This one closes the child route; applyXpGain's closes direct calls.
        if (!SkillGating.isSkillEnabled(skill)) {
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
        // PORT Phase 6: skill permission gate dropped — singleplayer always permits. GitHub #10 put a
        // config gate back in its place. Paired with the one in beginXpGain: that one closes the
        // child-skill split (which recurses into beginXpGain and never arrives here), this one closes
        // every direct call, including the SkillManager-driven awards that land here via
        // beginUnsharedXpGain. Gating XP is also what stops a disabled skill levelling, which is in
        // turn what stops its XP bar and its milestone plaques — those need no separate gate.
        if (!SkillGating.isSkillEnabled(primarySkillType)) {
            return;
        }

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
        //
        // mcMMO's own SelfListener was that event's first subscriber, so its adjustments belong
        // between the (dropped) firing and the application — see applySelfListenerModifiers.
        final float finalXp = applySelfListenerModifiers(primarySkillType, xp, xpGainReason);
        addXp(primarySkillType, finalXp);
        profile.registerXpGain(primarySkillType, finalXp);

        isUsingUnarmed = (primarySkillType == PrimarySkillType.UNARMED);
        checkXp(primarySkillType, xpGainReason, xpGainSource);

        // PORT Phase 11 (now wired): the deferred processPostXpEvent — refresh the on-screen XP bar
        // for the skill that just gained. Only non-child skills reach here (the child branch above
        // returns early after splitting to parents), which is exactly what should show a bar.
        processPostXpEvent(primarySkillType);
    }

    /**
     * Post-XP-gain hook (legacy {@code processPostXpEvent}). Shows/refreshes the on-screen XP bar for
     * {@code primarySkillType} and re-arms its fade timer, so the bar tracks the skill the player is
     * actively training and disappears after the configured lull. A no-op — and it never even builds
     * the {@link ExperienceBarManager} — when XP bars are globally disabled.
     *
     * <p>Legacy also drove the "skill unlocked" notification sweep from here; that rides with the
     * notification subsystem and is out of scope for the XP-bar wiring.
     */
    public void processPostXpEvent(@NotNull PrimarySkillType primarySkillType) {
        // No integrated server ⇒ no client to render a bar to (headless boot, between world sessions,
        // and every MC-free unit test). Same guard PlatformPlayer#grantMilestoneAdvancement uses to
        // stay a no-op outside a live world; it also keeps the XP pipeline from loading boss-bar
        // classes under the MC-free test harness.
        if (McMMOMod.getServer() == null) {
            return;
        }

        // getExperienceConfig() is @Nullable (null between world sessions); no config ⇒ no bar.
        final var experienceConfig = McMMOMod.getExperienceConfig();
        if (experienceConfig == null || !experienceConfig.isExperienceBarsEnabled()) {
            return;
        }
        getExperienceBarManager().updateExperienceBar(primarySkillType);
    }

    /** The player's XP-bar controller, created on first use. */
    public @NotNull ExperienceBarManager getExperienceBarManager() {
        if (experienceBarManager == null) {
            experienceBarManager = new ExperienceBarManager(this);
        }
        return experienceBarManager;
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

        // Milestone-advancement pre-state (Advancement Plaques support): captured before the level-up
        // loop so the awards are computed from the before/after delta. Skipped entirely when the
        // feature is off, so it can never touch the XP path for players who don't want it.
        final boolean milestonesEnabled =
                McMMOMod.getGeneralConfig().getMilestoneAdvancementsEnabled();
        // Rank-unlock plaques need the rank config; if it isn't wired (e.g. a unit test that only
        // exercises the XP pipeline) we still fire level/power/maxed plaques but skip the rank compare,
        // rather than making a plain level-up newly depend on RankConfig being present.
        final boolean rankMilestones = milestonesEnabled && McMMOMod.getRankConfig() != null;
        final int oldPowerLevel = milestonesEnabled ? getPowerLevel() : 0;
        // Levelling a parent also raises every child skill derived from it, so their plaques are
        // snapshotted here too. Without this, Agility's ten sub-skills would never fire a rank plaque
        // again the moment Agility became a child of Parkour/Swimming/Flying — and the failure mode is
        // silence, not an error.
        final List<MilestoneSnapshot> milestoneSnapshots =
                milestonesEnabled ? snapshotMilestones(primarySkillType, rankMilestones) : List.of();

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

            // The milestone firework, every Particles.LevelUp_Tier levels of this skill. Fed the
            // NEW level rather than levelsGained: a single XP award can cross several levels at
            // once, and it is the level reached that decides whether a tier boundary was hit.
            ParticleEffectUtils.playLevelUpEffect(player, profile.getSkillLevel(primarySkillType));

            if (milestonesEnabled) {
                awardMilestoneAdvancements(milestoneSnapshots, oldPowerLevel);
            }
        }

        // PORT Phase 11: the skill-unlock notification sweep and the XP-bar update still ride with
        // the deferred experience-bar subsystem (processPostXpEvent).
    }

    /**
     * One skill's pre-level-up milestone state.
     *
     * @param skill the skill being tracked
     * @param oldLevel its level before the level-up loop
     * @param subSkills its ranked sub-skills, snapshotted for the rank compare (empty when rank
     *                  milestones are unavailable)
     * @param oldRanks each of {@code subSkills}' rank before the loop, in list order
     */
    private record MilestoneSnapshot(PrimarySkillType skill, int oldLevel,
            List<SubSkillType> subSkills, int[] oldRanks) {
    }

    /**
     * Snapshot the milestone state of {@code skill} <em>and of every child skill derived from it</em>.
     *
     * <p>A child skill's level is a function of its parents' levels, so it climbs without ever
     * reaching the XP path itself — {@code applyXpGain} splits a child gain to the parents and
     * returns. Tracking only the skill that literally levelled would therefore mean a child's plaques
     * simply never fire, which is how Agility's ten sub-skill rank plaques would have gone silent when
     * Agility became a child of Parkour/Swimming/Flying. Salvage and Smelting get the same fix for
     * free; they had been quietly missing it all along.
     */
    private List<MilestoneSnapshot> snapshotMilestones(PrimarySkillType skill, boolean rankMilestones) {
        final List<PrimarySkillType> tracked = new ArrayList<>();
        tracked.add(skill);
        for (PrimarySkillType candidate : PrimarySkillType.values()) {
            if (SkillTools.isChildSkill(candidate)
                    && McMMOMod.getSkillTools().getChildSkillParents(candidate).contains(skill)) {
                // GitHub #10: the one plaque route the XP gate does NOT close. A disabled skill stops
                // levelling and therefore stops plaquing for free — but a *child* skill's level is a
                // function of its parents and climbs without any XP of its own ever being applied, so
                // a disabled Agility would keep firing plaques every time an enabled Parkour levelled.
                if (!SkillGating.isSkillEnabled(candidate)) {
                    continue;
                }
                tracked.add(candidate);
            }
        }

        final List<MilestoneSnapshot> snapshots = new ArrayList<>(tracked.size());
        for (PrimarySkillType tracking : tracked) {
            final List<SubSkillType> subSkills =
                    rankMilestones ? rankedSubSkillsOf(tracking) : List.<SubSkillType>of();
            snapshots.add(new MilestoneSnapshot(tracking, profile.getSkillLevel(tracking),
                    subSkills, currentRanks(subSkills)));
        }
        return snapshots;
    }

    /**
     * Grants any milestone advancements (optional <em>Advancement Plaques</em> support) earned by the
     * level-up that just completed. Computes the round-level / skill-maxed / power-tier / rank-unlock
     * awards from the pre-loop snapshots via the Minecraft-free {@link Milestones} core and hands each
     * to the {@link PlatformPlayer} advancement seam. Only called with the feature enabled (see
     * {@code checkXp}).
     *
     * @param snapshots the skill that levelled plus any child skills derived from it
     * @param oldPowerLevel the player's total power level before the level-up loop
     */
    private void awardMilestoneAdvancements(List<MilestoneSnapshot> snapshots, int oldPowerLevel) {
        final int interval = McMMOMod.getGeneralConfig().getMilestoneLevelInterval();
        final List<Milestones.MilestoneAward> awards = new ArrayList<>();

        for (MilestoneSnapshot snapshot : snapshots) {
            final PrimarySkillType skill = snapshot.skill();
            // GeneralConfig (not SkillTools) is the cap authority SkillTools#getLevelCap delegates to,
            // and it is guaranteed wired on the XP path (modifyXpGain already reads it) — so this keeps
            // the milestone hook from depending on SkillTools being wired.
            final int levelCap = McMMOMod.getGeneralConfig().getLevelCap(skill);
            awards.addAll(Milestones.skillLevelAwards(skill, snapshot.oldLevel(),
                    profile.getSkillLevel(skill), levelCap, interval));
            awards.addAll(Milestones.rankAwards(
                    rankChanges(snapshot.subSkills(), snapshot.oldRanks())));
        }
        // Power level is the player's, not a skill's — awarded once however many skills moved.
        awards.addAll(Milestones.powerAwards(oldPowerLevel, getPowerLevel()));

        for (Milestones.MilestoneAward award : awards) {
            player.grantMilestoneAdvancement(award.path(), award.repeatable());
        }
    }

    /**
     * The ranked sub-skills whose parent is {@code skill}; rank-unlock milestones only track these.
     *
     * <p>Limit Break is excluded while it is switched off. It ships off (see {@link LimitBreak}), and
     * "You can now use Swords Limit Break." toasting for a mechanic that adds no damage is the same
     * dead-mechanic-with-a-live-surface defect the eight plaques had before it was implemented at
     * all — a switch that silences the damage but leaves the announcement has not fixed anything.
     * The player who turns it on gets the plaques from that point, which is when they mean something.
     */
    private static List<SubSkillType> rankedSubSkillsOf(PrimarySkillType skill) {
        final List<SubSkillType> out = new ArrayList<>();
        final boolean limitBreakEnabled = LimitBreak.isEnabled();
        for (SubSkillType subSkill : SubSkillType.values()) {
            if (subSkill.getParentSkill() != skill || subSkill.getNumRanks() <= 0) {
                continue;
            }
            if (LimitBreak.isLimitBreak(subSkill) && !limitBreakEnabled) {
                continue;
            }
            out.add(subSkill);
        }
        return out;
    }

    /** Snapshot of this player's current rank in each of {@code subSkills}, in list order. */
    private int[] currentRanks(List<SubSkillType> subSkills) {
        final int[] ranks = new int[subSkills.size()];
        for (int i = 0; i < subSkills.size(); i++) {
            ranks[i] = RankUtils.getRank(this, subSkills.get(i));
        }
        return ranks;
    }

    /**
     * The before/after rank of each of {@code subSkills}, paired against its {@code oldRanks}
     * snapshot. Every tracked sub-skill is returned, climbed or not — {@link Milestones#rankAwards}
     * owns the "did it actually go up?" decision so the rule lives in the MC-free core with the rest
     * of them.
     */
    private List<Milestones.RankChange> rankChanges(List<SubSkillType> subSkills, int[] oldRanks) {
        final List<Milestones.RankChange> changes = new ArrayList<>(subSkills.size());
        for (int i = 0; i < subSkills.size(); i++) {
            changes.add(new Milestones.RankChange(subSkills.get(i), oldRanks[i],
                    RankUtils.getRank(this, subSkills.get(i))));
        }
        return changes;
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
     * The adjustments legacy's own {@code SelfListener#onPlayerXpGain} made to a gain, applied here
     * because the event it listened to is not fired in this port (see {@link #applyXpGain}).
     *
     * <p>Ordering is legacy's and is load-bearing: these run on the <em>already multiplied</em> value
     * — {@link #modifyXpGain} applies the per-skill and global multipliers before the point the event
     * was fired — so the early-game bonus is a flat top-up the global XP rate does not scale.
     *
     * <p>An admin-granted gain ({@link XPGainReason#COMMAND}) is returned untouched, legacy's first
     * check: {@code /addxp 500} must add 500.
     *
     * <p>Diminished returns runs <em>after</em> the early-game boost, on the boosted value, because
     * that is the order legacy's single listener body ran them in.
     *
     * @return the XP that should actually reach the profile
     */
    @VisibleForTesting
    float applySelfListenerModifiers(PrimarySkillType primarySkillType, float xp,
            XPGainReason xpGainReason) {
        if (xpGainReason == XPGainReason.COMMAND) {
            return xp;
        }
        return applyDiminishedReturns(primarySkillType,
                xp + earlyGameBoostBonus(primarySkillType));
    }

    /**
     * The {@code Diminished_Returns} anti-grind throttle: once a skill has earned more than its
     * configured threshold inside the rolling {@code Time_Interval} window, further gains in that
     * skill are scaled down in proportion to how far over the threshold the window total already is,
     * with a guaranteed-minimum floor so a farmed skill still pays <em>something</em>.
     *
     * <p>The rolling totals this reads have been maintained since Phase 11 —
     * {@link PlayerProfile#registerXpGain} records every gain and {@code ClearRegisteredXPGainTask}
     * expires them every 60 ticks — but nothing consulted them until the 2026-08-06 wiring audit
     * (TODO 4(b)). The whole system was one call short.
     *
     * <p>Ships disabled ({@code Diminished_Returns.Enabled: false}, legacy's own default), so this is
     * a no-op until the player turns it on.
     *
     * <p>The threshold is divided by the XP multipliers so it stays denominated in <em>pre-multiplier</em>
     * XP: doubling the global XP rate must not halve how long you may grind before the throttle bites.
     *
     * <p><b>One deliberate deviation from legacy:</b> the {@code modifiedThreshold <= 0 || !finite}
     * guard. Both multipliers it divides by are exposed as ModMenu sliders whose minimum is
     * {@code 0.0}, so a player can drive that expression to {@code 0} or {@code Infinity} from the
     * settings screen; legacy would then divide by it and hand a {@code NaN}/{@code -Infinity} XP
     * value straight to the profile, which persists to disk. A zeroed multiplier means "no XP for
     * this skill" and is not a coherent throttle input, so the throttle steps aside instead.
     *
     * <p>Legacy expressed "reduced to nothing" by cancelling its event, which skipped the profile
     * write entirely; here it is a {@code 0} return. Equivalent — {@link #addXp} with {@code 0} moves
     * no XP and {@link PlayerProfile#registerXpGain} with {@code 0} adds nothing to the rolling total.
     *
     * @return the throttled XP, or {@code xp} unchanged whenever the throttle does not apply
     */
    @VisibleForTesting
    float applyDiminishedReturns(PrimarySkillType primarySkillType, float xp) {
        final var experienceConfig = McMMOMod.getExperienceConfig();
        if (experienceConfig == null || !experienceConfig.getDiminishedReturnsEnabled()) {
            return xp;
        }

        // Legacy: "Don't calculate for XP subtraction". A negative gain scaled by a positive
        // fraction would move *towards* zero, i.e. the throttle would soften a penalty.
        if (xp <= 0) {
            return xp;
        }

        // A child skill's gain is split to its parents before it ever reaches the XP path, so the
        // parents are throttled and the child has no rolling total of its own to compare against.
        // Unreachable via applyXpGain (the split returns early); kept because this method is
        // package-private and legacy carried the same guard.
        if (SkillTools.isChildSkill(primarySkillType)) {
            return xp;
        }

        final int threshold = experienceConfig.getDiminishedReturnsThreshold(primarySkillType);
        if (threshold <= 0) {
            return xp;
        }

        final float modifiedThreshold = (float) (threshold
                / experienceConfig.getFormulaSkillModifier(primarySkillType)
                * experienceConfig.getExperienceGainsGlobalMultiplier());
        if (modifiedThreshold <= 0f || !Float.isFinite(modifiedThreshold)) {
            return xp;
        }

        final float overage =
                (profile.getRegisteredXpGain(primarySkillType) - modifiedThreshold)
                        / modifiedThreshold;
        if (overage <= 0f) {
            return xp;
        }

        final float guaranteedMinimum = experienceConfig.getDiminishedReturnsCap() * xp;
        final float reduced = xp - (xp * overage);

        if (guaranteedMinimum > 0f && reduced <= guaranteedMinimum) {
            return guaranteedMinimum;
        }
        return Math.max(reduced, 0f);
    }

    /**
     * The {@code EarlyGameBoost.Enabled} top-up for a skill still at level 0 — 5% of one level, added
     * to every qualifying gain. Zero when the boost is off, when the skill has passed
     * {@link PlayerLevelUtils#EARLY_GAME_CUTOFF}, or when no experience config is bound (between world
     * sessions, and in MC-free tests that drive {@link #applyXpGain} directly).
     */
    private int earlyGameBoostBonus(PrimarySkillType primarySkillType) {
        final var experienceConfig = McMMOMod.getExperienceConfig();
        if (experienceConfig == null || !experienceConfig.isEarlyGameBoostEnabled()) {
            return 0;
        }
        if (!PlayerLevelUtils.qualifiesForEarlyGameBoost(getSkillLevel(primarySkillType))) {
            return 0;
        }
        return PlayerLevelUtils.earlyGameBonusXp(getXpToLevel(primarySkillType));
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

    /**
     * Whether the "an off-hand item is blocking super-ability readying" hint may be shown now,
     * claiming the slot for {@value #OFFHAND_HINT_INTERVAL_MILLIS} ms if so.
     *
     * <p>⚠️ The throttle is the whole point. {@code SuperAbilityListener}'s off-hand rule is
     * evaluated on <em>every</em> right-click, so a player mining with a torch in the off hand would
     * otherwise get one message per torch placed — turning a warning about a silent mechanic into
     * spam, which players mute, which makes it silent again.
     *
     * <p>Transient, like {@link #getAbilityUse()} and the ability/tool modes beside it: a fresh
     * session should say it once more rather than inherit a stale timer from the last one.
     *
     * @param nowMillis current wall-clock time in millis
     * @return whether the caller may show the hint
     */
    public boolean claimOffhandBlockedHint(long nowMillis) {
        if (nowMillis < nextOffhandBlockedHintMillis) {
            return false;
        }
        nextOffhandBlockedHintMillis = nowMillis + OFFHAND_HINT_INTERVAL_MILLIS;
        return true;
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
     * Exploit prevention
     */

    /**
     * The player's last respawn time, in seconds, for the post-respawn XP grace period. Feed it to
     * {@link com.gmail.nossr50.util.skills.SkillUtils#cooldownExpired} with
     * {@link com.gmail.nossr50.util.Misc#PLAYER_RESPAWN_COOLDOWN_SECONDS}.
     *
     * @return the UNIX timestamp, in seconds, of the last respawn (or of login, if none since)
     */
    public int getRespawnATS() {
        return respawnATS;
    }

    /**
     * Stamp the respawn timestamp to now. Called on login (constructor) and on every respawn
     * ({@code PlayerSessionListener#onRespawn}), matching legacy's two call sites.
     *
     * <p>Seconds, not millis: {@link com.gmail.nossr50.util.skills.SkillUtils#cooldownExpired}
     * multiplies its timestamp by {@link com.gmail.nossr50.util.Misc#TIME_CONVERSION_FACTOR}, so a
     * millisecond value would push the deadline ~31,000 years out and the grace period would never
     * expire.
     */
    public void actualizeRespawnATS() {
        respawnATS = (int) (System.currentTimeMillis() / Misc.TIME_CONVERSION_FACTOR);
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
        // GitHub #10: the legacy getPermissions(player) gate was dropped in Phase 6 as "always granted
        // in singleplayer"; the per-skill master switch is what now stands in its place.
        //
        // Deliberately ABOVE the rank check below rather than leaning on it. The rank gate would also
        // refuse (hasUnlockedSubskill answers false for a disabled skill), but on its way out it tells
        // the player they need N more levels — which is a lie, and an especially confusing one at
        // level 800. Silence is the honest answer: they turned the skill off.
        if (!SkillGating.isSkillEnabled(primarySkillType)) {
            return;
        }

        ToolType tool = McMMOMod.getSkillTools().getPrimarySkillToolType(primarySkillType);
        SuperAbilityType superAbilityType = McMMOMod.getSkillTools().getSuperAbility(primarySkillType);
        SubSkillType subSkillType = superAbilityType.getSubSkillTypeDefinition();

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
        ParticleEffectUtils.playAbilityEnabledEffect(player);

        // If the held tool is still buffed from a prior activation, clear it so Efficiency doesn't stack.
        if (superAbilityType == SuperAbilityType.SUPER_BREAKER
                || superAbilityType == SuperAbilityType.GIGA_DRILL_BREAKER) {
            SkillUtils.removeAbilityBuffFromMainHand(player);
        }

        // Enable the ability.
        profile.setAbilityDATS(superAbilityType,
                System.currentTimeMillis() + ((long) ticks * Misc.TIME_CONVERSION_FACTOR));
        setAbilityMode(superAbilityType, true);

        // The vanilla dig-speed (haste) boost for the breaker abilities.
        if (superAbilityType == SuperAbilityType.SUPER_BREAKER
                || superAbilityType == SuperAbilityType.GIGA_DRILL_BREAKER) {
            SkillUtils.handleAbilitySpeedIncrease(player);
        }

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
        // GitHub #10. Readying is the half of the super-ability flow with no rank gate in it at all,
        // so without this a disabled skill would still announce "You ready your Axe" with the sound
        // every time the player right-clicked — an ability that cannot activate, advertising itself.
        if (!SkillGating.isSkillEnabled(primarySkillType)) {
            return;
        }

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
