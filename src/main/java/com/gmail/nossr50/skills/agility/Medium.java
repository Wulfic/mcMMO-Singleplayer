package com.gmail.nossr50.skills.agility;

import com.gmail.nossr50.datatypes.skills.SubSkillType;

/**
 * The medium a player is travelling through this tick — the one thing Agility's three new domains
 * differ by.
 *
 * <p>Exactly <b>one</b> medium applies per tick. The states overlap in vanilla (you can sprint into
 * water, or glide into it), so the classifier picks by the priority these constants are declared in:
 * <b>{@link #AIR} &gt; {@link #WATER} &gt; {@link #LAND}</b>, most specialised first. Without a
 * fixed priority a player gliding across a lake would be paid twice for one tick of travel.
 *
 * <p>MC-free on purpose: the platform layer decides <em>which</em> medium a tick is, and everything
 * downstream of that decision — XP, speed bonuses, ability bodies — is plain arithmetic keyed on
 * this enum and therefore unit-testable.
 */
public enum Medium {

    /** Sprinting on land. Fleet Footed's first rank. */
    LAND(1, "Land"),

    /** Moving while in water. Fleet Footed's second rank. */
    WATER(2, "Water"),

    /** Gliding on an elytra. Fleet Footed's third rank. */
    AIR(3, "Air");

    private final int fleetFootedRank;
    private final String configName;

    Medium(int fleetFootedRank, String configName) {
        this.fleetFootedRank = fleetFootedRank;
        this.configName = configName;
    }

    /**
     * The {@link SubSkillType#AGILITY_FLEET_FOOTED} (and
     * {@link SubSkillType#AGILITY_SECOND_WIND}) rank that unlocks this medium. Both sub-skills carry
     * one rank per medium in the same order, so the two share this number rather than each keeping
     * their own copy of "water is the second one".
     */
    public int fleetFootedRank() {
        return fleetFootedRank;
    }

    /** This medium's section name in {@code experience.yml} / {@code advanced.yml}. */
    public String configName() {
        return configName;
    }
}
