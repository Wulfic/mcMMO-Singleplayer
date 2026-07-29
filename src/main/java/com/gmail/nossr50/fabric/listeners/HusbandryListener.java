package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.skills.husbandry.HusbandryManager;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.text.ConfigStringUtils;
import java.util.List;
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

        maybeBearTwin(mmoPlayer, husbandry, parent, mate, child);
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
            AnimalEntity parent, AnimalEntity mate, PassiveEntity child) {
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
    private static boolean isMultiBreedCandidate(AnimalEntity fed, AnimalEntity candidate) {
        return candidate != fed
                && candidate.isAlive()
                && candidate.getType() == fed.getType()
                && candidate.getBreedingAge() == 0
                && candidate.canEat();
    }
}
