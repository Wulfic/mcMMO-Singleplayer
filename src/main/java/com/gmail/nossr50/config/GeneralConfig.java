package com.gmail.nossr50.config;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.util.text.StringUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

        if (getHardcoreDeathStatPenaltyPercentage() < 0.01
                || getHardcoreDeathStatPenaltyPercentage() > 100) {
            reason.add(
                    "Hardcore.Death_Stat_Loss.Penalty_Percentage only accepts values from 0.01 to 100!");
        }

        if (getHardcoreVampirismStatLeechPercentage() < 0.01
                || getHardcoreVampirismStatLeechPercentage() > 100) {
            reason.add("Hardcore.Vampirism.Leech_Percentage only accepts values from 0.01 to 100!");
        }

        if (getChimaeraUseCost() < 1 || getChimaeraUseCost() > 64) {
            reason.add("Items.Chimaera_Wing.Use_Cost only accepts values from 1 to 64!");
        }

        if (getChimaeraRecipeCost() < 1 || getChimaeraRecipeCost() > 9) {
            reason.add("Items.Chimaera_Wing.Recipe_Cost only accepts values from 1 to 9!");
        }

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
    public boolean getShowProfileLoadedMessage() {
        return config.getBoolean("General.Show_Profile_Loaded", true);
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

    public boolean getRefreshChunksEnabled() {
        return config.getBoolean("General.Refresh_Chunks", false);
    }

    public boolean getTruncateSkills() {
        return config.getBoolean("General.TruncateSkills", false);
    }

    public boolean isMasterySystemEnabled() {
        return config.getBoolean("General.PowerLevel.Skill_Mastery.Enabled");
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

    /* Hardcore Mode */
    public boolean getHardcoreStatLossEnabled(PrimarySkillType primarySkillType) {
        return config.getBoolean("Hardcore.Death_Stat_Loss.Enabled."
                + StringUtils.getCapitalized(primarySkillType.toString()), false);
    }

    public void setHardcoreStatLossEnabled(PrimarySkillType primarySkillType, boolean enabled) {
        config.set("Hardcore.Death_Stat_Loss.Enabled."
                + StringUtils.getCapitalized(primarySkillType.toString()), enabled);
    }

    public double getHardcoreDeathStatPenaltyPercentage() {
        return config.getDouble("Hardcore.Death_Stat_Loss.Penalty_Percentage", 75.0D);
    }

    public void setHardcoreDeathStatPenaltyPercentage(double value) {
        config.set("Hardcore.Death_Stat_Loss.Penalty_Percentage", value);
    }

    public int getHardcoreDeathStatPenaltyLevelThreshold() {
        return config.getInt("Hardcore.Death_Stat_Loss.Level_Threshold", 0);
    }

    public boolean getHardcoreVampirismEnabled(PrimarySkillType primarySkillType) {
        return config.getBoolean("Hardcore.Vampirism.Enabled."
                + StringUtils.getCapitalized(primarySkillType.toString()), false);
    }

    public void setHardcoreVampirismEnabled(PrimarySkillType primarySkillType, boolean enabled) {
        config.set("Hardcore.Vampirism.Enabled."
                + StringUtils.getCapitalized(primarySkillType.toString()), enabled);
    }

    public double getHardcoreVampirismStatLeechPercentage() {
        return config.getDouble("Hardcore.Vampirism.Leech_Percentage", 5.0D);
    }

    public void setHardcoreVampirismStatLeechPercentage(double value) {
        config.set("Hardcore.Vampirism.Leech_Percentage", value);
    }

    public int getHardcoreVampirismLevelThreshold() {
        return config.getInt("Hardcore.Vampirism.Level_Threshold", 0);
    }

    /*
     * ITEMS
     *
     * Material getters return the raw config name (registry path); resolve via platform/Materials.
     */
    public int getChimaeraUseCost() {
        return config.getInt("Items.Chimaera_Wing.Use_Cost", 1);
    }

    public int getChimaeraRecipeCost() {
        return config.getInt("Items.Chimaera_Wing.Recipe_Cost", 5);
    }

    public String getChimaeraItemName() {
        return config.getString("Items.Chimaera_Wing.Item_Name", "Feather");
    }

    public boolean getChimaeraEnabled() {
        return config.getBoolean("Items.Chimaera_Wing.Enabled", true);
    }

    public boolean getChimaeraPreventUseUnderground() {
        return config.getBoolean("Items.Chimaera_Wing.Prevent_Use_Underground", true);
    }

    public boolean getChimaeraUseBedSpawn() {
        return config.getBoolean("Items.Chimaera_Wing.Use_Bed_Spawn", true);
    }

    public int getChimaeraCooldown() {
        return config.getInt("Items.Chimaera_Wing.Cooldown", 240);
    }

    public int getChimaeraWarmup() {
        return config.getInt("Items.Chimaera_Wing.Warmup", 5);
    }

    public int getChimaeraRecentlyHurtCooldown() {
        return config.getInt("Items.Chimaera_Wing.RecentlyHurt_Cooldown", 60);
    }

    public boolean getChimaeraSoundEnabled() {
        return config.getBoolean("Items.Chimaera_Wing.Sound_Enabled", true);
    }

    public boolean getFluxPickaxeSoundEnabled() {
        return config.getBoolean("Items.Flux_Pickaxe.Sound_Enabled", true);
    }

    /*
     * PARTICLES
     */
    public boolean getAbilityActivationEffectEnabled() {
        return config.getBoolean("Particles.Ability_Activation", true);
    }

    public boolean getAbilityDeactivationEffectEnabled() {
        return config.getBoolean("Particles.Ability_Deactivation", true);
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

    public boolean getFluxEffectEnabled() {
        return config.getBoolean("Particles.Flux", true);
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

    public boolean getAbilitiesGateEnabled() {
        return config.getBoolean("Abilities.Activation.Level_Gate_Abilities");
    }

    /** Cooldown for a super ability, keyed by its config String (pending {@code SuperAbilityType}). */
    public int getCooldown(String superAbility) {
        return config.getInt("Abilities.Cooldowns." + superAbility);
    }

    /** Max duration for a super ability, keyed by its config String (pending {@code SuperAbilityType}). */
    public int getMaxLength(String superAbility) {
        return config.getInt("Abilities.Max_Seconds." + superAbility);
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

    /* Acrobatics */
    public boolean getDodgeLightningDisabled() {
        return config.getBoolean("Skills.Acrobatics.Prevent_Dodge_Lightning", false);
    }

    public int getXPAfterTeleportCooldown() {
        return config.getInt("Skills.Acrobatics.XP_After_Teleport_Cooldown", 5);
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

    public boolean getAllowVanillaInventoryRepair() {
        return config.getBoolean("Skills.Repair.Allow_Vanilla_Anvil_Repair", false);
    }

    public boolean getAllowVanillaAnvilRepair() {
        return config.getBoolean("Skills.Repair.Allow_Vanilla_Inventory_Repair", false);
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
