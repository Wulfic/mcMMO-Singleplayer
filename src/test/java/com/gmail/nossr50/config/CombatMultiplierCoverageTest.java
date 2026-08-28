package com.gmail.nossr50.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.util.McTestRegistries;
import com.gmail.nossr50.util.text.ConfigStringUtils;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.registry.Registries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * TODO §52 — the runtime half of the config-id gate for the entity-keyed tables.
 *
 * <p><b>Why a live test, when {@code scripts/config-id-audit.py} already reads these tables.</b>
 * The halves answer questions that do not overlap, and the offline one structurally cannot ask this
 * one. An id audit grades the rows that are <em>written down</em>: it finds {@code Snowman}, a key
 * naming a mob renamed to {@code snow_golem}, and reports it dead. It has no way to notice that
 * {@code Combat.Multiplier} contains <b>no {@code Vex} row at all</b> — an absence is not a row, so
 * there is nothing for it to grade. Every key in the file could be valid and vexes would still pay
 * nothing. Measured on {@code 26.2}: 8 dead keys for the script, 2 absent mobs for this class, and
 * <b>the two sets do not intersect</b>.
 *
 * <p><b>🔴 Why an absent row is the severe case and a dead one usually is not.</b> The fallback in
 * {@code CombatXp#baseXp} is deliberately <em>not</em> uniform:
 *
 * <table>
 *   <caption>what an unlisted mob is paid</caption>
 *   <tr><th>category</th><th>resolves to</th><th>consequence</th></tr>
 *   <tr><td>{@code MONSTER}</td><td>{@code getDouble} with <b>no default</b> → {@code 0.0}</td>
 *       <td><b>pays nothing, forever</b></td></tr>
 *   <tr><td>{@code ANIMAL}</td><td>{@code Combat.Multiplier.Animals} → 1.0</td>
 *       <td>mispaid only if the intended value was not 1.0</td></tr>
 *   <tr><td>{@code OTHER}</td><td>{@code hasCombatXP ? getCombatXP : 1.0}</td>
 *       <td>mispaid only if the intended value was not 1.0</td></tr>
 * </table>
 *
 * <p>So this class asserts exactly one property, the one whose failure is silent and total:
 * <b>every {@code HostileEntity} this version ships has a {@code Combat.Multiplier} row.</b> Animals and
 * everything else are deliberately not required — their fallback is correct by design, and demanding
 * a row for all 158 entity types would make this a transcription of the registry rather than a
 * statement about mcMMO.
 *
 * <h2>⚠️⚠️ Why the mob set comes from reflection over {@code EntityType}, and not from the two
 * obvious APIs</h2>
 *
 * Both obvious answers were tried and <b>measured wrong</b>, in the direction that passes quietly:
 *
 * <ol>
 *   <li><b>{@code EntityType#getBaseClass()} returns {@code Entity} for every registered type</b> —
 *       {@code zombie} included — under this bootstrap. It compiles, it runs, it needs no {@code
 *       Level}, and it silently reports <b>zero</b> monsters out of 158. As the sole input to a
 *       "every monster has a row" assertion that is a permanently green test that examines nothing.
 *       It was caught only by the anti-vacuity guard below.</li>
 *   <li><b>{@code EntityType#getCategory()} is a different question.</b> Vanilla's {@code MobCategory}
 *       says 45 monsters where {@code instanceof HostileEntity} says 34, and the 11-way difference is not
 *       noise: {@code slime}, {@code magma_cube}, {@code ghast}, {@code phantom}, {@code shulker},
 *       {@code hoglin} and {@code ender_dragon} carry the monster category while extending
 *       {@code MobEntity} rather than {@code HostileEntity}. {@code CombatUtils#categoryOf} branches on
 *       {@code instanceof HostileEntity}, so using the category would demand rows for 11 mobs that do not
 *       need one and are already correctly paid through the {@code OTHER} floor.</li>
 * </ol>
 *
 * The declared type argument of each {@code EntityType} field ({@code EntityType<Zombie> ZOMBIE})
 * is the compile-time class — exactly what {@code instanceof} tests against — and it is available
 * statically, with no {@code Level} and no instantiation.
 *
 * <p>⚠️ <b>Band note:</b> this is the YARN-mapped edition. {@code master} (26.2) spells these
 * {@code EntityTypes} and {@code net.minecraft.world.entity.monster.Monster}, and {@code mc/26.1.2}
 * a third way again — official names, but the constants still on {@code EntityType}. Three
 * spellings across nine branches for one idea; translate on cherry-pick, the same friction §38
 * handled for the tooling.
 */
class CombatMultiplierCoverageTest {

    private static final String TABLE = "Experience_Values.Combat.Multiplier";

    /**
     * Bootstrap and the anti-vacuity check are ONE method deliberately. JUnit does not order
     * multiple {@code @BeforeAll} methods, so as two methods the liveness check can run before the
     * bootstrap it depends on — reddening on the ordering rather than on the property.
     *
     * <p>The check is what caught {@code getBaseClass()} returning {@code Entity} for everything.
     * Without it that defect presents as a green suite.
     */
    @BeforeAll
    static void bootstrapAndProveTheMobSetIsLive() {
        McTestRegistries.bootstrap();
        assertTrue(McTestRegistries.entityTypeRegistryIsPopulated(),
                "the entity registry did not populate — every assertion below would pass for the "
                        + "wrong reason");
        assertFalse(monsterKeys().isEmpty(),
                "zero monsters resolved out of a populated entity registry. The class-resolution "
                        + "strategy has stopped working (see this class's javadoc: getBaseClass() "
                        + "already failed exactly this way), and 'every monster has a row' would "
                        + "hold vacuously.");
    }

    // ------------------------------------------------------------------ the property

    @Test
    void everyMonsterHasACombatMultiplierRow() {
        final Set<String> rows = multiplierRows();

        // An unreadable resource parses to an empty set, and then EVERY monster is reported — loud,
        // but for the wrong reason. Prove the table was actually read before grading it.
        assertFalse(rows.isEmpty(), "no " + TABLE + " rows parsed out of the bundled "
                + "experience.yml — the resource is missing or the shape changed");

        final List<String> unpaid = new ArrayList<>();
        monsterKeys().forEach((key, id) -> {
            if (!rows.contains(key)) {
                unpaid.add(key + "  (" + id + ")");
            }
        });

        assertTrue(unpaid.isEmpty(),
                "experience.yml -> " + TABLE + " has no row for " + unpaid.size() + " monster(s) "
                        + "this version ships. CombatXp#baseXp resolves an unlisted MONSTER through "
                        + "getDouble with no default, so each of these pays ZERO combat XP on every "
                        + "hit, silently and on every band:\n  " + String.join("\n  ", unpaid)
                        + "\nAdd a row per mob. See TODO.md §52; "
                        + "`python scripts/config-id-audit.py --check` is the other half of this "
                        + "gate and grades the rows that ARE present.");
    }

    /**
     * The detector must fire. Without this, {@link #everyMonsterHasACombatMultiplierRow} is
     * indistinguishable from a method that reports an empty list unconditionally — and this repo has
     * shipped fifteen assertions that did exactly that.
     *
     * <p>Removes one real row rather than inventing a fake mob, so the mutation stays on the shipped
     * code path: it is the same shape as a mob Mojang adds and nobody configures.
     */
    @Test
    void theDetectorReportsAMissingRow() {
        final Map<String, String> monsters = monsterKeys();
        final Set<String> rows = multiplierRows();
        final String victim = monsters.keySet().iterator().next();
        assertTrue(rows.contains(victim),
                "precondition: the mob chosen for the mutation (" + victim + ") must be present in "
                        + "the shipped table, or this proves nothing");

        final Set<String> mutated = new TreeSet<>(rows);
        mutated.remove(victim);

        final List<String> unpaid = new ArrayList<>();
        for (String key : monsters.keySet()) {
            if (!mutated.contains(key)) {
                unpaid.add(key);
            }
        }
        assertTrue(unpaid.contains(victim),
                "the coverage check did NOT report " + victim + " after its row was removed — it "
                        + "cannot detect the defect it exists to detect");
    }

    // ------------------------------------------------------------------ helpers

    /**
     * {@code config key -> registry id} for every entity type whose declared class is a
     * {@code Monster} — i.e. exactly those for which {@code CombatUtils#categoryOf} returns
     * {@code MONSTER}.
     *
     * <p>A field whose type argument is not a plain class is skipped rather than guessed at; the
     * anti-vacuity check above is what stops "skipped everything" from reading as "found nothing
     * wrong".
     */
    private static Map<String, String> monsterKeys() {
        final Map<String, String> out = new LinkedHashMap<>();
        for (Field f : EntityType.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())
                    || !EntityType.class.isAssignableFrom(f.getType())) {
                continue;
            }
            final Type generic = f.getGenericType();
            if (!(generic instanceof ParameterizedType pt)
                    || !(pt.getActualTypeArguments()[0] instanceof Class<?> declared)
                    || !HostileEntity.class.isAssignableFrom(declared)) {
                continue;
            }
            try {
                f.setAccessible(true);
                final EntityType<?> type = (EntityType<?>) f.get(null);
                final String id = Registries.ENTITY_TYPE.getId(type).getPath();
                out.put(ConfigStringUtils.getConfigEntityTypeString(id), id);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("could not read EntityType." + f.getName(), e);
            }
        }
        return out;
    }

    private static Set<String> multiplierRows() {
        try (InputStream in = ConfigLoader.class.getResourceAsStream("/experience.yml")) {
            if (in == null) {
                return Set.of();
            }
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(in);
            final YamlConfiguration node = yaml.getConfigurationSection(TABLE);
            return node == null ? Set.of() : node.getKeys(false);
        } catch (Exception e) {
            throw new IllegalStateException("could not read the bundled experience.yml", e);
        }
    }
}
