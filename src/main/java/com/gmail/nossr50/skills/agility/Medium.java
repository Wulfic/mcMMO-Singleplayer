package com.gmail.nossr50.skills.agility;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
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
 *
 * <p>Each medium also names the {@link PrimarySkillType} its travel XP is <em>paid into</em>. Agility
 * itself earns nothing: it is a child skill whose level is the mean of these three, so the enum is
 * the one place that maps "what the player is doing" to "which bar goes up." Keeping that mapping
 * here rather than in the tick handler is what stops the two answers drifting apart.
 */
public enum Medium {

    /** Sprinting on land. Pays Parkour, and gates on Parkour's own sub-skills. */
    LAND("Land", PrimarySkillType.PARKOUR,
            SubSkillType.PARKOUR_FLEET_FOOTED, SubSkillType.PARKOUR_SECOND_WIND),

    /** Moving while in water. Pays Swimming, and gates on Swimming's own sub-skills. */
    WATER("Water", PrimarySkillType.SWIMMING,
            SubSkillType.SWIMMING_FLEET_FOOTED, SubSkillType.SWIMMING_SECOND_WIND),

    /** Gliding on an elytra. Pays Flying, and gates on Flying's own sub-skills. */
    AIR("Air", PrimarySkillType.FLYING,
            SubSkillType.FLYING_FLEET_FOOTED, SubSkillType.FLYING_SECOND_WIND);

    private final String configName;
    private final PrimarySkillType primarySkill;
    private final SubSkillType fleetFootedSubSkill;
    private final SubSkillType secondWindSubSkill;

    Medium(String configName, PrimarySkillType primarySkill, SubSkillType fleetFootedSubSkill,
            SubSkillType secondWindSubSkill) {
        this.configName = configName;
        this.primarySkill = primarySkill;
        this.fleetFootedSubSkill = fleetFootedSubSkill;
        this.secondWindSubSkill = secondWindSubSkill;
    }

    /**
     * The skill this medium's travel XP is paid into — never {@code AGILITY}, which earns no XP of
     * its own.
     */
    public PrimarySkillType primarySkill() {
        return primarySkill;
    }

    /**
     * This medium's Fleet Footed sub-skill — {@code PARKOUR_}, {@code SWIMMING_} or {@code FLYING_}.
     *
     * <p>Replaced {@code fleetFootedRank()} on 2026-08-17. Until then both sub-skills lived on the
     * retired {@code AGILITY} child skill carrying one <em>rank</em> per medium (land 1, water 2, air
     * 3), and this enum held that rank number. Retiring the parent dissolved the ladder, so the
     * medium now names a whole sub-skill rather than an index into one — which is what lets each
     * medium be gated on the level of the skill you actually earn by travelling through it.
     */
    public SubSkillType fleetFootedSubSkill() {
        return fleetFootedSubSkill;
    }

    /**
     * This medium's Second Wind sub-skill — the body {@code SuperAbilityType.SECOND_WIND} dispatches
     * to here. The ability stays a single constant with a single cooldown; only what <em>gates</em>
     * each body is per-medium.
     */
    public SubSkillType secondWindSubSkill() {
        return secondWindSubSkill;
    }

    /** This medium's section name in {@code experience.yml} / {@code advanced.yml}. */
    public String configName() {
        return configName;
    }
}
