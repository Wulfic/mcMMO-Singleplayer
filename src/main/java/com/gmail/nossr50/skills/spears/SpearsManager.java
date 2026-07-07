package com.gmail.nossr50.skills.spears;

import static com.gmail.nossr50.util.skills.RankUtils.getRank;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.SkillManager;

/**
 * Spears skill manager (Phase 10.3 port). Only the Spear Mastery bonus-damage math and the Momentum
 * duration/strength constants survive; the Momentum effect body is dropped until the potion/entity
 * adapter lands.
 *
 * <p>Dropped until the combat phase:
 * <ul>
 *   <li>{@code potentiallyApplyMomentum} / {@code canMomentumBeApplied} — resolve the Speed
 *       {@code PotionEffectType} via the Bukkit registry, compare/apply it to the player, and
 *       notify;</li>
 *   <li>the {@code mockSpigotMatch} / {@code swiftnessEffectType} registry-lookup plumbing.</li>
 * </ul>
 */
public class SpearsManager extends SkillManager {
    public SpearsManager(McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.SPEARS);
    }

    public static int getMomentumTickDuration(int momentumRank) {
        return 20 * (momentumRank * 2);
    }

    public static int getMomentumStrength() {
        return 2;
    }

    public double getSpearMasteryBonusDamage() {
        return McMMOMod.getAdvancedConfig().getSpearMasteryRankDamageMultiplier()
                * getRank(getPlayer(), SubSkillType.SPEARS_SPEAR_MASTERY);
    }
}
