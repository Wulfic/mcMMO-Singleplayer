package com.gmail.nossr50.skills;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.util.text.ConfigStringUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Combat-XP core, MC-free half: the per-hit XP formula pulled out of legacy
 * {@code CombatUtils#processCombatXP} and {@code runnables/skills/AwardCombatXpTask} so it is
 * server-free and unit-testable against the real bundled {@code experience.yml}. The MC-typed half —
 * resolving the victim's type, category and remaining health — is
 * {@link com.gmail.nossr50.util.skills.CombatUtils#processCombatXP}.
 *
 * <h2>Per-hit, not per-kill</h2>
 * mcMMO pays combat XP on <em>every hit</em>, proportional to the damage that hit actually lands:
 * {@code (int) (damage * base * 10 * multiplier)}. This port originally simplified that to a single
 * per-kill award on {@code AFTER_DEATH} (Phase 3) — the damage fractions across a mob's whole life sum
 * to its max health, so the totals matched for a clean solo kill. That simplification was reverted:
 * it structurally could not carry the per-hit multipliers Archery (fired-from distance, bow draw
 * force) and Taming (the wolf-assist ×3) are balanced around, and it silently paid nothing at all when
 * a wolf landed the killing blow.
 *
 * <p>Legacy measured the damage indirectly, by scheduling {@code AwardCombatXpTask} for the next tick
 * and diffing the victim's health across it, because a Bukkit event handler could not know what the
 * hit would finally land. This port sits on the {@code modifyAppliedDamage} seam <em>inside</em>
 * {@code damage()}, holding the post-armor figure that is about to be written, so the diff is
 * unnecessary and both of that task's guards collapse into {@link #creditableDamage} directly.
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
     * The base combat XP an entity of this type and category pays <em>per point of damage</em> — the
     * mob's configured {@code Combat.Multiplier}, scaled by the flat ×10 legacy applies to every
     * award.
     *
     * @param entityRegistryId the victim's entity-type registry id (e.g. {@code "minecraft:zombie"})
     * @param category         the victim's coarse category (monster / animal / other)
     * @return the per-damage base XP, or {@code 0} if configs aren't loaded
     */
    public static double baseXp(@NotNull String entityRegistryId, @NotNull MobCategory category) {
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

    /**
     * How much of a hit's damage actually counts towards XP. Ports the two guards legacy's
     * {@code AwardCombatXpTask} applied to its measured health delta:
     *
     * <ul>
     *   <li><b>No credit for overkill.</b> Legacy expressed this as {@code if (health < 0) damage +=
     *       health}, which clamps the delta to the health the victim had left; hitting a 2-HP zombie
     *       for 40 pays for 2. Clamping the damage to {@code remainingHealth} here says the same thing
     *       against the pre-hit health this seam reads.</li>
     *   <li><b>The exploit-fix HP ceiling</b> ({@code ExploitFix.Combat.XPCeiling}), which caps the
     *       creditable damage of any single hit — a guard against enormous-health modded mobs. The
     *       bundled {@code experience.yml} ships neither key, so the defaults apply (enabled, 100 HP)
     *       and it does not bind for anything vanilla.</li>
     * </ul>
     *
     * @param damage          the damage this hit is about to land (post-armor)
     * @param remainingHealth the victim's health before the hit
     * @return the creditable damage, never negative
     */
    public static double creditableDamage(double damage, double remainingHealth) {
        double credited = Math.min(damage, Math.max(remainingHealth, 0.0));
        if (credited <= 0) {
            return 0;
        }
        final ExperienceConfig config = McMMOMod.getExperienceConfig();
        if (config != null && config.useCombatHPCeiling()) {
            credited = Math.min(credited, config.getCombatHPCeiling());
        }
        return credited;
    }

    /**
     * The XP a single hit pays: {@code (int) (creditableDamage * baseXp * multiplier)}, matching
     * legacy's {@code AwardCombatXpTask} (which likewise truncates rather than rounds).
     *
     * <p>Legacy applies the multiplier to the base <em>before</em> testing it for positivity and
     * skipping the award, so a zero multiplier pays nothing regardless of the damage — preserved here.
     *
     * @param entityRegistryId the victim's entity-type registry id
     * @param category         the victim's coarse category
     * @param damage           the damage this hit is about to land (post-armor)
     * @param remainingHealth  the victim's health before the hit
     * @param multiplier       the skill's per-hit XP multiplier (Archery distance × bow force, the
     *                         Taming wolf-assist ×3, or {@code 1.0} for everything else)
     * @return the XP to award, or {@code 0} if this hit pays none
     */
    public static int xpForHit(@NotNull String entityRegistryId, @NotNull MobCategory category,
            double damage, double remainingHealth, double multiplier) {
        final double base = baseXp(entityRegistryId, category) * multiplier;
        if (base <= 0) {
            return 0;
        }
        final double credited = creditableDamage(damage, remainingHealth);
        if (credited <= 0) {
            return 0;
        }
        return (int) (credited * base);
    }
}
