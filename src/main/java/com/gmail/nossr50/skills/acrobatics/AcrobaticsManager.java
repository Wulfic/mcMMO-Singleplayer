package com.gmail.nossr50.skills.acrobatics;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.SkillManager;

/**
 * Acrobatics skill manager (Phase 10.3 port). The fall-location history and the Dodge combat path
 * are dropped until the combat phase — they need Bukkit {@code Location}/{@code Entity} adapters
 * ({@code BlockLocationHistory}, {@code getPlayer().isBlocking()}, particle/metadata machinery).
 *
 * <p>What survives is {@link #canGainRollXP()}, the anti-exploit cooldown that throttles how often a
 * player can farm Roll XP by repeatedly taking fall damage. It is pure time/counter logic gated by
 * {@code ExperienceConfig.isAcrobaticsExploitingPrevented()}, so it ports cleanly and is unit
 * testable.
 */
public class AcrobaticsManager extends SkillManager {

    public AcrobaticsManager(McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.ACROBATICS);
    }

    private long rollXPCooldown = 0;
    private final long rollXPInterval = (1000 * 3);
    private long rollXPIntervalLengthen = (1000 * 10);

    /**
     * Whether the player may gain Roll XP right now. When exploit prevention is off this is always
     * true; when on, it enforces a cooldown that lengthens with every early retry so a player cannot
     * farm XP by spamming fall damage.
     *
     * @return {@code true} if Roll XP may be awarded this call
     */
    public boolean canGainRollXP() {
        if (!McMMOMod.getExperienceConfig().isAcrobaticsExploitingPrevented()) {
            return true;
        }

        if (System.currentTimeMillis() >= rollXPCooldown) {
            rollXPCooldown = System.currentTimeMillis() + rollXPInterval;
            rollXPIntervalLengthen = (1000 * 10);
            return true;
        } else {
            rollXPCooldown += rollXPIntervalLengthen;
            rollXPIntervalLengthen += 1000; // Add another second to the next penalty
            return false;
        }
    }
}
