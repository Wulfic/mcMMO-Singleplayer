package com.gmail.nossr50.datatypes.skills.alchemy;

import com.gmail.nossr50.util.PotionUtil;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.potion.Potion;
import net.minecraft.registry.entry.RegistryEntry;

/**
 * The "stage" (1–5) of an Alchemy potion, which selects its brew XP reward
 * ({@code Experience_Values.Alchemy.Potion_Brewing.Stage_N}). The stage counts how many brewing
 * steps the potion represents: a base effect, an amplifier (glowstone), a duration extension
 * (redstone), and splash/lingering conversion (gunpowder/dragon's breath) each add one.
 *
 * <p>Retargeted from Bukkit {@code PotionMeta} onto the vanilla {@link PotionContentsComponent}
 * carried by the built potion stack. "Strong" ({@code strong_}) counts as the amplifier step and
 * "long" ({@code long_}) as the duration step, mirroring how the legacy code read glowstone/redstone
 * off the meta.
 */
public enum PotionStage {
    FIVE(5),
    FOUR(4),
    THREE(3),
    TWO(2),
    ONE(1);

    final int numerical;

    PotionStage(int numerical) {
        this.numerical = numerical;
    }

    public int toNumerical() {
        return numerical;
    }

    private static PotionStage getPotionStageNumerical(int numerical) {
        for (PotionStage potionStage : values()) {
            if (numerical >= potionStage.toNumerical()) {
                return potionStage;
            }
        }
        return ONE;
    }

    public static PotionStage getPotionStage(AlchemyPotion input, AlchemyPotion output) {
        PotionStage outputPotionStage = getPotionStage(output);
        PotionStage inputPotionStage = getPotionStage(input);
        // Swapping amplifiers between two same-stage potions still counts as the top stage.
        if (!isWaterBottle(input) && inputPotionStage == outputPotionStage) {
            outputPotionStage = PotionStage.FIVE;
        }
        return outputPotionStage;
    }

    private static boolean isWaterBottle(AlchemyPotion alchemyPotion) {
        final RegistryEntry<Potion> base = basePotion(alchemyPotion);
        return base != null && PotionUtil.isWater(base);
    }

    public static PotionStage getPotionStage(AlchemyPotion alchemyPotion) {
        final PotionContentsComponent contents = alchemyPotion.getPotionContents();
        final RegistryEntry<Potion> base = basePotion(alchemyPotion);

        int stage = 1;

        // Any effect at all (a custom effect or a base-potion effect) is the first brew step.
        final boolean hasCustomEffects =
                contents != null && !contents.customEffects().isEmpty();
        if (hasCustomEffects || (base != null && PotionUtil.hasBaseEffects(base))) {
            stage++;
        }

        // Amplifier step: a strong_ base, else a custom effect with a raised amplifier.
        if (base != null && PotionUtil.isStrong(base)) {
            stage++;
        } else if (hasCustomEffects) {
            for (StatusEffectInstance effect : contents.customEffects()) {
                if (effect.getAmplifier() > 0) {
                    stage++;
                    break;
                }
            }
        }

        // Duration step: a long_ base.
        if (base != null && PotionUtil.isLong(base)) {
            stage++;
        }

        // Dispersion step: splash or lingering.
        if (alchemyPotion.isSplash() || alchemyPotion.isLingering()) {
            stage++;
        }

        return getPotionStageNumerical(stage);
    }

    private static RegistryEntry<Potion> basePotion(AlchemyPotion alchemyPotion) {
        final PotionContentsComponent contents = alchemyPotion.getPotionContents();
        return contents == null ? null : contents.potion().orElse(null);
    }
}
