package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.skills.axes.Axes;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats axes} — port of legacy {@code AxesCommand}. Shows Armor Impact, Axe Mastery bonus
 * damage, Critical Strikes chance, Greater Impact bonus damage, and Skull Splitter duration.
 *
 * <p>Limit Break is intentionally omitted — it was dropped from the port for all weapons.
 */
public final class AxesStatsRenderer extends SkillStatsRenderer {

    private boolean canImpact;
    private boolean canAxeMastery;
    private boolean canCritical;
    private boolean canSkullSplitter;

    private double impactDamage;
    private double axeMasteryDamage;
    private String critChance;
    private String skullSplitterLength;

    public AxesStatsRenderer() {
        super(PrimarySkillType.AXES);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        canImpact = hasUnlocked(SubSkillType.AXES_ARMOR_IMPACT);
        canAxeMastery = hasUnlocked(SubSkillType.AXES_AXE_MASTERY);
        canCritical = hasUnlocked(SubSkillType.AXES_CRITICAL_STRIKES);
        canSkullSplitter = hasUnlocked(SubSkillType.AXES_SKULL_SPLITTER);

        if (canImpact) {
            impactDamage = mmoPlayer.getAxesManager().getImpactDurabilityDamage();
        }
        if (canAxeMastery) {
            axeMasteryDamage = Axes.getAxeMasteryBonusDamage(mmoPlayer.getPlayer());
        }
        if (canCritical) {
            critChance = ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                    SubSkillType.AXES_CRITICAL_STRIKES)[0];
        }
        if (canSkullSplitter) {
            skullSplitterLength = calculateLength(skillValue);
        }
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();

        if (canImpact) {
            messages.add(LocaleLoader.getString("Ability.Generic.Template",
                    LocaleLoader.getString("Axes.Ability.Bonus.2"),
                    LocaleLoader.getString("Axes.Ability.Bonus.3", impactDamage)));
        }
        if (canAxeMastery) {
            messages.add(LocaleLoader.getString("Ability.Generic.Template",
                    LocaleLoader.getString("Axes.Ability.Bonus.0"),
                    LocaleLoader.getString("Axes.Ability.Bonus.1", axeMasteryDamage)));
        }
        if (canCritical) {
            messages.add(getStatMessage(SubSkillType.AXES_CRITICAL_STRIKES, critChance));
        }
        if (hasUnlocked(SubSkillType.AXES_GREATER_IMPACT)) {
            messages.add(LocaleLoader.getString("Ability.Generic.Template",
                    LocaleLoader.getString("Axes.Ability.Bonus.4"),
                    LocaleLoader.getString("Axes.Ability.Bonus.5",
                            McMMOMod.getAdvancedConfig().getGreaterImpactBonusDamage())));
        }
        if (canSkullSplitter) {
            messages.add(getStatMessage(SubSkillType.AXES_SKULL_SPLITTER, skullSplitterLength));
        }

        return messages;
    }
}
