package com.gmail.nossr50.config.treasure;

import com.gmail.nossr50.config.ConfigLoader;
import com.gmail.nossr50.datatypes.treasure.ExcavationTreasure;
import com.gmail.nossr50.datatypes.treasure.HylianTreasure;
import com.gmail.nossr50.datatypes.treasure.ItemSpec;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.util.LogUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code treasures.yml} — Excavation Archaeology (and, eventually, Hylian Luck) drop tables.
 * Ported onto {@link ConfigLoader}.
 *
 * <p><b>Port scope (singleplayer):</b> this port loads the <b>Excavation</b> and <b>Hylian Luck</b>
 * sections. The dropped pieces, each a genuine adapter gap rather than a mechanical skip:
 * <ul>
 *   <li><b>Live {@code ItemStack} construction</b> → each treasure now carries an MC-free
 *       {@link ItemSpec} blueprint (registries aren't populated at config-load; see {@link ItemSpec}).
 *       The potion / {@code ItemMeta} (custom-name/lore) branches collapse into the blueprint's
 *       optional §-coded name + lore fields. {@link ItemSpec} now also carries a potion base type
 *       (added for the Fishing Shake drops), but nothing here reads it — no {@code treasures.yml}
 *       entry is a potion, so this loader never populates one.</li>
 *   <li><b>Hylian Luck</b> is keyed by its raw {@code Drops_From} <b>group name</b>
 *       ({@code Bushes}/{@code Flowers}/{@code Pots}) rather than expanded into individual blocks at
 *       load time. Legacy expanded the groups through Bukkit {@code Tag.SAPLINGS}/{@code Tag.FLOWER_POTS}
 *       (plus a hardcoded flower/bush list) into a material-keyed map; but block tags are only bound
 *       once datapacks load, not necessarily at this {@code SERVER_STARTING} config load, so the port
 *       resolves a broken block's group live at break time instead (see
 *       {@link com.gmail.nossr50.util.BlockUtils#getHylianTreasureGroup}). The result is identical —
 *       {@link #getHylianTreasures(String)} returns the same treasures the expanded map would have.</li>
 *   <li><b>Legacy {@code Drop_Level} key auto-conversion</b> (the {@code LEGACY}/{@code WRONG_KEY_*}
 *       migration that rewrote old users' files) is dropped — a fresh singleplayer install ships the
 *       current {@code Level_Requirement} format, so there is nothing to migrate.</li>
 * </ul>
 *
 * <p>Retargets {@code mcMMO.isRetroModeEnabled()} → {@link McMMOMod#isRetroModeEnabled()} (null-safe;
 * defaults to Standard scaling when the config service is un-wired, e.g. in unit tests).
 */
public class TreasureConfig extends ConfigLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("mcMMO/TreasureConfig");

    public static final String FILENAME = "treasures.yml";
    public static final String LEVEL_REQUIREMENT_RETRO_MODE = ".Level_Requirement.Retro_Mode";
    public static final String LEVEL_REQUIREMENT_STANDARD_MODE = ".Level_Requirement.Standard_Mode";

    public HashMap<String, List<ExcavationTreasure>> excavationMap = new HashMap<>();
    // Keyed by the raw Drops_From group name (Bushes/Flowers/Pots), not by individual block — see the
    // class javadoc and BlockUtils.getHylianTreasureGroup.
    public HashMap<String, List<HylianTreasure>> hylianMap = new HashMap<>();

    public TreasureConfig(Path dataFolder) {
        super(FILENAME, dataFolder);
        loadKeys();
    }

    @Override
    protected void loadKeys() {
        loadTreasures("Excavation");
        loadTreasures("Hylian_Luck");
    }

    /**
     * The Hylian Luck treasures for a {@code Drops_From} group ({@code "Bushes"}/{@code "Flowers"}/
     * {@code "Pots"}), in config order (legacy iterates most-specific first and returns the first that
     * rolls). Empty for an unknown group.
     *
     * @param group the group name resolved from the broken block by
     *     {@link com.gmail.nossr50.util.BlockUtils#getHylianTreasureGroup}
     * @return the group's treasures (never {@code null})
     */
    public @NotNull List<HylianTreasure> getHylianTreasures(@NotNull String group) {
        return hylianMap.getOrDefault(group, List.of());
    }

    private void loadTreasures(String type) {
        var treasureSection = config.getConfigurationSection(type);

        if (treasureSection == null) {
            return;
        }

        final boolean isExcavation = type.equals("Excavation");

        for (String treasureName : treasureSection.getKeys(false)) {
            // Legacy allowed a "MATERIAL|data" form; the trailing block-data short is meaningless in
            // modern flattened MC, so we keep only the material portion.
            final String materialName = treasureName.split("[|]")[0];
            final String materialId = materialName.toLowerCase(Locale.ROOT);

            int amount = config.getInt(type + "." + treasureName + ".Amount");
            if (amount <= 0) {
                amount = 1;
            }

            final int xp = config.getInt(type + "." + treasureName + ".XP");
            final double dropChance = config.getDouble(type + "." + treasureName + ".Drop_Chance");

            final int dropLevel;
            if (McMMOMod.isRetroModeEnabled()) {
                dropLevel = config.getInt(type + "." + treasureName + LEVEL_REQUIREMENT_RETRO_MODE,
                        -1);
            } else {
                dropLevel = config.getInt(
                        type + "." + treasureName + LEVEL_REQUIREMENT_STANDARD_MODE, -1);
            }

            final List<String> reasons = new ArrayList<>();
            if (dropLevel == -1) {
                LOGGER.error("Could not find a Level_Requirement entry for treasure {}, skipping.",
                        treasureName);
                continue;
            }
            if (xp < 0) {
                reasons.add(treasureName + " has an invalid XP value: " + xp);
            }
            if (dropChance < 0.0D) {
                reasons.add(treasureName + " has an invalid Drop_Chance: " + dropChance);
            }
            if (!reasons.isEmpty()) {
                reasons.forEach(LOGGER::warn);
                continue;
            }

            final String customName = config.getString(type + "." + treasureName + ".Custom_Name",
                    null);
            final List<String> lore = config.getStringList(type + "." + treasureName + ".Lore");
            final ItemSpec item = new ItemSpec(materialId, amount, customName, lore);

            if (isExcavation) {
                final ExcavationTreasure treasure = new ExcavationTreasure(item, xp, dropChance,
                        dropLevel);
                for (String blockType : config.getStringList(
                        type + "." + treasureName + ".Drops_From")) {
                    excavationMap.computeIfAbsent(blockType, k -> new ArrayList<>()).add(treasure);
                }
            } else {
                // Hylian Luck: index by the raw Drops_From group name (Bushes/Flowers/Pots). The
                // group→block expansion legacy did at load time happens live at break time instead
                // (BlockUtils.getHylianTreasureGroup), so datapack tag binding order can't leave a
                // sapling/pot group silently empty. Order within a group follows the config file, so a
                // most-specific-first walk in processHylianLuck stays faithful.
                final HylianTreasure treasure = new HylianTreasure(item, xp, dropChance, dropLevel);
                for (String group : config.getStringList(
                        type + "." + treasureName + ".Drops_From")) {
                    hylianMap.computeIfAbsent(group, k -> new ArrayList<>()).add(treasure);
                }
            }
        }

        if (isExcavation) {
            LogUtils.debug("Loaded " + excavationMap.size() + " excavation treasure source blocks.");
        } else {
            LogUtils.debug("Loaded " + hylianMap.size() + " Hylian Luck treasure source groups.");
        }
    }
}
