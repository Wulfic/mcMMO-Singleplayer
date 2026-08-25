package com.gmail.nossr50.guards;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.util.McTestRegistries;
import java.util.Optional;
import net.minecraft.world.item.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * TODO §33.1b — the control on {@link McTestRegistries#bootstrap()} itself.
 *
 * <p><b>🔑 Why this class exists at all.</b> Every other test in the tree uses the bootstrap as a
 * precondition and asserts something about mcMMO. That makes a broken bootstrap the single
 * highest-leverage vacuous-guard shape in the repo: on {@code 26.2} it produced 171 red tests in one
 * run, and the *previous* failure mode was worse than red — a half-initialised registry answers
 * "no such item" and "no such entity" perfectly calmly, so tests that conclude something from an
 * absence go <em>green</em> while proving nothing. {@code itemRegistryIsPopulated} and
 * {@code entityTypeRegistryIsPopulated} were added for that reason; this class is where they are
 * actually asserted rather than merely offered.
 *
 * <p><b>The {@code 26.x} addition.</b> From {@code 26.x} Minecraft binds an entry's
 * {@link net.minecraft.core.component.DataComponentMap} onto the registry <em>holder</em> rather
 * than onto the value, and it does that during data-pack load — not during
 * {@code Bootstrap.bootStrap()}. A unit test loads no data pack, so
 * {@link McTestRegistries#bootstrap()} has to run the initializers itself.
 * {@link #itemComponentsAreBound()} is the control on that step.
 *
 * <p>⚠️ <b>This test is only worth having if it fails when the step is removed.</b> That was
 * verified by mutation, not assumed: deleting the {@code bindDataComponents()} call from
 * {@code bootstrap()} turns {@link #itemComponentsAreBound()} red with
 * {@code NullPointerException: Components not bound yet} — the same throw the 171 saw. Do not
 * "simplify" this class into a smoke test that would pass either way.
 */
class RegistryBootstrapControlTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        McTestRegistries.bootstrap();
    }

    @Test
    void itemRegistryIsPopulated() {
        assertTrue(
                McTestRegistries.itemRegistryIsPopulated(),
                "the item registry did not populate — every 'this band has no such item' assertion in "
                        + "the suite is vacuous until this passes");
    }

    @Test
    void entityTypeRegistryIsPopulated() {
        assertTrue(
                McTestRegistries.entityTypeRegistryIsPopulated(),
                "the entity-type registry did not populate — every 'this band has no such mob' "
                        + "assertion in the suite is vacuous until this passes");
    }

    /**
     * The §33.1 control: components are bound, and bound to something real.
     *
     * <p>Both halves matter. {@code areComponentsBound()} alone would still pass if the initializers
     * ran over an empty provider and handed every holder {@code DataComponentMap.EMPTY}, which is a
     * bootstrap that binds nothing while reporting success — so the map is asserted non-empty too.
     */
    @Test
    void itemComponentsAreBound() {
        assertTrue(
                McTestRegistries.itemComponentsAreBound(),
                "item registry holders have no data components bound — on 26.x that is a "
                        + "NullPointerException on the first component read, not a default value");
    }

    /**
     * The non-vacuity half of the control above: prove the assertion is reading a real component
     * map, not a lucky {@code true}.
     *
     * <p>An iron sword carries durability on every version in scope. If this map is empty, the
     * binding step ran but bound nothing — the exact failure {@link #itemComponentsAreBound()} is
     * designed to catch and the exact one a laxer assertion would miss.
     */
    @Test
    void aBoundComponentMapHasRealContent() {
        final Optional<Item> ironSword = McTestRegistries.optionalVanillaItem("iron_sword");
        assertTrue(ironSword.isPresent(), "iron_sword absent — the registry never populated");
        assertTrue(
                ironSword.get().builtInRegistryHolder().areComponentsBound(),
                "iron_sword's holder reports its components unbound");
        assertFalse(
                ironSword.get().components().isEmpty(),
                "iron_sword's component map is EMPTY — the initializers ran over a provider that "
                        + "listed no registries, which binds every holder to DataComponentMap.EMPTY "
                        + "and reports success");
    }
}
