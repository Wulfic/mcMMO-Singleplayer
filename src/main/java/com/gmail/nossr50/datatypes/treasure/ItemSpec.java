package com.gmail.nossr50.datatypes.treasure;

import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An MC-free blueprint for a treasure/loot item, parsed out of the config at load time.
 *
 * <p><b>Why this exists (port note):</b> the legacy plugin stored a live {@code
 * org.bukkit.inventory.ItemStack} on each {@link Treasure}, constructed while parsing the YAML. That
 * is impossible here for two reasons locked in earlier phases:
 * <ul>
 *   <li>configs load at mod-init, but the {@code net.minecraft} item/block <b>registries are only
 *       populated after Minecraft bootstrap</b> (see the {@code platform/Materials} gotcha), so a
 *       real {@code ItemStack} cannot be built during YAML parse;</li>
 *   <li>enchanted / potion items in 1.21 need a world's {@code RegistryManager} to resolve the
 *       dynamic enchantment / potion registries — not available at config-load either.</li>
 * </ul>
 *
 * <p>So we keep the parsed <i>intent</i> here — a vanilla registry-path material id (e.g.
 * {@code "heart_of_the_sea"}), a stack size, and optional §-coded display name / lore — and defer
 * the actual {@code ItemStack} construction to spawn time via a post-bootstrap builder.
 *
 * <p><b>PORT Phase 10 (item-spawn):</b> the builder that turns an {@code ItemSpec} into a real
 * {@code net.minecraft.item.ItemStack} (via {@code platform/Materials} + {@code PlatformItem}, and
 * the registry manager for name/lore data-components) lands with the item-spawn adapter, when the
 * first skill that actually drops treasure ({@code ExcavationManager#excavationBlockCheck}) is fully
 * wired. Potion / enchantment fields are intentionally omitted here until {@code FishingTreasureConfig}
 * ports and needs them.
 */
public final class ItemSpec {

    private final @NotNull String materialId;
    private final int amount;
    private final @Nullable String customName;
    private final @NotNull List<String> lore;

    public ItemSpec(@NotNull String materialId, int amount, @Nullable String customName,
            @NotNull List<String> lore) {
        this.materialId = Objects.requireNonNull(materialId, "materialId");
        this.amount = amount;
        this.customName = customName;
        this.lore = List.copyOf(Objects.requireNonNull(lore, "lore"));
    }

    public ItemSpec(@NotNull String materialId, int amount) {
        this(materialId, amount, null, List.of());
    }

    /** Vanilla registry path of the item (namespace stripped, e.g. {@code "heart_of_the_sea"}). */
    public @NotNull String getMaterialId() {
        return materialId;
    }

    public int getAmount() {
        return amount;
    }

    /** Optional §-coded display name, or {@code null} if the item uses its default name. */
    public @Nullable String getCustomName() {
        return customName;
    }

    /** §-coded lore lines; empty when none. */
    public @NotNull List<String> getLore() {
        return lore;
    }

    @Override
    public String toString() {
        return "ItemSpec{materialId='" + materialId + '\'' + ", amount=" + amount
                + ", customName='" + customName + '\'' + ", lore=" + lore + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ItemSpec other)) {
            return false;
        }
        return amount == other.amount && materialId.equals(other.materialId)
                && Objects.equals(customName, other.customName) && lore.equals(other.lore);
    }

    @Override
    public int hashCode() {
        return Objects.hash(materialId, amount, customName, lore);
    }
}
