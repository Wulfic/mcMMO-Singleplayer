package com.gmail.nossr50.util.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
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
     * Legacy dotted YAML <em>path</em> → where its value went, for paths this loader <b>cannot</b>
     * move automatically. Warn-only; insertion-ordered.
     *
     * <p>Distinct from {@link #LEGACY_CONFIG_SECTIONS} because that table matches a whole path
     * <em>segment</em> anywhere in a key, which can only express "this skill was renamed". A
     * sub-skill that moves from one parent to another moves a sub-tree while both parents keep
     * existing — {@code Skills.Agility.Roll} → {@code Skills.Parkour.Roll} — and a segment match on
     * "Agility" would fire for Dodge, Fleet Footed and eight others that did not move.
     *
     * <p>⚠️ The values here are <b>human-readable destinations, not writable paths</b>: an entry
     * lands in this table precisely because the value cannot simply be copied across — it changed
     * file, changed meaning, or was retired outright. Anything that IS a straight move belongs in
     * {@link #MOVED_CONFIG_PATHS}, which is machine-consumed. Never feed this table to a setter.
     */
    private static final Map<String, String> LEGACY_CONFIG_PATHS = new LinkedHashMap<>();

    /**
     * One straight within-file relocation of a config sub-tree, applied automatically on load.
     *
     * <p>⚠️ {@code fileName} is <b>not</b> bookkeeping — it is the safety property, and omitting it
     * is an actual bug this port shipped for about ten minutes. mcMMO spells the same skill at
     * different depths in different files: {@code Skills.Agility.Dodge} in advanced.yml but bare
     * {@code Agility.Dodge} in skillranks.yml. Registering the short form globally makes it match in
     * <em>every</em> file with a root-level {@code Agility} section — including coreskills.yml, whose
     * dev copies still carry an {@code Agility.Roll} block from the pre-GitHub-#10 schema that had
     * per-sub-skill switches. A file-blind migrator would have dutifully "moved" that dead key to
     * {@code Parkour.Roll}, resurrecting a switch the code stopped reading a schema ago.
     *
     * <p>So a move is scoped to the one file it was written for, and a path that means something
     * different elsewhere is simply not that file's problem.
     *
     * <p><b>Usually one destination; occasionally several.</b> {@code newPaths} is a list because a
     * skill that dissolves into three does not always split its keys one-for-one: retiring Agility
     * on 2026-08-17 left {@code Skills.Agility.FleetFooted.MaxBonusLevel} — one number meaning "the
     * level Fleet Footed stops scaling at" — with three equally correct homes, one per parent. Fanning
     * it out preserves the player's intent exactly; picking one parent and letting the other two fall
     * back to the shipped default would half-apply their tuning, which is worse than not applying it.
     * ⚠️ A one-to-many move is still <b>one delete</b>: every destination is written before the legacy
     * path is cleared. Declaring the same legacy path in three separate entries instead does NOT work
     * — the first entry deletes it and the other two silently find nothing.
     *
     * @param fileName   the config file this move applies to, and only this one
     * @param legacyPath where the value may still be stranded
     * @param newPaths   every path the code reads it from now; at least one
     */
    public record MovedPath(@NotNull String fileName, @NotNull String legacyPath,
                            @NotNull List<String> newPaths) {

        public MovedPath {
            if (newPaths.isEmpty()) {
                throw new IllegalArgumentException(
                        "MovedPath " + legacyPath + " in " + fileName + " has no destination; a move "
                                + "to nowhere is a RETIREMENT and belongs in LEGACY_CONFIG_PATHS, "
                                + "where the player is told rather than having their tuning deleted");
            }
            newPaths = List.copyOf(newPaths);
        }

        /** The overwhelmingly common single-destination move. */
        public MovedPath(@NotNull String fileName, @NotNull String legacyPath,
                @NotNull String newPath) {
            this(fileName, legacyPath, List.of(newPath));
        }
    }

    /**
     * Straight relocations that {@code ConfigLoader} migrates automatically, in declaration order.
     *
     * <p>Both paths are real, writable and rooted in the same file, and the sub-trees under them are
     * shape-identical — every leaf under the old path has exactly one destination under the new one.
     * That is the entry condition. A move that reshapes, renames leaves, changes units or crosses
     * files does not belong here: it goes in {@link #LEGACY_CONFIG_PATHS} and the player is told to
     * move it by hand.
     */
    private static final List<MovedPath> MOVED_CONFIG_PATHS = new ArrayList<>();

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
        //
        // These two shipped as warn-only and were PROMOTED to automatic migration on 2026-08-10,
        // when the entries below made warn-only untenable: leaving Roll to be hand-moved while its
        // siblings migrate themselves is an inconsistency nobody can be expected to reason about.
        MOVED_CONFIG_PATHS.add(new MovedPath("advanced.yml",
                "Skills.Agility.Roll", "Skills.Parkour.Roll"));

        // ⚠️ GracefulRoll is warn-only, NOT migrated, and the difference is not an oversight.
        // `Skills.Parkour.GracefulRoll.DamageThreshold` has a getter but is deliberately absent from
        // the shipped advanced.yml: AdvancedConfig#getGracefulRollDamageThreshold is read by nothing
        // except its own validator, because MovementManager hardcodes the graceful threshold as
        // getRollDamageThreshold() * 2 exactly as legacy did. Migrating a value into a key nothing
        // reads would move a player's tuning somewhere it is just as ignored, while implying it now
        // works. Tell them the truth instead.
        LEGACY_CONFIG_PATHS.put("Skills.Agility.GracefulRoll",
                "nothing — a graceful roll negates twice Skills.Parkour.Roll.DamageThreshold, and "
                        + "has no threshold of its own");

        // 2026-08-10: Agility keeps only the two sub-skills that span every movement medium (Fleet
        // Footed, Second Wind). The seven single-medium ones were re-parented onto the medium's own
        // primary skill, so that each is gated on the level you earn by doing the thing it is a perk
        // for -- the same argument #4 made for Roll, applied to the rest of the roster.
        //
        // ⚠️ Each sub-skill needs TWO entries, because its tuning and its unlock ladder live in
        // different files at different DEPTHS: advanced.yml nests everything under a `Skills` root,
        // skillranks.yml does not. Registering only the advanced.yml form is exactly the gap that
        // shipped in the first draft of this change -- the tuning moved, the rank ladder did not, and
        // the file was left carrying both spellings with only one of them read.
        for (String[] move : new String[][] {
                {"Dodge", "Parkour"}, {"Athlete", "Parkour"}, {"Smash", "Parkour"},
                {"LeadLungs", "Swimming"}, {"LakeRaider", "Swimming"},
                {"Glide", "Flying"}, {"SolarWings", "Flying"},
        }) {
            final String subSkill = move[0];
            final String newParent = move[1];
            MOVED_CONFIG_PATHS.add(new MovedPath("advanced.yml",
                    "Skills.Agility." + subSkill, "Skills." + newParent + "." + subSkill));
            MOVED_CONFIG_PATHS.add(new MovedPath("skillranks.yml",
                    "Agility." + subSkill, newParent + "." + subSkill));
        }

        // 2026-08-17: Agility is RETIRED. Its last two sub-skills split across the three movement
        // parents that already owned everything else, so what was one sub-skill carrying one rank per
        // medium is now three single-rank sub-skills -- a sub-skill's parent is derived from its enum
        // name prefix, and one constant cannot span three parents.
        //
        // ⚠️ Declared LEAF BY LEAF rather than as a sub-tree move, and that is the entry condition
        // above being honoured rather than dodged. `Skills.Agility.FleetFooted` as a sub-tree is NOT
        // shape-identical to any one parent's block: its three `<Medium>_MaxBonus` leaves each go to a
        // DIFFERENT parent and each is renamed to a bare `MaxBonus` on arrival. Named one at a time,
        // every entry below is a single leaf with a single destination, same unit and same meaning --
        // which is exactly what the migrator can carry safely.
        MOVED_CONFIG_PATHS.add(new MovedPath("advanced.yml",
                "Skills.Agility.FleetFooted.Land_MaxBonus",
                "Skills.Parkour.FleetFooted.MaxBonus"));
        MOVED_CONFIG_PATHS.add(new MovedPath("advanced.yml",
                "Skills.Agility.FleetFooted.Water_MaxBonus",
                "Skills.Swimming.FleetFooted.MaxBonus"));
        MOVED_CONFIG_PATHS.add(new MovedPath("advanced.yml",
                "Skills.Agility.FleetFooted.Air_MaxBonus",
                "Skills.Flying.FleetFooted.MaxBonus"));

        // The one-to-many case, and the reason MovedPath carries a list at all. "The level Fleet
        // Footed stops scaling at" was a single number under Agility and is now three, one per
        // parent. All three are equally the player's stated intent, so all three get it. Migrating it
        // to Parkour alone would leave a player who raised it to 1000 with 1000 on land and the
        // shipped default in water and air -- their tuning half-applied, which reads as a bug.
        MOVED_CONFIG_PATHS.add(new MovedPath("advanced.yml",
                "Skills.Agility.FleetFooted.MaxBonusLevel",
                List.of("Skills.Parkour.FleetFooted.MaxBonusLevel",
                        "Skills.Swimming.FleetFooted.MaxBonusLevel",
                        "Skills.Flying.FleetFooted.MaxBonusLevel")));

        // Second Wind stays ONE super ability with one cooldown and one trigger item (ruling A-2);
        // only the three BODIES split, each to the parent whose medium fires it. Leaf names are
        // unchanged, so these are the plainest moves in the table.
        for (String[] body : new String[][] {
                {"DartRange", "Parkour"}, {"DartDamage", "Parkour"}, {"DartKnockback", "Parkour"},
                {"AquamanAmplifier", "Swimming"},
                {"LimitlessBoost", "Flying"},
        }) {
            MOVED_CONFIG_PATHS.add(new MovedPath("advanced.yml",
                    "Skills.Agility.SecondWind." + body[0],
                    "Skills." + body[1] + ".SecondWind." + body[0]));
        }

        // ⚠️⚠️ THE RANK LADDERS ARE WARN-ONLY, AND THAT IS THE POINT OF RULING A-1.
        // `Agility.FleetFooted` shipped as Rank_1 1 / Rank_2 200 / Rank_3 400 and `Agility.SecondWind`
        // as 250 / 500 / 750 -- three ranks on ONE ladder, encoding the order the mediums unlocked in.
        // Split across three parents there is no order left to encode, so each is now a SINGLE rank at
        // the old rank 1 number. Migrating a player's ladder would carry Rank_2 and Rank_3 into a
        // sub-skill that has no rank 2 or 3, resurrecting the very gate A-1 deliberately removed and
        // stranding two values nothing reads. Same call as GracefulRoll above: migrate only where the
        // destination MEANS THE SAME THING.
        LEGACY_CONFIG_PATHS.put("Agility.FleetFooted",
                "Parkour.FleetFooted, Swimming.FleetFooted and Flying.FleetFooted -- now ONE rank "
                        + "each instead of one ladder of three, so the old Rank_2/Rank_3 levels no "
                        + "longer mean anything and are not carried across");
        LEGACY_CONFIG_PATHS.put("Agility.SecondWind",
                "Parkour.SecondWind, Swimming.SecondWind and Flying.SecondWind -- now ONE rank each "
                        + "instead of one ladder of three, so the old Rank_2/Rank_3 levels no longer "
                        + "mean anything and are not carried across");

        // config.yml, same date and the same reason: Dodge's anti-lightning switch follows Dodge.
        // A leaf rather than a sub-tree, which the migrator handles identically.
        MOVED_CONFIG_PATHS.add(new MovedPath("config.yml",
                "Skills.Agility.Prevent_Dodge_Lightning",
                "Skills.Parkour.Prevent_Dodge_Lightning"));

        // 2026-08-18: the settings that belong to MOVEMENT AS A WHOLE move off the retired skill's
        // name and onto a neutral `Movement` root. These are NOT sub-skill tuning -- that all moved
        // in the 2026-08-17 block above. These are the keys that were only ever under `Agility`
        // because Agility was the umbrella over all three domains, and they would read as a lie
        // filed under any single one of Parkour, Swimming or Flying.
        //
        // ⚠️ Every one of them is read from a LITERAL path, which is why they survived the enum's
        // removal at all. The enum-DERIVED siblings (`Skills.Agility.Level_Cap`,
        // `.Enabled_For_PVP`, `.Enabled_For_PVE`, `Experience_Bars.Agility`) are dead rather than
        // moved -- there is no skill left for a per-skill key to be about -- so they are absent here
        // on purpose. A MovedPath for one would migrate a player's value into a path nothing reads.
        MOVED_CONFIG_PATHS.add(new MovedPath("config.yml",
                "Skills.Agility.XP_After_Teleport_Cooldown",
                "Skills.Movement.XP_After_Teleport_Cooldown"));
        MOVED_CONFIG_PATHS.add(new MovedPath("config.yml",
                "Skills.Agility.Second_Wind_Item", "Skills.Movement.Second_Wind_Item"));
        MOVED_CONFIG_PATHS.add(new MovedPath("experience.yml",
                "ExploitFix.Agility", "ExploitFix.Movement"));
        // ⚠️ Declared leaf-group by leaf-group, NOT as one `Experience_Values.Agility` sub-tree
        // move, and the reason is a bug this caught: the old block nested a `Movement:` section
        // INSIDE `Agility:`, so renaming only the outer segment yields
        // `Experience_Values.Movement.Movement` -- a doubled path none of the getters read. The
        // inner block is renamed to `Travel` at the same time, and saying so explicitly here is
        // what keeps the two halves from being applied in the wrong order.
        for (String leaf : new String[] {"Dodge", "Roll", "Fall", "FeatherFall_Multiplier"}) {
            MOVED_CONFIG_PATHS.add(new MovedPath("experience.yml",
                    "Experience_Values.Agility." + leaf, "Experience_Values.Movement." + leaf));
        }
        MOVED_CONFIG_PATHS.add(new MovedPath("experience.yml",
                "Experience_Values.Agility.Movement", "Experience_Values.Movement.Travel"));

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
     * Legacy dotted config paths mapped to a <em>description</em> of where they live now, for the
     * config-orphan warning. Not writable paths — see {@link #LEGACY_CONFIG_PATHS}.
     *
     * @return an unmodifiable view; never {@code null}
     */
    public static @NotNull Map<String, String> legacyConfigPaths() {
        return Map.copyOf(LEGACY_CONFIG_PATHS);
    }

    /**
     * The straight path moves declared for {@code fileName}, in declaration order.
     *
     * <p>Filtered by file rather than handing back the whole table, so a caller cannot accidentally
     * apply another file's paths to this one — see {@link MovedPath} for the dead {@code
     * Agility.Roll} key in coreskills.yml that makes that a real hazard rather than a hypothetical.
     *
     * @param fileName the config file being loaded
     * @return an unmodifiable list, empty for the many files that have never had a path move
     */
    public static @NotNull List<MovedPath> movedConfigPaths(@NotNull String fileName) {
        final List<MovedPath> forFile = new ArrayList<>();
        for (MovedPath move : MOVED_CONFIG_PATHS) {
            if (move.fileName().equals(fileName)) {
                forFile.add(move);
            }
        }
        return Collections.unmodifiableList(forFile);
    }

    /**
     * Every declared path move, across all files, in declaration order. For drift guards that need
     * to assert something about the table as a whole; the loader itself always filters by file.
     *
     * @return an unmodifiable view; never {@code null}
     */
    public static @NotNull List<MovedPath> allMovedConfigPaths() {
        return Collections.unmodifiableList(new ArrayList<>(MOVED_CONFIG_PATHS));
    }
}
