package com.gmail.nossr50.config.skills.salvage;

import com.gmail.nossr50.config.ConfigLoader;
import com.gmail.nossr50.datatypes.skills.ItemType;
import com.gmail.nossr50.datatypes.skills.MaterialType;
import com.gmail.nossr50.platform.Materials;
import com.gmail.nossr50.skills.salvage.salvageables.Salvageable;
import com.gmail.nossr50.skills.salvage.salvageables.SalvageableFactory;
import com.gmail.nossr50.util.ItemUtils;
import com.gmail.nossr50.util.skills.SkillUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
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

    public SalvageConfig(Path dataFolder) {
        super(FILENAME, dataFolder);
        loadKeys();
    }

    @Override
    protected void loadKeys() {
        salvageables = new ArrayList<>();

        if (!config.isConfigurationSection("Salvageables")) {
            LOGGER.error("Could not find Salvageables section in {}", FILENAME);
            return;
        }

        final List<String> notSupported = new ArrayList<>();

        for (String key : config.getConfigurationSection("Salvageables").getKeys(false)) {
            final String base = "Salvageables." + key;

            final java.util.Optional<Item> itemOpt = Materials.item(key);
            if (itemOpt.isEmpty()) {
                notSupported.add(key);
                continue;
            }
            final Item item = itemOpt.get();
            final String itemPath = Registries.ITEM.getId(item).getPath();
            final ItemStack probe = new ItemStack(item);

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
                salvageMaterialType = classifyMaterialType(probe);
            }

            // Salvage material: explicit name, else the family default.
            final String salvageMaterialName = config.getString(base + ".SalvageMaterial");
            final String resolvedName = salvageMaterialName != null
                    ? salvageMaterialName
                    : salvageMaterialType.getDefaultMaterial();
            if (resolvedName == null || !Materials.isItem(resolvedName)) {
                notSupported.add(key);
                continue;
            }
            final String salvageMaterialPath = Materials.idOf(resolvedName).getPath();

            final short maximumDurability = (short) probe.getMaxDamage();

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
            } else if (ItemUtils.isMinecraftTool(probe)) {
                salvageItemType = ItemType.TOOL;
            } else if (ItemUtils.isArmor(probe)) {
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

        if (!notSupported.isEmpty()) {
            LOGGER.debug("Salvage config skipped {} unsupported item(s): {}", notSupported.size(),
                    String.join(", ", notSupported));
        }
        LOGGER.info("Loaded {} salvageables from {}", salvageables.size(), FILENAME);
    }

    /** Auto-classify an item probe into its {@link MaterialType} (legacy SalvageConfig fallback). */
    private static MaterialType classifyMaterialType(ItemStack probe) {
        if (ItemUtils.isWoodTool(probe)) {
            return MaterialType.WOOD;
        } else if (ItemUtils.isStoneTool(probe)) {
            return MaterialType.STONE;
        } else if (ItemUtils.isStringTool(probe)) {
            return MaterialType.STRING;
        } else if (ItemUtils.isPrismarineTool(probe)) {
            return MaterialType.PRISMARINE;
        } else if (ItemUtils.isLeatherArmor(probe)) {
            return MaterialType.LEATHER;
        } else if (ItemUtils.isIronArmor(probe) || ItemUtils.isIronTool(probe)) {
            return MaterialType.IRON;
        } else if (ItemUtils.isGoldArmor(probe) || ItemUtils.isGoldTool(probe)) {
            return MaterialType.GOLD;
        } else if (ItemUtils.isDiamondArmor(probe) || ItemUtils.isDiamondTool(probe)) {
            return MaterialType.DIAMOND;
        } else if (ItemUtils.isNetheriteTool(probe) || ItemUtils.isNetheriteArmor(probe)) {
            return MaterialType.NETHERITE;
        } else if (ItemUtils.isCopperTool(probe) || ItemUtils.isCopperArmor(probe)) {
            return MaterialType.COPPER;
        }
        return MaterialType.OTHER;
    }

    /** The salvageables parsed from the config. */
    public List<Salvageable> getLoadedSalvageables() {
        return salvageables == null ? new ArrayList<>() : salvageables;
    }
}
