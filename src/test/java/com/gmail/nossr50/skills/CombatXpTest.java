package com.gmail.nossr50.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.CombatXp.MobCategory;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies the Phase 3 combat-XP core: weapon→skill routing and the per-kill base XP against the
 * real bundled {@code experience.yml} ({@code base * 10}).
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

    @Test
    void weaponSkillRoutesByHeldItem() {
        assertEquals(PrimarySkillType.SWORDS, CombatXp.weaponSkill("minecraft:diamond_sword"));
        assertEquals(PrimarySkillType.AXES, CombatXp.weaponSkill("minecraft:netherite_axe"));
        assertEquals(PrimarySkillType.MACES, CombatXp.weaponSkill("minecraft:mace"));
        assertEquals(PrimarySkillType.TRIDENTS, CombatXp.weaponSkill("minecraft:trident"));
        assertEquals(PrimarySkillType.ARCHERY, CombatXp.weaponSkill("minecraft:bow"));
        assertEquals(PrimarySkillType.CROSSBOWS, CombatXp.weaponSkill("minecraft:crossbow"));
    }

    @Test
    void unrecognisedItemsAndEmptyHandTrainUnarmed() {
        assertEquals(PrimarySkillType.UNARMED, CombatXp.weaponSkill("minecraft:apple"));
        assertEquals(PrimarySkillType.UNARMED, CombatXp.weaponSkill("minecraft:diamond_pickaxe"));
        assertEquals(PrimarySkillType.UNARMED, CombatXp.weaponSkill(""));
        assertEquals(PrimarySkillType.UNARMED, CombatXp.weaponSkill(null));
    }

    @Test
    void monsterUsesConfiguredCombatMultiplierTimesTen() {
        // Zombie: 2.0 in experience.yml -> 2.0 * 10.
        assertEquals(20.0, CombatXp.baseXpForKill("minecraft:zombie", MobCategory.MONSTER));
        // Skeleton: 3.0 -> 30.
        assertEquals(30.0, CombatXp.baseXpForKill("minecraft:skeleton", MobCategory.MONSTER));
    }

    @Test
    void animalUsesAnimalsMultiplierTimesTen() {
        // Cow: 1.0 -> 10.
        assertEquals(10.0, CombatXp.baseXpForKill("minecraft:cow", MobCategory.ANIMAL));
        // An animal with no configured multiplier falls back to the Animals default (1.0) -> 10.
        assertEquals(10.0, CombatXp.baseXpForKill("mcmmotest:not_a_real_mob", MobCategory.ANIMAL));
    }

    @Test
    void unloadedConfigYieldsZero() {
        McMMOMod.setExperienceConfig(null);
        assertEquals(0.0, CombatXp.baseXpForKill("minecraft:zombie", MobCategory.MONSTER));
    }
}
