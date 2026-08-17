package com.gmail.nossr50.datatypes.skills.subskills.taming;

import org.jetbrains.annotations.NotNull;

/**
 * How a player's pets choose their fights — the player-wide combat stance toggled by sneak-clicking
 * one of them with the configured item (see {@code fabric.listeners.PetCombatModeListener}).
 *
 * <h2>Player-wide, despite a per-pet gesture</h2>
 * The gesture is aimed at one animal but the stance it flips belongs to the <em>player</em>: the
 * clicked pet is only proving intent and ownership. That mismatch is deliberate, and it is why this
 * lives in {@code PlayerProfile.uniquePlayerData} rather than on a Fabric data attachment. A
 * per-pet attachment could never be written to a pet standing in an unloaded chunk — the exact
 * load-boundary trap {@code PetFollowTeleport} is built around — so a player-wide fact stored on the
 * player is both cheaper and the only shape that can actually hold.
 *
 * <p>The consequence every player-facing string has to respect: the wording is <b>plural and
 * player-scoped</b> ("your pets"), never "this wolf". {@code PetCombatModeLocaleTest} pins that,
 * because the first bug report from getting it wrong is "I toggled one wolf and the others changed
 * too".
 *
 * <h2>Fail closed: an unreadable value is {@link #PASSIVE}</h2>
 * {@link #fromStoredValue(long)} answers {@link #PASSIVE} for anything it does not recognise, the
 * same reasoning {@code MobOrigin#byName} uses one level down. A stored value this build cannot read
 * is not a licence to start fights on the player's behalf: the failure direction of guessing
 * {@link #AGGRESSIVE} is a pack that picks fights the player never asked for and dies doing it,
 * while the failure direction of guessing {@link #PASSIVE} is the behaviour the mod had before this
 * feature existed. Only one of those is recoverable by toggling.
 *
 * <p>That default is also what makes the storage change free in both directions.
 * {@code FlatFileProfileStore} reads {@code data.<CONSTANT>} with a default of {@code 0}, so a
 * profile written before this feature existed loads as {@code 0} — which is {@link #PASSIVE}, which
 * is exactly the behaviour that profile last saw. No schema bump, no migration, no
 * {@code ConfigRetunes} row.
 *
 * <p>Minecraft-free on purpose: no wolf, no entity and no world reaches this file. The MC-typed half
 * (who is a valid target, and how far a pet will chase one) lives on
 * {@code skills.taming.PetTargeting} and {@code fabric.listeners.CallOfTheWildHandler}.
 */
public enum PetCombatMode {

    /**
     * Today's behaviour, and the default for every profile that has never toggled: a pet fights what
     * <em>you</em> fight, and picks no target of its own.
     *
     * <p>⚠️ Passive gates the <em>acquisition</em> of a new target and nothing else (ruling R-6). It
     * never clears a target a pet already has, so toggling to passive mid-fight lets that fight
     * finish rather than freezing a pet mid-swing with a zombie chewing on it. It also has no
     * bearing on how far a pet will chase what you sicced it on — the reach fix applies in both
     * modes, because "my wolves ignore what I shoot" was never a mode question.
     */
    PASSIVE(0),

    /**
     * The pack picks its own fights: an idle pet acquires the nearest hostile to the player, within
     * the configured radius.
     *
     * <p>⚠️ This is a new way to lose a pet, and that is inherent rather than a defect — wolves will
     * start fights the player did not. It belongs in the wiki page in plain words.
     */
    AGGRESSIVE(1);

    private final int storedValue;

    PetCombatMode(int storedValue) {
        this.storedValue = storedValue;
    }

    /**
     * The integer written to {@code data.PET_COMBAT_MODE} in the profile.
     *
     * <p>⚠️ These numbers are an on-disk format, not an implementation detail. {@link #PASSIVE} must
     * stay {@code 0} for as long as profiles exist that predate this feature, because {@code 0} is
     * what the store hands back for a key that is absent — see the class doc.
     */
    public int storedValue() {
        return storedValue;
    }

    /** The mode the player toggles into from this one. */
    public @NotNull PetCombatMode toggled() {
        return this == PASSIVE ? AGGRESSIVE : PASSIVE;
    }

    /** Whether pets in this mode may acquire a target of their own. */
    public boolean acquiresOwnTargets() {
        return this == AGGRESSIVE;
    }

    /** The locale key naming this mode to the player. */
    public @NotNull String localeKey() {
        return "Taming.PetMode." + (this == PASSIVE ? "Passive" : "Aggressive");
    }

    /**
     * Reads a value out of the profile, failing closed to {@link #PASSIVE}.
     *
     * <p>Takes a {@code long} because {@code PlayerProfile#getUniqueData} returns one; the stored
     * range is an {@code int}, so anything outside it is by definition unrecognised and lands on the
     * same safe answer as an unknown small integer.
     */
    public static @NotNull PetCombatMode fromStoredValue(long stored) {
        for (PetCombatMode mode : values()) {
            if (mode.storedValue == stored) {
                return mode;
            }
        }
        // Fail closed. See the class doc: a value this build cannot read is not a licence to start
        // fights. Deliberately not logged — unlike MobOrigin's marker, which can only mean a
        // downgrade or a hand-edited file, this one is reachable by a player editing their own
        // profile YAML, and a per-read warning on a value that never changes would spam the log for
        // as long as it took them to toggle it back.
        return PASSIVE;
    }
}
