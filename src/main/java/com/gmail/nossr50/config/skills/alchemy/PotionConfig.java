package com.gmail.nossr50.config.skills.alchemy;

import com.gmail.nossr50.config.ConfigLoader;
import com.gmail.nossr50.config.YamlConfiguration;
import com.gmail.nossr50.datatypes.skills.alchemy.AlchemyPotion;
import com.gmail.nossr50.platform.Materials;
import com.gmail.nossr50.util.PotionUtil;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.registry.entry.RegistryEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code potions.yml} — the Alchemy Concoctions ingredient tiers and the potion brewing tree.
 * Ported onto {@link ConfigLoader}, retargeted from Bukkit {@code PotionMeta} onto the vanilla
 * {@link PotionContentsComponent}.
 *
 * <p>Like {@link com.gmail.nossr50.config.skills.repair.RepairConfig}, this config is MC-typed at
 * load time: every ingredient resolves against the live item registry ({@link Materials}) and every
 * potion's base type / effects resolve against {@code Registries.POTION} / {@code STATUS_EFFECT}.
 * Those calls are only valid once Minecraft's registries are populated (server start); the unit test
 * drives it under the {@code fabric-loader-junit} registry harness. When registries are absent an
 * ingredient/potion simply fails to resolve and is skipped — no crash.
 *
 * <p>Deliberately deferred vs legacy (cosmetic, no effect on brew resolution or XP — breadcrumbs):
 * custom potion display name, lore, and colour. mcMMO's shipped {@code potions.yml} pre-1.21
 * compatibility skips are also unnecessary on this 1.21.11 target.
 */
public class PotionConfig extends ConfigLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("mcMMO/PotionConfig");

    public static final String FILENAME = "potions.yml";

    /** Cumulative Concoctions ingredient lists, indexed 1..8 (index 0 unused). */
    private final List<List<ItemStack>> concoctionTiers = new ArrayList<>();
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
                final ItemStack ingredient = loadIngredient(ingredientString);
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
    private ItemStack loadIngredient(String ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return null;
        }
        final Optional<Item> item = Materials.item(ingredient);
        return item.map(ItemStack::new).orElse(null);
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
            final Item item = Materials.item(materialString)
                    .or(() -> Materials.item("POTION"))
                    .orElse(null);
            if (item == null) {
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

            final RegistryEntry<Potion> basePotion =
                    PotionUtil.matchPotion(potionTypeStr, upgraded, extended);
            if (basePotion == null) {
                LOGGER.warn("PotionConfig: could not resolve potion type '{}' for {}", potionTypeStr,
                        key);
                return null;
            }

            final List<StatusEffectInstance> customEffects = new ArrayList<>();
            for (String effect : potion.getStringList("Effects")) {
                final StatusEffectInstance instance = parseEffect(key, effect);
                if (instance != null) {
                    customEffects.add(instance);
                }
            }

            final ItemStack itemStack = new ItemStack(item);
            itemStack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(
                    Optional.of(basePotion), Optional.empty(), customEffects, Optional.empty()));

            final Map<ItemStack, String> children = loadChildren(key, potion);

            return new AlchemyPotion(key, itemStack, children);
        } catch (Exception e) {
            LOGGER.warn("PotionConfig: failed to load Alchemy potion {}", key, e);
            return null;
        }
    }

    private StatusEffectInstance parseEffect(String key, String effect) {
        final String[] parts = effect.split(" ");
        final RegistryEntry<StatusEffect> type = parts.length > 0
                ? PotionUtil.matchEffect(parts[0])
                : null;
        if (type == null) {
            LOGGER.warn("PotionConfig: failed to parse effect '{}' for potion {}", effect, key);
            return null;
        }
        final int amplifier = parts.length > 1 ? parseIntSafe(parts[1]) : 0;
        final int duration = parts.length > 2 ? parseIntSafe(parts[2]) : 0;
        return new StatusEffectInstance(type, duration, amplifier);
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private Map<ItemStack, String> loadChildren(String key, YamlConfiguration potion) {
        final Map<ItemStack, String> children = new HashMap<>();
        final YamlConfiguration childSection = potion.getConfigurationSection("Children");
        if (childSection == null) {
            return children;
        }
        for (String childIngredient : childSection.getKeys(false)) {
            final ItemStack ingredient = loadIngredient(childIngredient);
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
    public List<ItemStack> getIngredients(int tier) {
        if (tier < 1 || tier > 8) {
            return concoctionTiers.isEmpty() ? new ArrayList<>() : concoctionTiers.get(1);
        }
        return concoctionTiers.get(tier);
    }

    /** The configured potion with the given config name, or {@code null}. */
    public AlchemyPotion getPotion(String name) {
        return alchemyPotions.get(name);
    }

    /** The configured potion functionally matching the given item stack, or {@code null}. */
    public AlchemyPotion getPotion(ItemStack item) {
        for (AlchemyPotion potion : alchemyPotions.values()) {
            if (potion.isSimilarPotion(item)) {
                return potion;
            }
        }
        return null;
    }

    /** Whether the given item stack is a recognised Alchemy potion. */
    public boolean isValidPotion(ItemStack item) {
        return getPotion(item) != null;
    }

    /** Number of potions successfully parsed (for tests/logging). */
    public int getLoadedPotionCount() {
        return alchemyPotions.size();
    }
}
