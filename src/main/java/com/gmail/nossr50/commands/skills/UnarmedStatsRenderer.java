package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats unarmed} — port of legacy {@code UnarmedCommand}. Shows Arrow Deflect, Berserk
 * duration and Steel Arm (Iron Arm) bonus damage.
 *
 * <p>Legacy's Disarm and Iron Grip lines are deliberately absent: both mechanics require
 * {@code target instanceof Player} and can never fire in singleplayer, so the sub-skills were
 * removed outright rather than rendered as chances that never apply (see {@code SubSkillType}).
 */
public final class UnarmedStatsRenderer extends SkillStatsRenderer {

    private boolean canDeflect;
    private boolean canBerserk;
    private boolean canIronArm;

    private String deflectChance;
    private String berserkLength;
    private double ironArmBonus;

    public UnarmedStatsRenderer() {
        super(PrimarySkillType.UNARMED);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        canDeflect = hasUnlocked(SubSkillType.UNARMED_ARROW_DEFLECT);
        canBerserk = hasUnlocked(SubSkillType.UNARMED_BERSERK);
        canIronArm = hasUnlocked(SubSkillType.UNARMED_STEEL_ARM_STYLE);

        if (canDeflect) {
            deflectChance = ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                    SubSkillType.UNARMED_ARROW_DEFLECT)[0];
        }
        if (canBerserk) {
            berserkLength = calculateLength(skillValue);
        }
        if (canIronArm) {
            ironArmBonus = mmoPlayer.getUnarmedManager().getSteelArmStyleDamage();
        }
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();

        if (canDeflect) {
            messages.add(getStatMessage(SubSkillType.UNARMED_ARROW_DEFLECT, deflectChance));
        }
        if (canBerserk) {
            messages.add(getStatMessage(SubSkillType.UNARMED_BERSERK, berserkLength));
        }
        if (canIronArm) {
            messages.add(LocaleLoader.getString("Ability.Generic.Template",
                    LocaleLoader.getString("Unarmed.Ability.Bonus.0"),
                    LocaleLoader.getString("Unarmed.Ability.Bonus.1", ironArmBonus)));
        }

        return messages;
    }
}
