package com.gmail.nossr50.fabric;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.util.McTestRegistries;
import java.util.Arrays;
import net.minecraft.world.explosion.ExplosionImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Proves mcMMO's mixins actually apply to their targets — that every injection point still resolves
 * against this Minecraft version.
 *
 * <p>This exists because a boot smoke-test <i>cannot</i> prove it for every mixin. Mixins apply
 * lazily, when their target class is first loaded, so a bad injection surfaces as a crash at the
 * moment of first use rather than at startup. {@link ExplosionImpl} is the case in point: nothing
 * loads it during server boot, so the first creeper in a live world would be the first thing to find
 * out. Loading the class here, under the same Knot classloader the mod runs on, forces Mixin to
 * apply and throw ({@code InvalidInjectionException}) if a target has drifted.
 *
 * <p>{@code TntExplodeMixin}'s target ({@code TntEntity}) does load during boot, so it is covered by
 * the smoke-test; the classes whose mixins are proven only here are the ones worth listing.
 *
 * <p>Note this test deliberately does <b>not</b> live in {@code com.gmail.nossr50.fabric.mixin}:
 * that package is {@code mcmmo.mixins.json}'s declared mixin package, so Mixin would try to treat
 * the test class itself as a mixin and fail to transform it.
 */
class MixinApplicationTest {

    @BeforeAll
    static void bootstrap() {
        McTestRegistries.bootstrap();
    }

    @Test
    void blastMiningExplosionMixinApplies() {
        // Class-loading ExplosionImpl is what triggers mixin application: if either injection in
        // ExplosionDropsMixin (the destroyBlocks HEAD hook, or the onExploded drop-collector arg)
        // no longer matches, this throws rather than silently no-op'ing.
        assertDoesNotThrow(() -> Class.forName(ExplosionImpl.class.getName(), true,
                MixinApplicationTest.class.getClassLoader()));

        // ...and prove the mixin was really applied, rather than the class merely loading: the
        // @Unique flag ExplosionDropsMixin adds only exists on a transformed ExplosionImpl.
        final boolean hasMixinField = Arrays.stream(ExplosionImpl.class.getDeclaredFields())
                .anyMatch(field -> field.getName().contains("blastMiningHandled"));
        assertTrue(hasMixinField,
                "ExplosionDropsMixin did not apply to ExplosionImpl — its blast-mining drop "
                        + "replacement would silently never run in-game");
    }
}
