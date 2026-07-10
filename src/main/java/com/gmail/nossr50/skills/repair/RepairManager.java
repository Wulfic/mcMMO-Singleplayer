package com.gmail.nossr50.skills.repair;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.skills.RankUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Repair skill manager (Phase 10.3 port, legacy 474 lines). The portable numeric core — the
 * percentage-repaired ratio, the Repair Mastery / Super Repair durability math, the Arcane Forging
 * rank and keep/downgrade enchant chances, and the anvil placement/last-use state — survives as pure
 * functions or plain state. Every body that touches a live {@code ItemStack}, {@code PlayerInventory},
 * {@code Enchantment}, the {@code RepairableManager}, notifications/sounds, or the repair-check event
 * is deferred until those adapters and the repairable-item config land — same convention as
 * {@link com.gmail.nossr50.skills.taming.TamingManager} and
 * {@link com.gmail.nossr50.skills.fishing.FishingManager}.
 *
 * <p>The RNG boundary is kept honest: {@link #repairCalculate} takes the Super Repair proc as an
 * injected boolean (the roll is made in-game via {@code ProbabilityUtil}, which has no test seam per
 * the port's RNG convention) so the deterministic durability math stays fully unit-testable.
 *
 * <p><b>Deferred until the item/inventory/enchant adapters + the repairable-item config land
 * (PORT Phase 10/11):</b>
 * <ul>
 *   <li>{@code handleRepair} — the whole repair action: needs {@code RepairableManager},
 *       {@code ItemStack}/{@code PlayerInventory} mutation, the repair-check event, the
 *       {@code getRepairXP(MaterialType)} XP getter (still unported), and {@code SkillUtils};</li>
 *   <li>{@code addEnchants} — Arcane Forging enchant loss/downgrade, needs live {@code Enchantment}
 *       mutation (the chances themselves are ported below);</li>
 *   <li>{@code placedAnvilCheck} — needs notification/sound adapters (the anvil-placed state itself
 *       is ported below);</li>
 *   <li>{@code checkConfirmation} — needs {@code SkillUtils.cooldownExpired} (still unported) plus
 *       notification;</li>
 *   <li>{@code checkPlayerProcRepair} — the Super Repair RNG roll + notification; its decision is
 *       injected into {@link #repairCalculate} instead.</li>
 * </ul>
 */
public class RepairManager extends SkillManager {
    private boolean placedAnvil;
    private int lastClick;

    public RepairManager(@NotNull McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.REPAIR);
    }

    /**
     * The fraction of an item's total durability restored by a single repair. Pure — drives the
     * Repair XP award in the deferred {@code handleRepair} body.
     *
     * @param startDurability the item's durability (damage) before repair
     * @param newDurability the item's durability (damage) after repair
     * @param totalDurability the item's maximum durability
     * @return the fraction repaired
     */
    public float getPercentageRepaired(short startDurability, short newDurability,
            short totalDurability) {
        return ((startDurability - newDurability) / (float) totalDurability);
    }

    /**
     * Computes the item's new durability (damage value) after a repair, applying the Repair Mastery
     * skill-scaled bonus and, when it procced in-game, the Super Repair doubling — then clamping to
     * a valid {@code short}. Faithful to legacy {@code repairCalculate}, but with the Super Repair
     * RNG lifted out into {@code superRepairActivated} so the math is deterministic and testable.
     *
     * @param durability the item's current durability (damage) value
     * @param repairAmount the base repair durability from the repairable definition
     * @param superRepairActivated whether Super Repair procced this repair (rolled in-game)
     * @return the item's new durability (damage) value, never below 0
     */
    public short repairCalculate(short durability, int repairAmount, boolean superRepairActivated) {
        if (RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.REPAIR_REPAIR_MASTERY)
                && Permissions.isSubSkillEnabled(getPlayer(),
                SubSkillType.REPAIR_REPAIR_MASTERY)) {
            final AdvancedConfig advancedConfig = McMMOMod.getAdvancedConfig();
            double repairMasteryMaxBonus = advancedConfig.getRepairMasteryMaxBonus();
            int repairMasteryMaxBonusLevel =
                    advancedConfig.getMaxBonusLevel(SubSkillType.REPAIR_REPAIR_MASTERY);

            double maxBonusCalc = repairMasteryMaxBonus / 100.0D;
            double skillLevelBonusCalc =
                    (repairMasteryMaxBonus / repairMasteryMaxBonusLevel) * (getSkillLevel() / 100.0D);
            double bonus = repairAmount * Math.min(skillLevelBonusCalc, maxBonusCalc);

            repairAmount += bonus;
        }

        if (superRepairActivated) {
            repairAmount *= 2.0D;
        }

        if (repairAmount <= 0 || repairAmount > Short.MAX_VALUE) {
            repairAmount = Short.MAX_VALUE;
        }

        return (short) Math.max(durability - repairAmount, 0);
    }

    /**
     * @return the current Arcane Forging rank
     */
    public int getArcaneForgingRank() {
        return RankUtils.getRank(getPlayer(), SubSkillType.REPAIR_ARCANE_FORGING);
    }

    /**
     * @return the chance (0-100) of keeping an enchantment during repair, by Arcane Forging rank
     */
    public double getKeepEnchantChance() {
        return McMMOMod.getAdvancedConfig()
                .getArcaneForgingKeepEnchantsChance(getArcaneForgingRank());
    }

    /**
     * @return the chance (0-100) of an enchantment being downgraded during repair, by rank
     */
    public double getDowngradeEnchantChance() {
        return McMMOMod.getAdvancedConfig().getArcaneForgingDowngradeChance(getArcaneForgingRank());
    }

    /*
     * Repair Anvil placement state
     */

    public boolean getPlacedAnvil() {
        return placedAnvil;
    }

    public void togglePlacedAnvil() {
        placedAnvil = !placedAnvil;
    }

    /*
     * Repair Anvil usage state
     */

    public int getLastAnvilUse() {
        return lastClick;
    }

    public void setLastAnvilUse(int value) {
        lastClick = value;
    }

    public void actualizeLastAnvilUse() {
        // legacy Misc.TIME_CONVERSION_FACTOR (ms -> s); inlined (Misc not yet ported)
        lastClick = (int) (System.currentTimeMillis() / 1000L);
    }
}
