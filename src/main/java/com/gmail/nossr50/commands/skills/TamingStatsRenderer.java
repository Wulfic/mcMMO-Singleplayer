package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats taming} — port of legacy {@code TamingCommand}. Shows Environmentally Aware, Fast
 * Food Service, Gore, Holy Hound, Sharpened Claws, Shock Proof, and Thick Fur. The legacy
 * {@code Taming.*} display constants map to the ported {@code AdvancedConfig} getters.
 */
public final class TamingStatsRenderer extends SkillStatsRenderer {

    private boolean canGore;
    private String goreChance;

    public TamingStatsRenderer() {
        super(PrimarySkillType.TAMING);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        canGore = hasUnlocked(SubSkillType.TAMING_GORE);
        if (canGore) {
            goreChance = ProbabilityUtil.getRNGDisplayValues(mmoPlayer, SubSkillType.TAMING_GORE)[0];
        }
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();

        if (hasUnlocked(SubSkillType.TAMING_ENVIRONMENTALLY_AWARE)) {
            messages.add(LocaleLoader.getString("Ability.Generic.Template",
                    LocaleLoader.getString("Taming.Ability.Bonus.0"),
                    LocaleLoader.getString("Taming.Ability.Bonus.1")));
        }
        if (hasUnlocked(SubSkillType.TAMING_FAST_FOOD_SERVICE)) {
            messages.add(LocaleLoader.getString("Ability.Generic.Template",
                    LocaleLoader.getString("Taming.Ability.Bonus.8"),
                    LocaleLoader.getString("Taming.Ability.Bonus.9",
                            percent.format(McMMOMod.getAdvancedConfig().getFastFoodChance() / 100D))));
        }
        if (canGore) {
            messages.add(LocaleLoader.getString("Ability.Generic.Template",
                    LocaleLoader.getString("Taming.Combat.Chance.Gore"), goreChance));
        }
        if (hasUnlocked(SubSkillType.TAMING_HOLY_HOUND)) {
            messages.add(LocaleLoader.getString("Ability.Generic.Template",
                    LocaleLoader.getString("Taming.Ability.Bonus.10"),
                    LocaleLoader.getString("Taming.Ability.Bonus.11")));
        }
        if (hasUnlocked(SubSkillType.TAMING_SHARPENED_CLAWS)) {
            messages.add(LocaleLoader.getString("Ability.Generic.Template",
                    LocaleLoader.getString("Taming.Ability.Bonus.6"),
                    LocaleLoader.getString("Taming.Ability.Bonus.7",
                            McMMOMod.getAdvancedConfig().getSharpenedClawsBonus())));
        }
        if (hasUnlocked(SubSkillType.TAMING_SHOCK_PROOF)) {
            messages.add(LocaleLoader.getString("Ability.Generic.Template",
                    LocaleLoader.getString("Taming.Ability.Bonus.4"),
                    LocaleLoader.getString("Taming.Ability.Bonus.5",
                            McMMOMod.getAdvancedConfig().getShockProofModifier())));
        }
        if (hasUnlocked(SubSkillType.TAMING_THICK_FUR)) {
            messages.add(LocaleLoader.getString("Ability.Generic.Template",
                    LocaleLoader.getString("Taming.Ability.Bonus.2"),
                    LocaleLoader.getString("Taming.Ability.Bonus.3",
                            McMMOMod.getAdvancedConfig().getThickFurModifier())));
        }

        return messages;
    }
}
