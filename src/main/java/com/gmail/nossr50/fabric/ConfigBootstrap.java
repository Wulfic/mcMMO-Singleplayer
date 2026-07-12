package com.gmail.nossr50.fabric;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.CoreSkillsConfig;
import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.RankConfig;
import com.gmail.nossr50.config.SoundConfig;
import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.config.skills.repair.RepairConfig;
import com.gmail.nossr50.config.skills.salvage.SalvageConfig;
import com.gmail.nossr50.config.treasure.TreasureConfig;
import com.gmail.nossr50.skills.repair.repairables.RepairableManager;
import com.gmail.nossr50.skills.repair.repairables.SimpleRepairableManager;
import com.gmail.nossr50.skills.salvage.salvageables.SalvageableManager;
import com.gmail.nossr50.skills.salvage.salvageables.SimpleSalvageableManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;

/**
 * Loads the concrete config files off disk and wires them into the {@link McMMOMod} service
 * locator (finishing the Phase 8 config tier).
 *
 * <p>The legacy plugin loaded every config in {@code onEnable} from {@code getDataFolder()}. Here
 * the data folder is injected as a {@link Path} (resolved from {@code FabricLoader.getConfigDir()}
 * by the caller) so the whole load flow is unit-testable against a temp directory with no
 * Minecraft/Fabric bootstrap — matching the {@link com.gmail.nossr50.config.ConfigLoader} design.
 *
 * <p>{@link GeneralConfig} is loaded and wired <em>first</em>: it carries the RetroMode flag that
 * {@link McMMOMod#isRetroModeEnabled()} reads, and several later configs' getters branch on it
 * (skill-rank ladders, XP curves). Wiring it before the rest means any eager read during their
 * construction sees the correct scaling mode.
 */
public final class ConfigBootstrap {

    private ConfigBootstrap() {
    }

    /**
     * Load every ported config from {@code dataFolder} (creating the directory and writing bundled
     * defaults on first run) and register each with {@link McMMOMod}.
     *
     * @param dataFolder the mod config directory
     * @throws IOException if the config directory cannot be created
     */
    public static void loadAll(@NotNull Path dataFolder) throws IOException {
        Files.createDirectories(dataFolder);

        // GeneralConfig first — it backs McMMOMod.isRetroModeEnabled(), which later configs read.
        final GeneralConfig general = new GeneralConfig(dataFolder);
        McMMOMod.setGeneralConfig(general);

        McMMOMod.setExperienceConfig(new ExperienceConfig(dataFolder));
        McMMOMod.setCoreSkillsConfig(new CoreSkillsConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));
        McMMOMod.setSoundConfig(new SoundConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));
        McMMOMod.setTreasureConfig(new TreasureConfig(dataFolder));

        // K8: repair/salvage item tables. Each config resolves against the live item registry, so it
        // must load after Minecraft's bootstrap (it is, at server start). The parsed definitions are
        // registered into the registry-path-keyed managers the anvil hook looks items up in.
        final RepairConfig repairConfig = new RepairConfig(dataFolder);
        final RepairableManager repairableManager =
                new SimpleRepairableManager(repairConfig.getLoadedRepairables().size());
        repairableManager.registerRepairables(repairConfig.getLoadedRepairables());
        McMMOMod.setRepairableManager(repairableManager);

        final SalvageConfig salvageConfig = new SalvageConfig(dataFolder);
        final SalvageableManager salvageableManager =
                new SimpleSalvageableManager(salvageConfig.getLoadedSalvageables().size());
        salvageableManager.registerSalvageables(salvageConfig.getLoadedSalvageables());
        McMMOMod.setSalvageableManager(salvageableManager);

        McMMOMod.LOGGER.info("mcMMO configs loaded from {}", dataFolder);
    }

    /**
     * Clear the wired configs (server-stop teardown). The next world session reloads them fresh
     * from disk, so any in-game config edits between sessions are picked up.
     */
    public static void unload() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setExperienceConfig(null);
        McMMOMod.setCoreSkillsConfig(null);
        McMMOMod.setRankConfig(null);
        McMMOMod.setSoundConfig(null);
        McMMOMod.setAdvancedConfig(null);
        McMMOMod.setTreasureConfig(null);
        McMMOMod.setRepairableManager(null);
        McMMOMod.setSalvageableManager(null);
    }
}
