package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.skills.cooking.CookingManager;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats cooking} — the window onto Cooking's three passives, plus the one number the skill
 * cannot be understood without.
 *
 * <h2>⚠️ Nothing in the test suite would have told anyone this class was missing</h2>
 * {@code SkillStatsRendererTest}'s roster tests are hand-kept {@code List.of(...)} literals rather
 * than loops over {@code PrimarySkillType.values()}, so a new skill reddens nothing there — it simply
 * falls through {@link SkillStatsRenderer#forSkill} to {@link GenericSkillStatsRenderer} and renders a
 * header and a sub-skill list with no effect values at all. Four skills shipped exactly that way
 * before anyone noticed, and their {@code .Stat} locale keys sat written-but-never-read (the
 * {@code SkillLocaleCompletenessTest} loops deliberately exempt {@code .Stat}). Cooking is added to
 * {@code pass2RenderersEmitAStatsSectionAtMaxLevel} in the same commit as this class.
 *
 * <h2>The hourly cook cap is rendered, and that is not decoration</h2>
 * {@code ExploitFix.Cooking.Max_Cooks_Per_Hour} is the skill's <b>only</b> anti-farm gate, and the
 * flat one-hour window was chosen knowing it is bursty: a stack of beef through a smoker array spends
 * a large slice of the budget in minutes, after which the player earns nothing for the rest of the
 * hour. A limit nobody can see is indistinguishable from a broken skill — the lesson two of the ten
 * GitHub issues turned on — so the number is on the screen. It is rendered <b>only when the cap is
 * actually on</b>: a line reading "0 per hour" would be worse than no line at all.
 */
public final class CookingStatsRenderer extends SkillStatsRenderer {

    private CookingManager cooking;

    private boolean canKitchenEfficiency;
    private boolean canMasterChef;
    private boolean canPowerCook;

    private String burnTimeMultiplier;
    private String secondHelpingChance;
    private String powerCookSeconds;

    public CookingStatsRenderer() {
        super(PrimarySkillType.COOKING);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        cooking = mmoPlayer.getCookingManager();

        canKitchenEfficiency = hasUnlocked(SubSkillType.COOKING_KITCHEN_EFFICIENCY);
        canMasterChef = hasUnlocked(SubSkillType.COOKING_MASTER_CHEF);
        canPowerCook = hasUnlocked(SubSkillType.COOKING_POWER_COOK);

        if (cooking == null) {
            return; // Profile not loaded; the header and sub-skill list still render.
        }
        // Every value below is read off the manager rather than re-derived from skillValue, so the
        // screen and the mechanic can never disagree about a rank — the same rule the Smelting and
        // Hunter renderers follow.
        if (canKitchenEfficiency) {
            burnTimeMultiplier = String.valueOf(cooking.getFuelEfficiencyMultiplier());
        }
        if (canMasterChef) {
            secondHelpingChance = ProbabilityUtil.getRNGDisplayValues(
                    mmoPlayer, SubSkillType.COOKING_MASTER_CHEF)[0];
        }
        if (canPowerCook) {
            powerCookSeconds = String.valueOf(cooking.getPowerCookSeconds());
        }
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();
        if (cooking == null) {
            return messages;
        }

        if (canKitchenEfficiency) {
            messages.add(getStatMessage(false, true, SubSkillType.COOKING_KITCHEN_EFFICIENCY,
                    burnTimeMultiplier));
        }
        if (canMasterChef) {
            messages.add(getStatMessage(SubSkillType.COOKING_MASTER_CHEF, secondHelpingChance));
        }
        if (canPowerCook) {
            messages.add(getStatMessage(false, true, SubSkillType.COOKING_POWER_COOK,
                    powerCookSeconds));
        }
        // Built from a raw locale key rather than getStatMessage: the cap is a property of the skill,
        // not of any sub-skill, so it has no SubSkillType to hang a .Stat key off. Same shape the
        // Taming and Hunter renderers use for their bespoke lines.
        if (cooking.isCookRateCapped()) {
            messages.add(LocaleLoader.getString("Ability.Generic.Template",
                    LocaleLoader.getString("Cooking.Stat.HourlyCap"),
                    cooking.getMaxCooksPerHour()));
        }

        return messages;
    }
}
