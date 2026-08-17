package com.gmail.nossr50.datatypes.player;

/**
 * Keys into {@link PlayerProfile}'s miscellaneous per-player integer store — the facts that fit
 * neither a skill level, an XP total, nor a super-ability cooldown.
 *
 * <h2>Adding a constant is free on load, and was NOT free on save</h2>
 * {@code FlatFileProfileStore} reads each key as {@code yc.getInt("data." + name(), 0)}, so a
 * profile written before a constant existed loads it as {@code 0} — no schema bump and no migration,
 * provided {@code 0} is a sane default for the new fact.
 *
 * <p>⚠️ The <em>save</em> side used to make that untrue. {@code FlatFileProfileStore#saveProfile}
 * loops over {@code values()} calling {@code PlayerProfile#getUniqueData}, which unboxes a
 * {@code Map#get} straight to {@code long} — so any constant the in-memory map had never been seeded
 * with was an NPE on save, not a default. The 3-argument {@link PlayerProfile} constructor seeded
 * exactly one constant by hand, which meant the very next constant added here would have crashed
 * every save for profiles built that way. Both halves are now defended: that constructor seeds from
 * {@code values()}, and {@code getUniqueData} defaults rather than unboxing null. Do not reintroduce
 * a hand-written seed list.
 */
public enum UniqueDataType {

    /** Chimaera Wing's deactivation timestamp (its per-use cooldown clock). */
    CHIMAERA_WING_DATS,

    /**
     * Whether this player's pets fight only what the player fights, or pick their own targets —
     * stored as {@link com.gmail.nossr50.datatypes.skills.subskills.taming.PetCombatMode#storedValue()},
     * where {@code 0} is {@code PASSIVE}.
     *
     * <p>Player-wide rather than per-pet on purpose: a per-pet fact cannot be written to a pet in an
     * unloaded chunk. See {@code PetCombatMode}'s class doc for why {@code 0} has to keep meaning
     * {@code PASSIVE}.
     */
    PET_COMBAT_MODE
}
