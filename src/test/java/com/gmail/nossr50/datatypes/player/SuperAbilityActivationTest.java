package com.gmail.nossr50.datatypes.player;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.datatypes.skills.ToolType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers the deterministic guard clauses of the K6 super-ability activation trigger
 * ({@link McMMOPlayer#processAbilityActivation}). These early-returns fire before the trigger reaches
 * the MC-typed / singleton-backed machinery (held-item detection, SkillTools, the scheduler,
 * NotificationManager), so they are the part that can be exercised MC-free.
 *
 * <p>The success path — flipping tool-prep mode on and scheduling the {@code ToolLowerTask} — needs a
 * real held item, {@code SkillTools}, and the tick scheduler, so it is verified in-game (Phase 12)
 * rather than here, matching the project's "gate is unit-tested, roll/side-effects are in-game" split.
 * The observable proxy for "the trigger bailed" is that <em>no tool was readied</em>
 * ({@link McMMOPlayer#getToolPreparationMode} stays {@code false}).
 */
class SuperAbilityActivationTest {

    private static final UUID UID = UUID.fromString("00000000-0000-0000-0000-0000000000ab");

    private PlatformPlayer player;
    private McMMOPlayer mmoPlayer;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setExperienceConfig(new ExperienceConfig(dataFolder));
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));

        player = mock(PlatformPlayer.class);
        when(player.getName()).thenReturn("TestPlayer");
        when(player.getUniqueId()).thenReturn(UID);
        // Neutralise the "only activate while sneaking" gate so the test targets the intended guard,
        // regardless of the bundled config default.
        when(player.isSneaking()).thenReturn(true);

        mmoPlayer = new McMMOPlayer(player, new PlayerProfile("TestPlayer", UID, 0));
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setExperienceConfig(null);
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setAdvancedConfig(null);
    }

    @Test
    void doesNotReadyToolWhenAbilityUseToggledOff() {
        mmoPlayer.toggleAbilityUse(); // default true → off

        mmoPlayer.processAbilityActivation(PrimarySkillType.MINING);

        assertFalse(mmoPlayer.getToolPreparationMode(ToolType.PICKAXE),
                "ability use disabled: no tool should be readied");
    }

    @Test
    void doesNotReadyToolWhileAnotherSuperAbilityIsActive() {
        // Any super ability already running blocks readying a new tool.
        mmoPlayer.setAbilityMode(SuperAbilityType.BERSERK, true);

        mmoPlayer.processAbilityActivation(PrimarySkillType.MINING);

        assertFalse(mmoPlayer.getToolPreparationMode(ToolType.PICKAXE),
                "a super ability is active: no tool should be readied");
    }
}
