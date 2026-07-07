package com.gmail.nossr50.skills.excavation;

import static com.gmail.nossr50.util.text.ConfigStringUtils.getMaterialConfigString;

import com.gmail.nossr50.config.treasure.TreasureConfig;
import com.gmail.nossr50.datatypes.treasure.ExcavationTreasure;
import com.gmail.nossr50.fabric.McMMOMod;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class Excavation {
    private Excavation() {}

    /**
     * Get the list of possible {@link ExcavationTreasure ExcavationTreasures} obtained from a given
     * block.
     *
     * <p><b>Port note:</b> legacy took a Bukkit {@code Block} and read {@code
     * block.getBlockData().getMaterial()}; we take the block's vanilla registry path directly (e.g.
     * {@code "dirt"}, from {@code PlatformBlock#getTypeId()} at the call site) and map it to the
     * config-friendly key ({@code "Dirt"}) the way the legacy code did via {@code Material}.
     *
     * @param blockRegistryPath the block's vanilla registry path
     * @return the list of treasures that could be found (empty if none, or if the config is un-wired)
     */
    protected static @NotNull List<ExcavationTreasure> getTreasures(
            @NotNull String blockRegistryPath) {
        final TreasureConfig treasureConfig = McMMOMod.getTreasureConfig();
        if (treasureConfig == null) {
            return new ArrayList<>();
        }

        final String friendly = getMaterialConfigString(blockRegistryPath);
        return treasureConfig.excavationMap.getOrDefault(friendly, new ArrayList<>());
    }
}
