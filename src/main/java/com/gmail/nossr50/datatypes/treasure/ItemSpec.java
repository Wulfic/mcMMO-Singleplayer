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
 * {@code "heart_of_the_sea"}), a stack size, optional §-coded display name / lore, and an optional
 * {@link PotionSpec} base type — and defer the actual {@code ItemStack} construction to spawn time
 * via a post-bootstrap builder ({@code platform/ItemSpecBuilder}).
 *
 * <p><b>The potion base type is carried as the config's own strings, not a resolved registry
 * entry.</b> {@code Registries.POTION} is a <i>static</i> registry and would in fact be populated by
 * the time configs load, but resolving here would drag {@code net.minecraft} into this datatype and
 * into {@code FishingTreasureConfig}, both of which are deliberately MC-free and plain-JUnit
 * testable. Deferring the lookup to {@code ItemSpecBuilder} keeps that property and matches the
 * resolve-at-use-time shape the Shake entity paths and the Magic Hunter enchantment paths already
 * use. The one behavioural consequence is that an unresolvable potion type is reported at drop time
 * rather than rejected at load, where legacy dropped the whole treasure.
 */
public final class ItemSpec {

    /**
     * The parsed {@code PotionData} block of a potion treasure — legacy's {@code PotionType} name
     * plus its two variant flags, verbatim from the config.
     *
     * <p>In modern Minecraft "upgraded" and "extended" are not flags but distinct registry entries
     * with {@code strong_} / {@code long_} id prefixes, so these three fields collapse into a single
     * lookup at build time; see {@code PotionUtil#matchPotion}, which also translates the legacy
     * Bukkit type names the shipped config still uses ({@code INSTANT_HEAL}, {@code SPEED}, …).
     *
     * @param potionType the {@code PotionData.PotionType} string ({@code "WATER"} when unset, as legacy)
     * @param upgraded   the {@code PotionData.Upgraded} flag (amplified — {@code strong_} variant)
     * @param extended   the {@code PotionData.Extended} flag ({@code long_} variant)
     */
    public record PotionSpec(@NotNull String potionType, boolean upgraded, boolean extended) {
        public PotionSpec {
            Objects.requireNonNull(potionType, "potionType");
        }
    }

    private final @NotNull String materialId;
    private final int amount;
    private final @Nullable String customName;
    private final @NotNull List<String> lore;
    private final @Nullable PotionSpec potion;

    public ItemSpec(@NotNull String materialId, int amount, @Nullable String customName,
            @NotNull List<String> lore, @Nullable PotionSpec potion) {
        this.materialId = Objects.requireNonNull(materialId, "materialId");
        this.amount = amount;
        this.customName = customName;
        this.lore = List.copyOf(Objects.requireNonNull(lore, "lore"));
        this.potion = potion;
    }

    public ItemSpec(@NotNull String materialId, int amount, @Nullable String customName,
            @NotNull List<String> lore) {
        this(materialId, amount, customName, lore, null);
    }

    public ItemSpec(@NotNull String materialId, int amount) {
        this(materialId, amount, null, List.of(), null);
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

    /**
     * The potion base type this item carries, or {@code null} when it is not a potion. Only
     * meaningful for the three potion items ({@code potion}, {@code splash_potion},
     * {@code lingering_potion}); the builder ignores it otherwise.
     */
    public @Nullable PotionSpec getPotion() {
        return potion;
    }

    @Override
    public String toString() {
        return "ItemSpec{materialId='" + materialId + '\'' + ", amount=" + amount
                + ", customName='" + customName + '\'' + ", lore=" + lore
                + ", potion=" + potion + '}';
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
                && Objects.equals(customName, other.customName) && lore.equals(other.lore)
                && Objects.equals(potion, other.potion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(materialId, amount, customName, lore, potion);
    }
}
