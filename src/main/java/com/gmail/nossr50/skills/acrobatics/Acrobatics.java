package com.gmail.nossr50.skills.acrobatics;

/**
 * Static helpers backing the Acrobatics skill. Port note (Phase 10.3): only the pure dodge-damage
 * math survives — the config-cached statics ({@code dodgeDamageModifier}, {@code dodgeXpModifier},
 * {@code dodgeLightningDisabled}) are dropped here and read live at their call sites when the Dodge
 * combat path lands, mirroring the {@link com.gmail.nossr50.skills.archery.Archery} treatment.
 */
public final class Acrobatics {

    private Acrobatics() {
    }

    /**
     * Reduces incoming fall/dodge damage by the configured modifier, flooring the result at 1.0 so a
     * successful dodge never fully negates the hit.
     *
     * @param damage         the raw incoming damage
     * @param damageModifier the divisor from config (guaranteed {@code > 1} by AdvancedConfig
     *                       validation)
     * @return the reduced damage, never below {@code 1.0}
     */
    static double calculateModifiedDodgeDamage(double damage, double damageModifier) {
        return Math.max(damage / damageModifier, 1.0);
    }
}
