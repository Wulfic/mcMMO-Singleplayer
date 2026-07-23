package com.gmail.nossr50.config.treasure;

import com.gmail.nossr50.config.ConfigLoader;
import com.gmail.nossr50.datatypes.treasure.EnchantmentTreasure;
import com.gmail.nossr50.datatypes.treasure.FishingTreasure;
import com.gmail.nossr50.datatypes.treasure.FishingTreasureBook;
import com.gmail.nossr50.datatypes.treasure.ItemSpec;
import com.gmail.nossr50.datatypes.treasure.Rarity;
import com.gmail.nossr50.datatypes.treasure.ShakeTreasure;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
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
 * MC-free and plain-JUnit testable. One reward shape is still deferred, a genuine adapter gap rather
 * than a mechanical skip:
 * <ul>
 *   <li><b>Potion Fishing rewards</b> — {@link ItemSpec} carries no potion base-type yet, so these are
 *       skipped by name with a log (the shipped {@code Fishing} section has none; the {@code Shake}
 *       section does).</li>
 * </ul>
 *
 * <p><b>Enchanted-book rewards are now loaded</b> as {@link FishingTreasureBook}, the one reward whose
 * enchantments do <i>not</i> come from the Magic Hunter table below: it draws one random enchantment
 * from the whole registry, narrowed by its own {@code Enchantments_Whitelist}/{@code _Blacklist}. Both
 * lists are kept as registry-path strings and resolved at drop time for the same dynamic-registry
 * reason as the Magic Hunter table. Faithful to legacy, a book ignores its configured {@code Amount}
 * and {@code Lore} (legacy builds it as {@code new ItemStack(material, 1)} and applies only the custom
 * name).
 *
 * <p><b>The Magic Hunter enchant tables are now loaded</b> ({@link #fishingEnchantments} +
 * {@link #getEnchantmentDropRate}, consumed by {@code FishingManager#rollMagicHunterRarity} /
 * {@code selectMagicHunterEnchants}). Legacy resolved each {@code Enchantments_Rarity} key to a live
 * Bukkit {@code Enchantment} at load; 1.21's enchantment registry is <b>dynamic</b> and does not exist
 * at config-load, so entries are kept as {@link EnchantmentTreasure} registry-path strings and resolved
 * at drop time — the same resolve-at-use shape as the Shake map's entity paths. <b>The "dynamic enchant
 * registry adapter" that deferred this turned out to be one {@code toLowerCase}:</b> every name the
 * shipped config uses ({@code EFFICIENCY}, {@code BANE_OF_ARTHROPODS}, …) is the modern Bukkit spelling,
 * which since 1.20.5 <i>is</i> the vanilla registry path. Legacy's {@code EnchantmentUtils} alias table
 * (which mapped those names back to pre-1.20.5 Bukkit enums like {@code DIG_SPEED}) is therefore not
 * ported: no shipped key needs it, and a hand-edited unknown name is reported at drop time.
 *
 * <p><b>The {@code Shake} map is now loaded</b> ({@link #shakeMap}, consumed by
 * {@code FishingManager#rollShakeTreasure}). Legacy keyed it by Bukkit {@code EntityType} and built it
 * by iterating {@code EntityType.values()}; this port keys it by the entity's <b>vanilla registry
 * path</b> ({@code "cave_spider"}) and iterates the config's own section names instead — the same
 * resolve-at-use-time shape the sibling {@link TreasureConfig} uses for its Hylian groups, and it keeps
 * this config MC-free (no entity registry read at load). Section names are lower-cased, with an alias
 * table for the three sections whose Bukkit enum names no longer match the registry (see
 * {@link #ENTITY_SECTION_ALIASES}). Two entry shapes are skipped by name, each a real adapter gap:
 * potion drops (Cave Spider's poison potion, the Witch's splash potions — {@link ItemSpec} carries no
 * potion base type yet) and the {@code PLAYER.INVENTORY} steal (legacy's magic-{@code BEDROCK}
 * inventory raid, unreachable in singleplayer where the only player is the angler).
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

    /**
     * The three {@code Shake} section names whose Bukkit enum spelling no longer matches the vanilla
     * entity registry, mapped to their registry paths.
     *
     * <p><b>This table is a deliberate fix, not a transcription (CONVERSION_TODO §F).</b> Legacy builds
     * the shake map by iterating the live {@code EntityType.values()} and reading
     * {@code "Shake." + entity}, so a section whose name is not a current enum constant is never
     * looked up: {@code PIG_ZOMBIE} was removed from Bukkit in 1.16 (it is {@code ZOMBIFIED_PIGLIN}),
     * and {@code MUSHROOM_COW}/{@code SNOWMAN} were renamed to {@code MOOSHROOM}/{@code SNOW_GOLEM} in
     * 1.20.5 — the very API version the vendored tree builds against. All three sections still ship in
     * {@code fishing_treasures.yml}, so upstream promises drops for those mobs and delivers none.
     * Aliasing them here makes the shipped config mean what it says.
     */
    private static final Map<String, String> ENTITY_SECTION_ALIASES = Map.of(
            "mushroom_cow", "mooshroom",
            "pig_zombie", "zombified_piglin",
            "snowman", "snow_golem");

    /** Treasure-Hunter fishing rewards, bucketed by rarity. Every rarity is present (possibly empty). */
    public final @NotNull Map<Rarity, List<FishingTreasure>> fishingRewards = new EnumMap<>(Rarity.class);

    /**
     * Shake drops keyed by the target's vanilla entity registry path (e.g. {@code "cave_spider"}).
     * Entities with no configured drops are simply absent — use {@link #getShakeTreasures(String)}.
     */
    public final @NotNull Map<String, List<ShakeTreasure>> shakeMap = new HashMap<>();

    /**
     * Magic Hunter enchantments, bucketed by rarity. Every rarity is present (possibly empty); each
     * entry names a vanilla enchantment registry path, resolved against the dynamic registry at drop
     * time. Use {@link #getEnchantmentTreasures(Rarity)}.
     */
    public final @NotNull Map<Rarity, List<EnchantmentTreasure>> fishingEnchantments =
            new EnumMap<>(Rarity.class);

    public FishingTreasureConfig(Path dataFolder) {
        super(FILENAME, dataFolder);
        loadKeys();
    }

    @Override
    protected void loadKeys() {
        // Seed every rarity so the reward roll can index any bucket without a null check (legacy parity).
        for (Rarity rarity : Rarity.values()) {
            fishingRewards.put(rarity, new ArrayList<>());
            fishingEnchantments.put(rarity, new ArrayList<>());
        }

        loadFishingRewards();
        loadShakeTreasures();
        loadEnchantments();

        LOGGER.info("Loaded {} fishing treasures, {} magic-hunter enchantments and {} shake drops"
                        + " across {} entities from {}",
                fishingRewards.values().stream().mapToInt(List::size).sum(),
                fishingEnchantments.values().stream().mapToInt(List::size).sum(),
                shakeMap.values().stream().mapToInt(List::size).sum(), shakeMap.size(), FILENAME);
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

            // Deferred reward shape (see class javadoc): potions need a potion base-type on ItemSpec.
            if (isPotionEntry(materialName)) {
                LOGGER.debug("Skipping deferred fishing reward '{}' (potion — ItemSpec carries no"
                        + " potion base type).", treasureName);
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

            // An enchanted book is its own treasure type: it always arrives carrying one random
            // enchantment (see FishingTreasureBook), so it carries the two enchantment filters
            // instead of a stack size and lore. Legacy builds it with `new ItemStack(material, 1)`
            // and applies only the custom name, so a configured Amount/Lore is ignored for books —
            // kept faithfully rather than "fixed", since a stack of enchanted books cannot hold
            // per-book enchantments anyway.
            if (materialName.equalsIgnoreCase("ENCHANTED_BOOK")) {
                fishingRewards.get(rarity).add(new FishingTreasureBook(
                        new ItemSpec(materialId, 1, customName, List.of()), xp,
                        config.getStringList(base + ".Enchantments_Blacklist"),
                        config.getStringList(base + ".Enchantments_Whitelist")));
                continue;
            }

            final List<String> lore = config.getStringList(base + ".Lore");
            final ItemSpec item = new ItemSpec(materialId, amount, customName, lore);

            fishingRewards.get(rarity).add(new FishingTreasure(item, xp));
        }
    }

    /**
     * Load the {@code Shake} section — the per-mob drops the Shake sub-skill knocks loose. Ports the
     * shake half of legacy {@code loadTreasures}, with two structural differences (both in the class
     * javadoc): we walk the config's own entity sections rather than {@code EntityType.values()}, and
     * we key by vanilla registry path so no entity registry is touched at load.
     */
    private void loadShakeTreasures() {
        final var shakeSection = config.getConfigurationSection("Shake");
        if (shakeSection == null) {
            LOGGER.warn("No Shake section in {}; the Shake sub-skill will drop nothing.", FILENAME);
            return;
        }

        for (String entitySectionName : shakeSection.getKeys(false)) {
            final String entityPath = toEntityRegistryPath(entitySectionName);
            final var dropsSection = config.getConfigurationSection("Shake." + entitySectionName);
            if (dropsSection == null) {
                continue;
            }

            for (String dropName : dropsSection.getKeys(false)) {
                // Legacy's "MATERIAL|data|potionType" form: the block-data short is meaningless in
                // modern flattened MC, and the potion arms are deferred (see below), so keep the head.
                final String materialName = dropName.split("[|]")[0];

                // Deferred/dropped entry shapes, skipped by name (class javadoc): potion drops need a
                // potion base type on ItemSpec, and INVENTORY is legacy's magic-BEDROCK player-inventory
                // steal, which cannot fire in singleplayer (its only target is the angler themselves).
                if (isPotionEntry(materialName) || materialName.equalsIgnoreCase("INVENTORY")) {
                    LOGGER.debug("Skipping Shake drop '{}.{}' (potion/inventory-steal — deferred or"
                            + " unreachable in singleplayer).", entitySectionName, dropName);
                    continue;
                }

                final String base = "Shake." + entitySectionName + "." + dropName;

                int amount = config.getInt(base + ".Amount");
                if (amount <= 0) {
                    amount = 1;
                }

                final int xp = config.getInt(base + ".XP");
                if (xp < 0) {
                    LOGGER.warn("Shake drop {} has an invalid XP value: {}, skipping.", base, xp);
                    continue;
                }

                final double dropChance = config.getDouble(base + ".Drop_Chance");
                if (dropChance < 0.0) {
                    LOGGER.warn("Shake drop {} has an invalid Drop_Chance: {}, skipping.", base,
                            dropChance);
                    continue;
                }

                final ItemSpec item = new ItemSpec(materialName.toLowerCase(Locale.ROOT), amount,
                        config.getString(base + ".Custom_Name", null),
                        config.getStringList(base + ".Lore"));

                shakeMap.computeIfAbsent(entityPath, key -> new ArrayList<>())
                        .add(new ShakeTreasure(item, xp, dropChance,
                                config.getInt(base + ".Drop_Level")));
            }
        }
    }

    /**
     * Load the {@code Enchantments_Rarity} section — the Magic Hunter enchantment pool, bucketed by
     * rarity. Ports legacy {@code loadEnchantments} with one structural difference (see the class
     * javadoc): the enchantment is kept as a registry-path string rather than resolved to a live
     * {@code Enchantment} here, because 1.21's enchantment registry is dynamic and unavailable at
     * config-load. An unresolvable name therefore cannot be reported until drop time — which is where
     * {@code FishingListener} logs it — so this method only validates the level.
     */
    private void loadEnchantments() {
        for (Rarity rarity : Rarity.values()) {
            final var enchantmentSection = config.getConfigurationSection(
                    "Enchantments_Rarity." + rarity);
            if (enchantmentSection == null) {
                continue; // a rarity with no configured enchantments keeps its empty bucket.
            }

            for (String enchantmentName : enchantmentSection.getKeys(false)) {
                final int level = config.getInt("Enchantments_Rarity." + rarity + "."
                        + enchantmentName);
                if (level <= 0) {
                    LOGGER.warn("Enchantment {} ({}) has an invalid level: {}, skipping.",
                            enchantmentName, rarity, level);
                    continue;
                }

                fishingEnchantments.get(rarity).add(new EnchantmentTreasure(
                        enchantmentName.toLowerCase(Locale.ROOT), level));
            }
        }
    }

    /**
     * The vanilla entity registry path a {@code Shake} section addresses. Bukkit enum names are
     * upper-snake and the registry is lower-snake, so this is a lower-case plus the rename fixes in
     * {@link #ENTITY_SECTION_ALIASES}.
     */
    private static String toEntityRegistryPath(@NotNull String entitySectionName) {
        final String lowerCased = entitySectionName.toLowerCase(Locale.ROOT);
        return ENTITY_SECTION_ALIASES.getOrDefault(lowerCased, lowerCased);
    }

    /**
     * Whether a config entry names a potion. Matches legacy's own loose test (its {@code POTION},
     * {@code SPLASH_POTION} and {@code LINGERING_POTION} entries all carry a {@code PotionData} block
     * that {@link ItemSpec} cannot yet express).
     */
    private static boolean isPotionEntry(@NotNull String materialName) {
        return materialName.toUpperCase(Locale.ROOT).contains("POTION");
    }

    /**
     * The Shake drops configured for an entity, in config order (the order legacy's cumulative
     * drop-chance walk depends on).
     *
     * @param entityRegistryPath the target's vanilla entity registry path, e.g. {@code "cave_spider"}
     * @return the configured drops, or an empty list when the entity has none
     */
    public @NotNull List<ShakeTreasure> getShakeTreasures(@NotNull String entityRegistryPath) {
        return shakeMap.getOrDefault(entityRegistryPath, List.of());
    }

    /**
     * The chance (percent) that a fishing catch at loot {@code tier} yields a {@code rarity} treasure.
     * Reads the bundled {@code Item_Drop_Rates.Tier_&lt;tier&gt;} curve. Consumed by the (still deferred)
     * Treasure-Hunter roll once the reward-spawn path lands.
     */
    public double getItemDropRate(int tier, @NotNull Rarity rarity) {
        return config.getDouble("Item_Drop_Rates.Tier_" + tier + "." + rarity);
    }

    /**
     * The chance (percent) that a caught treasure at loot {@code tier} is enchanted at {@code rarity}
     * by Magic Hunter. Reads the bundled {@code Enchantment_Drop_Rates.Tier_&lt;tier&gt;} curve — a
     * table entirely separate from {@link #getItemDropRate}, so the item roll and the enchant roll are
     * independent draws (as in legacy).
     */
    public double getEnchantmentDropRate(int tier, @NotNull Rarity rarity) {
        return config.getDouble("Enchantment_Drop_Rates.Tier_" + tier + "." + rarity);
    }

    /**
     * The Magic Hunter enchantments configured for a rarity, in config order (they are shuffled before
     * selection, so the order carries no weight — see {@code FishingManager#selectMagicHunterEnchants}).
     *
     * @return the configured enchantments, or an empty list when the rarity has none
     */
    public @NotNull List<EnchantmentTreasure> getEnchantmentTreasures(@NotNull Rarity rarity) {
        return fishingEnchantments.getOrDefault(rarity, List.of());
    }
}
