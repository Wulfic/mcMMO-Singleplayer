package com.gmail.nossr50.config.skills.repair;

import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.config.ConfigIdSkips;
import com.gmail.nossr50.config.ConfigLoader;
import com.gmail.nossr50.datatypes.skills.ItemType;
import com.gmail.nossr50.datatypes.skills.MaterialType;
import com.gmail.nossr50.platform.Materials;
import com.gmail.nossr50.skills.repair.repairables.Repairable;
import com.gmail.nossr50.skills.repair.repairables.RepairableFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code repair.vanilla.yml} — the table of items mcMMO Repair can restore and the material each is
 * repaired with. Ported onto {@link ConfigLoader}.
 *
 * <p>Unlike the MC-free {@link com.gmail.nossr50.config.treasure.TreasureConfig}, this config is
 * MC-typed at load time: resolving each entry needs the live item registry (existence + registry
 * path), the vanilla max-durability of the item, and {@link ItemUtils} auto-classification of an
 * item probe. Those calls are only valid once Minecraft's registries are populated (server start),
 * exactly like {@code ItemUtils}/{@code BlockUtils}; the unit test drives it under the
 * {@code fabric-loader-junit} registry harness. When registries are absent (e.g. an un-bootstrapped
 * unit context) every entry simply resolves to "unsupported" and the table loads empty — no crash.
 *
 * <p>Faithful to legacy {@code RepairConfig} with two deliberate omissions: the legacy {@code ItemId}
 * key auto-migration/backup (a fresh singleplayer install ships the current format) and the Bukkit
 * permission wiring (singleplayer always allows). The {@code MaterialType}/{@code ItemType} fields
 * fall back to {@link ItemUtils} classification when unset — matching upstream, which also ignores
 * the descriptive {@code ItemMaterialCategory} YAML comment key.
 */
public class RepairConfig extends ConfigLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("mcMMO/RepairConfig");

    public static final String FILENAME = "repair.vanilla.yml";

    private List<Repairable> repairables = new ArrayList<>();

    private final ConfigIdSkips skips = new ConfigIdSkips(FILENAME);

    public RepairConfig(Path dataFolder) {
        super(FILENAME, dataFolder);
        loadKeys();
    }

    /** Rows dropped because this Minecraft version has no such item (TODO 5.5). */
    public ConfigIdSkips getSkips() {
        return skips;
    }

    @Override
    protected void loadKeys() {
        repairables = new ArrayList<>();

        if (!config.isConfigurationSection("Repairables")) {
            LOGGER.error("Could not find Repairables section in {}", FILENAME);
            return;
        }

        // Nothing below can resolve without a live registry, and every miss would log. This is the
        // "registries absent" path the class javadoc documents (table loads empty, no crash) —
        // stated once here rather than as N warnings that say nothing about the config.
        if (!Materials.itemRegistryIsPopulated()) {
            LOGGER.debug("Item registry not populated; {} loads empty.", FILENAME);
            return;
        }

        for (String key : config.getConfigurationSection("Repairables").getKeys(false)) {
            final String base = "Repairables." + key;

            // TODO 5.5: test existence with isItem, which does NOT log, before itemPath, which warns
            // once per miss via Materials#item. Those per-id warnings are what the single summary
            // line replaces; leaving them in would make the summary redundant noise on any band
            // missing a tier (the 7 spears below 1.21.11, all of copper below 1.21.9).
            if (!skips.keepItem("Repairables", key)) {
                continue;
            }
            final String itemPath = Materials.itemPath(key).orElseThrow();

            final List<String> reasons = new ArrayList<>();

            // Material family: explicit MaterialType override, else auto-classify the probe.
            MaterialType repairMaterialType = MaterialType.OTHER;
            if (config.contains(base + ".MaterialType")) {
                final String name = config.getString(base + ".MaterialType", "OTHER");
                try {
                    repairMaterialType = MaterialType.valueOf(name);
                } catch (IllegalArgumentException ex) {
                    reasons.add(key + " has an invalid MaterialType of " + name);
                }
            } else {
                repairMaterialType = classifyMaterialType(itemPath);
            }

            // Repair material: explicit name, else the family default.
            final String repairMaterialName = config.getString(base + ".RepairMaterial");
            final String resolvedName = repairMaterialName != null
                    ? repairMaterialName
                    : repairMaterialType.getDefaultMaterial();
            if (resolvedName == null || !Materials.isItem(resolvedName)) {
                // The repaired item exists but its repair MATERIAL does not — same outcome for the
                // player, so it is recorded the same way, naming the material that was missing.
                skips.record("Repairables", key + " (repair material " + resolvedName + ")");
                continue;
            }
            final String repairMaterialPath = Materials.idOf(resolvedName).getPath();

            // Maximum durability: vanilla value, falling back to the config for non-damageable items.
            short maximumDurability = (short) Materials.maxDamage(key);
            if (maximumDurability <= 0) {
                maximumDurability = (short) config.getInt(base + ".MaximumDurability");
            }
            if (maximumDurability <= 0) {
                reasons.add("Maximum durability of " + key + " must be greater than 0!");
            }

            // Item type: explicit override, else auto-classify the probe.
            ItemType repairItemType = ItemType.OTHER;
            if (config.contains(base + ".ItemType")) {
                final String name = config.getString(base + ".ItemType", "OTHER");
                try {
                    repairItemType = ItemType.valueOf(name);
                } catch (IllegalArgumentException ex) {
                    reasons.add(key + " has an invalid ItemType of " + name);
                }
            } else if (store().isTool(itemPath)) {
                repairItemType = ItemType.TOOL;
            } else if (store().isArmor(itemPath)) {
                repairItemType = ItemType.ARMOR;
            }

            final int minimumLevel = config.getInt(base + ".MinimumLevel");
            final double xpMultiplier = config.getDouble(base + ".XpMultiplier", 1);
            if (minimumLevel < 0) {
                reasons.add(key + " has an invalid MinimumLevel of " + minimumLevel);
            }

            // Minimum quantity: 0 (unset) means "resolve from the recipe-count table" (see
            // SimpleRepairable.getMinimumQuantity), signalled by -1.
            int minimumQuantity = config.getInt(base + ".MinimumQuantity");
            if (minimumQuantity == 0) {
                minimumQuantity = -1;
            }

            if (!reasons.isEmpty()) {
                reasons.forEach(LOGGER::warn);
                continue;
            }

            repairables.add(RepairableFactory.getRepairable(itemPath, repairMaterialPath, null,
                    minimumLevel, maximumDurability, repairItemType, repairMaterialType, xpMultiplier,
                    minimumQuantity));
        }

        // TODO 5.5: this used to be DEBUG, which is indistinguishable from silence in a normal boot
        // log — and below 1.21.11 it hides the seven spears, below 1.21.9 the whole copper tier.
        // The report is the only artefact that says a shipped repairable is not available here.
        skips.logSummary(LOGGER);
        LOGGER.info("Loaded {} repairables from {}", repairables.size(), FILENAME);
    }

    /** Auto-classify an item probe into its {@link MaterialType} (legacy RepairConfig fallback). */
    /**
     * The MC-free material classifier. It is keyed on the vanilla registry-id <em>path</em>
     * ({@code diamond_axe}), which is exactly what this loader already resolves each config key to,
     * so the auto-classification below needs no {@code ItemStack} probe and no Minecraft types at all.
     */
    private static com.gmail.nossr50.util.MaterialMapStore store() {
        return McMMOMod.getMaterialMapStore();
    }

    private static MaterialType classifyMaterialType(String itemPath) {
        if (store().isWoodTool(itemPath)) {
            return MaterialType.WOOD;
        } else if (store().isStoneTool(itemPath)) {
            return MaterialType.STONE;
        } else if (store().isStringTool(itemPath)) {
            return MaterialType.STRING;
        } else if (store().isLeatherArmor(itemPath)) {
            return MaterialType.LEATHER;
        } else if (store().isIronArmor(itemPath) || store().isIronTool(itemPath)) {
            return MaterialType.IRON;
        } else if (store().isGoldArmor(itemPath) || store().isGoldTool(itemPath)) {
            return MaterialType.GOLD;
        } else if (store().isDiamondArmor(itemPath) || store().isDiamondTool(itemPath)) {
            return MaterialType.DIAMOND;
        } else if (store().isNetheriteArmor(itemPath) || store().isNetheriteTool(itemPath)) {
            return MaterialType.NETHERITE;
        } else if (store().isCopperTool(itemPath) || store().isCopperArmor(itemPath)) {
            return MaterialType.COPPER;
        }
        return MaterialType.OTHER;
    }

    /** The repairables parsed from the config. */
    public List<Repairable> getLoadedRepairables() {
        return repairables == null ? new ArrayList<>() : repairables;
    }
}
