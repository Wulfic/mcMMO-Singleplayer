package com.gmail.nossr50.skills.salvage;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.skills.RankUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Salvage skill manager (Phase 10.3 port, legacy 327 lines). The portable numeric core — the
 * salvage-yield-from-damage formula, the Scrap Collector yield limit, the Arcane Salvage rank and
 * full/partial enchant-extraction chances, the all-enchants-failed predicate, and the anvil
 * placement/last-use state — survives as pure functions or plain state. Every body that touches a
 * live {@code ItemStack}/{@code ItemMeta}, spawns items, mutates the inventory, builds an enchanted
 * book, reads the {@code SalvageableManager}, or fires the salvage-check event is deferred until
 * those adapters and the salvageable-item config land — same convention as
 * {@link com.gmail.nossr50.skills.repair.RepairManager}.
 *
 * <p><b>Deferred until the item/inventory/enchant adapters + the salvageable-item config land
 * (PORT Phase 10/11):</b>
 * <ul>
 *   <li>{@code handleSalvage} — the whole salvage action: needs {@code SalvageableManager},
 *       {@code ItemStack}/{@code ItemMeta} inspection, item-spawn adapters, the salvage-check event,
 *       and {@code SkillUtils};</li>
 *   <li>{@code arcaneSalvageCheck} — builds the extracted enchanted book, needs live
 *       {@code Enchantment}/{@code EnchantmentStorageMeta} (its chances are ported below);</li>
 *   <li>{@code placedAnvilCheck} — needs notification/sound adapters (anvil-placed state is ported);</li>
 *   <li>{@code checkConfirmation} — the anvil-use hook (K7) + notification; {@link
 *       com.gmail.nossr50.util.skills.SkillUtils#cooldownExpired} it depends on is now ported.</li>
 * </ul>
 */
public class SalvageManager extends SkillManager {
    private boolean placedAnvil;
    private int lastClick;

    public SalvageManager(@NotNull McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.SALVAGE);
    }

    /**
     * The number of salvage materials yielded by an item, scaled by how damaged it is. Legacy
     * {@code Salvage.calculateSalvageableAmount} — pure. A pristine item yields the full base amount;
     * a fully-damaged item yields nothing.
     *
     * @param currentDurability the item's current durability (damage) value
     * @param maxDurability the item's maximum durability
     * @param baseAmount the salvageable's base (max) yield quantity
     * @return the salvage yield before the Scrap Collector limit is applied
     */
    public static int calculateSalvageableAmount(int currentDurability, short maxDurability,
            int baseAmount) {
        double percentDamaged = (maxDurability <= 0) ? 1D
                : (double) (maxDurability - currentDurability) / maxDurability;

        return (int) Math.floor(baseAmount * percentDamaged);
    }

    /**
     * The Scrap Collector yield cap: rank 1 returns exactly 1, higher ranks return {@code rank * 2}.
     * Legacy {@code getSalvageLimit(Player)} retargeted to {@link PlatformPlayer}.
     *
     * @param player the player whose Scrap Collector rank sets the cap
     * @return the maximum salvage yield this player may receive
     */
    public static int getSalvageLimit(@NotNull PlatformPlayer player) {
        int rank = RankUtils.getRank(player, SubSkillType.SALVAGE_SCRAP_COLLECTOR);
        if (rank == 1) {
            return 1;
        }
        return rank * 2;
    }

    /**
     * @return the current Arcane Salvage rank
     */
    public int getArcaneSalvageRank() {
        return RankUtils.getRank(getPlayer(), SubSkillType.SALVAGE_ARCANE_SALVAGE);
    }

    /**
     * @return the chance (0-100) of extracting an enchantment at full level, by Arcane Salvage rank
     *     (100 if the player holds the enchant-bypass perk — never granted in singleplayer)
     */
    public double getExtractFullEnchantChance() {
        if (Permissions.hasSalvageEnchantBypassPerk(getPlayer())) {
            return 100.0D;
        }

        return McMMOMod.getAdvancedConfig()
                .getArcaneSalvageExtractFullEnchantsChance(getArcaneSalvageRank());
    }

    /**
     * @return the chance (0-100) of extracting an enchantment at a downgraded level, by rank
     */
    public double getExtractPartialEnchantChance() {
        return McMMOMod.getAdvancedConfig()
                .getArcaneSalvageExtractPartialEnchantsChance(getArcaneSalvageRank());
    }

    /**
     * @param arcaneFailureCount the number of enchantments that failed to extract
     * @param size the total number of enchantments on the item
     * @return whether every enchantment failed to extract
     */
    public boolean failedAllEnchants(int arcaneFailureCount, int size) {
        return arcaneFailureCount == size;
    }

    /*
     * Salvage Anvil placement state
     */

    public boolean getPlacedAnvil() {
        return placedAnvil;
    }

    public void togglePlacedAnvil() {
        placedAnvil = !placedAnvil;
    }

    /*
     * Salvage Anvil usage state
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
