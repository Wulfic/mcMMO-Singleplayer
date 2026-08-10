package com.gmail.nossr50.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises the trimmed {@link GeneralConfig} against the real bundled {@code config.yml} on the
 * test classpath, with a temp data folder. Covers the SP-core getters, the String-keyed
 * material/entity/ability lookups, and the Hardcore setters.
 */
class GeneralConfigTest {

    @Test
    void writesDefaultToDiskWhenMissing(@TempDir Path dataFolder) {
        new GeneralConfig(dataFolder);
        assertTrue(Files.exists(dataFolder.resolve("config.yml")));
    }

    @Test
    void readsGeneralAndLevelCapSettings(@TempDir Path dataFolder) {
        final GeneralConfig config = new GeneralConfig(dataFolder);
        assertEquals(10, config.getSaveInterval());
        // Power_Level_Cap default 0 -> unlimited.
        assertEquals(Integer.MAX_VALUE, config.getPowerLevelCap());
        // Per-skill level cap default 0 -> unlimited.
        assertEquals(Integer.MAX_VALUE, config.getLevelCap(PrimarySkillType.MINING));
    }

    @Test
    void milestoneAdvancementDefaults(@TempDir Path dataFolder) {
        final GeneralConfig config = new GeneralConfig(dataFolder);
        // Advancement Plaques support ships on by default with a 100-level round-level bracket.
        assertTrue(config.getMilestoneAdvancementsEnabled());
        assertEquals(100, config.getMilestoneLevelInterval());
    }

    @Test
    void milestoneLevelIntervalIsClampedAboveZero(@TempDir Path dataFolder) {
        final GeneralConfig generalConfig = new GeneralConfig(dataFolder);
        // Even if someone sets a nonsensical 0/negative interval, the getter never returns something
        // that would divide-by-zero in the crossing math. (config is protected in ConfigLoader, which
        // shares this package.)
        generalConfig.config.set("General.Milestone_Advancements.Level_Interval", 0);
        assertTrue(generalConfig.getMilestoneLevelInterval() >= 1);
    }

    /**
     * ⚠️ This used to read three Chimaera Wing values alongside the Tree Feller threshold, and a
     * {@code hardcoreSettersRoundTrip} test sat next to it. Both went with the Hardcore and Items
     * getters in the 2026-08-06 config cull — <b>they were round-tripping features the port does not
     * have</b>, which is how a dead getter earns a passing test and looks maintained.
     */
    @Test
    void readsAbilitySettings(@TempDir Path dataFolder) {
        final GeneralConfig config = new GeneralConfig(dataFolder);
        assertEquals(1000, config.getTreeFellerThreshold());
    }

    @Test
    void lilyPadDoubleDropsAlwaysDisabled(@TempDir Path dataFolder) {
        final GeneralConfig config = new GeneralConfig(dataFolder);
        // The exploit guard short-circuits regardless of config content.
        assertFalse(config.getDoubleDropsEnabled(PrimarySkillType.HERBALISM, "Lily_Pad"));
    }

    @Test
    void greenThumbReplantDefaultsTrueForUnknownCrop(@TempDir Path dataFolder) {
        final GeneralConfig config = new GeneralConfig(dataFolder);
        assertTrue(config.isGreenThumbReplantableCrop("Not_A_Real_Crop"));
    }

    @Test
    void petFollowTeleportDefaultsAreReadFromTheBundledConfig(@TempDir Path dataFolder) {
        // GitHub #2. Both keys are new, so copyMissingDefaults back-fills them into an existing
        // on-disk config for free — but only if they are really in the shipped resource under the
        // paths the getters name. A typo in either would fall through to the hardcoded default and be
        // invisible: the feature would work and the config knob would silently do nothing.
        final GeneralConfig config = new GeneralConfig(dataFolder);
        assertTrue(config.arePetsFollowingTeleports());
        assertEquals(128.0D, config.getPetFollowTeleportRadius());

        // Assert off the reference point: the getter must read the file, not return its default.
        config.config.set("Skills.Taming.Pets_Follow_Teleport", false);
        config.config.set("Skills.Taming.Pets_Follow_Teleport_Radius", 8.0D);
        assertFalse(config.arePetsFollowingTeleports());
        assertEquals(8.0D, config.getPetFollowTeleportRadius());
    }

    @Test
    void anExistingConfigStillHoldingTheOldPetRadiusIsMigrated(@TempDir Path dataFolder)
            throws Exception {
        // ⚠️ GitHub #12 IS A CHANGED DEFAULT, WHICH IS THE ONE KIND OF FIX THAT REACHES NOBODY.
        // copyMissingDefaults back-fills only ABSENT keys, so the reporter — who has run the mod and
        // therefore has `Pets_Follow_Teleport_Radius: 32` on disk — would have been the one player
        // the fix did not reach. This is ConfigRetunes' second customer ever, and config.yml's first,
        // so it is also the first exercise of the version stamp on this file.
        final Path file = dataFolder.resolve("config.yml");
        new GeneralConfig(dataFolder);
        Files.writeString(file, Files.readString(file)
                .replace("Pets_Follow_Teleport_Radius: 128", "Pets_Follow_Teleport_Radius: 32")
                .replace(ConfigRetunes.VERSION_KEY + ": 1", ConfigRetunes.VERSION_KEY + ": 0"));

        assertEquals(128.0D, new GeneralConfig(dataFolder).getPetFollowTeleportRadius(), 0.0001D,
                "an untouched old default must be carried forward");
    }

    @Test
    void aHandTunedPetRadiusSurvivesTheMigration(@TempDir Path dataFolder) throws Exception {
        // The other half of the promise: 64 is neither the old default nor the new one, so somebody
        // typed it on purpose and the file belongs to them.
        final Path file = dataFolder.resolve("config.yml");
        new GeneralConfig(dataFolder);
        Files.writeString(file, Files.readString(file)
                .replace("Pets_Follow_Teleport_Radius: 128", "Pets_Follow_Teleport_Radius: 64")
                .replace(ConfigRetunes.VERSION_KEY + ": 1", ConfigRetunes.VERSION_KEY + ": 0"));

        assertEquals(64.0D, new GeneralConfig(dataFolder).getPetFollowTeleportRadius(), 0.0001D,
                "a deliberately tuned value must never be reverted by a shipped-default change");
    }

    @Test
    void vanillaRepairGettersReadTheirOwnKeys(@TempDir Path dataFolder) {
        // Wiring audit 2026-08-06, item 2.2. getAllowVanillaInventoryRepair and
        // getAllowVanillaAnvilRepair read *each other's* keys, in this port and in legacy
        // (legacy/…/GeneralConfig.java:836-841 — the swap was ported verbatim). Latent only because
        // neither getter has a caller and both keys default to false, so no assertion that reads a
        // getter in isolation can see it. Wire either one to a repair listener and it silently
        // honours the wrong switch.
        //
        // Neither key ships in config.yml, so the probes have to be set: give the two swapped keys
        // OPPOSITE values, which is what makes a crossed read visible at all. A getter reading the
        // wrong key returns the other probe; a getter reading no key at all returns the false
        // default. Both failure modes are caught, and running it in both directions means neither
        // getter can pass by accident.
        final GeneralConfig config = new GeneralConfig(dataFolder);

        config.config.set("Skills.Repair.Allow_Vanilla_Anvil_Repair", true);
        config.config.set("Skills.Repair.Allow_Vanilla_Inventory_Repair", false);
        config.config.set("Skills.Repair.Allow_Vanilla_Grindstone_Repair", true);
        assertTrue(config.getAllowVanillaAnvilRepair());
        assertFalse(config.getAllowVanillaInventoryRepair());
        assertTrue(config.getAllowVanillaGrindstoneRepair());

        config.config.set("Skills.Repair.Allow_Vanilla_Anvil_Repair", false);
        config.config.set("Skills.Repair.Allow_Vanilla_Inventory_Repair", true);
        config.config.set("Skills.Repair.Allow_Vanilla_Grindstone_Repair", false);
        assertFalse(config.getAllowVanillaAnvilRepair());
        assertTrue(config.getAllowVanillaInventoryRepair());
        assertFalse(config.getAllowVanillaGrindstoneRepair());
    }

    @Test
    void superAbilityCooldownTypedOverloadDelegatesToStringKey(@TempDir Path dataFolder) {
        final GeneralConfig config = new GeneralConfig(dataFolder);
        // SuperAbilityType.toString() yields the PascalCase config key (e.g. "Super_Breaker").
        assertEquals(config.getCooldown("Super_Breaker"),
                config.getCooldown(SuperAbilityType.SUPER_BREAKER));
        assertEquals(config.getMaxLength("Super_Breaker"),
                config.getMaxLength(SuperAbilityType.SUPER_BREAKER));
    }
}
