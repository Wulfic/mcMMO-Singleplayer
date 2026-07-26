package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.skills.agility.AgilityManager;
import com.gmail.nossr50.skills.agility.Medium;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats agility} — the widest stats screen in the mod, and the reason it needs structure.
 *
 * <p>Agility carries ten sub-skills across four movement domains. Rendered as one flat list they are
 * unreadable, and it is impossible to tell at a glance which apply to what the player is currently
 * doing — so the effect lines are grouped under <b>Falling / Land / Water / Air</b> headers. Fleet
 * Footed and Second Wind each appear under every domain whose rank the player has reached, rather
 * than once with three numbers crammed into a single line.
 *
 * <p>A section with nothing unlocked is omitted entirely, so a new player still sees the short
 * Falling-only screen the skill shipped with rather than three empty headings.
 */
public final class AgilityStatsRenderer extends SkillStatsRenderer {

    private static final String SECTION_FALLING = "&6Falling";
    private static final String SECTION_LAND = "&6Land";
    private static final String SECTION_WATER = "&6Water";
    private static final String SECTION_AIR = "&6Air";

    private AgilityManager agility;

    private boolean canDodge;
    private boolean canRoll;
    private String dodgeChance;
    private String rollChance;

    public AgilityStatsRenderer() {
        super(PrimarySkillType.AGILITY);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        agility = mmoPlayer.getAgilityManager();

        canDodge = hasUnlocked(SubSkillType.AGILITY_DODGE);
        canRoll = hasUnlocked(SubSkillType.AGILITY_ROLL);

        if (canDodge) {
            dodgeChance = ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                    SubSkillType.AGILITY_DODGE)[0];
        }
        if (canRoll) {
            rollChance = ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                    SubSkillType.AGILITY_ROLL)[0];
        }
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();

        // --- Falling: the roster Agility shipped with as Acrobatics ---
        final List<String> falling = new ArrayList<>();
        if (canDodge) {
            falling.add(getStatMessage(SubSkillType.AGILITY_DODGE, dodgeChance));
        }
        if (canRoll) {
            falling.add(getStatMessage(SubSkillType.AGILITY_ROLL, rollChance));
        }
        addSection(messages, SECTION_FALLING, falling);

        // --- Land ---
        final List<String> land = new ArrayList<>();
        addFleetFooted(land, Medium.LAND);
        if (hasUnlocked(SubSkillType.AGILITY_ATHLETE) && agility != null) {
            // Shown as hunger *saved*, which is what the player feels; the manager returns the
            // multiplier that remains.
            land.add(getStatMessage(SubSkillType.AGILITY_ATHLETE,
                    percent.format(1.0 - agility.getAthleteExhaustionMultiplier())));
        }
        if (hasUnlocked(SubSkillType.AGILITY_SMASH)) {
            land.add(getStatMessage(SubSkillType.AGILITY_SMASH,
                    ProbabilityUtil.getRNGDisplayValues(mmoPlayer, SubSkillType.AGILITY_SMASH)[0]));
        }
        addSecondWind(land, Medium.LAND, skillValue);
        addSection(messages, SECTION_LAND, land);

        // --- Water ---
        final List<String> water = new ArrayList<>();
        addFleetFooted(water, Medium.WATER);
        if (hasUnlocked(SubSkillType.AGILITY_LEAD_LUNGS) && agility != null) {
            // Vanilla spends exactly one air per tick, so the per-tick top-up reads naturally as the
            // fraction of your breath that is being given back.
            water.add(getStatMessage(SubSkillType.AGILITY_LEAD_LUNGS,
                    percent.format(agility.getLeadLungsAirTopUpPerTick())));
        }
        if (hasUnlocked(SubSkillType.AGILITY_LAKE_RAIDER)) {
            water.add(getStatMessage(SubSkillType.AGILITY_LAKE_RAIDER,
                    ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                            SubSkillType.AGILITY_LAKE_RAIDER)[0]));
        }
        addSecondWind(water, Medium.WATER, skillValue);
        addSection(messages, SECTION_WATER, water);

        // --- Air ---
        final List<String> air = new ArrayList<>();
        addFleetFooted(air, Medium.AIR);
        if (hasUnlocked(SubSkillType.AGILITY_GLIDE) && agility != null) {
            air.add(getStatMessage(SubSkillType.AGILITY_GLIDE,
                    percent.format(agility.getGlideDescentReduction())));
        }
        if (hasUnlocked(SubSkillType.AGILITY_SOLAR_WINGS) && agility != null) {
            air.add(getStatMessage(SubSkillType.AGILITY_SOLAR_WINGS,
                    agility.getSolarWingsRepairAmount(false) + " per "
                            + Math.max(1, agility.getSolarWingsIntervalTicks() / 20) + "s"));
        }
        addSecondWind(air, Medium.AIR, skillValue);
        addSection(messages, SECTION_AIR, air);

        return messages;
    }

    /** Append {@code lines} under {@code header}, or nothing at all when the section is empty. */
    private static void addSection(List<String> messages, String header, List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        messages.add(header);
        messages.addAll(lines);
    }

    /** Fleet Footed's line for one medium, shown only once that medium's rank is unlocked. */
    private void addFleetFooted(List<String> lines, Medium medium) {
        if (agility == null || !agility.canFleetFoot(medium)) {
            return;
        }
        lines.add(getStatMessage(SubSkillType.AGILITY_FLEET_FOOTED,
                percent.format(agility.getFleetFootedBonus(medium))));
    }

    /** Second Wind's line for one medium, shown only once that body's rank is unlocked. */
    private void addSecondWind(List<String> lines, Medium medium, float skillValue) {
        if (agility == null || !agility.canSecondWind(medium)) {
            return;
        }
        lines.add(getStatMessage(SubSkillType.AGILITY_SECOND_WIND, calculateLength(skillValue)));
    }
}
