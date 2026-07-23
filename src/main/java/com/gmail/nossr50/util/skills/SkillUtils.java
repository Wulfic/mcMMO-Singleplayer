package com.gmail.nossr50.util.skills;

import com.gmail.nossr50.config.HiddenConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformItem;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.util.Misc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Singleplayer port of the legacy {@code util.skills.SkillUtils} grab-bag. Only the pieces whose
 * consumers are (or are about to be) wired have been ported; each is MC-free — the one MC-typed
 * dependency, held-tool enchant/durability mutation, is confined to {@link PlatformItem} (K3), so
 * this class never touches {@code net.minecraft}.
 *
 * <p>The Super/Giga Breaker haste dig-speed boost ({@link #handleAbilitySpeedIncrease},
 * {@link #removeAbilityBuffFromMainHand}, {@link #removeAbilityBoostsFromInventory}) is now wired: the
 * MC-free mode decision (enchant-buff vs the legacy Haste-potion fallback) lives here, and the actual
 * enchant/marker mutation is confined to {@link PlatformPlayer} (K3 write side).
 *
 * <p>PORT (deferred, breadcrumbs for the rest of K3/K4):
 * <ul>
 *   <li>The Haste-<em>potion</em> fallback branch of {@code handleAbilitySpeedIncrease} — unreachable
 *       with the bundled {@code hidden.yml} ({@code Options.EnchantmentBuffs=true}); port a
 *       {@code PlatformPlayer} Haste-effect method only if that knob is ever exposed to users.</li>
 *   <li>{@code getRepairAndSalvageItem} (runtime crafted-material lookup) — gated on the
 *       {@code RepairConfig}/{@code SalvageConfig} tables and the vanilla recipe iterator (K8).</li>
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
     * Apply the Super Breaker / Giga Drill Breaker dig-speed boost to the player's held tool (legacy
     * {@code SkillUtils.handleAbilitySpeedIncrease}). With the bundled {@code hidden.yml}
     * ({@code Options.EnchantmentBuffs=true}) this bumps the main-hand tool's Efficiency by the
     * configured {@code advanced.yml EnchantBuff}; the mutation itself lives on
     * {@link PlatformPlayer#applySuperAbilityDigBoost(int)} so this stays MC-free.
     *
     * @param player the player whose held tool to boost
     */
    public static void handleAbilitySpeedIncrease(PlatformPlayer player) {
        if (!HiddenConfig.getInstance().useEnchantmentBuffs()) {
            // PORT: the legacy Haste-potion fallback. Unreachable with the bundled hidden.yml
            // (EnchantmentBuffs=true); wire a PlatformPlayer Haste-effect method if that knob is exposed.
            return;
        }
        player.applySuperAbilityDigBoost(McMMOMod.getAdvancedConfig().getEnchantBuff());
    }

    /**
     * Strip any lingering dig-speed boost from the player's main-hand tool before (re)activation so the
     * added Efficiency can't stack across activations (legacy {@code SkillUtils.removeAbilityBuff} on the
     * held item).
     *
     * @param player the player whose main-hand tool to clean up
     */
    public static void removeAbilityBuffFromMainHand(PlatformPlayer player) {
        player.removeSuperAbilityBoostFromMainHand();
    }

    /**
     * Strip the dig-speed boost from every stack in the player's inventory when Super/Giga Breaker ends
     * (legacy {@code SkillUtils.removeAbilityBoostsFromInventory}), catching a boosted tool that was
     * moved out of the main hand during the ability.
     *
     * @param player the player whose inventory to sweep
     */
    public static void removeAbilityBoostsFromInventory(PlatformPlayer player) {
        player.removeSuperAbilityBoostsFromInventory();
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

    /**
     * The number of the crafting material a vanilla item consumes in its recipe — used as the default
     * repair minimum-quantity and salvage maximum-quantity when the config does not name one.
     *
     * <p>Legacy {@code getRepairAndSalvageQuantities(Material, Material)} discovered this by iterating
     * the server's live recipe list and counting matching ingredients. Those counts are fixed vanilla
     * constants, so this port reproduces them from the item's registry path instead: it never touches
     * the recipe manager, stays {@code net.minecraft}-free, and is fully unit-testable. Two families
     * short-circuit the shape lookup exactly as upstream did: netherite gear (one bar = four scraps)
     * and prismarine tools (a trident, 16 crystals).
     *
     * @param itemRegistryPath the item's vanilla registry path (e.g. {@code "diamond_pickaxe"})
     * @return the standard recipe material count, or 0 when the item's shape is unknown (callers then
     *     fall back to the config value / a floor of 1)
     */
    public static int getRepairAndSalvageQuantities(String itemRegistryPath) {
        final String path = itemRegistryPath.toLowerCase(java.util.Locale.ROOT);

        // One netherite bar is crafted from four netherite scraps, so all netherite gear salvages/
        // repairs in units of four regardless of the tool/armor shape (upstream special-case).
        if (path.startsWith("netherite_")) {
            return 4;
        }
        // Tridents are repaired with prismarine crystals; upstream returns a flat 16 for prismarine
        // tools (the trident is the only one).
        if (path.equals("trident")) {
            return 16;
        }

        // Standard tool/armor recipe ingredient counts (identical for repair and salvage).
        if (path.endsWith("_sword")) {
            return 2;
        }
        if (path.endsWith("_pickaxe") || path.endsWith("_axe")) {
            return 3;
        }
        if (path.endsWith("_shovel")) {
            return 1;
        }
        if (path.endsWith("_hoe")) {
            return 2;
        }
        if (path.endsWith("_helmet")) {
            return 5;
        }
        if (path.endsWith("_chestplate")) {
            return 8;
        }
        if (path.endsWith("_leggings")) {
            return 7;
        }
        if (path.endsWith("_boots")) {
            return 4;
        }

        // Non-gear vanilla repairables whose material count isn't derivable from a shape suffix.
        return switch (path) {
            case "shears" -> 2;         // 2 iron ingots
            case "flint_and_steel" -> 1; // 1 iron ingot
            case "bow" -> 3;            // 3 string
            case "fishing_rod" -> 2;    // 2 string
            default -> 0;               // unknown shape: caller supplies the config value / floor of 1
        };
    }

    /**
     * The food-level a diet sub-skill (Herbalism Farmer's Diet / Fishing Fisherman's Diet) restores,
     * given what the food would have restored on its own. Legacy {@code handleFoodSkills}.
     *
     * <p><b>No live food-level access is needed</b>, which is why this no longer waits on the K4
     * "{@code Player} food access" adapter the old breadcrumb called for. Legacy computed
     * {@code currentFoodLevel + ((eventFoodLevel - currentFoodLevel) + curRank)} — the current level
     * cancels out algebraically, leaving {@code eventFoodLevel + curRank}. Because only the
     * <em>difference</em> survives, the caller may equally pass the absolute post-eat food level
     * (Bukkit's {@code FoodLevelChangeEvent} value, as legacy did) or the raw nutrition delta (what the
     * vanilla {@code FoodComponent} seam hands us); the bonus is {@code curRank} either way.
     *
     * @param mmoPlayer    the eating player, or {@code null} if their data is not loaded
     * @param eventFoodLevel the food restoration before the sub-skill bonus
     * @param subSkillType the diet sub-skill being applied
     * @return the food restoration after adding the player's rank in that sub-skill
     */
    public static int handleFoodSkills(@Nullable McMMOPlayer mmoPlayer, int eventFoodLevel,
            @NotNull SubSkillType subSkillType) {
        final int curRank = RankUtils.getRank(mmoPlayer, subSkillType);

        // getRank returns -1 for sub-skills that declare no ranks; both diets declare five, but a
        // negative rank must never *cost* the player food if that ever changes upstream.
        return eventFoodLevel + Math.max(curRank, 0);
    }
}
