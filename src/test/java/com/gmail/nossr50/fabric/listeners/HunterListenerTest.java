package com.gmail.nossr50.fabric.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.datatypes.mobs.MobOrigin;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.player.PlayerProfile;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOAttachments;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.skills.hunter.HunterManager;
import com.gmail.nossr50.util.McTestRegistries;
import com.gmail.nossr50.util.TrackedSummon;
import com.gmail.nossr50.util.player.UserManager;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * Hunter stage 3's trigger layer: the half {@code HunterManagerTest} cannot reach.
 *
 * <p>That test pins the threshold arithmetic. What is unproven without this file is everything that
 * can go wrong silently in a live world — that a kill reaches the counter at all, that the counter is
 * keyed by the <b>victim's own namespaced registry id</b>, and above all that each of the four gates
 * is actually consulted. A gate proved as a predicate and never called is this port's recurring
 * failure mode, so every test here drives the real {@code AFTER_DEATH} handler.
 *
 * <p>Backed by a <b>real</b> {@link PlayerProfile}, like {@code HunterManagerTest} and for the same
 * reason: the counters are the skill's net-new persistence shape, and a mocked profile would assert
 * that the listener delegates while proving nothing about what it delegates.
 */
class HunterListenerTest {

    @BeforeAll
    static void bootstrapRegistries() {
        McTestRegistries.bootstrap();
        // The gate reads a persistent data attachment, which resolves by identifier — unregistered,
        // every lookup below would answer null and every "this mob is farmed" test would pass for
        // the wrong reason.
        McMMOAttachments.register();
    }

    private static final String ZOMBIE_ID = "minecraft:zombie";
    private static final String CREEPER_ID = "minecraft:creeper";

    private UUID playerId;
    private PlayerProfile profile;
    private McMMOPlayer mmoPlayer;
    private PlatformPlayer platformPlayer;
    private ServerPlayerEntity killer;

    @BeforeEach
    void setUp() {
        playerId = UUID.randomUUID();

        // Real config objects are files on disk; the listener only asks these two questions.
        final GeneralConfig generalConfig = mock(GeneralConfig.class);
        lenient().when(generalConfig.getPVEEnabled(PrimarySkillType.HUNTER)).thenReturn(true);
        lenient().when(generalConfig.getPVPEnabled(PrimarySkillType.HUNTER)).thenReturn(true);
        McMMOMod.setGeneralConfig(generalConfig);

        // Route the threshold notification to chat rather than the action bar, so a single
        // sendMessage verification covers it. (Production ships SubSkillUnlocked as action bar plus a
        // chat copy, which would fire both.)
        final AdvancedConfig advancedConfig = mock(AdvancedConfig.class);
        lenient().when(advancedConfig.doesNotificationUseActionBar(any())).thenReturn(false);
        McMMOMod.setAdvancedConfig(advancedConfig);

        profile = new PlayerProfile("Steve", playerId, 0);
        platformPlayer = mock(PlatformPlayer.class);
        lenient().when(platformPlayer.getUniqueId()).thenReturn(playerId);
        mmoPlayer = mock(McMMOPlayer.class);
        lenient().when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        lenient().when(mmoPlayer.getProfile()).thenReturn(profile);
        lenient().when(mmoPlayer.useChatNotifications()).thenReturn(true);
        lenient().when(mmoPlayer.getHunterManager()).thenReturn(new HunterManager(mmoPlayer));
        UserManager.track(mmoPlayer);

        killer = mock(ServerPlayerEntity.class);
        lenient().when(killer.getUuid()).thenReturn(playerId);
    }

    @AfterEach
    void tearDown() {
        UserManager.cleanupPlayer(mmoPlayer);
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setAdvancedConfig(null);
        // Process-wide static on a JVM JUnit reuses across classes: without this the first test to
        // run decides whether any later one logs, and `theFirstCountedKillOfASessionIsLogged` would
        // depend on execution order.
        HunterListener.resetFirstKillLogForTesting();
    }

    // --- fixtures -------------------------------------------------------------------------------

    private ZombieEntity zombie() {
        final ZombieEntity zombie = mock(ZombieEntity.class);
        Mockito.doReturn(EntityType.ZOMBIE).when(zombie).getType();
        lenient().when(zombie.getUuid()).thenReturn(UUID.randomUUID());
        return zombie;
    }

    private CreeperEntity creeper() {
        final CreeperEntity creeper = mock(CreeperEntity.class);
        Mockito.doReturn(EntityType.CREEPER).when(creeper).getType();
        lenient().when(creeper.getUuid()).thenReturn(UUID.randomUUID());
        return creeper;
    }

    /** A damage source attributed to {@code attacker} — null for "nothing killed it but the world". */
    private DamageSource killedBy(Entity attacker) {
        final DamageSource source = mock(DamageSource.class);
        lenient().when(source.getAttacker()).thenReturn(attacker);
        return source;
    }

    /** Stamp a disqualifying spawn origin on a mob, exactly as stage 1's mixin does. */
    private void markOrigin(LivingEntity victim, MobOrigin origin) {
        when(victim.getAttached(McMMOAttachments.MOB_ORIGIN)).thenReturn(origin.storageKey());
    }

    private int killsOf(String mobId) {
        return profile.getMobKills(mobId);
    }

    // --- the counter itself ---------------------------------------------------------------------

    @Test
    void aPlayerKillIsCountedAgainstTheVictimsNamespacedRegistryId() {
        // ⚠️ The namespace is load-bearing and easy to get wrong: Husbandry's tables key on
        // getId(...).getPath() ("cow"), and Hunter deliberately does NOT — the profile's kills map is
        // an open key space that has to survive two mods shipping a mob of the same name. A silent
        // switch to getPath() would pass every "the counter moved" assertion.
        HunterListener.onDeath(zombie(), killedBy(killer));

        assertEquals(1, killsOf(ZOMBIE_ID));
        assertEquals(1, profile.getAllMobKills().size(),
                "the kill must be filed under exactly one key");
        assertTrue(profile.getAllMobKills().containsKey(ZOMBIE_ID),
                () -> "expected the namespaced id; got " + profile.getAllMobKills().keySet());
    }

    @Test
    void eachMobTypeGetsItsOwnCounter() {
        // The whole horizontal axis is "per mob, never pooled". A shared counter would make the skill
        // a second XP bar and nothing would say so.
        HunterListener.onDeath(zombie(), killedBy(killer));
        HunterListener.onDeath(zombie(), killedBy(killer));
        HunterListener.onDeath(creeper(), killedBy(killer));

        assertEquals(2, killsOf(ZOMBIE_ID));
        assertEquals(1, killsOf(CREEPER_ID));
    }

    // --- gate 1: player attribution -------------------------------------------------------------

    @Test
    void aMobThatDiedWithNoAttackerCountsNothing() {
        // Fall, lava, suffocation, cactus, fire. This single gate is what excludes the majority of
        // real mob farms, which is why it runs first.
        HunterListener.onDeath(zombie(), killedBy(null));

        assertEquals(0, killsOf(ZOMBIE_ID));
    }

    @Test
    void aKillByThePlayersWolfCountsNothing() {
        // The attacker is the wolf, not its owner. Taming's Sharpened Claws already owns that hit;
        // paying Hunter for it too would make a wolf pack the fastest mastery farm in the game.
        HunterListener.onDeath(zombie(), killedBy(mock(WolfEntity.class)));

        assertEquals(0, killsOf(ZOMBIE_ID));
    }

    // --- gate 2: the PVE / PVP switches ---------------------------------------------------------

    @Test
    void theEnabledForPveSwitchStopsTheCounter() {
        final GeneralConfig pveOff = mock(GeneralConfig.class);
        when(pveOff.getPVEEnabled(PrimarySkillType.HUNTER)).thenReturn(false);
        McMMOMod.setGeneralConfig(pveOff);

        HunterListener.onDeath(zombie(), killedBy(killer));

        assertEquals(0, killsOf(ZOMBIE_ID),
                "Enabled_For_PVE: false must stop mobs feeding the skill, not merely mute a bonus");
    }

    // --- gate 3: summons and manufactured golems ------------------------------------------------

    @Test
    void aCallOfTheWildSummonCountsNothing() {
        final ZombieEntity summon = zombie();
        // Read off the mock BEFORE opening a stubbing on another one: resolving summon.getUuid()
        // inside when(...).thenReturn(...) is a mock call made mid-stubbing, which Mockito rejects
        // with UnfinishedStubbingException pointing at the wrong line.
        final UUID summonId = summon.getUuid();
        final TrackedSummon tracked = mock(TrackedSummon.class);
        when(tracked.getEntityId()).thenReturn(summonId);
        McMMOMod.getTransientEntityTracker().addSummon(playerId, tracked);
        try {
            HunterListener.onDeath(summon, killedBy(killer));

            assertEquals(0, killsOf(ZOMBIE_ID));
        } finally {
            // The tracker is a process-wide static; a leaked summon uuid would leak into every later
            // test class in the same fork.
            McMMOMod.getTransientEntityTracker().evictByEntityId(summonId);
        }
    }

    @Test
    void aPlayerBuiltIronGolemCountsNothingButAVillageOneDoes() {
        // Asserted on BOTH sides deliberately: a gate written as `instanceof IronGolemEntity` with
        // the isPlayerCreated() half dropped would pass the first assertion on its own.
        final IronGolemEntity built = mock(IronGolemEntity.class);
        Mockito.doReturn(EntityType.IRON_GOLEM).when(built).getType();
        lenient().when(built.getUuid()).thenReturn(UUID.randomUUID());
        when(built.isPlayerCreated()).thenReturn(true);

        HunterListener.onDeath(built, killedBy(killer));
        assertEquals(0, killsOf("minecraft:iron_golem"));

        final IronGolemEntity villageGolem = mock(IronGolemEntity.class);
        Mockito.doReturn(EntityType.IRON_GOLEM).when(villageGolem).getType();
        lenient().when(villageGolem.getUuid()).thenReturn(UUID.randomUUID());
        when(villageGolem.isPlayerCreated()).thenReturn(false);

        HunterListener.onDeath(villageGolem, killedBy(killer));
        assertEquals(1, killsOf("minecraft:iron_golem"));
    }

    // --- gate 4: stage 1's spawn origin ---------------------------------------------------------

    @Test
    void everyDisqualifyingSpawnOriginStopsTheCounter() {
        // ⚠️ THE test in this file. Stage 1's marker had nothing to refuse until stage 3 existed, so
        // this is the first place the anti-farm gate is observable at all — and 10,000 kills is under
        // four hours in front of a spawner farm versus roughly 28 hours by hand.
        for (MobOrigin origin : MobOrigin.values()) {
            if (origin.countsTowardMastery()) {
                continue;
            }
            final ZombieEntity farmed = zombie();
            markOrigin(farmed, origin);

            HunterListener.onDeath(farmed, killedBy(killer));

            assertEquals(0, killsOf(ZOMBIE_ID), origin + " must not advance mastery");
        }
    }

    @Test
    void anUnmarkedMobCounts() {
        // The companion to the test above, and the reason it is not vacuous: an unmarked mob is
        // MobOrigin.NATURAL, which is every mob the world spawned by its own rules.
        final ZombieEntity wild = zombie();
        when(wild.getAttached(McMMOAttachments.MOB_ORIGIN)).thenReturn(null);

        HunterListener.onDeath(wild, killedBy(killer));

        assertEquals(1, killsOf(ZOMBIE_ID));
    }

    // --- the threshold notification -------------------------------------------------------------

    @Test
    void crossingTheFirstThresholdNotifiesThePlayer() {
        seedKills(ZOMBIE_ID, HunterManager.MASTERY_THRESHOLDS[0] - 1);

        HunterListener.onDeath(zombie(), killedBy(killer));

        final ArgumentCaptor<Text> sent = ArgumentCaptor.forClass(Text.class);
        verify(platformPlayer).sendMessage(sent.capture());
        final String message = sent.getValue().getString();

        // ⚠️ Asserted against the rendered wording ("Mastery 1"), not against a bare "1". The three
        // substitutions are a mob name, a tier and a kill count, and a bare digit search is satisfied
        // by the wrong one of them — at tier 2 the count is 2,500, so contains("2") would pass even
        // with the arguments swapped. Pinning the wording is the price of a non-vacuous assertion,
        // and the arg ORDER is exactly what has no other guard.
        assertTrue(message.contains("Mastery 1"), () -> "expected 'Mastery 1' in: " + message);
        assertTrue(message.contains("500"), () -> "expected the kill count in: " + message);
        assertTrue(message.contains(EntityType.ZOMBIE.getName().getString()),
                () -> "expected the victim's name in: " + message);
    }

    @Test
    void theKillBeforeAThresholdIsSilent() {
        // Off-by-one on the announcement is invisible in review and would either fire the plaque a
        // kill early or swallow it entirely.
        seedKills(ZOMBIE_ID, HunterManager.MASTERY_THRESHOLDS[0] - 2);

        HunterListener.onDeath(zombie(), killedBy(killer));

        verify(platformPlayer, never()).sendMessage(any());
    }

    @Test
    void theKillAfterAThresholdIsSilentToo() {
        seedKills(ZOMBIE_ID, HunterManager.MASTERY_THRESHOLDS[0]);

        HunterListener.onDeath(zombie(), killedBy(killer));

        verify(platformPlayer, never()).sendMessage(any());
    }

    @Test
    void everyThresholdAnnouncesItsOwnTier() {
        // Walks the real table rather than hardcoding 500/2500/10000, so a rebalance moves the test
        // with the code instead of reddening it for the wrong reason.
        for (int i = 0; i < HunterManager.MASTERY_THRESHOLDS.length; i++) {
            final int expectedTier = i + 1;
            seedKills(CREEPER_ID, HunterManager.MASTERY_THRESHOLDS[i] - 1);
            Mockito.clearInvocations(platformPlayer);

            HunterListener.onDeath(creeper(), killedBy(killer));

            final ArgumentCaptor<Text> sent = ArgumentCaptor.forClass(Text.class);
            verify(platformPlayer).sendMessage(sent.capture());
            final String message = sent.getValue().getString();
            assertTrue(message.contains("Mastery " + expectedTier),
                    () -> "tier " + expectedTier + " missing from: " + message);
            assertTrue(message.contains(String.valueOf(HunterManager.MASTERY_THRESHOLDS[i])),
                    () -> "kill count missing from: " + message);
        }
    }

    @Test
    void aGatedKillNeverNotifies() {
        // The gates run before the counter, so a farmed mob cannot even announce a threshold it did
        // not move. Cheap to assert and it pins the ordering of the two halves.
        seedKills(ZOMBIE_ID, HunterManager.MASTERY_THRESHOLDS[0] - 1);
        final ZombieEntity farmed = zombie();
        markOrigin(farmed, MobOrigin.SPAWNER);

        HunterListener.onDeath(farmed, killedBy(killer));

        verify(platformPlayer, never()).sendMessage(any());
        assertEquals(HunterManager.MASTERY_THRESHOLDS[0] - 1, killsOf(ZOMBIE_ID));
    }

    /** Put the counter one below a threshold without going through the listener's gates. */
    private void seedKills(String mobId, int count) {
        while (profile.getMobKills(mobId) < count) {
            profile.incrementMobKills(mobId);
        }
    }
}
