package com.gmail.nossr50.skills;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.util.text.ConfigStringUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Combat-XP core (CONVERSION_TODO Phase 3): the weapon→skill routing and per-kill base-XP formula
 * pulled out of the legacy {@code CombatUtils#processCombatXP} so they're MC-free and unit-testable
 * against the real bundled {@code experience.yml}.
 *
 * <p>Legacy awarded combat XP per hit, proportional to damage dealt: {@code base * 10 * (damage /
 * maxHealth)}. Across a mob's whole life those damage fractions sum to 1, so awarding {@code base *
 * 10} once on death (where this port hooks) reproduces the total XP for a full kill.
 * ({@code base} is the mob's configured {@code Combat.Multiplier} value.)
 */
public final class CombatXp {

    /** The flat scalar legacy applies to every combat XP award ({@code baseXP *= 10}). */
    private static final double COMBAT_XP_SCALAR = 10.0;

    /** Coarse vanilla mob category, mirroring legacy's {@code Animals}/{@code Monster}/other branch. */
    public enum MobCategory {
        MONSTER,
        ANIMAL,
        OTHER
    }

    private CombatXp() {
    }

    /**
     * Map the attacker's held item to the melee/ranged skill it trains, by vanilla registry id.
     * Anything that isn't a recognised weapon (bare hand, tools, blocks) trains Unarmed, matching
     * mcMMO's "fists and everything else" rule.
     *
     * @param itemRegistryId the held item's registry id (e.g. {@code "minecraft:diamond_sword"}), or
     *                       {@code null}/empty for an empty hand
     */
    public static @NotNull PrimarySkillType weaponSkill(String itemRegistryId) {
        final String path = stripNamespace(itemRegistryId);
        if (path.endsWith("_sword")) {
            return PrimarySkillType.SWORDS;
        }
        if (path.endsWith("_axe")) {
            return PrimarySkillType.AXES;
        }
        if (path.equals("mace")) {
            return PrimarySkillType.MACES;
        }
        if (path.equals("trident")) {
            return PrimarySkillType.TRIDENTS;
        }
        if (path.equals("bow")) {
            return PrimarySkillType.ARCHERY;
        }
        if (path.equals("crossbow")) {
            return PrimarySkillType.CROSSBOWS;
        }
        return PrimarySkillType.UNARMED;
    }

    /**
     * The total base combat XP for killing an entity of the given type and category.
     *
     * @param entityRegistryId the victim's entity-type registry id (e.g. {@code "minecraft:zombie"})
     * @param category         the victim's coarse category (monster / animal / other)
     * @return base XP for the kill, or {@code 0} if configs aren't loaded
     */
    public static double baseXpForKill(@NotNull String entityRegistryId,
            @NotNull MobCategory category) {
        final ExperienceConfig config = McMMOMod.getExperienceConfig();
        if (config == null) {
            return 0;
        }
        final String key = ConfigStringUtils.getConfigEntityTypeString(entityRegistryId);
        final double base = switch (category) {
            // Animals fall back to the generic Animals multiplier when unlisted.
            case ANIMAL -> config.getAnimalsXP(key);
            // Monsters use their configured multiplier (0 if genuinely unlisted).
            case MONSTER -> config.getCombatXP(key);
            // Everything else: configured value if present, else the legacy 1.0 floor.
            case OTHER -> config.hasCombatXP(key) ? config.getCombatXP(key) : 1.0;
        };
        return base * COMBAT_XP_SCALAR;
    }

    private static @NotNull String stripNamespace(String registryId) {
        if (registryId == null) {
            return "";
        }
        final int colon = registryId.indexOf(':');
        return colon >= 0 ? registryId.substring(colon + 1) : registryId;
    }
}
