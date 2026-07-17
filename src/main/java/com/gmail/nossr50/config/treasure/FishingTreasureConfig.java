package com.gmail.nossr50.config.treasure;

import com.gmail.nossr50.config.ConfigLoader;
import com.gmail.nossr50.datatypes.treasure.FishingTreasure;
import com.gmail.nossr50.datatypes.treasure.ItemSpec;
import com.gmail.nossr50.datatypes.treasure.Rarity;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code fishing_treasures.yml} — the Fishing Treasure-Hunter loot table and its per-tier/per-rarity
 * drop-rate curve. Ported onto {@link ConfigLoader}.
 *
 * <p><b>Port scope (singleplayer):</b> this port loads the plain-item <b>{@code Fishing}</b> rewards
 * (bucketed by {@link Rarity}) and the {@code Item_Drop_Rates} table — everything the Treasure Hunter
 * item roll needs. Like the MC-free sibling {@link TreasureConfig}, each reward is kept as an
 * {@link ItemSpec} blueprint and its real {@code ItemStack} is built at spawn time, so this config is
 * MC-free and plain-JUnit testable. The following pieces are deferred, each a genuine adapter gap
 * rather than a mechanical skip:
 * <ul>
 *   <li><b>{@code ENCHANTED_BOOK} / potion Fishing rewards</b> — a book reward is a legacy
 *       {@code FishingTreasureBook} (an enchant whitelist/blacklist resolved through the <b>dynamic</b>
 *       1.21 enchantment registry, which isn't populated at config-load) and its reward path
 *       (Magic Hunter) needs the K3 enchant-write surface; {@link ItemSpec} likewise carries no
 *       potion base-type yet. Both are skipped by name with a log, so the shipped {@code ENCHANTED_BOOK}
 *       entry simply doesn't enter the pool until that adapter lands.</li>
 *   <li><b>Magic Hunter enchant tables</b> ({@code Enchantments_Rarity} / {@code Enchantment_Drop_Rates})
 *       — same dynamic-enchant-registry gap, and their only consumer ({@code FishingManager#processMagicHunter}/
 *       {@code getPossibleEnchantments}) is itself deferred. Loading a table nothing reads would be
 *       readerless state, so it's left out entirely (see §F "config that lies").</li>
 *   <li><b>{@code Shake} map</b> ({@code EntityType} → drops, incl. potions and the {@code INVENTORY}
 *       steal) — needs a Bukkit-{@code EntityType}→registry-entity mapping <i>and</i> the
 *       {@code FishingManager#shakeCheck} body (a {@code LivingEntity} target), both unported. Left
 *       out for the same readerless-state reason.</li>
 * </ul>
 *
 * <p><b>Faithfulness note:</b> unlike legacy, unknown/unsupported materials are not filtered here —
 * an entry's {@code materialId} is resolved to a real item only at spawn time (by the shared
 * {@code ItemSpecBuilder}), so a bogus material would produce no drop rather than being dropped at
 * load. Moot for the bundled config, whose every {@code Fishing} material resolves in this MC version;
 * flagged for the consumer increment, which may add a load-time filter once the roll path lands.
 */
public class FishingTreasureConfig extends ConfigLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("mcMMO/FishingTreasureConfig");

    public static final String FILENAME = "fishing_treasures.yml";

    /** Treasure-Hunter fishing rewards, bucketed by rarity. Every rarity is present (possibly empty). */
    public final @NotNull Map<Rarity, List<FishingTreasure>> fishingRewards = new EnumMap<>(Rarity.class);

    public FishingTreasureConfig(Path dataFolder) {
        super(FILENAME, dataFolder);
        loadKeys();
    }

    @Override
    protected void loadKeys() {
        // Seed every rarity so the reward roll can index any bucket without a null check (legacy parity).
        for (Rarity rarity : Rarity.values()) {
            fishingRewards.put(rarity, new ArrayList<>());
        }

        loadFishingRewards();

        LOGGER.info("Loaded {} fishing treasures from {}",
                fishingRewards.values().stream().mapToInt(List::size).sum(), FILENAME);
    }

    private void loadFishingRewards() {
        final var section = config.getConfigurationSection("Fishing");
        if (section == null) {
            LOGGER.warn("No Fishing section in {}; no treasure-hunter rewards loaded.", FILENAME);
            return;
        }

        for (String treasureName : section.getKeys(false)) {
            // Legacy allowed a "MATERIAL|data" form; the trailing block-data short is meaningless in
            // modern flattened MC, so keep only the material portion (the Fishing section uses none).
            final String materialName = treasureName.split("[|]")[0];

            // Deferred reward shapes (see class javadoc): enchanted books need the dynamic enchant
            // registry + K3 enchant-write; potions need a potion base-type on ItemSpec. Skip by name.
            if (materialName.equalsIgnoreCase("ENCHANTED_BOOK") || materialName.contains("POTION")) {
                LOGGER.debug("Skipping deferred fishing reward '{}' (book/potion — needs enchant/potion"
                        + " adapter).", treasureName);
                continue;
            }

            final String base = "Fishing." + treasureName;
            final String materialId = materialName.toLowerCase(Locale.ROOT);

            int amount = config.getInt(base + ".Amount");
            if (amount <= 0) {
                amount = 1;
            }

            final int xp = config.getInt(base + ".XP");
            if (xp < 0) {
                LOGGER.warn("{} has an invalid XP value: {}, skipping.", treasureName, xp);
                continue;
            }

            final String rarityStr = config.getString(base + ".Rarity", null);
            if (rarityStr == null) {
                LOGGER.error("Fishing treasure {} has no Rarity defined, skipping.", treasureName);
                continue;
            }
            final Rarity rarity = Rarity.getRarity(rarityStr);

            final String customName = config.getString(base + ".Custom_Name", null);
            final List<String> lore = config.getStringList(base + ".Lore");
            final ItemSpec item = new ItemSpec(materialId, amount, customName, lore);

            fishingRewards.get(rarity).add(new FishingTreasure(item, xp));
        }
    }

    /**
     * The chance (percent) that a fishing catch at loot {@code tier} yields a {@code rarity} treasure.
     * Reads the bundled {@code Item_Drop_Rates.Tier_&lt;tier&gt;} curve. Consumed by the (still deferred)
     * Treasure-Hunter roll once the reward-spawn path lands.
     */
    public double getItemDropRate(int tier, @NotNull Rarity rarity) {
        return config.getDouble("Item_Drop_Rates.Tier_" + tier + "." + rarity);
    }
}
