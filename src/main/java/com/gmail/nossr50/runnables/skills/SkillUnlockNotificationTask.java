package com.gmail.nossr50.runnables.skills;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.util.CancellableRunnable;
import com.gmail.nossr50.util.player.NotificationManager;

/**
 * Notifies a player, one tick after the level-up that unlocked it, that they have gained a new rank
 * of a subskill. Scheduled by the level-up path so the unlock message lands after the level-up
 * feedback rather than in the same frame.
 *
 * <p>Singleplayer port of legacy {@code SkillUnlockNotificationTask}: unchanged except the feedback
 * routes through the ported {@link NotificationManager#sendPlayerUnlockNotification} (which now takes
 * an {@link McMMOPlayer} directly and plays the unlock sound via the ported {@code SoundManager}).
 * The commented-out legacy {@code sendTitle} experiment is not carried over.
 */
public class SkillUnlockNotificationTask extends CancellableRunnable {
    private final McMMOPlayer mmoPlayer;
    private final SubSkillType subSkillType;

    /**
     * @param mmoPlayer target player
     * @param subSkillType the subskill that was just unlocked
     * @param rank the rank that was unlocked. Retained in the signature for call-site fidelity with
     *     legacy, but not stored: {@link NotificationManager#sendPlayerUnlockNotification} resolves
     *     the current rank itself, so a passed-in value would be redundant (and risk drift).
     */
    public SkillUnlockNotificationTask(McMMOPlayer mmoPlayer, SubSkillType subSkillType, int rank) {
        this.mmoPlayer = mmoPlayer;
        this.subSkillType = subSkillType;
    }

    @Override
    public void run() {
        NotificationManager.sendPlayerUnlockNotification(mmoPlayer, subSkillType);
    }
}
