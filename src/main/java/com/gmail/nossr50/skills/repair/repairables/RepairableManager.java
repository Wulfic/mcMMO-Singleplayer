package com.gmail.nossr50.skills.repair.repairables;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Registry of {@link Repairable} definitions, keyed by the item's vanilla registry path. Ported
 * MC-free (registry-path keyed) from the Bukkit {@code Material}-keyed original.
 */
public interface RepairableManager {
    /**
     * Register a repairable.
     *
     * @param repairable the repairable to register
     */
    void registerRepairable(@NotNull Repairable repairable);

    /**
     * Register a list of repairables.
     *
     * @param repairables the repairables to register
     */
    void registerRepairables(@NotNull List<Repairable> repairables);

    /**
     * @param itemRegistryPath the item's vanilla registry path
     * @return true if an item with this registry path is repairable
     */
    boolean isRepairable(@NotNull String itemRegistryPath);

    /**
     * @param itemRegistryPath the item's vanilla registry path
     * @return the repairable for this item, or {@code null} if none is registered
     */
    @Nullable Repairable getRepairable(@NotNull String itemRegistryPath);
}
