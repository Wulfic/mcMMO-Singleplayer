package com.gmail.nossr50.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.util.McTestRegistries;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Ruling A-9 (2026-08-18), and the reason it needed a test rather than a comment.
 *
 * <p>{@link SkillAttributeService.Managed} carries two names for every buff, and they are not the
 * same kind of thing:
 *
 * <ul>
 *   <li>the <b>enum constant</b>, which exists only in this source tree and is free to change — the
 *       2026-08-17 Agility retirement renamed two of them; and</li>
 *   <li>the <b>id string</b>, which is written into <em>every player's entity NBT</em> as the
 *       attribute modifier's identifier.</li>
 * </ul>
 *
 * <p>🔴 {@link SkillAttributeService#clearAll} removes only the ids it still knows about. Change an
 * id and the modifier already sitting in a player's save is orphaned: nothing can ever see it to
 * remove it, so it becomes a permanent, unremovable buff on everyone who has already played. The
 * failure is silent on every machine that has never run the old version — which includes CI, and
 * includes whoever makes the change.
 *
 * <p>So the two ids that have already shipped are pinned as literals. A rename of the constant
 * leaves this test green, which is the point; a rename of the id turns it red.
 */
class SkillAttributeIdsAreFrozenTest {

    @BeforeAll
    static void bootstrap() {
        McTestRegistries.bootstrap();
    }

    /**
     * ⚠️ The spelling is deliberately the OLD skill's name. {@code MOVEMENT_FLEET_FOOTED_LAND} was
     * {@code AGILITY_FLEET_FOOTED_LAND} until 2026-08-17 and its id still says {@code agility_}.
     * That mismatch is not rot — it is the ruling. Do not "tidy" it.
     */
    private static Map<SkillAttributeService.Managed, String> shippedIds() {
        // ⚠️ A METHOD, not a static field. Managed's constants hold RegistryEntry values, so
        // touching the enum runs EntityAttributes' class initialiser -- which throws "Not
        // bootstrapped" if it happens before @BeforeAll. A static field here initialises at class
        // load and fails the whole class with an initialisation error rather than a useful one.
        return Map.of(
                SkillAttributeService.Managed.MOVEMENT_FLEET_FOOTED_LAND, "agility_fleet_footed",
                SkillAttributeService.Managed.MOVEMENT_FLEET_FOOTED_WATER,
                        "agility_fleet_footed_water",
                SkillAttributeService.Managed.STEALTH_PADFOOT, "stealth_padfoot",
                SkillAttributeService.Managed.UNARMORED_IRON_SKIN, "unarmored_iron_skin",
                // Added by the 2026-08-14 Taming work and pinned here the moment this guard was
                // written -- which is exactly what the converse test below is for. Its id has not
                // reached a released save yet; it is frozen from the release that carries it.
                SkillAttributeService.Managed.TAMING_PET_ENGAGE_RANGE, "taming_pet_engage");
    }

    @Test
    void everyShippedModifierIdKeepsTheSpellingItWasSavedUnder() {
        for (Map.Entry<SkillAttributeService.Managed, String> entry : shippedIds().entrySet()) {
            assertEquals(entry.getValue(), entry.getKey().id().getPath(),
                    entry.getKey() + "'s modifier id changed. That id is in existing players' NBT "
                            + "and clearAll can no longer see the old one, so this strands an "
                            + "unremovable buff. Rename the CONSTANT, never the literal (A-9).");
        }
    }

    /**
     * The converse, and the half that stops the map above from silently going stale: a buff added to
     * {@code Managed} later must be added here too, or it ships with no id pinned at all.
     *
     * <p>Without this, the test degenerates into a statement about four constants somebody
     * remembered — the same vacuous shape as a guard driven by the very table it is guarding.
     */
    @Test
    void everyManagedBuffHasItsIdPinnedHere() {
        final Set<SkillAttributeService.Managed> unpinned =
                new HashSet<>(Set.of(SkillAttributeService.Managed.values()));
        unpinned.removeAll(shippedIds().keySet());

        assertTrue(unpinned.isEmpty(),
                "these managed buffs have no frozen id recorded: " + unpinned
                        + ". Add them to SHIPPED_IDS with the id they SHIPPED under — once a "
                        + "modifier id reaches a player's save it can never change again (A-9).");
    }

    /** Two buffs sharing an id would make clearAll remove one while believing it removed both. */
    @Test
    void noTwoManagedBuffsShareAnId() {
        final Set<String> seen = new HashSet<>();
        for (SkillAttributeService.Managed managed : SkillAttributeService.Managed.values()) {
            assertTrue(seen.add(managed.id().toString()),
                    managed + " reuses an id another managed buff already claims");
        }
    }
}
