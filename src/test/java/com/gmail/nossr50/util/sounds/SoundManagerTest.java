package com.gmail.nossr50.util.sounds;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.SoundConfig;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import java.nio.file.Path;
import net.minecraft.sound.SoundCategory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the singleplayer {@link SoundManager} tuning + routing against the real bundled
 * {@code sounds.yml} (volume master-scaling, pitch selection, category, default sound id) and,
 * via a mocked {@link SoundConfig}, the enable-gate, the null-config/null-player no-ops, the
 * custom-sound-id override and master-volume multiplication. Playback itself is verified through a
 * mocked {@link PlatformPlayer#playSound} — the MC-typed registry lookup + emit is exercised
 * in-game, not here.
 */
class SoundManagerTest {

    private PlatformPlayer player;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setSoundConfig(new SoundConfig(dataFolder));
        player = mock(PlatformPlayer.class);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setSoundConfig(null);
    }

    @Test
    void sendSoundUsesMasterCategoryAndConfiguredVolumePitch() {
        // LEVEL_UP in sounds.yml: Volume 0.3, Pitch 0.5, MasterVolume 1.0.
        SoundManager.sendSound(player, SoundType.LEVEL_UP);

        verify(player).playSound("minecraft:entity.player.levelup", SoundCategory.MASTER, 0.3F, 0.5F);
    }

    @Test
    void sendCategorizedSoundUsesGivenCategory() {
        // ANVIL in sounds.yml: Volume 1.0, Pitch 0.3.
        SoundManager.sendCategorizedSound(player, SoundType.ANVIL, SoundCategory.BLOCKS);

        verify(player).playSound("minecraft:block.anvil.place", SoundCategory.BLOCKS, 1.0F, 0.3F);
    }

    @Test
    void pitchModifierIsClampedToTwo() {
        // DEFLECT_ARROWS base pitch is 2.0; +1.0 must clamp to 2.0, not 3.0.
        SoundManager.sendCategorizedSound(player, SoundType.DEFLECT_ARROWS, SoundCategory.PLAYERS,
                1.0F);

        verify(player).playSound("minecraft:entity.ender_eye.death", SoundCategory.PLAYERS, 1.0F,
                2.0F);
    }

    @Test
    void worldSendSoundMaxPitchPinsPitchToTwo() {
        // LEVEL_UP volume 0.3 kept, pitch forced to 2.0.
        SoundManager.worldSendSoundMaxPitch(player, SoundType.LEVEL_UP);

        verify(player).playSound("minecraft:entity.player.levelup", SoundCategory.MASTER, 0.3F, 2.0F);
    }

    @Test
    void disabledSoundIsNoOp() {
        SoundConfig disabled = mock(SoundConfig.class);
        when(disabled.getIsEnabled(SoundType.LEVEL_UP)).thenReturn(false);
        McMMOMod.setSoundConfig(disabled);

        SoundManager.sendSound(player, SoundType.LEVEL_UP);

        verifyNoInteractions(player);
    }

    @Test
    void nullConfigIsNoOp() {
        McMMOMod.setSoundConfig(null);

        SoundManager.sendSound(player, SoundType.LEVEL_UP);

        verifyNoInteractions(player);
    }

    @Test
    void nullPlayerIsANoOp() {
        // Must not throw.
        SoundManager.sendSound(null, SoundType.LEVEL_UP);
    }

    @Test
    void customSoundIdOverridesDefaultAndVolumeIsMasterScaled() {
        SoundConfig custom = mock(SoundConfig.class);
        when(custom.getIsEnabled(SoundType.LEVEL_UP)).thenReturn(true);
        when(custom.getSound(SoundType.LEVEL_UP)).thenReturn("minecraft:ui.button.click");
        when(custom.getVolume(SoundType.LEVEL_UP)).thenReturn(0.5F);
        when(custom.getMasterVolume()).thenReturn(0.5F);
        when(custom.getPitch(SoundType.LEVEL_UP)).thenReturn(1.0F);
        McMMOMod.setSoundConfig(custom);

        SoundManager.sendSound(player, SoundType.LEVEL_UP);

        // 0.5 (volume) * 0.5 (master) = 0.25, and the custom id replaces the SoundType default.
        verify(player).playSound("minecraft:ui.button.click", SoundCategory.MASTER, 0.25F, 1.0F);
    }
}
