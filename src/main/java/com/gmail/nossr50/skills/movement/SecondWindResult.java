package com.gmail.nossr50.skills.movement;

import org.jetbrains.annotations.NotNull;

/**
 * The resolved effect of one Second Wind activation — everything the MC-typed trigger layer needs
 * to actually apply it, computed MC-free so the numbers are unit-testable.
 *
 * <p>Second Wind is a single super ability with three bodies, picked by the medium the player is
 * moving through at the moment they activate it. Modelling that as one result type with a
 * {@link #medium()} discriminator (rather than three ability enums) is what keeps the whole feature
 * to one cooldown slot, one config block and one locale block.
 *
 * @param medium         which body fired
 * @param durationTicks  how long the effect lasts (water/air bodies); {@code 0} for the instant
 *                       land lunge
 * @param magnitude      the body's headline number: lunge velocity (land), effect amplifier
 *                       (water), or forward boost multiplier (air)
 * @param dartRange      raycast reach of the land lunge, in blocks; unused by the other bodies
 * @param dartDamage     damage dealt to entities caught by the land lunge; unused by the others
 * @param dartKnockback  knockback strength applied by the land lunge; unused by the others
 */
public record SecondWindResult(
        @NotNull Medium medium,
        int durationTicks,
        double magnitude,
        double dartRange,
        double dartDamage,
        double dartKnockback) {
}
