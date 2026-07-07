package com.gmail.nossr50.skills.smelting;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;

/**
 * Smelting skill manager (Phase 10.3 port). Only the rank-driven multipliers and the Second Smelt RNG
 * gate survive; the furnace-event bodies are dropped until the ItemStack/inventory adapter lands.
 *
 * <p>Dropped until the ItemStack adapter:
 * <ul>
 *   <li>{@code smeltProcessing} / {@code processDoubleSmelt} / {@code canDoubleSmeltItemStack} — read
 *       and mutate the {@code FurnaceSmeltEvent} result and the furnace {@code ItemStack}s;</li>
 *   <li>the {@code Smelting} helper ({@code getSmeltXP(ItemStack)}) — resolves per-material smelt XP;
 *       not ported (its only caller was {@code smeltProcessing}).</li>
 * </ul>
 */
public class SmeltingManager extends SkillManager {
    public SmeltingManager(McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.SMELTING);
    }

    public boolean isSecondSmeltSuccessful() {
        return Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.SMELTING_SECOND_SMELT)
                && ProbabilityUtil.isSkillRNGSuccessful(SubSkillType.SMELTING_SECOND_SMELT,
                mmoPlayer);
    }

    /**
     * Increases burn time for furnace fuel.
     *
     * @param burnTime The initial burn time from the furnace burn event
     * @return the boosted burn time, clamped to {@code [1, Short.MAX_VALUE]}
     */
    public int fuelEfficiency(int burnTime) {
        if (burnTime <= 0) {
            return 0;
        }
        return Math.min(Short.MAX_VALUE, Math.max(1, burnTime * getFuelEfficiencyMultiplier()));
    }

    public int getFuelEfficiencyMultiplier() {
        return switch (RankUtils.getRank(getPlayer(), SubSkillType.SMELTING_FUEL_EFFICIENCY)) {
            case 1 -> 2;
            case 2 -> 3;
            case 3 -> 4;
            default -> 1;
        };
    }

    public int vanillaXPBoost(int experience) {
        return experience * getVanillaXpMultiplier();
    }

    /**
     * Gets the vanilla XP multiplier.
     *
     * @return the vanilla XP multiplier
     */
    public int getVanillaXpMultiplier() {
        return Math.max(1,
                RankUtils.getRank(getPlayer(), SubSkillType.SMELTING_UNDERSTANDING_THE_ART));
    }
}
