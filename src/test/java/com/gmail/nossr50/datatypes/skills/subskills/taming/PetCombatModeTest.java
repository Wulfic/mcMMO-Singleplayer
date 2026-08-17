package com.gmail.nossr50.datatypes.skills.subskills.taming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.database.ProfileStore;
import com.gmail.nossr50.datatypes.player.PlayerProfile;
import com.gmail.nossr50.datatypes.player.UniqueDataType;
import com.gmail.nossr50.fabric.McMMOMod;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link PetCombatMode} and the profile slot it lives in.
 *
 * <p>MC-free by construction: the mode is a player fact, so nothing here needs a wolf.
 */
class PetCombatModeTest {

    private static final UUID UID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void wireExperienceConfig(@TempDir Path dataFolder) {
        McMMOMod.setExperienceConfig(new ExperienceConfig(dataFolder));
    }

    @AfterEach
    void unbindProfileStore() {
        // One test binds a mock store. Leaving it bound would make every later test in this fork
        // silently persist through a Mockito stub — the ConfigBootstrapTest "poisoned its whole
        // fork" shape.
        McMMOMod.setProfileStore(null);
    }

    // --- the on-disk contract -------------------------------------------------------------------

    @Test
    void passiveIsZeroBecauseAnAbsentKeyReadsAsZero() {
        // ⚠️ This is an on-disk format assertion, not a style preference. FlatFileProfileStore reads
        // data.<CONSTANT> with a default of 0, so every profile written before this feature existed
        // loads as 0. If PASSIVE stops being 0, every one of those profiles silently becomes
        // AGGRESSIVE on the next login and packs start fights their owners never asked for.
        assertEquals(0, PetCombatMode.PASSIVE.storedValue());
    }

    @Test
    void everyModeHasADistinctStoredValue() {
        // A duplicate would make fromStoredValue answer whichever came first in values(), turning a
        // toggle into a one-way trip with no error anywhere.
        for (PetCombatMode a : PetCombatMode.values()) {
            for (PetCombatMode b : PetCombatMode.values()) {
                if (a != b) {
                    assertNotEquals(a.storedValue(), b.storedValue(),
                            a + " and " + b + " share a stored value");
                }
            }
        }
    }

    @Test
    void everyStoredValueRoundTrips() {
        // Walks values() rather than naming the two constants, so a third mode added later is
        // covered without anyone remembering to extend this.
        for (PetCombatMode mode : PetCombatMode.values()) {
            assertSame(mode, PetCombatMode.fromStoredValue(mode.storedValue()));
        }
    }

    // --- fail closed ----------------------------------------------------------------------------

    @Test
    void anUnknownStoredValueReadsAsPassive() {
        // ⚠️⚠️ The row this class exists for. A value this build cannot read is not a licence to
        // start fights: guessing AGGRESSIVE loses pets, guessing PASSIVE reproduces the behaviour
        // the mod had before the feature and is undone by one toggle.
        assertSame(PetCombatMode.PASSIVE, PetCombatMode.fromStoredValue(2));
        assertSame(PetCombatMode.PASSIVE, PetCombatMode.fromStoredValue(-1));
        assertSame(PetCombatMode.PASSIVE, PetCombatMode.fromStoredValue(Long.MAX_VALUE));
        assertSame(PetCombatMode.PASSIVE, PetCombatMode.fromStoredValue(Long.MIN_VALUE));
    }

    // --- behaviour ------------------------------------------------------------------------------

    @Test
    void togglingIsItsOwnInverse() {
        for (PetCombatMode mode : PetCombatMode.values()) {
            assertSame(mode, mode.toggled().toggled(), mode + " does not survive two toggles");
            assertNotSame(mode, mode.toggled(), mode + " toggles to itself");
        }
    }

    @Test
    void onlyAggressiveAcquiresItsOwnTargets() {
        // The positive AND the negative. Without the negative, the whole feature is
        // indistinguishable from "pets are always aggressive now".
        assertTrue(PetCombatMode.AGGRESSIVE.acquiresOwnTargets());
        assertFalse(PetCombatMode.PASSIVE.acquiresOwnTargets());
    }

    @Test
    void everyModeNamesADistinctLocaleKey() {
        assertEquals("Taming.PetMode.Passive", PetCombatMode.PASSIVE.localeKey());
        assertEquals("Taming.PetMode.Aggressive", PetCombatMode.AGGRESSIVE.localeKey());
    }

    // --- the profile slot -----------------------------------------------------------------------

    @Test
    void aFreshProfileIsPassive() {
        final PlayerProfile profile = new PlayerProfile("p", UID, 0);
        assertSame(PetCombatMode.PASSIVE, PetCombatMode.fromStoredValue(
                profile.getUniqueData(UniqueDataType.PET_COMBAT_MODE)));
    }

    @Test
    void theModeRoundTripsThroughTheProfile() {
        final PlayerProfile profile = new PlayerProfile("p", UID, 0);
        profile.setUniqueData(UniqueDataType.PET_COMBAT_MODE,
                PetCombatMode.AGGRESSIVE.storedValue());
        assertSame(PetCombatMode.AGGRESSIVE, PetCombatMode.fromStoredValue(
                profile.getUniqueData(UniqueDataType.PET_COMBAT_MODE)));

        profile.setUniqueData(UniqueDataType.PET_COMBAT_MODE, PetCombatMode.PASSIVE.storedValue());
        assertSame(PetCombatMode.PASSIVE, PetCombatMode.fromStoredValue(
                profile.getUniqueData(UniqueDataType.PET_COMBAT_MODE)));
    }

    @Test
    void writingTheModeMarksTheProfileDirtySoItActuallyReachesDisk() {
        // A toggle that is never persisted resets on relog, and nothing else in the build would
        // notice: the in-memory read answers correctly for the whole session, so the feature looks
        // perfect until you quit.
        //
        // Asserted through a real save rather than a dirty-flag getter, because PlayerProfile has no
        // such getter and adding one would let this test pass against a flag nothing consumes.
        // PlayerProfile#save is a no-op unless the profile is BOTH loaded and changed, so a mock
        // store receiving saveProfile proves both.
        final ProfileStore store = mock(ProfileStore.class);
        McMMOMod.setProfileStore(store);

        final PlayerProfile profile = new PlayerProfile("p", UID, true, 0);
        profile.save(true);
        verify(store, never()).saveProfile(profile); // precondition: an untouched profile is clean

        profile.setUniqueData(UniqueDataType.PET_COMBAT_MODE,
                PetCombatMode.AGGRESSIVE.storedValue());
        profile.save(true);
        verify(store).saveProfile(profile);
    }

    /**
     * ⚠️⚠️ The regression this feature nearly shipped.
     *
     * <p>{@code PlayerProfile}'s 3-argument constructor used to seed exactly one
     * {@link UniqueDataType} by hand, and {@code getUniqueData} unboxed a {@code Map#get} straight to
     * {@code long}. {@code FlatFileProfileStore#saveProfile} then loops over {@code values()} calling
     * it — so the first constant added after {@code CHIMAERA_WING_DATS} was an NPE that took out
     * saving for the <em>entire</em> profile, not just the new field. The player would have lost
     * every skill level earned that session.
     *
     * <p>Walks {@code values()} exactly as {@code saveProfile} does, so it stays a real guard for the
     * next constant rather than a test of the two that exist today.
     */
    @Test
    void everyUniqueDataKeyIsReadableOnAFreshProfile() {
        final PlayerProfile profile = new PlayerProfile("p", UID, 0);
        for (UniqueDataType type : UniqueDataType.values()) {
            assertEquals(0L, profile.getUniqueData(type),
                    type + " must read as 0 on a fresh profile — saveProfile loops over values()");
        }
    }

    @Test
    void theModeEnumIsMinecraftFree() {
        // The stance is a player fact and the manager holding it is MC-free; a Minecraft type on
        // this class would drag the entity hierarchy into every profile test that touches it.
        for (var method : PetCombatMode.class.getDeclaredMethods()) {
            assertFalse(method.getReturnType().getName().startsWith("net.minecraft"),
                    method.getName() + " returns a Minecraft type");
            for (Class<?> parameter : method.getParameterTypes()) {
                assertFalse(parameter.getName().startsWith("net.minecraft"),
                        method.getName() + " takes a Minecraft type");
            }
        }
        assertNotNull(PetCombatMode.values());
    }
}
