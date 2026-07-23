package com.gmail.nossr50.runnables;

import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.util.CancellableRunnable;
import com.gmail.nossr50.util.LogUtils;
import com.gmail.nossr50.util.player.UserManager;

/**
 * Periodic crash-safety autosave of mcMMO's world data (Phase 5 / Phase 11).
 *
 * <p>Scheduled as a repeating task at server start (interval = {@code General.Save_Interval}
 * minutes) and cancelled at server stop. Replaces the legacy Bukkit task, which fanned each
 * profile out onto FoliaLib's async scheduler and also saved parties. In singleplayer the party
 * system is cut and the flatfile store is small, so this simply flushes all tracked profiles
 * synchronously on the tick thread via {@link UserManager#saveAll()}.
 *
 * <p>It also flushes the §A hand-placed-block flags, which legacy did not do here only because its
 * {@code HashChunkManager} wrote them out on chunk unload instead. Both saves matter for the same
 * reason: a crash between here and the clean shutdown save must not roll the world back to the last
 * good write — for the flags that would hand back the place → mine → repeat XP farm.
 */
public class SaveTimerTask extends CancellableRunnable {

    @Override
    public void run() {
        LogUtils.debug("[User Data] Autosaving all online players...");
        UserManager.saveAll();
        McMMOMod.savePlacedBlocks();
    }
}
