package com.gmail.nossr50.skills.cooking;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.Misc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Cooking skill manager (Pass 2). Holds the MC-free half of every food-processing behaviour; the
 * MC-typed half (reading furnace slots, the crafting result slot, and the eaten stack) lives on the
 * existing {@code fabric.listeners.SmeltingListener} / {@code CookingListener} / {@code FoodListener}
 * seams.
 *
 * <p><b>Stage 2 (this class's XP half) is live.</b> Each remaining behaviour lands with the seam that
 * drives it:
 * <ul>
 *   <li><b>Stage 2</b> ✅ — cook XP (the food branch of {@code onFurnaceSmelt}) and crafted-food XP
 *       ({@code CraftingResultSlot}), plus the {@code Max_Cooks_Per_Hour} rolling cap;</li>
 *   <li><b>Stage 3</b> — Kitchen Efficiency ({@code boostFuelTime}'s {@code else}) and Master Chef
 *       ({@code onSmeltComplete}'s food arm);</li>
 *   <li><b>Stage 4</b> — Power Cook (the eat seam, and the level → duration math, which lives here
 *       precisely so it is unit-testable with no world);</li>
 *   <li><b>Stage 5</b> — campfires and {@code CookingStatsRenderer}.</li>
 * </ul>
 *
 * <h2>⚠️ Two key spaces, and they are deliberately not one</h2>
 * The two XP hooks read <b>different items</b> and therefore address <b>different config sections</b>:
 * <ul>
 *   <li>{@link #onCook} is handed the furnace's <b>input</b> — {@code beef}, {@code potato},
 *       {@code kelp} — because vanilla's {@code craftRecipe} is what decrements it, and the existing
 *       furnace seam injects <em>before</em> that call. Priced under
 *       {@code Experience_Values.Cooking.Cook}.</li>
 *   <li>{@link #onCraft} is handed the crafting grid's <b>result</b> — {@code bread}, {@code cookie},
 *       {@code dried_kelp}. Priced under {@code Experience_Values.Cooking.Craft}.</li>
 * </ul>
 * <b>Do not flatten these into one section.</b> {@code dried_kelp} is exactly why: smoking a
 * {@code kelp} is a legitimate 60-XP cook, while <em>crafting</em> a {@code dried_kelp} out of a
 * dried kelp block and crafting the block straight back is a free infinite loop that must price
 * <b>0</b>. The same item, two verbs, two prices — and the split is what makes that structural
 * rather than a coincidence of vanilla's recipe names.
 *
 * <h2>⚠️ Two of the three sub-skills need an explicit per-skill disable gate</h2>
 * {@code SkillGating} enforces the {@code coreskills.yml} master switch at three chokepoints:
 * {@code Permissions}, {@code RankUtils} booleans, and {@code ProbabilityUtil#isSkillRNGSuccessful}.
 * Master Chef is an RNG proc and is therefore covered for free. <b>Kitchen Efficiency is a
 * multiplier and Power Cook is a deterministic effect, so neither passes through any of those</b> —
 * each needs {@code SkillGating.isSkillEnabled(PrimarySkillType.COOKING)} checked at its own call
 * site, or switching Cooking off would still boost fuel and still hand out Strength.
 *
 * <p>The XP on this class needs no such call: every award goes through
 * {@code SkillManager#applyXpGain} → {@code McMMOPlayer#beginXpGain}, which GitHub #10 already gates.
 *
 * <h2>⚠️ The Smelting boundary</h2>
 * Cooking and Smelting share the furnace and the shared {@code FURNACE_OWNERS} map, and the boundary
 * between them is already enforced in shipped code in both directions:
 * {@code Experience_Values.Smelting} lists ore only, and {@code SmeltingManager#boostFuelTime} gates
 * on {@code isSmeltable(input)} so that food burns at vanilla speed. Kitchen Efficiency is literally
 * the {@code else} of a gate that already exists — do not widen either side to "unify" them.
 */
public class CookingManager extends SkillManager {

    /**
     * How many cooked or crafted <b>items</b> may pay Cooking XP inside one
     * {@link #COOK_RATE_WINDOW_SECONDS window}; {@code 0} or less disables the cap.
     *
     * <p><b>This is the skill's only anti-farm gate — read this before raising it.</b> Cooking has
     * none of the four gates Hunter got: there is no transient-entity check, no player-created-golem
     * check, no killing blow to attribute, and above all <b>an item has no spawn origin</b>. The
     * furnace-owner map is populated by a single right-click and held for the whole session, so a
     * hopper-fed array keeps paying its owner while they sleep.
     *
     * <p>The arithmetic it is derived from: vanilla's own cook times (read out of the shipped recipe
     * JSONs, not recalled) are <b>smoking 100 ticks, smelting 200, campfire 600</b>, so one smoker is
     * 720 items/h unattended and eight are 5,760/h. <b>1,200 is two continuously-running smokers</b>,
     * which against the {@code 10N² + 1010N} curve's 11,010,000 XP to RetroMode 1000 puts the skill's
     * floor at ~92 XP per average cook over ~100 hours. Cooks past the cap still cook; they pay
     * nothing.
     *
     * <p>⚠️ <b>The cap counts items, not events, and that is load-bearing.</b> XP is priced per item
     * and multiplied by the batch, so a cap counting <em>takes</em> would let one shift-click of 64
     * cookies spend a single unit of a 1,200 budget while paying 64 items' worth of XP — a 64×
     * hole in the one gate the skill has.
     */
    public static final int DEFAULT_MAX_COOKS_PER_HOUR = 1200;

    /**
     * How long one cook-rate window lasts, in seconds.
     *
     * <p><b>Fixed at one hour by the config key's own name</b> ({@code Max_Cooks_Per_Hour}), and
     * deliberately not configurable: the flat one-hour shape was chosen over Husbandry's
     * {@code Awards_Per_Window} + {@code _Window_Seconds} pair, which was offered and declined.
     * Renaming or splitting this later needs a {@code ConfigRetunes}-style migration, so it is not a
     * change to make casually.
     */
    public static final int COOK_RATE_WINDOW_SECONDS = 3600;

    /**
     * What one cook or craft was worth, and whether it was the moment the rate cap started biting.
     *
     * @param xp             the XP awarded; {@code 0} when the item is unpriced <em>or</em> when this
     *                       window's budget is entirely spent
     * @param creditedItems  how many of the batch's items actually paid — between {@code 0} and the
     *                       batch size, because a batch straddling the cap boundary is credited in
     *                       part rather than refused whole
     * @param capReached     {@code true} only on the <b>first</b> award a window has to trim. The
     *                       caller uses it to tell the player once per window rather than once per
     *                       cook — a gate that silently pays nothing is indistinguishable from a
     *                       broken one, and a gate that says so on every item of a 64-cookie craft is
     *                       worse
     */
    public record CookAward(float xp, int creditedItems, boolean capReached) {

        /** Whether this cook actually paid. */
        public boolean paid() {
            return xp > 0;
        }
    }

    /** The award for an unpriced item: no XP, no cap spend, nothing to say. */
    private static final CookAward NOTHING = new CookAward(0F, 0, false);

    /**
     * Whether the rate window is open at all — distinct from a start tick of zero, which is a
     * perfectly ordinary world time on a freshly created world.
     */
    private boolean cookWindowOpen;

    /** The world tick the current window opened on; meaningless while {@link #cookWindowOpen} is false. */
    private long cookWindowStartTick;

    /** Items already credited in the current window. */
    private int cookedItemsThisWindow;

    /** Whether the player has already been told the cap bit, in the current window. */
    private boolean cookCapAnnouncedThisWindow;

    public CookingManager(McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.COOKING);
    }

    // --- The two XP verbs -------------------------------------------------------------------------

    /**
     * Credit one finished furnace, smoker, blast furnace or campfire cook.
     *
     * @param inputConfigString the config string of the <b>input</b> the furnace just consumed (e.g.
     *                          {@code "Beef"}), which is the key {@code Experience_Values.Cooking.Cook}
     *                          is written against
     * @param worldTick         the current world time; the clock the rate cap is measured on
     */
    public CookAward onCook(@NotNull String inputConfigString, long worldTick) {
        // A cook produces exactly one item: every vanilla cooking recipe has result count 1
        // (verified against the shipped recipe JSONs). Second Smelt's bonus copy is Stage 3's and
        // is deliberately not counted here -- it is a drop, not a cook.
        return award(getCookXp(inputConfigString), 1, worldTick);
    }

    /**
     * Credit a crafted food taken out of a crafting grid's result slot.
     *
     * @param resultConfigString the config string of the <b>result</b> item (e.g. {@code "Bread"}),
     *                           the key {@code Experience_Values.Cooking.Craft} is written against
     * @param items              how many items the take produced — <b>not</b> how many crafts. One
     *                           take of the cookie recipe is 8, dried kelp 9, honey bottle 4, and a
     *                           shift-click multiplies all of them again
     * @param worldTick          the current world time; the clock the rate cap is measured on
     */
    public CookAward onCraft(@NotNull String resultConfigString, int items, long worldTick) {
        return award(getCraftXp(resultConfigString), items, worldTick);
    }

    /**
     * The shared half of both verbs: price the batch per item, spend what the window will allow, and
     * award only that.
     *
     * <p>Priced per item and multiplied by the count rather than per event, because
     * {@code CraftingResultSlot#onCrafted(ItemStack)} fires <b>once per take with the whole batch</b>
     * — pricing per event pays for one cookie when eight were made.
     */
    private CookAward award(int xpPerItem, int items, long worldTick) {
        if (xpPerItem <= 0 || items <= 0) {
            return NOTHING; // Unpriced item, or nothing actually produced. Costs no cap budget.
        }
        final int credited = claimCooks(items, worldTick);
        boolean announce = false;
        if (credited < items) {
            announce = !cookCapAnnouncedThisWindow;
            cookCapAnnouncedThisWindow = true;
        }
        if (credited <= 0) {
            return new CookAward(0F, 0, announce);
        }
        final float xp = (float) xpPerItem * credited;
        applyXpGain(xp, XPGainReason.PVE, XPGainSource.SELF);
        return new CookAward(xp, credited, announce);
    }

    /**
     * Spend up to {@code wanted} of this window's cook budget.
     *
     * <p>A fixed window rather than a sliding one — the same shape as Husbandry's breed-award cap:
     * the window opens on the first award after the last one expired and runs for a fixed length, so
     * at a boundary a player can collect up to two windows' worth in quick succession. That is
     * deliberate and bounded (the burst is twice the cap, the sustained rate is exactly the cap) and
     * it costs one long and one int of state instead of a queue of timestamps per player.
     *
     * <p>⚠️ <b>The known cost of the flat shape, accepted deliberately:</b> it is bursty. A stack of
     * raw beef through eight smokers spends a large slice of the hour in minutes, and the player then
     * earns nothing for the rest of it. That is why {@link CookAward#capReached()} exists — a limit
     * nobody is told about is indistinguishable from the skill being broken.
     *
     * <p>A batch that straddles the boundary is credited <b>in part</b>, not refused whole: refusing
     * a 9-item craft because 3 units of budget remain would make the cap's bite depend on batch size.
     *
     * <p>A negative elapsed time means the world clock moved backwards ({@code /time set}, or a
     * restore from backup). The window is reset rather than trusted — refusing to reset would lock
     * the player out of Cooking XP for as long as the clock stayed behind, silently.
     *
     * @return how many of {@code wanted} were available and have now been spent, {@code 0..wanted}
     */
    private int claimCooks(int wanted, long worldTick) {
        final int max = getMaxCooksPerHour();
        final int windowTicks = getCookRateWindowTicks();
        if (max <= 0 || windowTicks <= 0) {
            return wanted; // Gate configured off.
        }
        final long elapsed = worldTick - cookWindowStartTick;
        if (!cookWindowOpen || elapsed < 0 || elapsed >= windowTicks) {
            cookWindowOpen = true;
            cookWindowStartTick = worldTick;
            cookedItemsThisWindow = 0;
            cookCapAnnouncedThisWindow = false;
        }
        final int remaining = max - cookedItemsThisWindow;
        if (remaining <= 0) {
            return 0;
        }
        final int credited = Math.min(wanted, remaining);
        cookedItemsThisWindow += credited;
        return credited;
    }

    // --- Config reads -----------------------------------------------------------------------------

    /** XP for one item cooked from {@code inputConfigString} in a furnace, smoker or campfire. */
    public int getCookXp(@NotNull String inputConfigString) {
        final ExperienceConfig experience = experience();
        return experience == null ? 0 : experience.getCookingCookXp(inputConfigString);
    }

    /** XP for one item of {@code resultConfigString} taken out of a crafting grid. */
    public int getCraftXp(@NotNull String resultConfigString) {
        final ExperienceConfig experience = experience();
        return experience == null ? 0 : experience.getCookingCraftXp(resultConfigString);
    }

    /**
     * Whether an item is "cookable" as far as Cooking is concerned — the food-side mirror of
     * {@code SmeltingManager#isSmeltable}, and the gate that decides which of the two skills a
     * finished furnace cook pays.
     *
     * <p>Answers {@code false} when no config is wired, which fails <b>closed</b> for Cooking: a
     * furnace with no opinion available pays nobody rather than paying twice.
     *
     * @param inputConfigString the config string of the furnace's <em>input</em> material
     */
    public static boolean isCookable(@NotNull String inputConfigString) {
        final ExperienceConfig experience = McMMOMod.getExperienceConfig();
        return experience != null && experience.getCookingCookXp(inputConfigString) >= 1;
    }

    /**
     * How many items may pay Cooking XP per hour; {@code 0} or less disables the cap entirely.
     *
     * <p>See {@link #DEFAULT_MAX_COOKS_PER_HOUR} for why this gate exists and why it counts items.
     */
    public int getMaxCooksPerHour() {
        final ExperienceConfig experience = experience();
        return experience == null
                ? DEFAULT_MAX_COOKS_PER_HOUR
                : experience.getCookingMaxCooksPerHour();
    }

    /** The window in world ticks, which is the clock {@link #onCook} is actually measured on. */
    public int getCookRateWindowTicks() {
        return COOK_RATE_WINDOW_SECONDS * Misc.TICK_CONVERSION_FACTOR;
    }

    /**
     * Whether the rate cap is switched on at all.
     *
     * <p>Read by {@code /mcstats cooking} (Stage 5), which renders the cap only when there is one —
     * a line reading "0 per hour" would be worse than no line.
     */
    public boolean isCookRateCapped() {
        return getMaxCooksPerHour() > 0 && getCookRateWindowTicks() > 0;
    }

    /**
     * The config, or {@code null} when none is wired (unit tests, the headless boot, between world
     * sessions). Every caller above falls back to a value that changes no behaviour.
     */
    private static @Nullable ExperienceConfig experience() {
        return McMMOMod.getExperienceConfig();
    }
}
