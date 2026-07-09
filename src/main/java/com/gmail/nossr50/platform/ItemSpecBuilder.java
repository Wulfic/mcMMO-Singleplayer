package com.gmail.nossr50.platform;

import com.gmail.nossr50.datatypes.treasure.ItemSpec;
import com.gmail.nossr50.util.text.TextUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

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
 */
public final class ItemSpecBuilder {

    private ItemSpecBuilder() {
    }

    /**
     * Construct the {@link ItemStack} described by {@code spec}, or empty if its material id has no
     * vanilla item (the miss is logged by {@link Materials#item}). The stack size is clamped to at
     * least 1 so a malformed {@code Amount: 0} config never yields an empty stack.
     *
     * @param spec the MC-free blueprint parsed from a treasure/loot config
     * @return the built stack, or {@link Optional#empty()} if the material is unknown
     */
    public static @NotNull Optional<ItemStack> build(@NotNull ItemSpec spec) {
        final Optional<Item> item = Materials.item(spec.getMaterialId());
        if (item.isEmpty()) {
            return Optional.empty();
        }
        final ItemStack stack = new ItemStack(item.get(), Math.max(1, spec.getAmount()));

        if (spec.getCustomName() != null) {
            stack.set(DataComponentTypes.CUSTOM_NAME, TextUtils.toText(spec.getCustomName()));
        }

        final List<String> lore = spec.getLore();
        if (!lore.isEmpty()) {
            final List<Text> lines = new ArrayList<>(lore.size());
            for (String line : lore) {
                lines.add(TextUtils.toText(line));
            }
            stack.set(DataComponentTypes.LORE, new LoreComponent(lines));
        }

        return Optional.of(stack);
    }
}
