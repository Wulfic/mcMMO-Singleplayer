package com.gmail.nossr50.fabric.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.platform.text.TextUtils;
import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.RankConfig;
import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.mobs.MobOrigin;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.player.PlayerProfile;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOAttachments;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.skills.hunter.HunterManager;
import com.gmail.nossr50.util.McTestRegistries;
import com.gmail.nossr50.util.TrackedSummon;
import com.gmail.nossr50.util.player.UserManager;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

    /** Data folder for the real bundled {@code skillranks.yml}, which Trophy Hunter's rank gate reads. */
    @TempDir
    Path rankFolder;

    private UUID playerId;
    private PlayerProfile profile;
    private McMMOPlayer mmoPlayer;
    private PlatformPlayer platformPlayer;
    private ServerPlayer killer;

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
        // Stubbed explicitly even though 0 is Mockito's default for an int: 0 happens to be this
        // getter's "no usable override" value, so relying on the default would leave the tier
        // assertions below silently dependent on that coincidence.
        lenient().when(advancedConfig.getHunterTierOverride(any())).thenReturn(0);
        McMMOMod.setAdvancedConfig(advancedConfig);
        // Left unset on purpose, so the XP figures below come from HunterManager.DEFAULT_TIER_XP --
        // which is the fallback a player hits if the config service is ever unavailable, and the one
        // direction where a defensive 0 would silently stop the whole vertical axis.
        McMMOMod.setExperienceConfig(null);

        profile = new PlayerProfile("Steve", playerId, 0);
        platformPlayer = mock(PlatformPlayer.class);
        lenient().when(platformPlayer.getUniqueId()).thenReturn(playerId);
        mmoPlayer = mock(McMMOPlayer.class);
        lenient().when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        lenient().when(mmoPlayer.getProfile()).thenReturn(profile);
        lenient().when(mmoPlayer.useChatNotifications()).thenReturn(true);
        lenient().when(mmoPlayer.getHunterManager()).thenReturn(new HunterManager(mmoPlayer));
        UserManager.track(mmoPlayer);

        killer = mock(ServerPlayer.class);
        lenient().when(killer.getUuid()).thenReturn(playerId);
    }

    @AfterEach
    void tearDown() {
        UserManager.cleanupPlayer(mmoPlayer);
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setAdvancedConfig(null);
        McMMOMod.setExperienceConfig(null);
        // Only the Trophy Hunter tests set this, but it is the same process-wide static as the rest:
        // a RankConfig left behind would give an unrelated test class a rank ladder it never asked
        // for, and the symptom would be an ordering-dependent failure somewhere else entirely.
        McMMOMod.setRankConfig(null);
        // Process-wide static on a JVM JUnit reuses across classes: without this the first test to
        // run decides whether any later one logs, and `theFirstCountedKillOfASessionIsLogged` would
        // depend on execution order.
        HunterListener.resetFirstKillLogForTesting();
    }

    // --- fixtures -------------------------------------------------------------------------------

    private Zombie zombie() {
        final Zombie zombie = mock(Zombie.class);
        Mockito.doReturn(EntityType.ZOMBIE).when(zombie).getType();
        lenient().when(zombie.getUuid()).thenReturn(UUID.randomUUID());
        return zombie;
    }

    private Creeper creeper() {
        final Creeper creeper = mock(Creeper.class);
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
        HunterListener.onDeath(zombie(), killedBy(mock(Wolf.class)));

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
        final Zombie summon = zombie();
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
        final IronGolem built = mock(IronGolem.class);
        Mockito.doReturn(EntityType.IRON_GOLEM).when(built).getType();
        lenient().when(built.getUuid()).thenReturn(UUID.randomUUID());
        when(built.isPlayerCreated()).thenReturn(true);

        HunterListener.onDeath(built, killedBy(killer));
        assertEquals(0, killsOf("minecraft:iron_golem"));

        final IronGolem villageGolem = mock(IronGolem.class);
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
            final Zombie farmed = zombie();
            markOrigin(farmed, origin);

            HunterListener.onDeath(farmed, killedBy(killer));

            assertEquals(0, killsOf(ZOMBIE_ID), origin + " must not advance mastery");
        }
    }

    @Test
    void anUnmarkedMobCounts() {
        // The companion to the test above, and the reason it is not vacuous: an unmarked mob is
        // MobOrigin.NATURAL, which is every mob the world spawned by its own rules.
        final Zombie wild = zombie();
        when(wild.getAttached(McMMOAttachments.MOB_ORIGIN)).thenReturn(null);

        HunterListener.onDeath(wild, killedBy(killer));

        assertEquals(1, killsOf(ZOMBIE_ID));
    }

    // --- the threshold notification -------------------------------------------------------------

    @Test
    void crossingTheFirstThresholdNotifiesThePlayer() {
        seedKills(ZOMBIE_ID, HunterManager.MASTERY_THRESHOLDS[0] - 1);

        HunterListener.onDeath(zombie(), killedBy(killer));

        final ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(platformPlayer).sendMessage(sent.capture());
        final String message = TextUtils.toText(sent.getValue()).getString();

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

            final ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
            verify(platformPlayer).sendMessage(sent.capture());
            final String message = TextUtils.toText(sent.getValue()).getString();
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
        final Zombie farmed = zombie();
        markOrigin(farmed, MobOrigin.SPAWNER);

        HunterListener.onDeath(farmed, killedBy(killer));

        verify(platformPlayer, never()).sendMessage(any());
        assertEquals(HunterManager.MASTERY_THRESHOLDS[0] - 1, killsOf(ZOMBIE_ID));
    }

    // --- stage 5: the XP award ------------------------------------------------------------------

    @Test
    void aCountedKillPaysHunterXpForTheVictimsTier() {
        // The vertical axis, wired here rather than in a listener of its own precisely so it cannot
        // drift from the counter. A zombie is a common hostile: tier 2, 300 XP.
        HunterListener.onDeath(zombie(), killedBy(killer));

        verify(mmoPlayer).beginXpGain(PrimarySkillType.HUNTER, 300.0F, XPGainReason.PVE,
                XPGainSource.SELF);
    }

    @Test
    void theXpPaidTracksTheVictimRatherThanBeingFlat() {
        // Two mobs of different tiers through the real handler. Without this, a hard-coded award
        // would satisfy every other test in this file -- the tier lookup could be deleted entirely.
        HunterListener.onDeath(chicken(), killedBy(killer));
        verify(mmoPlayer).beginXpGain(PrimarySkillType.HUNTER, 100.0F, XPGainReason.PVE,
                XPGainSource.SELF);

        HunterListener.onDeath(zombie(), killedBy(killer));
        verify(mmoPlayer).beginXpGain(PrimarySkillType.HUNTER, 300.0F, XPGainReason.PVE,
                XPGainSource.SELF);
    }

    @Test
    void everyGateThatStopsTheCounterAlsoStopsTheXp() {
        // ⚠️ THE stage-5 test, and the one that pins the 2026-07-30 ruling.
        //
        // The plan's D-HU1 note recommended the opposite -- strict gates for mastery, the looser
        // processCombatXP set for XP, so a spawner zombie would pay XP but not mastery. The
        // arithmetic killed it: 11,010,000 XP to max, 300 a kill, and a modest spawner farm turns
        // over a thousand mobs an hour = 37 hours, against an 80 h floor.
        //
        // Asserted across all four gates in one loop, because the failure this guards is that the
        // award drifts ABOVE or BELOW one of them during a later refactor -- and a per-gate test
        // written by hand is exactly the kind that gets added for three gates and forgotten for the
        // fourth.
        final List<Runnable> gatedKills = List.of(
                // gate 1: nothing killed it but the world.
                () -> HunterListener.onDeath(zombie(), killedBy(null)),
                // gate 1: the wolf's kill, which is Taming's.
                () -> HunterListener.onDeath(zombie(), killedBy(mock(Wolf.class))),
                // gate 3: a golem the player stacked out of blocks.
                () -> HunterListener.onDeath(snowGolem(), killedBy(killer)),
                // gate 4: stage 1's spawn-origin marker.
                () -> {
                    final Zombie farmed = zombie();
                    markOrigin(farmed, MobOrigin.SPAWNER);
                    HunterListener.onDeath(farmed, killedBy(killer));
                });

        for (Runnable gatedKill : gatedKills) {
            gatedKill.run();
        }

        verify(mmoPlayer, never()).beginXpGain(any(), anyFloat(), any(), any());
    }

    @Test
    void theEnabledForPveSwitchStopsTheXpAsWellAsTheCounter() {
        // Gate 2 needs its own test: it is the one gate driven by a config object rather than by the
        // victim, so it cannot be expressed as a fixture in the loop above.
        final GeneralConfig pveOff = mock(GeneralConfig.class);
        when(pveOff.getPVEEnabled(PrimarySkillType.HUNTER)).thenReturn(false);
        McMMOMod.setGeneralConfig(pveOff);

        HunterListener.onDeath(zombie(), killedBy(killer));

        verify(mmoPlayer, never()).beginXpGain(any(), anyFloat(), any(), any());
    }

    // --- stage 5: the golems stage 5 had to close -----------------------------------------------

    @Test
    void aConstructedSnowOrCopperGolemPaysNothingOnEitherAxis() {
        // ⚠️ A hole stage 5 CREATED and therefore had to close in the same commit.
        //
        // CarvedPumpkinBlock builds the snow, iron and copper golems alike with
        // SpawnReason.TRIGGERED, which stage 1 correctly maps to NATURAL -- TRIGGERED is also how a
        // warden leaves a shrieker, how silverfish leave infested stone and how a slime splits, so
        // that mapping must NOT change. Legacy's gate only ever knew about the iron golem.
        //
        // Before stage 5 the leak was worth nothing (bonus damage against a mob you manufacture).
        // Paying skill XP is what makes a dispenser loop an exploit.
        //
        // The snow golem half runs on every supported Minecraft version, and since the exclusion is
        // keyed by registry id both golems now take the identical code path -- so this half is
        // permanent proof that the id-keyed rule works, and the coverage does not lapse on a band
        // that has no copper golem.
        HunterListener.onDeath(snowGolem(), killedBy(killer));
        assertEquals(0, killsOf("minecraft:snow_golem"));

        // The copper golem is resolved through the registry rather than named as
        // EntityType.COPPER_GOLEM / CopperGolemEntity. It does not exist on every supported version,
        // and there naming either one fails the BUILD rather than the assertion -- the whole test
        // tree stops compiling, exactly as Items.IRON_SPEAR did on mc/1.21.10.
        final LivingEntity copperGolem = copperGolem();
        if (copperGolem == null) {
            // Absence is ASSERTED, never skipped. The one way this observation lies is a bootstrap
            // that never populated anything, in which case "no copper golem" is not a fact about
            // Minecraft at all -- so that is the thing ruled out.
            assertTrue(McTestRegistries.entityTypeRegistryIsPopulated(),
                    "copper_golem does not resolve AND the entity registry looks empty — that is a "
                            + "broken bootstrap, not a Minecraft version without copper golems");
        } else {
            HunterListener.onDeath(copperGolem, killedBy(killer));
            assertEquals(0, killsOf("minecraft:copper_golem"));
        }

        verify(mmoPlayer, never()).beginXpGain(any(), anyFloat(), any(), any());
    }

    @Test
    void theGolemExclusionDoesNotSweepUpOrdinaryPassiveCreatures() {
        // Asserted OFF the reference point. `victim instanceof GolemEntity` would look like a tidier
        // version of the same rule and would silently take the village iron golem with it -- and a
        // check written against the wrong supertype would take far more.
        HunterListener.onDeath(chicken(), killedBy(killer));

        assertEquals(1, killsOf("minecraft:chicken"));
    }

    private Chicken chicken() {
        final Chicken chicken = mock(Chicken.class);
        Mockito.doReturn(EntityType.CHICKEN).when(chicken).getType();
        lenient().when(chicken.getUuid()).thenReturn(UUID.randomUUID());
        return chicken;
    }

    private SnowGolem snowGolem() {
        final SnowGolem golem = mock(SnowGolem.class);
        Mockito.doReturn(EntityType.SNOW_GOLEM).when(golem).getType();
        lenient().when(golem.getUuid()).thenReturn(UUID.randomUUID());
        return golem;
    }

    /**
     * A stand-in copper golem, or {@code null} on a Minecraft version that has none.
     *
     * <p>Mocks the plain {@link LivingEntity} rather than {@code CopperGolemEntity}, because naming
     * that class is a compile error below the version it arrives in. Nothing is lost by it: since the
     * exclusion is keyed by {@code getType()}'s registry id and no longer by {@code instanceof}, the
     * concrete Java class was never what the rule read.
     *
     * <p>⚠️ The type comes from {@code containsId}-guarded lookup, never a bare {@code get}.
     * {@code Registries.ENTITY_TYPE} defaults to {@code PIG}, so an unguarded lookup on a version
     * without copper golems would hand back a pig and this test would pass while proving that pigs
     * earn no Hunter XP — which is false. See {@code McTestRegistries#optionalVanillaEntityType}.
     */
    private LivingEntity copperGolem() {
        return McTestRegistries.optionalVanillaEntityType("copper_golem")
                .map(type -> {
                    final LivingEntity golem = mock(LivingEntity.class);
                    Mockito.doReturn(type).when(golem).getType();
                    lenient().when(golem.getUuid()).thenReturn(UUID.randomUUID());
                    return golem;
                })
                .orElse(null);
    }

    /** Put the counter one below a threshold without going through the listener's gates. */
    private void seedKills(String mobId, int count) {
        while (profile.getMobKills(mobId) < count) {
            profile.incrementMobKills(mobId);
        }
    }

    // --- stage 6: Trophy Hunter's bonus loot roll ------------------------------------------------

    /**
     * Wire the rank ladder and the chance ceiling for Trophy Hunter, then hand back a counter that
     * stands in for the mixin's re-roll.
     *
     * <p>Counting a {@link Runnable} is the whole reason {@code onLootDropped} takes the roll as a
     * parameter: the "exactly one extra roll" property D-HU6 demands is not otherwise assertable
     * without a live world and a loot table, and the failure it guards against is an item-duplication
     * bomb rather than a clean error.
     */
    private AtomicInteger trophySetUp(int hunterLevel, double chanceMax) {
        final GeneralConfig generalConfig = mock(GeneralConfig.class);
        lenient().when(generalConfig.getPVEEnabled(PrimarySkillType.HUNTER)).thenReturn(true);
        lenient().when(generalConfig.getPVPEnabled(PrimarySkillType.HUNTER)).thenReturn(true);
        // RetroMode is the shipped default, so skillranks.yml unlocks the four tiers at
        // 100 / 300 / 600 / 900. Stubbed explicitly: Mockito's false would silently switch the
        // ladder to Standard and make every level below mean something else.
        lenient().when(generalConfig.getIsRetroMode()).thenReturn(true);
        McMMOMod.setGeneralConfig(generalConfig);
        McMMOMod.setRankConfig(new RankConfig(rankFolder));

        final AdvancedConfig advanced = mock(AdvancedConfig.class);
        lenient().when(advanced.doesNotificationUseActionBar(any())).thenReturn(false);
        lenient().when(advanced.getHunterTierOverride(any())).thenReturn(0);
        lenient().when(advanced.getMaximumProbability(SubSkillType.HUNTER_TROPHY_HUNTER))
                .thenReturn(chanceMax);
        lenient().when(advanced.getMaxBonusLevel(SubSkillType.HUNTER_TROPHY_HUNTER)).thenReturn(0);
        McMMOMod.setAdvancedConfig(advanced);

        lenient().when(mmoPlayer.getSkillLevel(PrimarySkillType.HUNTER)).thenReturn(hunterLevel);
        return new AtomicInteger();
    }

    @Test
    void aQualifyingKillRollsTheLootTableExactlyOnce() {
        // ⚠️ "Exactly once", not "at least once". The whole family of mistakes this sub-skill can
        // make -- injecting on the 4-arg dropLoot instead of the 3-arg, re-invoking inside a loop --
        // produces MORE rolls rather than none, so an at-least assertion would pass against the bug.
        final AtomicInteger rolls = trophySetUp(1_000, 100.0D);

        HunterListener.onLootDropped(zombie(), killedBy(killer), rolls::incrementAndGet);

        assertEquals(1, rolls.get());
    }

    @Test
    void aKillWithNoAttackerNeverRollsABonusTrophy() {
        // ⚠️ D-HU6's second trap, and the one that makes this method different from the AFTER_DEATH
        // handler: dropLoot fires for EVERY death in the world. A creature burning in lava on the far
        // side of the map reaches here, and without gate 1 it would drop double.
        final AtomicInteger rolls = trophySetUp(1_000, 100.0D);

        HunterListener.onLootDropped(zombie(), killedBy(null), rolls::incrementAndGet);

        assertEquals(0, rolls.get());
    }

    @Test
    void aFarmedCreatureDropsItsLootOnceLikeAnyOther() {
        // ✅ Ruled: loot rides the SAME four gates as the counter and the XP. Loot is a property of
        // the kill, not of a hit, so stage 4's "origin gates the kill, not the hit" carve-out does
        // not reach it -- and doubling a spawner or bred creature's drops is precisely the farm
        // amplification stage 1 exists to prevent.
        final AtomicInteger rolls = trophySetUp(1_000, 100.0D);

        for (MobOrigin origin : MobOrigin.values()) {
            if (origin.countsTowardMastery()) {
                continue;
            }
            final Zombie farmed = zombie();
            markOrigin(farmed, origin);

            HunterListener.onLootDropped(farmed, killedBy(killer), rolls::incrementAndGet);

            assertEquals(0, rolls.get(), origin + " must not pay a bonus trophy");
        }

        // ...and the reference point, so the loop above is not passing because the roll is dead.
        HunterListener.onLootDropped(zombie(), killedBy(killer), rolls::incrementAndGet);
        assertEquals(1, rolls.get(), "a wild creature must still roll");
    }

    @Test
    void aManufacturedGolemDropsItsLootOnce() {
        // Gate 3. A snow golem is a pumpkin and two snow blocks, so a dispenser loop would otherwise
        // be an infinite snowball press once Trophy Hunter unlocked tier 1.
        final AtomicInteger rolls = trophySetUp(1_000, 100.0D);

        // The snow golem half runs on every supported Minecraft version, so this test keeps its
        // coverage on a band that has no copper golem instead of quietly becoming a no-op.
        HunterListener.onLootDropped(snowGolem(), killedBy(killer), rolls::incrementAndGet);
        assertEquals(0, rolls.get(), "a snow golem must not pay a bonus trophy");

        // ⚠️ copperGolem() is null on a version without copper golems -- it is resolved through the
        // registry precisely because naming EntityType.COPPER_GOLEM would fail the BUILD there. The
        // sibling gate test above handles that; this one did not, and dereferenced the null.
        final LivingEntity copperGolem = copperGolem();
        if (copperGolem == null) {
            // Absence is ASSERTED, never skipped: "no copper golem" is only a fact about Minecraft if
            // the registry actually populated, and a broken bootstrap looks identical otherwise.
            assertTrue(McTestRegistries.entityTypeRegistryIsPopulated(),
                    "copper_golem does not resolve AND the entity registry looks empty — that is a "
                            + "broken bootstrap, not a Minecraft version without copper golems");
        } else {
            HunterListener.onLootDropped(copperGolem, killedBy(killer), rolls::incrementAndGet);
            assertEquals(0, rolls.get(), "a copper golem must not pay a bonus trophy");
        }

        // ...and the reference point, so the zeros above cannot pass merely because the roll is dead.
        // Without this the whole test is satisfied by Trophy Hunter being switched off entirely.
        HunterListener.onLootDropped(zombie(), killedBy(killer), rolls::incrementAndGet);
        assertEquals(1, rolls.get(), "a wild creature must still roll");
    }

    @Test
    void aCreatureAboveThePlayersTrophyRankDropsItsLootOnce() {
        // Both halves in one test. Rank 1 (Hunter 100) unlocks tier 1 only, so a chicken rolls and a
        // zombie does not -- and a rank check that was missing, inverted or comparing against the
        // wrong number would fail exactly one of these two assertions.
        final AtomicInteger rolls = trophySetUp(100, 100.0D);

        HunterListener.onLootDropped(zombie(), killedBy(killer), rolls::incrementAndGet);
        assertEquals(0, rolls.get(), "a tier-2 creature is locked at Trophy Hunter rank 1");

        HunterListener.onLootDropped(chicken(), killedBy(killer), rolls::incrementAndGet);
        assertEquals(1, rolls.get(), "a tier-1 creature is unlocked at Trophy Hunter rank 1");
    }

    @Test
    void aZeroChanceCeilingRollsNothingEvenForAFullyRankedHunter() {
        // The other end of the RNG, so a roll hard-wired to true cannot pass the file.
        final AtomicInteger rolls = trophySetUp(1_000, 0.0D);

        HunterListener.onLootDropped(zombie(), killedBy(killer), rolls::incrementAndGet);

        assertEquals(0, rolls.get());
    }

    @Test
    void anUntrackedKillerRollsNothingRatherThanThrowing() {
        // A player whose profile has not finished loading -- ordinary during a join, and this runs on
        // every mob death in the world, so it must be a quiet no-op rather than an exception in the
        // middle of vanilla's loot drop.
        final AtomicInteger rolls = trophySetUp(1_000, 100.0D);
        UserManager.cleanupPlayer(mmoPlayer);

        HunterListener.onLootDropped(zombie(), killedBy(killer), rolls::incrementAndGet);

        assertEquals(0, rolls.get());
    }
}
