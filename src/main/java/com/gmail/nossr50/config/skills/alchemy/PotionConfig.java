package com.gmail.nossr50.config.skills.alchemy;

import com.gmail.nossr50.config.ConfigLoader;
import com.gmail.nossr50.config.YamlConfiguration;
import com.gmail.nossr50.datatypes.skills.alchemy.AlchemyPotion;
import com.gmail.nossr50.datatypes.skills.alchemy.EffectSpec;
import com.gmail.nossr50.datatypes.skills.alchemy.PotionSpec;
import com.gmail.nossr50.platform.Materials;
import com.gmail.nossr50.platform.PlatformItem;
import com.gmail.nossr50.platform.Potions;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code potions.yml} — the Alchemy Concoctions ingredient tiers and the potion brewing tree.
 * Ported onto {@link ConfigLoader}, retargeted from Bukkit {@code PotionMeta} onto vanilla potion
 * contents.
 *
 * <p>Like {@link com.gmail.nossr50.config.skills.repair.RepairConfig}, this config resolves against
 * the live registries at load time: every ingredient through {@link Materials} and every potion's
 * base type / effects through {@link Potions}. Those calls are only valid once Minecraft's registries
 * are populated (server start); the unit test drives it under the {@code fabric-loader-junit}
 * registry harness. When registries are absent an ingredient/potion simply fails to resolve and is
 * skipped — no crash.
 *
 * <p>Phase 2 slice 5 sealed this file: the registry and data-component work moved behind
 * {@link Potions} / {@link Materials} and the parsed model is {@link PlatformItem} +
 * {@link com.gmail.nossr50.datatypes.skills.alchemy.PotionSpec}, so no Minecraft type appears here.
 *
 * <p>Deliberately deferred vs legacy (cosmetic, no effect on brew resolution or XP — breadcrumbs):
 * custom potion display name, lore, and colour. mcMMO's shipped {@code potions.yml} pre-1.21
 * compatibility skips are also unnecessary on this 1.21.11 target.
 */
public class PotionConfig extends ConfigLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("mcMMO/PotionConfig");

    public static final String FILENAME = "potions.yml";

    /** Cumulative Concoctions ingredient lists, indexed 1..8 (index 0 unused). */
    private final List<List<PlatformItem>> concoctionTiers = new ArrayList<>();
    private final Map<String, AlchemyPotion> alchemyPotions = new LinkedHashMap<>();

    public PotionConfig(Path dataFolder) {
        super(FILENAME, dataFolder);
        loadKeys();
    }

    @Override
    protected void loadKeys() {
        loadConcoctions();
        loadPotionMap();
    }

    // --------------------------------------------------------------------- Concoctions

    private void loadConcoctions() {
        concoctionTiers.clear();
        // Eight tier buckets (plus a dummy at index 0 so tier N maps to index N).
        for (int i = 0; i <= 8; i++) {
            concoctionTiers.add(new ArrayList<>());
        }

        final YamlConfiguration section = config.getConfigurationSection("Concoctions");
        if (section == null) {
            LOGGER.error("Could not find Concoctions section in {}", FILENAME);
            return;
        }

        final String[] tierKeys = {null, "Tier_One_Ingredients", "Tier_Two_Ingredients",
                "Tier_Three_Ingredients", "Tier_Four_Ingredients", "Tier_Five_Ingredients",
                "Tier_Six_Ingredients", "Tier_Seven_Ingredients", "Tier_Eight_Ingredients"};

        for (int tier = 1; tier <= 8; tier++) {
            for (String ingredientString : section.getStringList(tierKeys[tier])) {
                final PlatformItem ingredient = loadIngredient(ingredientString);
                if (ingredient != null) {
                    concoctionTiers.get(tier).add(ingredient);
                }
            }
        }

        // Each tier includes every lower tier's ingredients (legacy cascade).
        for (int tier = 2; tier <= 8; tier++) {
            concoctionTiers.get(tier).addAll(concoctionTiers.get(tier - 1));
        }
    }

    /** Parse an ingredient material name into a single-item stack, or {@code null} if unknown. */
    private PlatformItem loadIngredient(String ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return null;
        }
        return Materials.stack(ingredient).orElse(null);
    }

    // --------------------------------------------------------------------- Potions

    private void loadPotionMap() {
        alchemyPotions.clear();

        final YamlConfiguration potionSection = config.getConfigurationSection("Potions");
        if (potionSection == null) {
            LOGGER.error("Could not find Potions section in {}", FILENAME);
            return;
        }

        int loaded = 0;
        int failures = 0;
        for (String potionName : potionSection.getKeys(false)) {
            final AlchemyPotion potion =
                    loadPotion(potionName, potionSection.getConfigurationSection(potionName));
            if (potion != null) {
                alchemyPotions.put(potionName, potion);
                loaded++;
            } else {
                failures++;
            }
        }

        LOGGER.info("Loaded {} of {} Alchemy potions from {}", loaded, loaded + failures, FILENAME);
    }

    private AlchemyPotion loadPotion(String key, YamlConfiguration potion) {
        if (potion == null) {
            return null;
        }
        try {
            // Material: defaults to a plain potion if missing/unresolvable (legacy behaviour).
            final String materialString = potion.getString("Material", "POTION");
            final PlatformItem itemStack = Materials.stack(materialString)
                    .or(() -> Materials.stack("POTION"))
                    .orElse(null);
            if (itemStack == null) {
                LOGGER.warn("PotionConfig: could not resolve item for potion {}", key);
                return null;
            }

            boolean extended = potion.getBoolean("PotionData.Extended", false);
            boolean upgraded = potion.getBoolean("PotionData.Upgraded", false);
            // Extended and Upgraded are mutually exclusive; default to Extended (legacy).
            if (extended && upgraded) {
                upgraded = false;
            }

            final String potionTypeStr = potion.getString("PotionData.PotionType");
            if (potionTypeStr == null) {
                LOGGER.warn("PotionConfig: missing PotionType for {}", key);
                return null;
            }

            final Optional<String> basePotionId =
                    Potions.resolvePotionId(potionTypeStr, upgraded, extended);
            if (basePotionId.isEmpty()) {
                LOGGER.warn("PotionConfig: could not resolve potion type '{}' for {}", potionTypeStr,
                        key);
                return null;
            }

            final List<EffectSpec> customEffects = new ArrayList<>();
            for (String effect : potion.getStringList("Effects")) {
                final EffectSpec instance = parseEffect(key, effect);
                if (instance != null) {
                    customEffects.add(instance);
                }
            }

            if (!Potions.applyContents(itemStack, basePotionId.get(), customEffects)) {
                // Cannot happen for an id this class just resolved, but a silent half-built potion
                // would resolve as a *different* potion at brew time, so it is a hard skip.
                LOGGER.warn("PotionConfig: could not apply potion contents '{}' to {}",
                        basePotionId.get(), key);
                return null;
            }

            final Map<PlatformItem, String> children = loadChildren(key, potion);

            return new AlchemyPotion(key, itemStack, children);
        } catch (Exception e) {
            LOGGER.warn("PotionConfig: failed to load Alchemy potion {}", key, e);
            return null;
        }
    }

    private EffectSpec parseEffect(String key, String effect) {
        final String[] parts = effect.split(" ");
        final Optional<String> effectId = parts.length > 0
                ? Potions.resolveEffectId(parts[0])
                : Optional.empty();
        if (effectId.isEmpty()) {
            LOGGER.warn("PotionConfig: failed to parse effect '{}' for potion {}", effect, key);
            return null;
        }
        final int amplifier = parts.length > 1 ? parseIntSafe(parts[1]) : 0;
        final int duration = parts.length > 2 ? parseIntSafe(parts[2]) : 0;
        return new EffectSpec(effectId.get(), amplifier, duration);
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private Map<PlatformItem, String> loadChildren(String key, YamlConfiguration potion) {
        final Map<PlatformItem, String> children = new HashMap<>();
        final YamlConfiguration childSection = potion.getConfigurationSection("Children");
        if (childSection == null) {
            return children;
        }
        for (String childIngredient : childSection.getKeys(false)) {
            final PlatformItem ingredient = loadIngredient(childIngredient);
            if (ingredient != null) {
                children.put(ingredient, childSection.getString(childIngredient));
            } else {
                LOGGER.debug("PotionConfig: skipped unknown child ingredient '{}' for potion {}",
                        childIngredient, key);
            }
        }
        return children;
    }

    // --------------------------------------------------------------------- API

    /** The cumulative Concoctions ingredient list for the given tier (1..8; out-of-range → tier 1). */
    public List<PlatformItem> getIngredients(int tier) {
        if (tier < 1 || tier > 8) {
            return concoctionTiers.isEmpty() ? new ArrayList<>() : concoctionTiers.get(1);
        }
        return concoctionTiers.get(tier);
    }

    /** The configured potion with the given config name, or {@code null}. */
    public AlchemyPotion getPotion(String name) {
        return alchemyPotions.get(name);
    }

    /**
     * The configured potion functionally matching the given item, or {@code null}.
     *
     * <p>The item's own spec is read once and reused across the whole scan: this runs on the brewing
     * stand's per-tick {@code canCraft} path, so a registry lookup per configured potion would be
     * paid every tick a brew is in progress.
     */
    public AlchemyPotion getPotion(PlatformItem item) {
        final PotionSpec spec = Potions.specOf(item);
        for (AlchemyPotion potion : alchemyPotions.values()) {
            if (potion.isSimilarPotion(item, spec)) {
                return potion;
            }
        }
        return null;
    }

    /** Whether the given item is a recognised Alchemy potion. */
    public boolean isValidPotion(PlatformItem item) {
        return getPotion(item) != null;
    }

    /** Number of potions successfully parsed (for tests/logging). */
    public int getLoadedPotionCount() {
        return alchemyPotions.size();
    }
}
