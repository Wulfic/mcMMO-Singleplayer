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
    AGILITY,
    ALCHEMY,
    ARCHERY,
    AXES,
    CROSSBOWS,
    EXCAVATION,
    FISHING,
    FLYING,
    HERBALISM,
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
    WOODCUTTING
}
