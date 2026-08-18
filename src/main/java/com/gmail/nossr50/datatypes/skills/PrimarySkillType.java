package com.gmail.nossr50.datatypes.skills;

/**
 * The core set of mcMMO skills.
 * <p>
 * In the original Bukkit plugin this enum also carried a large collection of
 * {@code @Deprecated} convenience methods that simply delegated to
 * {@code mcMMO.p.getSkillTools()} / the config classes. Those have been dropped
 * during the Fabric port: the authoritative behaviour lives in
 * {@code SkillTools} (ported in the skill-modules phase), and call sites should
 * go through it directly rather than through this enum. Keeping this type as a
 * pure enum makes it Minecraft-free and unit-testable, and lets the rest of the
 * datatype vocabulary compile without dragging in the config/skill-tools graph.
 * <p>
 * <b>A constant's {@code name()} is the on-disk save key</b> ({@code skills.<NAME>} /
 * {@code experience.<NAME>} in {@code FlatFileProfileStore}) <b>and</b> the milestone
 * advancement file name ({@code Milestones.key()}). Both fail <em>silently</em> when a
 * constant is renamed — the profile loader falls back to the starting level and the
 * plaque simply stops firing. Renames go through {@code util/skills/SkillRenames}.
 */
public enum PrimarySkillType {
    // AGILITY -- RETIRED 2026-08-17, and there is deliberately no constant here. It was a CHILD
    // skill whose level was the mean of Parkour, Swimming and Flying; its last two sub-skills (Fleet
    // Footed, Second Wind) became six single-rank sub-skills, one pair per parent. A sub-skill's
    // parent is derived from its enum name PREFIX, so one constant could not span three parents.
    //
    // The movement manager survives the removal and is keyed NOMINALLY on PARKOUR -- see
    // MovementManager's constructor for why that field is load-bearing for nothing.
    //
    // An existing profile's `skills.AGILITY` / `experience.AGILITY` key is an ORPHAN, not a rename:
    // a child skill never had a stored level to migrate. FlatFileProfileStore drops it on the next
    // save; FlatFileProfileStoreTest pins both directions.
    ALCHEMY,
    ARCHERY,
    AXES,
    // Pass 2. The food-processing skill: XP for cooking food in a furnace/smoker/blast furnace and
    // for crafting food at a bench. Its boundary against SMELTING is already enforced in shipped
    // code, in both directions -- Experience_Values.Smelting is 25 ore entries, and boostFuelTime
    // gates on isSmeltable(input) so that "cooking food burns at vanilla speed". Kitchen Efficiency
    // is literally the else of a gate that already exists.
    //
    // It has NO spawn-origin flag and no equivalent gate to hide behind: an item carries no record
    // of where it came from, so an unattended hopper-fed smoker array pays exactly like a hand-fed
    // one. The rolling ExploitFix.Cooking.Max_Cooks_Per_Hour cap is the only anti-farm gate the
    // skill has, which is why it ships in the same stage as the XP that needs it.
    COOKING,
    CROSSBOWS,
    EXCAVATION,
    FISHING,
    FLYING,
    HERBALISM,
    // Pass 2. The mob-knowledge skill, and the only one in the mod that progresses on two independent
    // axes: a per-mob kill counter (mastery -- flat bonus damage against THAT creature only) and a
    // normal XP level (better loot, unlocked per mob tier). Killing 10,000 zombies makes you a zombie
    // specialist; killing 200 of everything makes you a generalist with better drops. Neither
    // substitutes for the other, and keeping them independent is the whole design.
    //
    // It is a COMBAT skill but NOT a weapon skill: Swords/Axes/Unarmed/Archery already own "how hard
    // do I hit with X", so Hunter owns "how well do I know Y" and its bonus is deliberately
    // weapon-agnostic. That is why the bonus does not live in MeleeDamageBonus, which is per-weapon
    // by construction.
    HUNTER,
    // Pass 2. The livestock-lifecycle skill: breed, raise, feed, shear, harvest a hive, milk, brush.
    // Its boundary against TAMING is the VERB, never the species -- Taming pays once, for making an
    // animal yours; Husbandry pays repeatedly, for what you do with it afterwards. A species split
    // is not available: Taming.Animal_Taming already claims every animal in the game.
    HUSBANDRY,
    MACES,
    MINING,
    PARKOUR,
    REPAIR,
    SALVAGE,
    SMELTING,
    SPEARS,
    STEALTH,
    SWIMMING,
    SWORDS,
    TAMING,
    TRIDENTS,
    UNARMED,
    // Pass 2. One letter of difference from UNARMED, which is load-bearing rather than merely
    // unfortunate: SkillTools resolves a sub-skill's parent from the enum-name prefix up to the
    // first '_', so UNARMORED_IRON_SKIN parents onto this constant and UNARMED_ARROW_DEFLECT onto
    // the one above. The match is equalsIgnoreCase on the WHOLE prefix, not startsWith, so the two
    // cannot collide — see UnarmoredManagerTest, which pins that.
    UNARMORED,
    WOODCUTTING
}
