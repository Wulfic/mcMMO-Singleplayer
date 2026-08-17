package com.gmail.nossr50.fabric.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.datatypes.skills.subskills.taming.PetCombatMode;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.locale.LocaleLoader;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The shipped configuration and wording of the pet combat-mode feature.
 *
 * <p>Run against the <b>real bundled {@code config.yml}</b> rather than a mock, for the same reason
 * {@code HerdsmansCallListenerTest} is: the shipped values <em>are</em> the invariant, and mocking
 * the getters would prove they compile while the YAML shipped a collision.
 */
class PetCombatModeConfigTest {

    private GeneralConfig config;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        config = new GeneralConfig(dataFolder);
        McMMOMod.setGeneralConfig(config);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
    }

    // --- the shipped defaults -------------------------------------------------------------------

    @Test
    void theFeatureShipsOn() {
        assertTrue(config.isPetCombatModeEnabled());
    }

    @Test
    void theToggleItemIsTheShippedBone() {
        assertEquals("BONE", config.getPetCombatModeToggleItem());
    }

    /**
     * ⚠️⚠️ Extends the three-way guard in {@code HerdsmansCallListenerTest} to four.
     *
     * <p>The first three collide because they share a {@code UseItemCallback}. This one is on
     * {@code UseEntityCallback} instead, so the collision is not identical — it is worse: a player
     * holding the shared item and right-clicking a pet would fire the use-item active <em>and</em>
     * the stance toggle from a single press, and neither listener could tell that had happened.
     */
    @Test
    void theFourConfigurableTriggerItemsAreAllDistinct() {
        final Set<String> items = Set.of(config.getSecondWindItem(), config.getSmokeBombItem(),
                config.getHerdsmansCallItem(), config.getPetCombatModeToggleItem());
        assertEquals(4, items.size(),
                "the four configurable trigger items must be distinct: " + items);
    }

    /**
     * The scan radius and the chase range ship equal, and that pairing is load-bearing (ruling R-5):
     * a hostile acquired at the scan radius is only worth acquiring if a pet can path that far.
     * Letting the scan out-run the chase produces pets that stand still staring at a target — the
     * exact symptom the reach fix exists to remove.
     */
    @Test
    void theEngageRangeReachesAsFarAsTheAggressiveScan() {
        assertTrue(config.getPetEngageRange() >= config.getPetAggressiveRadius(),
                "a pet must be able to path to anything the sweep can pick for it: engage="
                        + config.getPetEngageRange() + " scan=" + config.getPetAggressiveRadius());
    }

    /**
     * The whole point of the reach fix: the shipped engage range must exceed a wolf's natural
     * {@code FOLLOW_RANGE} of 16. At or below it the modifier is a no-op and every pet still stops
     * dead at 16 blocks, with nothing anywhere reporting a problem.
     */
    @Test
    void theShippedEngageRangeActuallyExceedsAWolfsNaturalFollowRange() {
        assertTrue(config.getPetEngageRange() > 16.0D,
                "an engage range of " + config.getPetEngageRange()
                        + " is at or below a wolf's base FOLLOW_RANGE of 16 — the fix does nothing");
    }

    @Test
    void theSweepIntervalIsAtLeastOneTick() {
        // A zero or negative interval would make the modulo throw or the sweep run every tick.
        assertTrue(config.getPetSweepIntervalTicks() >= 1);
        assertEquals(20, config.getPetSweepIntervalTicks());
    }

    // --- the wording (ruling R-2) ---------------------------------------------------------------

    /**
     * ⚠️⚠️ The gesture is aimed at one animal; the stance it flips is player-wide. If the message
     * says "this wolf", the first bug report is "I toggled one wolf and the others changed too" —
     * and the code will be correct while the words are wrong, which nothing else in the build can
     * catch.
     */
    @Test
    void theToggleMessageIsPluralAndPlayerScoped() {
        final String message = LocaleLoader.getString("Taming.PetMode.Toggled", "Aggressive")
                .toLowerCase(Locale.ROOT);

        assertTrue(message.contains("your pets"),
                "the toggle message must say \"your pets\" (the stance is player-wide): " + message);
        for (String petScoped : Set.of("this wolf", "this pet", "this cat", "that wolf")) {
            assertTrue(!message.contains(petScoped),
                    "the toggle message must not be pet-scoped — found \"" + petScoped + "\" in: "
                            + message);
        }
    }

    @Test
    void bothModesAndBothDetailLinesAreLocalised() {
        // Walks values(), so a third mode added later cannot ship with a missing string. A raw
        // LocaleLoader miss renders the key itself to the player rather than throwing.
        for (PetCombatMode mode : PetCombatMode.values()) {
            final String name = LocaleLoader.getString(mode.localeKey());
            assertTrue(!name.contains(mode.localeKey()),
                    "missing locale string for " + mode + ": " + name);

            final String detail = LocaleLoader.getString(mode.localeKey() + ".Detail");
            assertTrue(!detail.contains(mode.localeKey()),
                    "missing detail locale string for " + mode + ": " + detail);
        }
    }

    /**
     * The two detail lines must actually differ. A copy-paste that gave both modes the same sentence
     * would leave the player unable to tell which stance they had just switched into, while every
     * other test here still passed.
     */
    @Test
    void theTwoModesDescribeThemselvesDifferently() {
        assertTrue(!LocaleLoader.getString("Taming.PetMode.Passive.Detail")
                        .equals(LocaleLoader.getString("Taming.PetMode.Aggressive.Detail")),
                "passive and aggressive ship the same description");
    }
}
