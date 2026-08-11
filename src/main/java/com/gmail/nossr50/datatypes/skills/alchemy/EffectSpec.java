package com.gmail.nossr50.datatypes.skills.alchemy;

import static java.util.Objects.requireNonNull;

import org.jetbrains.annotations.NotNull;

/**
 * A Minecraft-free description of one custom status effect on a potion: which effect, how strong,
 * and for how long. The MC-free half of a vanilla {@code StatusEffectInstance}.
 *
 * @param effectId  the <em>namespaced</em> status-effect registry id, e.g. {@code minecraft:speed}.
 *                  Namespaced deliberately: comparing bare paths would let another mod's
 *                  {@code mymod:speed} match vanilla's.
 * @param amplifier the effect amplifier (0 = level I), as vanilla counts it
 * @param duration  the effect duration in ticks
 */
public record EffectSpec(@NotNull String effectId, int amplifier, int duration) {

    public EffectSpec {
        requireNonNull(effectId, "effectId cannot be null");
    }

    /**
     * Whether this effect is the same effect at the same strength and length as {@code other} — the
     * three fields mcMMO's brew resolution keys on, matching what the legacy Bukkit
     * {@code PotionMeta} comparison compared.
     */
    public boolean matches(@NotNull EffectSpec other) {
        return effectId.equals(other.effectId)
                && amplifier == other.amplifier
                && duration == other.duration;
    }
}
