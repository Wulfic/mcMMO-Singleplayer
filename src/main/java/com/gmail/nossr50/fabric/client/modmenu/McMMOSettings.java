package com.gmail.nossr50.fabric.client.modmenu;

import com.gmail.nossr50.skills.agility.MovementXpSettings;
import com.gmail.nossr50.skills.stealth.StealthXpSettings;
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
    private static final String CAT_EXPLOITS = "Anti-Cheat";

    /** Skills that have an {@code Experience_Formula.Skill_Multiplier.<name>} key. */
    private static final String[] XP_MULTIPLIER_SKILLS = {
        "Agility", "Alchemy", "Archery", "Axes", "Crossbows", "Excavation", "Fishing", "Flying",
        "Herbalism", "Hunter", "Husbandry", "Maces", "Mining", "Parkour", "Repair", "Spears",
        "Stealth", "Swimming", "Swords", "Taming", "Tridents", "Unarmed", "Unarmored", "Woodcutting"
    };

    /** Skills that have a {@code Skills.<name>.Level_Cap} key. */
    private static final String[] LEVEL_CAP_SKILLS = {
        "Agility", "Alchemy", "Archery", "Axes", "Crossbows", "Excavation", "Fishing", "Flying",
        "Herbalism", "Hunter", "Husbandry", "Maces", "Mining", "Parkour", "Repair", "Salvage",
        "Smelting", "Spears", "Stealth", "Swimming", "Swords", "Taming", "Tridents", "Unarmed",
        "Unarmored", "Woodcutting"
    };

    /**
     * Super-abilities with an {@code Abilities.Cooldowns.<name>} key.
     *
     * <p>⚠️ <b>Must list every such key in {@code config.yml}</b>, which is what
     * {@code McMMOSettingsTest#everyCooldownKeyInConfigIsOffered} pins. A new super ability whose
     * cooldown key ships but never reaches this array simply has no slider — invisible, because the
     * catalogue's other guard only proves that declared keys <em>exist</em>, never the converse.
     * {@code Herdsmans_Call} was missed exactly that way when Husbandry landed.
     */
    private static final String[] COOLDOWN_ABILITIES = {
        "Berserk", "Blast_Mining", "Giga_Drill_Breaker", "Green_Terra", "Herdsmans_Call",
        "Second_Wind", "Serrated_Strikes", "Skull_Splitter", "Smoke_Bomb", "Super_Breaker",
        "Tree_Feller"
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

        // Agility movement XP. The baseline is the one knob a player actually wants; the reference
        // speeds and per-medium weights are balance internals that only make sense as a set, so they
        // stay YAML-only rather than being three sliders that quietly break each other's ratios.
        // The default here MUST match experience.yml's shipped value. It was left at 30.0 when the
        // YAML was halved to 15.0, so the editor was offering to "reset to default" a value that had
        // not been the default for some time — the same class of silent balance bug as a config
        // fallback that disagrees with the class it feeds.
        list.add(ConfigSetting.decimal(CAT_XP, EXPERIENCE_YML,
                "Experience_Values.Agility.Movement.Baseline_Xp_Per_Second",
                MovementXpSettings.DEFAULT_BASELINE_XP_PER_SECOND, 0.0, 1000.0,
                "Agility: Movement XP per Second",
                "XP per second of sprinting, swimming or gliding. Each medium's payout is "
                        + "normalised against its own top speed, so a faster medium does not level "
                        + "faster — raise this to level Agility faster overall."));

        // Stealth sneak XP, same shape and same reasoning: the baseline is the knob a player wants,
        // while the reference speed is a balance internal that only means anything alongside it.
        list.add(ConfigSetting.decimal(CAT_XP, EXPERIENCE_YML,
                "Experience_Values.Stealth.Sneak.Baseline_Xp_Per_Second",
                StealthXpSettings.DEFAULT_BASELINE_XP_PER_SECOND, 0.0, 1000.0,
                "Stealth: Sneak XP per Second",
                "XP per second spent sneaking on the ground. Payout is normalised against sneaking "
                        + "speed, so Padfoot makes you cover ground faster without levelling you "
                        + "faster."));

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

        // ---- Anti-cheat / exploit gates (experience.yml) ---------------------------------------
        // GitHub #9. Every switch below is verified to reach live code by
        // ExperienceConfigKeyAgreementTest (the getter reads the shipped key) and
        // McMMOSettingsTest#everyExploitFixKeyIsOffered (no shipped gate is missing from this tab).
        //
        // ⚠️ Half of these guarded nothing when the tab was written: eight ExploitFix gates and all
        // four spawn-origin multipliers had no caller anywhere in the port, so the file promised
        // protection it never delivered. They were wired first, precisely because a settings screen
        // is the one place a dead mechanic becomes an active lie — a player reads "on" and believes
        // they are covered. Do not add a toggle here without first proving its gate has a caller.
        list.add(ConfigSetting.bool(CAT_EXPLOITS, EXPERIENCE_YML, "ExploitFix.PlacedBlocks", true,
                "Track Hand-Placed Blocks",
                "Blocks you place by hand give no gathering rewards when you mine them again. This "
                        + "is the biggest anti-farm gate in the mod — with it off, one stack of ore "
                        + "is infinite Mining XP. It is also the master switch for the lava, snow "
                        + "golem and piston gates below, which share its bookkeeping. Crops are "
                        + "unaffected: they pay on maturity instead."));
        list.add(ConfigSetting.bool(CAT_EXPLOITS, EXPERIENCE_YML,
                "ExploitFix.LavaStoneAndCobbleFarming", true, "Lava Generator Blocks Give No XP",
                "Stone, cobblestone and basalt made by lava meeting water pay no Mining XP. A "
                        + "basalt generator is otherwise 40 XP a block, forever, unattended. "
                        + "Obsidian is exempt — making it costs the lava source."));
        list.add(ConfigSetting.bool(CAT_EXPLOITS, EXPERIENCE_YML, "ExploitFix.SnowGolemExcavation",
                true, "Snow Golem Trails Give No XP",
                "Snow laid down by a snow golem pays no Excavation XP. A penned golem over an "
                        + "auto-breaker is otherwise an income you can leave running."));
        list.add(ConfigSetting.bool(CAT_EXPLOITS, EXPERIENCE_YML, "ExploitFix.PistonCheating", true,
                "Pistons Carry Placed-Block Flags",
                "A block a piston moves keeps its hand-placed flag. Without this, place → push → "
                        + "mine turns a block you placed back into one that rewards you."));
        list.add(ConfigSetting.bool(CAT_EXPLOITS, EXPERIENCE_YML,
                "ExploitFix.EndermanEndermiteFarms", true, "Endermite-Lured Endermen Give No XP",
                "An enderman that has been aggro'd by an endermite pays no combat XP — the "
                        + "signature of an enderman grinder."));
        list.add(ConfigSetting.bool(CAT_EXPLOITS, EXPERIENCE_YML, "ExploitFix.COTWBreeding", true,
                "Summoned Animals Cannot Be Bred For XP",
                "Breeding your own Call of the Wild summons pays no Husbandry XP, so Taming "
                        + "cannot be turned into a Husbandry tap."));
        list.add(ConfigSetting.bool(CAT_EXPLOITS, EXPERIENCE_YML,
                "ExploitFix.PreventArmorStandInteraction", true, "Armour Stands Are Not Opponents",
                "Hitting an armour stand trains no combat skill."));
        list.add(ConfigSetting.bool(CAT_EXPLOITS, EXPERIENCE_YML,
                "ExploitFix.PreventMannequinInteraction", true, "Mannequins Are Not Opponents",
                "Hitting a mannequin trains no combat skill."));
        list.add(ConfigSetting.bool(CAT_EXPLOITS, EXPERIENCE_YML, "ExploitFix.Fishing", true,
                "Fishing Anti-Exploit", "Limits rapid re-casting in the same spot."));
        list.add(ConfigSetting.integer(CAT_EXPLOITS, EXPERIENCE_YML,
                "Fishing_ExploitFix_Options.MoveRange", 3, 0, 64, "Fishing: Move Range (blocks)",
                "How far you must move before fishing the same spot counts again."));
        list.add(ConfigSetting.integer(CAT_EXPLOITS, EXPERIENCE_YML,
                "Fishing_ExploitFix_Options.OverFishLimit", 10, 0, 1000, "Fishing: Over-Fish Limit",
                "Casts in one spot before the catch quality starts dropping."));
        list.add(ConfigSetting.bool(CAT_EXPLOITS, EXPERIENCE_YML, "ExploitFix.Agility", true,
                "Agility Anti-Exploit", "Blocks self-inflicted damage from feeding Agility XP."));
        list.add(ConfigSetting.bool(CAT_EXPLOITS, EXPERIENCE_YML, "ExploitFix.TreeFellerReducedXP",
                true, "Tree Feller Pays Reduced XP",
                "Felling a whole tree at once pays less per log than cutting it by hand."));
        list.add(ConfigSetting.bool(CAT_EXPLOITS, EXPERIENCE_YML,
                "ExploitFix.LimitTallPlantFarming", true, "Limit Bone-Meal Plant Farming",
                "Caps XP from unnaturally tall plants, such as bone-mealed sugar cane."));
        list.add(ConfigSetting.bool(CAT_EXPLOITS, EXPERIENCE_YML, "ExploitFix.UnsafeEnchantments",
                false, "Allow Unsafe Enchantments",
                "⚠️ Reversed: ON permits above-vanilla enchantment levels through Repair and "
                        + "Salvage. Off is the safe setting."));
        list.add(ConfigSetting.bool(CAT_EXPLOITS, EXPERIENCE_YML,
                "ExploitFix.Combat.XPCeiling.Enabled", true, "Cap XP From One Huge Hit",
                "Stops an enormous-health modded mob paying its whole health bar in one blow."));
        list.add(ConfigSetting.integer(CAT_EXPLOITS, EXPERIENCE_YML,
                "ExploitFix.Combat.XPCeiling.Damage_Limit", 100, 1, 10000,
                "Combat: Damage Counted Per Hit",
                "The most damage a single hit may be paid for. Nothing in vanilla reaches 100."));
        list.add(ConfigSetting.bool(CAT_EXPLOITS, EXPERIENCE_YML,
                "ExploitFix.Stealth.Require_Movement_Input", true,
                "Stealth: Require Real Movement Input",
                "Sneak XP needs you actually holding a movement key, so a taped-down shift on a "
                        + "boat or a piston loop earns nothing."));
        list.add(ConfigSetting.bool(CAT_EXPLOITS, EXPERIENCE_YML,
                "ExploitFix.Unarmored.Require_Living_Attacker", true,
                "Unarmored: Require A Living Attacker",
                "Only damage from a mob or player pays. Off, a cactus or a drowning pool does."));
        list.add(ConfigSetting.integer(CAT_EXPLOITS, EXPERIENCE_YML,
                "ExploitFix.Unarmored.Max_Awards_Per_Attacker", 20, 0, 1000,
                "Unarmored: Max Awards Per Attacker",
                "How often one mob can pay you before it stops counting. 0 disables the cap."));
        list.add(ConfigSetting.integer(CAT_EXPLOITS, EXPERIENCE_YML,
                "ExploitFix.Husbandry.Harvest_Cooldown_Seconds", 300, 0, 3600,
                "Husbandry: Harvest Cooldown (sec)",
                "How long one animal waits before milking or brushing it pays again. 0 disables."));
        list.add(ConfigSetting.integer(CAT_EXPLOITS, EXPERIENCE_YML,
                "ExploitFix.Husbandry.Breed_Xp_Awards_Per_Window", 8, 0, 100,
                "Husbandry: Paid Breedings Per Window",
                "How many breedings pay XP inside one window. 0 disables the cap."));
        list.add(ConfigSetting.integer(CAT_EXPLOITS, EXPERIENCE_YML,
                "ExploitFix.Husbandry.Breed_Xp_Award_Window_Seconds", 30, 0, 600,
                "Husbandry: Breeding Window (sec)",
                "The window the cap above counts in. 30s is vanilla's own love duration — "
                        + "shortening it silently doubles the effective cap."));
        list.add(ConfigSetting.decimal(CAT_EXPLOITS, EXPERIENCE_YML,
                "Experience_Formula.Mobspawners.Multiplier", 0.0, 0.0, 10.0,
                "XP From Spawner Mobs (×)",
                "Combat XP multiplier for mobs from a monster or trial spawner. 0 = a grinder "
                        + "pays nothing."));
        list.add(ConfigSetting.decimal(CAT_EXPLOITS, EXPERIENCE_YML,
                "Experience_Formula.Eggs.Multiplier", 0.0, 0.0, 10.0,
                "XP From Spawn-Egg Mobs (×)",
                "Combat XP multiplier for mobs placed by a spawn egg or /summon."));
        list.add(ConfigSetting.decimal(CAT_EXPLOITS, EXPERIENCE_YML,
                "Experience_Formula.Nether_Portal.Multiplier", 0.0, 0.0, 10.0,
                "XP From Portal / Structure Mobs (×)",
                "Combat XP multiplier for mobs spawned by a nether portal or placed by structure "
                        + "generation."));
        list.add(ConfigSetting.decimal(CAT_EXPLOITS, EXPERIENCE_YML,
                "Experience_Formula.Breeding.Multiplier", 1.0, 0.0, 10.0,
                "XP From Bred Mobs (×)",
                "Combat XP multiplier for animals born from breeding."));

        // ---- Skill level caps (config.yml) -----------------------------------------------------
        for (String skill : LEVEL_CAP_SKILLS) {
            list.add(ConfigSetting.integer(CAT_CAPS, CONFIG_YML, "Skills." + skill + ".Level_Cap", 0,
                    0, 100000, skill + " Level Cap", "0 = no cap."));
        }

        return List.copyOf(list);
    }
}
