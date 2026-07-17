package com.gmail.nossr50.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.CombatXp.MobCategory;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies the per-hit combat-XP core against the real bundled {@code experience.yml}: the per-damage
 * base ({@code Combat.Multiplier * 10}), the two creditable-damage guards legacy's
 * {@code AwardCombatXpTask} applied, and the {@code (int) (damage * base * multiplier)} award.
 */
class CombatXpTest {

    @BeforeEach
    void loadConfig(@TempDir Path dir) {
        McMMOMod.setExperienceConfig(new ExperienceConfig(dir));
    }

    @AfterEach
    void clearConfig() {
        McMMOMod.setExperienceConfig(null);
    }

    // --- base XP ------------------------------------------------------------

    @Test
    void monsterUsesConfiguredCombatMultiplierTimesTen() {
        // Zombie: 2.0 in experience.yml -> 2.0 * 10.
        assertEquals(20.0, CombatXp.baseXp("minecraft:zombie", MobCategory.MONSTER));
        // Skeleton: 3.0 -> 30.
        assertEquals(30.0, CombatXp.baseXp("minecraft:skeleton", MobCategory.MONSTER));
    }

    @Test
    void animalUsesAnimalsMultiplierTimesTen() {
        // Cow: 1.0 -> 10.
        assertEquals(10.0, CombatXp.baseXp("minecraft:cow", MobCategory.ANIMAL));
        // An animal with no configured multiplier falls back to the Animals default (1.0) -> 10.
        assertEquals(10.0, CombatXp.baseXp("mcmmotest:not_a_real_mob", MobCategory.ANIMAL));
    }

    @Test
    void otherFallsBackToTheLegacyFloorWhenUnlisted() {
        // Iron Golem is listed at 2.0 -> 20.
        assertEquals(20.0, CombatXp.baseXp("minecraft:iron_golem", MobCategory.OTHER));
        // Anything genuinely unlisted takes legacy's 1.0 floor -> 10.
        assertEquals(10.0, CombatXp.baseXp("mcmmotest:not_a_real_mob", MobCategory.OTHER));
    }

    @Test
    void unloadedConfigYieldsZero() {
        McMMOMod.setExperienceConfig(null);
        assertEquals(0.0, CombatXp.baseXp("minecraft:zombie", MobCategory.MONSTER));
    }

    // --- creditable damage --------------------------------------------------

    @Test
    void damageBelowRemainingHealthIsCreditedInFull() {
        assertEquals(7.0, CombatXp.creditableDamage(7.0, 20.0));
    }

    @Test
    void overkillIsCreditedOnlyUpToTheVictimsRemainingHealth() {
        // Legacy's `if (health < 0) damage += health`: a 40-damage hit on a 2-HP zombie pays for 2.
        assertEquals(2.0, CombatXp.creditableDamage(40.0, 2.0));
    }

    @Test
    void theHpCeilingCapsASingleHugeHit() {
        // ExploitFix.Combat.XPCeiling defaults: enabled, HP_Modifier_Limit 100. Neither key ships in
        // experience.yml, so the ceiling is live and clamps a 500-damage hit on a 500-HP modded mob.
        assertEquals(100.0, CombatXp.creditableDamage(500.0, 500.0));
    }

    @Test
    void nonPositiveDamageCreditsNothing() {
        assertEquals(0.0, CombatXp.creditableDamage(0.0, 20.0));
        assertEquals(0.0, CombatXp.creditableDamage(5.0, 0.0)); // already dead
        assertEquals(0.0, CombatXp.creditableDamage(5.0, -3.0)); // health underflowed somehow
    }

    // --- the award ----------------------------------------------------------

    @Test
    void xpForHitIsDamageTimesBaseTruncated() {
        // Zombie base 2.0 * 10 = 20 per damage point; a 3.5-damage hit -> (int) (3.5 * 20) = 70.
        assertEquals(70, CombatXp.xpForHit("minecraft:zombie", MobCategory.MONSTER, 3.5, 20.0, 1.0));
    }

    @Test
    void xpForHitTruncatesRatherThanRounds() {
        // Cow base 1.0 * 10 = 10; 0.45 damage -> 4.5 -> truncated to 4, as legacy's (int) cast did.
        assertEquals(4, CombatXp.xpForHit("minecraft:cow", MobCategory.ANIMAL, 0.45, 10.0, 1.0));
    }

    @Test
    void xpForHitAppliesTheMultiplier() {
        // The wolf-assist x3: a 3.5-damage bite on a zombie pays 3x what the player's own swing would.
        assertEquals(210, CombatXp.xpForHit("minecraft:zombie", MobCategory.MONSTER, 3.5, 20.0, 3.0));
    }

    @Test
    void xpForHitPaysOnlyForTheOverkillClampedDamage() {
        // 40 damage into a 2-HP zombie: (int) (2 * 20) = 40, not (int) (40 * 20) = 800.
        assertEquals(40, CombatXp.xpForHit("minecraft:zombie", MobCategory.MONSTER, 40.0, 2.0, 1.0));
    }

    @Test
    void aZeroMultiplierPaysNothingRegardlessOfDamage() {
        // Legacy tests baseXP for positivity *after* applying the multiplier, then skips the award.
        assertEquals(0, CombatXp.xpForHit("minecraft:zombie", MobCategory.MONSTER, 40.0, 100.0, 0.0));
    }

    @Test
    void anUnlistedMonsterPaysNothing() {
        // MONSTER takes getCombatXP with no floor, so a genuinely unlisted monster is worth 0.
        assertEquals(0,
                CombatXp.xpForHit("mcmmotest:not_a_real_mob", MobCategory.MONSTER, 10.0, 20.0, 1.0));
    }
}
