package com.gmail.nossr50.datatypes.skills.alchemy;

import org.jetbrains.annotations.Nullable;

/**
 * The "stage" (1–5) of an Alchemy potion, which selects its brew XP reward
 * ({@code Experience_Values.Alchemy.Potion_Brewing.Stage_N}). The stage counts how many brewing
 * steps the potion represents: a base effect, an amplifier (glowstone), a duration extension
 * (redstone), and splash/lingering conversion (gunpowder/dragon's breath) each add one.
 *
 * <p>Retargeted from Bukkit {@code PotionMeta} onto the Minecraft-free {@link PotionSpec} read off
 * the built potion stack. "Strong" ({@code strong_}) counts as the amplifier step and "long"
 * ({@code long_}) as the duration step, mirroring how the legacy code read glowstone/redstone off
 * the meta.
 *
 * <p>Phase 2 slice 5 made this class fully Minecraft-free: it used to read a
 * {@code PotionContentsComponent} and {@code RegistryEntry<Potion>} directly. Every question it asks
 * is now answered by {@link PotionSpec}, which {@code platform/Potions} produces.
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
        final PotionSpec spec = alchemyPotion.getSpec();
        return spec != null && spec.isWaterBase();
    }

    public static PotionStage getPotionStage(AlchemyPotion alchemyPotion) {
        return getPotionStage(alchemyPotion.getSpec());
    }

    /**
     * The brew stage of the potion described by {@code spec}. A {@code null} spec (a stack with no
     * potion contents at all) is stage one, the same floor the component-null branch produced.
     */
    public static PotionStage getPotionStage(@Nullable PotionSpec spec) {
        if (spec == null) {
            return ONE;
        }

        int stage = 1;

        // Any effect at all (a custom effect or a base-potion effect) is the first brew step.
        if (spec.hasCustomEffects() || spec.baseHasEffects()) {
            stage++;
        }

        // Amplifier step: a strong_ base, else a custom effect with a raised amplifier.
        if (spec.isStrongBase() || spec.hasAmplifiedCustomEffect()) {
            stage++;
        }

        // Duration step: a long_ base.
        if (spec.isLongBase()) {
            stage++;
        }

        // Dispersion step: splash or lingering.
        if (spec.form() != PotionForm.NORMAL) {
            stage++;
        }

        return getPotionStageNumerical(stage);
    }
}
