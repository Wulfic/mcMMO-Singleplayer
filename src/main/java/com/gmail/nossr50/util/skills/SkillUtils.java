package com.gmail.nossr50.util.skills;

import com.gmail.nossr50.platform.PlatformItem;
import com.gmail.nossr50.util.Misc;

/**
 * Singleplayer port of the legacy {@code util.skills.SkillUtils} grab-bag. Only the pieces whose
 * consumers are (or are about to be) wired have been ported; each is MC-free — the one MC-typed
 * dependency, held-tool enchant/durability mutation, is confined to {@link PlatformItem} (K3), so
 * this class never touches {@code net.minecraft}.
 *
 * <p>PORT (deferred, breadcrumbs for the rest of K3/K4):
 * <ul>
 *   <li>{@code handleAbilitySpeedIncrease} / {@code removeAbilityBuff} /
 *       {@code removeAbilityBoostsFromInventory} — the Super/Giga Breaker haste dig-speed boost. Needs
 *       enchant <em>write</em> (add/remove Efficiency + the super-ability-boosted marker component) and
 *       an inventory-sweep adapter; both mutate the player's live stacks, so they want in-game
 *       verification (Phase 12) rather than blind generation.</li>
 *   <li>{@code getRepairAndSalvageItem} / {@code getRepairAndSalvageQuantities} — gated on the
 *       {@code RepairConfig}/{@code SalvageConfig} tables and the vanilla recipe iterator (K8).</li>
 *   <li>{@code handleFoodSkills} — needs a food-level change event (K7) + {@code Player} food access.</li>
 *   <li>{@code calculateLengthDisplayValues} / {@code sendSkillMessage} / {@code isSkill} — display and
 *       multiplayer-broadcast surfaces not needed by any ported body.</li>
 *   <li>The {@code RepairableManager} custom max-durability override inside
 *       {@link #handleDurabilityChange} — Repair config unported, so vanilla max durability is used.</li>
 * </ul>
 */
public final class SkillUtils {

    private SkillUtils() {
    }

    /**
     * Whether a super-ability/skill cooldown has expired. Does NOT account for cooldown perks (there
     * are none in singleplayer). Mirrors legacy {@code SkillUtils.cooldownExpired}.
     *
     * @param deactivatedTimeStamp time of deactivation, in seconds
     * @param cooldown             cooldown length, in seconds
     * @return {@code true} once the cooldown has elapsed
     */
    public static boolean cooldownExpired(long deactivatedTimeStamp, int cooldown) {
        return System.currentTimeMillis()
                >= (deactivatedTimeStamp + cooldown) * Misc.TIME_CONVERSION_FACTOR;
    }

    /**
     * Applies a tool durability change using the Tools-specific Unbreaking damage-reduction formula
     * (legacy {@code SkillUtils.handleDurabilityChange}). Used by Super Breaker / Giga Drill Breaker
     * (and, once ported, Repair/Salvage) to wear down the held tool.
     *
     * @param item               the stack to damage
     * @param durabilityModifier the raw amount of durability to consume
     */
    public static void handleDurabilityChange(PlatformItem item, double durabilityModifier) {
        handleDurabilityChange(item, durabilityModifier, 1.0);
    }

    /**
     * Applies a tool durability change with a cap on the fraction of max durability consumed in one
     * hit. Unbreaking reduces the effective damage by a factor of {@code (level + 1)}. Unbreakable
     * items are skipped.
     *
     * @param item               the stack to damage
     * @param durabilityModifier the raw amount of durability to consume
     * @param maxDamageModifier  cap on damage as a fraction of the item's max durability
     */
    public static void handleDurabilityChange(PlatformItem item, double durabilityModifier,
            double maxDamageModifier) {
        if (item.isUnbreakable()) {
            return;
        }

        // PORT K8: RepairableManager custom max-durability override (Repair config unported) — vanilla
        // max durability is used; only differs for items with a configured repairable override.
        int maxDurability = item.getMaxDurability();
        durabilityModifier = (int) Math.min(
                durabilityModifier / (item.getUnbreakingLevel() + 1),
                maxDurability * maxDamageModifier);

        item.setDurability((int) Math.min(item.getDurability() + durabilityModifier, maxDurability));
    }

    /**
     * Applies an armor durability change using the Armor-specific Unbreaking damage-reduction formula
     * (legacy {@code SkillUtils.handleArmorDurabilityChange}; a gentler curve than the tool formula).
     *
     * @param item               the armor stack to damage
     * @param durabilityModifier the raw amount of durability to consume
     * @param maxDamageModifier  cap on damage as a fraction of the item's max durability
     */
    public static void handleArmorDurabilityChange(PlatformItem item, double durabilityModifier,
            double maxDamageModifier) {
        if (item.isUnbreakable()) {
            return;
        }

        int maxDurability = item.getMaxDurability();
        durabilityModifier = (int) Math.min(
                durabilityModifier * (0.6 + 0.4 / (item.getUnbreakingLevel() + 1)),
                maxDurability * maxDamageModifier);

        item.setDurability((int) Math.min(item.getDurability() + durabilityModifier, maxDurability));
    }
}
