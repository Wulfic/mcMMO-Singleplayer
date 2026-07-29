package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.skills.husbandry.HusbandryManager;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats husbandry} — the livestock-lifecycle skill's effect values.
 *
 * <p>Lines are ordered by the <b>verb</b> they affect rather than alphabetically, so the screen reads
 * in the order a player actually works an animal: breed it, raise it, then harvest it. That is the
 * same spine the skill itself is organised around (see {@link HusbandryManager}'s class javadoc), and
 * it keeps the two breed-family sub-skills — which multiply with each other — adjacent.
 *
 * <p>Two of the four sub-skills carry <b>two independent numbers</b>, rendered as a {@code .Stat} plus
 * a {@code .Stat.Extra} line rather than crammed into one:
 * <ul>
 *   <li><b>Multi-Breed</b> — how far it reaches, and how many animals one breeding item may set in
 *       love. The second is the one that bounds XP per click, so it is the more important of the two
 *       to surface (see {@link HusbandryManager#getMultiBreedMaxAdditionalAnimals()}).</li>
 *   <li><b>Accelerated Growth</b> — the passive childhood-shortening, and the active double-feed
 *       roll. <b>Bountiful Harvest</b> is the same shape: a bonus-drop chance and a durability save.</li>
 * </ul>
 *
 * <p>The two chance-based numbers come from {@code ProbabilityUtil} (which keys its odds off the
 * {@link SubSkillType}); the other four are read from the manager, which owns the level scaling and
 * the hard clamps. Nothing is recomputed here — a stats screen that derives its own numbers is a
 * second implementation to keep in step with the first.
 */
public final class HusbandryStatsRenderer extends SkillStatsRenderer {

    private HusbandryManager husbandry;

    private boolean canMultiBreed;
    private boolean canTwins;
    private boolean canAcceleratedGrowth;
    private boolean canBountifulHarvest;
    private boolean canBeekeeper;

    public HusbandryStatsRenderer() {
        super(PrimarySkillType.HUSBANDRY);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        husbandry = mmoPlayer.getHusbandryManager();

        canMultiBreed = hasUnlocked(SubSkillType.HUSBANDRY_MULTI_BREED);
        canTwins = hasUnlocked(SubSkillType.HUSBANDRY_TWINS);
        canAcceleratedGrowth = hasUnlocked(SubSkillType.HUSBANDRY_ACCELERATED_GROWTH);
        canBountifulHarvest = hasUnlocked(SubSkillType.HUSBANDRY_BOUNTIFUL_HARVEST);
        canBeekeeper = hasUnlocked(SubSkillType.HUSBANDRY_BEEKEEPER);
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();
        if (husbandry == null) {
            return messages; // Profile not loaded; the header and sub-skill list still render.
        }

        // --- Breeding --------------------------------------------------------------------------
        if (canMultiBreed) {
            messages.add(getStatMessage(SubSkillType.HUSBANDRY_MULTI_BREED,
                    decimal.format(husbandry.getMultiBreedRadius()) + " blocks"));
            messages.add(getStatMessage(true, false, SubSkillType.HUSBANDRY_MULTI_BREED,
                    String.valueOf(husbandry.getMultiBreedMaxAdditionalAnimals())));
        }
        if (canTwins) {
            messages.add(getStatMessage(SubSkillType.HUSBANDRY_TWINS,
                    ProbabilityUtil.getRNGDisplayValues(mmoPlayer, SubSkillType.HUSBANDRY_TWINS)[0]));
        }

        // --- Raising ---------------------------------------------------------------------------
        if (canAcceleratedGrowth) {
            // A fraction of childhood removed, so it formats directly as a percentage.
            messages.add(getStatMessage(SubSkillType.HUSBANDRY_ACCELERATED_GROWTH,
                    percent.format(husbandry.getGrowthAcceleration())));
            messages.add(getStatMessage(true, false, SubSkillType.HUSBANDRY_ACCELERATED_GROWTH,
                    ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                            SubSkillType.HUSBANDRY_ACCELERATED_GROWTH)[0]));
        }

        // --- Harvesting ------------------------------------------------------------------------
        if (canBountifulHarvest) {
            messages.add(getStatMessage(SubSkillType.HUSBANDRY_BOUNTIFUL_HARVEST,
                    ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                            SubSkillType.HUSBANDRY_BOUNTIFUL_HARVEST)[0]));
            // The manager reports this one in percent (0-100), unlike the growth fraction above.
            messages.add(getStatMessage(true, false, SubSkillType.HUSBANDRY_BOUNTIFUL_HARVEST,
                    percent.format(husbandry.getHarvestDurabilitySaveChance() / 100.0D)));
        }
        // Only Beekeeper's bonus-yield half gets a number. Its headline half — bees never anger — is
        // binary at the unlock level, so there is no value to render: a percentage would imply a roll
        // that never happens, and a hard-coded "yes" would be a line that can only ever say one thing.
        // The sub-skill's own description carries it, which is the same call ParkourStatsRenderer
        // makes for Snow Walker.
        if (canBeekeeper) {
            messages.add(getStatMessage(SubSkillType.HUSBANDRY_BEEKEEPER,
                    ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                            SubSkillType.HUSBANDRY_BEEKEEPER)[0]));
        }

        return messages;
    }
}
