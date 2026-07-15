package com.gmail.nossr50.skills.axes;

import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformLivingEntity;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.util.skills.RankUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Static helpers backing the Axes skill. Port note (Phase 10.3): only the Axe Mastery bonus-damage
 * math survives — it is pure config + rank arithmetic and therefore provable without a server.
 *
 * <p>The critical-hit / impact / greater-impact / skull-splitter modifier statics stay dropped: each
 * was only a cached config read, and their combat bodies (now live — see {@link AxesManager}) read
 * the config directly at the point of use, like the Axe Mastery multiplier below.
 *
 * <p>Config statics were made live reads (was {@code static final} inited at class-load): the
 * service-locator config is installed after class load, so cached statics were fragile — same call
 * the ported {@code Archery} makes.
 */
public final class Axes {

    private Axes() {
    }

    /**
     * Whether {@code target} is wearing any armor mcMMO recognises. Ports legacy
     * {@code Axes.hasArmor(LivingEntity)}, which walked {@code getEquipment().getArmorContents()}
     * looking for one {@code ItemUtils.isArmor} hit; that walk-and-filter is now
     * {@link PlatformLivingEntity#getArmorPieces()}, so this is just its emptiness test.
     *
     * <p>Splits Armor Impact from Greater Impact: an armored target has its armor chewed up, an
     * unarmored one is knocked back and takes bonus damage instead.
     *
     * @param target the entity being hit
     * @return {@code true} if the target is alive and wearing at least one recognised armor piece
     */
    public static boolean hasArmor(@NotNull PlatformLivingEntity target) {
        return target.isValid() && !target.getArmorPieces().isEmpty();
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
