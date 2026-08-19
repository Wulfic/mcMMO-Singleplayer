package com.gmail.nossr50.skills.cooking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.YamlConfiguration;
import com.gmail.nossr50.util.McTestRegistries;
import com.gmail.nossr50.platform.Potions;
import com.gmail.nossr50.util.text.ConfigStringUtils;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The shipped {@code Skills.Cooking.Power_Cook_Effects} table, audited against the item and status
 * effect registries rather than against anybody's memory of them.
 *
 * <p><b>This class exists because the table's failure modes are config edits, not code edits</b>, and
 * no other test in the suite covers that direction. Every rule Power Cook is built on lives in a
 * YAML file an operator can change, and each one is silent when broken:
 *
 * <ul>
 *   <li>⚠️⚠️ <b>an effect that fires every tick.</b> Three rows once mapped Saturation, which
 *       {@code InstantStatusEffect} applies <em>per tick for its whole duration</em> — 3 seconds of
 *       it is +60 food onto a 20-point bar, from one slice of bread, forever. The check is
 *       {@code canApplyUpdateEffect(1, 0)}, <b>never {@code instanceof InstantStatusEffect}</b>:
 *       four of the seven per-tick effects in the registry are not subclasses of it, so the
 *       obvious spelling of this test passes {@code hunger}, {@code absorption}, {@code bad_omen}
 *       and {@code raid_omen} straight through;</li>
 *   <li>a misspelled effect name, which silently disables the row;</li>
 *   <li>a food that is not edible — the four fish buckets, which the eat seam can never fire for;</li>
 *   <li>a food vanilla already gives an effect to, which would stack two effects on one bite;</li>
 *   <li>Fire Resistance or Water Breathing, which are banned outright at any duration.</li>
 * </ul>
 */
class PowerCookEffectTableTest {

    @BeforeAll
    static void bootstrapRegistries() {
        McTestRegistries.bootstrap();
    }

    /** Where the table lives. Restated so a section rename has to come through this test. */
    private static final String SECTION = "Skills.Cooking.Power_Cook_Effects";

    /**
     * The fifteen ruled rows, restated as a literal.
     *
     * <p>🔑 <b>Only a literal proves the shipped config matches the ruling.</b> Deriving both sides
     * of this comparison from the same YAML would prove the file agrees with itself, which it always
     * does. The roster is a design decision — cooked and crafted foods only, no food vanilla already
     * buffs — and a decision belongs in the test, not in the data under test.
     */
    private static final String[][] RULED_TABLE = {
            {"Cooked_Beef", "STRENGTH"},
            {"Cooked_Porkchop", "RESISTANCE"},
            {"Cooked_Mutton", "RESISTANCE"},
            {"Cooked_Chicken", "SPEED"},
            {"Cooked_Rabbit", "JUMP_BOOST"},
            {"Cooked_Cod", "DOLPHINS_GRACE"},
            {"Cooked_Salmon", "DOLPHINS_GRACE"},
            {"Baked_Potato", "HASTE"},
            {"Dried_Kelp", "HASTE"},
            {"Bread", "SPEED"},
            {"Cookie", "SPEED"},
            {"Pumpkin_Pie", "REGENERATION"},
            {"Mushroom_Stew", "REGENERATION"},
            {"Beetroot_Soup", "REGENERATION"},
            {"Rabbit_Stew", "JUMP_BOOST"},
    };

    /**
     * The four items that carry {@code FOOD} but no {@code CONSUMABLE}. They cannot be eaten, so a
     * table filtered on the wrong component silently gains four rows that can never fire.
     */
    private static final String[] FISH_BUCKETS = {
            "cod_bucket", "salmon_bucket", "pufferfish_bucket", "tropical_fish_bucket"};

    private YamlConfiguration table;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) throws java.io.IOException {
        // Construct the real config so the defaults are written out, then read the section straight
        // off disk -- this is the file a player actually gets.
        new GeneralConfig(dataFolder);
        final YamlConfiguration config =
                YamlConfiguration.loadConfiguration(dataFolder.resolve("config.yml"));
        table = config.getConfigurationSection(SECTION);
        assertNotNull(table, SECTION + " must exist in the shipped config.yml");
    }

    // --- The roster -------------------------------------------------------------------------------

    @Test
    void theShippedTableIsExactlyTheFifteenRuledRows() {
        final Set<String> shipped = new LinkedHashSet<>(table.getKeys(false));
        final Set<String> ruled = new LinkedHashSet<>();
        for (String[] row : RULED_TABLE) {
            ruled.add(row[0]);
        }
        // Both directions. A one-directional completeness test is half a test: "every ruled row
        // ships" says nothing about a sixteenth row somebody added.
        assertEquals(ruled, shipped, "the shipped table and the ruled table must be the same set");
    }

    @Test
    void everyRowGrantsTheRuledEffect() {
        for (String[] row : RULED_TABLE) {
            assertEquals(row[1], table.getString(row[0]),
                    () -> row[0] + " must grant " + row[1]);
        }
    }

    // --- The cadence ban (D-CK5a) -----------------------------------------------------------------

    @Test
    void noConfiguredEffectFiresEveryTick() {
        // ⚠️⚠️ THE ONE THAT MATTERS. An effect's NAME does not tell you its cadence, and the
        // hunger-cannon defect got as far as a written table three times before anyone disassembled
        // canApplyUpdateEffect. Asserted at duration 1 AND at 20: an effect that declines to fire
        // at 1 tick but fires at every tick thereafter would pass the cheaper check alone.
        for (String food : table.getKeys(false)) {
            final StatusEffect effect = resolve(food).value();
            assertFalse(effect.canApplyUpdateEffect(1, 0),
                    () -> food + " maps to a status effect that applies EVERY TICK. Three seconds of"
                            + " one of those is sixty applications; Saturation would fill the hunger"
                            + " bar three times over from a single bite.");
            assertFalse(effect.canApplyUpdateEffect(20, 0),
                    () -> food + " maps to a per-tick status effect (caught at duration 20)");
        }
    }

    @Test
    void theCadenceCheckActuallyCatchesAPerTickEffect() {
        // The reference point for the test above. Without it, a canApplyUpdateEffect that always
        // answered false -- or a loop over an empty section -- would look identical to a clean table.
        assertTrue(StatusEffects.SATURATION.value().canApplyUpdateEffect(1, 0),
                "Saturation must still be per-tick, or the check above is measuring nothing");
        // ⚠️ And the reason the check is not `instanceof InstantStatusEffect`: four of the seven
        // per-tick effects are not subclasses of it. Hunger is one of them.
        assertTrue(StatusEffects.HUNGER.value().canApplyUpdateEffect(1, 0));
    }

    // --- The bans and the exclusions --------------------------------------------------------------

    @Test
    void noRowGrantsFireResistanceOrWaterBreathing() {
        // Banned outright, at any duration: 15 seconds of either is a lava-lake shortcut and a
        // monument shortcut, and both are Alchemy's to sell.
        for (String food : table.getKeys(false)) {
            final RegistryEntry<StatusEffect> effect = resolve(food);
            assertFalse(effect.equals(StatusEffects.FIRE_RESISTANCE),
                    () -> food + " grants Fire Resistance, which is banned from this table");
            assertFalse(effect.equals(StatusEffects.WATER_BREATHING),
                    () -> food + " grants Water Breathing, which is banned from this table");
        }
    }

    @Test
    void noRowIsAFoodVanillaAlreadyBuffs() {
        // Derived, never written down: Stage 0 found the hand-authored list of these was wrong on
        // four of nine and still summed to nine, which is why nobody re-counted it. An effect a food
        // grants can live on any of three components; this is the one the eat path walks.
        for (String food : table.getKeys(false)) {
            // ⚠️ Which component carries a food's granted effects is version-specific. Where eating
            // is split onto a consumable component, they live there; at this version they are still
            // on FOOD itself. Same question, different component.
            final FoodComponent food_ = new ItemStack(item(food)).get(DataComponentTypes.FOOD);
            assertNotNull(food_, () -> food + " is not edible");
            assertTrue(food_.effects().isEmpty(),
                    () -> food + " already carries a vanilla consume effect; Power Cook must not"
                            + " stack a second one on the same bite");
        }
    }

    // --- The item domain --------------------------------------------------------------------------

    @Test
    void everyRowIsARealEdibleItem() {
        for (String food : table.getKeys(false)) {
            final ItemStack stack = new ItemStack(item(food));
            assertNotNull(stack.get(DataComponentTypes.FOOD),
                    () -> food + " has no FOOD component");
            // ⚠️ On versions that split eating onto a separate consumable component, FOOD alone is
            // NOT edibility and a second assertion is needed here. At this version there is no such
            // component: FOOD is what LivingEntity#eatFood consumes, and it is the seam Power Cook
            // rides, so the FOOD assertion above is the whole question.
        }
    }

    @Test
    void everyRowRestoresHunger() {
        // ⚠️ Not cosmetic. `if (nutrition <= 0) return;` sits ABOVE the whole chain in FoodListener,
        // so a zero-nutrition food is unreachable for Power Cook no matter how it is configured.
        // Today no row is affected; this is what would say so if one ever were.
        for (String food : table.getKeys(false)) {
            final FoodComponent component = new ItemStack(item(food)).get(DataComponentTypes.FOOD);
            assertNotNull(component);
            assertTrue(component.nutrition() >= 1,
                    () -> food + " restores no hunger, so the nutrition guard above the chain would"
                            + " stop Power Cook ever seeing it");
        }
    }

    @Test
    void theFishBucketsAreNotInTheTable() {
        // ⚠️ The rationale is version-specific, the assertion is not. Where eating is split onto a
        // consumable component these carry FOOD and no CONSUMABLE, so a food domain filtered on the
        // wrong component gains four rows that can never fire. This version has no such split — but
        // a bucket is still not something Power Cook may buff, so the row must still be absent.
        for (String bucket : FISH_BUCKETS) {
            assertFalse(table.contains(ConfigStringUtils.getMaterialConfigString(bucket)),
                    () -> bucket + " is not edible and must not be in the Power Cook table");
        }
    }

    // --- Helpers ----------------------------------------------------------------------------------

    /** Resolve a row's configured effect name the same way the eat seam does. */
    private RegistryEntry<StatusEffect> resolve(String foodConfigString) {
        final String name = table.getString(foodConfigString);
        final RegistryEntry<StatusEffect> effect = Potions.matchEffect(name);
        // A name that does not resolve disables its row silently -- the failure mode a typo has.
        assertNotNull(effect,
                () -> foodConfigString + " maps to '" + name + "', which is not a status effect");
        return effect;
    }

    /** The registry item a config key names, e.g. {@code Cooked_Beef} → {@code cooked_beef}. */
    private static Item item(String foodConfigString) {
        final String path = foodConfigString.toLowerCase(Locale.ENGLISH);
        final Item found = Registries.ITEM.getOrEmpty(Identifier.ofVanilla(path)).orElse(null);
        assertNotNull(found, () -> foodConfigString + " is not a vanilla item (" + path + ")");
        return found;
    }
}
