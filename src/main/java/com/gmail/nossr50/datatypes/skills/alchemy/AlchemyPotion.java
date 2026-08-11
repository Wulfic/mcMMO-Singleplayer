package com.gmail.nossr50.datatypes.skills.alchemy;

import static java.util.Objects.requireNonNull;

import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformItem;
import com.gmail.nossr50.platform.Potions;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A configured Alchemy potion: the resulting item (a POTION / SPLASH_POTION / LINGERING_POTION
 * carrying potion contents) plus the ingredient → child-potion map that drives the brewing tree.
 *
 * <p>Retargeted from Bukkit's {@code ItemStack}/{@code PotionMeta} onto vanilla data components, and
 * sealed in Phase 2 slice 5: the stack is held as a {@link PlatformItem} and everything this class
 * decides runs on the Minecraft-free {@link PotionSpec} that {@link Potions#specOf} reads off it, so
 * no Minecraft type appears here.
 *
 * <p>Two potions are considered "the same" (for recognising a brewing-stand input and matching an
 * ingredient's child) by their <em>functional</em> identity — item type, base potion, and custom
 * effects — which is exactly what mcMMO's brew resolution keys on. The legacy display comparison of
 * custom name / lore / colour is cosmetic and deferred (breadcrumbed below), so those are not set on
 * the built stack either, keeping config potions and their brewed outputs consistently matchable
 * against vanilla potions.
 */
public class AlchemyPotion {
    private final @NotNull String potionConfigName;
    private final @NotNull PlatformItem potionItem;
    private final @NotNull Map<PlatformItem, String> alchemyPotionChildren;
    /**
     * Read once at construction rather than per query: the stack is built by {@link
     * com.gmail.nossr50.config.skills.alchemy.PotionConfig} and never mutated afterwards, and the
     * brew path asks for the stage on every completed brew.
     */
    private final @Nullable PotionSpec spec;

    public AlchemyPotion(@NotNull String potionConfigName, @NotNull PlatformItem potionItem,
            @NotNull Map<PlatformItem, String> alchemyPotionChildren) {
        this.potionConfigName = requireNonNull(potionConfigName, "potionConfigName cannot be null");
        this.potionItem = requireNonNull(potionItem, "potionItem cannot be null");
        this.alchemyPotionChildren = requireNonNull(alchemyPotionChildren,
                "alchemyPotionChildren cannot be null");
        this.spec = Potions.specOf(potionItem);
    }

    public @NotNull String getPotionConfigName() {
        return potionConfigName;
    }

    /**
     * The Minecraft-free description of this potion's contents, or {@code null} if the stack carries
     * no potion contents at all (which a configured potion never does — {@code PotionConfig} refuses
     * to build one without a resolved base type).
     */
    public @Nullable PotionSpec getSpec() {
        return spec;
    }

    /** A fresh copy of this potion's item with the requested (min 1) count. */
    public @NotNull PlatformItem toItem(int amount) {
        return potionItem.copyWithCount(Math.max(1, amount));
    }

    public @NotNull Map<PlatformItem, String> getAlchemyPotionChildren() {
        return alchemyPotionChildren;
    }

    /**
     * The potion this one brews into when the given ingredient is added, or {@code null} if the
     * ingredient is not a valid child transition for this potion.
     */
    public @Nullable AlchemyPotion getChild(@NotNull PlatformItem ingredient) {
        if (!alchemyPotionChildren.isEmpty()) {
            for (Map.Entry<PlatformItem, String> child : alchemyPotionChildren.entrySet()) {
                if (ingredient.matchesItemAndComponents(child.getKey())) {
                    return McMMOMod.getPotionConfig() == null
                            ? null
                            : McMMOMod.getPotionConfig().getPotion(child.getValue());
                }
            }
        }
        return null;
    }

    public boolean isSplash() {
        return spec != null && spec.form() == PotionForm.SPLASH;
    }

    public boolean isLingering() {
        return spec != null && spec.form() == PotionForm.LINGERING;
    }

    /**
     * Whether {@code otherPotion} is functionally the same potion as this one: same item type, same
     * base potion, and the same set of custom effects (type + amplifier + duration). Custom
     * name/lore/colour matching is deferred (cosmetic — see class doc).
     */
    public boolean isSimilarPotion(@NotNull PlatformItem otherPotion) {
        requireNonNull(otherPotion, "otherPotion cannot be null");
        return isSimilarPotion(otherPotion, Potions.specOf(otherPotion));
    }

    /**
     * As {@link #isSimilarPotion(PlatformItem)}, but taking the candidate's already-read
     * {@link PotionSpec}.
     *
     * <p>This overload exists for {@code PotionConfig#getPotion}, which asks the same question of one
     * stack against every configured potion in the tree. Reading the spec per candidate would mean a
     * registry lookup and an allocation per configured potion <em>per tick</em> — vanilla calls
     * {@code BrewingStandBlockEntity#canCraft} from {@code tick}, and that is what
     * {@code AlchemyPotionBrewer#isValidBrew} hangs off. Hoisted, the loop body is pure comparison of
     * two already-built specs.
     *
     * @param otherSpec {@code otherPotion}'s spec, or {@code null} if it carries no potion contents
     */
    public boolean isSimilarPotion(@NotNull PlatformItem otherPotion,
            @Nullable PotionSpec otherSpec) {
        requireNonNull(otherPotion, "otherPotion cannot be null");

        if (!potionItem.isSimilar(otherPotion)) {
            return false;
        }

        if (spec == null || otherSpec == null) {
            // One of them carries no potion contents at all; equal only if neither does.
            return spec == null && otherSpec == null;
        }

        return spec.matchesContents(otherSpec);
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
                && potionItem.matchesExactly(that.potionItem)
                && Objects.equals(alchemyPotionChildren, that.alchemyPotionChildren);
    }

    @Override
    public int hashCode() {
        return Objects.hash(potionConfigName, alchemyPotionChildren);
    }

    @Override
    public String toString() {
        return "AlchemyPotion{potionConfigName='" + potionConfigName + "', item="
                + potionItem.getTypePath() + ", spec=" + spec + ", alchemyPotionChildren="
                + alchemyPotionChildren + '}';
    }
}
