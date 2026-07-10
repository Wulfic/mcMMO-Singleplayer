package com.gmail.nossr50.skills.alchemy;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.skills.RankUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Alchemy skill manager (Phase 10.3 port, legacy 69 lines) — the 20th and final skill manager. The
 * portable numeric core is the Concoctions tier lookup and the Catalysis brew-speed curve
 * ({@link #calculateBrewSpeed}); everything touching the potion ingredient tables, brewing-stand
 * {@code ItemStack}s, or the potion-brew XP award is deferred until the still-unported
 * {@code PotionConfig}/{@code PotionStage} and the item adapters land — same convention as
 * {@link com.gmail.nossr50.skills.repair.RepairManager} and
 * {@link com.gmail.nossr50.skills.salvage.SalvageManager}.
 *
 * <p><b>Deferred until {@code PotionConfig}/{@code PotionStage} + the item adapters land
 * (PORT Phase 10/11):</b>
 * <ul>
 *   <li>{@code getIngredients}/{@code getIngredientList} — need the still-unported {@code PotionConfig}
 *       ingredient tables and {@code ItemStack}/{@code ConfigStringUtils};</li>
 *   <li>{@code handlePotionBrewSuccesses} — needs {@code PotionStage} and
 *       {@code ExperienceConfig.getPotionXP(PotionStage)} (still unported);</li>
 *   <li>the whole brewing-stand machinery ({@code Alchemy.brewingStandMap}, {@code AlchemyBrewTask},
 *       {@code AlchemyPotionBrewer}) — needs the Phase 11 scheduler and block/inventory adapters.</li>
 * </ul>
 */
public class AlchemyManager extends SkillManager {
    private static final double LUCKY_MODIFIER = 4.0 / 3.0;

    public AlchemyManager(@NotNull McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.ALCHEMY);
    }

    /**
     * @return the Concoctions tier (rank), which selects the available potion ingredient set
     */
    public int getTier() {
        return RankUtils.getRank(getPlayer(), SubSkillType.ALCHEMY_CONCOCTIONS);
    }

    /**
     * The Catalysis brew-speed multiplier: below the Catalysis unlock level the player brews at the
     * minimum speed; from there it scales linearly up to the maximum at the max-bonus level. The
     * Lucky perk multiplies the whole result by 4/3 (can exceed the max, faithful to legacy).
     *
     * @param isLucky whether the Lucky perk applies (rolled/looked up by the caller)
     * @return the brew-speed multiplier
     */
    public double calculateBrewSpeed(boolean isLucky) {
        final AdvancedConfig advancedConfig = McMMOMod.getAdvancedConfig();
        double catalysisMinSpeed = advancedConfig.getCatalysisMinSpeed();
        double catalysisMaxSpeed = advancedConfig.getCatalysisMaxSpeed();
        int catalysisMaxBonusLevel = advancedConfig.getCatalysisMaxBonusLevel();
        int unlockLevel = RankUtils.getUnlockLevel(SubSkillType.ALCHEMY_CATALYSIS);

        int skillLevel = getSkillLevel();

        if (skillLevel < unlockLevel) {
            return catalysisMinSpeed;
        }

        return Math.min(catalysisMaxSpeed, catalysisMinSpeed
                + (catalysisMaxSpeed - catalysisMinSpeed) * (skillLevel - unlockLevel)
                / (catalysisMaxBonusLevel - unlockLevel)) * (isLucky ? LUCKY_MODIFIER : 1.0);
    }
}
