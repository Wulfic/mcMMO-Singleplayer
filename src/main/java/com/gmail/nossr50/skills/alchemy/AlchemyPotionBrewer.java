package com.gmail.nossr50.skills.alchemy;

import com.gmail.nossr50.config.skills.alchemy.PotionConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.alchemy.AlchemyPotion;
import com.gmail.nossr50.datatypes.skills.alchemy.PotionStage;
import com.gmail.nossr50.fabric.McMMOMod;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.collection.DefaultedList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The brew-resolution half of mcMMO Alchemy (K7): given a brewing-stand inventory, decide whether a
 * configured mcMMO brew applies and, on completion, transform the input potions into their child
 * potions, consume one ingredient, and award the per-stage Alchemy XP.
 *
 * <p>Retargeted from the legacy Bukkit {@code AlchemyPotionBrewer} (which drove a Bukkit
 * {@code BrewerInventory} from a cancelled {@code BrewEvent} + a scheduler-driven
 * {@code AlchemyBrewTask}) onto the vanilla brewing-stand machinery. mcMMO's brewing tree includes
 * recipes vanilla does not know, so it must take over recipe recognition and the actual craft: the
 * {@code fabric/mixin/BrewingStandBlockEntityMixin} injects into the block entity's private
 * {@code canCraft}/{@code craft} statics, forcing {@code canCraft} true for a recognised mcMMO brew
 * (so vanilla still runs the fuel/timer/particles/GUI) and replacing {@code craft} with
 * {@link #finishBrewing}. {@code fabric/listeners/AlchemyListener} supplies the brewing player (owner)
 * for the XP award, and shortens the timer vanilla runs by the owner's Catalysis brew speed
 * ({@link CatalysisTimer}) — between them, that is the whole of the legacy {@code AlchemyBrewTask}.
 *
 * <p>The five brewing-stand slots follow vanilla's layout: three potion bottles (0–2), the ingredient
 * ({@link #INGREDIENT_SLOT} = 3), and the blaze-powder fuel (4). All logic here operates directly on
 * the block entity's {@link DefaultedList} (the same list vanilla mutates), so it is unit-testable
 * with a hand-built inventory under the registry harness.
 *
 * <p><b>Deferred vs legacy</b> (breadcrumbs — the numeric cores are ported + tested, only the
 * MC-typed wiring is deferred):
 * <ul>
 *   <li><b>Concoctions ingredient-tier gating</b> — {@link #isValidBrew} recognises any configured
 *       Concoctions ingredient (max tier) because the {@code canCraft} injection point has no
 *       {@code BlockPos} to resolve the brewing player's Concoctions rank. Gating recipe recognition
 *       by the owner's tier would risk a brew that starts (via {@code canCraft}) but never completes
 *       (a below-tier {@code craft}), looping the brew bar forever. The tier lookup itself
 *       ({@link AlchemyManager#getTier}) is ported.</li>
 *   <li><b>Custom potion display</b> — name/lore/colour, deferred cosmetically in {@link AlchemyPotion}.</li>
 * </ul>
 */
public final class AlchemyPotionBrewer {

    /** Ingredient slot in a vanilla brewing-stand inventory (bottles are 0–2, fuel is 4). */
    public static final int INGREDIENT_SLOT = 3;

    private static final int[] BOTTLE_SLOTS = {0, 1, 2};

    private AlchemyPotionBrewer() {
    }

    /** Whether the stack is absent (null / empty). */
    public static boolean isEmpty(@Nullable ItemStack item) {
        return item == null || item.isEmpty();
    }

    /**
     * Whether the ingredient is a configured Concoctions ingredient. Checked against the top tier
     * (every ingredient) — see the class doc on the deferred tier gating. A no-op ({@code false})
     * when {@link PotionConfig} is not loaded (no world session), so vanilla brewing is untouched.
     */
    public static boolean isValidIngredient(@Nullable ItemStack ingredient) {
        if (isEmpty(ingredient)) {
            return false;
        }
        final PotionConfig config = McMMOMod.getPotionConfig();
        if (config == null) {
            return false;
        }
        for (ItemStack candidate : config.getIngredients(8)) {
            if (ItemStack.areItemsAndComponentsEqual(ingredient, candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the brewing stand's contents form a valid mcMMO brew: the ingredient slot holds a
     * recognised Concoctions ingredient and at least one bottle is a configured potion that has a
     * child transition for that ingredient. This is what the {@code canCraft} injection forces true
     * (and what {@link #finishBrewing} re-checks before taking over the craft), so the two stay
     * consistent across the brew.
     *
     * @param slots the brewing-stand inventory (vanilla's 5-slot list)
     */
    public static boolean isValidBrew(@NotNull DefaultedList<ItemStack> slots) {
        final PotionConfig config = McMMOMod.getPotionConfig();
        if (config == null) {
            return false;
        }
        final ItemStack ingredient = slots.get(INGREDIENT_SLOT);
        if (!isValidIngredient(ingredient)) {
            return false; // fast path: no valid ingredient → not our brew, skip the potion scan.
        }
        for (int slot : BOTTLE_SLOTS) {
            final ItemStack bottle = slots.get(slot);
            if (isEmpty(bottle) || bottle.isOf(Items.GLASS_BOTTLE)) {
                continue;
            }
            final AlchemyPotion potion = config.getPotion(bottle);
            if (potion != null && potion.getChild(ingredient) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Complete an mcMMO brew (the {@code craft} replacement): for every bottle that has a child
     * transition for the ingredient, replace it with the child potion; consume one ingredient; and,
     * when a brewing player is known, award the per-stage Alchemy XP. Port of legacy
     * {@code AlchemyPotionBrewer.finishBrewing} operating on the vanilla inventory list.
     *
     * <p>Unlike legacy (which bailed when the player was {@code null}), an unattended brew — e.g. a
     * hopper-fed stand the player never opened — still completes, so custom potions are not left
     * stuck mid-brew; it simply earns no XP. XP is awarded per transformed bottle (amount 1 each),
     * matching legacy.
     *
     * @param slots     the brewing-stand inventory to mutate in place
     * @param mmoPlayer the brewing player for the XP award, or {@code null} for an unattended brew
     */
    public static void finishBrewing(@NotNull DefaultedList<ItemStack> slots,
            @Nullable McMMOPlayer mmoPlayer) {
        final PotionConfig config = McMMOMod.getPotionConfig();
        if (config == null) {
            return;
        }
        final ItemStack ingredient = slots.get(INGREDIENT_SLOT);
        if (!isValidIngredient(ingredient)) {
            return;
        }

        boolean brewedAny = false;
        for (int slot : BOTTLE_SLOTS) {
            final ItemStack bottle = slots.get(slot);
            if (isEmpty(bottle) || bottle.isOf(Items.GLASS_BOTTLE)
                    || !config.isValidPotion(bottle)) {
                continue;
            }

            final AlchemyPotion input = config.getPotion(bottle);
            final AlchemyPotion output = input == null ? null : input.getChild(ingredient);
            if (output == null) {
                continue;
            }

            slots.set(slot, output.toItemStack(bottle.getCount()));
            brewedAny = true;

            if (mmoPlayer != null) {
                final PotionStage potionStage = PotionStage.getPotionStage(input, output);
                final AlchemyManager alchemyManager = mmoPlayer.getAlchemyManager();
                if (alchemyManager != null) {
                    alchemyManager.handlePotionBrewSuccesses(potionStage, 1);
                }
            }
        }

        // Only consume the ingredient if something actually brewed (mirrors the canCraft gate).
        if (brewedAny) {
            consumeIngredient(slots);
        }
    }

    /** Remove one ingredient from the ingredient slot (legacy {@code removeIngredient}). */
    private static void consumeIngredient(@NotNull DefaultedList<ItemStack> slots) {
        final ItemStack ingredient = slots.get(INGREDIENT_SLOT);
        if (isEmpty(ingredient)) {
            return;
        }
        if (ingredient.getCount() <= 1) {
            slots.set(INGREDIENT_SLOT, ItemStack.EMPTY);
        } else {
            ingredient.decrement(1);
        }
    }
}
