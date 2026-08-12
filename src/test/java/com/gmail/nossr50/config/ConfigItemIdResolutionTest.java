package com.gmail.nossr50.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.config.skills.repair.RepairConfig;
import com.gmail.nossr50.config.skills.salvage.SalvageConfig;
import com.gmail.nossr50.config.treasure.FishingTreasureConfig;
import com.gmail.nossr50.config.treasure.TreasureConfig;
import com.gmail.nossr50.datatypes.treasure.Treasure;
import com.gmail.nossr50.platform.Materials;
import com.gmail.nossr50.util.McTestRegistries;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TODO 5.5 / risk R5 — the per-band ship gate for item-id drift.
 *
 * <p>~4,200 lines of item-keyed YAML ship in the jar and the mod supports a band of Minecraft
 * versions, so a shipped row naming an item that version does not have is expected. The requirement
 * is that such a row is <b>dropped and reported</b>, never dropped silently. This asserts exactly
 * that, and it is written to hold on <em>every</em> band rather than only the one it was written on.
 *
 * <p><b>⚠️⚠️ The obvious test — "assert every shipped id resolves" — is both vacuous here and wrong
 * everywhere else.</b> On the newest band every id resolves, so it passes with the entire pruning
 * mechanism deleted; and on {@code mc/1.21.5} it would fail for 43 ids that are absent by design.
 * It asserts the wrong property in the one direction where it can fail. This project has shipped
 * five vacuous guards already ({@code SkillAvailability}'s first wiring test being the most recent);
 * the fix each time was to assert the property, then prove the assertion can fail.
 *
 * <p>Three properties instead, none of which a missing gate satisfies:
 * <ol>
 *   <li><b>Post-prune invariant.</b> Nothing that survived into a live table names an item this
 *       version lacks. Fails the moment pruning stops working, on any band.</li>
 *   <li><b>Anti-vacuity floor.</b> The tables are actually populated — otherwise a config that
 *       failed to load satisfies (1) trivially by being empty.</li>
 *   <li><b>The report fires.</b> Fed a row naming a deliberately non-existent item, the loader drops
 *       it <em>and</em> records it. This is the "silent skips are not acceptable" half, and it is
 *       provable on every band, including one where nothing is genuinely absent.</li>
 * </ol>
 *
 * <p>Runs under the {@code fabric-loader-junit} harness: it asks the real registry, which is the only
 * authority on what this Minecraft version has. The cross-band counterpart is
 * {@code scripts/config-id-audit.py}, which answers the same question for a band not yet cut.
 */
class ConfigItemIdResolutionTest {

    /** An id no Minecraft version has or ever will; the namespace makes that unambiguous. */
    private static final String NEVER_AN_ITEM = "mcmmo_test_absent_item";

    @BeforeAll
    static void bootstrap() {
        McTestRegistries.bootstrap();
    }

    /**
     * The registry must really be populated. Without this the whole class is vacuous in the most
     * convincing way available: an empty registry answers "no such item" for everything, so property
     * (1) would hold because every table pruned itself to nothing.
     */
    @BeforeAll
    static void registryIsLive() {
        assertTrue(Materials.itemRegistryIsPopulated(),
                "registry not populated — every assertion below would pass for the wrong reason");
    }

    // ------------------------------------------------------------------ (1) + (2)

    @Test
    void survivingTreasuresAllResolve(@TempDir Path dir) {
        final TreasureConfig config = new TreasureConfig(dir);
        config.pruneUnavailableEntries();

        final List<Treasure> all = new ArrayList<>();
        config.excavationMap.values().forEach(all::addAll);
        config.hylianMap.values().forEach(all::addAll);
        config.husbandryMap.values().forEach(all::addAll);

        assertFalse(all.isEmpty(), "bundled treasures.yml should yield treasures");
        for (Treasure treasure : all) {
            final String id = treasure.getDrop().getMaterialId();
            assertTrue(Materials.isItem(id),
                    "treasure '" + id + "' survived pruning but has no item on this Minecraft"
                            + " version — it would be rolled and drop nothing");
        }
    }

    @Test
    void survivingFishingRewardsAllResolve(@TempDir Path dir) {
        final FishingTreasureConfig config = new FishingTreasureConfig(dir);
        config.pruneUnavailableEntries();

        final List<Treasure> all = new ArrayList<>();
        config.fishingRewards.values().forEach(all::addAll);
        config.shakeMap.values().forEach(all::addAll);

        assertFalse(all.isEmpty(), "bundled fishing_treasures.yml should yield rewards");
        for (Treasure treasure : all) {
            final String id = treasure.getDrop().getMaterialId();
            assertTrue(Materials.isItem(id),
                    "fishing reward '" + id + "' survived pruning but has no item on this"
                            + " Minecraft version — the catch would yield nothing");
        }
    }

    @Test
    void everyLoadedRepairableAndSalvageableResolves(@TempDir Path dir) throws IOException {
        final List<String> repairIds = new RepairConfig(dir).getLoadedRepairables().stream()
                .map(r -> r.getItemMaterial()).toList();
        final List<String> salvageIds =
                new SalvageConfig(Files.createDirectory(dir.resolve("salvage")))
                        .getLoadedSalvageables().stream()
                        .map(s -> s.getItemMaterial()).toList();

        assertFalse(repairIds.isEmpty(), "bundled repair.vanilla.yml should yield repairables");
        assertFalse(salvageIds.isEmpty(), "bundled salvage.vanilla.yml should yield salvageables");
        for (String id : repairIds) {
            assertTrue(Materials.isItem(id), "repairable '" + id + "' has no item here");
        }
        for (String id : salvageIds) {
            assertTrue(Materials.isItem(id), "salvageable '" + id + "' has no item here");
        }
    }

    /**
     * The XP tables are reported rather than pruned (an unmatched row is inert), so the assertion is
     * that the pass runs and does not throw — the report itself is a log line. Kept because a
     * registry probe added to this config's load path is what poisoned 351 tests once already; if
     * that regresses, this is the test that names the class.
     */
    @Test
    void experienceConfigReportPassRuns(@TempDir Path dir) {
        new ExperienceConfig(dir).reportUnresolvableRows();
    }

    // ------------------------------------------------------------------ (3) the report fires

    @Test
    void anAbsentTreasureItemIsDroppedAndRecorded(@TempDir Path dir) throws IOException {
        writeTreasureNaming(dir, NEVER_AN_ITEM);

        final TreasureConfig config = new TreasureConfig(dir);
        assertTrue(containsDrop(config, NEVER_AN_ITEM),
                "precondition: the fabricated row must actually load, or this test proves nothing");

        config.pruneUnavailableEntries();

        assertFalse(containsDrop(config, NEVER_AN_ITEM),
                "a treasure naming a non-existent item must not survive into a drop pool");
        assertTrue(config.getSkips().bySection().getOrDefault("Excavation", List.of())
                        .contains(NEVER_AN_ITEM),
                "dropping it is not enough — it must be recorded so the summary line names it");
        assertTrue(config.getSkips().count() >= 1, "the skip must be counted for the summary");
    }

    /**
     * The other direction, and the one a positive-only test cannot see: a treasure whose item DOES
     * exist must survive and must not be recorded. Without this, a prune that drops everything
     * passes the test above just as convincingly.
     */
    @Test
    void aPresentTreasureItemSurvivesAndIsNotRecorded(@TempDir Path dir) throws IOException {
        writeTreasureNaming(dir, "diamond");

        final TreasureConfig config = new TreasureConfig(dir);
        config.pruneUnavailableEntries();

        assertTrue(containsDrop(config, "diamond"),
                "diamond exists on every supported version and must not be pruned");
        assertFalse(config.getSkips().bySection().getOrDefault("Excavation", List.of())
                        .contains("diamond"),
                "an item that resolves must not be reported as skipped");
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Write a treasures.yml carrying one Excavation entry for {@code materialId}. ConfigLoader
     * back-fills absent keys from the bundled default afterwards, so the shipped rows load too and
     * the fixture stays realistic rather than a lone hand-rolled entry.
     */
    private static void writeTreasureNaming(Path dir, String materialId) throws IOException {
        Files.writeString(dir.resolve(TreasureConfig.FILENAME),
                "Excavation:\n"
                        + "    " + materialId.toUpperCase(java.util.Locale.ROOT) + ":\n"
                        + "        Amount: 1\n"
                        + "        XP: 50\n"
                        + "        Drop_Chance: 1.0\n"
                        + "        Level_Requirement:\n"
                        + "            Standard_Mode: 1\n"
                        + "            Retro_Mode: 10\n"
                        + "        Drops_From: [Dirt]\n");
    }

    private static boolean containsDrop(TreasureConfig config, String materialId) {
        return config.excavationMap.values().stream()
                .flatMap(List::stream)
                .anyMatch(t -> t.getDrop().getMaterialId().equals(materialId));
    }
}
