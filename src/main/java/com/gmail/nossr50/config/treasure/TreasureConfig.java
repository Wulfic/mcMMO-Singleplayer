package com.gmail.nossr50.config.treasure;

import com.gmail.nossr50.config.ConfigIdSkips;
import com.gmail.nossr50.config.ConfigLoader;
import com.gmail.nossr50.datatypes.treasure.ExcavationTreasure;
import com.gmail.nossr50.datatypes.treasure.HusbandryTreasure;
import com.gmail.nossr50.datatypes.treasure.HylianTreasure;
import com.gmail.nossr50.datatypes.treasure.ItemSpec;
import com.gmail.nossr50.datatypes.treasure.Treasure;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.util.LogUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 *       {@link com.gmail.nossr50.platform.BlockUtils#getHylianTreasureGroup}). The result is identical —
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
    // Keyed by the Husbandry harvest VERB the treasure can turn up on (Shear/Hive/Milk/Brush), which
    // is the same "group name" shape hylianMap uses. Keying on the verb rather than the species is the
    // skill's own boundary rule: a species table would need a row per animal and would rot.
    public HashMap<String, List<HusbandryTreasure>> husbandryMap = new HashMap<>();

    private final ConfigIdSkips skips = new ConfigIdSkips(FILENAME);

    public TreasureConfig(Path dataFolder) {
        super(FILENAME, dataFolder);
        loadKeys();
    }

    @Override
    protected void loadKeys() {
        loadTreasures("Excavation");
        loadTreasures("Hylian_Luck");
        loadTreasures("Husbandry");
    }

    /**
     * Drop every treasure whose item this Minecraft version does not have, and report them (TODO 5.5).
     *
     * <p><b>⚠️⚠️ Deliberately NOT done during {@link #loadKeys}, and this is not a style choice.</b>
     * This class is Minecraft-free by design — its tests construct it in a plain fork with no
     * bootstrap — and the registry probe touches {@code net.minecraft.core.registries.BuiltInRegistries}. Class
     * initialization for that type <em>throws</em> in an un-bootstrapped fork, and the failure is
     * sticky: every later touch of the class in the same fork gets {@code NoClassDefFoundError}, so
     * one probe in a constructor poisons the whole fork. Doing it in the constructor cost 351 test
     * failures across 8 unrelated classes, none of which mention treasures. (Same shape as
     * {@code ConfigBootstrapTest} poisoning its fork.)
     *
     * <p>So the probe lives in a method the MC-free tests never call, invoked once from
     * {@code McMMOMod#onServerStarting} after {@code ConfigBootstrap.loadAll} — the same point, and
     * for the same reason, as {@code SkillAvailability#probe}.
     *
     * <p>Pruning here rather than at drop time is the actual fix: resolution used to be deferred to
     * {@code ItemSpecBuilder}, so a treasure naming an absent item stayed in the pool, got rolled and
     * yielded an empty {@code Optional} — the player lost the roll outright. Removing it first keeps
     * the surviving treasures' relative odds honest.
     */
    public void pruneUnavailableEntries() {
        pruneMap("Excavation", excavationMap);
        pruneMap("Hylian_Luck", hylianMap);
        pruneMap("Husbandry", husbandryMap);
        skips.logSummary(LOGGER);
    }

    private <T extends Treasure> void pruneMap(String section, Map<String, List<T>> map) {
        for (var entry : map.entrySet()) {
            entry.getValue().removeIf(
                    t -> !skips.keepItem(section, t.getDrop().getMaterialId()));
        }
        // A source block / group whose every treasure was dropped keeps an empty list rather than
        // vanishing: getOrDefault callers read "no treasure rolled here", which is what we mean.
    }

    /** Rows dropped because this Minecraft version has no such item (TODO 5.5). */
    public @NotNull ConfigIdSkips getSkips() {
        return skips;
    }

    /**
     * The {@code Hidden Bounty} treasures a given Husbandry harvest verb can turn up, in config order.
     *
     * @param verb the {@code Drops_From} verb group — {@code "Shear"}, {@code "Hive"}, {@code "Milk"}
     *             or {@code "Brush"}
     * @return the verb's treasures (never {@code null})
     */
    public @NotNull List<HusbandryTreasure> getHusbandryTreasures(@NotNull String verb) {
        return husbandryMap.getOrDefault(verb, List.of());
    }

    /**
     * The Hylian Luck treasures for a {@code Drops_From} group ({@code "Bushes"}/{@code "Flowers"}/
     * {@code "Pots"}), in config order (legacy iterates most-specific first and returns the first that
     * rolls). Empty for an unknown group.
     *
     * @param group the group name resolved from the broken block by
     *     {@link com.gmail.nossr50.platform.BlockUtils#getHylianTreasureGroup}
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
        final boolean isHusbandry = type.equals("Husbandry");

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
            } else if (isHusbandry) {
                // Hidden Bounty: indexed by harvest VERB (Shear/Hive/Milk/Brush), the same
                // group-name shape Hylian Luck uses. A treasure may list several verbs, so one
                // config entry can be reachable from more than one map key.
                final HusbandryTreasure treasure = new HusbandryTreasure(item, xp, dropChance,
                        dropLevel);
                for (String verb : config.getStringList(
                        type + "." + treasureName + ".Drops_From")) {
                    husbandryMap.computeIfAbsent(verb, k -> new ArrayList<>()).add(treasure);
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
        } else if (isHusbandry) {
            // At INFO, unlike its two siblings: a mis-indented Husbandry section would otherwise ship
            // a sub-skill that silently finds nothing, and a headless boot is the cheapest place to
            // catch that. Same trick the Smelting recipe index uses.
            LOGGER.info("Loaded Hidden Bounty treasures for {} harvest verb(s) from {}",
                    husbandryMap.size(), FILENAME);
        } else {
            LogUtils.debug("Loaded " + hylianMap.size() + " Hylian Luck treasure source groups.");
        }
    }
}
