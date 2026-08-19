package com.gmail.nossr50.platform;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.datatypes.mobs.MobOrigin;
import com.gmail.nossr50.fabric.McMMOAttachments;
import com.gmail.nossr50.util.McTestRegistries;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The Minecraft-typed half of Hunter's D-HU1 anti-farm gate: the {@link SpawnReason} mapping and the
 * attachment read/write rules.
 *
 * <p>Whether a spawner mob really fails to advance mastery in a live world is a {@code PLAYTEST_G}
 * row, not something this suite can reach. What it can pin is every property whose loss would make
 * that row fail <em>silently</em>, and the one that matters most is the third test below.
 */
class MobOriginsTest {

    @BeforeAll
    static void bootstrapRegistries() {
        McTestRegistries.bootstrap();
        McMMOAttachments.register();
    }

    /** A server-side world — the only kind the gate acts on. */
    private static World serverWorld() {
        final World world = mock(World.class);
        when(world.isClient()).thenReturn(false);
        return world;
    }

    @Test
    void everySpawnReasonMapsToAnOrigin() {
        // ⚠️ The compile-time guard is the real one: MobOrigins.classify is a switch expression with
        // no default arm, so a Minecraft version that adds a SpawnReason fails the build. This is the
        // runtime companion for the case that guard cannot see — running against a newer Minecraft
        // than the mod was compiled against, where an unmatched constant throws MatchException on the
        // very first mob spawn rather than at startup.
        for (SpawnReason reason : SpawnReason.values()) {
            final MobOrigin origin = MobOrigins.classify(reason);
            assertNotNull(origin, reason + " has no MobOrigin mapping");
        }
    }

    @Test
    void theDisqualifyingReasonsAreTheRuledOnes() {
        // The truth table, named reason by reason. Each line is a farm.
        assertSame(MobOrigin.SPAWNER, MobOrigins.classify(SpawnReason.SPAWNER));
        assertSame(MobOrigin.SPAWNER, MobOrigins.classify(SpawnReason.TRIAL_SPAWNER));
        assertSame(MobOrigin.BRED, MobOrigins.classify(SpawnReason.BREEDING));
        assertSame(MobOrigin.PLAYER_PLACED, MobOrigins.classify(SpawnReason.SPAWN_EGG));
        assertSame(MobOrigin.PLAYER_PLACED, MobOrigins.classify(SpawnReason.COMMAND));
        assertSame(MobOrigin.PLAYER_PLACED, MobOrigins.classify(SpawnReason.DISPENSER));

        // ⚠️ A portal-spawned zombified piglin is STRUCTURE, not a travel reason. Legacy's flag was
        // called NETHER_PORTAL_MOB and NetherPortalBlock's randomTick calls
        // EntityType.spawn(world, pos, SpawnReason.STRUCTURE), bytecode verified.
        //
        // ⚠️ The companion assertion — that re-creating an EXISTING mob on the far side of a portal
        // stays NATURAL — cannot be written on this version: it has no such SpawnReason constant, so
        // there is no value to pass. It is asserted on the bands that do. Do not read its absence
        // here as the case being unhandled; read it as the case being unreachable.
        assertSame(MobOrigin.STRUCTURE, MobOrigins.classify(SpawnReason.STRUCTURE));
    }

    @Test
    void aQualifyingOriginIsNeverWritten() {
        // ⚠️⚠️ THE test in this file. EntityType#create(World, SpawnReason) — where the stamp happens
        // — is also the path taken by the reasons that RE-INTRODUCE a mob rather than create one:
        // chunk load, and travel through a portal. Those arrive carrying mobs that already own a
        // marker from a previous session or from the other side. Writing "NATURAL" for them would
        // erase it, and the symptom would be that a spawner farm quietly starts counting again after
        // a world reload — indistinguishable from the gate never having worked.
        //
        // ⚠️ Which constants those are is version-specific and this version names neither of them,
        // so they cannot appear in the array below. The invariant under test does not change, and
        // NATURAL below still carries the mutation check.
        //
        // Mutation check: make stampOnSpawn write unconditionally and this test, and only this test,
        // goes red.
        final LivingEntity zombie = mock(ZombieEntity.class);
        for (SpawnReason qualifying : new SpawnReason[] {
                SpawnReason.NATURAL, SpawnReason.CHUNK_GENERATION, SpawnReason.CONVERSION,
                SpawnReason.EVENT }) {
            MobOrigins.stampOnSpawn(serverWorld(), qualifying, zombie);
        }
        verify(zombie, never()).setAttached(eq(McMMOAttachments.MOB_ORIGIN), any());
    }

    @Test
    void aDisqualifyingOriginIsStampedOnce() {
        final LivingEntity zombie = mock(ZombieEntity.class);
        MobOrigins.stampOnSpawn(serverWorld(), SpawnReason.SPAWNER, zombie);
        verify(zombie).setAttached(McMMOAttachments.MOB_ORIGIN, MobOrigin.SPAWNER.storageKey());
    }

    @Test
    void theClientSideAndNullCasesAreIgnored() {
        // EntityType#create runs on both sides in single-player, and returns null for a type behind a
        // disabled feature flag. Neither should reach the attachment: a client-side write is dead
        // weight on a copy of the entity that is never saved, and a null dereference here would crash
        // every spawn of a feature-flagged mob.
        final World clientWorld = mock(World.class);
        when(clientWorld.isClient()).thenReturn(true);
        final LivingEntity zombie = mock(ZombieEntity.class);

        MobOrigins.stampOnSpawn(clientWorld, SpawnReason.SPAWNER, zombie);
        verify(zombie, never()).setAttached(eq(McMMOAttachments.MOB_ORIGIN), any());

        MobOrigins.stampOnSpawn(serverWorld(), SpawnReason.SPAWNER, null);
    }

    @Test
    void anUnmarkedMobCountsAndAMarkedOneDoesNot() {
        final LivingEntity unmarked = mock(ZombieEntity.class);
        when(unmarked.getAttached(McMMOAttachments.MOB_ORIGIN)).thenReturn(null);
        assertSame(MobOrigin.NATURAL, MobOrigins.of(unmarked));
        assertTrue(MobOrigins.countsTowardMastery(unmarked));

        final LivingEntity fromSpawner = mock(ZombieEntity.class);
        when(fromSpawner.getAttached(McMMOAttachments.MOB_ORIGIN))
                .thenReturn(MobOrigin.SPAWNER.storageKey());
        assertSame(MobOrigin.SPAWNER, MobOrigins.of(fromSpawner));
        org.junit.jupiter.api.Assertions.assertFalse(MobOrigins.countsTowardMastery(fromSpawner));
    }

    @Test
    void anUnreadableMarkerReadsAsUnknownRatherThanNatural() {
        // getType() is stubbed because it is a format argument on the warn this path logs, and an
        // unstubbed call on a strict mock is its own failure — the MetadataStore null-key lesson.
        final LivingEntity odd = mock(ZombieEntity.class);
        when(odd.getAttached(McMMOAttachments.MOB_ORIGIN)).thenReturn("PLAYER_TAMED_MOB");
        assertSame(MobOrigin.UNKNOWN, MobOrigins.of(odd),
                "a marker this build cannot parse must stay disqualified, not silently become "
                        + "NATURAL and re-open the farm that wrote it");
    }

    @Test
    void conversionCarriesADisqualifyingOriginAcross() {
        // ⚠️ The drowned-farm hole. A zombie spawner over water produces drowned through
        // EntityType.create(world, SpawnReason.CONVERSION), which qualifies — so without this the
        // farm launders its own origin one conversion later.
        final LivingEntity spawnerZombie = mock(ZombieEntity.class);
        when(spawnerZombie.getAttached(McMMOAttachments.MOB_ORIGIN))
                .thenReturn(MobOrigin.SPAWNER.storageKey());
        final LivingEntity drowned = mock(ZombieEntity.class);

        MobOrigins.carryThroughConversion(spawnerZombie, drowned);

        verify(drowned).setAttached(McMMOAttachments.MOB_ORIGIN, MobOrigin.SPAWNER.storageKey());
    }

    @Test
    void conversionOfACleanMobWritesNothing() {
        // The converse, and it is not symmetry for its own sake: writing NATURAL onto the product of
        // a legitimate conversion would put a marker on a mob that has no business carrying one, and
        // the next reader of that marker is a chunk load.
        final LivingEntity naturalZombie = mock(ZombieEntity.class);
        when(naturalZombie.getAttached(McMMOAttachments.MOB_ORIGIN)).thenReturn(null);
        final LivingEntity drowned = mock(ZombieEntity.class);

        MobOrigins.carryThroughConversion(naturalZombie, drowned);

        verify(drowned, never()).setAttached(eq(McMMOAttachments.MOB_ORIGIN), any());
        // A failed conversion returns null and must not throw.
        MobOrigins.carryThroughConversion(naturalZombie, null);
    }

    @Test
    void nonLivingEntitiesAreNeverStamped() {
        // Hunter counts mob kills, so an item frame or a boat spawned from a dispenser has no reason
        // to carry a marker into the region file. ArmorStandEntity is a LivingEntity and therefore
        // does get one — harmless, and not worth a second predicate.
        final ArmorStandEntity stand = mock(ArmorStandEntity.class);
        MobOrigins.stampOnSpawn(serverWorld(), SpawnReason.DISPENSER, stand);
        verify(stand).setAttached(McMMOAttachments.MOB_ORIGIN,
                MobOrigin.PLAYER_PLACED.storageKey());
    }
}
