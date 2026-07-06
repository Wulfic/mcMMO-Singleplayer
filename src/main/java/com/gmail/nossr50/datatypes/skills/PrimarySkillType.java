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
 */
public enum PrimarySkillType {
    ACROBATICS,
    ALCHEMY,
    ARCHERY,
    AXES,
    CROSSBOWS,
    EXCAVATION,
    FISHING,
    HERBALISM,
    MACES,
    MINING,
    REPAIR,
    SALVAGE,
    SMELTING,
    SPEARS,
    SWORDS,
    TAMING,
    TRIDENTS,
    UNARMED,
    WOODCUTTING
}
