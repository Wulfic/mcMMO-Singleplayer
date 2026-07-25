package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.util.skills.RankUtils;
import com.gmail.nossr50.util.skills.SkillTools;
import com.gmail.nossr50.util.text.StringUtils;
import com.gmail.nossr50.util.text.TextUtils;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

/**
 * Renders the full-detail {@code /mcstats <skill>} screen for one skill — the singleplayer port of
 * mcMMO's per-skill {@code SkillCommand} tree ({@code MiningCommand}, {@code SwordsCommand}, …).
 *
 * <p>Legacy had one {@code CommandExecutor} subclass per skill; this port keeps that shape (one
 * {@link SkillStatsRenderer} subclass per skill) but strips everything that was multiplayer- or
 * Adventure-specific: the clickable/hover sub-skill guide, the scoreboard toggle, the URL footer, and
 * the {@code lucky}/{@code endurance} perk suffixes (singleplayer has no perks — {@link
 * com.gmail.nossr50.util.skills.PerksUtils#handleActivationPerks} takes no player, so those branches
 * are always {@code false}). What remains is the informative core: the header (skill name, XP-gain
 * method, level/XP), the sub-skill list with ranks, and the bespoke per-skill effect stats.
 *
 * <p>Rendering is to a {@code Consumer<Text>} sink rather than straight to a player, so the output can
 * be captured and asserted in unit tests. Effect lines are built as legacy §-coded strings (a near
 * verbatim port of each skill's {@code statsDisplay}) and converted to vanilla {@link Text} via
 * {@link TextUtils#toText} on the way out.
 */
public abstract class SkillStatsRenderer {

    protected final PrimarySkillType skill;

    protected final DecimalFormat percent =
            new DecimalFormat("##0.00%", DecimalFormatSymbols.getInstance(Locale.US));
    protected final DecimalFormat decimal =
            new DecimalFormat("##0.00", DecimalFormatSymbols.getInstance(Locale.US));

    /** Set for the duration of a {@link #render} call so the subclass hooks can read it. */
    protected McMMOPlayer mmoPlayer;

    protected SkillStatsRenderer(@NotNull PrimarySkillType skill) {
        this.skill = skill;
    }

    /**
     * The renderer for {@code skill}: its dedicated subclass if one has been ported, otherwise a
     * {@link GenericSkillStatsRenderer} (header + sub-skill list, no bespoke effect lines). Add a case
     * here as each skill's detailed renderer lands.
     */
    public static @NotNull SkillStatsRenderer forSkill(@NotNull PrimarySkillType skill) {
        return switch (skill) {
            case MINING -> new MiningStatsRenderer();
            case WOODCUTTING -> new WoodcuttingStatsRenderer();
            case EXCAVATION -> new ExcavationStatsRenderer();
            case HERBALISM -> new HerbalismStatsRenderer();
            case SWORDS -> new SwordsStatsRenderer();
            case AXES -> new AxesStatsRenderer();
            case UNARMED -> new UnarmedStatsRenderer();
            case ARCHERY -> new ArcheryStatsRenderer();
            case CROSSBOWS -> new CrossbowsStatsRenderer();
            case TRIDENTS -> new TridentsStatsRenderer();
            case MACES -> new MacesStatsRenderer();
            case SPEARS -> new SpearsStatsRenderer();
            case TAMING -> new TamingStatsRenderer();
            default -> new GenericSkillStatsRenderer(skill);
        };
    }

    /**
     * Render the whole screen for {@code mmoPlayer} into {@code out}, one message per line.
     */
    public final void render(@NotNull McMMOPlayer mmoPlayer, @NotNull Consumer<Text> out) {
        this.mmoPlayer = mmoPlayer;
        final float skillValue = mmoPlayer.getSkillLevel(skill);

        dataCalculations(skillValue);

        sendHeader(out, skillValue);
        sendSubSkillList(out);
        sendStats(out, skillValue);
    }

    // --- shared header ------------------------------------------------------

    private void sendHeader(Consumer<Text> out, float skillValue) {
        final String skillName = McMMOMod.getSkillTools().getLocalizedSkillName(skill);
        out.accept(LocaleLoader.getText("Skills.Overhaul.Header", skillName));

        if (!SkillTools.isChildSkill(skill)) {
            out.accept(LocaleLoader.getText("Commands.XPGain.Overhaul", LocaleLoader.getString(
                    "Commands.XPGain." + StringUtils.getCapitalized(skill.toString()))));
            out.accept(LocaleLoader.getText("Effects.Level.Overhaul", (int) skillValue,
                    mmoPlayer.getProfile().getSkillXpLevel(skill),
                    mmoPlayer.getProfile().getXpToLevel(skill)));
            return;
        }

        // Child skill: show the parent levels instead of an XP-gain method (child skills earn no XP
        // directly — their level is derived from their parents).
        final StringBuilder parents = new StringBuilder();
        final List<PrimarySkillType> parentList =
                new ArrayList<>(McMMOMod.getSkillTools().getChildSkillParents(skill));
        for (int i = 0; i < parentList.size(); i++) {
            parents.append(LocaleLoader.getString("Effects.Child.ParentList",
                    McMMOMod.getSkillTools().getLocalizedSkillName(parentList.get(i)),
                    mmoPlayer.getSkillLevel(parentList.get(i))));
            if (i + 1 < parentList.size()) {
                parents.append("&7, ");
            }
        }
        out.accept(LocaleLoader.getText("Commands.XPGain.Overhaul",
                LocaleLoader.getString("Commands.XPGain.Child")));
        out.accept(LocaleLoader.getText("Effects.Child.Overhaul", (int) skillValue,
                parents.toString()));
    }

    // --- shared sub-skill list ----------------------------------------------

    /**
     * List every sub-skill of this skill with its rank / unlock state. Legacy rendered a clickable
     * guide list here; singleplayer has no guide links, so this shows the more useful rank info
     * instead (the effect values follow in the stats section).
     */
    private void sendSubSkillList(Consumer<Text> out) {
        final List<SubSkillType> subSkills =
                new ArrayList<>(McMMOMod.getSkillTools().getSubSkills(skill));
        if (subSkills.isEmpty()) {
            return;
        }
        subSkills.sort(Comparator.comparing(SubSkillType::getLocaleName, String.CASE_INSENSITIVE_ORDER));

        out.accept(LocaleLoader.getText("Skills.Overhaul.Header",
                LocaleLoader.getString("Effects.SubSkills.Overhaul")));

        for (SubSkillType subSkill : subSkills) {
            out.accept(TextUtils.toText(subSkillLine(subSkill)));
        }
    }

    private String subSkillLine(SubSkillType subSkill) {
        final String name = subSkill.getLocaleName();
        if (!RankUtils.hasUnlockedSubskill(mmoPlayer, subSkill)) {
            final int unlockLevel = RankUtils.getRankUnlockLevel(subSkill, 1);
            return "&8" + name + " &7- Locked (unlocks at Lv." + unlockLevel + ")";
        }
        final int highest = RankUtils.getHighestRank(subSkill);
        if (highest > 1) {
            return "&a" + name + " &7- Rank " + RankUtils.getRank(mmoPlayer, subSkill) + "/" + highest;
        }
        return "&a" + name + " &7- Unlocked";
    }

    // --- shared stats section -----------------------------------------------

    private void sendStats(Consumer<Text> out, float skillValue) {
        final List<String> statsMessages = statsDisplay(skillValue);
        if (statsMessages.isEmpty()) {
            return;
        }
        out.accept(LocaleLoader.getText("Skills.Overhaul.Header",
                LocaleLoader.getString("Commands.Stats.Self.Overhaul")));
        for (String message : statsMessages) {
            out.accept(TextUtils.toText(message));
        }
    }

    // --- helpers shared by skill subclasses (ported from legacy SkillCommand) ----

    /**
     * Build a "{@code <stat label>: <value>}" line for a sub-skill via the {@code Ability.Generic}
     * template, exactly like legacy {@code SkillCommand#getStatMessage}. {@code vars} fill the stat
     * description's placeholders (or, for {@code isCustom}, the description itself is the single
     * template argument).
     */
    protected String getStatMessage(SubSkillType subSkillType, String... vars) {
        return getStatMessage(false, false, subSkillType, vars);
    }

    protected String getStatMessage(boolean isExtra, boolean isCustom,
            @NotNull SubSkillType subSkillType, String... vars) {
        final String templateKey =
                isCustom ? "Ability.Generic.Template.Custom" : "Ability.Generic.Template";
        final String statDescriptionKey = !isExtra
                ? subSkillType.getLocaleKeyStatDescription()
                : subSkillType.getLocaleKeyStatExtraDescription();

        if (isCustom) {
            return LocaleLoader.getString(templateKey,
                    LocaleLoader.getString(statDescriptionKey, (Object[]) vars));
        }
        // {0} = the stat label, {1..} = vars (legacy prepended the label to the vars array).
        final Object[] merged = new Object[vars.length + 1];
        merged[0] = LocaleLoader.getString(statDescriptionKey);
        System.arraycopy(vars, 0, merged, 1, vars.length);
        return LocaleLoader.getString(templateKey, merged);
    }

    /** {@code min(level, maxLevel) / rankChangeLevel} — legacy {@code SkillCommand#calculateRank}. */
    protected int calculateRank(float skillValue, int maxLevel, int rankChangeLevel) {
        return Math.min((int) skillValue, maxLevel) / rankChangeLevel;
    }

    /**
     * Whether the current player has unlocked {@code subSkill}. Singleplayer's stand-in for legacy's
     * {@code RankUtils.hasUnlockedSubskill(player, …) && Permissions.x(player)} pair — permissions are
     * always granted in singleplayer, so the unlock check is the whole gate.
     */
    protected boolean hasUnlocked(@NotNull SubSkillType subSkill) {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, subSkill);
    }

    /**
     * The displayed length (seconds) of this skill's super ability at {@code skillValue}. Legacy
     * {@code calculateLengthDisplayValues} also returned an "endurance" value; singleplayer has no
     * activation perks, so only the base length remains.
     */
    protected String calculateLength(float skillValue) {
        final SuperAbilityType ability = McMMOMod.getSkillTools().getSuperAbility(skill);
        final int maxLength = McMMOMod.getSkillTools().getSuperAbilityMaxLength(ability);
        final int abilityLengthVar = McMMOMod.getAdvancedConfig().getAbilityLength();
        final int abilityLengthCap = McMMOMod.getAdvancedConfig().getAbilityLengthCap();

        int length = abilityLengthCap <= 0
                ? 2 + (int) (skillValue / abilityLengthVar)
                : 2 + (int) (Math.min(abilityLengthCap, skillValue) / abilityLengthVar);

        if (maxLength != 0) {
            length = Math.min(length, maxLength);
        }
        return String.valueOf(length);
    }

    // --- per-skill hooks ----------------------------------------------------

    /** Pre-compute any values the stats lines need (legacy {@code dataCalculations}). */
    protected abstract void dataCalculations(float skillValue);

    /** The bespoke effect lines for this skill (legacy {@code statsDisplay}), as §-coded strings. */
    protected abstract List<String> statsDisplay(float skillValue);
}
