package com.gmail.nossr50.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.util.text.StringUtils;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

/**
 * Exercises {@link CoreSkillsConfig} against the real bundled {@code coreskills.yml} on the test
 * classpath, with a temp data folder.
 */
class CoreSkillsConfigTest {

    @Test
    void writesDefaultToDiskWhenMissing(@TempDir Path dataFolder) {
        new CoreSkillsConfig(dataFolder);
        assertTrue(Files.exists(dataFolder.resolve("coreskills.yml")));
    }

    @Test
    void primarySkillEnabledReadsExplicitTrue(@TempDir Path dataFolder) {
        final CoreSkillsConfig config = new CoreSkillsConfig(dataFolder);
        // coreskills.yml sets Alchemy.Enabled: true explicitly. (This read the retired Agility row
        // until 2026-08-17 -- any explicitly-listed skill exercises the same branch.)
        assertTrue(config.isPrimarySkillEnabled(PrimarySkillType.ALCHEMY));
    }

    @Test
    void everyPrimarySkillHasAnExplicitRow() throws Exception {
        // ENUM -> YML. Replaces `primarySkillEnabledDefaultsTrueForUnlistedSkill`, which was
        // VACUOUS: it claimed "Mining has no entry in the bundled default -> defaults true" and
        // `Mining.Enabled: true` has been present all along, so it re-exercised the explicit-true
        // branch above it and the default branch it was named for was never reached.
        //
        // 🔑 The branch it meant to cover is UNREACHABLE through the public surface, and that is
        // this assertion's whole point rather than an excuse for dropping it. `loadKeys` reads
        // `config.getBoolean(enabledPath(skill), true)`, so a missing key and a present `true` are
        // indistinguishable -- and a user who deletes a row gets it back-filled by
        // ConfigLoader#copyMissingDefaults from the bundled default anyway. The ONLY way to reach
        // the default is for the BUNDLED file to omit a skill, which is exactly the drift this
        // asserts cannot happen. The default itself is correct and deliberate (failing closed would
        // silently switch the mod off); what was missing is anything asking the question it hides.
        //
        // ⚠️ Audit the roster against PrimarySkillType.values(), never against a diff -- an added
        // enum constant is invisible to every incremental edit. Cooking shipped across six commits
        // and reached sixteen wiki files zero times.
        final Map<String, Object> root = loadBundledCoreSkills();

        final Set<String> missing = new TreeSet<>();
        for (PrimarySkillType skill : PrimarySkillType.values()) {
            final Object section = root.get(sectionName(skill));
            if (!(section instanceof Map<?, ?> rows) || !(rows.get("Enabled") instanceof Boolean)) {
                missing.add(sectionName(skill));
            }
        }

        assertTrue(missing.isEmpty(),
                "coreskills.yml has no Enabled switch for: " + missing
                        + " -- those skills cannot be turned off, and the absence reads as `true`"
                        + " through getBoolean's default, so nothing else in the build will say so");
    }

    @Test
    void everyCoreSkillsSectionMapsToALivePrimarySkill() throws Exception {
        // YML -> ENUM, the converse and the dangerous direction. coreskills.yml IS written to disk
        // and IS player-editable, so a section naming a skill that no longer exists is a switch the
        // player sets and nothing reads -- it looks exactly like a live one in the file. That is how
        // `Unarmed.Disarm` and `Unarmed.IronGrip` outlived the mechanics they configured (TODO 1.1),
        // and the same one-directional-completeness trap RankConfigTest documents for skillranks.yml.
        //
        // ⚠️ A retired skill must have its row DELETED, not left behind. The file's own header says
        // a leftover `Agility:` block is harmless because it is never read -- true of the code, and
        // precisely why nothing would ever flag it.
        final Map<String, Object> root = loadBundledCoreSkills();

        final Set<String> live = new TreeSet<>();
        for (PrimarySkillType skill : PrimarySkillType.values()) {
            live.add(sectionName(skill));
        }

        final Set<String> orphans = new TreeSet<>();
        for (String section : root.keySet()) {
            if (!live.contains(section)) {
                orphans.add(section);
            }
        }

        assertTrue(orphans.isEmpty(),
                "coreskills.yml configures skills that no longer exist: " + orphans);
    }

    /**
     * The {@code coreskills.yml} section name for {@code skill}, derived through the shipped
     * key-builder rather than a second copy of its capitalisation rule.
     */
    private static String sectionName(PrimarySkillType skill) {
        return StringUtils.getCapitalized(skill.toString());
    }

    private static Map<String, Object> loadBundledCoreSkills() throws Exception {
        // The BUNDLED resource, not the temp-dir copy: the disk copy has already been back-filled
        // by copyMissingDefaults, which would paper over exactly the omission being looked for.
        try (InputStream in = CoreSkillsConfigTest.class.getResourceAsStream("/coreskills.yml")) {
            assertNotNull(in, "coreskills.yml is not on the test classpath");
            return new Yaml().load(in);
        }
    }
}
