package com.gmail.nossr50.util.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The single registry of skills that have been <em>renamed</em> since a save file or config file
 * could have been written, and what they used to be called.
 *
 * <p>This exists because {@link PrimarySkillType#name()} is not just an identifier — it is
 * <b>the on-disk save key</b> ({@code skills.<NAME>} / {@code experience.<NAME>} in
 * {@link com.gmail.nossr50.database.FlatFileProfileStore}) and the <b>section name</b> in every
 * shipped YAML config. Renaming a constant therefore silently orphans player data: the profile
 * loader falls back to the starting level and the player's progress "vanishes" with no error at
 * all. Both consumers read this table so a rename costs one entry here instead of a bug report.
 *
 * <p>Two separate mappings, because the two surfaces spell skills differently:
 * <ul>
 *   <li>{@link #legacyEnumName(PrimarySkillType)} — {@code SCREAMING_CASE}, the save-file key.
 *       Consumed by the profile store's read path (write always uses the current name, so the
 *       orphan disappears on the next save).</li>
 *   <li>{@link #legacyConfigSections()} — {@code Capitalized}, the YAML section name. Consumed by
 *       {@link com.gmail.nossr50.config.ConfigLoader}, which only <em>warns</em>: the user's config
 *       file is theirs, and a rewriter that silently moves their tuning around is a worse failure
 *       mode than a log line telling them to move it.</li>
 * </ul>
 *
 * <p>MC-free and dependency-free by design, so both the profile store and the config tier can read
 * it without pulling in the other.
 */
public final class SkillRenames {

    private SkillRenames() {
    }

    /**
     * Current skill → the {@code name()} it was persisted under before the rename. Only skills that
     * have actually been renamed appear here.
     */
    private static final Map<PrimarySkillType, String> LEGACY_ENUM_NAMES =
            new EnumMap<>(PrimarySkillType.class);

    /**
     * Legacy YAML section name → the section name it is spelled with today. Insertion-ordered so
     * the warnings come out in a stable order.
     */
    private static final Map<String, String> LEGACY_CONFIG_SECTIONS = new LinkedHashMap<>();

    /**
     * Legacy dotted YAML <em>path</em> → the path it lives at today. Insertion-ordered, same as
     * above.
     *
     * <p>Distinct from {@link #LEGACY_CONFIG_SECTIONS} because that table matches a whole path
     * <em>segment</em> anywhere in a key, which can only express "this skill was renamed". A
     * sub-skill that moves from one parent to another moves a sub-tree while both parents keep
     * existing — {@code Skills.Agility.Roll} → {@code Skills.Parkour.Roll} — and a segment match on
     * "Agility" would fire for Dodge, Fleet Footed and eight others that did not move.
     */
    private static final Map<String, String> LEGACY_CONFIG_PATHS = new LinkedHashMap<>();

    static {
        // Pass 2 / D5 (2026-07-25): ACROBATICS was renamed AGILITY when it absorbed the Land, Water
        // and Air movement domains, and a `skills.AGILITY` read-alias for `skills.ACROBATICS` lived
        // here.
        //
        // That alias was REMOVED on 2026-07-27 (ruled by the user), when Agility became a *child*
        // skill of Parkour/Swimming/Flying. A child skill has no save key at all — the profile store
        // only reads and writes NON_CHILD_SKILLS, and a child's level is recomputed from its parents
        // on every load — so there is nothing left for the alias to migrate to, and pre-existing
        // Agility progress is deliberately allowed to zero out.
        //
        // LEGACY_ENUM_NAMES is intentionally left EMPTY rather than deleted. It is the mechanism, not
        // the data: the next rename costs one line here instead of a silent profile reset.
        LEGACY_CONFIG_SECTIONS.put("Acrobatics", "Agility");

        // 2026-08-03 (GitHub #4): Roll was re-parented from AGILITY to PARKOUR so that the falls it
        // pays XP for level the skill that gates it. Every derived address moved with the enum name;
        // a user who had tuned the old block would otherwise find it silently ignored, because
        // copyMissingDefaults back-fills only ABSENT keys and would happily write shipped defaults
        // to the new path alongside their edits at the old one.
        LEGACY_CONFIG_PATHS.put("Skills.Agility.Roll", "Skills.Parkour.Roll");
        LEGACY_CONFIG_PATHS.put("Skills.Agility.GracefulRoll", "Skills.Parkour.GracefulRoll");

        // 2026-08-04 (GitHub #3): Husbandry's anti-exploit gate moved off the BREEDING and onto the
        // XP PAYOUT, which also moved it out of advanced.yml and into experience.yml. The old key is
        // not renamed so much as retired -- there is no longer any cap on how many animals one item
        // may set in love -- but the failure it leaves behind is the same one this table exists for:
        // copyMissingDefaults never deletes anything, so a player who deliberately tuned
        // MaxAdditionalAnimals is left with an edited-looking key the game no longer reads.
        //
        // The target names its file, because unlike every other entry here this move crosses one.
        LEGACY_CONFIG_PATHS.put("Skills.Husbandry.MultiBreed.MaxAdditionalAnimals",
                "experience.yml → ExploitFix.Husbandry.Breed_Xp_Awards_Per_Window");
    }

    /**
     * The save-file key this skill used to be written under.
     *
     * @param skill the skill to look up
     * @return the legacy {@code name()}, or {@code null} when this skill has never been renamed
     *         (the overwhelmingly common case)
     */
    public static @Nullable String legacyEnumName(@NotNull PrimarySkillType skill) {
        return LEGACY_ENUM_NAMES.get(skill);
    }

    /**
     * Legacy YAML section names mapped to their current spelling, for the config-orphan warning.
     *
     * @return an unmodifiable view; never {@code null}
     */
    public static @NotNull Map<String, String> legacyConfigSections() {
        return Map.copyOf(LEGACY_CONFIG_SECTIONS);
    }

    /**
     * Legacy dotted config paths mapped to where they live now, for the config-orphan warning.
     *
     * @return an unmodifiable view; never {@code null}
     */
    public static @NotNull Map<String, String> legacyConfigPaths() {
        return Map.copyOf(LEGACY_CONFIG_PATHS);
    }
}
