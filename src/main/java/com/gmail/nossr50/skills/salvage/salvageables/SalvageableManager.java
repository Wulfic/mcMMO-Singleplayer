package com.gmail.nossr50.skills.salvage.salvageables;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Registry of {@link Salvageable} definitions, keyed by the item's vanilla registry path. Ported
 * MC-free (registry-path keyed) from the Bukkit {@code Material}-keyed original.
 */
public interface SalvageableManager {
    void registerSalvageable(@NotNull Salvageable salvageable);

    void registerSalvageables(@NotNull List<Salvageable> salvageables);

    boolean isSalvageable(@NotNull String itemRegistryPath);

    @Nullable Salvageable getSalvageable(@NotNull String itemRegistryPath);
}
