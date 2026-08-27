package com.gmail.nossr50.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.platform.Materials;
import com.gmail.nossr50.util.McTestRegistries;
import com.gmail.nossr50.util.text.ConfigStringUtils;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TODO §50 — the runtime half of the config-id gate for {@code config.yml}.
 *
 * <p><b>Why this class exists at all.</b> TODO 5.5 has two legs: {@code scripts/config-id-audit.py}
 * (cross-version, run by hand) and {@link ConfigItemIdResolutionTest} (this band's live registry,
 * run unattended inside {@code ./gradlew build}). Until §50, {@code config.yml} was in
 * <b>neither</b> — 187 id references across 9 sections, the largest behavioural id table in the jar,
 * with no id check of any kind for the whole life of the port. The first run of the extended script
 * found <b>26 rows dead on every supported version</b>, two of which were live player-facing bugs.
 *
 * <p><b>⚠️⚠️ The obvious assertion is wrong here, and {@link ConfigItemIdResolutionTest} says why
 * in more detail: "every shipped id resolves on this version" is vacuous on the newest band and
 * FALSE by design on an older one.</b> {@code Bonus_Drops.Herbalism} legitimately names
 * {@code Firefly_Bush} and {@code Leaf_Litter}, which do not exist on {@code 1.21}. Worse,
 * {@code config.yml}'s bonus-drop tables are <b>not pruned at all</b> — {@code getDoubleDropsEnabled}
 * is a plain key lookup — so there is no post-prune invariant to lean on either.
 *
 * <p>Two properties that hold identically on every band, and that a missing gate does not satisfy:
 * <ol>
 *   <li><b>No row names the wrong registry for its seam.</b> A section keyed on the broken BLOCK may
 *       not name something that is an item and not a block. This is version-independent — an item
 *       that is not a block is not a block on any version — and it is the exact defect class §50
 *       deleted 21 of. It says nothing about rows that resolve as <em>neither</em>, which is what
 *       legitimate band drift looks like.</li>
 *   <li><b>Whatever this Minecraft version calls an object, the table knows that name.</b> The
 *       both-names pattern asserted from the live registry rather than from a spelling. This is the
 *       half that catches a rename: on {@code 26.2} the chain block is {@code iron_chain} and the
 *       row must be {@code Iron_Chain}; on {@code 1.21.5} it is {@code chain} and the row must be
 *       {@code Chain}. One assertion, correct on every band, red on the band that is wrong.</li>
 * </ol>
 *
 * <p>Both are backed by an anti-vacuity test that feeds each detector an input it MUST flag. A
 * detector nobody has watched fire is a detector that passes because it examines nothing — this
 * repo has shipped fifteen of those.
 */
class ConfigYamlBonusDropsTest {

    private static final String BLOCK = "block";
    private static final String ITEM = "item";

    /**
     * Mirrors {@code BONUS_DROP_KIND} in {@code scripts/config-id-audit.py}, and like it, traced to
     * the call site rather than guessed.
     *
     * <p>⚠️ Smelting and Cooking are ITEM because their seams key on the furnace/campfire
     * <em>result</em>; the three block skills key on the block that was broken. Note this does NOT
     * line up with {@code experience.yml}, where Smelting keys on the <em>input</em> item — same
     * kind, different object, so neither table can be derived from the other.
     */
    private static final Map<String, String> BONUS_DROP_KIND = Map.of(
            "Herbalism", BLOCK,
            "Mining", BLOCK,
            "Woodcutting", BLOCK,
            "Smelting", ITEM,
            "Cooking", ITEM);

    /**
     * An object mcMMO must cover under {@code section}, listed by every registry path it has carried
     * across the supported band. Exactly those candidates that exist on THIS version must appear in
     * the table; a candidate that does not exist here is not required and not forbidden.
     */
    private record Covered(String section, String kind, List<String> candidates, String why) {}

    private static final List<Covered> MUST_BE_COVERED = List.of(
            // minecraft:chain -> minecraft:iron_chain in the Copper Age drop. experience.yml
            // carried both names from the start; config.yml carried only `Chain`, so chains lost
            // their bonus roll on 1.21.10, 1.21.11, 26.1.2 and 26.2 until §50.
            new Covered("Mining", BLOCK, List.of("chain", "iron_chain"),
                    "the chain block is renamed mid-band; both spellings must be present"),
            // config.yml said `Block_Of_Amethyst`, which is not a registry id on ANY version, while
            // experience.yml said `Amethyst_Block: 500`. Mining one paid 500 XP and dropped nothing
            // extra, on every band since the port.
            new Covered("Mining", BLOCK, List.of("amethyst_block"),
                    "experience.yml pays 500 XP for this block; the drop table must agree"));

    @BeforeAll
    static void bootstrap() {
        McTestRegistries.bootstrap();
    }

    /**
     * Without this the whole class is vacuous in the most convincing way available: an empty
     * registry answers "not an item" and "not a block" for everything, so property (1) holds because
     * nothing is ever the wrong kind, and property (2) holds because no candidate exists.
     */
    @BeforeAll
    static void registryIsLive() {
        assertTrue(Materials.itemRegistryIsPopulated(),
                "registry not populated — every assertion below would pass for the wrong reason");
    }

    // ------------------------------------------------------------------ (1) the wrong-kind rule

    @Test
    void noBonusDropRowNamesTheWrongRegistryForItsSeam() {
        final Map<String, Set<String>> sections = bonusDropSections();

        // Anti-vacuity first: an unparsed file is an empty map, and an empty map has no bad rows.
        assertFalse(sections.isEmpty(), "no Bonus_Drops sections parsed out of the bundled "
                + "config.yml — the resource is missing or the shape changed");
        for (Map.Entry<String, Set<String>> e : sections.entrySet()) {
            assertFalse(e.getValue().isEmpty(),
                    "Bonus_Drops." + e.getKey() + " parsed to zero rows");
        }

        final List<String> wrong = wrongKindRows(sections);
        assertTrue(wrong.isEmpty(),
                "config.yml lists " + wrong.size() + " bonus-drop row(s) under a section keyed on "
                        + "the other registry. Such a row can never match and pays nothing, on any "
                        + "version — it is a defect, not band drift:\n  " + String.join("\n  ", wrong)
                        + "\nSee TODO.md §50; `python scripts/config-id-audit.py --check` is the "
                        + "cross-version half of the same question.");
    }

    /**
     * The detector must fire. Fed the exact shape §50 deleted — {@code Coal}, a real item, under the
     * block-keyed Mining section — it has to report it. Without this, {@link
     * #noBonusDropRowNamesTheWrongRegistryForItsSeam} is indistinguishable from a method that
     * returns an empty list unconditionally.
     */
    @Test
    void theWrongKindDetectorActuallyFires() {
        final List<String> flagged = wrongKindRows(Map.of("Mining", Set.of("Coal")));
        assertFalse(flagged.isEmpty(),
                "the wrong-kind detector did not flag `Coal` under the block-keyed Mining section; "
                        + "coal is an item and not a block, so this is the one case it exists for");

        // ...and it must NOT fire on the legitimate shapes, or it would report band drift as a bug.
        assertTrue(wrongKindRows(Map.of("Mining", Set.of("Coal_Ore"))).isEmpty(),
                "a genuine block row was flagged");
        assertTrue(wrongKindRows(Map.of("Mining", Set.of("Firefly_Bush_Not_A_Real_Id"))).isEmpty(),
                "a row that resolves as NEITHER kind was flagged — that is band drift on an older "
                        + "version, not a wrong-kind defect, and flagging it fails every old band");
    }

    // ------------------------------------------------------------------ (2) the both-names rule

    @Test
    void everyRenamedObjectIsCoveredUnderWhateverThisVersionCallsIt(@TempDir Path dataFolder) {
        final GeneralConfig config = new GeneralConfig(dataFolder);
        final List<String> gaps = new ArrayList<>();
        int checked = 0;

        for (Covered covered : MUST_BE_COVERED) {
            final PrimarySkillType skill =
                    PrimarySkillType.valueOf(covered.section().toUpperCase(Locale.ROOT));
            for (String path : covered.candidates()) {
                final boolean existsHere = BLOCK.equals(covered.kind())
                        ? Materials.isBlock(path)
                        : Materials.isItem(path);
                if (!existsHere) {
                    continue;   // not this version's spelling; nothing is owed
                }
                checked++;
                final String key = ConfigStringUtils.getMaterialConfigString(path);
                if (!config.getDoubleDropsEnabled(skill, key)) {
                    gaps.add("Bonus_Drops." + covered.section() + "." + key + " — " + covered.why());
                }
            }
        }

        // Anti-vacuity: if no candidate resolved, the loop asserted nothing at all.
        assertTrue(checked > 0,
                "no candidate id in MUST_BE_COVERED exists on this Minecraft version — the table is "
                        + "stale or the registry did not load, and a pass here means nothing");
        assertTrue(gaps.isEmpty(),
                "config.yml does not cover " + gaps.size() + " object(s) under the name this "
                        + "Minecraft version actually uses. The skill pays XP for them and grants no "
                        + "bonus drop:\n  " + String.join("\n  ", gaps));
    }

    /**
     * The coverage check must be able to fail. An id that certainly exists and is certainly not in
     * any bonus-drop table has to come back uncovered — otherwise the assertion above passes because
     * {@code getDoubleDropsEnabled} answers {@code true} for everything.
     */
    @Test
    void theCoverageCheckActuallyFires(@TempDir Path dataFolder) {
        final GeneralConfig config = new GeneralConfig(dataFolder);
        assertTrue(Materials.isBlock("bedrock"), "bedrock is missing — pick another control block");
        assertFalse(
                config.getDoubleDropsEnabled(PrimarySkillType.MINING,
                        ConfigStringUtils.getMaterialConfigString("bedrock")),
                "getDoubleDropsEnabled answered true for bedrock, which no bonus-drop table lists; "
                        + "the coverage assertion above would pass for any id at all");
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Flags rows naming the registry OPPOSITE to their section's seam.
     *
     * <p>⚠️ Deliberately silent about a row that resolves as neither kind. On {@code 1.21} that
     * describes {@code Firefly_Bush} and eleven others which are correct rows for a newer band, and
     * a check that flagged them would be red on every old band for rows that are all right.
     */
    private static List<String> wrongKindRows(Map<String, Set<String>> sections) {
        final List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : sections.entrySet()) {
            final String kind = BONUS_DROP_KIND.get(entry.getKey());
            if (kind == null) {
                continue;
            }
            for (String row : entry.getValue()) {
                final boolean isItem = Materials.isItem(row);
                final boolean isBlock = Materials.isBlock(row);
                if (BLOCK.equals(kind) && isItem && !isBlock) {
                    wrong.add("Bonus_Drops." + entry.getKey() + "." + row
                            + " is an ITEM; that seam is keyed on the broken BLOCK");
                } else if (ITEM.equals(kind) && isBlock && !isItem) {
                    wrong.add("Bonus_Drops." + entry.getKey() + "." + row
                            + " is a BLOCK; that seam is keyed on the resulting ITEM");
                }
            }
        }
        return wrong;
    }

    /**
     * The shipped {@code Bonus_Drops} tables, read from the bundled resource.
     *
     * <p>Read off the classpath rather than from {@code src/main/resources} on disk: the resource is
     * a declared Gradle input, so editing it re-runs this test. A {@code Path.of("src", ...)} read is
     * not, which is how the two doc guards ended up silently skipped on a docs-only change.
     */
    private static Map<String, Set<String>> bonusDropSections() {
        final Map<String, Set<String>> out = new LinkedHashMap<>();
        try (InputStream in = ConfigLoader.class.getResourceAsStream("/config.yml")) {
            if (in == null) {
                return out;
            }
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(in);
            for (String section : BONUS_DROP_KIND.keySet()) {
                final YamlConfiguration node =
                        yaml.getConfigurationSection("Bonus_Drops." + section);
                if (node != null) {
                    out.put(section, node.getKeys(false));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("could not read the bundled config.yml", e);
        }
        return out;
    }
}
