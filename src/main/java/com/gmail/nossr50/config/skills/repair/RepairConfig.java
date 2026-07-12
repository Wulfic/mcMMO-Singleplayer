package com.gmail.nossr50.config.skills.repair;

import com.gmail.nossr50.config.ConfigLoader;
import com.gmail.nossr50.datatypes.skills.ItemType;
import com.gmail.nossr50.datatypes.skills.MaterialType;
import com.gmail.nossr50.platform.Materials;
import com.gmail.nossr50.skills.repair.repairables.Repairable;
import com.gmail.nossr50.skills.repair.repairables.RepairableFactory;
import com.gmail.nossr50.util.ItemUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
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

    public RepairConfig(Path dataFolder) {
        super(FILENAME, dataFolder);
        loadKeys();
    }

    @Override
    protected void loadKeys() {
        repairables = new ArrayList<>();

        if (!config.isConfigurationSection("Repairables")) {
            LOGGER.error("Could not find Repairables section in {}", FILENAME);
            return;
        }

        final List<String> notSupported = new ArrayList<>();

        for (String key : config.getConfigurationSection("Repairables").getKeys(false)) {
            final String base = "Repairables." + key;

            // Resolve the item to repair. matchMaterial-style lookup: unknown items (not in this
            // MC version, or registries not yet loaded) are collected and skipped, not fatal.
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
            MaterialType repairMaterialType = MaterialType.OTHER;
            if (config.contains(base + ".MaterialType")) {
                final String name = config.getString(base + ".MaterialType", "OTHER");
                try {
                    repairMaterialType = MaterialType.valueOf(name);
                } catch (IllegalArgumentException ex) {
                    reasons.add(key + " has an invalid MaterialType of " + name);
                }
            } else {
                repairMaterialType = classifyMaterialType(probe);
            }

            // Repair material: explicit name, else the family default.
            final String repairMaterialName = config.getString(base + ".RepairMaterial");
            final String resolvedName = repairMaterialName != null
                    ? repairMaterialName
                    : repairMaterialType.getDefaultMaterial();
            if (resolvedName == null || !Materials.isItem(resolvedName)) {
                notSupported.add(key);
                continue;
            }
            final String repairMaterialPath = Materials.idOf(resolvedName).getPath();

            // Maximum durability: vanilla value, falling back to the config for non-damageable items.
            short maximumDurability = (short) probe.getMaxDamage();
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
            } else if (ItemUtils.isMinecraftTool(probe)) {
                repairItemType = ItemType.TOOL;
            } else if (ItemUtils.isArmor(probe)) {
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

        if (!notSupported.isEmpty()) {
            LOGGER.debug("Repair config skipped {} unsupported item(s): {}", notSupported.size(),
                    String.join(", ", notSupported));
        }
        LOGGER.info("Loaded {} repairables from {}", repairables.size(), FILENAME);
    }

    /** Auto-classify an item probe into its {@link MaterialType} (legacy RepairConfig fallback). */
    private static MaterialType classifyMaterialType(ItemStack probe) {
        if (ItemUtils.isWoodTool(probe)) {
            return MaterialType.WOOD;
        } else if (ItemUtils.isStoneTool(probe)) {
            return MaterialType.STONE;
        } else if (ItemUtils.isStringTool(probe)) {
            return MaterialType.STRING;
        } else if (ItemUtils.isLeatherArmor(probe)) {
            return MaterialType.LEATHER;
        } else if (ItemUtils.isIronArmor(probe) || ItemUtils.isIronTool(probe)) {
            return MaterialType.IRON;
        } else if (ItemUtils.isGoldArmor(probe) || ItemUtils.isGoldTool(probe)) {
            return MaterialType.GOLD;
        } else if (ItemUtils.isDiamondArmor(probe) || ItemUtils.isDiamondTool(probe)) {
            return MaterialType.DIAMOND;
        } else if (ItemUtils.isNetheriteArmor(probe) || ItemUtils.isNetheriteTool(probe)) {
            return MaterialType.NETHERITE;
        } else if (ItemUtils.isCopperTool(probe) || ItemUtils.isCopperArmor(probe)) {
            return MaterialType.COPPER;
        }
        return MaterialType.OTHER;
    }

    /** The repairables parsed from the config. */
    public List<Repairable> getLoadedRepairables() {
        return repairables == null ? new ArrayList<>() : repairables;
    }
}
