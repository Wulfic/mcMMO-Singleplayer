package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats parkour} — a thin screen, but no longer an almost-empty one.
 *
 * <h2>What lives here, and why</h2>
 * Parkour is one of the three parents of the child skill <b>Agility</b>, and most movement effects a
 * player associates with it — Fleet Footed, Second Wind, Dodge, Smash — are {@code AGILITY_*}
 * sub-skills gated on that averaged child level, so they render under {@code /mcstats agility}. The
 * two rendered here are gated on the Parkour level <em>directly</em>, because a sub-skill's parent is
 * resolved from its enum name prefix:
 * <ul>
 *   <li>{@link SubSkillType#PARKOUR_SNOW_WALKER} — not falling through powder snow is a
 *       running-and-jumping perk, and a strong swimmer should not be handed it by the average.</li>
 *   <li>{@link SubSkillType#PARKOUR_ROLL} — moved off Agility on 2026-08-03 (GitHub #4). Fall XP is
 *       paid to Parkour, so the odds now sit next to the level that actually moves them.</li>
 * </ul>
 *
 * <h2>Why a binary sub-skill still gets a stats line</h2>
 * Snow Walker has no magnitude — it is on or it is off — so the line pairs its {@code .Stat} label
 * with its {@code .Description} rather than a number. That is more useful than the alternative the
 * one existing binary precedent uses ({@code Fishing}'s Ice Fishing, which pairs the label with
 * itself and renders "Ice Fishing: Ice Fishing"): the player learns what the unlock actually does,
 * which for a sub-skill with no number is the only information there is to give.
 *
 * <h2>Why Roll prints two numbers</h2>
 * Graceful Roll is not a separate sub-skill — it is Roll rolled at double odds while sneaking. The
 * shipped locale has carried a {@code .Stat.Extra} ("Graceful Roll Chance") label since the Bukkit
 * port and <b>nothing had ever rendered it</b>, so the only number a player could see was the one
 * that does <em>not</em> apply when they deliberately crouch to land. That gap fed GitHub #4 being
 * reported as "rolling never procs": the doubled figure is what tells an unlucky roll from a broken
 * one, and it was not on screen anywhere.
 */
public final class ParkourStatsRenderer extends SkillStatsRenderer {

    private String rollChance;
    private String gracefulRollChance;

    public ParkourStatsRenderer() {
        super(PrimarySkillType.PARKOUR);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        // Snow Walker is a binary unlock; there is nothing to pre-compute for it.
        if (!hasUnlocked(SubSkillType.PARKOUR_ROLL)) {
            return;
        }
        rollChance = ProbabilityUtil.getRNGDisplayValues(mmoPlayer, SubSkillType.PARKOUR_ROLL)[0];
        // Deliberately routed through the same helper the mechanic itself calls, rather than
        // formatting `rollChance * 2` here: if the graceful multiplier ever stops being exactly two,
        // this screen must not keep confidently printing the old relationship.
        gracefulRollChance = ProbabilityUtil.getRNGDisplayValues(
                ProbabilityUtil.getGracefulRollProbability(mmoPlayer))[0];
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();

        if (hasUnlocked(SubSkillType.PARKOUR_ROLL)) {
            messages.add(getStatMessage(SubSkillType.PARKOUR_ROLL, rollChance));
            messages.add(getStatMessage(true, false, SubSkillType.PARKOUR_ROLL,
                    gracefulRollChance));
        }

        if (hasUnlocked(SubSkillType.PARKOUR_SNOW_WALKER)) {
            messages.add(getStatMessage(SubSkillType.PARKOUR_SNOW_WALKER,
                    SubSkillType.PARKOUR_SNOW_WALKER.getLocaleDescription()));
        }

        return messages;
    }
}
