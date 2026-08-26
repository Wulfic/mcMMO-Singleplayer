package com.gmail.nossr50.util;

import java.util.Optional;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;

/**
 * Shared one-time Minecraft bootstrap for unit tests that touch live vanilla registries (item/block
 * id-path extraction in {@code ItemUtils}/{@code BlockUtils}, etc.). Call {@link #bootstrap()} from a
 * {@code @BeforeAll}.
 *
 * <p>Only works under the {@code fabric-loader-junit} test launcher (see {@code build.gradle}), which
 * runs tests through Knot's classloader so Minecraft's access wideners are applied — plain JUnit
 * throws an {@code IllegalAccessError} from {@code SimpleRegistry} during registration. Idempotent:
 * {@link Bootstrap#initialize()} is itself guarded, and the flag here avoids re-entry.
 */
public final class McTestRegistries {

    private static boolean bootstrapped;

    private McTestRegistries() {}

    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        bindDataComponents();
        bootstrapped = true;
    }

    /**
     * Binds the per-registry-entry {@link net.minecraft.core.component.DataComponentMap}s that
     * {@code Bootstrap.bootStrap()} does <em>not</em> bind.
     *
     * <p>⚠️ <b>This is a {@code 26.x} requirement that did not exist on any earlier band, and its
     * absence is silent until something reads a component.</b> Through {@code 1.21.11} an item's
     * components lived on the {@code Item} instance, so a plain bootstrap was enough. From
     * {@code 26.x} they live on the registry <em>holder</em>: {@code Holder.Reference} gained
     * {@code bindComponents} / {@code areComponentsBound}, and {@code components()} throws
     * {@code NullPointerException: Components not bound yet} until something binds them. In the real
     * game that "something" is data-pack load — {@code ReloadableServerResources} on the server,
     * {@code RegistryDataCollector} on the client — neither of which a unit test runs. 171 tests
     * NPE'd on the first {@code 26.2} suite run for exactly this reason.
     *
     * <p>Resolved from the {@code 26.2} jar, not from memory:
     * {@code BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(HolderLookup.Provider)} returns one
     * {@code PendingComponents} per registry and {@code apply()} performs the binding. {@code build}
     * pre-seeds an entry for <em>every</em> registry the provider lists and hands
     * {@code DataComponentMap.EMPTY} to holders no initializer touched, so every holder in every
     * listed registry ends up bound — not just the ones with real components. That matters: an
     * unbound holder throws, it does not read as empty.
     *
     * <p>🔑 <b>The provider has to include the data-pack registries, and that was measured, not
     * assumed.</b> The obvious choice —
     * {@code RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)} — lists 95
     * registries and does <em>not</em> contain {@code minecraft:damage_type}, so the first
     * {@code fireResistant()} item's initializer dies with
     * {@code IllegalStateException: Missing tag TagKey[minecraft:damage_type / minecraft:is_fire]}
     * and nothing binds at all. {@link VanillaRegistries#createLookup()} lists 141 and is the same
     * lookup Mojang's own {@code RegistryComponentsReport} data provider is handed, which is the one
     * other place in the jar that calls {@code build} outside a running game.
     *
     * <p>⚠️ <b>What this does NOT give you: tag CONTENTS.</b> A component that references a tag comes
     * back as an unresolved {@code NamedSet(TagKey[...])[null]}, because tag membership lives in the
     * data pack and no data pack is loaded. That is the pre-existing "{@code isIn(TagKey)} throws in
     * unit tests" condition, unchanged on every band — not a regression introduced here. Assert on
     * component <em>presence</em> and on scalar component values; never on tag membership.
     *
     * <p>Deliberately not caught: if this throws, every downstream test would fail on a component
     * read anyway, and a bootstrap that swallows its own failure is precisely the vacuous-guard shape
     * {@link #itemComponentsAreBound()} exists to rule out.
     */
    private static void bindDataComponents() {
        final HolderLookup.Provider provider = VanillaRegistries.createLookup();
        for (DataComponentInitializers.PendingComponents<?> pending
                : BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(provider)) {
            pending.apply();
        }
    }

    /**
     * The vanilla item with this id path, or empty if <em>this</em> Minecraft version does not have
     * it.
     *
     * <p>Exists for items that arrive part-way through the supported range, so a test can assert the
     * right thing on every band from one source tree. The spears
     * ({@code wooden_spear} … {@code netherite_spear}) are the live case: they ship from
     * {@code 1.21.11} and do not exist on the {@code mc/1.21.10} band at all, where naming
     * {@code Items.IRON_SPEAR} is a compile error rather than a failing assertion.
     *
     * <p>⚠️⚠️ <b>{@code containsId} first — never {@code get} alone.</b> {@code Registries.ITEM} is a
     * <em>defaulted</em> registry: {@code get} on an unknown id returns {@code AIR}, not
     * {@code null}. A caller that null-checks the result of {@code get} therefore sees a perfectly
     * valid item and carries on with the wrong one. That exact trap shipped once already — the Hunter
     * skill read {@code Registries.ENTITY_TYPE} the same way and every unrecognised id silently
     * became a {@code PIG}. {@code platform/Materials} guards the same way for the same reason.
     */
    public static Optional<Item> optionalVanillaItem(String path) {
        final Identifier id = Identifier.withDefaultNamespace(path);
        return BuiltInRegistries.ITEM.containsKey(id) ? Optional.of(BuiltInRegistries.ITEM.getValue(id)) : Optional.empty();
    }

    /**
     * True if the item registry actually populated.
     *
     * <p>The point of this is negative assertions. "This band has no spears" and "the registry is
     * empty" are the same observation, so any test that concludes something from an <em>absence</em>
     * has to rule the second one out first — otherwise a broken bootstrap reads as a clean pass on
     * every band.
     */
    public static boolean itemRegistryIsPopulated() {
        return BuiltInRegistries.ITEM.containsKey(Identifier.withDefaultNamespace("iron_sword"))
                && BuiltInRegistries.ITEM.containsKey(Identifier.withDefaultNamespace("stone"));
    }

    /**
     * True if item registry entries actually had their data components bound.
     *
     * <p>The {@link #itemRegistryIsPopulated} argument, one layer down. A populated registry whose
     * holders are unbound is not a working bootstrap — every component read throws — and from the
     * outside "this band has no such component" and "nothing ever bound them" are again the same
     * observation. Any test that concludes something from a component being absent or default has to
     * rule the second one out first.
     *
     * <p>Iron sword is chosen because its component map is non-empty on every version in scope
     * (durability at minimum), so an <em>empty</em> map here is as much a failure as a throw.
     */
    public static boolean itemComponentsAreBound() {
        final Optional<Item> ironSword = optionalVanillaItem("iron_sword");
        if (ironSword.isEmpty()) {
            return false;
        }
        return ironSword.get().builtInRegistryHolder().areComponentsBound()
                && !ironSword.get().components().isEmpty();
    }

    /**
     * The vanilla entity type with this id path, or empty if <em>this</em> Minecraft version does not
     * have it — {@link #optionalVanillaItem} for creatures.
     *
     * <p>The live case is the copper golem, which arrives part-way through the supported range. Below
     * that, {@code EntityType.COPPER_GOLEM} and {@code CopperGolemEntity} are both a compile error
     * rather than a failing assertion, so a test that names either cannot be built from one source
     * tree across bands.
     *
     * <p>⚠️⚠️ <b>{@code containsId} first — and here that is not merely good practice, it is the
     * exact trap this mod already shipped once.</b> {@code Registries.ENTITY_TYPE} is a
     * <em>defaulted</em> registry whose default is {@code PIG}, so {@code get} on an unknown id
     * hands back a perfectly valid pig. A test resolving {@code copper_golem} on a version without
     * one would therefore receive a pig, stub it as the victim, and assert happily that mcMMO
     * excluded a "copper golem" from Hunter XP — while actually proving that <b>pigs pay nothing</b>,
     * which is false and would have gone green. That is the same defaulted-registry bug that made
     * every unrecognised mob id a pig in Hunter's own first cut.
     */
    public static Optional<EntityType<?>> optionalVanillaEntityType(String path) {
        final Identifier id = Identifier.withDefaultNamespace(path);
        return BuiltInRegistries.ENTITY_TYPE.containsKey(id)
                ? Optional.of(BuiltInRegistries.ENTITY_TYPE.getValue(id))
                : Optional.empty();
    }

    /**
     * True if the entity-type registry actually populated.
     *
     * <p>The {@link #itemRegistryIsPopulated} argument, for the other registry: "this version has no
     * copper golem" and "the bootstrap never ran" are the same observation from the outside, so a
     * test concluding anything from an absence has to rule the second out first. {@code zombie} and
     * {@code cow} are chosen because they predate every version in scope by roughly a decade.
     */
    public static boolean entityTypeRegistryIsPopulated() {
        return BuiltInRegistries.ENTITY_TYPE.containsKey(Identifier.withDefaultNamespace("zombie"))
                && BuiltInRegistries.ENTITY_TYPE.containsKey(Identifier.withDefaultNamespace("cow"));
    }
}
