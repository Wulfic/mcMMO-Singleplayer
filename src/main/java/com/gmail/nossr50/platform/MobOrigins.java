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
 * <h2>The seam: {@code EntityType#create(World, SpawnReason)}</h2>
 * The plan implied {@code MobEntity#initialize(ServerWorldAccess, LocalDifficulty, SpawnReason,
 * EntityData)}, which is where both spawner logics hand a reason to a freshly built mob. <b>It is not
 * safe.</b> Of the 57 classes that override {@code initialize} in 1.21.11, exactly one does not call
 * {@code super} — {@code CaveSpiderEntity}, whose override is a bare {@code return entityData} that
 * deliberately skips {@code SpiderEntity}'s jockey and effect logic. A mixin there would therefore
 * have missed cave spiders entirely, and a mineshaft cave-spider spawner is one of the two or three
 * most-built grinders in the game. The one seam that cannot be dodged like that is the factory those
 * paths all bottom out in, verified against the merged jar:
 *
 * <ul>
 *   <li>{@code MobSpawnerLogic} and {@code TrialSpawnerLogic} →
 *       {@code EntityType.loadEntityWithPassengers(…, SpawnReason, …)} → {@code loadEntityFromData} →
 *       {@code getEntityFromData} → {@code create(World, SpawnReason)}</li>
 *   <li>{@code SpawnEggItem} → {@code spawnFromItemStack} → {@code spawn} →
 *       {@code create(ServerWorld, Consumer, BlockPos, SpawnReason, boolean, boolean)} →
 *       {@code create(World, SpawnReason)}</li>
 *   <li>{@code NetherPortalBlock} → {@code spawn(ServerWorld, BlockPos, SpawnReason)} → the same</li>
 *   <li>roughly forty {@code createChild} implementations → {@code create(World, SpawnReason)}
 *       directly, with {@code SpawnReason.BREEDING}</li>
 * </ul>
 *
 * <p>It is an instance method on {@code EntityType}, a class with no vanilla subclasses, so no mob can
 * override it. And it <b>ignores its own {@code SpawnReason} parameter</b> (the body is a feature-flag
 * check and a factory call), which makes it a safe place to read the reason: nothing else in vanilla
 * depends on that value here, so there is no behaviour to perturb.
 *
 * <h2>Never write a qualifying origin</h2>
 * {@link #stampOnSpawn} returns without touching the entity when the reason maps to
 * {@link MobOrigin#NATURAL}. That is required, not tidy — {@code SpawnReason.LOAD} and
 * {@code DIMENSION_TRAVEL} both reach this seam for mobs that already carry a marker from a previous
 * session or from the far side of a portal, and a write would erase it. See
 * {@code McMMOAttachments#MOB_ORIGIN}.
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
            // See the class doc: writing here would clobber a marker LOAD or DIMENSION_TRAVEL is
            // about to restore.
            return;
        }
        entity.setAttached(McMMOAttachments.MOB_ORIGIN, origin.storageKey());
        announceFirstMark(origin, reason);
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
            case SPAWN_ITEM_USE, COMMAND, DISPENSER -> MobOrigin.PLAYER_PLACED;

            // Structure generation, and — the reason this arm exists at all — NetherPortalBlock,
            // which spawns portal zombified piglins with STRUCTURE. That is the modern spelling of
            // legacy's NETHER_PORTAL_MOB; nothing in 1.21.11 is named after a portal.
            case STRUCTURE -> MobOrigin.STRUCTURE;

            // Everything below counts.
            //
            // LOAD and DIMENSION_TRAVEL are the two that must count and must not be written: they
            // reach this seam carrying mobs that already own a marker. See stampOnSpawn.
            //
            // CONVERSION counts here and is then overwritten by carryThroughConversion, which is what
            // stops a zombie-spawner drowned farm from laundering its origin.
            case NATURAL, CHUNK_GENERATION, MOB_SUMMONED, JOCKEY, EVENT, CONVERSION, REINFORCEMENT,
                    TRIGGERED, BUCKET, PATROL, LOAD, DIMENSION_TRAVEL -> MobOrigin.NATURAL;
        };
    }
}
