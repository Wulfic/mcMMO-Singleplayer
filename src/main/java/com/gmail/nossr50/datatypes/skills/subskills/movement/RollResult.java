package com.gmail.nossr50.datatypes.skills.subskills.movement;

/**
 * Immutable outcome of an Agility fall/roll evaluation (see
 * {@link com.gmail.nossr50.skills.movement.MovementManager#rollCheck}).
 *
 * <p>Port note (K2): the legacy builder took a Bukkit {@code EntityDamageEvent} and tracked
 * {@code eventDamage}/{@code isFatal}; both were only read for the never-shipped stats UI, so the
 * MC-free port drops them and keeps the five fields the fall-damage handler actually consumes. A
 * {@code null} result (not represented here) means the fall was fatal and mcMMO must not interfere.
 */
public final class RollResult {
    private final boolean rollSuccess;
    private final boolean graceful;
    private final double modifiedDamage;
    private final boolean exploiting;
    private final float xpGain;

    public RollResult(boolean rollSuccess, boolean graceful, double modifiedDamage,
            boolean exploiting, float xpGain) {
        this.rollSuccess = rollSuccess;
        this.graceful = graceful;
        this.modifiedDamage = modifiedDamage;
        this.exploiting = exploiting;
        this.xpGain = xpGain;
    }

    /** Whether the Roll (or Graceful Roll) proc succeeded and its damage reduction should apply. */
    public boolean isRollSuccess() {
        return rollSuccess;
    }

    /** Whether the player was sneaking when they landed (Graceful Roll — double the odds). */
    public boolean isGraceful() {
        return graceful;
    }

    /** The reduced fall damage to apply on a successful roll. */
    public double getModifiedDamage() {
        return modifiedDamage;
    }

    /** Whether the player is detected as farming Agility XP (suppresses the XP award). */
    public boolean isExploiting() {
        return exploiting;
    }

    /** The Agility XP to award for this fall (0 when throttled or exploiting). */
    public float getXpGain() {
        return xpGain;
    }
}
