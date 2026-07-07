package com.gmail.nossr50.skills.axes;

import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.util.skills.RankUtils;

/**
 * Static helpers backing the Axes skill. Port note (Phase 10.3): only the Axe Mastery bonus-damage
 * math survives — it is pure config + rank arithmetic and therefore provable without a server.
 *
 * <p>Dropped until the combat phase:
 * <ul>
 *   <li>{@code hasArmor(LivingEntity)} — walks the target's armor slots (entity-equipment adapter
 *       needed); it only gated the Impact / Greater Impact combat bodies, which are also dropped;</li>
 *   <li>the critical-hit / impact / greater-impact / skull-splitter modifier statics — they belonged
 *       to those dropped combat bodies.</li>
 * </ul>
 *
 * <p>Config statics were made live reads (was {@code static final} inited at class-load): the
 * service-locator config is installed after class load, so cached statics were fragile — same call
 * the ported {@code Archery} makes.
 */
public final class Axes {

    private Axes() {
    }

    /**
     * For every rank in Axe Mastery we add the configured per-rank multiplier to get the total bonus
     * damage from Axe Mastery.
     *
     * @param player the attacking player
     * @return the Axe Mastery bonus damage added to the attack
     */
    public static double getAxeMasteryBonusDamage(PlatformPlayer player) {
        return RankUtils.getRank(player, SubSkillType.AXES_AXE_MASTERY)
                * McMMOMod.getAdvancedConfig().getAxeMasteryRankDamageMultiplier();
    }
}
