package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.platform.MetadataStore;
import com.gmail.nossr50.skills.husbandry.HusbandryManager;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.text.ConfigStringUtils;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

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
     * {@code MetadataStore} key holding the {@link UUID} of the player who bred an animal — D-H6's
     * "bred by" marker.
     *
     * <p>The raise verb pays roughly twenty minutes after the act it rewards, so the child has to
     * carry its breeder with it. The store is in-memory by design (ruled 2026-07-29): an animal bred
     * before a server restart pays nothing when it matures. That fails in the safe direction — never
     * the wrong player, never twice — and a singleplayer session almost always outlives a growth
     * timer that only ticks while the chunk is loaded anyway.
     *
     * <p>The marker is <b>removed as it is consumed</b>, which is also what makes "pays once per
     * animal" true rather than merely likely. What is left behind is the marker on an animal that
     * never grows up (killed, or in a chunk that stays unloaded until the server stops) — a few
     * dozen bytes each until {@code MetadataStore.clearAll()} at shutdown, the same bargain
     * {@code Archery}'s arrow counters already make.
     */
    private static final String BRED_BY_KEY = "mcmmo_husbandry_bred_by";

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

        final String entityConfigString = ConfigStringUtils.getConfigEntityTypeString(
                Registries.ENTITY_TYPE.getId(parent.getType()).getPath());
        husbandry.onBreed(entityConfigString);

        claimOffspring(husbandry, breeder, child);
        maybeBearTwin(mmoPlayer, husbandry, breeder, parent, mate, child);
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
     */
    private static void claimOffspring(HusbandryManager husbandry, ServerPlayerEntity breeder,
            PassiveEntity child) {
        if (child == null) {
            return; // Egg-laying breeder: the clutch is not an entity we can mark.
        }
        MetadataStore.set(child, BRED_BY_KEY, breeder.getUuid());

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
            PassiveEntity child) {
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
        // be paid for raising it.
        claimOffspring(husbandry, breeder, twin);
        serverWorld.spawnEntityAndPassengers(twin);

        NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_MESSAGE,
                "Husbandry.SubSkill.Twins.Proc");
    }

    /**
     * {@code Multi-Breed}: a player has just set one animal in love, so set its nearby same-species
     * neighbours in love too, from that one breeding item.
     *
     * <p>Called from {@code AnimalLovePlayerMixin}. Bounded on <b>two</b> independent axes and both
     * matter: the radius decides how far a player can reach, while
     * {@link HusbandryManager#getMultiBreedMaxAdditionalAnimals()} decides how much XP a single
     * click can be worth. Husbandry pays per breeding, so without the count cap one item in a large
     * pen would pay dozens of breedings at once — see that method for the full reasoning.
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

        final int maxAdditional = husbandry.getMultiBreedMaxAdditionalAnimals();
        final double radius = husbandry.getMultiBreedRadius();
        if (maxAdditional <= 0 || radius <= 0) {
            return;
        }

        final Box searchBox = fed.getBoundingBox().expand(radius);
        final List<AnimalEntity> neighbours = world.getEntitiesByClass(AnimalEntity.class, searchBox,
                candidate -> isMultiBreedCandidate(fed, candidate));

        SPREADING_LOVE.set(true);
        try {
            int spread = 0;
            for (AnimalEntity neighbour : neighbours) {
                if (spread >= maxAdditional) {
                    break;
                }
                neighbour.lovePlayer(serverPlayer);
                spread++;
            }
        } finally {
            SPREADING_LOVE.set(false);
        }
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
        final HusbandryManager husbandry = husbandryOf(interaction.player());
        if (husbandry == null) {
            return growthSeconds;
        }

        husbandry.onFeedBaby(configStringOf(animal));
        return husbandry.applyFeedBonus(growthSeconds);
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
     * baby in the world coming of age.
     *
     * @param animal      the animal whose age is changing
     * @param previousAge the age it currently has — negative while it is a baby
     * @param newAge      the age it is about to have
     */
    public static void onBreedingAgeChange(PassiveEntity animal, int previousAge, int newAge) {
        if (animal == null || previousAge >= 0 || newAge < 0) {
            return; // Not the baby -> adult crossing.
        }
        final UUID breederId = MetadataStore.get(animal, BRED_BY_KEY, UUID.class);
        if (breederId == null) {
            return; // Nobody bred this one; it grew up on its own.
        }
        // Consumed as it is read, so this animal can never pay a second time even if something
        // later drives it back across the boundary.
        MetadataStore.remove(animal, BRED_BY_KEY);

        final McMMOPlayer mmoPlayer = UserManager.getPlayer(breederId);
        if (mmoPlayer == null) {
            return;
        }
        final HusbandryManager husbandry = mmoPlayer.getHusbandryManager();
        if (husbandry != null) {
            husbandry.onRaise(configStringOf(animal));
        }
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

    private static boolean isMultiBreedCandidate(AnimalEntity fed, AnimalEntity candidate) {
        return candidate != fed
                && candidate.isAlive()
                && candidate.getType() == fed.getType()
                && candidate.getBreedingAge() == 0
                && candidate.canEat();
    }
}
