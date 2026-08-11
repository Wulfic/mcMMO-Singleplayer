package com.gmail.nossr50.util;

import java.util.Optional;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

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
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
        bootstrapped = true;
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
        final Identifier id = Identifier.ofVanilla(path);
        return Registries.ITEM.containsId(id) ? Optional.of(Registries.ITEM.get(id)) : Optional.empty();
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
        return Registries.ITEM.containsId(Identifier.ofVanilla("iron_sword"))
                && Registries.ITEM.containsId(Identifier.ofVanilla("stone"));
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
        final Identifier id = Identifier.ofVanilla(path);
        return Registries.ENTITY_TYPE.containsId(id)
                ? Optional.of(Registries.ENTITY_TYPE.get(id))
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
        return Registries.ENTITY_TYPE.containsId(Identifier.ofVanilla("zombie"))
                && Registries.ENTITY_TYPE.containsId(Identifier.ofVanilla("cow"));
    }
}
