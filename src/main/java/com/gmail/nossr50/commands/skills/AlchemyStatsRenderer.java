package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.skills.alchemy.AlchemyManager;
import com.gmail.nossr50.util.skills.RankUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats alchemy} — port of legacy {@code AlchemyCommand}. Shows Catalysis brew speed and
 * the Concoctions tier.
 *
 * <p>The unlocked-ingredient list was dropped by GitHub #17.2; the in-game guide still carries it.
 */
public final class AlchemyStatsRenderer extends SkillStatsRenderer {

    private boolean canCatalysis;
    private boolean canConcoctions;
    private String brewSpeed;
    private int tier;

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
        }
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();

        if (canCatalysis) {
            messages.add(getStatMessage(SubSkillType.ALCHEMY_CATALYSIS, brewSpeed));
        }
        if (canConcoctions) {
            // GitHub #17.2: the unlocked-ingredient dump is no longer shown. It was a long,
            // comma-separated wall of item names on a screen that is otherwise one line per
            // sub-skill. The rank line below still says which tier you are on, and the in-game guide
            // (Guides.Alchemy.Section.3-6) still lists every tier's ingredients for anyone who wants
            // them -- so the information is moved, not lost.
            messages.add(getStatMessage(false, true, SubSkillType.ALCHEMY_CONCOCTIONS,
                    String.valueOf(tier),
                    String.valueOf(RankUtils.getHighestRank(SubSkillType.ALCHEMY_CONCOCTIONS))));
        }

        return messages;
    }
}
