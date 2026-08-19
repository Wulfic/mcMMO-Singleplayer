package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.config.treasure.TreasureConfig;
import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.treasure.HusbandryTreasure;
import com.gmail.nossr50.fabric.McMMOAttachments;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.ItemSpecBuilder;
import com.gmail.nossr50.platform.MetadataStore;
import com.gmail.nossr50.skills.husbandry.HusbandryManager;
import com.gmail.nossr50.util.LogUtils;
import com.gmail.nossr50.util.Misc;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.text.ConfigStringUtils;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Husbandry's breed-verb trigger layer (stage 1): breeding XP, {@code Twins} and {@code Multi-Breed}.
 *
 * <p>MC-typed glue only. Every decision — what a breeding is worth, whether a twin is born, how far
 * Multi-Breed reaches and how many animals it may touch — belongs to the MC-free
 * {@link HusbandryManager}; this class resolves entities, spawns things and reads the world.
 *
 * <h2>⚠️ The two seams here are NOT the ones the plan named, and the difference is silent</h2>
 *
 * <b>1. Breeding XP hangs off {@code BredAnimalsCriterion#trigger}, not {@code AnimalEntity#breed}.</b>
 * The plan called {@code AnimalEntity#breed(ServerWorld, AnimalEntity, PassiveEntity)} the universal
 * funnel. Bytecode says otherwise: {@code FoxEntity$MateGoal#breed()} and
 * {@code TurtleEntity$MateGoal#breed()} <em>re-implement the whole breeding sequence inline</em> —
 * their own child creation, loving-player resolution, breeding-age reset, love reset and XP orb —
 * and never call {@code AnimalEntity.breed} at all, in either overload. Hooking {@code breed} would
 * therefore have paid <b>exactly zero</b> for foxes and turtles, both of which
 * {@code experience.yml} prices (800 and 700), with nothing anywhere to say so.
 *
 * <p>The one point all three paths <em>do</em> share is
 * {@code Criteria.BRED_ANIMALS.trigger(ServerPlayerEntity, AnimalEntity, AnimalEntity,
 * PassiveEntity)} — verified as the only reference to {@code BredAnimalsCriterion} in the whole
 * entity package. It is a strictly better seam for three separate reasons: it covers fox and turtle;
 * it is reached <b>only</b> when vanilla itself has resolved a real {@code ServerPlayerEntity} as
 * the breeder, so AI-driven and command-driven breeding pay nothing without a gate of our own; and
 * it fires exactly once per breeding rather than once per parent, which is the rule this verb has
 * had since the plan was written.
 *
 * <p><b>2. Multi-Breed hangs off {@code AnimalEntity#lovePlayer}, not {@code interactMob}.</b>
 * {@code AbstractHorseEntity}, {@code CamelEntity}, {@code LlamaEntity} and {@code PandaEntity} all
 * override {@code interactMob} and call {@code lovePlayer} themselves, so an {@code interactMob}
 * hook would have left Multi-Breed quietly dead on four species — including horses, the most
 * expensive line in the breeding table. {@code lovePlayer} is the shared callee and the only method
 * vanilla ever uses to attribute an animal's love to a player.
 *
 * <h2>The child may be null, and that is normal</h2>
 * {@code FrogEntity} and {@code SnifferEntity} pass {@code null} as the child (they lay eggs rather
 * than spawn a baby), and {@code TurtleEntity$MateGoal} passes {@code null} too. Breeding XP is paid
 * regardless — the player did breed them — but {@code Twins} needs something to copy, so it is
 * skipped. That is the right behaviour rather than a limitation: duplicating an egg-layer's clutch
 * is a different mechanic from bearing two young.
 */
public final class HusbandryListener {

    /**
     * Set while Multi-Breed is spreading love to an animal's neighbours.
     *
     * <p>Load-bearing: the spread is implemented by calling {@code lovePlayer} on each neighbour,
     * which is the very method this class hooks. Without the guard the first fed animal would set
     * its neighbours in love, each of those would run the sweep again from its own position, and one
     * piece of wheat would walk outward across every animal in the world until the stack overflowed.
     *
     * <p>A {@link ThreadLocal} rather than a plain field for the same reason
     * {@code CombatUtils.IN_MCMMO_DAMAGE} is one — the entire window is a single synchronous call on
     * the server thread, so it covers the whole re-entrant region exactly.
     */
    private static final ThreadLocal<Boolean> SPREADING_LOVE = ThreadLocal.withInitial(() -> false);

    /**
     * The player-entity interaction currently in flight, or {@code null} outside one.
     *
     * <p><b>This exists because {@code growUp} has no player and no honest way to find one.</b> The
     * feed verb has to know who fed the animal, and vanilla's six feeding paths
     * ({@code AnimalEntity#interactMob}, {@code DolphinEntity#interactMob},
     * {@code PandaEntity#interactMob}, and {@code receiveFood} on horse, camel and llama) share
     * exactly one callee — {@code PassiveEntity#growUp(int, boolean)} — which takes only an int.
     *
     * <p>Hooking those six entry points instead is the enumeration this port has been burned by
     * three times; hooking {@code growUp} alone is worse still, because two of its callers are not
     * players at all: {@code SheepEntity#onEatingGrass} would turn a lamb standing in a field into
     * an AFK income, and {@code TadpoleEntity} ages itself through it. So the player is stashed at
     * the one funnel every player-entity interaction passes through and consumed at the one funnel
     * every growth passes through.
     *
     * <p>Scoped by {@code PlayerEntityInteractMixin}'s HEAD/RETURN pair, so it is set for exactly
     * the duration of one synchronous call on the server thread — the {@code IN_MCMMO_DAMAGE} shape.
     * A nested interaction would clear its parent's stash early, which costs XP rather than paying
     * it wrongly; vanilla has no such nesting today.
     */
    private static final ThreadLocal<Interaction> PLAYER_INTERACTION = new ThreadLocal<>();

    /** One player-entity interaction: who, and with what. */
    private record Interaction(ServerPlayerEntity player, Entity target) {
    }

    private HusbandryListener() {
    }

    /**
     * Whether this animal is a live Call-of-the-Wild summon, and so may not price a breeding.
     *
     * <p>Answered from the transient summon tracker rather than a mob flag: it already holds every
     * live summon for exactly as long as the summon exists, which is the same lifetime legacy's
     * {@code COTW_SUMMONED_MOB} metadata had. {@code CombatUtils#processCombatXP} closes the combat
     * half of the same exploit off the same tracker.
     *
     * <p>A {@code null} mate is treated as not-a-summon: the egg-laying breeders reach
     * {@link #onAnimalsBred} with only one parent, and refusing those would break turtles and frogs
     * for everyone.
     */
    private static boolean isCallOfTheWildSummon(@Nullable AnimalEntity animal) {
        final ExperienceConfig config = McMMOMod.getExperienceConfig();
        if (animal == null || config == null || !config.isCOTWBreedingPrevented()) {
            return false;
        }
        return McMMOMod.getTransientEntityTracker().isTransient(animal.getUuid());
    }

    /**
     * A player bred two animals: award Husbandry XP, then roll {@code Twins}.
     *
     * <p>Called from {@code BredAnimalsCriterionMixin}. See the class javadoc for why that is the
     * seam.
     *
     * @param breeder the player vanilla credits with the breeding — never null at this call site
     * @param parent  the animal whose species prices the breeding
     * @param mate    the other parent
     * @param child   the baby about to be spawned; {@code null} for the egg-laying breeders
     */
    public static void onAnimalsBred(ServerPlayerEntity breeder, AnimalEntity parent,
            AnimalEntity mate, PassiveEntity child) {
        if (breeder == null || parent == null) {
            return;
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(breeder.getUuid());
        if (mmoPlayer == null) {
            return; // data not loaded (e.g. mid-join).
        }
        final HusbandryManager husbandry = mmoPlayer.getHusbandryManager();
        if (husbandry == null) {
            return;
        }

        // Breeding your own Call-of-the-Wild summons pays nothing (ExploitFix.COTWBreeding, legacy
        // EntityListener#onEntityBreed). Taming conjures the parents out of a few bones, so paying
        // for their offspring turns one skill's ability into another skill's XP tap.
        //
        // Legacy cancels the breeding outright and zeroes both parents' love ticks. This port cannot:
        // its seam is the bred-animals advancement criterion, which fires *after* vanilla has already
        // decided. Refusing the award is the reachable half, and it is the half that matters -- the
        // XP, not the calf.
        //
        // ⚠️ It must refuse the calf as well as the XP, exactly as the award cap does (GitHub #3):
        // leaving the child claimed would let the raise verb pay for it twenty minutes later, making
        // this a delay rather than a gate.
        if (isCallOfTheWildSummon(parent) || isCallOfTheWildSummon(mate)) {
            LogUtils.debug("Refusing Husbandry breed XP for " + breeder.getUuid()
                    + ": a parent is a Call of the Wild summon.");
            return;
        }

        final String entityConfigString = ConfigStringUtils.getConfigEntityTypeString(
                Registries.ENTITY_TYPE.getId(parent.getType()).getPath());
        final HusbandryManager.BreedAward award =
                husbandry.onBreed(entityConfigString, parent.getEntityWorld().getTime());
        if (award.capReached()) {
            // Once per window, not once per breeding. A gate that pays nothing and says nothing is
            // indistinguishable from a broken one -- the lesson GitHub #4 and #5 both turned on.
            NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_MESSAGE,
                    "Husbandry.BreedAwardCap.Reached");
        }

        claimOffspring(husbandry, breeder, child, award.paid());
        maybeBearTwin(mmoPlayer, husbandry, breeder, parent, mate, child, award.paid());
    }

    /**
     * Record a newborn as this player's, and shorten its childhood by {@code Accelerated Growth}.
     *
     * <p>Both halves belong together and both belong here: the marker is what lets the raise verb
     * pay the right player twenty minutes later, and the acceleration is applied once at birth
     * rather than by speeding the animal's ageing up every tick.
     *
     * <p>Called for the twin as well as for vanilla's child. A twin that carried no marker would be
     * the only baby in the game its breeder could not be paid for, which reads as a bug rather than
     * as balance.
     *
     * <p>The marker is a <b>persistent data attachment</b> ({@link McMMOAttachments#BRED_BY}), so it
     * is written into the calf's own NBT and survives the world being closed and reopened — D-H6,
     * reversed on 2026-07-29. It used to be a {@code MetadataStore} entry, which meant a calf bred
     * before a restart quietly paid nothing when it matured; twenty minutes of vanilla growth is
     * long enough that "did you quit in between?" was a real and invisible condition on the payout.
     *
     * <h2>⚠️ A breeding the award cap refused marks nothing</h2>
     * {@code paid} is what carries that, and it is the difference between a cap and a twenty-minute
     * delay. The raise verb pays a full breeding's worth of XP off this marker alone, so marking a
     * calf whose breeding paid nothing would let a pen bred past the cap collect the whole refused
     * amount anyway — later, invisibly, and with the cap's own log line saying it had been stopped.
     * <b>Brood already sets this precedent</b> and for the same reason: a hatched chick is
     * deliberately unmarked so an AFK egg farm cannot become a raise-XP farm twenty minutes on.
     *
     * <p>The growth acceleration is applied either way. It is a yield effect, not a payout — no XP
     * flows from a calf growing up sooner unless it also carries the marker — so withholding it
     * would only make a refused breeding feel arbitrarily punished.
     *
     * @param paid whether the breeding that produced this child actually paid Husbandry XP
     */
    private static void claimOffspring(HusbandryManager husbandry, ServerPlayerEntity breeder,
            PassiveEntity child, boolean paid) {
        if (child == null) {
            return; // Egg-laying breeder: the clutch is not an entity we can mark.
        }
        if (paid) {
            child.setAttached(McMMOAttachments.BRED_BY, breeder.getUuid());
        }

        final int acceleratedAge = husbandry.applyGrowthAcceleration(child.getBreedingAge());
        if (acceleratedAge != child.getBreedingAge()) {
            child.setBreedingAge(acceleratedAge);
        }
    }

    /**
     * {@code Twins}: on a successful roll, create and spawn a second baby alongside the first.
     *
     * <p>The twin is built with a fresh {@code createChild} call rather than by copying the child
     * vanilla made, so every species' own offspring logic — a horse's inherited attribute roll, a
     * sheep's dyed-wool colour blend, a mooshroom's variant — runs a second time and the twins are
     * siblings rather than clones.
     *
     * <p><b>D-H4 is satisfied structurally, not by a guard.</b> The plan requires that a Twins baby
     * cannot itself pay Twins in a self-sustaining loop. It cannot: the twin is spawned directly
     * rather than through {@code AnimalEntity#breed}, so no breeding criterion fires for it, so
     * nothing re-enters this method. There is deliberately no flag to forget to set.
     *
     * <p><b>Known deviation, foxes only.</b> {@code FoxEntity$MateGoal} calls {@code trust()} on the
     * child it made, so a fox twin is born untrusting where its sibling is not. Left as-is: the
     * alternative is a species branch here, which is exactly the "lookup table that will rot" the
     * skill's boundary rule exists to avoid.
     */
    private static void maybeBearTwin(McMMOPlayer mmoPlayer, HusbandryManager husbandry,
            ServerPlayerEntity breeder, AnimalEntity parent, AnimalEntity mate,
            PassiveEntity child, boolean paid) {
        if (child == null || mate == null) {
            return; // Egg-laying breeder: vanilla produced no baby for us to double.
        }
        if (!(parent.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        if (!husbandry.rollTwins()) {
            return;
        }

        final PassiveEntity twin = parent.createChild(serverWorld, mate);
        if (twin == null) {
            return; // Species declined to make a second child; nothing to report.
        }
        twin.setBaby(true);
        twin.refreshPositionAndAngles(parent.getX(), parent.getY(), parent.getZ(), 0.0F, 0.0F);
        // Claimed exactly like its sibling, and after setBaby so there is a childhood to shorten.
        // A twin with no bred-by marker would be the one baby in the game whose breeder could never
        // be paid for raising it -- and by the same token it inherits its sibling's refusal, so a
        // breeding the award cap turned down cannot smuggle a payable calf out through Twins.
        claimOffspring(husbandry, breeder, twin, paid);
        serverWorld.spawnEntityAndPassengers(twin);

        NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_MESSAGE,
                "Husbandry.SubSkill.Twins.Proc");
    }

    /**
     * {@code Multi-Breed}: a player has just set one animal in love, so set its nearby same-species
     * neighbours in love too, from that one breeding item.
     *
     * <p>Called from {@code AnimalLovePlayerMixin}. <b>Bounded by the radius alone</b> — every
     * eligible animal it reaches is set in love, however many that is.
     *
     * <h2>The count cap that used to live here (GitHub #3, 2026-08-04)</h2>
     * There was a second bound: at most four neighbours per item. It is gone, and the gate it was
     * standing in for now sits on the XP payout instead
     * ({@link HusbandryManager#getBreedXpAwardsPerWindow()}). Two reasons, and the second is why the
     * move was not merely a preference:
     *
     * <ul>
     *   <li>It taxed the mechanic rather than the reward — the sub-skill exists so you can feed the
     *       pen from where you stand, and a cap of four sent you walking to the rest of the herd.</li>
     *   <li>It bounded XP <b>per item</b>, and wheat is free. Twenty clicks in one breath paid fifty
     *       breedings straight through it, so the farm it was written against was never closed.</li>
     * </ul>
     *
     * <p><b>The sweep's cost is unchanged</b> by removing it: {@code getEntitiesByClass} already
     * walked and filtered every animal in the box, and the cap only decided how many of the survivors
     * got a {@code lovePlayer} call. The radius is what sizes that scan, and it keeps its own hard
     * clamp ({@link HusbandryManager#HARD_MAX_MULTI_BREED_RADIUS}).
     *
     * @param fed    the animal the player actually fed
     * @param player the feeder; ignored unless it is a real server player
     */
    public static void onLovePlayer(AnimalEntity fed, PlayerEntity player) {
        if (SPREADING_LOVE.get()) {
            return; // We are the ones doing the spreading — see the field javadoc.
        }
        if (fed == null || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        final World world = fed.getEntityWorld();
        if (!(world instanceof ServerWorld)) {
            return;
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(serverPlayer.getUuid());
        if (mmoPlayer == null) {
            return;
        }
        final HusbandryManager husbandry = mmoPlayer.getHusbandryManager();
        if (husbandry == null || !husbandry.canMultiBreed()) {
            return;
        }

        final double radius = husbandry.getMultiBreedRadius();
        if (radius <= 0) {
            return;
        }

        final Box searchBox = fed.getBoundingBox().expand(radius);
        final List<AnimalEntity> neighbours = world.getEntitiesByClass(AnimalEntity.class, searchBox,
                candidate -> isMultiBreedCandidate(fed, candidate));

        SPREADING_LOVE.set(true);
        try {
            for (AnimalEntity neighbour : neighbours) {
                neighbour.lovePlayer(serverPlayer);
            }
        } finally {
            SPREADING_LOVE.set(false);
        }
    }

    // --- Stage 2: raise, feed and Accelerated Growth --------------------------------------------

    /**
     * A player has begun interacting with an entity; remember it for the feed verb.
     *
     * <p>Called from {@code PlayerEntityInteractMixin}. See {@link #PLAYER_INTERACTION} for why the
     * feed verb cannot simply hook the feeding methods.
     */
    public static void beginPlayerInteraction(PlayerEntity player, Entity target) {
        if (player instanceof ServerPlayerEntity serverPlayer && target != null) {
            PLAYER_INTERACTION.set(new Interaction(serverPlayer, target));
        }
    }

    /** The interaction has finished, successfully or not. Called from the mixin's RETURN injector. */
    public static void endPlayerInteraction() {
        PLAYER_INTERACTION.remove();
    }

    /**
     * An animal is about to be grown along by {@code growthSeconds}: pay the feed verb, and let
     * {@code Accelerated Growth} double the growth.
     *
     * <p>Called from {@code PassiveEntityGrowthMixin}. <b>Everything hinges on the identity check
     * below.</b> {@code growUp} is reached by plenty of things that are not a player feeding an
     * animal — a lamb eating grass calls it, and a tadpole ages itself through it — so growth only
     * counts as a feed when a player is mid-interaction <em>with this very animal</em>. Without that
     * second half, the AFK wool-farm shape this skill's plan warns about turns into an AFK
     * grass-farm one.
     *
     * @param animal        the animal being grown
     * @param growthSeconds the seconds of growth vanilla was about to apply; positive
     * @return the seconds to actually apply — doubled on a successful Accelerated Growth roll
     */
    public static int onGrowthApplied(PassiveEntity animal, int growthSeconds) {
        if (animal == null || growthSeconds <= 0) {
            return growthSeconds;
        }
        final Interaction interaction = PLAYER_INTERACTION.get();
        if (interaction == null || interaction.target() != animal) {
            return growthSeconds; // Not a player feed: grass, or a tadpole ageing itself.
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(interaction.player().getUuid());
        if (mmoPlayer == null) {
            return growthSeconds;
        }
        final HusbandryManager husbandry = mmoPlayer.getHusbandryManager();
        if (husbandry == null) {
            return growthSeconds;
        }

        husbandry.onFeedBaby(configStringOf(animal));

        // Announced rather than silent, and the comparison is what makes it honest: applyFeedBonus
        // owns the roll, so asking whether it actually doubled beats re-rolling here and reporting a
        // different outcome from the one vanilla is about to act on. Without this the sub-skill's
        // active half is invisible -- a baby simply grows a bit more, with nothing to attribute it
        // to -- and Husbandry.SubSkill.AcceleratedGrowth.Proc ships as a string nothing ever sends.
        final int boostedSeconds = husbandry.applyFeedBonus(growthSeconds);
        if (boostedSeconds > growthSeconds) {
            NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_MESSAGE,
                    "Husbandry.SubSkill.AcceleratedGrowth.Proc");
        }
        return boostedSeconds;
    }

    /**
     * An animal's breeding age is changing: if this is the moment it grows up, pay whoever bred it.
     *
     * <p>Called from {@code PassiveEntityGrowthMixin} at the head of
     * {@code PassiveEntity#setBreedingAge}, so {@code previousAge} is the value still in the field.
     *
     * <h2>⚠️ Why the transition is computed here rather than hooking {@code onGrowUp}</h2>
     * The plan named {@code PassiveEntity#onGrowUp()} as the raise seam and flagged that it fires on
     * <em>both</em> age transitions and on every chunk load of every baby. All of that is true, but
     * bytecode found a fourth problem the plan missed and it is the fatal one:
     * <b>{@code HoglinEntity#onGrowUp()} and {@code GoatEntity#onGrowUp()} do not call
     * {@code super}</b>. A mixin there would have paid <b>zero</b> for goats and hoglins — priced at
     * 400 and 900 in {@code experience.yml} — with nothing to report it.
     * {@code setBreedingAge} is declared only on {@code PassiveEntity}, is overridden by nothing, and
     * is where the {@code onGrowUp} call itself lives, so every path arrives here.
     *
     * <h2>The two gates, both load-bearing</h2>
     * <b>The transition gate</b> is what makes this "grew up" rather than "the age changed": vanilla
     * runs the same code when an adult becomes a baby (a spawn egg) and, because
     * {@code readCustomData} routes through {@code setBreedingAge}, every single time a baby animal
     * loads from disk. Without it, flying away and back would pay the raise verb again.
     * <b>The marker gate</b> is what makes it <em>this player's</em> livestock rather than every wild
     * baby in the world coming of age. Since the marker became a persistent attachment it also
     * survives the world being closed and reopened, so the twenty-minute wait no longer has a hidden
     * "and do not quit" clause attached to it — see {@link McMMOAttachments#BRED_BY}.
     *
     * @param animal      the animal whose age is changing
     * @param previousAge the age it currently has — negative while it is a baby
     * @param newAge      the age it is about to have
     */
    public static void onBreedingAgeChange(PassiveEntity animal, int previousAge, int newAge) {
        if (animal == null || previousAge >= 0 || newAge < 0) {
            return; // Not the baby -> adult crossing.
        }
        // Read and consumed in one call, so this animal can never pay a second time even if
        // something later drives it back across the boundary.
        final UUID breederId = animal.removeAttached(McMMOAttachments.BRED_BY);
        if (breederId == null) {
            return; // Nobody bred this one; it grew up on its own.
        }

        final McMMOPlayer mmoPlayer = UserManager.getPlayer(breederId);
        if (mmoPlayer == null) {
            return;
        }
        final HusbandryManager husbandry = mmoPlayer.getHusbandryManager();
        if (husbandry != null) {
            husbandry.onRaise(configStringOf(animal));
        }
    }

    // --- Stage 3: shear and Bountiful Harvest ---------------------------------------------------

    /**
     * An entity is about to hand out its shear loot: pay the shear verb, and let
     * {@code Bountiful Harvest} double what it drops.
     *
     * <p>Called from {@code LivingEntityShearDropsMixin}.
     *
     * <h2>⚠️ Why {@code forEachShearedItem} and not the four {@code interactMob}s the plan named</h2>
     * The plan said to hook {@code interactMob} on each of {@code SheepEntity},
     * {@code MooshroomEntity}, {@code SnowGolemEntity} and {@code BoggedEntity}. That list was
     * already <b>stale</b>: 1.21.11 has a <b>fifth</b> shearable, {@code CopperGolemEntity}, and
     * enumerating species by hand is the mistake this skill has now made four times over.
     *
     * <p>{@code LivingEntity#forEachShearedItem} is the shared loot funnel underneath all of it, and
     * choosing it settles three separate questions at once:
     * <ul>
     *   <li><b>One injection covers every species</b>, including any shearable a future version
     *       adds, because a new one will roll a shear loot table like the rest.</li>
     *   <li><b>The copper golem is excluded for free.</b> It is the one shearable that reaches no
     *       loot table — shearing it simply takes the poppy out of its hand and drops that — so it
     *       never arrives here. That matters: {@code isShearable()} for a copper golem is just "is
     *       holding a flower", and you can hand it another one and shear again forever, which under
     *       the plan's per-species hook would have been a click-for-300-XP loop. The exclusion falls
     *       out of the seam rather than out of a species blacklist, which is exactly what this
     *       skill's "the line is the verb, never the species" rule asks for.</li>
     *   <li><b>Block shearing stays out</b> structurally: shearing leaves or a pumpkin stem goes
     *       through {@code useOnBlock} and never touches an entity loot table (D-H3).</li>
     * </ul>
     *
     * <h2>⚠️ The dispenser gate is the interaction stash, and it is the whole point</h2>
     * {@code ShearsDispenserBehavior} calls {@code Shearable#sheared} too, and reaches this same
     * funnel — that <em>is</em> the classic AFK wool farm, and it is the single most important thing
     * this method must not pay for. The gate is the stash stage 2 built for the feed verb: a shear
     * counts only when a player is mid-interaction <em>with this very entity</em>. A dispenser
     * opens no stash, so it earns nothing without a species check, a block check, or anything else
     * that could drift out of date.
     *
     * @param sheared the entity handing out loot
     * @param dropper vanilla's own per-item handler — each species drops, converts or equips in its
     *                own way, so the bonus is delivered by calling this again rather than by
     *                spawning an item ourselves
     * @return {@code dropper} unchanged, or a wrapper that delivers everything twice
     */
    /**
     * Open for exactly the duration of one {@code sheared(SoundCategory)} call, and {@code true} only
     * when that shear both belongs to a player and won its {@code Bountiful Harvest} roll.
     *
     * <p>A {@link ThreadLocal} for the same reason as {@link #PLAYER_INTERACTION}: the value is only
     * ever read on the thread that set it, on the same call stack, and it must not survive an
     * exception on an unrelated one.
     *
     * <p>🔑 <b>The window is what makes a seam on {@code Entity#dropStack} safe.</b> That method is
     * how most of the game drops most of its items; gating on an explicitly opened window is what
     * narrows it back down to "items this shear produced".
     */
    private static final ThreadLocal<Boolean> SHEAR_BONUS = ThreadLocal.withInitial(() -> false);

    /**
     * A shear is starting: pay the verb, roll Hidden Bounty, and decide {@code Bountiful Harvest}
     * once for the whole shear.
     *
     * <p>Where vanilla funnels every species' shear loot through one {@code BiConsumer}, this rides
     * that funnel instead and needs no window. Where it does not — each species drops inline, by its
     * own route — the equivalent is this explicit window plus {@link #onShearDropStack}.
     *
     * <p>⚠️ <b>Rolled ONCE per shear rather than per item</b>, which is the behaviour worth
     * preserving across both shapes: a sheep that yields three wool would otherwise resolve the
     * sub-skill three times and turn a clean "this shear paid double" into a noisy partial one.
     */
    public static void beginShear(LivingEntity sheared) {
        SHEAR_BONUS.set(false);
        if (sheared == null) {
            return;
        }
        final Interaction interaction = PLAYER_INTERACTION.get();
        final HusbandryManager husbandry = husbandryOfInteractionWith(sheared);
        if (husbandry == null) {
            return; // A dispenser, or a player whose data is not loaded.
        }

        husbandry.onShear();
        rollHiddenBounty(husbandry, interaction.player(), HIDDEN_BOUNTY_SHEAR);
        SHEAR_BONUS.set(husbandry.rollBonusHarvestDrop());
    }

    /** The shear is over. Always called, including when the shear dropped nothing. */
    public static void endShear() {
        SHEAR_BONUS.remove();
    }

    /**
     * {@code Bountiful Harvest}: hand back a doubled stack while a winning shear window is open.
     *
     * <p>Doubling the stack rather than dropping a second one is deliberate — it is one
     * {@code ItemEntity} instead of two, and it cannot desynchronise from the first drop's position
     * or pickup delay.
     *
     * @return {@code stack} untouched unless a shear window is open and won its roll
     */
    public static ItemStack onShearDropStack(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !Boolean.TRUE.equals(SHEAR_BONUS.get())) {
            return stack;
        }
        final ItemStack doubled = stack.copy();
        doubled.setCount(stack.getCount() * 2);
        return doubled;
    }

    /**
     * A shearing tool is about to take {@code damageAmount} durability: let {@code Bountiful
     * Harvest} spare it.
     *
     * <p>Called from {@code ShearableInteractMixin}. This one cannot ride the loot funnel above —
     * vanilla damages the shears back in {@code interactMob}, after {@code sheared} has returned —
     * so it is the one part of stage 3 that does name the species. That is tolerable here in a way
     * it was not for the XP: an injector that fails to find its target is a <em>load-time
     * failure</em>, so a missing or renamed species is loud, whereas the XP hook going quiet would
     * have been silent.
     *
     * @param sheared      the animal being sheared
     * @param damageAmount the durability vanilla was about to take
     * @return {@code damageAmount}, or {@code 0} on a successful save
     */
    public static int onShearToolDamaged(LivingEntity sheared, int damageAmount) {
        if (sheared == null || damageAmount <= 0) {
            return damageAmount;
        }
        final HusbandryManager husbandry = husbandryOfInteractionWith(sheared);
        if (husbandry == null) {
            return damageAmount;
        }
        return husbandry.rollToolDurabilitySave() ? 0 : damageAmount;
    }

    // --- Stage 4: hive, milk, brush, Beekeeper and the D-H5 harvest cooldown ---------------------

    /**
     * The world tick at which one animal last paid a Husbandry harvest award.
     *
     * <p>Transient on purpose, and that is the whole reason it is a {@link MetadataStore} entry rather
     * than the persistent attachment the bred-by marker became: this is a five-minute window, so
     * losing it when the world closes costs a player one early payout and nothing else. The bred-by
     * marker had to persist because <em>twenty minutes of growth</em> would otherwise hinge invisibly
     * on whether you quit in between; five minutes of cooldown does not have that problem.
     */
    private static final String HARVEST_COOLDOWN_KEY = "mcMMO_husbandryHarvestTick";

    /*
     * The treasures.yml Drops_From verb groups Hidden Bounty is keyed on. Named constants rather than
     * inline literals because they must match the shipped YAML exactly and a typo would be a sub-skill
     * that silently never finds anything -- HusbandryTreasureConfigTest pins them against the file.
     */
    static final String HIDDEN_BOUNTY_SHEAR = "Shear";
    static final String HIDDEN_BOUNTY_HIVE = "Hive";
    static final String HIDDEN_BOUNTY_MILK = "Milk";
    static final String HIDDEN_BOUNTY_BRUSH = "Brush";

    /**
     * A hive gave up its honeycomb to a player's shears: pay the hive verb, and let
     * {@code Bountiful Harvest} and {@code Beekeeper} add to the haul.
     *
     * <p>Called from {@code BeehiveHarvestMixin}.
     *
     * <h2>⚠️ Why the trigger is here and not on {@code takeHoney}, which the plan named</h2>
     * The plan said {@code takeHoney} has "a player overload and a 3-arg automated one" and to gate on
     * the player overload. <b>Bytecode says that is not what the two overloads are.</b> The 3-arg form
     * is simply the "set the honey level back to zero" primitive; the 5-arg form calls it and
     * <em>then</em> angers the hive. And {@code onUseWithItem} — the player path, the only path a
     * human ever takes — calls the <b>3-arg</b> form directly whenever a lit campfire is in range.
     * Gating on the "player overload" would therefore have paid <b>zero</b> for every hive harvested
     * over a campfire, which is how essentially every bee farm in the game is built, while paying
     * normally for the careless harvests that get you stung. Silent, and backwards.
     *
     * <p>{@code onUseWithItem} is also what closes the automation, structurally and without a check of
     * our own: jar-grep finds {@code ShearsDispenserBehavior} calling {@code dropHoneycomb} and
     * {@code DispenserBehavior$3} calling {@code takeHoney}, so <b>both</b> halves of this verb can be
     * fully automated in vanilla — but neither dispenser goes anywhere near {@code onUseWithItem}.
     * Hooking the block's use path is the gate.
     *
     * @param player  the harvester
     * @param usedItem the shears in their hand, already worn by this harvest
     * @param state   the hive's state, still reading a full honey level at this point
     * @param world   the hive's world
     * @param pos     the hive
     */
    public static void onHoneycombHarvested(PlayerEntity player, ItemStack usedItem, BlockState state,
            World world, BlockPos pos) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)
                || !(world instanceof ServerWorld serverWorld)) {
            return;
        }
        final HusbandryManager husbandry = husbandryOf(serverPlayer);
        if (husbandry == null) {
            return;
        }
        husbandry.onHiveHarvest();

        // Delivered as extra rolls of vanilla's own harvest loot table rather than as honeycombs we
        // fabricate, exactly as the shear bonus re-runs the species' own drop handler: the yield stays
        // whatever the game says a hive yields, including any future change to it.
        for (int helping = bonusHiveHelpings(husbandry); helping > 0; helping--) {
            BeehiveBlock.dropHoneycomb(serverWorld, pos);
        }
        rollHiddenBounty(husbandry, serverPlayer, HIDDEN_BOUNTY_HIVE);
    }

    /**
     * A hive gave up a bottle of honey to a player: pay the hive verb, and let
     * {@code Bountiful Harvest} and {@code Beekeeper} add to the haul.
     *
     * <p>The bottle half of the same verb, hooked separately from
     * {@link #onHoneycombHarvested} because the two halves of {@code onUseWithItem} produce their
     * yields by completely different means — the shears roll a loot table, the bottle hands over one
     * hard-coded {@code HONEY_BOTTLE} — and because splitting them is what makes each hook
     * unambiguous about which harvest it is looking at.
     *
     * @param player the harvester
     */
    public static void onHoneyBottled(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        final HusbandryManager husbandry = husbandryOf(serverPlayer);
        if (husbandry == null) {
            return;
        }
        husbandry.onHiveHarvest();

        for (int helping = bonusHiveHelpings(husbandry); helping > 0; helping--) {
            giveOrDrop(serverPlayer, new ItemStack(Items.HONEY_BOTTLE));
        }
        rollHiddenBounty(husbandry, serverPlayer, HIDDEN_BOUNTY_HIVE);
    }

    /**
     * How many <em>extra</em> helpings this hive harvest yields, 0–2.
     *
     * <p>The two rolls stack rather than being an either/or, which is the deliberate difference
     * between the harvest family's general bonus and the bee specialist's: a maxed beekeeper should
     * visibly out-yield a maxed generalist at a hive, and cannot if their sub-skill only re-rolls the
     * same coin.
     *
     * <p>Package-private so the stacking can be asserted directly. The delivery it feeds cannot be
     * unit-tested — an extra {@code dropHoneycomb} roll needs a real {@code ServerWorld} and a real
     * loot table — so the arithmetic is pinned here and the delivery is a §G row.
     */
    static int bonusHiveHelpings(HusbandryManager husbandry) {
        return (husbandry.rollBonusHarvestDrop() ? 1 : 0) + (husbandry.rollBonusHoney() ? 1 : 0);
    }

    /**
     * Whether this player's hive harvests leave the bees alone — {@code Beekeeper}.
     *
     * <p>Called from {@code BeehiveHarvestMixin}, which feeds the answer into vanilla's own
     * "is there a lit campfire under this hive" test. That is the entire mechanic: vanilla already has
     * exactly one branch for "this harvest was sheltered", and taking it covers <b>both</b> ways a
     * harvest can anger bees — {@code angerNearbyBees}, which sets nearby bees on the player, and
     * {@code takeHoney(..., BeeState.EMERGENCY)}, which drives the hive's own occupants out angry.
     * Suppressing the first alone, which is what the plan proposed, would have left the second firing.
     */
    public static boolean hiveHarvestLeavesBeesCalm(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return false;
        }
        final HusbandryManager husbandry = husbandryOf(serverPlayer);
        return husbandry != null && husbandry.countsAsShelteredHiveHarvest();
    }

    /**
     * A hive is about to wear the shears that robbed it: let {@code Bountiful Harvest} spare them.
     *
     * <p>Called from {@code BeehiveHarvestMixin}. The gate is the tool's own holder, which vanilla
     * hands to the {@code damage} call — no interaction stash needed, because a dispenser harvesting a
     * hive never reaches {@code onUseWithItem} at all.
     *
     * @param holder       whoever is holding the shears
     * @param damageAmount the durability vanilla was about to take
     * @return {@code damageAmount}, or {@code 0} on a successful save
     */
    public static int onHiveToolDamaged(LivingEntity holder, int damageAmount) {
        if (damageAmount <= 0 || !(holder instanceof ServerPlayerEntity player)) {
            return damageAmount;
        }
        final HusbandryManager husbandry = husbandryOf(player);
        if (husbandry == null) {
            return damageAmount;
        }
        return husbandry.rollToolDurabilitySave() ? 0 : damageAmount;
    }

    /**
     * A player milked a cow or a goat, or bowled a mooshroom's stew: pay the milk verb.
     *
     * <p>Called from {@code CowMilkMixin} — which targets the cow family <b>and</b>
     * {@code GoatEntity}, since a goat re-implements the bucket branch inline rather than inheriting
     * it — and from {@code MooshroomStewMixin}. Every route is the same verb and shares this one body
     * and one cooldown, so a mooshroom cannot be milked and stewed for two awards in the same breath,
     * and a newly added target inherits the gate rather than needing it wired again.
     *
     * <p><b>Vanilla rate-limits this verb by nothing at all</b> — right-clicking the same cow with a
     * bucket is free and repeatable as fast as a player can click — so it is the D-H5 cooldown, not
     * any game mechanic, that bounds it. Unlike the shear and hive verbs there is no interaction stash
     * to check: {@code interactMob} takes the {@code PlayerEntity} directly and no dispenser reaches
     * it, so the real-player gate is the signature.
     *
     * @param animal the cow, goat or mooshroom
     * @param player the milker
     */
    public static void onMilked(Entity animal, PlayerEntity player) {
        if (animal == null || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        final HusbandryManager husbandry = husbandryOf(serverPlayer);
        if (husbandry == null || !harvestCooldownElapsed(husbandry, animal)) {
            return;
        }
        husbandry.onMilk();
        rollHiddenBounty(husbandry, serverPlayer, HIDDEN_BOUNTY_MILK);
    }

    /**
     * A player brushed an armadillo and a scute was actually delivered: pay the brush verb, and let
     * {@code Bountiful Harvest} deliver a second one.
     *
     * <h2>&#9888; Why this pays on the drop and the shear verb pays on the attempt</h2>
     * Shearing is gated upstream by {@code isShearable()} — a sheep with no wool cannot be sheared at
     * all — so by the time the shear seam is reached, a harvest has definitely happened.
     * <b>Brushing has no such gate</b> beyond age: {@code brushScute} refuses a baby and succeeds for
     * any adult, and {@code nextScuteShedCooldown} — the timer cited as vanilla's own limit — governs
     * only the passive shed and is never read or reset on this path. So the caller hands us
     * {@code brushScute}'s own result and this pays only on a real delivery.
     *
     * <h2>&#128273; The real-player gate is the call site, not the signature</h2>
     * Where vanilla's brush loot funnel takes the brushing entity as a parameter, a {@code null}
     * brusher excludes the dispenser by the signature itself. Where it does not, the equivalent gate
     * is <b>which method we hang off</b>: {@code interactMob} is only ever reached by a player, and
     * vanilla's armadillo-brushing dispenser behaviour does not go through it. That is a stricter
     * gate, not a weaker one — but it is a different reason, and it lives at the call site.
     *
     * @param armadillo the animal being brushed
     * @param brusher   whoever is brushing
     * @param brushed   whether vanilla's own {@code brushScute} actually handed over a scute
     * @return {@code true} if {@code Bountiful Harvest} won and a second scute is owed
     */
    public static boolean onBrushed(Entity armadillo, Entity brusher, boolean brushed) {
        // 🔑 The "a scute really changed hands" gate lives HERE rather than in the mixin, so that a
        // test can reach it. It is the whole basis of this verb -- brushing has no upstream gate the
        // way isShearable() gates shearing -- and a guard the caller owns is a guard nothing proves.
        if (!brushed) {
            return false;
        }
        if (armadillo == null || !(brusher instanceof ServerPlayerEntity player)) {
            return false;
        }
        final HusbandryManager husbandry = husbandryOf(player);
        if (husbandry == null) {
            return false;
        }
        // The cooldown gates the reward, never the drop: a brush inside the window still yields its
        // scute, it simply does not pay again. Refusing vanilla's own loot would be a mod quietly
        // breaking the game to enforce its own balance.
        if (!harvestCooldownElapsed(husbandry, armadillo)) {
            return false;
        }
        husbandry.onBrush();
        rollHiddenBounty(husbandry, player, HIDDEN_BOUNTY_BRUSH);
        return husbandry.rollBonusHarvestDrop();
    }

    /**
     * A brush is about to take {@code damageAmount} durability: let {@code Bountiful Harvest} spare it.
     *
     * <p>Called from {@code ArmadilloBrushMixin}. The shear verb's durability save has an exact
     * sibling here for the same reason it had to name species there — vanilla wears the tool back in
     * {@code interactMob}, after the loot funnel has returned. It is worth 16 durability a brush,
     * against a brush's total of 64, so this is a much larger effect than the shear save.
     *
     * @param armadillo    the animal being brushed
     * @param damageAmount the durability vanilla was about to take
     * @return {@code damageAmount}, or {@code 0} on a successful save
     */
    public static int onBrushToolDamaged(Entity armadillo, int damageAmount) {
        if (armadillo == null || damageAmount <= 0) {
            return damageAmount;
        }
        final HusbandryManager husbandry = husbandryOfInteractionWith(armadillo);
        if (husbandry == null) {
            return damageAmount;
        }
        return husbandry.rollToolDurabilitySave() ? 0 : damageAmount;
    }

    // --- Stage 5: Selective Breeding, Brood and Hidden Bounty -----------------------------------

    /**
     * The Selective Breeding bias in force for the breeding currently being resolved, or {@code null}
     * outside one.
     *
     * <p><b>This exists because vanilla's inheritance roll is static and holds no player.</b>
     * {@code AbstractHorseEntity.calculateAttributeBaseValue} is where the foal's health, speed and
     * jump strength are actually decided, and it takes five numbers and a {@code Random} — there is
     * nobody in it to ask. So the bias is computed once, at the one point on the path that <em>is</em>
     * an instance method on a parent, and read back inside the static call.
     *
     * <p>Same {@link ThreadLocal} HEAD/RETURN shape as the feed verb's interaction stash, and for the
     * same reason: the whole window is one synchronous call on the server thread.
     *
     * <p>It holds the breeder's <b>manager</b> rather than a pre-computed bias so that the biasing
     * arithmetic lives in exactly one place — {@link HusbandryManager#applyStatBias} — where it is
     * MC-free and unit-tested. Two copies of a balance formula is how the two halves drift apart, and
     * re-reading the config three times per breeding (once per inherited attribute) costs nothing.
     */
    private static final ThreadLocal<HusbandryManager> SELECTIVE_BREEDING = new ThreadLocal<>();

    /**
     * A horse-family pair is about to roll their foal's stats: work out whose Selective Breeding
     * applies.
     *
     * <p>Called from {@code HorseChildAttributesMixin}. Either parent will do — vanilla sets the loving
     * player on whichever animal was fed, and it only reaches breeding when at least one has one.
     * Resolving the bias here rather than in the static call is the whole point of the stash.
     */
    public static void beginSelectiveBreeding(AnimalEntity parent, AnimalEntity mate) {
        final HusbandryManager husbandry = husbandryOfBreeder(parent, mate);
        if (husbandry == null) {
            return; // AI-driven or command-driven breeding: nobody's sub-skill applies.
        }
        SELECTIVE_BREEDING.set(husbandry);
    }

    /** The breeding has finished rolling its stats. Called from the mixin's RETURN injector. */
    public static void endSelectiveBreeding() {
        SELECTIVE_BREEDING.remove();
    }

    /**
     * Apply the stashed bias to one rolled offspring stat.
     *
     * <p>Called from {@code HorseChildAttributesMixin}. Returns {@code rolled} untouched when no
     * breeding is in flight, which is the common case by a wide margin: this same static method runs
     * for every horse bred anywhere in the world, including with no player involved.
     */
    public static double applySelectiveBreedingBias(double rolled, double min, double max) {
        final HusbandryManager husbandry = SELECTIVE_BREEDING.get();
        return husbandry == null ? rolled : husbandry.applyStatBias(rolled, min, max);
    }

    /** The Husbandry manager of whichever parent vanilla credits with the breeding, or {@code null}. */
    private static HusbandryManager husbandryOfBreeder(AnimalEntity parent, AnimalEntity mate) {
        for (AnimalEntity candidate : new AnimalEntity[] {parent, mate}) {
            if (candidate == null) {
                continue;
            }
            final ServerPlayerEntity breeder = candidate.getLovingPlayer();
            if (breeder != null) {
                final HusbandryManager husbandry = husbandryOf(breeder);
                if (husbandry != null) {
                    return husbandry;
                }
            }
        }
        return null;
    }

    /**
     * {@code Brood}: rescue a thrown egg vanilla was about to waste.
     *
     * <p>Called from {@code EggHatchMixin}. Returning {@code 0} makes vanilla take its own hatch
     * branch, so Brood's chance <em>adds to</em> the vanilla 1-in-8 rather than replacing it.
     *
     * @param egg  the thrown egg, whose owner is the thrower
     * @param roll vanilla's own {@code nextInt(8)}; {@code 0} already means "hatch"
     * @return {@code roll}, or {@code 0} to force a hatch
     */
    public static int onEggHatchRoll(Entity egg, int roll) {
        if (roll == 0) {
            return roll; // Vanilla is already hatching it; nothing to add.
        }
        final HusbandryManager husbandry = husbandryOfThrower(egg);
        return husbandry != null && husbandry.rollEggHatch() ? 0 : roll;
    }

    /**
     * {@code Brood}: turn a hatch into vanilla's rare full clutch.
     *
     * @param egg  the thrown egg
     * @param roll vanilla's own {@code nextInt(32)}; {@code 0} already means "four chicks"
     * @return {@code roll}, or {@code 0} to force a full clutch
     */
    public static int onFullClutchRoll(Entity egg, int roll) {
        if (roll == 0) {
            return roll;
        }
        final HusbandryManager husbandry = husbandryOfThrower(egg);
        return husbandry != null && husbandry.rollMultipleChicks() ? 0 : roll;
    }

    /**
     * The Husbandry manager of whoever threw a projectile, or {@code null}.
     *
     * <p>The owner check is also the dispenser gate: eggs are dispensable in vanilla, and a dispensed
     * egg has no player owner.
     */
    private static HusbandryManager husbandryOfThrower(Entity projectile) {
        if (!(projectile instanceof ProjectileEntity thrown)) {
            return null;
        }
        return thrown.getOwner() instanceof ServerPlayerEntity thrower ? husbandryOf(thrower) : null;
    }

    /**
     * {@code Hidden Bounty}: roll for a rare find on a harvest, and hand it over.
     *
     * <p>One body shared by all four harvest verbs — the plan's instruction not to write four copies of
     * the drop logic. The verb arrives as the {@code treasures.yml} {@code Drops_From} group name, which
     * is what lets the table be keyed on the <em>act</em> rather than on the species: keying on the
     * animal would need a row per mob and would rot the first time a version added one.
     *
     * <p>The selection itself is MC-free and lives in the manager, taking both random draws as
     * arguments; this method owns only the config read and the item spawn. Same split as Hylian Luck's
     * and Fishing's treasure rolls.
     *
     * @param husbandry the harvester's manager
     * @param player    the harvester, who the find is given to
     * @param verb      {@code "Shear"}, {@code "Hive"}, {@code "Milk"} or {@code "Brush"}
     */
    private static void rollHiddenBounty(HusbandryManager husbandry, ServerPlayerEntity player,
            String verb) {
        final TreasureConfig treasures = McMMOMod.getTreasureConfig();
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(player.getUuid());
        if (treasures == null || mmoPlayer == null) {
            return; // Not loaded (early boot, or a unit test with no config bound).
        }
        final List<HusbandryTreasure> candidates = treasures.getHusbandryTreasures(verb);
        if (candidates.isEmpty()) {
            return;
        }
        final Optional<HusbandryTreasure> won = husbandry.selectHiddenBounty(candidates,
                husbandry.rollHiddenBounty(),
                chance -> ProbabilityUtil.isStaticSkillRNGSuccessful(PrimarySkillType.HUSBANDRY,
                        mmoPlayer, chance));
        if (won.isEmpty()) {
            return;
        }

        final HusbandryTreasure treasure = won.get();
        final Optional<ItemStack> built = ItemSpecBuilder.build(treasure.getDrop());
        if (built.isEmpty()) {
            // The treasure names a material with no vanilla item (already logged by Materials).
            // Deliberately silent beyond that: announcing a find nobody received would be worse.
            return;
        }
        giveOrDrop(player, built.get());
        husbandry.onHiddenBountyFound(treasure.getXp());
        NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_MESSAGE,
                "Husbandry.SubSkill.HiddenBounty.Proc");
    }

    /**
     * Whether {@code animal} is off its Husbandry harvest cooldown, consuming the window if it is.
     *
     * <p>Measured in <b>world ticks, not wall-clock milliseconds</b>, which is the right clock for a
     * singleplayer game: a cooldown counted in real time would keep draining while the world is
     * paused in the menu, so a player could stand at a cow, open the pause menu for five minutes and
     * come back to a fresh payout. It also makes the arithmetic testable without a fake clock.
     *
     * <p>A negative elapsed time counts as elapsed. The world's clock can legitimately move backwards
     * ({@code /time set}, or an animal led into a dimension keeping its own count), and the failure
     * mode of ignoring that is the worst one available — the animal would be locked out of paying
     * anything ever again, silently.
     */
    private static boolean harvestCooldownElapsed(HusbandryManager husbandry, Entity animal) {
        // Herdsman's Call's cooldown-bypass half. Placed here rather than at the two call sites so it
        // cannot be wired into milking and forgotten for brushing, and it deliberately does NOT stamp
        // the animal's timestamp: a bypassed harvest leaves the ordinary cooldown exactly where it was,
        // so blowing the horn over a herd cannot also reset every animal's clock and hand the player a
        // second full round the moment the ability ends.
        if (husbandry.isHerdsmansCallActive()) {
            return true;
        }
        final int seconds = husbandry.getHarvestCooldownSeconds();
        if (seconds <= 0) {
            return true; // Gate configured off.
        }
        final long now = animal.getEntityWorld().getTime();
        final Long lastAward = MetadataStore.get(animal, HARVEST_COOLDOWN_KEY, Long.class);
        if (lastAward != null) {
            final long elapsed = now - lastAward;
            if (elapsed >= 0 && elapsed < (long) seconds * Misc.TICK_CONVERSION_FACTOR) {
                return false;
            }
        }
        MetadataStore.set(animal, HARVEST_COOLDOWN_KEY, now);
        return true;
    }

    /** Hand a bonus stack to the player, dropping it at their feet if they have no room. */
    private static void giveOrDrop(ServerPlayerEntity player, ItemStack stack) {
        if (!player.getInventory().insertStack(stack)) {
            player.dropItem(stack, false);
        }
    }

    /**
     * The Husbandry manager of the player currently interacting with {@code target}, or {@code null}
     * if nobody is.
     *
     * <p>The shared real-player gate for every harvest verb (D-H4). The identity check is what makes
     * it a gate rather than a hint: without it, any harvest anywhere during a right-click — a
     * dispenser firing in the same tick, on the other side of the world — would bill to whoever
     * happened to have a hand out.
     */
    private static HusbandryManager husbandryOfInteractionWith(Entity target) {
        final Interaction interaction = PLAYER_INTERACTION.get();
        if (interaction == null || interaction.target() != target) {
            return null;
        }
        return husbandryOf(interaction.player());
    }

    /** The Husbandry manager for a server player, or {@code null} if their data is not loaded. */
    private static HusbandryManager husbandryOf(ServerPlayerEntity player) {
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(player.getUuid());
        return mmoPlayer == null ? null : mmoPlayer.getHusbandryManager();
    }

    /** The animal's {@code experience.yml} key, e.g. {@code "Cow"}. */
    private static String configStringOf(Entity animal) {
        return ConfigStringUtils.getConfigEntityTypeString(
                Registries.ENTITY_TYPE.getId(animal.getType()).getPath());
    }

    /**
     * Whether one nearby animal should be swept up by Multi-Breed.
     *
     * <p>Mirrors the conditions vanilla's own {@code AnimalEntity#interactMob} requires before it
     * will accept a breeding item: an adult, off its post-breeding cooldown, not already courting.
     * {@code getBreedingAge() == 0} covers both halves of that cooldown — it is negative while the
     * animal is still a baby and positive for the five minutes after a breeding — and
     * {@code canEat()} is vanilla's own name for "not already in love".
     */
    private static boolean isMultiBreedCandidate(AnimalEntity fed, AnimalEntity candidate) {
        return candidate != fed
                && candidate.isAlive()
                && candidate.getType() == fed.getType()
                && candidate.getBreedingAge() == 0
                && candidate.canEat();
    }
}
