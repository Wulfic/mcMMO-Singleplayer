package com.gmail.nossr50.skills.salvage.salvageables;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link SalvageableManager} backed by a {@code HashMap} keyed on the item's vanilla registry path.
 */
public class SimpleSalvageableManager implements SalvageableManager {
    private final Map<String, Salvageable> salvageables;

    public SimpleSalvageableManager() {
        this(55);
    }

    public SimpleSalvageableManager(int salvageablesSize) {
        this.salvageables = new HashMap<>(salvageablesSize);
    }

    @Override
    public void registerSalvageable(@NotNull Salvageable salvageable) {
        salvageables.put(salvageable.getItemMaterial(), salvageable);
    }

    @Override
    public void registerSalvageables(@NotNull List<Salvageable> salvageables) {
        for (Salvageable salvageable : salvageables) {
            registerSalvageable(salvageable);
        }
    }

    @Override
    public boolean isSalvageable(@NotNull String itemRegistryPath) {
        return salvageables.containsKey(itemRegistryPath);
    }

    @Override
    public @Nullable Salvageable getSalvageable(@NotNull String itemRegistryPath) {
        return salvageables.get(itemRegistryPath);
    }
}
