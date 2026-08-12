package com.gmail.nossr50.config.skills.salvage;

import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.config.ConfigIdSkips;
import com.gmail.nossr50.config.ConfigLoader;
import com.gmail.nossr50.datatypes.skills.ItemType;
import com.gmail.nossr50.datatypes.skills.MaterialType;
import com.gmail.nossr50.platform.Materials;
import com.gmail.nossr50.skills.salvage.salvageables.Salvageable;
import com.gmail.nossr50.skills.salvage.salvageables.SalvageableFactory;
import com.gmail.nossr50.util.skills.SkillUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code salvage.vanilla.yml} — the table of items mcMMO Salvage can break down and the material each
 * yields. Ported onto {@link ConfigLoader}; MC-typed at load time exactly like
 * {@link com.gmail.nossr50.config.skills.repair.RepairConfig} (registry existence + path, vanilla
 * max-durability, {@link ItemUtils} auto-classification), so it is Knot-harness tested and degrades
 * to an empty table when registries are absent.
 *
 * <p>Faithful to legacy {@code SalvageConfig} minus the one-time {@code FIX_NETHERITE_SALVAGE_QUANTITIES}
 * upgrade migration (a fresh singleplayer install ships the corrected quantities) and the Bukkit
 * permission wiring. Note {@code Salvage} grants <b>no XP</b> in mcMMO — the {@code XpMultiplier} field
 * is parsed for fidelity but unused.
 */
public class SalvageConfig extends ConfigLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("mcMMO/SalvageConfig");

    public static final String FILENAME = "salvage.vanilla.yml";

    private List<Salvageable> salvageables = new ArrayList<>();

    private final ConfigIdSkips skips = new ConfigIdSkips(FILENAME);

    public SalvageConfig(Path dataFolder) {
        super(FILENAME, dataFolder);
        loadKeys();
    }

    /** Rows dropped because this Minecraft version has no such item (TODO 5.5). */
    public ConfigIdSkips getSkips() {
        return skips;
    }

    @Override
    protected void loadKeys() {
        salvageables = new ArrayList<>();

        if (!config.isConfigurationSection("Salvageables")) {
            LOGGER.error("Could not find Salvageables section in {}", FILENAME);
            return;
        }

        // See RepairConfig: stated once, rather than as one warning per unresolvable row.
        if (!Materials.itemRegistryIsPopulated()) {
            LOGGER.debug("Item registry not populated; {} loads empty.", FILENAME);
            return;
        }

        for (String key : config.getConfigurationSection("Salvageables").getKeys(false)) {
            final String base = "Salvageables." + key;

            // TODO 5.5: isItem does not log, itemPath does — see RepairConfig for the argument.
            if (!skips.keepItem("Salvageables", key)) {
                continue;
            }
            final String itemPath = Materials.itemPath(key).orElseThrow();

            final List<String> reasons = new ArrayList<>();

            // Material family: explicit MaterialType override, else auto-classify the probe.
            MaterialType salvageMaterialType = MaterialType.OTHER;
            if (config.contains(base + ".MaterialType")) {
                final String name = config.getString(base + ".MaterialType", "OTHER")
                        .replace(" ", "_").toUpperCase(Locale.ENGLISH);
                try {
                    salvageMaterialType = MaterialType.valueOf(name);
                } catch (IllegalArgumentException ex) {
                    reasons.add(key + " has an invalid MaterialType of " + name);
                }
            } else {
                salvageMaterialType = classifyMaterialType(itemPath);
            }

            // Salvage material: explicit name, else the family default.
            final String salvageMaterialName = config.getString(base + ".SalvageMaterial");
            final String resolvedName = salvageMaterialName != null
                    ? salvageMaterialName
                    : salvageMaterialType.getDefaultMaterial();
            if (resolvedName == null || !Materials.isItem(resolvedName)) {
                skips.record("Salvageables", key + " (salvage material " + resolvedName + ")");
                continue;
            }
            final String salvageMaterialPath = Materials.idOf(resolvedName).getPath();

            final short maximumDurability = (short) Materials.maxDamage(key);

            // Item type: explicit override, else auto-classify the probe.
            ItemType salvageItemType = ItemType.OTHER;
            if (config.contains(base + ".ItemType")) {
                final String name = config.getString(base + ".ItemType", "OTHER")
                        .replace(" ", "_").toUpperCase(Locale.ENGLISH);
                try {
                    salvageItemType = ItemType.valueOf(name);
                } catch (IllegalArgumentException ex) {
                    reasons.add(key + " has an invalid ItemType of " + name);
                }
            } else if (store().isTool(itemPath)) {
                salvageItemType = ItemType.TOOL;
            } else if (store().isArmor(itemPath)) {
                salvageItemType = ItemType.ARMOR;
            }

            final int minimumLevel = config.getInt(base + ".MinimumLevel");
            final double xpMultiplier = config.getDouble(base + ".XpMultiplier", 1);
            if (minimumLevel < 0) {
                reasons.add(key + " has an invalid MinimumLevel of " + minimumLevel);
            }

            // Maximum quantity: standard recipe count, overridden by an explicit config value.
            int maximumQuantity = SkillUtils.getRepairAndSalvageQuantities(itemPath);
            if (maximumQuantity <= 0) {
                maximumQuantity = config.getInt(base + ".MaximumQuantity", 1);
            }
            final int configMaximumQuantity = config.getInt(base + ".MaximumQuantity", -1);
            if (configMaximumQuantity > 0) {
                maximumQuantity = configMaximumQuantity;
            }
            if (maximumQuantity <= 0) {
                reasons.add("Maximum quantity of " + key + " must be greater than 0!");
            }

            if (!reasons.isEmpty()) {
                reasons.forEach(LOGGER::warn);
                continue;
            }

            salvageables.add(SalvageableFactory.getSalvageable(itemPath, salvageMaterialPath,
                    minimumLevel, maximumQuantity, maximumDurability, salvageItemType,
                    salvageMaterialType, xpMultiplier));
        }

        // TODO 5.5: was DEBUG, i.e. silent in a normal boot log. See RepairConfig for the argument.
        skips.logSummary(LOGGER);
        LOGGER.info("Loaded {} salvageables from {}", salvageables.size(), FILENAME);
    }

    /** Auto-classify an item probe into its {@link MaterialType} (legacy SalvageConfig fallback). */
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
        } else if (store().isPrismarineTool(itemPath)) {
            return MaterialType.PRISMARINE;
        } else if (store().isLeatherArmor(itemPath)) {
            return MaterialType.LEATHER;
        } else if (store().isIronArmor(itemPath) || store().isIronTool(itemPath)) {
            return MaterialType.IRON;
        } else if (store().isGoldArmor(itemPath) || store().isGoldTool(itemPath)) {
            return MaterialType.GOLD;
        } else if (store().isDiamondArmor(itemPath) || store().isDiamondTool(itemPath)) {
            return MaterialType.DIAMOND;
        } else if (store().isNetheriteTool(itemPath) || store().isNetheriteArmor(itemPath)) {
            return MaterialType.NETHERITE;
        } else if (store().isCopperTool(itemPath) || store().isCopperArmor(itemPath)) {
            return MaterialType.COPPER;
        }
        return MaterialType.OTHER;
    }

    /** The salvageables parsed from the config. */
    public List<Salvageable> getLoadedSalvageables() {
        return salvageables == null ? new ArrayList<>() : salvageables;
    }
}
