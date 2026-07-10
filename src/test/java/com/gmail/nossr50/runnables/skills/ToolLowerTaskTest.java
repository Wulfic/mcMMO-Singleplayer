package com.gmail.nossr50.runnables.skills;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.ToolType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Gate behaviour of the ported {@link ToolLowerTask}: it only clears prep mode (and notifies) when
 * the tool is actually prepared. Configs are the real bundled defaults ({@code Abilities.Messages}
 * defaults true).
 */
class ToolLowerTaskTest {

    private McMMOPlayer mmoPlayer;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));

        mmoPlayer = mock(McMMOPlayer.class);
        PlatformPlayer platformPlayer = mock(PlatformPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setAdvancedConfig(null);
    }

    @Test
    void doesNothingWhenToolNotPrepared() {
        when(mmoPlayer.getToolPreparationMode(ToolType.PICKAXE)).thenReturn(false);

        new ToolLowerTask(mmoPlayer, ToolType.PICKAXE).run();

        verify(mmoPlayer, never()).setToolPreparationMode(ToolType.PICKAXE, false);
    }

    @Test
    void clearsPrepModeWhenPrepared() {
        when(mmoPlayer.getToolPreparationMode(ToolType.PICKAXE)).thenReturn(true);
        // Chat notifications off → NotificationManager is a no-op, so this test only asserts the
        // state change and never touches the routing config.
        when(mmoPlayer.useChatNotifications()).thenReturn(false);

        new ToolLowerTask(mmoPlayer, ToolType.PICKAXE).run();

        verify(mmoPlayer).setToolPreparationMode(ToolType.PICKAXE, false);
    }
}
