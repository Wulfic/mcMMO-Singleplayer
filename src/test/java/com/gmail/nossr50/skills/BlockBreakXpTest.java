package com.gmail.nossr50.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies the Phase 3 block-break XP resolver routes vanilla blocks to the right gathering skill
 * with the XP amount from the real bundled {@code experience.yml}.
 */
class BlockBreakXpTest {

    @BeforeEach
    void loadConfig(@TempDir Path dir) {
        McMMOMod.setExperienceConfig(new ExperienceConfig(dir));
    }

    @AfterEach
    void clearConfig() {
        McMMOMod.setExperienceConfig(null);
    }

    @Test
    void routesMiningBlocks() {
        final BlockBreakXp.Reward reward = BlockBreakXp.resolve("minecraft:stone");
        assertEquals(PrimarySkillType.MINING, reward.skill());
        assertEquals(15, reward.xp());
    }

    @Test
    void routesWoodcuttingLogs() {
        final BlockBreakXp.Reward reward = BlockBreakXp.resolve("minecraft:oak_log");
        assertEquals(PrimarySkillType.WOODCUTTING, reward.skill());
        assertEquals(70, reward.xp());
    }

    @Test
    void routesExcavationBlocks() {
        final BlockBreakXp.Reward reward = BlockBreakXp.resolve("minecraft:dirt");
        assertEquals(PrimarySkillType.EXCAVATION, reward.skill());
        assertEquals(40, reward.xp());
    }

    @Test
    void acceptsBareRegistryPathWithoutNamespace() {
        final BlockBreakXp.Reward reward = BlockBreakXp.resolve("coal_ore");
        assertEquals(PrimarySkillType.MINING, reward.skill());
        assertEquals(400, reward.xp());
    }

    @Test
    void nonGatheringBlockYieldsNoReward() {
        assertNull(BlockBreakXp.resolve("minecraft:bedrock"));
    }

    @Test
    void unloadedConfigYieldsNoReward() {
        McMMOMod.setExperienceConfig(null);
        assertNull(BlockBreakXp.resolve("minecraft:stone"));
    }
}
