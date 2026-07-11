package com.gmail.nossr50.skills.smelting;

import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Smelting skill manager (Phase 10.3 port). Only the rank-driven multipliers and the Second Smelt RNG
 * gate survive; the furnace-event bodies are dropped until the ItemStack/inventory adapter lands.
 *
 * <p>The XP-award slice of the legacy {@code smeltProcessing} is now live via {@link #awardSmeltingXP}
 * (K7 furnace-smelt hook, CONVERSION_TODO §B); the MC-typed caller
 * ({@code fabric.listeners.SmeltingListener}) resolves the smelted input's material config string.
 *
 * <p>Still dropped until the ItemStack/inventory adapter (K3):
 * <ul>
 *   <li>{@code processDoubleSmelt} / {@code canDoubleSmeltItemStack} — read and mutate the furnace
 *       result {@code ItemStack} for the Second Smelt bonus (the {@link #isSecondSmeltSuccessful} RNG
 *       gate is ported; only the item duplication is deferred);</li>
 *   <li>the vanilla-XP boost application ({@link #vanillaXPBoost} math is ported; wiring it onto the
 *       furnace's dropped-experience is deferred).</li>
 * </ul>
 */
public class SmeltingManager extends SkillManager {
    public SmeltingManager(McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.SMELTING);
    }

    /**
     * Award Smelting XP for smelting an item (the XP slice of legacy {@code smeltProcessing}, which
     * called {@code Smelting.getResourceXp(ItemStack)}). The MC-typed caller
     * ({@code fabric.listeners.SmeltingListener}) resolves the smelted <em>input</em> material's
     * config string (e.g. {@code "Iron_Ore"}); this looks up the per-material XP and awards it,
     * keeping the manager MC-free.
     *
     * @param materialConfigString the config string of the smelted input material
     */
    public void awardSmeltingXP(@NotNull String materialConfigString) {
        final int xp = McMMOMod.getExperienceConfig().getSmeltingXP(materialConfigString);
        if (xp <= 0) {
            return; // material carries no configured Smelting XP (e.g. cooking food, not ore).
        }
        applyXpGain(xp, XPGainReason.PVE, XPGainSource.SELF);
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
