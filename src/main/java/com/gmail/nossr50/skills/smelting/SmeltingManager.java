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
 * Smelting skill manager (Phase 10.3 port). Holds the MC-free half of every furnace behaviour; the
 * MC-typed half (reading and mutating the furnace's slots and burn timer) lives on
 * {@code fabric.listeners.SmeltingListener}, driven by {@code fabric/mixin/AbstractFurnaceSmeltMixin}.
 *
 * <p>Live behaviours:
 * <ul>
 *   <li>{@link #awardSmeltingXP} — the XP slice of the legacy {@code smeltProcessing} (K7
 *       furnace-smelt hook, CONVERSION_TODO §B);</li>
 *   <li>{@link #canSecondSmelt} + {@link #hasRoomForSecondSmelt} — Second Smelt, the legacy
 *       {@code processDoubleSmelt} / {@code canDoubleSmeltItemStack};</li>
 *   <li>{@link #boostFuelTime} — Fuel Efficiency, the legacy {@code InventoryListener}
 *       {@code onFurnaceBurnEvent} arm.</li>
 * </ul>
 *
 * <p><b>Still deferred:</b> the Understanding the Art vanilla-XP boost. The {@link #vanillaXPBoost}
 * math is ported, but the wiring is not: legacy hooked Bukkit's {@code FurnaceExtractEvent}, whose
 * nearest vanilla seam is {@code FurnaceOutputSlot#onTakeItem} →
 * {@code AbstractFurnaceBlockEntity#dropExperienceForRecipesUsed} → the private static
 * {@code dropExperience}. That split needs the multiplier carried from the slot (which knows the
 * player) down into the orb spawn (which does not), and legacy additionally gated the boost on
 * {@code ItemUtils.isSmelted} — "this result came from a furnace recipe whose input was an ore" —
 * which the drop path no longer knows. See CONVERSION_TODO §B.
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

    /**
     * Whether an item is "smeltable" as far as mcMMO is concerned — legacy
     * {@code ItemUtils.isSmeltable}, which was {@code Smelting.getSmeltXP(item) >= 1}. Only such
     * items earn Smelting XP, and only a furnace smelting one of them gets Fuel Efficiency; cooking
     * food carries no configured Smelting XP and so is left at vanilla burn times.
     *
     * @param materialConfigString the config string of the furnace's <em>input</em> material
     */
    public static boolean isSmeltable(@NotNull String materialConfigString) {
        return McMMOMod.getExperienceConfig().getSmeltingXP(materialConfigString) >= 1;
    }

    public boolean isSecondSmeltSuccessful() {
        return Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.SMELTING_SECOND_SMELT)
                && ProbabilityUtil.isSkillRNGSuccessful(SubSkillType.SMELTING_SECOND_SMELT,
                mmoPlayer);
    }

    /**
     * The config + RNG half of legacy {@code processDoubleSmelt}: whether this smelt should yield a
     * second copy of its result. The caller still has to check {@link #hasRoomForSecondSmelt}, which
     * needs the live output stack.
     *
     * @param resultMaterialConfigString the config string of the smelt <em>result</em> material —
     *                                   legacy keyed the double-drop toggle on the result, not the
     *                                   input ({@code resultItemStack.getType()})
     */
    public boolean canSecondSmelt(@NotNull String resultMaterialConfigString) {
        return McMMOMod.getGeneralConfig()
                .getDoubleDropsEnabled(PrimarySkillType.SMELTING, resultMaterialConfigString)
                && isSecondSmeltSuccessful();
    }

    /**
     * Whether the furnace's output slot can take the Second Smelt's extra item, expressed against the
     * output stack <em>as it is once vanilla has already added the smelt result</em>.
     *
     * <p>Legacy asked the same question one step earlier — it ran on Bukkit's {@code FurnaceSmeltEvent}
     * before the result was merged in, so {@code canDoubleSmeltItemStack} tested the <em>pre-merge</em>
     * count against {@code maxStackSize - 2} ("don't double smelt if it would cause an illegal stack
     * size"). Our seam is the other side of vanilla's own merge, where the count is already one
     * higher, so the equivalent bound is {@code maxCount - 1} — i.e. simply "is there room for one
     * more". The two are the same test: {@code before <= max - 2} ⇔ {@code before + 1 < max}.
     *
     * @param resultCount the output slot's count after vanilla merged the smelt result
     * @param maxCount    the output item's maximum stack size
     */
    public static boolean hasRoomForSecondSmelt(int resultCount, int maxCount) {
        return resultCount < maxCount;
    }

    /**
     * Fuel Efficiency, gated: the legacy {@code onFurnaceBurnEvent} arm checked the sub-skill was
     * enabled before calling {@link #fuelEfficiency}. Returns {@code burnTime} unchanged when the
     * sub-skill is off.
     *
     * @param burnTime vanilla's own burn time for the fuel that is about to be consumed
     */
    public int boostFuelTime(int burnTime) {
        if (!Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.SMELTING_FUEL_EFFICIENCY)) {
            return burnTime;
        }
        return fuelEfficiency(burnTime);
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
