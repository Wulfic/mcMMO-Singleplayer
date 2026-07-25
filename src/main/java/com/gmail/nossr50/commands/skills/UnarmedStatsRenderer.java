package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats unarmed} — port of legacy {@code UnarmedCommand}. Shows Arrow Deflect, Berserk
 * duration, Disarm chance, Steel Arm (Iron Arm) bonus damage, and Iron Grip chance.
 *
 * <p>Limit Break is intentionally omitted — it was dropped from the port for all weapons.
 */
public final class UnarmedStatsRenderer extends SkillStatsRenderer {

    private boolean canDeflect;
    private boolean canBerserk;
    private boolean canDisarm;
    private boolean canIronArm;
    private boolean canIronGrip;

    private String deflectChance;
    private String berserkLength;
    private String disarmChance;
    private double ironArmBonus;
    private String ironGripChance;

    public UnarmedStatsRenderer() {
        super(PrimarySkillType.UNARMED);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        canDeflect = hasUnlocked(SubSkillType.UNARMED_ARROW_DEFLECT);
        canBerserk = hasUnlocked(SubSkillType.UNARMED_BERSERK);
        canDisarm = hasUnlocked(SubSkillType.UNARMED_DISARM);
        canIronArm = hasUnlocked(SubSkillType.UNARMED_STEEL_ARM_STYLE);
        canIronGrip = hasUnlocked(SubSkillType.UNARMED_IRON_GRIP);

        if (canDeflect) {
            deflectChance = ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                    SubSkillType.UNARMED_ARROW_DEFLECT)[0];
        }
        if (canBerserk) {
            berserkLength = calculateLength(skillValue);
        }
        if (canDisarm) {
            disarmChance = ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                    SubSkillType.UNARMED_DISARM)[0];
        }
        if (canIronArm) {
            ironArmBonus = mmoPlayer.getUnarmedManager().getSteelArmStyleDamage();
        }
        if (canIronGrip) {
            ironGripChance = ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                    SubSkillType.UNARMED_IRON_GRIP)[0];
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
        if (canDisarm) {
            messages.add(getStatMessage(SubSkillType.UNARMED_DISARM, disarmChance));
        }
        if (canIronArm) {
            messages.add(LocaleLoader.getString("Ability.Generic.Template",
                    LocaleLoader.getString("Unarmed.Ability.Bonus.0"),
                    LocaleLoader.getString("Unarmed.Ability.Bonus.1", ironArmBonus)));
        }
        if (canIronGrip) {
            messages.add(getStatMessage(SubSkillType.UNARMED_IRON_GRIP, ironGripChance));
        }

        return messages;
    }
}
