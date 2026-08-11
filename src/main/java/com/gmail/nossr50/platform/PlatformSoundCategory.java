package com.gmail.nossr50.platform;

/**
 * The volume-slider category a sound obeys — mcMMO's platform-neutral mirror of vanilla's
 * {@code net.minecraft.sound.SoundCategory}.
 *
 * <p><b>Why this exists (Phase 2, multi-version support).</b> Sound playback is the one MC concept
 * that reaches deep into skill logic: {@code MacesManager}, {@code RepairManager},
 * {@code NotificationManager} and {@code ParticleEffectUtils} each named a category and nothing
 * else, so a single vanilla enum was dragging {@code net.minecraft} into five otherwise MC-free
 * files. This enum carries the same information with no Minecraft on the classpath; the mapping to
 * the vanilla constant lives in {@link PlatformPlayer#playSound}, which is the only place that
 * actually needs it.
 *
 * <p>The constants mirror vanilla 1:1 (verified against {@code SoundCategory} on 1.21.11 — note
 * {@code UI} exists and is easy to forget). Keeping the sets identical means the mapping is a total
 * switch with no default arm, so a constant that disappears on some other Minecraft band becomes a
 * <em>compile error in {@code platform/}</em> rather than a silent fallback to {@code MASTER}.
 */
public enum PlatformSoundCategory {
    MASTER,
    MUSIC,
    RECORDS,
    WEATHER,
    BLOCKS,
    HOSTILE,
    NEUTRAL,
    PLAYERS,
    AMBIENT,
    VOICE,
    UI
}
