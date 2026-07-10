package com.gmail.nossr50.runnables.skills;

import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.util.CancellableRunnable;
import com.gmail.nossr50.util.player.NotificationManager;

/**
 * Fires when a super ability's cooldown finishes, telling the player the ability is ready again
 * (Phase 11). Scheduled by {@link AbilityDisableTask} for {@code cooldown} seconds after the ability
 * deactivates.
 *
 * <p>Singleplayer port of legacy {@code AbilityCooldownTask}: the Bukkit {@code Player.isOnline()}
 * liveness guard becomes {@link com.gmail.nossr50.platform.PlatformPlayer#isAlive()}, and the
 * feedback routes through the ported {@link NotificationManager} (which takes the
 * {@link McMMOPlayer}). The {@code abilityInformed} flag guards against a doubled reminder: it flips
 * {@code informed} true so the same refresh isn't announced twice.
 */
public class AbilityCooldownTask extends CancellableRunnable {
    private final McMMOPlayer mmoPlayer;
    private final SuperAbilityType ability;

    public AbilityCooldownTask(McMMOPlayer mmoPlayer, SuperAbilityType ability) {
        this.mmoPlayer = mmoPlayer;
        this.ability = ability;
    }

    @Override
    public void run() {
        if (!mmoPlayer.getPlayer().isAlive() || mmoPlayer.getAbilityInformed(ability)) {
            return;
        }

        mmoPlayer.setAbilityInformed(ability, true);
        NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.ABILITY_REFRESHED,
                ability.getAbilityRefresh());
    }
}
