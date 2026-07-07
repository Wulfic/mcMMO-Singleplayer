package com.gmail.nossr50.skills.maces;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.skills.RankUtils;

/**
 * Maces skill manager (Phase 10.3 port). Only the Crush damage math and the Cripple duration/strength
 * constants survive; the Cripple effect body is dropped until the potion/entity adapter lands.
 *
 * <p>Dropped until the combat phase:
 * <ul>
 *   <li>{@code processCripple} — resolves the Slowness {@code PotionEffectType} via the Bukkit
 *       registry, applies it to a {@code LivingEntity}, and plays a particle effect;</li>
 *   <li>the {@code mockSpigotMatch} / {@code slowEffectType} registry-lookup plumbing that fed it.</li>
 * </ul>
 */
public class MacesManager extends SkillManager {
    public MacesManager(McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.MACES);
    }

    /**
     * Get the Crush damage bonus.
     *
     * @return the Crush damage bonus.
     */
    public double getCrushDamage() {
        if (!Permissions.canUseSubSkill(getPlayer(), SubSkillType.MACES_CRUSH)) {
            return 0;
        }

        int rank = RankUtils.getRank(getPlayer(), SubSkillType.MACES_CRUSH);

        if (rank > 0) {
            return (0.5D + (rank * 1.D));
        }

        return 0;
    }

    public static int getCrippleTickDuration(boolean isPlayerTarget) {
        // TODO: Make configurable
        if (isPlayerTarget) {
            return 20;
        } else {
            return 30;
        }
    }

    public static int getCrippleStrength(boolean isPlayerTarget) {
        // TODO: Make configurable
        return isPlayerTarget ? 1 : 2;
    }
}
