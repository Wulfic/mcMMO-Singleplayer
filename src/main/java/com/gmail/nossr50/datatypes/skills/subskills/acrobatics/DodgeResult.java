package com.gmail.nossr50.datatypes.skills.subskills.acrobatics;

/**
 * Immutable outcome of a successful Acrobatics Dodge (see
 * {@link com.gmail.nossr50.skills.acrobatics.AcrobaticsManager#dodgeCheck}).
 *
 * <p>Unlike {@link RollResult}, a Dodge only produces a result when it <em>succeeds</em>: the
 * evaluator returns {@code null} when the player fails the roll (or the reduced hit would still be
 * fatal), meaning no damage reduction, XP, or feedback should fire. So the two fields here are simply
 * the reduced damage the listener applies and the XP the orchestration awards (0 when the attacker is
 * not an XP-eligible mob).
 */
public final class DodgeResult {
    private final double modifiedDamage;
    private final float xpGain;

    public DodgeResult(double modifiedDamage, float xpGain) {
        this.modifiedDamage = modifiedDamage;
        this.xpGain = xpGain;
    }

    /** The reduced damage to apply after a successful dodge. */
    public double getModifiedDamage() {
        return modifiedDamage;
    }

    /** The Acrobatics XP to award for this dodge (0 when the attacker is not XP-eligible). */
    public float getXpGain() {
        return xpGain;
    }
}
