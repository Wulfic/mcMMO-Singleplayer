package com.gmail.nossr50.util.skills;

import com.gmail.nossr50.datatypes.skills.SuperAbilityType;

/**
 * Perk-driven modifiers for cooldowns, ability durations, and XP.
 *
 * <h2>Singleplayer collapse (CONVERSION_TODO Phase 6)</h2>
 * Legacy perks were permission nodes ({@code mcmmo.perks.cooldowns.halved},
 * {@code mcmmo.perks.activationtime.*}, the XP-boost nodes) that a permissions plugin granted on a
 * multiplayer server. Singleplayer has no permissions plugin and {@link com.gmail.nossr50.util.Permissions}
 * never grants an opt-in perk node, so every perk branch here is dead. What remains is the
 * <em>non-perk</em> arithmetic those methods still had to perform:
 * <ul>
 *   <li>{@link #handleCooldownPerks} — returns the cooldown unchanged.</li>
 *   <li>{@link #handleActivationPerks} — still applies the {@code maxTicks} cap
 *       ({@link SuperAbilityType#getMaxLength()}), which is config-driven, not a perk.</li>
 * </ul>
 * The {@code Player} parameter (used only to read perk nodes) is dropped. The
 * {@code SkillActivationPerkEvent} that legacy fired inside {@code handleActivationPerks} is dropped
 * with the Bukkit event system — no listeners in singleplayer (PORT Phase 3 if ever needed). XP
 * perks ({@code handleXpPerks}) are already collapsed inline in {@code McMMOPlayer#modifyXpGain}.
 */
public final class PerksUtils {

    private PerksUtils() {
    }

    /**
     * Applies cooldown-reduction perks. No perk is ever granted in singleplayer, so the cooldown is
     * returned unchanged.
     *
     * @param cooldown the base cooldown in seconds
     * @return {@code cooldown}, unmodified
     */
    public static int handleCooldownPerks(int cooldown) {
        return cooldown;
    }

    /**
     * Caps an ability's activation length. No activation-time perk is ever granted in singleplayer,
     * so only the config-driven {@code maxTicks} cap is applied (matching legacy behaviour when the
     * cap is non-zero; {@code 0} means "no cap").
     *
     * @param ticks    the computed ability length in ticks
     * @param maxTicks the per-ability maximum ({@link SuperAbilityType#getMaxLength()}); {@code 0}
     *                 disables the cap
     * @return the capped tick count
     */
    public static int handleActivationPerks(int ticks, int maxTicks) {
        if (maxTicks != 0) {
            return Math.min(ticks, maxTicks);
        }
        return ticks;
    }
}
