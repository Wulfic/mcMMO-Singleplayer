package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.skills.alchemy.AlchemyManager;
import com.gmail.nossr50.util.skills.RankUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats alchemy} — port of legacy {@code AlchemyCommand}. Shows Catalysis brew speed and
 * Concoctions (tier + unlocked ingredient list).
 */
public final class AlchemyStatsRenderer extends SkillStatsRenderer {

    private boolean canCatalysis;
    private boolean canConcoctions;
    private String brewSpeed;
    private int tier;
    private int ingredientCount;
    private String ingredientList;

    public AlchemyStatsRenderer() {
        super(PrimarySkillType.ALCHEMY);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        final AlchemyManager alchemyManager = mmoPlayer.getAlchemyManager();
        canCatalysis = hasUnlocked(SubSkillType.ALCHEMY_CATALYSIS);
        canConcoctions = hasUnlocked(SubSkillType.ALCHEMY_CONCOCTIONS);

        if (canCatalysis) {
            brewSpeed = decimal.format(alchemyManager.calculateBrewSpeed(false)) + "x";
        }
        if (canConcoctions) {
            tier = alchemyManager.getTier();
            ingredientCount = alchemyManager.getIngredients().size();
            ingredientList = alchemyManager.getIngredientList();
        }
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();

        if (canCatalysis) {
            messages.add(getStatMessage(SubSkillType.ALCHEMY_CATALYSIS, brewSpeed));
        }
        if (canConcoctions) {
            messages.add(getStatMessage(false, true, SubSkillType.ALCHEMY_CONCOCTIONS,
                    String.valueOf(tier),
                    String.valueOf(RankUtils.getHighestRank(SubSkillType.ALCHEMY_CONCOCTIONS))));
            messages.add(getStatMessage(true, true, SubSkillType.ALCHEMY_CONCOCTIONS,
                    String.valueOf(ingredientCount), ingredientList));
        }

        return messages;
    }
}
