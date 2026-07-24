package com.gmail.nossr50.fabric.client.modmenu;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * The curated catalogue of mcMMO config options exposed by the ModMenu config screen.
 *
 * <p>This is intentionally a hand-picked subset of the ~hundreds of keys across mcMMO's dozen YAML
 * files — the settings a singleplayer player is most likely to want to change (XP rates, ability
 * toggles/cooldowns, skill level caps, sounds), spanning {@code config.yml} and {@code
 * experience.yml}. Every {@link ConfigSetting#path()} here must exist in the bundled default of its
 * file with a matching type; {@code McMMOSettingsTest} asserts exactly that, so a typo can never
 * ship as a silent no-op.
 *
 * <p>Minecraft-free by design — the Cloth Config UI reads this list, but the list itself needs no
 * game classes and is fully unit-testable.
 */
public final class McMMOSettings {

    public static final String CONFIG_YML = "config.yml";
    public static final String EXPERIENCE_YML = "experience.yml";

    // Category (Cloth tab) display names.
    private static final String CAT_GENERAL = "General";
    private static final String CAT_XP = "Experience";
    private static final String CAT_XP_SKILL = "XP Multipliers";
    private static final String CAT_ABILITIES = "Abilities";
    private static final String CAT_CAPS = "Skill Level Caps";

    /** Skills that have an {@code Experience_Formula.Skill_Multiplier.<name>} key. */
    private static final String[] XP_MULTIPLIER_SKILLS = {
        "Acrobatics", "Alchemy", "Archery", "Axes", "Crossbows", "Excavation", "Fishing",
        "Herbalism", "Maces", "Mining", "Repair", "Spears", "Swords", "Taming", "Tridents",
        "Unarmed", "Woodcutting"
    };

    /** Skills that have a {@code Skills.<name>.Level_Cap} key. */
    private static final String[] LEVEL_CAP_SKILLS = {
        "Acrobatics", "Alchemy", "Archery", "Axes", "Crossbows", "Excavation", "Fishing",
        "Herbalism", "Maces", "Mining", "Repair", "Salvage", "Smelting", "Spears", "Swords",
        "Taming", "Tridents", "Unarmed", "Woodcutting"
    };

    /** Super-abilities with an {@code Abilities.Cooldowns.<name>} key. */
    private static final String[] COOLDOWN_ABILITIES = {
        "Berserk", "Blast_Mining", "Giga_Drill_Breaker", "Green_Terra", "Serrated_Strikes",
        "Skull_Splitter", "Super_Breaker", "Tree_Feller"
    };

    private static final List<ConfigSetting> ALL = buildCatalogue();

    private McMMOSettings() {
    }

    /** The full, ordered catalogue of editable settings. */
    public static @NotNull List<ConfigSetting> all() {
        return ALL;
    }

    /** Category (tab) names in display order. */
    public static @NotNull List<String> categories() {
        final Set<String> ordered = new LinkedHashSet<>();
        for (ConfigSetting s : ALL) {
            ordered.add(s.category());
        }
        return List.copyOf(ordered);
    }

    /** The settings under one category, in catalogue order. */
    public static @NotNull List<ConfigSetting> byCategory(@NotNull String category) {
        final List<ConfigSetting> out = new ArrayList<>();
        for (ConfigSetting s : ALL) {
            if (s.category().equals(category)) {
                out.add(s);
            }
        }
        return out;
    }

    private static @NotNull List<ConfigSetting> buildCatalogue() {
        final List<ConfigSetting> list = new ArrayList<>();

        // ---- General (config.yml) --------------------------------------------------------------
        list.add(ConfigSetting.bool(CAT_GENERAL, CONFIG_YML, "General.RetroMode.Enabled", true,
                "Retro Mode (1–1000 scaling)",
                "Scales every level requirement/bonus ×10 for the classic mcMMO feel. Changing "
                        + "this on an existing world is disruptive — set it before starting a "
                        + "new world."));
        list.add(ConfigSetting.bool(CAT_GENERAL, CONFIG_YML,
                "General.Level_Up_Chat_Broadcasts.Enabled", true, "Level-Up Chat Broadcasts", null));
        list.add(ConfigSetting.bool(CAT_GENERAL, CONFIG_YML, "General.LevelUp_Sounds", true,
                "Level-Up Sounds", null));
        list.add(ConfigSetting.bool(CAT_GENERAL, CONFIG_YML, "General.Show_Profile_Loaded", false,
                "Show \"Profile Loaded\" Message", null));
        list.add(ConfigSetting.integer(CAT_GENERAL, CONFIG_YML, "General.Save_Interval", 10, 1, 60,
                "Autosave Interval (minutes)", null));
        list.add(ConfigSetting.integer(CAT_GENERAL, CONFIG_YML, "General.Power_Level_Cap", 0, 0,
                100000, "Power Level Cap", "0 disables the power-level cap."));
        list.add(ConfigSetting.bool(CAT_GENERAL, CONFIG_YML,
                "General.Milestone_Advancements.Enabled", true, "Milestone Plaque Advancements",
                "Grant a hidden advancement at skill milestones so the Advancement Plaques mod "
                        + "shows a plaque (or, without it, a normal toast). mcMMO does not require "
                        + "the mod."));
        list.add(ConfigSetting.integer(CAT_GENERAL, CONFIG_YML,
                "General.Milestone_Advancements.Level_Interval", 100, 1, 1000,
                "Milestone Level Interval",
                "A round-level plaque fires each time a skill crosses a multiple of this value."));
        list.add(ConfigSetting.decimal(CAT_GENERAL, CONFIG_YML, "Sounds.MasterVolume", 1.0, 0.0, 1.0,
                "Master Sound Volume", "mcMMO sound volume. 1.0 = full, 0.0 = muted."));
        list.add(ConfigSetting.bool(CAT_GENERAL, CONFIG_YML, "Skills.Fishing.Drops_Enabled", true,
                "Fishing: Treasure Drops", null));
        list.add(ConfigSetting.bool(CAT_GENERAL, CONFIG_YML,
                "Skills.Fishing.Override_Vanilla_Treasures", true,
                "Fishing: Override Vanilla Loot", null));
        list.add(ConfigSetting.bool(CAT_GENERAL, CONFIG_YML, "Skills.Herbalism.Prevent_AFK_Leveling",
                true, "Herbalism: Prevent AFK Leveling",
                "Blocks Herbalism XP from crops harvested while riding (anti-AFK-farm)."));

        // ---- Experience (experience.yml) -------------------------------------------------------
        list.add(ConfigSetting.decimal(CAT_XP, EXPERIENCE_YML, "Experience_Formula.Multiplier.Global",
                1.0, 0.0, 100.0, "Global XP Multiplier",
                "Multiplies XP gained in every skill. 2.0 = double XP, 0.5 = half."));
        list.add(ConfigSetting.bool(CAT_XP, EXPERIENCE_YML, "EarlyGameBoost.Enabled", true,
                "Early Game XP Boost", "Faster leveling at very low skill levels."));
        list.add(ConfigSetting.bool(CAT_XP, EXPERIENCE_YML, "Experience_Formula.Cumulative_Curve",
                false, "Cumulative XP Curve",
                "Level cost scales with total power level instead of per-skill level."));

        // ---- Per-skill XP multipliers (experience.yml) -----------------------------------------
        for (String skill : XP_MULTIPLIER_SKILLS) {
            list.add(ConfigSetting.decimal(CAT_XP_SKILL, EXPERIENCE_YML,
                    "Experience_Formula.Skill_Multiplier." + skill, 1.0, 0.0, 100.0,
                    skill + " XP Multiplier", null));
        }

        // ---- Abilities (config.yml) ------------------------------------------------------------
        list.add(ConfigSetting.bool(CAT_ABILITIES, CONFIG_YML, "Abilities.Enabled", true,
                "Super Abilities Enabled", null));
        list.add(ConfigSetting.bool(CAT_ABILITIES, CONFIG_YML, "Abilities.Messages", true,
                "Ability Messages", null));
        list.add(ConfigSetting.bool(CAT_ABILITIES, CONFIG_YML,
                "Abilities.Activation.Only_Activate_When_Sneaking", false,
                "Only Activate When Sneaking", null));
        list.add(ConfigSetting.integer(CAT_ABILITIES, CONFIG_YML,
                "Abilities.Limits.Tree_Feller_Threshold", 1000, 1, 100000,
                "Tree Feller Block Limit", null));
        list.add(ConfigSetting.integer(CAT_ABILITIES, CONFIG_YML, "Abilities.Tools.Durability_Loss",
                1, 0, 100, "Extra Tool Durability Loss",
                "Extra durability used per block while an ability is active. 0 disables it."));
        for (String ability : COOLDOWN_ABILITIES) {
            final int def = ability.equals("Blast_Mining") ? 60 : 240;
            list.add(ConfigSetting.integer(CAT_ABILITIES, CONFIG_YML,
                    "Abilities.Cooldowns." + ability, def, 0, 6000,
                    ability.replace('_', ' ') + " Cooldown (sec)", null));
        }

        // ---- Skill level caps (config.yml) -----------------------------------------------------
        for (String skill : LEVEL_CAP_SKILLS) {
            list.add(ConfigSetting.integer(CAT_CAPS, CONFIG_YML, "Skills." + skill + ".Level_Cap", 0,
                    0, 100000, skill + " Level Cap", "0 = no cap."));
        }

        return List.copyOf(list);
    }
}
