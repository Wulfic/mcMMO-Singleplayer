package com.gmail.nossr50.skills.repair;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.skills.repair.repairables.Repairable;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.skills.RankUtils;
import com.gmail.nossr50.util.skills.SkillUtils;
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
 *   <li>{@code addEnchants} — <b>DONE</b>: its per-enchantment decision is
 *       {@link #resolveEnchantOutcome} here, and the enchant mutation lives in
 *       {@code RepairSalvageListener} (the only MC-typed part left);</li>
 *   <li>{@code placedAnvilCheck} — needs notification/sound adapters (the anvil-placed state itself
 *       is ported below);</li>
 *   <li>{@code checkConfirmation} — the anvil-use hook (K7) + notification; {@link
 *       com.gmail.nossr50.util.skills.SkillUtils#cooldownExpired} it depends on is now ported;</li>
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
     * Awards the Repair XP for a completed repair. Faithful to legacy {@code handleRepair}'s XP
     * formula: the fraction of the item's max durability restored, scaled by the repairable's XP
     * multiplier, the {@code Experience_Values.Repair.Base} factor, and the per-{@link
     * com.gmail.nossr50.datatypes.skills.MaterialType} Repair XP factor. MC-free (operates on the
     * MC-free {@link Repairable} + config getters), so the anvil listener rolls the RNG and mutates
     * the live {@link net.minecraft.item.ItemStack}, then calls this for the deterministic award.
     *
     * @param startDurability the item's durability (damage) before the repair
     * @param newDurability the item's durability (damage) after the repair
     * @param repairable the definition of the item being repaired
     */
    public void awardRepairXp(short startDurability, short newDurability,
            @NotNull Repairable repairable) {
        final ExperienceConfig experienceConfig = McMMOMod.getExperienceConfig();
        applyXpGain((float) (getPercentageRepaired(startDurability, newDurability,
                        repairable.getMaximumDurability())
                        * repairable.getXpMultiplier()
                        * experienceConfig.getRepairXPBase()
                        * experienceConfig.getRepairXP(repairable.getRepairMaterialType())),
                XPGainReason.PVE, XPGainSource.SELF);
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

    /**
     * What Arcane Forging does to one enchantment on a repaired item.
     *
     * <p>Legacy expressed this by mutating the {@code ItemStack} inline; splitting it into an
     * outcome keeps the decision unit-testable and leaves the enchant write to the MC-typed caller.
     */
    public enum ArcaneOutcome {
        /** The enchantment survives the repair at its current level. */
        KEPT,
        /** The enchantment survives but drops one level. */
        DOWNGRADED,
        /** The enchantment is stripped from the item. */
        LOST
    }

    /**
     * Whether this player's Arcane Forging can preserve enchantments at all. Legacy strips
     * <em>every</em> enchantment from a repaired item when the player has no Arcane Forging rank —
     * repairing an enchanted item below the first rank threshold (level 100 in RetroMode) is a
     * guaranteed total loss, not merely a low keep-chance.
     *
     * @return {@code true} if the per-enchantment roll should run, {@code false} to strip them all
     */
    public boolean canKeepEnchants() {
        return getArcaneForgingRank() != 0
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.REPAIR_ARCANE_FORGING);
    }

    /**
     * Whether the "keep, but at a lower level" outcome is reachable at all
     * (advanced.yml {@code Skills.Repair.ArcaneForging.Downgrades_Enabled}). With downgrades off, a
     * kept enchantment always keeps its full level. Legacy read this through the static
     * {@code ArcaneForging.arcaneForgingDowngrades} field, which snapshotted the config at plugin
     * load; reading it live here is equivalent for a config that is never reloaded mid-session.
     *
     * @return whether Arcane Forging may downgrade a kept enchantment
     */
    public boolean isArcaneForgingDowngradeEnabled() {
        return McMMOMod.getAdvancedConfig().getArcaneForgingDowngradeEnabled();
    }

    /**
     * Whether repairing may cost an item its enchantments at all
     * (advanced.yml {@code Skills.Repair.ArcaneForging.May_Lose_Enchants}). With it off, Arcane
     * Forging is skipped outright and a repaired item keeps every enchantment untouched — legacy
     * gates the whole {@code addEnchants} call on this, not on any individual roll. Legacy read it
     * through the static {@code ArcaneForging.arcaneForgingEnchantLoss} field, snapshotted at plugin
     * load; reading it live is equivalent for a config that is never reloaded mid-session.
     *
     * @return whether the Arcane Forging enchantment roll should run at all
     */
    public boolean isArcaneForgingEnchantLossEnabled() {
        return McMMOMod.getAdvancedConfig().getArcaneForgingEnchantLossEnabled();
    }

    /**
     * Resolve one enchantment's fate on repair. Both RNG draws are supplied by the caller (the
     * port's RNG convention — {@code ProbabilityUtil} has no test seam), so the branching itself is
     * deterministic and fully testable.
     *
     * <p>Note the inverted sense of the second draw, which is legacy's: it rolls against
     * {@code 100 - getDowngradeEnchantChance()}, and a <em>success</em> means the enchantment
     * escaped being downgraded. A level-1 enchantment can never be downgraded (there is no level 0
     * to drop to) so it is kept outright.
     *
     * @param enchantLevel the enchantment's current level
     * @param keptRoll result of the keep roll against {@link #getKeepEnchantChance()}
     * @param downgradeAvoidedRoll result of the roll against
     *     {@code 100 - }{@link #getDowngradeEnchantChance()}; {@code true} means no downgrade
     * @return the outcome to apply to the item
     */
    public @NotNull ArcaneOutcome resolveEnchantOutcome(int enchantLevel, boolean keptRoll,
            boolean downgradeAvoidedRoll) {
        if (!keptRoll) {
            return ArcaneOutcome.LOST;
        }
        if (isArcaneForgingDowngradeEnabled() && enchantLevel > 1 && !downgradeAvoidedRoll) {
            return ArcaneOutcome.DOWNGRADED;
        }
        return ArcaneOutcome.KEPT;
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

    /**
     * The double-click confirmation gate for using a repair anvil (legacy {@code checkConfirmation}).
     * When {@code Repair.Confirm_Required} is on, the first anvil click within the 3-second window
     * only <em>arms</em> the repair (records the click and prompts "confirm or cancel"); a second
     * click within the window actually performs it. Returns {@code true} when the caller may proceed
     * with the repair — i.e. the cooldown has not expired (this is the confirming second click) or
     * confirmation is disabled entirely. MC-free: notification routing is handled by the ported
     * {@link NotificationManager}.
     *
     * @param actualize whether to record this click (and prompt) when it is the arming first click;
     *     pass {@code false} to peek at the confirmation state without mutating it
     * @return {@code true} if the repair may proceed now, {@code false} if it was merely armed
     */
    public boolean checkConfirmation(boolean actualize) {
        long lastUse = getLastAnvilUse();

        if (!SkillUtils.cooldownExpired(lastUse, 3)
                || !McMMOMod.getGeneralConfig().getRepairConfirmRequired()) {
            return true;
        }

        if (!actualize) {
            return false;
        }

        actualizeLastAnvilUse();
        NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_MESSAGE,
                "Skills.ConfirmOrCancel", LocaleLoader.getString("Repair.Pretty.Name"));

        return false;
    }
}
