package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.skills.movement.MovementManager;
import com.gmail.nossr50.skills.movement.Medium;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats swimming} — the water screen.
 *
 * <p>Swimming is one of the three parents of the child skill <b>Agility</b>. Until 2026-08-10 it
 * owned no sub-skills at all and fell through to {@link GenericSkillStatsRenderer}, which showed a
 * level and nothing else; both sub-skills below were {@code AGILITY_*} constants gated on the mean of
 * Parkour, Swimming and Flying. Holding your breath and turning up treasure in silt are earned by
 * swimming, so they are gated on this level now and their numbers belong on this screen.
 *
 * <p><b>Fleet Footed and Second Wind moved here on 2026-08-17</b>, when Agility was retired. Each was
 * one sub-skill carrying one rank per medium, gated on the mean of the three movement skills; each is
 * now a single-rank sub-skill of the skill that earns it, so this screen shows the <em>water</em>
 * body of both, unlocked by Swimming alone.
 */
public final class SwimmingStatsRenderer extends SkillStatsRenderer {

    private MovementManager agility;

    public SwimmingStatsRenderer() {
        super(PrimarySkillType.SWIMMING);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        // Lead Lungs' magnitude comes off the manager (it reads the live config and the level
        // ladder); Lake Raider is an RNG line resolved at render time like every other one.
        agility = mmoPlayer.getMovementManager();
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();

        if (hasUnlocked(SubSkillType.SWIMMING_LEAD_LUNGS) && agility != null) {
            // Vanilla spends exactly one air per tick, so the per-tick top-up reads naturally as the
            // fraction of your breath that is being given back.
            messages.add(getStatMessage(SubSkillType.SWIMMING_LEAD_LUNGS,
                    percent.format(agility.getLeadLungsAirTopUpPerTick())));
        }

        if (hasUnlocked(SubSkillType.SWIMMING_LAKE_RAIDER)) {
            messages.add(getStatMessage(SubSkillType.SWIMMING_LAKE_RAIDER,
                    ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                            SubSkillType.SWIMMING_LAKE_RAIDER)[0]));
        }

        // Fleet Footed and Second Wind arrived here on 2026-08-17 with the retirement of Agility.
        // Both used to be one sub-skill carrying one rank per medium, gated on the MEAN of the three
        // movement skills, so a Swimming specialist could not reach the rank for the medium they had
        // actually trained. Each is now a single-rank sub-skill of this skill, and each line below
        // shows Swimming's OWN body of it -- not a shared number repeated on three screens.
        if (agility != null && agility.canFleetFoot(Medium.WATER)) {
            messages.add(getStatMessage(SubSkillType.SWIMMING_FLEET_FOOTED,
                    percent.format(agility.getFleetFootedBonus(Medium.WATER))));
        }
        // ⚠️ The length is asked for SECOND_WIND by name. getSuperAbility(skill) is one-to-one and
        // answers null for two of the three movement skills, which would NPE rather than misprint.
        if (agility != null && agility.canSecondWind(Medium.WATER)) {
            messages.add(getStatMessage(SubSkillType.SWIMMING_SECOND_WIND,
                    calculateLength(skillValue, SuperAbilityType.SECOND_WIND)));
        }

        return messages;
    }
}
