package com.gmail.nossr50.fabric.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.skills.cooking.CookingManager;
import com.gmail.nossr50.skills.fishing.FishingManager;
import com.gmail.nossr50.skills.herbalism.HerbalismManager;
import com.gmail.nossr50.util.McTestRegistries;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.text.ConfigStringUtils;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The food-consumption seam — the one every skill that reacts to eating has to share.
 *
 * <p><b>This seam shipped with no test at all.</b> Both diet sub-skills' arithmetic is pinned on
 * their own managers, but nothing anywhere asserted that eating a loaf of bread reaches either of
 * them, which is the half that has repeatedly been wrong in this port.
 *
 * <p>What this class exists to stop is the <b>ordering trap</b>: {@code onFoodConsumed} used to be a
 * bare {@code if / else if / else} in which <em>every arm returns</em>, so a skill appended as one
 * more {@code else if} silently never fires for the 17 foods the two diets already claim — exactly
 * the cooked and crafted foods. The tests here assert the diets still work <em>and</em> that a food
 * one of them claims still reaches the rest of the chain.
 */
class FoodListenerTest {

    @BeforeAll
    static void bootstrapRegistries() {
        McTestRegistries.bootstrap();
    }

    /** Rank-driven diet bonus used throughout: +2 hunger points on top of the food's own. */
    private static final int DIET_BONUS = 2;

    /** The hunger bar starts here, well below full, so a bonus has room to land and be seen. */
    private static final int START_FOOD_LEVEL = 4;

    /**
     * A Power Cook duration in ticks. Deliberately not one of the shipped ladder's values: the
     * ladder is the manager's business and is pinned there, and a number that appears in both places
     * would let a wiring bug that reads the wrong field still look right.
     */
    private static final int POWER_COOK_TICKS = 137;

    private ServerLevel world;
    private ServerPlayer player;
    private FoodData hunger;
    private McMMOPlayer mmoPlayer;
    private HerbalismManager herbalism;
    private FishingManager fishing;
    private CookingManager cooking;

    @BeforeEach
    void setUp() {
        world = mock(ServerLevel.class);
        lenient().when(world.isClientSide()).thenReturn(false);

        // A real HungerManager, not a mock: the bonus is applied through vanilla's own clamping
        // setters, and a mock would happily record a food level of 40 on a 20-point bar.
        hunger = new FoodData();
        hunger.setFoodLevel(START_FOOD_LEVEL);
        hunger.setSaturation(0.0f);

        final UUID uuid = UUID.randomUUID();
        player = mock(ServerPlayer.class);
        lenient().when(player.getUUID()).thenReturn(uuid);
        lenient().when(player.getFoodData()).thenReturn(hunger);

        final PlatformPlayer platformPlayer = mock(PlatformPlayer.class);
        lenient().when(platformPlayer.getUniqueId()).thenReturn(uuid);

        herbalism = mock(HerbalismManager.class);
        fishing = mock(FishingManager.class);
        cooking = mock(CookingManager.class);
        mmoPlayer = mock(McMMOPlayer.class);
        lenient().when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        lenient().when(mmoPlayer.getHerbalismManager()).thenReturn(herbalism);
        lenient().when(mmoPlayer.getFishingManager()).thenReturn(fishing);
        lenient().when(mmoPlayer.getCookingManager()).thenReturn(cooking);
        UserManager.track(mmoPlayer);
    }

    @AfterEach
    void tearDown() {
        UserManager.cleanupPlayer(mmoPlayer);
    }

    // --- The diet half ----------------------------------------------------------------------------

    @Test
    void eatingAFarmersDietFoodTopsUpTheHungerBar() {
        rankedFarmer();

        eat(Items.BREAD);

        assertEquals(START_FOOD_LEVEL + nutritionOf(Items.BREAD) + DIET_BONUS, hunger.getFoodLevel(),
                "Farmer's Diet must add its bonus on top of the food vanilla already applied");
    }

    @Test
    void eatingAFishermansDietFoodTopsUpTheHungerBar() {
        rankedFisherman();

        eat(Items.COOKED_COD);

        assertEquals(START_FOOD_LEVEL + nutritionOf(Items.COOKED_COD) + DIET_BONUS,
                hunger.getFoodLevel());
    }

    @Test
    void theTwoDietsStayMutuallyExclusive() {
        // Both ranked, one food. Bread is Farmer's; Fisherman's must not also pay out on it, or the
        // restructure has turned an exclusive chain into two additive bonuses on one bite.
        rankedFarmer();
        rankedFisherman();

        eat(Items.BREAD);

        assertEquals(START_FOOD_LEVEL + nutritionOf(Items.BREAD) + DIET_BONUS, hunger.getFoodLevel(),
                "a food claimed by one diet must be paid by exactly one diet");
    }

    @Test
    void aFoodNeitherDietClaimsGetsNoHungerBonus() {
        // The reference point. Without it, a test suite full of green diet assertions is equally
        // consistent with a listener that tops up the bar for everything.
        rankedFarmer();
        rankedFisherman();

        eat(Items.COOKED_BEEF);

        assertEquals(START_FOOD_LEVEL + nutritionOf(Items.COOKED_BEEF), hunger.getFoodLevel(),
                "cooked beef belongs to neither diet");
    }

    @Test
    void anUnrankedDietGrantsNothing() {
        when(herbalism.canUseFarmersDiet()).thenReturn(false);

        eat(Items.BREAD);

        assertEquals(START_FOOD_LEVEL + nutritionOf(Items.BREAD), hunger.getFoodLevel());
    }

    @Test
    void theClientHalfOfASingleplayerSessionIsIgnored() {
        // Singleplayer runs both logical sides in one process; the client's copy of the consumption
        // would double every diet bonus.
        rankedFarmer();
        when(world.isClientSide()).thenReturn(true);

        eat(Items.BREAD);

        assertEquals(START_FOOD_LEVEL, hunger.getFoodLevel(),
                "the client side must apply nothing at all -- vanilla has not eaten yet either");
    }

    // --- Power Cook, and the ordering trap --------------------------------------------------------

    @Test
    void eatingBreadFiresBothTheDietBonusAndPowerCook() {
        // ⚠️⚠️ THE TEST THIS WHOLE RESTRUCTURE EXISTS FOR. Bread is a Farmer's Diet food, and while
        // the chain was a bare if/else-if every arm returned -- so a skill appended as one more
        // `else if` would never fire for bread, cookie, pumpkin_pie, mushroom_stew, baked_potato,
        // cooked_cod or cooked_salmon. That is 17 of the game's 40 edible items and it is precisely
        // the set a cook cooks.
        //
        // Asserted OFF THE REFERENCE POINT: a food ONE of the diets already claims, not a food only
        // Cooking claims. A test on cooked beef would pass against the broken chain.
        rankedFarmer();
        powerCooked(Items.BREAD, "SPEED", POWER_COOK_TICKS);

        eat(Items.BREAD);

        assertEquals(START_FOOD_LEVEL + nutritionOf(Items.BREAD) + DIET_BONUS, hunger.getFoodLevel(),
                "the Farmer's Diet bonus must survive Cooking joining this seam");
        assertEffectApplied(MobEffects.SPEED, POWER_COOK_TICKS);
    }

    @Test
    void eatingACookedFoodAppliesItsMappedEffect() {
        powerCooked(Items.COOKED_BEEF, "STRENGTH", POWER_COOK_TICKS);

        eat(Items.COOKED_BEEF);

        assertEffectApplied(MobEffects.STRENGTH, POWER_COOK_TICKS);
    }

    @Test
    void theEffectIsAlwaysAppliedAtAmplifierZero() {
        powerCooked(Items.COOKED_BEEF, "STRENGTH", POWER_COOK_TICKS);

        eat(Items.COOKED_BEEF);

        assertEquals(0, captureEffect().getAmplifier(), "no Strength II from a sandwich");
    }

    @Test
    void aFoodTheManagerDeclinesGrantsNoEffect() {
        // The manager answers null for an unranked player, a disabled skill and a food outside the
        // table alike. All three must reach vanilla untouched.
        when(cooking.powerCookEffect(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);

        eat(Items.COOKED_BEEF);

        verify(player, never()).addEffect(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void anUnknownEffectNameGrantsNothingAndDoesNotThrow() {
        // An operator typo in config.yml. The row is disabled and logged once -- it must not take
        // the eat path down with it, because every food in the game runs through here.
        powerCooked(Items.COOKED_BEEF, "STRENGTH_II_PLEASE", POWER_COOK_TICKS);

        eat(Items.COOKED_BEEF);

        verify(player, never()).addEffect(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void theSeamIsKeyedOnTheConfigStringAndNotTheRegistryPath() {
        // Bonus_Drops.Cooking and Experience_Values.Cooking are both Config_String-keyed, so a seam
        // handing over "cooked_beef" would find no row and grant nothing -- silently, forever.
        powerCooked(Items.COOKED_BEEF, "STRENGTH", POWER_COOK_TICKS);

        eat(Items.COOKED_BEEF);

        verify(cooking).powerCookEffect("Cooked_Beef");
    }

    @Test
    void powerCookNeverDowngradesAStrongerEffectThePlayerAlreadyHas() {
        // ⚠️ The clause is delegated to vanilla rather than reimplemented, so what has to be pinned
        // is the assumption about SOMEBODY ELSE'S code -- if a future MC version changes
        // StatusEffectInstance#upgrade, eating a steak starts cutting a 3:00 Strength II down to
        // 15 seconds of Strength I and nothing else in this suite would notice.
        final MobEffectInstance brewedPotion =
                new MobEffectInstance(MobEffects.STRENGTH, 3600, 1);

        final boolean changed = brewedPotion.update(
                new MobEffectInstance(MobEffects.STRENGTH, POWER_COOK_TICKS, 0));

        assertFalse(changed, "a weaker, shorter effect must not replace a brewed potion");
        assertEquals(3600, brewedPotion.getDuration(), "the potion's duration must be untouched");
        assertEquals(1, brewedPotion.getAmplifier(), "the potion's amplifier must be untouched");
    }

    @Test
    void powerCookStillExtendsAnEffectOfItsOwnStrength() {
        // The other direction, or the test above is equally consistent with an effect that can never
        // be applied at all. Equal amplifier and longer duration IS accepted by vanilla.
        final MobEffectInstance running =
                new MobEffectInstance(MobEffects.STRENGTH, 20, 0);

        final boolean changed = running.update(
                new MobEffectInstance(MobEffects.STRENGTH, POWER_COOK_TICKS, 0));

        assertTrue(changed, "a longer effect at the same strength must extend the running one");
        assertEquals(POWER_COOK_TICKS, running.getDuration());
    }

    // --- Helpers ----------------------------------------------------------------------------------

    /**
     * Tell the mocked manager that {@code item} grants {@code effectName} for {@code ticks}.
     *
     * <p>The manager is mocked here on purpose: which effect and how long is its decision and is
     * pinned against the real shipped config in {@code CookingManagerTest} and
     * {@code PowerCookEffectTableTest}. What is unproven without this class is the <b>wiring</b> —
     * that a bite reaches the manager at all, with the right key, and that what comes back is handed
     * to vanilla.
     */
    private void powerCooked(Item item, String effectName, int ticks) {
        final String key = ConfigStringUtils.getMaterialConfigString(
                BuiltInRegistries.ITEM.getKey(item).getPath());
        when(cooking.powerCookEffect(key))
                .thenReturn(new CookingManager.PowerCookEffect(effectName, ticks));
    }

    /** Assert exactly one effect was applied, and that it is this one. */
    private void assertEffectApplied(Holder<MobEffect> expected, int expectedTicks) {
        final MobEffectInstance applied = captureEffect();
        assertTrue(applied.equals(expected),
                "expected " + expected.getRegisteredName() + " but got "
                        + applied.getEffect().getRegisteredName());
        assertEquals(expectedTicks, applied.getDuration());
    }

    /** The single status effect the listener handed to vanilla. */
    private MobEffectInstance captureEffect() {
        final ArgumentCaptor<MobEffectInstance> captor =
                ArgumentCaptor.forClass(MobEffectInstance.class);
        verify(player).addEffect(captor.capture());
        return captor.getValue();
    }

    /** Give the player a Farmer's Diet rank worth {@link #DIET_BONUS} points. */
    private void rankedFarmer() {
        when(herbalism.canUseFarmersDiet()).thenReturn(true);
        when(herbalism.farmersDiet(org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(call -> (int) call.getArgument(0) + DIET_BONUS);
    }

    /** Give the player a Fisherman's Diet rank worth {@link #DIET_BONUS} points. */
    private void rankedFisherman() {
        when(fishing.canUseFishermansDiet()).thenReturn(true);
        when(fishing.handleFishermanDiet(org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(call -> (int) call.getArgument(0) + DIET_BONUS);
    }

    /**
     * Eat one {@code item}, the way vanilla does: apply the food to the hunger bar first, then fire
     * the seam. {@code FoodComponent#onConsume} is injected at {@code TAIL} — <b>after</b> vanilla's
     * own {@code HungerManager#eat} — so a test that skips that step measures a bar the listener
     * never actually sees.
     */
    private void eat(Item item) {
        final ItemStack stack = new ItemStack(item);
        final FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null) {
            throw new AssertionError(item + " has no FOOD component; the seam can never fire for it");
        }
        if (!world.isClientSide()) {
            hunger.eat(food);
        }
        FoodListener.onFoodConsumed(world, player, stack, food);
    }

    /** The food's own nutrition, read off the registry rather than recalled. */
    private static int nutritionOf(Item item) {
        final FoodProperties food = new ItemStack(item).get(DataComponents.FOOD);
        if (food == null) {
            throw new AssertionError(item + " has no FOOD component");
        }
        return food.nutrition();
    }
}
