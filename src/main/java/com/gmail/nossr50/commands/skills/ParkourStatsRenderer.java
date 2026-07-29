package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats parkour} — the thinnest stats screen in the mod, and deliberately so.
 *
 * <h2>Why Parkour looks almost empty, and why that is correct</h2>
 * Parkour is one of the three parents of the child skill <b>Agility</b>, and nearly every movement
 * effect a player associates with it — Fleet Footed, Second Wind, Dodge, Roll, Smash — is an
 * {@code AGILITY_*} sub-skill gated on that averaged child level, so it renders under
 * {@code /mcstats agility} rather than here. Parkour owns exactly one sub-skill of its own,
 * {@link SubSkillType#PARKOUR_SNOW_WALKER}, because a sub-skill's parent is resolved from its enum
 * name prefix: {@code PARKOUR_SNOW_WALKER} reads the Parkour level directly instead of the mean of
 * Parkour, Swimming and Flying. Not falling through powder snow is a running-and-jumping perk, and a
 * strong swimmer should not be handed it by the average.
 *
 * <h2>Why a binary sub-skill still gets a stats line</h2>
 * Snow Walker has no magnitude — it is on or it is off — so the line pairs its {@code .Stat} label
 * with its {@code .Description} rather than a number. That is more useful than the alternative the
 * one existing binary precedent uses ({@code Fishing}'s Ice Fishing, which pairs the label with
 * itself and renders "Ice Fishing: Ice Fishing"): the player learns what the unlock actually does,
 * which for a sub-skill with no number is the only information there is to give.
 */
public final class ParkourStatsRenderer extends SkillStatsRenderer {

    public ParkourStatsRenderer() {
        super(PrimarySkillType.PARKOUR);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        // Snow Walker is a binary unlock; there is nothing to pre-compute.
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();

        if (hasUnlocked(SubSkillType.PARKOUR_SNOW_WALKER)) {
            messages.add(getStatMessage(SubSkillType.PARKOUR_SNOW_WALKER,
                    SubSkillType.PARKOUR_SNOW_WALKER.getLocaleDescription()));
        }

        return messages;
    }
}
