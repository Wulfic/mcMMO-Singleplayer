package com.gmail.nossr50.fabric.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.platform.text.TextUtils;
import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.RankConfig;
import com.gmail.nossr50.datatypes.mobs.MobOrigin;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.player.PlayerProfile;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOAttachments;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.skills.hunter.HunterManager;
import com.gmail.nossr50.skills.stealth.StealthManager;
import com.gmail.nossr50.util.McTestRegistries;
import com.gmail.nossr50.util.player.UserManager;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * Hunter stage 4: Mob Mastery's bonus damage where it is actually spent — the half
 * {@code HunterManagerTest} cannot reach.
 *
 * <p>That test pins the tier ladder against a kill count it is simply handed. What is unproven
 * without this file is everything that can go wrong silently in a live world: that the bonus is
 * looked up under <b>the same key the counter banks it under</b> (a drift there is total and
 * completely silent — counters climb, damage never changes), that it lands <b>after</b> Assassin
 * rather than inside its multiplier, that melee scales with the attack-cooldown charge and ranged
 * does not, and that the arms which are supposed to pay nothing pay nothing.
 *
 * <p>Backed by a <b>real</b> {@link PlayerProfile} and a real {@link HunterManager}, like
 * {@code HunterListenerTest} and for the same reason — a mocked manager would let the key-drift bug
 * this file exists to catch sail straight through.
 */
class EntityDamageListenerHunterTest {

    @BeforeAll
    static void bootstrapRegistries() {
        McTestRegistries.bootstrap();
        // Needed only by the end-to-end test below, which banks its kills through the real
        // AFTER_DEATH handler — and that reads the stage-1 spawn-origin attachment.
        McMMOAttachments.register();
    }

    private static final float EPSILON = 1.0E-4F;

    private static final String ZOMBIE_ID = "minecraft:zombie";

    /** Kills for the top mastery tier, and the +3.0 it is worth. Read off the real table. */
    private static final int TOP_TIER_KILLS =
            HunterManager.MASTERY_THRESHOLDS[HunterManager.MASTERY_THRESHOLDS.length - 1];
    private static final double TOP_TIER_BONUS =
            HunterManager.MASTERY_DAMAGE_BONUS[HunterManager.MASTERY_DAMAGE_BONUS.length - 1];

    private UUID playerId;
    private PlayerProfile profile;
    private McMMOPlayer mmoPlayer;
    private ServerPlayer attacker;
    private AdvancedConfig advancedConfig;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        playerId = UUID.randomUUID();

        final GeneralConfig generalConfig = mock(GeneralConfig.class);
        lenient().when(generalConfig.getPVEEnabled(PrimarySkillType.HUNTER)).thenReturn(true);
        lenient().when(generalConfig.getPVPEnabled(PrimarySkillType.HUNTER)).thenReturn(true);
        McMMOMod.setGeneralConfig(generalConfig);
        // Stage 7's Quarry Sense and Trophy Hunter both read the REAL bundled skillranks.yml through
        // RankUtils. ⚠️ The general-config mock answers false for getIsRetroMode(), so the ladders
        // resolve in STANDARD mode here — Quarry Sense at 1, Trophy Hunter at 10/30/60/90.
        McMMOMod.setRankConfig(new RankConfig(dataFolder));

        advancedConfig = mock(AdvancedConfig.class);
        // ⚠️ The SHIPPED default, not Mockito's zero. This is a multiplier: left unstubbed it would
        // read 0.0 and silently delete the entire ranged half of the sub-skill, so every ranged test
        // below would pass against a code path no player ever runs.
        lenient().when(advancedConfig.getHunterMasteryRangedDamageMultiplier()).thenReturn(1.0D);
        lenient().when(advancedConfig.doesNotificationUseActionBar(any())).thenReturn(false);
        McMMOMod.setAdvancedConfig(advancedConfig);

        profile = new PlayerProfile("Steve", playerId, 0);
        final PlatformPlayer platformPlayer = mock(PlatformPlayer.class);
        lenient().when(platformPlayer.getUniqueId()).thenReturn(playerId);
        mmoPlayer = mock(McMMOPlayer.class);
        lenient().when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        lenient().when(mmoPlayer.getProfile()).thenReturn(profile);
        lenient().when(mmoPlayer.useChatNotifications()).thenReturn(true);
        lenient().when(mmoPlayer.getHunterManager()).thenReturn(new HunterManager(mmoPlayer));
        // A fully charged swing unless a test says otherwise.
        lenient().when(mmoPlayer.getAttackStrength()).thenReturn(1.0F);
        // A maxed Hunter unless a test says otherwise, so the stage-7 rank gates are open. Nothing
        // in stages 4-6 reads the level, so this changes no existing test.
        lenient().when(mmoPlayer.getSkillLevel(PrimarySkillType.HUNTER)).thenReturn(1_000);
        UserManager.track(mmoPlayer);

        attacker = player();
    }

    @AfterEach
    void tearDown() {
        UserManager.cleanupPlayer(mmoPlayer);
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setAdvancedConfig(null);
        McMMOMod.setRankConfig(null);
        EntityDamageListener.clear();
        HunterListener.resetFirstKillLogForTesting();
    }

    // --- fixtures -------------------------------------------------------------------------------

    /**
     * The attacking player: neither sprinting nor sneaking, holding something no weapon skill claims.
     *
     * <p>The dirt matters for the ordering test, which drives the whole dispatch: it classifies as
     * {@code MeleeWeapon.OTHER}, so the weapon arm bows out and the arithmetic under test is not
     * buried under a Swords bonus.
     */
    private ServerPlayer player() {
        final MinecraftServer server = mock(MinecraftServer.class);
        lenient().when(server.getTicks()).thenReturn(10_000);
        final ServerLevel world = mock(ServerLevel.class);
        lenient().when(world.getServer()).thenReturn(server);

        final ServerPlayer player = mock(ServerPlayer.class);
        lenient().when(player.getUuid()).thenReturn(playerId);
        lenient().when(player.getEntityWorld()).thenReturn(world);
        lenient().when(player.getMainHandStack()).thenReturn(new ItemStack(Items.DIRT));
        lenient().when(player.isSprinting()).thenReturn(false);
        lenient().when(player.isSneaking()).thenReturn(false);
        return player;
    }

    private static Zombie zombie() {
        final Zombie zombie = mock(Zombie.class);
        Mockito.doReturn(EntityType.ZOMBIE).when(zombie).getType();
        lenient().when(zombie.getUuid()).thenReturn(UUID.randomUUID());
        return zombie;
    }

    /** A direct melee swing: the attacker is both the responsible and the direct damager. */
    private DamageSource melee() {
        final DamageSource source = mock(DamageSource.class);
        lenient().when(source.getAttacker()).thenReturn(attacker);
        lenient().when(source.getSource()).thenReturn(attacker);
        lenient().when(source.isOf(any())).thenReturn(false);
        lenient().when(source.isIn(any())).thenReturn(false);
        return source;
    }

    /** An arrow loosed by {@code owner}, credited to {@code responsible}. */
    private DamageSource arrow(Entity responsible, Entity owner) {
        final Arrow projectile = mock(Arrow.class);
        lenient().when(projectile.getOwner()).thenReturn(owner);

        final DamageSource source = mock(DamageSource.class);
        lenient().when(source.getAttacker()).thenReturn(responsible);
        lenient().when(source.getSource()).thenReturn(projectile);
        lenient().when(source.isOf(any())).thenReturn(false);
        lenient().when(source.isIn(any())).thenReturn(false);
        return source;
    }

    /** Bank kills straight onto the profile, bypassing the listener's gates. */
    private void seedKills(String mobId, int count) {
        while (profile.getMobKills(mobId) < count) {
            profile.incrementMobKills(mobId);
        }
    }

    // --- the payload ----------------------------------------------------------------------------

    @Test
    void aMasteredCreatureTakesTheFlatBonus() {
        seedKills(ZOMBIE_ID, TOP_TIER_KILLS);

        assertEquals(10F + (float) TOP_TIER_BONUS,
                EntityDamageListener.applyHunterMastery(zombie(), melee(), 10F), EPSILON);
    }

    @Test
    void anUnmasteredCreatureTakesNothingExtra() {
        // One kill below the first threshold. Asserted because "the bonus applies to everything" and
        // "the bonus applies to what you have mastered" are indistinguishable once you are mastered.
        seedKills(ZOMBIE_ID, HunterManager.MASTERY_THRESHOLDS[0] - 1);

        assertEquals(10F, EntityDamageListener.applyHunterMastery(zombie(), melee(), 10F), EPSILON);
    }

    @Test
    void masteryOfOneCreatureIsWorthNothingAgainstAnother() {
        // The whole horizontal axis. A pooled counter would make Hunter a second XP bar, and every
        // assertion above would still pass.
        seedKills("minecraft:creeper", TOP_TIER_KILLS);

        assertEquals(10F, EntityDamageListener.applyHunterMastery(zombie(), melee(), 10F), EPSILON);
    }

    // --- ⚠️ the ordering, which is the point of this stage ---------------------------------------

    @Test
    void theMasteryBonusIsAddedAfterAssassinMultiplies() {
        // ⚠️ THE test of stage 4, and the only one that drives the real dispatch. Assassin multiplies
        // the whole running melee total, so the two orderings are:
        //     Hunter last  (correct): 10 x 3        + 3 = 33
        //     Hunter first (wrong)  : (10 + 3) x 3      = 39
        // Swap the two lines in onModifyAppliedDamage and only this test goes red. It is also the
        // only thing proving applyHunterMastery is CALLED at all — every other test here invokes it
        // directly, which is exactly the "gate proved, call site deleted" trap this port has hit.
        seedKills(ZOMBIE_ID, TOP_TIER_KILLS);
        when(attacker.isSneaking()).thenReturn(true);

        final StealthManager stealth = mock(StealthManager.class);
        when(stealth.assassinReady(anyBoolean(), anyLong())).thenReturn(true);
        when(stealth.getAssassinDamageMultiplier()).thenReturn(3.0D);
        when(mmoPlayer.getStealthManager()).thenReturn(stealth);

        assertEquals(10F * 3F + (float) TOP_TIER_BONUS,
                EntityDamageListener.onModifyAppliedDamage(zombie(), melee(), 10F), EPSILON);
    }

    // --- the melee / ranged asymmetry (D-HU4) ----------------------------------------------------

    @Test
    void theMeleeBonusIsScaledByTheAttackCooldownCharge() {
        // Every melee bonus in this port scales by the charge. Unscaled, spam-clicking would beat a
        // charged swing — an exploit, and off-pattern for the whole codebase.
        seedKills(ZOMBIE_ID, TOP_TIER_KILLS);
        when(mmoPlayer.getAttackStrength()).thenReturn(0.5F);

        assertEquals(10F + (float) TOP_TIER_BONUS * 0.5F,
                EntityDamageListener.applyHunterMastery(zombie(), melee(), 10F), EPSILON);
    }

    @Test
    void aPlayersOwnArrowCarriesTheFullBonusRegardlessOfTheCharge() {
        // Ranged deliberately does NOT scale: a loosed arrow has no swing to charge, and
        // attackStrength is a field stamped at melee-swing time — reading it here would make an
        // archer's bonus depend on how recently they punched something. Same asymmetry as Impale.
        seedKills(ZOMBIE_ID, TOP_TIER_KILLS);
        when(mmoPlayer.getAttackStrength()).thenReturn(0.1F);

        assertEquals(10F + (float) TOP_TIER_BONUS, EntityDamageListener.applyHunterMastery(
                zombie(), arrow(attacker, attacker), 10F), EPSILON);
    }

    @Test
    void theRangedMultiplierRetunesTheRangedHalfOnly() {
        // Proves the §G tuning knob is actually consulted, and that it cannot reach melee by accident
        // — both halves asserted, because a multiplier applied in the wrong place still moves a
        // one-sided assertion.
        seedKills(ZOMBIE_ID, TOP_TIER_KILLS);
        when(advancedConfig.getHunterMasteryRangedDamageMultiplier()).thenReturn(0.5D);

        assertEquals(10F + (float) TOP_TIER_BONUS * 0.5F, EntityDamageListener.applyHunterMastery(
                zombie(), arrow(attacker, attacker), 10F), EPSILON);
        assertEquals(10F + (float) TOP_TIER_BONUS,
                EntityDamageListener.applyHunterMastery(zombie(), melee(), 10F), EPSILON);
    }

    // --- what must pay nothing --------------------------------------------------------------------

    @Test
    void aWolfsBiteCarriesNoMasteryBonus() {
        // Ruled out explicitly in D-HU4: that is the wolf's damage, and Taming's Sharpened Claws and
        // Gore already own it. Adding Hunter would double-dip on a single bite.
        seedKills(ZOMBIE_ID, TOP_TIER_KILLS);
        final Wolf wolf = mock(Wolf.class);
        final DamageSource bite = mock(DamageSource.class);
        lenient().when(bite.getAttacker()).thenReturn(wolf);
        lenient().when(bite.getSource()).thenReturn(wolf);

        assertEquals(10F, EntityDamageListener.applyHunterMastery(zombie(), bite, 10F), EPSILON);
    }

    @Test
    void anArrowThePlayerDidNotFireCarriesNoBonus() {
        // A dispenser's arrow, or one a mod re-credited mid-flight: something is blamed for the hit
        // but the projectile is not this player's. Owner and attacker have to agree.
        seedKills(ZOMBIE_ID, TOP_TIER_KILLS);

        assertEquals(10F, EntityDamageListener.applyHunterMastery(
                zombie(), arrow(attacker, mock(Zombie.class)), 10F), EPSILON);
    }

    @Test
    void aPlayersOwnExplosionCarriesNoBonus() {
        // Attributable to the player and emphatically not a hunt. Without the melee/projectile test
        // this would be a flat bonus on every tick of a blast, a lingering cloud or a lit TNT cart.
        seedKills(ZOMBIE_ID, TOP_TIER_KILLS);
        final DamageSource blast = mock(DamageSource.class);
        lenient().when(blast.getAttacker()).thenReturn(attacker);
        lenient().when(blast.getSource()).thenReturn(mock(PrimedTnt.class));
        lenient().when(blast.isOf(any())).thenReturn(false);

        assertEquals(10F, EntityDamageListener.applyHunterMastery(zombie(), blast, 10F), EPSILON);
    }

    @Test
    void thornsIsNotASwing() {
        // Credited to the wearer, direct-sourced from them, and not a hunt. Same carve-out the weapon
        // arm, Smash and Assassin all make.
        seedKills(ZOMBIE_ID, TOP_TIER_KILLS);
        final DamageSource thorns = melee();
        when(thorns.isOf(DamageTypes.THORNS)).thenReturn(true);

        assertEquals(10F, EntityDamageListener.applyHunterMastery(zombie(), thorns, 10F), EPSILON);
    }

    @Test
    void anArmourStandIsNotAHunt() {
        seedKills("minecraft:armor_stand", TOP_TIER_KILLS);

        assertEquals(10F, EntityDamageListener.applyHunterMastery(
                mock(ArmorStand.class), melee(), 10F), EPSILON);
    }

    /**
     * The mannequin half of the same rule, and the only test that covers it at all.
     *
     * <p>It exists because the check was rewritten from {@code instanceof MannequinEntity} to a
     * registry-id comparison so that this file still compiles on a Minecraft version without
     * mannequins — and that rewrite was, until this test, changing behaviour nothing asserted. A
     * silent behaviour change to an untested exclusion is how a target dummy quietly becomes an
     * infinite XP source.
     *
     * <p>Resolved through the registry rather than named as {@code MannequinEntity}: naming the class
     * is a compile error on the versions this whole change is about, which would defeat the point.
     *
     * <p>Its converse — that the rule still lets an ordinary creature through, so an
     * {@code isTargetDummy} stuck at {@code true} could not pass this — is already carried by
     * {@link #aMasteredCreatureTakesTheFlatBonus}, which asserts a zombie's full payout through this
     * same method. Not duplicated here.
     */
    @Test
    void aMannequinIsNotAHuntEither() {
        seedKills("minecraft:mannequin", TOP_TIER_KILLS);

        final var mannequinType = McTestRegistries.optionalVanillaEntityType("mannequin");
        if (mannequinType.isEmpty()) {
            // Absence asserted, never skipped — and the one alternative explanation ruled out.
            assertTrue(McTestRegistries.entityTypeRegistryIsPopulated(),
                    "mannequin does not resolve AND the entity registry looks empty — that is a "
                            + "broken bootstrap, not a Minecraft version without mannequins");
            return;
        }
        final LivingEntity mannequin = mock(LivingEntity.class);
        Mockito.doReturn(mannequinType.get()).when(mannequin).getType();

        assertEquals(10F, EntityDamageListener.applyHunterMastery(mannequin, melee(), 10F), EPSILON,
                "a mannequin is a decoration, not quarry — mastery must not pay out against one");
    }

    @Test
    void theEnabledForPveSwitchMutesTheBonusToo() {
        // The switch has to reach both halves of the skill. Muting only the counter would leave an
        // operator who turned Hunter off still taking +3.0 from it.
        seedKills(ZOMBIE_ID, TOP_TIER_KILLS);
        final GeneralConfig pveOff = mock(GeneralConfig.class);
        when(pveOff.getPVEEnabled(PrimarySkillType.HUNTER)).thenReturn(false);
        McMMOMod.setGeneralConfig(pveOff);

        assertEquals(10F, EntityDamageListener.applyHunterMastery(zombie(), melee(), 10F), EPSILON);
    }

    // --- the ruling: origin gates the KILL, not the HIT ------------------------------------------

    @Test
    void aSpawnerMobStillTakesTheBonusItJustDoesNotAdvanceMastery() {
        // Stage 1's marker decides what a kill is WORTH, not what a hit is worth. A spawner zombie is
        // still a zombie; refusing the bonus there closes no farm (the farm banks nothing either way)
        // while making the damage a player sees depend on an invisible property of their target.
        seedKills(ZOMBIE_ID, TOP_TIER_KILLS);
        final Zombie farmed = zombie();
        when(farmed.getAttached(McMMOAttachments.MOB_ORIGIN))
                .thenReturn(MobOrigin.SPAWNER.storageKey());

        assertEquals(10F + (float) TOP_TIER_BONUS,
                EntityDamageListener.applyHunterMastery(farmed, melee(), 10F), EPSILON);

        HunterListener.onDeath(farmed, melee());
        assertEquals(TOP_TIER_KILLS, profile.getMobKills(ZOMBIE_ID),
                "a farmed kill must still bank nothing");
    }

    // --- ⚠️ the drift test ------------------------------------------------------------------------

    @Test
    void theBonusIsLookedUpUnderTheSameKeyTheCounterBanksItUnder() {
        // ⚠️ The failure this exists for is silent and total: if the banking path and the damage path
        // ever disagreed about the mob key — full registry id here, bare path there — the counters
        // would keep climbing, the threshold message would still fire, and the damage would never
        // change. Neither side's own tests would notice, because each is self-consistent. So the
        // kills here are banked through the REAL AFTER_DEATH handler and spent through the REAL
        // damage arm, with nothing in between agreeing on a literal.
        final Zombie victim = zombie();
        for (int i = 0; i < HunterManager.MASTERY_THRESHOLDS[0]; i++) {
            HunterListener.onDeath(victim, melee());
        }

        assertEquals(10F + (float) HunterManager.MASTERY_DAMAGE_BONUS[0],
                EntityDamageListener.applyHunterMastery(zombie(), melee(), 10F), EPSILON);
    }

    // --- Stage 7: Quarry Sense, the in-world readout ---------------------------------------------

    /**
     * The messages the real {@code ALLOW_DAMAGE} dispatcher sent, and whether it cancelled the hit.
     *
     * <p>Everything below drives {@link EntityDamageListener#onAllowDamage} rather than the inspect
     * branch directly: a gate proved on a method nothing calls is the trap this port keeps hitting,
     * and the sneak requirement in particular is only meaningful if the dispatcher consults it.
     */
    private record Inspection(boolean cancelled, String message) {
    }

    private Inspection inspect(LivingEntity target) {
        final boolean allowed = EntityDamageListener.onAllowDamage(target, melee(), 1F);
        // attacker is a raw ServerPlayerEntity here, so this is vanilla's own sendMessage(Text) --
        // not PlatformPlayer's §-string overload.
        final ArgumentCaptor<Component> sent = ArgumentCaptor.forClass(Component.class);
        Mockito.verify(attacker, Mockito.atMost(1)).sendMessage(sent.capture());
        return new Inspection(!allowed,
                sent.getAllValues().isEmpty() ? "" : sent.getValue().getString());
    }

    /** Put a bone in the attacker's hand and crouch them — the full Quarry Sense gesture. */
    private void readyToInspect() {
        when(attacker.getMainHandStack()).thenReturn(new ItemStack(Items.BONE));
        when(attacker.isSneaking()).thenReturn(true);
    }

    @Test
    void quarrySenseReadsBackTheCountTheTierAndWhatThatTierIsWorth() {
        seedKills(ZOMBIE_ID, HunterManager.MASTERY_THRESHOLDS[1]);
        readyToInspect();

        final Inspection inspection = inspect(zombie());

        assertTrue(inspection.cancelled(), "an inspected creature is not also hit");
        assertTrue(inspection.message().contains("Zombie"), inspection.message());
        // ⚠️ The count is asserted MessageFormat-grouped, which is how it reaches the player, and the
        // tier as its rendered wording rather than a bare "2" — the count 2,500 contains a 2, so a
        // digit search here would pass against a readout that never printed the tier at all. Same
        // trap stage 3's threshold notification walked into.
        assertTrue(inspection.message().contains("2,500"), inspection.message());
        assertTrue(inspection.message().contains("Mastery 2"), inspection.message());
        assertTrue(inspection.message().contains("+2.0"), inspection.message());
        // The countdown to Mastery III: 10,000 - 2,500.
        assertTrue(inspection.message().contains("7,500"), inspection.message());
    }

    @Test
    void anUnhuntedCreatureStillReadsBackTheTargetToAimAt() {
        // The first-kill case is the whole reason the sub-skill exists: without it the horizontal
        // axis is 499 kills of nothing appearing to happen.
        readyToInspect();

        final Inspection inspection = inspect(zombie());

        assertTrue(inspection.message().contains("500"), "the first threshold is the target");
        // ⚠️ NOT `!contains("Mastery 1")` — that reads right and is wrong, because the countdown
        // line names the tier being worked toward ("500 more for Mastery 1"). The real claim is that
        // the mastery slot itself is empty, so assert the wording that fills it.
        assertTrue(inspection.message().contains("No mastery yet"), inspection.message());
    }

    @Test
    void aFullyMasteredCreatureIsToldSoInsteadOfCountingDownPastZero() {
        seedKills(ZOMBIE_ID, TOP_TIER_KILLS);
        readyToInspect();

        final Inspection inspection = inspect(zombie());

        assertTrue(inspection.message().contains("fully mastered"), inspection.message());
        assertTrue(inspection.message().contains("Mastery 3"), inspection.message());
    }

    @Test
    void everyBranchOfTheReadoutResolvesItsLocaleKey() {
        // ⚠️ Nine keys, and a miss renders as literal "!Hunter.SubSkill.QuarrySense.Lore.Capped!"
        // rather than throwing — the ungreppable-locale-family failure this port has shipped seven
        // times. Driven across all three both-ways branches (mastered/not, capped/not, trophy
        // reached/not) so no arm is left unrendered.
        readyToInspect();
        for (int kills : new int[] {0, HunterManager.MASTERY_THRESHOLDS[0], TOP_TIER_KILLS}) {
            seedKills(ZOMBIE_ID, kills);
            // ⚠️ Both levels must UNLOCK Quarry Sense or the assertion is vacuous — a locked readout
            // prints nothing at all and contains no unresolved key either. Level 1 is the lowest
            // level that opens it (and leaves Trophy Hunter at rank 0, the "does not reach" arm);
            // 1,000 opens rank 4, the other arm.
            for (int level : new int[] {1, 1_000}) {
                when(mmoPlayer.getSkillLevel(PrimarySkillType.HUNTER)).thenReturn(level);
                final String message = inspect(zombie()).message();
                assertTrue(message.contains("QUARRY SENSE"),
                        "the readout must actually render at " + kills + " kills, level " + level);
                assertFalse(message.contains("!Hunter."),
                        "unresolved locale key at " + kills + " kills, level " + level
                                + ": " + message);
                Mockito.clearInvocations(attacker);
            }
        }
    }

    @Test
    void theTrophyLineTellsThePlayerWhetherTheirRankReachesThisCreature() {
        // Both arms, in one test, driven off the REAL Standard ladder (10/30/60/90): a zombie is
        // tier 2, so rank 2 is what reaches it. Asserting only the unlocked arm would pass against a
        // line hard-wired to "yes", which is the more dangerous of the two lies.
        readyToInspect();

        when(mmoPlayer.getSkillLevel(PrimarySkillType.HUNTER)).thenReturn(10); // rank 1 -- T1 only
        assertTrue(inspect(zombie()).message().contains("does not reach"),
                "rank 1 must not claim to reach a tier-2 creature");
        Mockito.clearInvocations(attacker);

        when(mmoPlayer.getSkillLevel(PrimarySkillType.HUNTER)).thenReturn(30); // rank 2 -- T2
        assertTrue(inspect(zombie()).message().contains("reaches this creature"));
    }

    // --- ⚠️ the gate that keeps a bone usable as a bone -------------------------------------------

    @Test
    void aBoneSwungWithoutCrouchingIsAnOrdinaryPunch() {
        // ⚠️ THE reason Quarry Sense needs a modifier Beast Lore does not: it works on every creature
        // in the game, and a bone is a SKELETON'S OWN DROP. Without this, a player who picks one up
        // and is then set upon cannot swing back at anything.
        seedKills(ZOMBIE_ID, TOP_TIER_KILLS);
        when(attacker.getMainHandStack()).thenReturn(new ItemStack(Items.BONE));
        when(attacker.isSneaking()).thenReturn(false);

        final Inspection inspection = inspect(zombie());

        assertFalse(inspection.cancelled(), "the hit must land");
        assertEquals("", inspection.message(), "and nothing is printed");
    }

    @Test
    void crouchingWithoutABoneIsAnOrdinaryBackstab() {
        seedKills(ZOMBIE_ID, TOP_TIER_KILLS);
        when(attacker.isSneaking()).thenReturn(true);

        assertFalse(inspect(zombie()).cancelled(), "the hit must land");
    }

    @Test
    void anArmourStandIsNotQuarryEither() {
        // Sneak-hitting an armour stand is how a player dismantles one, and its counter is
        // permanently zero — a readout there would be all cost and no information.
        readyToInspect();

        assertFalse(inspect(mock(ArmorStand.class)).cancelled());
    }

    @Test
    void aLockedQuarrySenseInspectsNothing() {
        // Level 0 leaves rank 1 unreached, so an operator who disables the skill gets a bone back.
        when(mmoPlayer.getSkillLevel(PrimarySkillType.HUNTER)).thenReturn(0);
        readyToInspect();

        assertFalse(inspect(zombie()).cancelled());
    }
}
