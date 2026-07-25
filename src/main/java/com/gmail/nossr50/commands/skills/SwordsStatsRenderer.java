package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats swords} — port of legacy {@code SwordsCommand}. Shows Counter Attack chance,
 * Rupture (apply chance, duration, tick damage), Serrated Strikes duration, and Stab damage.
 *
 * <p>Limit Break is intentionally omitted — it was dropped from the port for all weapons.
 */
public final class SwordsStatsRenderer extends SkillStatsRenderer {

    private boolean canCounter;
    private boolean canRupture;
    private boolean canSerratedStrike;

    private String counterChance;
    private String serratedStrikesLength;
    private String ruptureChanceToApply;
    private String ruptureLengthPlayers;
    private String ruptureLengthMobs;
    private String ruptureTickDamagePlayers;
    private String ruptureTickDamageMobs;

    public SwordsStatsRenderer() {
        super(PrimarySkillType.SWORDS);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        canCounter = hasUnlocked(SubSkillType.SWORDS_COUNTER_ATTACK);
        canRupture = hasUnlocked(SubSkillType.SWORDS_RUPTURE);
        canSerratedStrike = hasUnlocked(SubSkillType.SWORDS_SERRATED_STRIKES);

        if (canCounter) {
            counterChance = ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                    SubSkillType.SWORDS_COUNTER_ATTACK)[0];
        }
        if (canRupture) {
            final int ruptureRank = RankUtils.getRank(mmoPlayer, SubSkillType.SWORDS_RUPTURE);
            ruptureLengthPlayers = String.valueOf(
                    McMMOMod.getAdvancedConfig().getRuptureDurationSeconds(true));
            ruptureLengthMobs = String.valueOf(
                    McMMOMod.getAdvancedConfig().getRuptureDurationSeconds(false));
            ruptureTickDamagePlayers = String.valueOf(
                    McMMOMod.getAdvancedConfig().getRuptureTickDamage(true, ruptureRank));
            ruptureTickDamageMobs = String.valueOf(
                    McMMOMod.getAdvancedConfig().getRuptureTickDamage(false, ruptureRank));
            ruptureChanceToApply =
                    McMMOMod.getAdvancedConfig().getRuptureChanceToApplyOnHit(ruptureRank) + "%";
        }
        if (canSerratedStrike) {
            serratedStrikesLength = calculateLength(skillValue);
        }
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();

        if (canCounter) {
            messages.add(getStatMessage(SubSkillType.SWORDS_COUNTER_ATTACK, counterChance));
        }
        if (canRupture) {
            messages.add(getStatMessage(SubSkillType.SWORDS_RUPTURE, ruptureChanceToApply));
            messages.add(getStatMessage(true, true, SubSkillType.SWORDS_RUPTURE,
                    ruptureLengthPlayers, ruptureLengthMobs));
            messages.add(LocaleLoader.getString("Swords.SubSkill.Rupture.Stat.TickDamage",
                    ruptureTickDamagePlayers, ruptureTickDamageMobs));
            messages.add(LocaleLoader.getString("Swords.Combat.Rupture.Note.Update.One"));
        }
        if (canSerratedStrike) {
            messages.add(getStatMessage(SubSkillType.SWORDS_SERRATED_STRIKES, serratedStrikesLength));
        }
        if (hasUnlocked(SubSkillType.SWORDS_STAB)) {
            messages.add(getStatMessage(SubSkillType.SWORDS_STAB,
                    String.valueOf(mmoPlayer.getSwordsManager().getStabDamage())));
        }

        return messages;
    }
}
