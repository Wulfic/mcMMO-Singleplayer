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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code treasures.yml} — Excavation Archaeology (and, eventually, Hylian Luck) drop tables.
 * Ported onto {@link ConfigLoader}.
 *
 * <p><b>Port scope (singleplayer):</b> this port loads the <b>Excavation</b> section only. The
 * dropped pieces, each a genuine adapter gap rather than a mechanical skip:
 * <ul>
 *   <li><b>Live {@code ItemStack} construction</b> → each treasure now carries an MC-free
 *       {@link ItemSpec} blueprint (registries aren't populated at config-load; see {@link ItemSpec}).
 *       The potion / {@code ItemMeta} (custom-name/lore) branches collapse into the blueprint's
 *       optional §-coded name + lore fields; potion base-type data is <b>PORT Phase 10 (fishing)</b>
 *       — no Excavation treasure uses it.</li>
 *   <li><b>Hylian Luck</b> loading is <b>PORT Phase 10</b>: its {@code Drops_From} groups
 *       ({@code Bushes}/{@code Flowers}/{@code Pots}) expand through Bukkit {@code Tag.SAPLINGS} /
 *       {@code Tag.FLOWER_POTS} / {@code BlockUtils.getShortGrass()}, which need a block-tag adapter
 *       that doesn't exist yet. {@link #hylianMap} stays empty until then.</li>
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
    // PORT Phase 10 (Hylian Luck): stays empty until a block-tag adapter lands (see class javadoc).
    public HashMap<String, List<HylianTreasure>> hylianMap = new HashMap<>();

    public TreasureConfig(Path dataFolder) {
        super(FILENAME, dataFolder);
        loadKeys();
    }

    @Override
    protected void loadKeys() {
        loadTreasures("Excavation");
        // PORT Phase 10: loadTreasures("Hylian_Luck") — needs the block-tag adapter.
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
            }
        }

        LogUtils.debug("Loaded " + excavationMap.size() + " excavation treasure source blocks.");
    }
}
