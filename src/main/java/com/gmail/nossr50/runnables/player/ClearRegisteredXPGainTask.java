package com.gmail.nossr50.runnables.player;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.util.CancellableRunnable;
import com.gmail.nossr50.util.player.UserManager;

/**
 * Periodically expires stale diminished-returns XP records (Phase 11). mcMMO's exploit-fix
 * diminished-returns feature caps how much XP a skill can earn inside a rolling time window; each
 * gain is registered on the {@link com.gmail.nossr50.datatypes.player.PlayerProfile} with an expiry
 * and must be purged once it ages out, or the rolling totals only ever grow. This task fans that
 * purge across every tracked player.
 *
 * <p>Straight port of legacy {@code ClearRegisteredXPGainTask} (the FoliaLib async fan-out collapses
 * to a plain main-thread loop in singleplayer — the work is a cheap map poll). Scheduled as a
 * repeating task at server start (every {@code 60} ticks, matching legacy) and cancelled at stop.
 */
public class ClearRegisteredXPGainTask extends CancellableRunnable {
    @Override
    public void run() {
        for (McMMOPlayer mmoPlayer : UserManager.getPlayers()) {
            mmoPlayer.getProfile().purgeExpiredXpGains();
        }
    }
}
