package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.platform.Potions;
import com.gmail.nossr50.skills.cooking.CookingManager;
import com.gmail.nossr50.skills.fishing.FishingManager;
import com.gmail.nossr50.skills.herbalism.HerbalismManager;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.text.ConfigStringUtils;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodConstants;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The eat seam — everything mcMMO does when a player finishes a food. Replaces legacy
 * {@code EntityListener#onFoodLevelChange}; driven by {@link com.gmail.nossr50.fabric.mixin.FoodComponentMixin}
 * on {@code FoodComponent#onConsume}, which fires after vanilla has already applied the food.
 *
 * <p>Two skills share it, and they share it in a very specific way:
 * <ul>
 *   <li>the <b>diets</b> — Herbalism's Farmer's Diet and Fishing's Fisherman's Diet — restore one
 *       extra hunger point per rank on the foods their own skill claims, <b>one or the other, never
 *       both</b>;</li>
 *   <li>Cooking's <b>Power Cook</b> grants the eaten food's mapped status effect, and it runs
 *       <b>unconditionally, after them, for every food</b> — including the 17 the diets claim, which
 *       are exactly the cooked and crafted ones a cook cares about.</li>
 * </ul>
 * That asymmetry is the whole design of this class; see the ordering note in
 * {@link #onFoodConsumed}.
 *
 * <p>The rank math is MC-free on the managers ({@link HerbalismManager#farmersDiet(int)},
 * {@link FishingManager#handleFishermanDiet(int)}, {@link CookingManager#powerCookEffect(String)});
 * this class owns the item classification, the hunger-bar mutation and the one registry lookup.
 *
 * <p>Two pieces of legacy's handler collapse here rather than being skipped. Its main-hand/off-hand
 * {@code MaterialMapStore.isFood} probe is gone — Bukkit's event only reported a food <em>level</em>, so
 * legacy had to guess what had been eaten, while our seam is handed the stack. And its
 * {@code foodChange <= 0} early-return becomes the {@code nutrition <= 0} check below: the two are the
 * same quantity, since legacy's food change is exactly the food's nutrition.
 */
public final class FoodListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("mcMMO/FoodListener");

    /**
     * Power Cook effect names that failed to resolve, so each bad one is reported once instead of
     * once per bite. This sits on the eat path and a player working through a smoker's output eats a
     * great many times; an unbounded warn there would bury the rest of the log.
     *
     * <p>Bounded by the number of distinct <em>misspellings</em> in a config file a human wrote, so
     * it cannot grow with play. Concurrent because both logical sides share this process.
     */
    private static final Set<String> UNRESOLVED_EFFECTS = ConcurrentHashMap.newKeySet();

    private FoodListener() {
    }

    /**
     * Run everything mcMMO does for one bite: the eating player's diet bonus if the food belongs to
     * a diet, and Cooking's Power Cook effect for every food. Called from the food-consume mixin; a
     * no-op for non-players, client-side calls, and unranked players.
     *
     * <p>⚠️ The {@code nutrition <= 0} guard below sits <b>above</b> everything, so a consumable that
     * restores no hunger is unreachable for every tenant of this seam. No shipped Power Cook food is
     * affected and a test says so; moving the guard is the only way to change that.
     *
     * @param world the world the consumption happened in
     * @param user  the entity that ate (only players carry mcMMO data)
     * @param stack the stack that was eaten
     * @param food  the food component vanilla just applied
     */
    public static void onFoodConsumed(Level world, LivingEntity user, ItemStack stack,
            FoodProperties food) {
        if (world.isClientSide()) {
            return; // the client half of a singleplayer session also runs consumption; server is authoritative.
        }
        if (!(user instanceof ServerPlayer player)) {
            return; // mobs eat too (e.g. via consumable items); no mcMMO data to read.
        }

        // Legacy's `foodChange <= 0` early-return: nothing to boost when the food restores no hunger.
        final int nutrition = food.nutrition();
        if (nutrition <= 0) {
            return;
        }

        final McMMOPlayer mmoPlayer = UserManager.getPlayer(player.getUUID());
        if (mmoPlayer == null) {
            return; // data not loaded (e.g. mid-join).
        }

        final String itemPath = BuiltInRegistries.ITEM.getId(stack.getItem()).getPath();

        // ⚠️⚠️ THE ORDERING TRAP — READ THIS BEFORE ADDING A SKILL TO THIS SEAM.
        //
        // The two diets are mutually exclusive WITH EACH OTHER and that is all they are. Any skill
        // that fires on *every* food must be called on its own line, unconditionally, and never
        // appended as a third `else if`: the diets between them claim 17 of the game's 40 edible
        // items -- bread, cookie, pumpkin_pie, mushroom_stew, baked_potato, cooked_cod,
        // cooked_salmon -- which are precisely the foods a cook cooks. An `else if` would skip
        // every one of them, silently, with no error and no failing test on either side alone.
        //
        // Worse, the diet arms also bail out on a null manager and on rank 0, so an edit made
        // *inside* an arm additionally misses every unranked player. That is why the diet chain is
        // sealed into its own method here: there is no longer an arm to append to by mistake.
        //
        // This is the fourth time this port has been bitten by exactly this shape.
        applyDietBonus(player, mmoPlayer, itemPath, nutrition, food.saturation());
        applyPowerCook(player, mmoPlayer, itemPath);
    }

    /**
     * Cooking's <b>Power Cook</b>: the food you cooked carries its effect into the meal. Runs for
     * every food, including the 17 the two diets claim — see the ordering note above.
     *
     * <p>All of the decision-making is on {@link CookingManager#powerCookEffect}, which is MC-free
     * and unit-tested without a world. What is left here is the one MC-typed step: resolving the
     * configured name against the status-effect registry, and handing it to vanilla.
     *
     * <h2>⚠️ The "don't overwrite a stronger effect" clause is vanilla's, and that is deliberate</h2>
     * Eating a steak in the middle of a 3:00 Strength II must not cut it to 15 s of Strength I.
     * {@code LivingEntity#addStatusEffect} routes an already-present effect through
     * {@code StatusEffectInstance#upgrade}, which (bytecode-verified against this MC version) takes
     * the new instance only when it is <em>stronger</em>, or equally strong and <em>longer</em>; a
     * weaker-but-longer one is parked as the hidden effect and resumes afterwards. Writing our own
     * comparison would duplicate that and get the hidden-effect case wrong. The same delegation the
     * Spears Momentum port made, for the same reason — and it is pinned by a test rather than
     * assumed, because it is an assumption about someone else's code.
     *
     * @param player    the eating player
     * @param mmoPlayer their mcMMO data
     * @param itemPath  the eaten item's registry path
     */
    private static void applyPowerCook(ServerPlayer player, McMMOPlayer mmoPlayer,
            String itemPath) {
        final CookingManager cooking = mmoPlayer.getCookingManager();
        if (cooking == null) {
            return; // Manager not built (mid-join); behave exactly like vanilla.
        }
        final CookingManager.PowerCookEffect effect =
                cooking.powerCookEffect(ConfigStringUtils.getMaterialConfigString(itemPath));
        if (effect == null) {
            return; // Skill off, unranked, or a food that grants nothing. All three are normal.
        }
        final Holder<MobEffect> type = Potions.matchEffect(effect.effectName());
        if (type == null) {
            // An operator typo in config.yml. Warn once per bad name rather than once per bite:
            // this is on the eat path, and a player with a full smoker eats a lot.
            if (UNRESOLVED_EFFECTS.add(effect.effectName())) {
                LOGGER.warn(
                        "Power Cook: config.yml -> Skills.Cooking.Power_Cook_Effects maps {} to '{}',"
                                + " which is not a status effect. That food will grant nothing.",
                        itemPath, effect.effectName());
            }
            return;
        }
        player.addEffect(new MobEffectInstance(type, effect.durationTicks(),
                CookingManager.POWER_COOK_AMPLIFIER));
    }

    /**
     * The diet half: Farmer's Diet or Fisherman's Diet, whichever claims this food — never both, and
     * never for a food neither claims.
     *
     * <p>The early returns are the originals and are deliberately confined to this method. Each arm
     * bails on a missing manager or an unranked player, and while the diets were the only tenants of
     * this seam that was invisible; with a second tenant on the seam it is a live hazard, so nothing
     * that must run for every food may live inside the chain.
     *
     * @param player     the eating player
     * @param mmoPlayer  their mcMMO data
     * @param itemPath   the eaten item's registry path
     * @param nutrition  the food's own nutrition, which is what the bonus is computed from
     * @param saturation the food's own saturation, scaled alongside the bonus
     */
    private static void applyDietBonus(ServerPlayer player, McMMOPlayer mmoPlayer,
            String itemPath, int nutrition, float saturation) {
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
        applyBonus(player, bonus, nutrition, saturation);
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
    private static void applyBonus(ServerPlayer player, int bonusFood, int nutrition,
            float saturation) {
        final FoodData hunger = player.getFoodData();
        final int newFoodLevel = Mth.clamp(hunger.getFoodLevel() + bonusFood, 0,
                FoodConstants.MAX_FOOD);
        final float bonusSaturation = saturation * bonusFood / nutrition;

        hunger.setFoodLevel(newFoodLevel);
        hunger.setSaturation(
                Mth.clamp(hunger.getSaturationLevel() + bonusSaturation, 0.0f, newFoodLevel));
    }
}
