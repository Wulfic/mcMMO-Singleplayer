package com.gmail.nossr50.config.experience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.config.YamlConfiguration;
import com.gmail.nossr50.datatypes.mobs.MobOrigin;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.util.skills.SkillTools;
import com.gmail.nossr50.util.text.StringUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves that every {@code ExploitFix} getter reads the key the shipped {@code experience.yml}
 * actually writes.
 *
 * <p><b>Why this exists.</b> {@link ExperienceConfig#getCombatHPCeiling()} read
 * {@code ExploitFix.Combat.XPCeiling.HP_Modifier_Limit} (legacy's name) while the bundled
 * {@code experience.yml} has always shipped {@code Damage_Limit}. The two never met: the documented
 * key was read by nobody, and the getter silently fell back to its hard-coded {@code 100} forever.
 * Editing the one knob the file offers you did nothing at all, for the whole life of the port.
 *
 * <p><b>Why nothing caught it.</b> Every existing guard passes on a mismatched pair:
 * <ul>
 *   <li>{@code McMMOSettingsTest} proves a <em>catalogue</em> key exists in the yml — it says nothing
 *       about which key the Java reader asks for, and this key was not in the catalogue anyway;</li>
 *   <li>a getter test that asserts the shipped value passes <em>because</em> the fallback default and
 *       the shipped value agree — which is exactly the case here (both {@code 100}).</li>
 * </ul>
 * So the test has to write a value that is <b>not</b> the default, at the <b>shipped</b> path, and
 * demand the getter return it. A reader pointed at any other key answers with its fallback and the
 * assertion fails.
 *
 * @see <a href="https://github.com/Wulfic/mcMMO-Singleplayer/issues/9">GitHub #9</a>
 */
class ExperienceConfigKeyAgreementTest {

    /**
     * One gate: the path the shipped yml writes, a value that is deliberately not that key's
     * default, and the getter that must report it back.
     */
    private record Gate(String path, Object nonDefaultValue,
            Function<ExperienceConfig, Object> getter) {}

    /**
     * Every {@code ExploitFix} gate, with a distinct non-default probe value each.
     *
     * <p>Booleans are probed with the negation of their shipped default; a getter reading the wrong
     * key returns its own fallback, which is the shipped default, so the assertion catches it.
     * Numbers use values no default in this file uses, so a getter that happens to read a
     * <em>different</em> numeric key is caught too.
     *
     * <p>{@code ExploitFix.Pistons} ({@code isPistonExploitPrevented}) is deliberately absent: it
     * ships in no yml and has no caller <b>in legacy either</b> — a faithful port of an upstream
     * dead getter, not a gate. Giving it a probe here would demand we invent a key for it.
     */
    private static List<Gate> gates() {
        final List<Gate> gates = new ArrayList<>();
        gates.add(new Gate("ExploitFix.SnowGolemExcavation", false,
                ExperienceConfig::isSnowExploitPrevented));
        gates.add(new Gate("ExploitFix.EndermanEndermiteFarms", false,
                ExperienceConfig::isEndermanEndermiteFarmingPrevented));
        gates.add(new Gate("ExploitFix.PistonCheating", false,
                ExperienceConfig::isPistonCheatingPrevented));
        gates.add(new Gate("ExploitFix.UnsafeEnchantments", true,
                ExperienceConfig::allowUnsafeEnchantments));
        gates.add(new Gate("ExploitFix.COTWBreeding", false,
                ExperienceConfig::isCOTWBreedingPrevented));
        gates.add(new Gate("ExploitFix.PreventArmorStandInteraction", false,
                ExperienceConfig::isArmorStandInteractionPrevented));
        gates.add(new Gate("ExploitFix.PreventMannequinInteraction", false,
                ExperienceConfig::isMannequinInteractionPrevented));
        gates.add(new Gate("ExploitFix.Fishing", false,
                ExperienceConfig::isFishingExploitingPrevented));
        gates.add(new Gate("ExploitFix.Movement", false,
                ExperienceConfig::isMovementExploitingPrevented));
        gates.add(new Gate("ExploitFix.TreeFellerReducedXP", false,
                ExperienceConfig::isTreeFellerXPReduced));
        gates.add(new Gate("ExploitFix.LavaStoneAndCobbleFarming", false,
                ExperienceConfig::preventStoneLavaFarming));
        gates.add(new Gate("ExploitFix.LimitTallPlantFarming", false,
                ExperienceConfig::limitXPOnTallPlants));
        gates.add(new Gate("ExploitFix.PlacedBlocks", false,
                ExperienceConfig::isPlacedBlockTrackingEnabled));
        gates.add(new Gate("ExploitFix.Combat.XPCeiling.Enabled", false,
                ExperienceConfig::useCombatHPCeiling));
        // The key that started all of this.
        gates.add(new Gate("ExploitFix.Combat.XPCeiling.Damage_Limit", 55,
                ExperienceConfig::getCombatHPCeiling));
        gates.add(new Gate("ExploitFix.Stealth.Require_Movement_Input", false,
                ExperienceConfig::isSneakInputRequired));
        gates.add(new Gate("ExploitFix.Unarmored.Require_Living_Attacker", false,
                ExperienceConfig::isUnarmoredLivingAttackerRequired));
        gates.add(new Gate("ExploitFix.Unarmored.Max_Awards_Per_Attacker", 7,
                ExperienceConfig::getUnarmoredMaxAwardsPerAttacker));
        gates.add(new Gate("ExploitFix.Cooking.Max_Cooks_Per_Hour", 137,
                ExperienceConfig::getCookingMaxCooksPerHour));
        gates.add(new Gate("ExploitFix.Husbandry.Harvest_Cooldown_Seconds", 77,
                ExperienceConfig::getHusbandryHarvestCooldownSeconds));
        gates.add(new Gate("ExploitFix.Husbandry.Breed_Xp_Awards_Per_Window", 3,
                ExperienceConfig::getHusbandryBreedXpAwardsPerWindow));
        gates.add(new Gate("ExploitFix.Husbandry.Breed_Xp_Award_Window_Seconds", 17,
                ExperienceConfig::getHusbandryBreedXpAwardWindowSeconds));
        gates.add(new Gate("Fishing_ExploitFix_Options.MoveRange", 7,
                ExperienceConfig::getFishingExploitingOptionMoveRange));
        gates.add(new Gate("Fishing_ExploitFix_Options.OverFishLimit", 17,
                ExperienceConfig::getFishingExploitingOptionOverFishLimit));
        // The spawn-origin multipliers. Not under ExploitFix, but they are anti-farm gates by any
        // other name, and all four were dead in exactly the same way until GitHub #9.
        gates.add(new Gate("Experience_Formula.Mobspawners.Multiplier", 0.25D,
                ExperienceConfig::getSpawnedMobXpMultiplier));
        gates.add(new Gate("Experience_Formula.Eggs.Multiplier", 0.35D,
                ExperienceConfig::getEggXpMultiplier));
        gates.add(new Gate("Experience_Formula.Nether_Portal.Multiplier", 0.45D,
                ExperienceConfig::getNetherPortalXpMultiplier));
        gates.add(new Gate("Experience_Formula.Breeding.Multiplier", 0.55D,
                ExperienceConfig::getBredMobXpMultiplier));
        // Diminished returns. Also not under ExploitFix, also an anti-farm gate by any other name,
        // and also dead until 2026-08-06 — the scheduled task feeding it had been running since
        // Phase 11 with no consumer. The per-skill Threshold sub-table is built by concatenation and
        // cannot be probed from a fixed path, so DiminishedReturnsThresholdCatalogueTest covers it.
        gates.add(new Gate("Diminished_Returns.Enabled", true,
                ExperienceConfig::getDiminishedReturnsEnabled));
        gates.add(new Gate("Diminished_Returns.Time_Interval", 23,
                ExperienceConfig::getDiminishedReturnsTimeInterval));
        // A float probe, exactly representable, because the getter narrows to float — a Double probe
        // would fail the equals() on the box type rather than on the value.
        gates.add(new Gate("Diminished_Returns.Guaranteed_Minimum_Percentage", 0.375F,
                ExperienceConfig::getDiminishedReturnsCap));
        return gates;
    }

    /**
     * Each {@link MobOrigin} must resolve to the multiplier its own config key holds.
     *
     * <p>The four getters above can each read the right key and the feature still be wrong, because
     * what {@code processCombatXP} actually calls is
     * {@link ExperienceConfig#getMobOriginXpMultiplier}, and a switch arm pointing at the wrong
     * neighbour is invisible to every other assertion here — every value would still be "a number
     * that was read from the file". Distinct probe values per key are what make the mapping testable
     * at all.
     */
    @Test
    void eachMobOriginResolvesToItsOwnMultiplier(@TempDir Path dataFolder) throws IOException {
        writeProbeConfig(dataFolder, gates());
        final ExperienceConfig config = new ExperienceConfig(dataFolder);

        assertEquals(0.25D, config.getMobOriginXpMultiplier(MobOrigin.SPAWNER));
        assertEquals(0.35D, config.getMobOriginXpMultiplier(MobOrigin.PLAYER_PLACED));
        assertEquals(0.45D, config.getMobOriginXpMultiplier(MobOrigin.STRUCTURE));
        assertEquals(0.55D, config.getMobOriginXpMultiplier(MobOrigin.BRED));
        // A naturally spawned mob is never scaled, and reads no key at all.
        assertEquals(1.0D, config.getMobOriginXpMultiplier(MobOrigin.NATURAL));
        // UNKNOWN pays in full on purpose (see the getter's javadoc): XP is a rate, and only the
        // permanent thing -- Hunter mastery -- gets the fail-closed treatment.
        assertEquals(1.0D, config.getMobOriginXpMultiplier(MobOrigin.UNKNOWN));
    }

    @Test
    void everyExploitFixGetterReadsTheKeyTheShippedYamlWrites(@TempDir Path dataFolder)
            throws IOException {
        final List<Gate> gates = gates();
        writeProbeConfig(dataFolder, gates);

        final ExperienceConfig config = new ExperienceConfig(dataFolder);

        for (Gate gate : gates) {
            assertEquals(gate.nonDefaultValue(), gate.getter().apply(config),
                    "getter for " + gate.path() + " did not read the value written at that path — "
                            + "it is reading some other key and silently answering with its own "
                            + "hard-coded default");
        }
    }

    /**
     * The other half, and the one the {@code Damage_Limit} bug needed: every gate this test probes
     * must be a key the bundled {@code experience.yml} actually ships. Without it, a getter and a
     * probe could agree on a path that reaches no player's config file — green, and still nothing
     * a player edits has any effect.
     */
    @Test
    void everyProbedKeyShipsInTheBundledDefault() throws IOException {
        final InputStream in = getClass().getResourceAsStream("/experience.yml");
        assertNotNull(in, "bundled experience.yml missing from the test classpath");
        final YamlConfiguration bundled = YamlConfiguration.loadConfiguration(in);

        for (Gate gate : gates()) {
            assertTrue(bundled.contains(gate.path()),
                    "the bundled experience.yml does not ship " + gate.path()
                            + " — a gate nobody can configure");
        }
    }

    /**
     * {@code Experience_Formula.Skill_Multiplier} must hold exactly the skills that can earn XP:
     * every non-child {@link PrimarySkillType}, and <b>no child skill</b>.
     *
     * <p><b>Why this exists (TODO.md item 4(e)).</b> The table shipped an {@code Agility: 1.0} row,
     * and {@code McMMOSettings} offered it a slider on the XP Multipliers tab. Agility is a
     * <em>child</em> skill: its level is the mean of Parkour/Swimming/Flying and it earns no XP of
     * its own, so both {@code beginXpGain} and {@code applyXpGain} split the gain to those parents
     * and return before {@code modifyXpGain} ever consults a multiplier. The row could never be
     * read and the slider could never do anything.
     *
     * <p>🔑 <b>The tell was the sibling asymmetry</b>: {@code Salvage} and {@code Smelting} are also
     * child skills and were correctly absent from both the table and the array. When a hand-kept
     * roster lists one member its siblings don't, the odd one out is the bug — the same tell that
     * exposed Herbalism's Verdant Bounty (item 1.2).
     *
     * <p>Both directions are asserted because a one-directional guard is what let this survive:
     * {@code McMMOSettingsTest} proves every shipped key is offered a slider, which is exactly the
     * check a dead row passes. Nothing asked whether the row could be <em>read</em>.
     */
    @Test
    void skillMultiplierHoldsEveryEarningSkillAndNoChildSkill() throws IOException {
        final InputStream in = getClass().getResourceAsStream("/experience.yml");
        assertNotNull(in, "bundled experience.yml missing from the test classpath");
        final YamlConfiguration bundled = YamlConfiguration.loadConfiguration(in);

        final List<String> missing = new ArrayList<>();
        final List<String> unreadable = new ArrayList<>();
        for (PrimarySkillType skill : PrimarySkillType.values()) {
            final String path = "Experience_Formula.Skill_Multiplier."
                    + StringUtils.getCapitalized(skill.name());
            if (SkillTools.isChildSkill(skill)) {
                if (bundled.contains(path)) {
                    unreadable.add(path);
                }
            } else if (!bundled.contains(path)) {
                missing.add(path);
            }
        }

        assertTrue(unreadable.isEmpty(),
                "child skills earn no XP, so these multipliers can never be read: " + unreadable);
        assertTrue(missing.isEmpty(),
                "these skills earn XP but have no multiplier row: " + missing);
    }

    /**
     * Writes a partial {@code experience.yml} holding only the probe values. {@code ConfigLoader}'s
     * {@code copyMissingDefaults} back-fills every other key from the bundled resource, and leaves
     * present keys alone — which is precisely the behaviour being relied on here.
     */
    private static void writeProbeConfig(Path dataFolder, List<Gate> gates) throws IOException {
        Files.createDirectories(dataFolder);

        // Build the nested shape from the dotted paths rather than hand-writing section headers, so
        // adding a gate at a new depth needs no changes here. LinkedHashMap keeps sibling order
        // stable, which makes a failure diff readable.
        final Map<String, Object> tree = new LinkedHashMap<>();
        for (Gate gate : gates) {
            final String[] parts = gate.path().split("\\.");
            Map<String, Object> node = tree;
            for (int i = 0; i < parts.length - 1; i++) {
                final Object child = node.computeIfAbsent(parts[i], k -> new LinkedHashMap<>());
                if (!(child instanceof Map)) {
                    throw new IllegalStateException(
                            "two probes disagree about whether " + parts[i] + " is a section");
                }
                @SuppressWarnings("unchecked")
                final Map<String, Object> next = (Map<String, Object>) child;
                node = next;
            }
            node.put(parts[parts.length - 1], gate.nonDefaultValue());
        }

        final StringBuilder yaml = new StringBuilder();
        appendTree(yaml, tree, 0);
        Files.writeString(dataFolder.resolve("experience.yml"), yaml.toString(),
                StandardCharsets.UTF_8);
    }

    /** Emits one level of the probe tree as YAML, four spaces per level (this file's style). */
    private static void appendTree(StringBuilder yaml, Map<String, Object> node, int depth) {
        for (Map.Entry<String, Object> entry : node.entrySet()) {
            yaml.append("    ".repeat(depth)).append(entry.getKey()).append(':');
            if (entry.getValue() instanceof Map<?, ?> section) {
                yaml.append('\n');
                @SuppressWarnings("unchecked")
                final Map<String, Object> typed = (Map<String, Object>) section;
                appendTree(yaml, typed, depth + 1);
            } else {
                yaml.append(' ').append(entry.getValue()).append('\n');
            }
        }
    }
}
