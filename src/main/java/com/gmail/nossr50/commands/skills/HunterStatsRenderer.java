package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.skills.hunter.HunterManager;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * {@code /mcstats hunter} — D-HU7's answer to the skill's central usability problem: <b>three mastery
 * thresholds across every creature in the game is a great deal of invisible state.</b> Until this
 * screen existed, a player could kill 499 zombies and have nothing at all to show they were 499 of
 * the way somewhere.
 *
 * <p>The two axes are shown as what they are. The <b>vertical</b> one is ordinary — Trophy Hunter's
 * chance and the mob tier its rank reaches, exactly like any other sub-skill's stats. The
 * <b>horizontal</b> one has no equivalent anywhere else in the mod: it is an open-ended per-creature
 * counter, so it renders as a short league table rather than as a number.
 *
 * <h2>Why only the top few creatures</h2>
 * A dedicated player's kill map holds dozens of entries and the profile allows 4,096. D-HU7 asks for
 * "the top N most-killed mobs, not all 120", and {@link #TOP_CREATURES} is that N. The complete
 * per-creature answer is Quarry Sense's job, in front of the creature, where the player is actually
 * asking the question.
 *
 * <p>The entries show the <b>mastery</b> tier rather than the creature's Hunter tier. On this screen
 * the question is "how is my mastery coming along", and the kill count is meaningless without the
 * tier it has bought; a creature's own tier is a fact about the creature, which Quarry Sense answers
 * where it matters.
 *
 * @see <a href="file:../../../../../../../plans/new-skills/hunter.md">plans/new-skills/hunter.md</a>
 */
public final class HunterStatsRenderer extends SkillStatsRenderer {

    /**
     * How many creatures the mastery league table lists.
     *
     * <p>Three, because the section has to stay readable underneath the header and sub-skill list on
     * a chat screen that cannot scroll far, and because the entries below third place are a long tail
     * — the interesting question is which creatures the player is close to mastering.
     */
    static final int TOP_CREATURES = 3;

    private HunterManager hunter;

    private boolean canTrophyHunt;
    private String trophyChance;
    private int trophyTier;

    public HunterStatsRenderer() {
        super(PrimarySkillType.HUNTER);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        hunter = mmoPlayer.getHunterManager();

        canTrophyHunt = hasUnlocked(SubSkillType.HUNTER_TROPHY_HUNTER);
        if (canTrophyHunt) {
            trophyChance = ProbabilityUtil.getRNGDisplayValues(
                    mmoPlayer, SubSkillType.HUNTER_TROPHY_HUNTER)[0];
            // The rank number IS the mob tier (see HunterManager#canTrophyHunt), so the rank the
            // player has reached is literally the highest tier they may trophy-hunt. Read off
            // RankUtils rather than re-derived from the level, so it cannot drift from the gate.
            trophyTier = RankUtils.getRank(mmoPlayer, SubSkillType.HUNTER_TROPHY_HUNTER);
        }
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();
        if (hunter == null) {
            return messages; // Profile not loaded; the header and sub-skill list still render.
        }

        addMasteryStats(messages);

        if (canTrophyHunt) {
            messages.add(getStatMessage(SubSkillType.HUNTER_TROPHY_HUNTER, trophyChance));
            messages.add(getStatMessage(true, false, SubSkillType.HUNTER_TROPHY_HUNTER,
                    trophyTier + "/" + HunterManager.MAX_TIER));
        }
        if (hunter.canQuarrySense()) {
            messages.add(getStatMessage(SubSkillType.HUNTER_QUARRY_SENSE,
                    LocaleLoader.getString("Hunter.SubSkill.QuarrySense.Stat.Extra")));
        }

        return messages;
    }

    /**
     * The horizontal axis: how much of the bestiary this player has worked through, then the league
     * table.
     *
     * <p>Built from raw locale keys rather than {@link #getStatMessage}, because Mob Mastery has no
     * {@link SubSkillType} constant and deliberately never will — it unlocks on a per-creature kill
     * counter, which no rank config can express (see {@code HunterManager}'s class javadoc). This is
     * the same shape {@code TamingStatsRenderer} uses for its bespoke-keyed lines.
     */
    private void addMasteryStats(List<String> messages) {
        final Map<String, Integer> allKills = hunter.getAllKills();
        if (allKills.isEmpty()) {
            // Worth a line of its own: an empty stats block reads as a broken screen, and a first-time
            // Hunter looking here is exactly the player who most needs telling the counters are live.
            messages.add(LocaleLoader.getString("Hunter.SubSkill.MobMastery.Stat.Nothing"));
            return;
        }

        messages.add(LocaleLoader.getString("Ability.Generic.Template",
                LocaleLoader.getString("Hunter.SubSkill.MobMastery.Stat"), allKills.size()));
        messages.add(LocaleLoader.getString("Ability.Generic.Template",
                LocaleLoader.getString("Hunter.SubSkill.MobMastery.Stat.Extra"),
                hunter.masteredCreatureCount()));

        for (Map.Entry<String, Integer> entry : hunter.topKills(TOP_CREATURES)) {
            final int kills = entry.getValue();
            final int tier = hunter.masteryTier(kills);
            messages.add(LocaleLoader.getString("Hunter.SubSkill.MobMastery.Stat.Entry",
                    creatureName(entry.getKey()), kills,
                    tier <= 0
                            ? LocaleLoader.getString("Hunter.SubSkill.MobMastery.Stat.Unmastered")
                            : LocaleLoader.getString("Hunter.SubSkill.MobMastery.Stat.Mastered",
                                    tier, String.valueOf(hunter.masteryDamageBonus(kills)))));
        }
    }

    /**
     * A stored kill-counter key rendered as a creature name, falling back to the raw id.
     *
     * <h2>⚠️ {@code Registries.ENTITY_TYPE} is a {@code DefaultedRegistry} and its {@code get} LIES</h2>
     * {@code SimpleDefaultedRegistry#get(Identifier)} answers an unknown id with the registry's
     * <em>default entry</em> — {@code minecraft:pig} — rather than {@code null} (bytecode: it calls
     * {@code SimpleRegistry.get}, tests the result for null, and substitutes {@code defaultEntry}).
     * That is not a hypothetical here: stage 2 deliberately stores these keys as raw strings and
     * resolves them only at use, precisely so that a creature from an uninstalled mod does not cost
     * the player their profile — which means this screen is the one place those unresolvable keys
     * surface. Reading them through {@code get} would file somebody's 4,000 modded kills under
     * <b>"Pig"</b>, silently and plausibly.
     *
     * <p>{@code getOptionalValue} is the honest read: its body calls the same
     * {@code SimpleRegistry.get} and wraps it in {@code Optional.ofNullable}, skipping the default
     * substitution entirely. An id that no longer parses at all (a corrupted profile) falls back the
     * same way.
     */
    private static String creatureName(String mobId) {
        final Identifier id = Identifier.tryParse(mobId);
        if (id == null) {
            return mobId;
        }
        return Registries.ENTITY_TYPE.getOptionalValue(id)
                .map(type -> type.getName().getString())
                .orElse(mobId);
    }
}
