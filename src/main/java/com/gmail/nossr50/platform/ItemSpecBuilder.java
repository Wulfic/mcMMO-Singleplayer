package com.gmail.nossr50.platform;

import com.gmail.nossr50.datatypes.treasure.ItemSpec;
import com.gmail.nossr50.platform.text.TextUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds a live {@link ItemStack} from an MC-free {@link ItemSpec} blueprint (CONVERSION_TODO
 * Phase 10.4 item-spawn). This is the post-bootstrap builder {@link ItemSpec} was designed around:
 * configs parse to {@code ItemSpec}s at mod-load (when the item registry isn't populated yet), and
 * this class turns one into a real stack at spawn time, when a world — and therefore the registries —
 * exists.
 *
 * <p>Kept in {@code platform/} (not a skill manager) because it touches vanilla item/registry and
 * data-component types; managers stay MC-free and decide <i>which</i> {@code ItemSpec}s to spawn,
 * this performs the construction. It is the treasure-drop analogue of {@link BlockDrops}.
 *
 * <p>The §-coded display name / lore carried by the spec are parsed to vanilla {@link Text} via the
 * Phase 7 {@link TextUtils#toText} parser and attached as {@code CUSTOM_NAME} / {@code LORE}
 * data components — the 1.21 replacement for the legacy {@code ItemMeta} display-name/lore path.
 *
 * <p>A spec carrying an {@link ItemSpec.PotionSpec} additionally gets a
 * {@link PotionContentsComponent}, the 1.21 replacement for legacy's {@code PotionMeta}
 * base-potion-type path. This is where the config's {@code PotionData} strings finally meet the
 * registry: the lookup is deferred to here (rather than done at config load) to keep
 * {@code FishingTreasureConfig} and {@link ItemSpec} MC-free.
 */
public final class ItemSpecBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger("mcMMO/ItemSpecBuilder");

    private ItemSpecBuilder() {
    }

    /**
     * Construct the {@link ItemStack} described by {@code spec}, or empty if its material id has no
     * vanilla item (the miss is logged by {@link Materials#item}). The stack size is clamped to at
     * least 1 so a malformed {@code Amount: 0} config never yields an empty stack.
     *
     * <p>An unresolvable potion type also yields empty rather than a plain water bottle: legacy
     * rejected such a treasure outright at config-load, and dropping nothing is the faithful
     * analogue at this (deferred) resolution point — the same contract an unknown material has.
     *
     * @param spec the MC-free blueprint parsed from a treasure/loot config
     * @return the built stack, or {@link Optional#empty()} if the material or potion type is unknown
     */
    public static @NotNull Optional<ItemStack> build(@NotNull ItemSpec spec) {
        final Optional<Item> item = Materials.item(spec.getMaterialId());
        if (item.isEmpty()) {
            return Optional.empty();
        }
        final ItemStack stack = new ItemStack(item.get(), Math.max(1, spec.getAmount()));

        final ItemSpec.PotionSpec potion = spec.getPotion();
        if (potion != null) {
            final Optional<Holder<Potion>> base = Potions.matchPotion(
                    potion.potionType(), potion.upgraded(), potion.extended());
            if (base.isEmpty()) {
                LOGGER.warn("Could not resolve potion type '{}' (upgraded={}, extended={}) for"
                                + " treasure '{}'; dropping nothing.",
                        potion.potionType(), potion.upgraded(), potion.extended(),
                        spec.getMaterialId());
                return Optional.empty();
            }
            stack.set(DataComponents.POTION_CONTENTS, new PotionContents(base.get()));
        }

        if (spec.getCustomName() != null) {
            stack.set(DataComponents.CUSTOM_NAME, TextUtils.toText(spec.getCustomName()));
        }

        final List<String> lore = spec.getLore();
        if (!lore.isEmpty()) {
            final List<Component> lines = new ArrayList<>(lore.size());
            for (String line : lore) {
                lines.add(TextUtils.toText(line));
            }
            stack.set(DataComponents.LORE, new ItemLore(lines));
        }

        return Optional.of(stack);
    }
}
