package com.gmail.nossr50.util.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Verifies the singleplayer collapse of {@link PerksUtils}: no perk node is ever granted, so the
 * only behaviour left is the config-driven {@code maxTicks} cap in {@link
 * PerksUtils#handleActivationPerks}.
 */
class PerksUtilsTest {

    @Test
    void cooldownPerksReturnCooldownUnchanged() {
        assertEquals(240, PerksUtils.handleCooldownPerks(240),
                "no cooldown-reduction perk exists in singleplayer");
        assertEquals(0, PerksUtils.handleCooldownPerks(0));
    }

    @Test
    void activationPerksApplyMaxTicksCapWhenPositive() {
        assertEquals(5, PerksUtils.handleActivationPerks(7, 5), "ticks are capped to maxTicks");
        assertEquals(7, PerksUtils.handleActivationPerks(7, 10),
                "ticks below the cap pass through");
    }

    @Test
    void activationPerksSkipCapWhenMaxTicksZero() {
        assertEquals(7, PerksUtils.handleActivationPerks(7, 0),
                "maxTicks 0 means no cap — ticks pass through unchanged");
    }
}
