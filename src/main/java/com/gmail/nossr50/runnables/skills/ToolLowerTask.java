package com.gmail.nossr50.runnables.skills;

import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.ToolType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.util.CancellableRunnable;
import com.gmail.nossr50.util.player.NotificationManager;

/**
 * Ends a tool-preparation window (Phase 11). When a player readies a super-ability tool (e.g. holds
 * right-click with a pickaxe), mcMMO flips the matching {@link ToolType} into "preparation mode" and
 * schedules this task; if the ability isn't activated within the window, this clears the mode and
 * tells the player their tool was lowered.
 *
 * <p>Singleplayer port of legacy {@code ToolLowerTask}: unchanged except the feedback now routes
 * through the ported {@link NotificationManager} (which takes an {@link McMMOPlayer} instead of a
 * Bukkit {@code Player}) and the config read goes through {@link McMMOMod#getGeneralConfig()}.
 */
public class ToolLowerTask extends CancellableRunnable {
    private final McMMOPlayer mmoPlayer;
    private final ToolType tool;

    public ToolLowerTask(McMMOPlayer mmoPlayer, ToolType tool) {
        this.mmoPlayer = mmoPlayer;
        this.tool = tool;
    }

    @Override
    public void run() {
        if (!mmoPlayer.getToolPreparationMode(tool)) {
            return;
        }

        mmoPlayer.setToolPreparationMode(tool, false);

        if (McMMOMod.getGeneralConfig().getAbilityMessagesEnabled()) {
            NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.TOOL,
                    tool.getLowerTool());
        }
    }
}
