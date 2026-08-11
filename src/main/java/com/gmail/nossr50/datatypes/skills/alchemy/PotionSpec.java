package com.gmail.nossr50.datatypes.skills.alchemy;

import static java.util.Objects.requireNonNull;

import com.gmail.nossr50.util.PotionNames;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A Minecraft-free description of a potion: its base potion type, whether that base carries effects
 * of its own, its custom effects, and its dispersion form. The MC-free half of a vanilla
 * {@code PotionContentsComponent} plus the potion item's own identity.
 *
 * <p>This is what lets {@link PotionStage} and {@code AlchemyPotion}'s brew matching stay MC-free:
 * every question mcMMO's Alchemy asks of a potion ("does it have effects?", "is it a strong_
 * variant?", "is it the same potion as that one?") is answerable from these four fields.
 * {@code platform/Potions#specOf} is the single place that reads one off a live stack.
 *
 * @param basePotionId    the <em>namespaced</em> base-potion registry id, e.g.
 *                        {@code minecraft:long_swiftness}, or {@code null} when the potion has no
 *                        base type. Namespaced deliberately — bare-path equality would let another
 *                        mod's {@code mymod:swiftness} match vanilla's, which the
 *                        {@code RegistryEntry} comparison this replaces would never have done.
 * @param baseHasEffects  whether the base potion type carries any status effects of its own
 * @param customEffects   the potion's custom (non-base) effects; empty, never null
 * @param form            drinkable / splash / lingering
 */
public record PotionSpec(@Nullable String basePotionId, boolean baseHasEffects,
        @NotNull List<EffectSpec> customEffects, @NotNull PotionForm form) {

    public PotionSpec {
        requireNonNull(customEffects, "customEffects cannot be null");
        requireNonNull(form, "form cannot be null");
        customEffects = List.copyOf(customEffects);
    }

    /**
     * The registry <em>path</em> of the base potion (e.g. {@code long_swiftness}), or {@code null}
     * when there is no base potion. This is what the {@code strong_}/{@code long_}/{@code water}
     * predicates read — matching the legacy behaviour, which compared paths and ignored namespaces
     * for those three questions.
     */
    public @Nullable String basePotionPath() {
        if (basePotionId == null) {
            return null;
        }
        final int colon = basePotionId.indexOf(':');
        return colon < 0 ? basePotionId : basePotionId.substring(colon + 1);
    }

    /** Whether the base potion is the plain water potion (Alchemy's stage-1 base). */
    public boolean isWaterBase() {
        return PotionNames.isWater(basePotionPath());
    }

    /** Whether the base potion is an amplified ({@code strong_}) variant. */
    public boolean isStrongBase() {
        return PotionNames.isStrong(basePotionPath());
    }

    /** Whether the base potion is an extended ({@code long_}) variant. */
    public boolean isLongBase() {
        return PotionNames.isLong(basePotionPath());
    }

    public boolean hasCustomEffects() {
        return !customEffects.isEmpty();
    }

    /** Whether any custom effect is amplified beyond level I (the glowstone brew step). */
    public boolean hasAmplifiedCustomEffect() {
        for (EffectSpec effect : customEffects) {
            if (effect.amplifier() > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code other} describes the same potion <em>contents</em> as this one: the same base
     * potion and the same set of custom effects. Deliberately does not compare {@link #form} — the
     * caller compares the potion item's identity separately, which is what distinguishes a splash
     * from a drinkable, exactly as the pre-seal {@code isSimilarPotion} did.
     */
    public boolean matchesContents(@NotNull PotionSpec other) {
        if (basePotionId == null ? other.basePotionId != null
                : !basePotionId.equals(other.basePotionId)) {
            return false;
        }
        if (customEffects.size() != other.customEffects.size()) {
            return false;
        }
        // Order-independent: every effect on one side must have a match on the other. mcMMO potions
        // carry at most a couple of custom effects, so the nested scan is cheap.
        for (EffectSpec effect : customEffects) {
            boolean matched = false;
            for (EffectSpec candidate : other.customEffects) {
                if (effect.matches(candidate)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }
}
