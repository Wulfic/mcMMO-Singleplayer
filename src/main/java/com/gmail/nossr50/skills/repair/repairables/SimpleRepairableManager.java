package com.gmail.nossr50.skills.repair.repairables;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link RepairableManager} backed by a {@code HashMap} keyed on the item's vanilla registry path.
 */
public class SimpleRepairableManager implements RepairableManager {
    private final Map<String, Repairable> repairables;

    public SimpleRepairableManager() {
        this(55);
    }

    public SimpleRepairableManager(int repairablesSize) {
        this.repairables = new HashMap<>(repairablesSize);
    }

    @Override
    public void registerRepairable(@NotNull Repairable repairable) {
        repairables.put(repairable.getItemMaterial(), repairable);
    }

    @Override
    public void registerRepairables(@NotNull List<Repairable> repairables) {
        for (Repairable repairable : repairables) {
            registerRepairable(repairable);
        }
    }

    @Override
    public boolean isRepairable(@NotNull String itemRegistryPath) {
        return repairables.containsKey(itemRegistryPath);
    }

    @Override
    public @Nullable Repairable getRepairable(@NotNull String itemRegistryPath) {
        return repairables.get(itemRegistryPath);
    }
}
