package com.gmail.nossr50.datatypes.skills;

import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.util.text.StringUtils;

public enum SuperAbilityType {
    EXPLOSIVE_SHOT("Archery.Skills.ExplosiveShot.On",
            "Archery.Skills.ExplosiveShot.Off",
            "Archery.Skills.ExplosiveShot.Other.On",
            "Archery.Skills.ExplosiveShot.Refresh",
            "Archery.Skills.ExplosiveShot.Other.Off",
            "Archery.SubSkill.ExplosiveShot.Name"),
    BERSERK(
            "Unarmed.Skills.Berserk.On",
            "Unarmed.Skills.Berserk.Off",
            "Unarmed.Skills.Berserk.Other.On",
            "Unarmed.Skills.Berserk.Refresh",
            "Unarmed.Skills.Berserk.Other.Off",
            "Unarmed.SubSkill.Berserk.Name"),

    SUPER_BREAKER(
            "Mining.Skills.SuperBreaker.On",
            "Mining.Skills.SuperBreaker.Off",
            "Mining.Skills.SuperBreaker.Other.On",
            "Mining.Skills.SuperBreaker.Refresh",
            "Mining.Skills.SuperBreaker.Other.Off",
            "Mining.SubSkill.SuperBreaker.Name"),

    GIGA_DRILL_BREAKER(
            "Excavation.Skills.GigaDrillBreaker.On",
            "Excavation.Skills.GigaDrillBreaker.Off",
            "Excavation.Skills.GigaDrillBreaker.Other.On",
            "Excavation.Skills.GigaDrillBreaker.Refresh",
            "Excavation.Skills.GigaDrillBreaker.Other.Off",
            "Excavation.SubSkill.GigaDrillBreaker.Name"),

    GREEN_TERRA(
            "Herbalism.Skills.GTe.On",
            "Herbalism.Skills.GTe.Off",
            "Herbalism.Skills.GTe.Other.On",
            "Herbalism.Skills.GTe.Refresh",
            "Herbalism.Skills.GTe.Other.Off",
            "Herbalism.SubSkill.GreenTerra.Name"),

    SKULL_SPLITTER(
            "Axes.Skills.SS.On",
            "Axes.Skills.SS.Off",
            "Axes.Skills.SS.Other.On",
            "Axes.Skills.SS.Refresh",
            "Axes.Skills.SS.Other.Off",
            "Axes.SubSkill.SkullSplitter.Name"),

    TREE_FELLER(
            "Woodcutting.Skills.TreeFeller.On",
            "Woodcutting.Skills.TreeFeller.Off",
            "Woodcutting.Skills.TreeFeller.Other.On",
            "Woodcutting.Skills.TreeFeller.Refresh",
            "Woodcutting.Skills.TreeFeller.Other.Off",
            "Woodcutting.SubSkill.TreeFeller.Name"),

    SERRATED_STRIKES(
            "Swords.Skills.SS.On",
            "Swords.Skills.SS.Off",
            "Swords.Skills.SS.Other.On",
            "Swords.Skills.SS.Refresh",
            "Swords.Skills.SS.Other.Off",
            "Swords.SubSkill.SerratedStrikes.Name"),
    SUPER_SHOTGUN(
            "Placeholder",
            "Placeholder",
            "Placeholder",
            "Placeholder",
            "Placeholder",
            "Placeholder"),
    TRIDENTS_SUPER_ABILITY(
            "Placeholder",
            "Placeholder",
            "Placeholder",
            "Placeholder",
            "Placeholder",
            "Placeholder"),
    MACES_SUPER_ABILITY(
            "Placeholder",
            "Placeholder",
            "Placeholder",
            "Placeholder",
            "Placeholder",
            "Placeholder"),
    SPEARS_SUPER_ABILITY(
            "Placeholder",
            "Placeholder",
            "Placeholder",
            "Placeholder",
            "Placeholder",
            "Placeholder"),

    /**
     * Has cooldown - but has to share a skill with Super Breaker, so needs special treatment
     */
    BLAST_MINING(
            null,
            null,
            "Mining.Blast.Other.On",
            "Mining.Blast.Refresh",
            null,
            "Mining.SubSkill.BlastMining.Name"),
    ;

    /*
     * Defining their associated SubSkillType definitions
     * This is a bit of a band-aid fix until the new skill system is in place
     */
    // TODO: This is stupid
    static {
        BERSERK.subSkillTypeDefinition = SubSkillType.UNARMED_BERSERK;
        SUPER_BREAKER.subSkillTypeDefinition = SubSkillType.MINING_SUPER_BREAKER;
        GIGA_DRILL_BREAKER.subSkillTypeDefinition = SubSkillType.EXCAVATION_GIGA_DRILL_BREAKER;
        GREEN_TERRA.subSkillTypeDefinition = SubSkillType.HERBALISM_GREEN_TERRA;
        SKULL_SPLITTER.subSkillTypeDefinition = SubSkillType.AXES_SKULL_SPLITTER;
        TREE_FELLER.subSkillTypeDefinition = SubSkillType.WOODCUTTING_TREE_FELLER;
        SERRATED_STRIKES.subSkillTypeDefinition = SubSkillType.SWORDS_SERRATED_STRIKES;
        BLAST_MINING.subSkillTypeDefinition = SubSkillType.MINING_BLAST_MINING;
    }

    private final String abilityOn;
    private final String abilityOff;
    private final String abilityPlayer;
    private final String abilityRefresh;
    private final String abilityPlayerOff;
    private SubSkillType subSkillTypeDefinition;
    private final String localizedName;

    SuperAbilityType(String abilityOn, String abilityOff, String abilityPlayer,
            String abilityRefresh, String abilityPlayerOff, String localizedName) {
        this.abilityOn = abilityOn;
        this.abilityOff = abilityOff;
        this.abilityPlayer = abilityPlayer;
        this.abilityRefresh = abilityRefresh;
        this.abilityPlayerOff = abilityPlayerOff;
        this.localizedName = localizedName;
    }

    public int getCooldown() {
        return McMMOMod.getSkillTools().getSuperAbilityCooldown(this);
    }

    public int getMaxLength() {
        return McMMOMod.getGeneralConfig().getMaxLength(this);
    }

    public String getAbilityOn() {
        return abilityOn;
    }

    public String getAbilityOff() {
        return abilityOff;
    }

    public String getAbilityPlayer() {
        return abilityPlayer;
    }

    public String getAbilityPlayerOff() {
        return abilityPlayerOff;
    }

    public String getAbilityRefresh() {
        return abilityRefresh;
    }

    public String getName() {
        // Legacy used StringUtils.getPrettySuperAbilityString(this); the ported StringUtils
        // keeps only the String-based getPrettyString. name() ("SUPER_BREAKER") prettifies
        // identically ("Super Breaker").
        return StringUtils.getPrettyString(name());
    }

    public String getLocalizedName() {
        return LocaleLoader.getString(localizedName);
    }

    @Override
    public String toString() {
        String baseString = name();
        String[] substrings = baseString.split("_");
        String formattedString = "";

        int size = 1;

        for (String string : substrings) {
            formattedString = formattedString.concat(StringUtils.getCapitalized(string));

            if (size < substrings.length) {
                formattedString = formattedString.concat("_");
            }

            size++;
        }

        return formattedString;
    }

    // PORT Phase 6/10: getPermissions(Player) — dropped here. In singleplayer permission
    // checks collapse to op-level/config/always-allow (Phase 6); the Bukkit Player + Permissions
    // dependency is re-added against the platform/ player adapter when abilities port.

    // PORT Phase 10: blockCheck(Block) — dropped here. Needs BlockUtils + the material map
    // store, which land with the Mining/Excavation/Herbalism/Woodcutting skills against the
    // platform/ block adapter.

    /**
     * Grabs the associated SubSkillType definition for this SuperAbilityType
     *
     * @return the matching SubSkillType definition for this SuperAbilityType
     */
    public SubSkillType getSubSkillTypeDefinition() {
        return subSkillTypeDefinition;
    }
}
