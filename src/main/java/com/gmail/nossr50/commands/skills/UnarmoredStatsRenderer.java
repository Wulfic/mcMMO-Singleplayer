package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.skills.unarmored.UnarmoredManager;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats unarmored} — the armour points bare skin is worth, and what it stings back for.
 *
 * <p><b>Both values are asked for with {@code unarmored = true}</b>, and that is the whole subtlety of
 * this screen. Every {@link UnarmoredManager} getter takes "is every armour slot empty right now?" and
 * answers {@code 0} when it is not, because the skill is a trade rather than a bonus. A stats screen
 * has no tick to read, and answering "0 armour" to a player who happened to open the menu in a
 * chestplate would report the skill as broken. So these lines state what the skill is <em>worth</em> —
 * the value the player gets by taking the armour off — which is the question the screen is being
 * asked.
 *
 * <p>Iron Skin's tier is shown alongside its armour points because the tier is what the sub-skill's
 * rank means (1-4 = leather, gold, iron, diamond) and it is the number a player levelling toward the
 * next breakpoint actually wants.
 */
public final class UnarmoredStatsRenderer extends SkillStatsRenderer {

    /** Armour is only ever granted to a player wearing none — see the class javadoc. */
    private static final boolean AS_IF_UNARMORED = true;

    private UnarmoredManager unarmored;

    private boolean canIronSkin;
    private boolean canThornySkin;

    public UnarmoredStatsRenderer() {
        super(PrimarySkillType.UNARMORED);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        unarmored = mmoPlayer.getUnarmoredManager();

        canIronSkin = hasUnlocked(SubSkillType.UNARMORED_IRON_SKIN);
        canThornySkin = hasUnlocked(SubSkillType.UNARMORED_THORNY_SKIN);
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();
        if (unarmored == null) {
            return messages; // Profile not loaded; the header and sub-skill list still render.
        }

        if (canIronSkin) {
            messages.add(getStatMessage(SubSkillType.UNARMORED_IRON_SKIN,
                    decimal.format(unarmored.getSkinArmorPoints(AS_IF_UNARMORED))
                            + " (tier " + unarmored.getIronSkinTier() + ")"));
        }
        if (canThornySkin) {
            messages.add(getStatMessage(SubSkillType.UNARMORED_THORNY_SKIN,
                    decimal.format(unarmored.getThornsDamage(AS_IF_UNARMORED))));
        }

        return messages;
    }
}
