package com.gmail.nossr50.runnables.skills;

import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.util.CancellableRunnable;
import com.gmail.nossr50.util.Misc;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.skills.PerksUtils;
import com.gmail.nossr50.util.skills.SkillUtils;

/**
 * Deactivates a running super ability when its duration expires (Phase 11). Flips the ability out of
 * active mode, tells the player it wore off, and schedules the {@link AbilityCooldownTask} that will
 * announce when it's ready again.
 *
 * <p><b>Singleplayer port notes.</b> The mode/informed flips, the off-notification, and the
 * follow-up cooldown scheduling are ported verbatim (the FoliaLib {@code runAtEntityLater} becomes
 * {@link McMMOMod#getScheduler()}{@code .runLater}). What's dropped/deferred:
 * <ul>
 *   <li><b>Wired (K3/K4)</b> — {@code SUPER_BREAKER}/{@code GIGA_DRILL_BREAKER} now run
 *       {@link SkillUtils#removeAbilityBoostsFromInventory} to strip the dig-speed enchant boost from
 *       any tool it was applied to. <b>PORT Phase 11 (still deferred)</b> — the breaker/{@code BERSERK}
 *       chunk resend ({@code General.RefreshChunks} → Bukkit {@code World.refreshChunk}) has no
 *       singleplayer analogue (the integrated client renders locally); re-add via a client
 *       chunk-refresh mixin only if actually needed.</li>
 *   <li><b>PORT Phase 3</b> — {@code EventUtils.callAbilityDeactivateEvent}: a Bukkit API event with
 *       no listeners in singleplayer. Re-home onto the internal {@code EventBus} if ever needed.</li>
 *   <li><b>Cut (Phase 1.5)</b> — {@code sendAbilityNotificationToOtherPlayers} /
 *       {@code SkillUtils.sendSkillMessage}: the "alert nearby players" broadcast is a multiplayer
 *       surface (no other players exist in singleplayer).</li>
 *   <li><b>Dropped guard</b> — legacy gated the cooldown-task scheduling on
 *       {@code !isServerShutdownExecuted()} so FoliaLib wouldn't throw after disable. The
 *       {@link com.gmail.nossr50.platform.scheduler.TickScheduler} is {@code cancelAll()}-ed at
 *       server stop instead, so a shutdown-window schedule is harmless (the entry is cleared before
 *       it could fire).</li>
 * </ul>
 */
public class AbilityDisableTask extends CancellableRunnable {
    private final McMMOPlayer mmoPlayer;
    private final SuperAbilityType ability;

    public AbilityDisableTask(McMMOPlayer mmoPlayer, SuperAbilityType ability) {
        this.mmoPlayer = mmoPlayer;
        this.ability = ability;
    }

    @Override
    public void run() {
        if (!mmoPlayer.getAbilityMode(ability)) {
            return;
        }

        mmoPlayer.setAbilityMode(ability, false);
        mmoPlayer.setAbilityInformed(ability, false);

        // Clear the Super/Giga Breaker dig-speed boost from any tool it was applied to.
        if (ability == SuperAbilityType.SUPER_BREAKER
                || ability == SuperAbilityType.GIGA_DRILL_BREAKER) {
            SkillUtils.removeAbilityBoostsFromInventory(mmoPlayer.getPlayer());
        }

        if (mmoPlayer.useChatNotifications()) {
            NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.ABILITY_OFF,
                    ability.getAbilityOff());
        }

        McMMOMod.getScheduler().runLater(new AbilityCooldownTask(mmoPlayer, ability),
                (long) PerksUtils.handleCooldownPerks(ability.getCooldown())
                        * Misc.TICK_CONVERSION_FACTOR);
    }
}
