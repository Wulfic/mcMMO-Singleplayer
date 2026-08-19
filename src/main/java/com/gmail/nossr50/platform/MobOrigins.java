package com.gmail.nossr50.platform;

import com.gmail.nossr50.datatypes.mobs.MobOrigin;
import com.gmail.nossr50.fabric.McMMOAttachments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads and writes a mob's {@link MobOrigin} — the Minecraft-typed half of Hunter's D-HU1 anti-farm
 * gate. {@link MobOrigin} owns the vocabulary and the "does it count" predicate; this owns the mapping
 * from vanilla's {@link SpawnReason} and the attachment access.
 *
 * <h2>The seam: whatever carries the spawn reason on this version</h2>
 * The plan implied {@code MobEntity#initialize(ServerWorldAccess, LocalDifficulty, SpawnReason,
 * EntityData)}, which is where both spawner logics hand a reason to a freshly built mob. <b>It is not
 * safe on any version.</b> {@code CaveSpiderEntity} overrides {@code initialize} with a bare
 * {@code return entityData} that deliberately skips {@code SpiderEntity}'s jockey logic and never
 * calls {@code super} — so a mixin there misses cave spiders entirely, and a mineshaft cave-spider
 * spawner is one of the two or three most-built grinders in the game.
 *
 * <p>Where vanilla funnels every creation through <b>one</b> factory that carries the reason
 * ({@code EntityType#create(World, SpawnReason)}), that factory is the seam and one injector covers
 * everything: it is an instance method on {@code EntityType}, a class with no vanilla subclasses, so
 * no mob can override it, and it ignores its own {@code SpawnReason} parameter, so reading the reason
 * there perturbs nothing.
 *
 * <h2>&#128273;&#128273; Where that funnel does not exist, ONE injector is a TRAP</h2>
 * On versions without it the reason-carrying entry points do <b>not</b> converge, verified against the
 * merged jar:
 *
 * <ul>
 *   <li>{@code MobSpawnerLogic}/{@code TrialSpawnerLogic} reach
 *       {@code EntityType.loadEntityWithPassengers(NbtCompound, World, Function)}, which <b>takes no
 *       {@code SpawnReason} at all</b> — the spawner's own {@code SpawnReason.SPAWNER} goes only to
 *       {@code canSpawn} and {@code initialize}, never to entity creation</li>
 *   <li>{@code AnimalEntity#breed} reaches {@code createChild} → {@code EntityType.create(World)} —
 *       also no reason</li>
 *   <li>only spawn eggs, dispensers and portals reach
 *       {@code create(ServerWorld, Consumer, BlockPos, SpawnReason, boolean, boolean)}</li>
 * </ul>
 *
 * <p><b>That last method exists on those versions, so retargeting to it BINDS</b> — the mixin audit
 * goes green while spawner-farmed and bred mobs are silently left unmarked. That is strictly worse
 * than an injector that resolves to nothing, because a dead injector is at least loud. So on those
 * versions the stamp is split per origin, one injector at each real spawn site, and
 * {@link #stampOnSpawn(Entity, SpawnReason)} is the entry they share.
 *
 * <h2>Never write a qualifying origin</h2>
 * {@link #stampOnSpawn} returns without touching the entity when the reason maps to
 * {@link MobOrigin#NATURAL}. That is required, not tidy: some spawn reasons re-introduce a mob that
 * <em>already carries a marker</em> — restored from a previous session, or arriving from the far
 * side of a portal — and a write here would erase it. Which reasons those are is version-specific
 * and some do not exist on every supported version; the invariant that does not change is
 * <b>never write a qualifying origin</b>. See {@code McMMOAttachments#MOB_ORIGIN}.
 */
public final class MobOrigins {

    private static final Logger LOGGER = LoggerFactory.getLogger("mcMMO");

    /**
     * Guards a single INFO line the first time this session marks a mob.
     *
     * <p>This exists for the play-test, and it is the {@code [[smelting-furnace-arm]]} trick: the gate
     * is invisible by construction — it produces no message, no particle and no observable effect
     * until a Hunter mastery counter exists to refuse — so "spawner mobs do not count" and "the mixin
     * silently never bound" look identical from inside the game. One INFO line separates them.
     * {@code AtomicBoolean} because entity creation is not confined to the server thread; worldgen
     * builds mobs on the chunk-generation executor.
     */
    private static final java.util.concurrent.atomic.AtomicBoolean LOGGED_FIRST_MARK =
            new java.util.concurrent.atomic.AtomicBoolean();

    private MobOrigins() {
    }

    /**
     * This mob's origin — {@link MobOrigin#NATURAL} when it carries no marker, which is the case for
     * every mob the world spawned by its own rules.
     */
    public static @NotNull MobOrigin of(@NotNull Entity entity) {
        final String stored = entity.getAttached(McMMOAttachments.MOB_ORIGIN);
        if (stored == null) {
            return MobOrigin.NATURAL;
        }
        final MobOrigin resolved = MobOrigin.byName(stored);
        if (resolved == null) {
            // Fail closed. A marker exists, so something disqualified this mob; a value this build
            // cannot read is not a licence to count it. Logged because it can only mean a downgrade
            // or a hand-edited region file, both of which someone should hear about.
            LOGGER.warn("Unrecognised mcMMO mob-origin marker '{}' on {} — treating it as "
                            + "disqualified for Hunter mastery.",
                    stored, entity.getType());
            return MobOrigin.UNKNOWN;
        }
        return resolved;
    }

    /**
     * Whether killing this mob may advance a Hunter mob-mastery counter. The single question every
     * caller outside this class should be asking.
     */
    public static boolean countsTowardMastery(@NotNull Entity entity) {
        return of(entity).countsTowardMastery();
    }

    /**
     * Stamps a disqualifying origin onto a mob as it is created. Called from
     * {@code EntityTypeSpawnOriginMixin} for every {@code EntityType#create(World, SpawnReason)}.
     *
     * @param world  the world the entity is being created in; client-side creations are ignored
     * @param reason vanilla's reason for the spawn
     * @param entity the new entity, or {@code null} when the type is behind a disabled feature flag
     */
    public static void stampOnSpawn(@NotNull World world, @NotNull SpawnReason reason,
            @Nullable Entity entity) {
        if (entity == null || world.isClient() || !(entity instanceof LivingEntity)) {
            return;
        }
        final MobOrigin origin = classify(reason);
        if (origin.countsTowardMastery()) {
            // See the class doc: writing here would clobber a marker that a re-introducing spawn
            // reason is about to restore.
            return;
        }
        entity.setAttached(McMMOAttachments.MOB_ORIGIN, origin.storageKey());
        announceFirstMark(origin, reason);
    }

    /**
     * Stamps an origin onto a mob whose spawn site knows the reason but does not carry it as a
     * parameter — a spawner's mob, or a bred child.
     *
     * <p>The world is read off the entity rather than passed in, because these call sites have the
     * finished entity and not always a separate world reference. Everything else — the client guard,
     * the {@code LivingEntity} narrowing, and the "never write a qualifying origin" rule — is
     * delegated, so there is exactly one place that decides what gets written.
     *
     * @param entity the newly created entity, or {@code null} if creation failed
     * @param reason the reason its spawn site knows it to be
     */
    public static void stampOnSpawn(@Nullable Entity entity, @NotNull SpawnReason reason) {
        if (entity == null) {
            return;
        }
        stampOnSpawn(entity.getWorld(), reason, entity);
    }

    /**
     * Carries a mob's origin onto the mob it converts into. Called from
     * {@code MobConversionOriginMixin}.
     *
     * <p>Without this, a zombie spawner feeding a water column produces drowned that count — which is
     * precisely how drowned farms are built, and it would have been the largest hole left in the gate.
     * {@code convertTo} builds the replacement through {@code EntityType.create(world,
     * SpawnReason.CONVERSION)}, and {@code CONVERSION} maps to {@link MobOrigin#NATURAL}, so
     * {@link #stampOnSpawn} deliberately leaves the new mob unmarked and this runs afterwards to say
     * what it inherited.
     *
     * @param from the mob being converted away
     * @param to   the mob it became, or {@code null} if the conversion failed
     */
    public static void carryThroughConversion(@NotNull Entity from, @Nullable Entity to) {
        if (to == null) {
            return;
        }
        final MobOrigin origin = of(from);
        if (origin.countsTowardMastery()) {
            return;
        }
        to.setAttached(McMMOAttachments.MOB_ORIGIN, origin.storageKey());
    }

    /**
     * Maps one of vanilla's spawn reasons onto mcMMO's gate.
     *
     * <h2>⚠️ There is deliberately no {@code default} arm</h2>
     * A switch expression over an enum with no default must be exhaustive, so a Minecraft version that
     * adds a {@code SpawnReason} <b>fails the compile</b> instead of falling through to "counts". That
     * matters more here than anywhere else in the mod: every previous silent failure in this port has
     * been a table or a list that went stale without anything noticing, and the failure direction here
     * is an exploit rather than a shortfall. {@code MobOriginsTest} additionally walks
     * {@code SpawnReason.values()} at runtime, which catches the case where the mod is run against a
     * newer Minecraft than it was built against.
     *
     * <h2>What is deliberately left counting</h2>
     * Raids ({@code EVENT}), patrols ({@code PATROL}), an evoker's vexes ({@code MOB_SUMMONED}) and a
     * zombie's reinforcements ({@code REINFORCEMENT}) all count. Farming them is possible, but a
     * defended village raid is also about the most legitimate combat in the game, and excluding it to
     * pre-empt a farm would take more from honest play than it saves. These are the §G watch items if
     * mastery ever moves faster than the play-test rows expect; the rolling per-mob-per-hour cap D-HU1
     * holds in reserve is the additive backstop for them, not a re-mapping of this switch.
     *
     * <p>{@code JOCKEY} counts too, and that is a known small leak rather than a judgement: the
     * skeleton riding a spawner-spawned spider arrives with {@code JOCKEY}, not {@code SPAWNER}, so it
     * escapes the gate its mount does not.
     */
    /**
     * Logs the first mark of the session at INFO, so a play-test can tell "the gate refused this mob"
     * apart from "the injector never bound". See {@link #LOGGED_FIRST_MARK}.
     */
    private static void announceFirstMark(@NotNull MobOrigin origin, @NotNull SpawnReason reason) {
        if (LOGGED_FIRST_MARK.compareAndSet(false, true)) {
            LOGGER.info("Hunter: mob-origin gate is live — first mob marked {} (SpawnReason.{}). "
                            + "Mobs from this origin will not advance mob mastery.",
                    origin, reason);
        }
    }

    public static @NotNull MobOrigin classify(@NotNull SpawnReason reason) {
        return switch (reason) {
            // The gate's whole purpose. isAnySpawner() covers both, but they are spelled out so the
            // mapping stays readable next to the rest.
            case SPAWNER, TRIAL_SPAWNER -> MobOrigin.SPAWNER;

            // Every createChild in the game, plus shulker self-duplication.
            case BREEDING -> MobOrigin.BRED;

            // Player placement. COMMAND and DISPENSER are not in legacy's flag set and are added
            // here: /summon and a dispenser firing a spawn egg are the same cheese as using the egg
            // by hand, and the ruling that put spawn eggs in this bucket was about closing exactly
            // that. BUCKET is NOT here — releasing an axolotl you caught is not free mob generation.
            case SPAWN_EGG, COMMAND, DISPENSER -> MobOrigin.PLAYER_PLACED;

            // Structure generation, and — the reason this arm exists at all — NetherPortalBlock,
            // which spawns portal zombified piglins with STRUCTURE. That is the modern spelling of
            // legacy's NETHER_PORTAL_MOB; nothing in 1.21.11 is named after a portal.
            case STRUCTURE -> MobOrigin.STRUCTURE;

            // Everything below counts.
            //
            // CONVERSION counts here and is then overwritten by carryThroughConversion, which is what
            // stops a zombie-spawner drowned farm from laundering its origin.
            case NATURAL, CHUNK_GENERATION, MOB_SUMMONED, JOCKEY, EVENT, CONVERSION, REINFORCEMENT,
                    TRIGGERED, BUCKET, PATROL -> MobOrigin.NATURAL;
        };
    }
}
