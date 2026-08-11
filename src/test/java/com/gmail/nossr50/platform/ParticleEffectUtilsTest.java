package com.gmail.nossr50.platform;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.gmail.nossr50.fabric.McMMOMod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The MC-free half of {@link ParticleEffectUtils}: the milestone arithmetic, and the guarantee that
 * every entry point survives an unbound config.
 *
 * <p>The particle emission itself is not covered here — it needs a live {@code ServerWorld} — but
 * neither of the two things that could break gameplay does. A firework's <em>damage</em> is the other
 * one, and that lives in {@code FireworkRocketEntityMixin}, pinned by {@code MixinApplicationTest}.
 */
class ParticleEffectUtilsTest {

    @BeforeEach
    @AfterEach
    void unbindConfig() {
        McMMOMod.setGeneralConfig(null);
    }

    /**
     * ⚠️ The regression this exists for. {@code McMMOMod.getGeneralConfig()} is {@code @Nullable},
     * and {@code RuptureTask} — which is deliberately MC-free and unit-tested against mocks — calls
     * {@link ParticleEffectUtils#playBleedEffect} on <em>every</em> bleed damage tick. The first
     * draft dereferenced the config directly in each gate, which turns a cosmetic into an NPE thrown
     * from inside a scheduled task whenever the config is not yet bound.
     */
    @Test
    void everyEntryPointIsANoOpWhenTheConfigIsUnbound() {
        assertDoesNotThrow(() -> {
            ParticleEffectUtils.playBleedEffect(null);
            ParticleEffectUtils.playCrippleEffect(null);
            ParticleEffectUtils.playDodgeEffect(null);
            ParticleEffectUtils.playGreaterImpactEffect(null);
            ParticleEffectUtils.playCallOfTheWildEffect(null);
            ParticleEffectUtils.playAbilityEnabledEffect(null);
            ParticleEffectUtils.playAbilityDisabledEffect(null);
            ParticleEffectUtils.playLevelUpEffect(null, 100);
        });
    }

    @Test
    void aMilestoneFiresOnlyOnMultiplesOfTheTier() {
        assertTrue(ParticleEffectUtils.isMilestoneLevel(100, 100));
        assertTrue(ParticleEffectUtils.isMilestoneLevel(1000, 100));
        assertFalse(ParticleEffectUtils.isMilestoneLevel(99, 100));
        assertFalse(ParticleEffectUtils.isMilestoneLevel(101, 100));
        assertFalse(ParticleEffectUtils.isMilestoneLevel(150, 100));
    }

    /**
     * Level 0 is not a milestone even though {@code 0 % anything == 0}. Without the explicit
     * {@code newLevel >= 1} guard a profile sitting at level 0 would qualify.
     */
    @Test
    void levelZeroIsNotAMilestone() {
        assertFalse(ParticleEffectUtils.isMilestoneLevel(0, 100));
    }

    /**
     * ⚠️ {@code Particles.LevelUp_Tier} is a ModMenu slider and a hand-editable yml value, and
     * {@code GeneralConfig}'s load-time validation only <em>warns</em> about a sub-1 tier — it does
     * not correct it. {@code newLevel % 0} throws {@link ArithmeticException}, and this runs inside
     * an XP award, so a zero tier would kill the level-up path rather than skip a firework.
     */
    @Test
    void aTierOfZeroOrLessNeverFiresAndNeverDividesByZero() {
        assertDoesNotThrow(() -> ParticleEffectUtils.isMilestoneLevel(100, 0));
        assertFalse(ParticleEffectUtils.isMilestoneLevel(100, 0));
        assertFalse(ParticleEffectUtils.isMilestoneLevel(100, -5));
    }

    /** A tier of 1 makes every level a milestone — the boundary the {@code >= 1} guard allows. */
    @Test
    void aTierOfOneFiresOnEveryLevel() {
        assertTrue(ParticleEffectUtils.isMilestoneLevel(1, 1));
        assertTrue(ParticleEffectUtils.isMilestoneLevel(37, 1));
    }
}
