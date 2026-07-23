package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.skills.fishing.FishingManager;
import com.gmail.nossr50.skills.herbalism.HerbalismManager;
import com.gmail.nossr50.util.player.UserManager;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.HungerConstants;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * The diet sub-skills: Herbalism <b>Farmer's Diet</b> and Fishing <b>Fisherman's Diet</b> both restore
 * one extra hunger point per rank when the player eats their skill's foods. Replaces legacy
 * {@code EntityListener#onFoodLevelChange}; driven by {@link com.gmail.nossr50.fabric.mixin.FoodComponentMixin}
 * on {@code FoodComponent#onConsume}, which fires after vanilla has already applied the food.
 *
 * <p>The rank math is MC-free on the two managers ({@link HerbalismManager#farmersDiet(int)} /
 * {@link FishingManager#handleFishermanDiet(int)}, both over {@code SkillUtils.handleFoodSkills}); this
 * class owns the item classification and the hunger-bar mutation.
 *
 * <p>Two pieces of legacy's handler collapse here rather than being skipped. Its main-hand/off-hand
 * {@code MaterialMapStore.isFood} probe is gone — Bukkit's event only reported a food <em>level</em>, so
 * legacy had to guess what had been eaten, while our seam is handed the stack. And its
 * {@code foodChange <= 0} early-return becomes the {@code nutrition <= 0} check below: the two are the
 * same quantity, since legacy's food change is exactly the food's nutrition.
 */
public final class FoodListener {

    private FoodListener() {
    }

    /**
     * Apply the eating player's diet sub-skill bonus, if the food belongs to one. Called from the
     * food-consume mixin; a no-op for non-players, client-side calls, and unranked players.
     *
     * @param world the world the consumption happened in
     * @param user  the entity that ate (only players carry mcMMO data)
     * @param stack the stack that was eaten
     * @param food  the food component vanilla just applied
     */
    public static void onFoodConsumed(World world, LivingEntity user, ItemStack stack,
            FoodComponent food) {
        if (world.isClient()) {
            return; // the client half of a singleplayer session also runs consumption; server is authoritative.
        }
        if (!(user instanceof ServerPlayerEntity player)) {
            return; // mobs eat too (e.g. via consumable items); no mcMMO data to read.
        }

        // Legacy's `foodChange <= 0` early-return: nothing to boost when the food restores no hunger.
        final int nutrition = food.nutrition();
        if (nutrition <= 0) {
            return;
        }

        final McMMOPlayer mmoPlayer = UserManager.getPlayer(player.getUuid());
        if (mmoPlayer == null) {
            return; // data not loaded (e.g. mid-join).
        }

        final String itemPath = Registries.ITEM.getId(stack.getItem()).getPath();
        final int boosted;
        if (HerbalismManager.isFarmersDietFood(itemPath)) {
            final HerbalismManager herbalism = mmoPlayer.getHerbalismManager();
            if (herbalism == null || !herbalism.canUseFarmersDiet()) {
                return;
            }
            boosted = herbalism.farmersDiet(nutrition);
        } else if (FishingManager.isFishermansDietFood(itemPath)) {
            final FishingManager fishing = mmoPlayer.getFishingManager();
            if (fishing == null || !fishing.canUseFishermansDiet()) {
                return;
            }
            boosted = fishing.handleFishermanDiet(nutrition);
        } else {
            return; // not a diet food.
        }

        final int bonus = boosted - nutrition;
        if (bonus <= 0) {
            return; // rank 0 — the common case for a new player.
        }
        applyBonus(player, bonus, nutrition, food.saturation());
    }

    /**
     * Top the hunger bar up by {@code bonusFood} points, reproducing the clamping of vanilla's own
     * (private) {@code HungerManager#addInternal} through its public setters: food is clamped to
     * {@code [0, 20]} and saturation is clamped to the resulting food level.
     *
     * <p>Saturation scales with the bonus in the same proportion legacy's did. Legacy fed the boosted
     * food change back through Bukkit's {@code FoodData.eat(foodChange, saturationModifier)}, which
     * granted {@code foodChange * modifier * 2} saturation — and since 1.20.5 that product <em>is</em>
     * the component's absolute {@code saturation()}, so the extra saturation for {@code bonusFood}
     * points is {@code saturation * bonusFood / nutrition}.
     *
     * @param player    the eating player
     * @param bonusFood extra food points to grant (always positive)
     * @param nutrition the food's own nutrition, as the proportion base for saturation
     * @param saturation the food's own saturation
     */
    private static void applyBonus(ServerPlayerEntity player, int bonusFood, int nutrition,
            float saturation) {
        final HungerManager hunger = player.getHungerManager();
        final int newFoodLevel = MathHelper.clamp(hunger.getFoodLevel() + bonusFood, 0,
                HungerConstants.FULL_FOOD_LEVEL);
        final float bonusSaturation = saturation * bonusFood / nutrition;

        hunger.setFoodLevel(newFoodLevel);
        hunger.setSaturationLevel(
                MathHelper.clamp(hunger.getSaturationLevel() + bonusSaturation, 0.0f, newFoodLevel));
    }
}
