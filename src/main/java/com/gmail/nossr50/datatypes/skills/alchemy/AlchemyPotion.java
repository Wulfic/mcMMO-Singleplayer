package com.gmail.nossr50.datatypes.skills.alchemy;

import static java.util.Objects.requireNonNull;

import com.gmail.nossr50.fabric.McMMOMod;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A configured Alchemy potion: the resulting {@link ItemStack} (a POTION / SPLASH_POTION /
 * LINGERING_POTION carrying a {@link PotionContentsComponent}) plus the ingredient → child-potion
 * map that drives the brewing tree.
 *
 * <p>Retargeted from Bukkit's {@code ItemStack}/{@code PotionMeta} onto vanilla data components.
 * Two potions are considered "the same" (for recognising a brewing-stand input and matching an
 * ingredient's child) by their <em>functional</em> identity — item type, base potion, and custom
 * effects — which is exactly what mcMMO's brew resolution keys on. The legacy display comparison of
 * custom name / lore / colour is cosmetic and deferred (breadcrumbed below), so those are not set on
 * the built stack either, keeping config potions and their brewed outputs consistently matchable
 * against vanilla potions.
 */
public class AlchemyPotion {
    private final @NotNull String potionConfigName;
    private final @NotNull ItemStack potionItemStack;
    private final @NotNull Map<ItemStack, String> alchemyPotionChildren;

    public AlchemyPotion(@NotNull String potionConfigName, @NotNull ItemStack potionItemStack,
            @NotNull Map<ItemStack, String> alchemyPotionChildren) {
        this.potionConfigName = requireNonNull(potionConfigName, "potionConfigName cannot be null");
        this.potionItemStack = requireNonNull(potionItemStack, "potionItemStack cannot be null");
        this.alchemyPotionChildren = requireNonNull(alchemyPotionChildren,
                "alchemyPotionChildren cannot be null");
    }

    public @NotNull String getPotionConfigName() {
        return potionConfigName;
    }

    /** A fresh copy of this potion's item stack with the requested (min 1) count. */
    public @NotNull ItemStack toItemStack(int amount) {
        final ItemStack clone = potionItemStack.copy();
        clone.setCount(Math.max(1, amount));
        return clone;
    }

    public @NotNull Map<ItemStack, String> getAlchemyPotionChildren() {
        return alchemyPotionChildren;
    }

    /**
     * The potion this one brews into when the given ingredient is added, or {@code null} if the
     * ingredient is not a valid child transition for this potion.
     */
    public @Nullable AlchemyPotion getChild(@NotNull ItemStack ingredient) {
        if (!alchemyPotionChildren.isEmpty()) {
            for (Map.Entry<ItemStack, String> child : alchemyPotionChildren.entrySet()) {
                if (ItemStack.areItemsAndComponentsEqual(ingredient, child.getKey())) {
                    return McMMOMod.getPotionConfig() == null
                            ? null
                            : McMMOMod.getPotionConfig().getPotion(child.getValue());
                }
            }
        }
        return null;
    }

    /** The potion contents component of the built stack (never null — always a potion item). */
    public @Nullable PotionContentsComponent getPotionContents() {
        return potionItemStack.get(DataComponentTypes.POTION_CONTENTS);
    }

    public boolean isSplash() {
        return potionItemStack.isOf(Items.SPLASH_POTION);
    }

    public boolean isLingering() {
        return potionItemStack.isOf(Items.LINGERING_POTION);
    }

    /**
     * Whether {@code otherPotion} is functionally the same potion as this one: same item type, same
     * base potion, and the same set of custom effects (type + amplifier + duration). Custom
     * name/lore/colour matching is deferred (cosmetic — see class doc).
     */
    public boolean isSimilarPotion(@NotNull ItemStack otherPotion) {
        requireNonNull(otherPotion, "otherPotion cannot be null");

        if (!otherPotion.isOf(potionItemStack.getItem())) {
            return false;
        }

        final PotionContentsComponent mine = getPotionContents();
        final PotionContentsComponent theirs = otherPotion.get(DataComponentTypes.POTION_CONTENTS);
        if (mine == null || theirs == null) {
            return mine == theirs;
        }

        if (!Objects.equals(mine.potion(), theirs.potion())) {
            return false;
        }

        return sameCustomEffects(mine.customEffects(), theirs.customEffects());
    }

    private static boolean sameCustomEffects(@NotNull List<StatusEffectInstance> a,
            @NotNull List<StatusEffectInstance> b) {
        if (a.size() != b.size()) {
            return false;
        }
        // Order-independent: every effect on one side must have an amplifier/duration match on the
        // other. mcMMO potions carry at most a couple of custom effects, so the nested scan is cheap.
        for (StatusEffectInstance effect : a) {
            boolean matched = false;
            for (StatusEffectInstance other : b) {
                if (Objects.equals(effect.getEffectType(), other.getEffectType())
                        && effect.getAmplifier() == other.getAmplifier()
                        && effect.getDuration() == other.getDuration()) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AlchemyPotion that = (AlchemyPotion) o;
        return Objects.equals(potionConfigName, that.potionConfigName)
                && ItemStack.areEqual(potionItemStack, that.potionItemStack)
                && Objects.equals(alchemyPotionChildren, that.alchemyPotionChildren);
    }

    @Override
    public int hashCode() {
        return Objects.hash(potionConfigName, alchemyPotionChildren);
    }

    @Override
    public String toString() {
        return "AlchemyPotion{potionConfigName='" + potionConfigName + "', potionItemStack="
                + potionItemStack + ", alchemyPotionChildren=" + alchemyPotionChildren + '}';
    }
}
