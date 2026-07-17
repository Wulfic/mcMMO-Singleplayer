package com.gmail.nossr50.skills.taming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.RankConfig;
import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformLivingEntity;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.util.player.UserManager;
import org.mockito.ArgumentMatchers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the Taming numeric cores (Phase 10.3) against the real bundled configs.
 *
 * <p>RetroMode is on by default (skillranks.yml), so Taming sub-skills unlock at these Taming levels:
 * BeastLore 1, CallOfTheWild 1, EnvironmentallyAware 100, Gore 150, Pummel/FastFoodService 200,
 * ThickFur 250, HolyHound 350, ShockProof 500, SharpenedClaws 750. advanced.yml default modifiers:
 * Gore 2.0, SharpenedClaws bonus 2.0, ThickFur 2.0, ShockProof 6.0.
 */
class TamingManagerTest {

    private McMMOPlayer mmoPlayer;
    private PlatformPlayer platformPlayer;
    private TamingManager tamingManager;
    private Path dataFolder;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        this.dataFolder = dataFolder;
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));
        McMMOMod.setExperienceConfig(new ExperienceConfig(dataFolder));

        platformPlayer = mock(PlatformPlayer.class);
        when(platformPlayer.getUniqueId())
                .thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));

        mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        UserManager.track(mmoPlayer);

        tamingManager = new TamingManager(mmoPlayer);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
        McMMOMod.setAdvancedConfig(null);
        McMMOMod.setExperienceConfig(null);
        UserManager.clearAll();
    }

    private void atTamingLevel(int level) {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.TAMING)).thenReturn(level);
    }

    @Test
    void gatesUnlockAtTheirRetroModeLevels() {
        atTamingLevel(0);
        assertFalse(tamingManager.canUseGore(), "Gore not yet unlocked at level 0");
        assertFalse(tamingManager.canUseThickFur(), "Thick Fur not yet unlocked at level 0");
        assertFalse(tamingManager.canUseSharpenedClaws(), "Sharpened Claws not yet at level 0");

        atTamingLevel(150);
        assertTrue(tamingManager.canUseGore(), "Gore unlocks at 150 (RetroMode)");
        assertFalse(tamingManager.canUseThickFur(), "Thick Fur still locked (unlocks at 250)");

        atTamingLevel(250);
        assertTrue(tamingManager.canUseThickFur(), "Thick Fur unlocks at 250");

        atTamingLevel(750);
        assertTrue(tamingManager.canUseSharpenedClaws(), "Sharpened Claws unlocks at 750");
    }

    @Test
    void holyHoundUnlocksOffEnvironmentallyAwareRankLadder() {
        atTamingLevel(99);
        assertFalse(tamingManager.canUseHolyHound(), "Environmentally Aware ladder unlocks at 100");
        atTamingLevel(100);
        assertTrue(tamingManager.canUseHolyHound(),
                "Holy Hound is gated on the Environmentally Aware unlock level (legacy quirk)");
        // proves it does NOT wait for the Holy Hound (350) ladder
    }

    @Test
    void goreReturnsOnlyTheExtraDamage() {
        // default Gore modifier 2.0 -> (10 * 2) - 10 = 10 extra damage
        assertEquals(10.0, tamingManager.gore(10.0), 1.0e-9);
        assertEquals(0.0, tamingManager.gore(0.0), 1.0e-9);
    }

    @Test
    void sharpenedClawsReturnsConfiguredBonus() {
        assertEquals(2.0, tamingManager.sharpenedClaws(), 1.0e-9);
    }

    @Test
    void thickFurAndShockProofDivideByTheirModifiers() {
        assertEquals(5.0, tamingManager.processThickFur(10.0), 1.0e-9, "Thick Fur modifier 2.0");
        assertEquals(2.0, tamingManager.processShockProof(12.0), 1.0e-9, "Shock Proof modifier 6.0");
    }

    @Test
    void awardTamingXpLooksUpPerEntityXpAndGainsIt() {
        // experience.yml default: Taming.Animal_Taming.Wolf = 250.
        tamingManager.awardTamingXP("Wolf");
        verify(mmoPlayer).beginXpGain(PrimarySkillType.TAMING, 250f, XPGainReason.PVE,
                XPGainSource.SELF);
    }

    @Test
    void awardTamingXpIsNoOpForEntityWithNoConfiguredXp() {
        // An entity type with no Taming.Animal_Taming entry resolves to 0 XP -> no gain.
        tamingManager.awardTamingXP("Not_A_Real_Animal");
        verify(mmoPlayer, never()).beginXpGain(ArgumentMatchers.any(), ArgumentMatchers.anyFloat(),
                ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    /**
     * Re-points {@link AdvancedConfig} at a data folder whose {@code advanced.yml} overrides only the
     * Fast Food Service chance; {@code ConfigLoader} back-fills every other key from the bundled
     * default. Fast Food Service is the one STATIC_CONFIGURABLE Taming roll, and
     * {@code Probability.isSuccessfulRoll} is {@code value >= nextDouble(1.0)} over {@code [0, 1)} —
     * so a chance of 100 always succeeds and a chance of 0 never does, making the roll deterministic
     * without injecting an RNG.
     */
    private void withFastFoodChance(double chance) throws IOException {
        final Path overrideFolder = dataFolder.resolve("ffs-" + chance);
        Files.createDirectories(overrideFolder);
        Files.writeString(overrideFolder.resolve("advanced.yml"), """
                Skills:
                    Taming:
                        FastFoodService:
                            Chance: %s
                """.formatted(chance));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(overrideFolder));
    }

    private PlatformLivingEntity wolfAt(float health, float maxHealth) {
        PlatformLivingEntity wolf = mock(PlatformLivingEntity.class);
        when(wolf.getHealth()).thenReturn(health);
        when(wolf.getMaxHealth()).thenReturn(maxHealth);
        return wolf;
    }

    @Test
    void fastFoodServiceHealsTheWolfForTheDamageItDealt() throws IOException {
        withFastFoodChance(100.0);
        PlatformLivingEntity wolf = wolfAt(10f, 20f);

        tamingManager.fastFoodService(wolf, 5.0);

        verify(wolf).setHealth(15f);
    }

    @Test
    void fastFoodServiceClampsTheHealToMaxHealth() throws IOException {
        withFastFoodChance(100.0);
        PlatformLivingEntity wolf = wolfAt(18f, 20f);

        tamingManager.fastFoodService(wolf, 5.0);

        verify(wolf).setHealth(20f);
    }

    @Test
    void fastFoodServiceLeavesAFullHealthWolfAlone() throws IOException {
        // Pins legacy's `health < maxHealth` guard: a full-health wolf is never written to at all,
        // rather than being redundantly re-set to its own max.
        withFastFoodChance(100.0);
        PlatformLivingEntity wolf = wolfAt(20f, 20f);

        tamingManager.fastFoodService(wolf, 5.0);

        verify(wolf, never()).setHealth(ArgumentMatchers.anyFloat());
    }

    @Test
    void fastFoodServiceDoesNothingWhenTheRollFails() throws IOException {
        withFastFoodChance(0.0);
        PlatformLivingEntity wolf = wolfAt(10f, 20f);

        tamingManager.fastFoodService(wolf, 5.0);

        verify(wolf, never()).setHealth(ArgumentMatchers.anyFloat());
    }

    @Test
    void holyHoundHealsTheWolfForTheIncomingDamageAndClamps() {
        // Holy Hound has no RNG gate and no `health < maxHealth` guard — the min clamp is the whole
        // body, exactly as legacy wrote it.
        PlatformLivingEntity hurt = wolfAt(10f, 20f);
        tamingManager.processHolyHound(hurt, 5.0);
        verify(hurt).setHealth(15f);

        PlatformLivingEntity nearlyFull = wolfAt(18f, 20f);
        tamingManager.processHolyHound(nearlyFull, 5.0);
        verify(nearlyFull).setHealth(20f);
    }

    // --- Environmentally Aware (teleport arm) -------------------------------
    // The body itself is just the survivability check + teleport; its rank/permission gate lives on
    // the listener (as in legacy). The notification is a no-op under the mocked player (chat
    // notifications default off), so these assert only the teleport behaviour.

    @Test
    void environmentallyAwareTeleportsAWolfToItsOwnerWhenTheHitIsSurvivable() {
        PlatformLivingEntity wolf = wolfAt(20f, 20f);

        tamingManager.processEnvironmentallyAware(wolf, 5.0);

        verify(wolf).teleportTo(platformPlayer);
    }

    @Test
    void environmentallyAwareLeavesAWolfToDieWhenTheHitIsLethal() {
        // Pins legacy's `damage > wolf.getHealth()` skip: a killing blow is not rescued.
        PlatformLivingEntity wolf = wolfAt(4f, 20f);

        tamingManager.processEnvironmentallyAware(wolf, 5.0);

        verify(wolf, never()).teleportTo(ArgumentMatchers.any(PlatformPlayer.class));
    }

    @Test
    void beastLoreGateUnlocksAtLevelOne() {
        atTamingLevel(0);
        assertFalse(tamingManager.canUseBeastLore(), "Beast Lore locked at level 0");
        atTamingLevel(1);
        assertTrue(tamingManager.canUseBeastLore(), "Beast Lore unlocks at level 1 (RetroMode)");
    }

    @Test
    void beastLoreHorseJumpStrengthMatchesTheWikiPolynomial() {
        // raw jump-strength attribute of 0.7 -> ~2.8917 (verbatim wiki polynomial)
        assertEquals(2.8917, TamingManager.beastLoreHorseJumpStrength(0.7), 1.0e-3);
        // a raw value of 0 -> the polynomial's constant term
        assertEquals(-0.343930367, TamingManager.beastLoreHorseJumpStrength(0.0), 1.0e-9);
    }

    // --- Pummel -------------------------------------------------------------
    // Pummel unlocks at Taming level 200 (RetroMode) and rolls the static Skills.Taming.Pummel.Chance,
    // overridden here the same way withFastFoodChance overrides Fast Food Service, so a chance of 100
    // always fires and 0 never does — making the roll deterministic without injecting an RNG.

    private void withPummelChance(double chance) throws IOException {
        final Path overrideFolder = dataFolder.resolve("pummel-" + chance);
        Files.createDirectories(overrideFolder);
        Files.writeString(overrideFolder.resolve("advanced.yml"), """
                Skills:
                    Taming:
                        Pummel:
                            Chance: %s
                """.formatted(chance));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(overrideFolder));
    }

    @Test
    void pummelKnocksTheTargetBackAlongTheWolfLookOnASuccessfulRoll() throws IOException {
        atTamingLevel(200);
        withPummelChance(100.0);
        PlatformLivingEntity target = mock(PlatformLivingEntity.class);
        PlatformLivingEntity wolf = mock(PlatformLivingEntity.class);

        tamingManager.processPummel(target, wolf);

        // Legacy's hardcoded 1.5 knockback, flung along the wolf (not the player).
        verify(target).setVelocityAlongLookDirection(wolf, 1.5D);
    }

    @Test
    void pummelDoesNothingWhenTheRollFails() throws IOException {
        atTamingLevel(200);
        withPummelChance(0.0);
        PlatformLivingEntity target = mock(PlatformLivingEntity.class);
        PlatformLivingEntity wolf = mock(PlatformLivingEntity.class);

        tamingManager.processPummel(target, wolf);

        verify(target, never()).setVelocityAlongLookDirection(
                ArgumentMatchers.any(PlatformLivingEntity.class), ArgumentMatchers.anyDouble());
    }

    @Test
    void pummelLockedBelowItsUnlockDoesNothing() throws IOException {
        atTamingLevel(199); // one below the Pummel unlock — the roll must be skipped entirely
        withPummelChance(100.0);
        PlatformLivingEntity target = mock(PlatformLivingEntity.class);
        PlatformLivingEntity wolf = mock(PlatformLivingEntity.class);

        tamingManager.processPummel(target, wolf);

        verify(target, never()).setVelocityAlongLookDirection(
                ArgumentMatchers.any(PlatformLivingEntity.class), ArgumentMatchers.anyDouble());
    }
}
