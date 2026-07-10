package com.gmail.nossr50.util.sounds;

import com.gmail.nossr50.config.SoundConfig;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.sound.SoundCategory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Plays mcMMO's feedback sounds (level-up, super-ability activation, tool-ready, anvil, etc.) to a
 * player. Singleplayer port of legacy {@code util/sounds/SoundManager}.
 *
 * <p><b>Design (mirrors {@link com.gmail.nossr50.util.player.NotificationManager}'s scope):</b> the
 * per-{@link SoundType} <em>tuning</em> — enabled flag, volume (master-scaled), pitch — is read from
 * the real {@code sounds.yml} via {@link SoundConfig} (through {@link McMMOMod#getSoundConfig()},
 * replacing legacy's {@code SoundConfig.getInstance()}). The MC-typed glue (sound-registry lookup +
 * actual playback) lives in {@link PlatformPlayer#playSound}, so this class stays unit-testable
 * against a mocked player. Every entry point takes a {@link PlatformPlayer} and plays at that
 * player's position.
 *
 * <p><b>What was dropped and why (singleplayer / modern MC):</b>
 * <ul>
 *   <li><b>Player-only vs. world-broadcast distinction.</b> Legacy had {@code sendSound} (only that
 *       player hears it) and {@code worldSendSound} (everyone in range hears it). In singleplayer
 *       there is one listener, so both collapse to "play at the player"; the method names are kept
 *       for call-site fidelity but route through the same {@link PlatformPlayer#playSound}.</li>
 *   <li><b>The legacy-version reflection sound lookup</b> ({@code getSoundLegacyCustom},
 *       {@code getCrippleSound}, {@code SoundRegistryUtils}, the {@code soundCache}) — that machinery
 *       existed to bridge Spigot's enum→interface {@code Sound} change across Bukkit versions. On a
 *       single fixed target (1.21.11) the sound id resolves directly through the vanilla registry in
 *       {@link PlatformPlayer#playSound}.</li>
 *   <li><b>{@code Location}/{@code World} parameters</b> — not wrapped in the platform layer; every
 *       ported call site passed {@code player.getLocation()}/{@code player.getWorld()}, so the player
 *       position is used.</li>
 * </ul>
 */
public final class SoundManager {

    private SoundManager() {
    }

    /**
     * Plays {@code soundType} to {@code player} under the {@link SoundCategory#MASTER} category.
     * Replaces legacy {@code sendSound(Player, Location, SoundType)}.
     */
    public static void sendSound(@Nullable PlatformPlayer player, @NotNull SoundType soundType) {
        SoundConfig soundConfig = readyConfig(player, soundType);
        if (soundConfig != null) {
            emit(player, soundConfig, soundType, SoundCategory.MASTER, getPitch(soundConfig, soundType));
        }
    }

    /**
     * Plays {@code soundType} to {@code player} under an explicit {@link SoundCategory}. Replaces
     * legacy {@code sendCategorizedSound(Player, Location, SoundType, SoundCategory)}.
     */
    public static void sendCategorizedSound(@Nullable PlatformPlayer player,
            @NotNull SoundType soundType, @NotNull SoundCategory soundCategory) {
        SoundConfig soundConfig = readyConfig(player, soundType);
        if (soundConfig != null) {
            emit(player, soundConfig, soundType, soundCategory, getPitch(soundConfig, soundType));
        }
    }

    /**
     * As {@link #sendCategorizedSound(PlatformPlayer, SoundType, SoundCategory)} but shifts the pitch
     * by {@code pitchModifier} (clamped to a max of {@code 2.0}), matching legacy.
     */
    public static void sendCategorizedSound(@Nullable PlatformPlayer player,
            @NotNull SoundType soundType, @NotNull SoundCategory soundCategory,
            float pitchModifier) {
        SoundConfig soundConfig = readyConfig(player, soundType);
        if (soundConfig != null) {
            float totalPitch = Math.min(2.0F, getPitch(soundConfig, soundType) + pitchModifier);
            emit(player, soundConfig, soundType, soundCategory, totalPitch);
        }
    }

    /**
     * Legacy {@code worldSendSound(World, Location, SoundType)}: a world-audible sound with no
     * explicit category (Bukkit defaulted to {@link SoundCategory#MASTER}). In singleplayer this is
     * equivalent to {@link #sendSound}.
     */
    public static void worldSendSound(@Nullable PlatformPlayer player, @NotNull SoundType soundType) {
        sendSound(player, soundType);
    }

    /**
     * Legacy {@code worldSendSoundMaxPitch}: like {@link #worldSendSound} but pinned to maximum pitch
     * ({@code 2.0}).
     */
    public static void worldSendSoundMaxPitch(@Nullable PlatformPlayer player,
            @NotNull SoundType soundType) {
        SoundConfig soundConfig = readyConfig(player, soundType);
        if (soundConfig != null) {
            emit(player, soundConfig, soundType, SoundCategory.MASTER, 2.0F);
        }
    }

    /**
     * Returns the bound {@link SoundConfig} if this sound should play — a non-null player, a bound
     * config, and the sound enabled in {@code sounds.yml} — otherwise {@code null} (a no-op). Volume
     * and pitch are read from the returned config, so nothing dereferences it before this gate.
     */
    private static @Nullable SoundConfig readyConfig(@Nullable PlatformPlayer player,
            @NotNull SoundType soundType) {
        SoundConfig soundConfig = McMMOMod.getSoundConfig();
        if (player == null || soundConfig == null || !soundConfig.getIsEnabled(soundType)) {
            return null;
        }
        return soundConfig;
    }

    /**
     * Resolves the sound id — a custom id from config if set, otherwise the {@link SoundType}'s
     * default — computes the master-scaled volume, and hands it to the platform for playback.
     */
    private static void emit(@NotNull PlatformPlayer player, @NotNull SoundConfig soundConfig,
            @NotNull SoundType soundType, @NotNull SoundCategory soundCategory, float pitch) {
        float volume = soundConfig.getVolume(soundType) * soundConfig.getMasterVolume();
        String customId = soundConfig.getSound(soundType);
        String soundId = (customId != null && !customId.isEmpty()) ? customId : soundType.id();
        player.playSound(soundId, soundCategory, volume, pitch);
    }

    private static float getPitch(@NotNull SoundConfig soundConfig, @NotNull SoundType soundType) {
        return switch (soundType) {
            case FIZZ -> getFizzPitch();
            case POP -> getPopPitch();
            default -> soundConfig.getPitch(soundType);
        };
    }

    /** Randomized fizz pitch (verbatim legacy math). RNG is cosmetic, not a gameplay roll. */
    public static float getFizzPitch() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return 2.6F + (random.nextFloat() - random.nextFloat()) * 0.8F;
    }

    /** Randomized item-pickup ("pop") pitch (verbatim legacy math). */
    public static float getPopPitch() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return ((random.nextFloat() - random.nextFloat()) * 0.7F + 1.0F) * 2.0F;
    }
}
