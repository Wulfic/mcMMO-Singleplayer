package com.gmail.nossr50.config;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.util.text.StringUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * mcMMO's main config ({@code config.yml}), ported onto {@link ConfigLoader} and trimmed to the
 * singleplayer core.
 *
 * <p><b>Port note (singleplayer):</b> the legacy class was ~1080 lines, most of it addressing
 * multiplayer/server-admin surface that the port drops (see {@code CONVERSION_TODO.md} scope
 * reduction). Cut here: MySQL / database purging / DB commands ({@code PoolIdentifier}), the party
 * system + party teleport ({@code PartyFeature}), scoreboards, mob healthbar ({@code
 * MobHealthbarType}), level-up chat broadcasts + admin notifications, SMP item/armor/block/entity
 * mods, flatfile backups, bStats metrics, the update checker, and MOTD / donate / inspect /
 * match-offline cosmetics.
 *
 * <p>Two retargets keep this class MC-free and unit-testable:
 * <ul>
 *   <li>Item/material getters return the raw config <b>String</b> (the vanilla registry path a user
 *       wrote, e.g. {@code "feather"}); Phase 10 resolves it via {@code platform/Materials} at
 *       runtime. Resolving here would need the item registry, which isn't populated until MC
 *       bootstrap (see {@code platform/Materials}).</li>
 *   <li>{@code getCooldown}/{@code getMaxLength} are keyed by the super-ability's config String
 *       until {@code SuperAbilityType} ports; a typed overload gets added then.</li>
 * </ul>
 */
public class GeneralConfig extends ConfigLoader {

    public GeneralConfig(Path dataFolder) {
        super("config.yml", dataFolder);
        loadKeys();
        validateKeys();
    }

    @Override
    protected void loadKeys() {
        // Values are read lazily through the getters; nothing to pre-compute.
    }

    protected boolean validateKeys() {
        final List<String> reason = new ArrayList<>();

        if (getSaveInterval() <= 0) {
            reason.add("General.Save_Interval should be greater than 0!");
        }

        // The four Hardcore / Chimaera Wing range checks that stood here went with their getters
        // and their config.yml sections (2026-08-06). Validating a key nobody ships and nobody
        // reads is how SerratedStrikes.BleedTicks stayed alive for so long: a load-time validator
        // is the one caller that makes a dead getter look reachable.

        if (getLevelUpEffectsTier() < 1) {
            reason.add("Particles.LevelUp_Tier should be at least 1!");
        }

        if (getTreeFellerThreshold() <= 0) {
            reason.add("Abilities.Limits.Tree_Feller_Threshold should be greater than 0!");
        }

        if (getFishingLureModifier() < 0) {
            reason.add("Abilities.Fishing.Lure_Modifier should be at least 0!");
        }

        for (String issue : reason) {
            LOGGER.warn(issue);
        }
        return reason.isEmpty();
    }

    /*
     * GENERAL SETTINGS
     */
    /**
     * Whether to greet the player with {@code Profile.Loading.Success} once their skill data is in
     * memory. Read by {@code PlayerSessionListener#onJoin}.
     *
     * <p>⚠️ <b>The fallback is {@code false} here and {@code true} upstream, deliberately.</b> It
     * must agree with the value {@code config.yml} actually ships ({@code Show_Profile_Loaded:
     * false}), because a fallback that disagrees with the shipped file describes behaviour no player
     * ever sees and quietly becomes the truth for anyone whose config predates the key. Upstream
     * ships the same {@code false} and defaults the getter {@code true}; the disagreement is
     * upstream's, and copying it buys nothing.
     */
    public boolean getShowProfileLoadedMessage() {
        return config.getBoolean("General.Show_Profile_Loaded", false);
    }

    public int getSaveInterval() {
        return config.getInt("General.Save_Interval", 10);
    }

    public boolean getVerboseLoggingEnabled() {
        return config.getBoolean("General.Verbose_Logging", false);
    }

    public boolean useVerboseLogging() {
        return config.getBoolean("General.Verbose_Logging", false);
    }

    public boolean getLevelUpSoundsEnabled() {
        return config.getBoolean("General.LevelUp_Sounds", true);
    }

    /**
     * Whether milestone advancements are granted (the optional <em>Advancement Plaques</em> support).
     * When on, hitting a skill milestone grants a hidden vanilla advancement so Advancement Plaques —
     * or, with no such mod, a plain vanilla toast — celebrates it. Default on; mcMMO carries no
     * dependency on the mod either way.
     */
    public boolean getMilestoneAdvancementsEnabled() {
        return config.getBoolean("General.Milestone_Advancements.Enabled", true);
    }

    /**
     * Bracket size for round-level milestone plaques: a plaque fires each time a skill crosses a
     * multiple of this value (e.g. 100 → 100, 200, 300…). Clamped to at least 1 so the crossing math
     * can never divide by zero. Rank, power-tier and skill-maxed plaques ignore this value.
     */
    public int getMilestoneLevelInterval() {
        return Math.max(1, config.getInt("General.Milestone_Advancements.Level_Interval", 100));
    }

    public boolean getRefreshChunksEnabled() {
        return config.getBoolean("General.Refresh_Chunks", false);
    }

    public boolean getTruncateSkills() {
        return config.getBoolean("General.TruncateSkills", false);
    }

    public boolean isMasterySystemEnabled() {
        return config.getBoolean("General.PowerLevel.Skill_Mastery.Enabled");
    }

    /**
     * Whether RetroMode (1–1000 skill scaling) is enabled. A leveling-scale option, not a
     * multiplayer concept, so it is kept for the singleplayer port; {@code RankConfig} reads it to
     * pick the {@code Standard} vs {@code RetroMode} rank sections in {@code skillranks.yml}.
     */
    public boolean getIsRetroMode() {
        return config.getBoolean("General.RetroMode.Enabled", true);
    }

    /* Level Caps */
    public int getPowerLevelCap() {
        int cap = config.getInt("General.Power_Level_Cap", 0);
        return (cap <= 0) ? Integer.MAX_VALUE : cap;
    }

    public int getLevelCap(PrimarySkillType skill) {
        int cap = config.getInt(
                "Skills." + StringUtils.getCapitalized(skill.toString()) + ".Level_Cap");
        return (cap <= 0) ? Integer.MAX_VALUE : cap;
    }

    /* PVP & PVE Settings */
    public boolean getPVPEnabled(PrimarySkillType skill) {
        return config.getBoolean(
                "Skills." + StringUtils.getCapitalized(skill.toString()) + ".Enabled_For_PVP", true);
    }

    public boolean getPVEEnabled(PrimarySkillType skill) {
        return config.getBoolean(
                "Skills." + StringUtils.getCapitalized(skill.toString()) + ".Enabled_For_PVE", true);
    }

    /*
     * REMOVED (2026-08-06): the Hardcore-mode and Items (Chimaera Wing / Flux Pickaxe) getters.
     *
     * Hardcore stat-loss and vampirism take skill levels from a player when ANOTHER PLAYER kills
     * them -- unreachable with one player. The Chimaera Wing is a multiplayer teleport-to-spawn
     * item. The Flux Pickaxe sound belonged to Flux Mining, a Smelting sub-skill this port never
     * implemented (no SubSkillType, no manager, no listener -- so Particles.Flux went with it).
     *
     * None had a production caller, and their config.yml sections are culled with them. The four
     * Hardcore setters went too: a setter whose getter is dead is dead in both directions.
     */

    /*
     * PARTICLES
     */
    /**
     * ⚠️ Defaults to {@code false} to match the shipped {@code config.yml}, which has always shipped
     * these two off. Upstream's getter defaults to {@code true} and its file to {@code false} — an
     * invisible disagreement while nothing read the key, and a live one now that something does.
     */
    public boolean getAbilityActivationEffectEnabled() {
        return config.getBoolean("Particles.Ability_Activation", false);
    }

    /** @see #getAbilityActivationEffectEnabled() for why this defaults {@code false}. */
    public boolean getAbilityDeactivationEffectEnabled() {
        return config.getBoolean("Particles.Ability_Deactivation", false);
    }

    public boolean getBleedEffectEnabled() {
        return config.getBoolean("Particles.Bleed", true);
    }

    public boolean getCrippleEffectEnabled() {
        return config.getBoolean("Particles.Cripple", true);
    }

    public boolean getDodgeEffectEnabled() {
        return config.getBoolean("Particles.Dodge", true);
    }

    public boolean getGreaterImpactEffectEnabled() {
        return config.getBoolean("Particles.Greater_Impact", true);
    }

    public boolean getCallOfTheWildEffectEnabled() {
        return config.getBoolean("Particles.Call_of_the_Wild", true);
    }

    public boolean getLevelUpEffectsEnabled() {
        return config.getBoolean("Particles.LevelUp_Enabled", true);
    }

    public int getLevelUpEffectsTier() {
        return config.getInt("Particles.LevelUp_Tier", 100);
    }

    /**
     * Whether mcMMO's celebratory fireworks burst as a large ball or a small one.
     *
     * <p>⚠️ This getter is <b>new in the port</b>. {@code Particles.LargeFireworks} has shipped in
     * {@code config.yml} since forever with no getter anywhere in the codebase — upstream reads it
     * only from inside {@code ParticleEffectUtils#fireworkParticleShower}, which is commented out.
     * The 2026-08-06 audit's getter→caller sweep could not see it, exactly like
     * {@code Level_Up_Chat_Broadcasts.Enabled} in item 1.3: <b>a shipped key with no getter at all is
     * invisible to a sweep that starts from getters.</b>
     */
    public boolean getLargeFireworks() {
        return config.getBoolean("Particles.LargeFireworks", true);
    }

    /*
     * ABILITY SETTINGS
     */
    public boolean getUrlLinksEnabled() {
        return config.getBoolean("Commands.Skills.URL_Links");
    }

    public boolean getAbilityMessagesEnabled() {
        return config.getBoolean("Abilities.Messages", true);
    }

    public boolean getAbilitiesEnabled() {
        return config.getBoolean("Abilities.Enabled", true);
    }

    public boolean getAbilitiesOnlyActivateWhenSneaking() {
        return config.getBoolean("Abilities.Activation.Only_Activate_When_Sneaking", false);
    }

    /**
     * Whether an item in the <b>off hand</b> suppresses super-ability readying — legacy
     * {@code PlayerListener} L872-875 ({@code RIGHT_CLICK_BLOCK}) and L952-955
     * ({@code RIGHT_CLICK_AIR}), whose rule is "off hand not empty, not in a vehicle, not sneaking
     * ⇒ skip the whole arm".
     *
     * <p>⚠️ <b>Ships {@code false}, deliberately diverging from upstream.</b> Readying is step 1 of
     * 2 and {@code checkAbilityActivation} is only reachable through
     * {@code getToolPreparationMode(tool)}, so this one condition switches off <em>every</em> super
     * ability in the mod at once — with no message and no sound. A torch in the off hand is the
     * canonical mining loadout, so upstream's rule silently disables the feature for precisely the
     * player who uses it most (found live 2026-08-06: 33 torches in the off hand, zero super-ability
     * activations for four days).
     *
     * <p>{@code false} is safe as a getter default as well as a shipped one: this key is new, so a
     * config file written before it existed has no entry and must behave the new way rather than
     * inherit the old rule by omission.
     */
    public boolean getOffhandBlocksReadying() {
        return config.getBoolean("Abilities.Activation.Offhand_Blocks_Readying", false);
    }

    public boolean getAbilitiesGateEnabled() {
        return config.getBoolean("Abilities.Activation.Level_Gate_Abilities");
    }

    /** Cooldown for a super ability, keyed by its config String. */
    public int getCooldown(String superAbility) {
        return config.getInt("Abilities.Cooldowns." + superAbility);
    }

    /** Max duration for a super ability, keyed by its config String. */
    public int getMaxLength(String superAbility) {
        return config.getInt("Abilities.Max_Seconds." + superAbility);
    }

    /**
     * Cooldown for a super ability. {@link SuperAbilityType#toString()} yields the PascalCase
     * config key (e.g. {@code Super_Breaker}), matching {@link #getCooldown(String)}.
     */
    public int getCooldown(SuperAbilityType ability) {
        return getCooldown(ability.toString());
    }

    /** Max duration for a super ability, keyed by {@link SuperAbilityType#toString()}. */
    public int getMaxLength(SuperAbilityType ability) {
        return getMaxLength(ability.toString());
    }

    public int getAbilityToolDamage() {
        return config.getInt("Abilities.Tools.Durability_Loss", 1);
    }

    public int getTreeFellerThreshold() {
        return config.getInt("Abilities.Limits.Tree_Feller_Threshold", 1000);
    }

    /*
     * SKILL SETTINGS
     *
     * Bonus-drop / green-thumb lookups are keyed by the config-material String (see
     * ConfigStringUtils) that callers derive from a registry path.
     */
    public boolean getDoubleDropsEnabled(PrimarySkillType skill, String materialConfigString) {
        // Temporary measure to fix an exploit caused by a Spigot bug (legacy note, kept for parity).
        if (materialConfigString.equalsIgnoreCase("Lily_Pad")) {
            return false;
        }
        return config.getBoolean(
                "Bonus_Drops." + StringUtils.getCapitalized(skill.toString()) + "."
                        + materialConfigString);
    }

    public boolean getDoubleDropsDisabled(PrimarySkillType skill) {
        final String skillName = StringUtils.getCapitalized(skill.toString());
        final YamlConfiguration section = config.getConfigurationSection("Bonus_Drops." + skillName);
        if (section == null) {
            return false;
        }
        boolean disabled = true;
        for (String key : section.getKeys(false)) {
            if (config.getBoolean("Bonus_Drops." + skillName + "." + key)) {
                disabled = false;
                break;
            }
        }
        return disabled;
    }

    /**
     * Power Cook: the status effect a cooked or crafted food grants when eaten, as the raw config
     * String (a registry name such as {@code STRENGTH}, or a legacy Bukkit alias).
     *
     * <p><b>Returned as a String on purpose.</b> This class is deliberately MC-free — see the class
     * note on why item getters hand back the raw config value — and the status-effect registry is
     * not populated when configs load. {@code PotionUtil#matchEffect} resolves it at the call site,
     * on the eat seam, where the registry is live.
     *
     * <p>⚠️ Keyed on the <b>Config_String</b> form ({@code Cooked_Beef}), matching
     * {@code Bonus_Drops.Cooking} and {@code Experience_Values.Cooking} rather than the lowercase
     * registry path. One skill, one key style, one file.
     *
     * @param foodConfigString the eaten food's config string
     * @return the configured effect name, or {@code null} when the food grants nothing
     */
    public @Nullable String getPowerCookEffect(@NotNull String foodConfigString) {
        return config.getString("Skills.Cooking.Power_Cook_Effects." + foodConfigString);
    }

    public boolean getWoodcuttingDoubleDropsEnabled(String materialConfigString) {
        return config.getBoolean("Bonus_Drops.Woodcutting." + materialConfigString);
    }

    public boolean isGreenThumbReplantableCrop(String materialConfigString) {
        return config.getBoolean("Green_Thumb_Replanting_Crops." + materialConfigString, true);
    }

    /* Axes */
    public int getAxesGate() {
        return config.getInt("Skills.Axes.Ability_Activation_Level_Gate", 10);
    }

    /* Agility */
    public boolean getDodgeLightningDisabled() {
        return config.getBoolean("Skills.Agility.Prevent_Dodge_Lightning", false);
    }

    public int getXPAfterTeleportCooldown() {
        return config.getInt("Skills.Agility.XP_After_Teleport_Cooldown", 5);
    }

    /**
     * The item that triggers Agility's Second Wind super ability on right-click (never consumed).
     * Named Bukkit-style or as a namespaced id; resolved through
     * {@link com.gmail.nossr50.platform.Materials}, so an unknown name simply never triggers rather
     * than crashing.
     */
    public String getSecondWindItem() {
        return config.getString("Skills.Agility.Second_Wind_Item", "FEATHER");
    }

    /**
     * The item that triggers Stealth's Smoke Bomb super ability on right-click (never consumed).
     *
     * <p>Must differ from {@link #getSecondWindItem()}: both actives listen on the same use-item
     * event, so a shared item would activate whichever gate passes and print the other's refusal
     * message alongside it.
     */
    public String getSmokeBombItem() {
        return config.getString("Skills.Stealth.Smoke_Bomb_Item", "GUNPOWDER");
    }

    /**
     * The item that triggers Husbandry's Herdsman's Call on right-click; never consumed.
     *
     * <p>Must differ from {@link #getSecondWindItem()} and {@link #getSmokeBombItem()} — all three
     * tool-free actives listen on the same {@code UseItemCallback}, so a shared item activates one and
     * prints another's refusal message. {@code HerdsmansCallListenerTest} pins the three apart.
     */
    public String getHerdsmansCallItem() {
        return config.getString("Skills.Husbandry.Herdsmans_Call_Item", "GOAT_HORN");
    }

    /* Alchemy */
    public boolean getEnabledForHoppers() {
        return config.getBoolean("Skills.Alchemy.Enabled_for_Hoppers", true);
    }

    public boolean getPreventHopperTransferIngredients() {
        return config.getBoolean("Skills.Alchemy.Prevent_Hopper_Transfer_Ingredients", false);
    }

    public boolean getPreventHopperTransferBottles() {
        return config.getBoolean("Skills.Alchemy.Prevent_Hopper_Transfer_Bottles", false);
    }

    /* Fishing */
    public boolean getFishingDropsEnabled() {
        return config.getBoolean("Skills.Fishing.Drops_Enabled", true);
    }

    public boolean getFishingOverrideTreasures() {
        return config.getBoolean("Skills.Fishing.Override_Vanilla_Treasures", true);
    }

    public boolean getFishingExtraFish() {
        return config.getBoolean("Skills.Fishing.Extra_Fish", true);
    }

    public double getFishingLureModifier() {
        return config.getDouble("Skills.Fishing.Lure_Modifier", 4.0D);
    }

    /* Mining */
    public String getDetonatorItemName() {
        return config.getString("Skills.Mining.Detonator_Name", "FLINT_AND_STEEL");
    }

    /* Excavation */
    public int getExcavationGate() {
        return config.getInt("Skills.Excavation.Ability_Activation_Level_Gate", 10);
    }

    /* Repair */
    public boolean getRepairAnvilMessagesEnabled() {
        return config.getBoolean("Skills.Repair.Anvil_Messages", true);
    }

    public boolean getRepairAnvilPlaceSoundsEnabled() {
        return config.getBoolean("Skills.Repair.Anvil_Placed_Sounds", true);
    }

    public boolean getRepairAnvilUseSoundsEnabled() {
        return config.getBoolean("Skills.Repair.Anvil_Use_Sounds", true);
    }

    public String getRepairAnvilMaterialName() {
        return config.getString("Skills.Repair.Anvil_Material", "IRON_BLOCK");
    }

    public boolean getRepairConfirmRequired() {
        return config.getBoolean("Skills.Repair.Confirm_Required", true);
    }

    /**
     * ⚠️ <b>Deliberate divergence from legacy.</b> Upstream
     * ({@code legacy/…/config/GeneralConfig.java:836-841}) has these two getters reading
     * <em>each other's</em> keys, and this port copied the swap verbatim. It is invisible upstream
     * and here because neither getter has a caller and both keys default to {@code false} — the
     * exact shape as issue #9's {@code Damage_Limit} / {@code HP_Modifier_Limit} pair.
     *
     * <p>Fixed rather than kept faithful: faithfulness to a dead getter's <em>bug</em> buys nothing,
     * and the moment either one is wired to a repair listener it silently honours the wrong switch.
     * {@code GeneralConfigTest#vanillaRepairGettersReadTheirOwnKeys} pins both, in both directions.
     * Do not "restore" these to match legacy.
     */
    public boolean getAllowVanillaInventoryRepair() {
        return config.getBoolean("Skills.Repair.Allow_Vanilla_Inventory_Repair", false);
    }

    /** @see #getAllowVanillaInventoryRepair() for why this diverges from legacy. */
    public boolean getAllowVanillaAnvilRepair() {
        return config.getBoolean("Skills.Repair.Allow_Vanilla_Anvil_Repair", false);
    }

    public boolean getAllowVanillaGrindstoneRepair() {
        return config.getBoolean("Skills.Repair.Allow_Vanilla_Grindstone_Repair", false);
    }

    /* Salvage */
    public boolean getSalvageAnvilMessagesEnabled() {
        return config.getBoolean("Skills.Salvage.Anvil_Messages", true);
    }

    public boolean getSalvageAnvilPlaceSoundsEnabled() {
        return config.getBoolean("Skills.Salvage.Anvil_Placed_Sounds", true);
    }

    public boolean getSalvageAnvilUseSoundsEnabled() {
        return config.getBoolean("Skills.Salvage.Anvil_Use_Sounds", true);
    }

    public String getSalvageAnvilMaterialName() {
        return config.getString("Skills.Salvage.Anvil_Material", "GOLD_BLOCK");
    }

    public boolean getSalvageConfirmRequired() {
        return config.getBoolean("Skills.Salvage.Confirm_Required", true);
    }

    /* Unarmed */
    public boolean isBlockCrackerAllowed() {
        return config.getBoolean("Skills.Unarmed.Block_Cracker.Allow_Block_Cracker", true);
    }

    public boolean getUnarmedItemPickupDisabled() {
        return config.getBoolean("Skills.Unarmed.Item_Pickup_Disabled_Full_Inventory", true);
    }

    public boolean getUnarmedItemsAsUnarmed() {
        return config.getBoolean("Skills.Unarmed.Items_As_Unarmed", false);
    }

    public int getUnarmedGate() {
        return config.getInt("Skills.Unarmed.Ability_Activation_Level_Gate", 10);
    }

    /* Swords */
    public int getSwordsGate() {
        return config.getInt("Skills.Swords.Ability_Activation_Level_Gate", 10);
    }

    /* Taming — Call of the Wild, keyed by config-entity String. */
    public String getTamingCOTWMaterialName(String cotwEntity) {
        return config.getString("Skills.Taming.Call_Of_The_Wild." + cotwEntity + ".Item_Material");
    }

    public int getTamingCOTWCost(String cotwEntity) {
        return config.getInt("Skills.Taming.Call_Of_The_Wild." + cotwEntity + ".Item_Amount");
    }

    public int getTamingCOTWAmount(String cotwEntity) {
        return config.getInt("Skills.Taming.Call_Of_The_Wild." + cotwEntity + ".Summon_Amount");
    }

    public int getTamingCOTWLength(String cotwEntity) {
        return config.getInt("Skills.Taming.Call_Of_The_Wild." + cotwEntity + ".Summon_Length");
    }

    public int getTamingCOTWMaxAmount(String cotwEntity) {
        return config.getInt(
                "Skills.Taming.Call_Of_The_Wild." + cotwEntity + ".Per_Player_Limit", 1);
    }

    /**
     * Whether tamed pets are pulled along when their owner makes a long jump inside one world
     * (GitHub #2). Off restores vanilla behaviour exactly.
     */
    public boolean arePetsFollowingTeleports() {
        return config.getBoolean("Skills.Taming.Pets_Follow_Teleport", true);
    }

    /**
     * How far from the departure point a pet may be and still be brought along, in blocks. See
     * {@code fabric.listeners.PetFollowTeleport} for why this is a radius rather than "every pet you
     * own".
     *
     * <p>⚠️ <b>32 → 128 (GitHub #12).</b> Changing this literal alone reaches nobody: it is
     * {@code ConfigRetunes} that carries the new value onto a {@code config.yml} that already exists,
     * and the two must be edited together or returning players keep 32 forever.
     */
    public double getPetFollowTeleportRadius() {
        return config.getDouble("Skills.Taming.Pets_Follow_Teleport_Radius", 128.0D);
    }

    /* Woodcutting */
    public boolean getTreeFellerSoundsEnabled() {
        return config.getBoolean("Skills.Woodcutting.Tree_Feller_Sounds", true);
    }

    public int getWoodcuttingGate() {
        return config.getInt("Skills.Woodcutting.Ability_Activation_Level_Gate", 10);
    }

    /* Herbalism */
    public boolean getHerbalismPreventAFK() {
        return config.getBoolean("Skills.Herbalism.Prevent_AFK_Leveling", true);
    }
}
