package com.gmail.nossr50.runnables.player;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.util.CancellableRunnable;
import com.gmail.nossr50.util.player.UserManager;

/**
 * §81. Flushes the accumulated {@code /mcstats <skill> keep} chat echo for every tracked player.
 *
 * <p>The echo used to send one chat line per XP gain, which is one line per block mined and one per
 * HIT landed — several a second on exactly the skills a player switches it on for. Gains now
 * accumulate on the {@link McMMOPlayer} and this task turns each window into a single summary line.
 *
 * <p>It is a TIMER rather than a "have five seconds passed?" check on the next gain, because the
 * lazy form can never flush the last window: a player who mines for three seconds and stops would
 * see nothing at all. Scheduled at server start and cancelled at stop with every other task
 * ({@code McMMOMod#XP_CHAT_FLUSH_TICKS}).
 *
 * <p>Fan-out only — what a flush prints, and whether it prints at all, is
 * {@link McMMOPlayer#flushXpChat()}'s concern. Same shape as {@link ClearRegisteredXPGainTask}.
 */
public class XpChatFlushTask extends CancellableRunnable {
    @Override
    public void run() {
        for (McMMOPlayer mmoPlayer : UserManager.getPlayers()) {
            mmoPlayer.flushXpChat();
        }
    }
}
