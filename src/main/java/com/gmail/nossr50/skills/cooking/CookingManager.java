package com.gmail.nossr50.skills.cooking;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.skills.SkillManager;

/**
 * Cooking skill manager (Pass 2). Holds the MC-free half of every food-processing behaviour; the
 * MC-typed half (reading furnace slots, the crafting result slot, and the eaten stack) lives on the
 * existing {@code fabric.listeners.SmeltingListener} / {@code FoodListener} seams.
 *
 * <p><b>Stage 1 (registration) deliberately ships this as a shell.</b> The skill is registered,
 * persisted, configurable and rendered generically by {@code /mcstats}, and nothing fires it yet.
 * Each behaviour lands with the seam that drives it:
 * <ul>
 *   <li><b>Stage 2</b> — cook XP (the food branch of {@code onFurnaceSmelt}) and crafted-food XP
 *       ({@code CraftingResultSlot}), plus the {@code Max_Cooks_Per_Hour} rolling cap;</li>
 *   <li><b>Stage 3</b> — Kitchen Efficiency ({@code boostFuelTime}'s {@code else}) and Master Chef
 *       ({@code onSmeltComplete}'s food arm);</li>
 *   <li><b>Stage 4</b> — Power Cook (the eat seam, and the level → duration math, which lives here
 *       precisely so it is unit-testable with no world);</li>
 *   <li><b>Stage 5</b> — campfires and {@code CookingStatsRenderer}.</li>
 * </ul>
 *
 * <h2>⚠️ Two of the three sub-skills need an explicit per-skill disable gate</h2>
 * {@code SkillGating} enforces the {@code coreskills.yml} master switch at three chokepoints:
 * {@code Permissions}, {@code RankUtils} booleans, and {@code ProbabilityUtil#isSkillRNGSuccessful}.
 * Master Chef is an RNG proc and is therefore covered for free. <b>Kitchen Efficiency is a
 * multiplier and Power Cook is a deterministic effect, so neither passes through any of those</b> —
 * each needs {@code SkillGating.isSkillEnabled(PrimarySkillType.COOKING)} checked at its own call
 * site, or switching Cooking off would still boost fuel and still hand out Strength.
 *
 * <h2>⚠️ The Smelting boundary</h2>
 * Cooking and Smelting share the furnace and the shared {@code FURNACE_OWNERS} map, and the boundary
 * between them is already enforced in shipped code in both directions:
 * {@code Experience_Values.Smelting} lists ore only, and {@code SmeltingManager#boostFuelTime} gates
 * on {@code isSmeltable(input)} so that food burns at vanilla speed. Kitchen Efficiency is literally
 * the {@code else} of a gate that already exists — do not widen either side to "unify" them.
 */
public class CookingManager extends SkillManager {
    public CookingManager(McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.COOKING);
    }
}
