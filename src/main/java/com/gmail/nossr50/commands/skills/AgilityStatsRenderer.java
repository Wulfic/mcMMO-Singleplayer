package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.skills.agility.AgilityManager;
import com.gmail.nossr50.skills.agility.Medium;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats agility} — the cross-medium screen.
 *
 * <p>Agility is a child skill whose level is the mean of Parkour, Swimming and Flying, and after the
 * 2026-08-10 re-parenting it carries exactly the two sub-skills for which that mean is an honest
 * gate: <b>Fleet Footed</b> and <b>Second Wind</b>. Both work in all three mediums and both carry
 * one rank per medium, which is precisely why neither could be moved to a parent — no single
 * parent's level could gate all three of their ranks.
 *
 * <p>The lines are grouped under <b>Land / Water / Air</b> headers, because each sub-skill appears
 * once per medium whose rank the player has reached rather than once with three numbers crammed into
 * a line. A medium with nothing unlocked is omitted entirely, so a player who has only reached the
 * land ranks sees a two-line screen rather than three headings, two of them empty.
 *
 * <p><b>What is no longer here.</b> Seven single-medium sub-skills moved to the skill that earns
 * them: Dodge, Athlete and Smash to {@link ParkourStatsRenderer} (joining Roll and Snow Walker),
 * Lead Lungs and Lake Raider to {@link SwimmingStatsRenderer}, Glide and Solar Wings to
 * {@link FlyingStatsRenderer}. Each is gated on that parent's own level now, so its number belongs
 * on the screen that shows that level — the same argument GitHub #4 made for Roll.
 */
public final class AgilityStatsRenderer extends SkillStatsRenderer {

    private static final String SECTION_LAND = "&6Land";
    private static final String SECTION_WATER = "&6Water";
    private static final String SECTION_AIR = "&6Air";

    private AgilityManager agility;

    public AgilityStatsRenderer() {
        super(PrimarySkillType.AGILITY);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        agility = mmoPlayer.getAgilityManager();
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();
        for (Medium medium : Medium.values()) {
            addSection(messages, sectionHeader(medium), mediumLines(medium, skillValue));
        }
        return messages;
    }

    /**
     * Both of Agility's sub-skills, for one medium, each shown only once that medium's rank is
     * reached.
     *
     * <p>Driven off {@link Medium#values()} rather than three hand-written blocks: with the roster
     * down to two sub-skills that both rank per medium, a per-medium loop is the shape of the data,
     * and a medium added later cannot be rendered on two headings out of three and missed on the
     * one nobody remembered to copy.
     */
    private List<String> mediumLines(Medium medium, float skillValue) {
        final List<String> lines = new ArrayList<>();
        if (agility == null) {
            return lines;
        }
        if (agility.canFleetFoot(medium)) {
            lines.add(getStatMessage(SubSkillType.AGILITY_FLEET_FOOTED,
                    percent.format(agility.getFleetFootedBonus(medium))));
        }
        if (agility.canSecondWind(medium)) {
            lines.add(getStatMessage(SubSkillType.AGILITY_SECOND_WIND, calculateLength(skillValue)));
        }
        return lines;
    }

    private static String sectionHeader(Medium medium) {
        return switch (medium) {
            case LAND -> SECTION_LAND;
            case WATER -> SECTION_WATER;
            case AIR -> SECTION_AIR;
        };
    }

    /** Append {@code lines} under {@code header}, or nothing at all when the section is empty. */
    private static void addSection(List<String> messages, String header, List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        messages.add(header);
        messages.addAll(lines);
    }
}
