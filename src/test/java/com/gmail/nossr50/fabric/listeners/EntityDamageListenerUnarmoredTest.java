package com.gmail.nossr50.fabric.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.MetadataStore;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.skills.unarmored.UnarmoredManager;
import com.gmail.nossr50.util.player.UserManager;
import java.util.UUID;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageSources;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unarmored's wiring into the damage seam — everything {@code UnarmoredManagerTest} cannot reach.
 *
 * <p>That test pins the payout arithmetic against a damage figure it is simply handed. What is
 * unproven without this file is the part that can silently go wrong in-game: that the figure handed
 * over is the <b>pre-armour</b> one (the whole reason the second injector exists), that the
 * "every slot empty" gate is really consulted, that the living-attacker exploit gate holds, and —
 * the one a predicate-only test would miss entirely — that {@link
 * EntityDamageListener#onModifyAppliedDamage} actually calls any of it. The last test here drives
 * the real dispatch for exactly that reason: delete the call from the victim branch and only that
 * test fails.
 */
class EntityDamageListenerUnarmoredTest {

    @BeforeAll
    static void bootstrapRegistries() {
        com.gmail.nossr50.util.McTestRegistries.bootstrap();
    }

    private static final float EPSILON = 1.0E-4F;

    /** The shipped rate; restated so a retune surfaces here rather than as a silent drift. */
    private static final int XP_PER_DAMAGE = 100;

    private UUID uuid;
    private McMMOPlayer mmoPlayer;
    private UnarmoredManager unarmored;
    private ExperienceConfig experienceConfig;

    @BeforeEach
    void setUp() {
        experienceConfig = mock(ExperienceConfig.class);
        lenient().when(experienceConfig.isUnarmoredLivingAttackerRequired()).thenReturn(true);
        // The shipped cap, not Mockito's zero: a fixture that silently disables the anti-farm gate
        // would let every test above pass on a code path no player ever runs.
        lenient().when(experienceConfig.getUnarmoredMaxAwardsPerAttacker()).thenReturn(20);
        McMMOMod.setExperienceConfig(experienceConfig);
    }

    @AfterEach
    void tearDown() {
        if (mmoPlayer != null) {
            UserManager.cleanupPlayer(mmoPlayer);
        }
        EntityDamageListener.clear();
        MetadataStore.clearAll();
        McMMOMod.setExperienceConfig(null);
    }

    /** A bare-skinned player with a server clock behind them, tracked in {@link UserManager}. */
    private ServerPlayerEntity unarmoredPlayer() {
        uuid = UUID.randomUUID();

        final MinecraftServer server = mock(MinecraftServer.class);
        lenient().when(server.getTicks()).thenReturn(1_000);
        final ServerWorld world = mock(ServerWorld.class);
        lenient().when(world.getServer()).thenReturn(server);

        final ServerPlayerEntity player = mock(ServerPlayerEntity.class);
        lenient().when(player.getUuid()).thenReturn(uuid);
        lenient().when(player.getEntityWorld()).thenReturn(world);
        lenient().when(player.getMainHandStack()).thenReturn(ItemStack.EMPTY);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            lenient().when(player.getEquippedStack(slot)).thenReturn(ItemStack.EMPTY);
        }

        unarmored = mock(UnarmoredManager.class);
        final PlatformPlayer platformPlayer = mock(PlatformPlayer.class);
        lenient().when(platformPlayer.getUniqueId()).thenReturn(uuid);
        mmoPlayer = mock(McMMOPlayer.class);
        lenient().when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        lenient().when(mmoPlayer.getUnarmoredManager()).thenReturn(unarmored);
        UserManager.track(mmoPlayer);
        return player;
    }

    /**
     * A distinct zombie.
     *
     * <p>The identity matters: {@link MetadataStore} is keyed on {@code getUuid()}, which a bare mock
     * answers with {@code null} — and a {@code ConcurrentHashMap} refuses a null key outright, so an
     * unstubbed attacker would blow up inside the per-attacker cap rather than quietly sharing a
     * counter. Every zombie here gets its own id so "this mob is spent" cannot be confused with
     * "all mobs are spent".
     */
    private static ZombieEntity zombie() {
        final ZombieEntity zombie = mock(ZombieEntity.class);
        lenient().when(zombie.getUuid()).thenReturn(UUID.randomUUID());
        return zombie;
    }

    /** A zombie's punch: a living attacker who is not the victim. */
    private static DamageSource mobAttack(LivingEntity attacker) {
        final DamageSource source = mock(DamageSource.class);
        lenient().when(source.getAttacker()).thenReturn(attacker);
        lenient().when(source.getSource()).thenReturn(attacker);
        lenient().when(source.isOf(any())).thenReturn(false);
        lenient().when(source.isIn(any())).thenReturn(false);
        return source;
    }

    // --- the pre-armour reading -------------------------------------------------------------------

    @Test
    void theXpIsPaidOnTheDamageBeforeArmourAteItsShare() {
        // The entire reason the applyArmorToDamage injector exists. Iron Skin IS armour, so at the
        // diamond tier vanilla soaks roughly two thirds of a hit — metering XP on what landed would
        // have the skill run its longest stretch at a third rate, which reads as a bug.
        final ServerPlayerEntity player = unarmoredPlayer();
        final DamageSource source = mobAttack(zombie());

        EntityDamageListener.recordPreArmorDamage(player, source, 9F);
        // ...and the post-armour figure the seam is actually handed is much smaller.
        EntityDamageListener.onModifyAppliedDamage(player, source, 3F);

        verify(unarmored).onDamageTaken(9.0);
        verify(unarmored, never()).onDamageTaken(3.0);
    }

    @Test
    void aReadingIsSpentOnceAndOnceOnly() {
        final ServerPlayerEntity player = unarmoredPlayer();
        final DamageSource source = mobAttack(zombie());

        EntityDamageListener.recordPreArmorDamage(player, source, 9F);
        EntityDamageListener.onModifyAppliedDamage(player, source, 3F);
        // A second hit with no fresh capture must not re-use the first one's 9 — it falls back to
        // the post-armour amount, which pays less rather than paying a stale jackpot.
        EntityDamageListener.onModifyAppliedDamage(player, source, 3F);

        verify(unarmored).onDamageTaken(9.0);
        verify(unarmored).onDamageTaken(3.0);
    }

    @Test
    void aReadingCapturedAgainstAnotherVictimIsRefused() {
        // The guard that makes the two-injector join safe: a stash left by some other entity's hit
        // must not be spent on this player's.
        final ServerPlayerEntity player = unarmoredPlayer();
        final DamageSource source = mobAttack(zombie());

        EntityDamageListener.recordPreArmorDamage(mock(ZombieEntity.class), source, 9F);
        EntityDamageListener.onModifyAppliedDamage(player, source, 3F);

        verify(unarmored).onDamageTaken(3.0);
    }

    @Test
    void aReadingCapturedAgainstAnotherDamageSourceIsRefused() {
        final ServerPlayerEntity player = unarmoredPlayer();
        final DamageSource source = mobAttack(zombie());

        EntityDamageListener.recordPreArmorDamage(player, mobAttack(zombie()), 9F);
        EntityDamageListener.onModifyAppliedDamage(player, source, 3F);

        verify(unarmored).onDamageTaken(3.0);
    }

    // --- the "every slot empty" gate ---------------------------------------------------------------

    @Test
    void aSingleWornPieceTurnsTheWholeSkillOff() {
        // One slot is enough, and each of the four is tested: a gate written as an || chain can lose
        // one arm and stay green against a test that only ever equips a helmet.
        for (EquipmentSlot slot : new EquipmentSlot[] {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            final ServerPlayerEntity player = unarmoredPlayer();
            when(player.getEquippedStack(slot)).thenReturn(new ItemStack(Items.LEATHER_HELMET));

            EntityDamageListener.maybeAwardUnarmoredXp(player, mobAttack(zombie()), 9F);

            verify(unarmored, never()).onDamageTaken(anyDouble());
            UserManager.cleanupPlayer(mmoPlayer);
            mmoPlayer = null;
        }
    }

    @Test
    void anOccupiedSlotCountsEvenWhenItIsNotArmour() {
        // Deliberately stricter than PlatformLivingEntity#getArmorPieces, which filters by
        // ItemUtils.isArmor. "Free diamond-grade armour as long as mcMMO does not recognise your hat"
        // is a rule that rewards hunting for the one head-slot item outside the material store.
        final ServerPlayerEntity player = unarmoredPlayer();
        when(player.getEquippedStack(EquipmentSlot.HEAD))
                .thenReturn(new ItemStack(Items.CARVED_PUMPKIN));

        EntityDamageListener.maybeAwardUnarmoredXp(player, mobAttack(zombie()), 9F);

        verify(unarmored, never()).onDamageTaken(anyDouble());
    }

    // --- the living-attacker exploit gate ---------------------------------------------------------

    @Test
    void environmentalDamagePaysNothingWhileTheGateIsOn() {
        // The skill's main cheese: stand in a cactus or a fire with a stack of food and level up
        // while doing something else. A sourceless hit has no attacker at all.
        final ServerPlayerEntity player = unarmoredPlayer();
        final DamageSource cactus = mock(DamageSource.class);
        lenient().when(cactus.getAttacker()).thenReturn(null);

        EntityDamageListener.maybeAwardUnarmoredXp(player, cactus, 9F);

        verify(unarmored, never()).onDamageTaken(anyDouble());
    }

    @Test
    void turningTheGateOffMakesEnvironmentalDamagePay() {
        // Proves the config key is actually consulted rather than the behaviour being hardcoded —
        // otherwise the play-test escape hatch would be a knob that lies.
        when(experienceConfig.isUnarmoredLivingAttackerRequired()).thenReturn(false);
        final ServerPlayerEntity player = unarmoredPlayer();
        final DamageSource cactus = mock(DamageSource.class);
        lenient().when(cactus.getAttacker()).thenReturn(null);

        EntityDamageListener.maybeAwardUnarmoredXp(player, cactus, 9F);

        verify(unarmored).onDamageTaken(9.0);
    }

    @Test
    void aNonLivingAttackerPaysNothing() {
        // An arrow from a dispenser, a falling anvil, a Blast Mining charge: something is credited
        // with the hit, but nothing that can be fought.
        final ServerPlayerEntity player = unarmoredPlayer();
        final DamageSource source = mock(DamageSource.class);
        lenient().when(source.getAttacker()).thenReturn(mock(TntEntity.class));

        EntityDamageListener.maybeAwardUnarmoredXp(player, source, 9F);

        verify(unarmored, never()).onDamageTaken(anyDouble());
    }

    @Test
    void blowingYourselfUpPaysNothing() {
        // A player IS a living entity, so without the "not the victim" clause a Blast Mining charge
        // — a repeatable mining loop Demolitions Expertise exists to make survivable — would be a
        // fully automatable XP source that never needs a mob.
        final ServerPlayerEntity player = unarmoredPlayer();
        final DamageSource ownBlast = mock(DamageSource.class);
        lenient().when(ownBlast.getAttacker()).thenReturn(player);

        EntityDamageListener.maybeAwardUnarmoredXp(player, ownBlast, 9F);

        verify(unarmored, never()).onDamageTaken(anyDouble());
    }

    @Test
    void aNonPositiveHitIsNotEvenLookedUp() {
        final ServerPlayerEntity player = unarmoredPlayer();

        EntityDamageListener.maybeAwardUnarmoredXp(player, mobAttack(zombie()), 0F);

        verify(unarmored, never()).onDamageTaken(anyDouble());
    }

    // --- the per-attacker anti-farm cap -------------------------------------------------------------

    @Test
    void oneMobStopsPayingOnceItHasHandedOverItsShare() {
        // The gate that decides whether the skill's 92-hour budget means anything. Require_Living_
        // Attacker does NOT reach this case — a zombie IS a living attacker, so one of them hitting a
        // player through a slab while saturation regen keeps up is a passive ~250 XP/s.
        when(experienceConfig.getUnarmoredMaxAwardsPerAttacker()).thenReturn(3);
        final ServerPlayerEntity player = unarmoredPlayer();
        when(unarmored.onDamageTaken(anyDouble())).thenReturn(900F);
        final DamageSource source = mobAttack(zombie());

        for (int i = 0; i < 5; i++) {
            EntityDamageListener.maybeAwardUnarmoredXp(player, source, 9F);
        }

        verify(unarmored, times(3)).onDamageTaken(anyDouble());
    }

    @Test
    void eachAttackerCarriesItsOwnCounter() {
        // A cap keyed on the player instead of on the mob would be a rate limit, and would throttle
        // the legitimate case — a real fight is several mobs hitting you at once.
        when(experienceConfig.getUnarmoredMaxAwardsPerAttacker()).thenReturn(1);
        final ServerPlayerEntity player = unarmoredPlayer();
        when(unarmored.onDamageTaken(anyDouble())).thenReturn(900F);

        final DamageSource first = mobAttack(zombie());
        EntityDamageListener.maybeAwardUnarmoredXp(player, first, 9F);
        EntityDamageListener.maybeAwardUnarmoredXp(player, first, 9F); // spent
        EntityDamageListener.maybeAwardUnarmoredXp(player, mobAttack(zombie()), 9F); // fresh mob

        verify(unarmored, times(2)).onDamageTaken(anyDouble());
    }

    @Test
    void aHitThatPaysNothingDoesNotBurnACapSlot() {
        // The counter is bumped on the award, not on the attempt. Otherwise a player who is capped
        // out on XP for some unrelated reason would silently spend their whole allowance on hits
        // that were worth zero.
        when(experienceConfig.getUnarmoredMaxAwardsPerAttacker()).thenReturn(1);
        final ServerPlayerEntity player = unarmoredPlayer();
        when(unarmored.onDamageTaken(anyDouble())).thenReturn(0F);
        final DamageSource source = mobAttack(zombie());

        for (int i = 0; i < 4; i++) {
            EntityDamageListener.maybeAwardUnarmoredXp(player, source, 9F);
        }

        verify(unarmored, times(4)).onDamageTaken(anyDouble());
    }

    @Test
    void aCapOfZeroTurnsTheLimitOff() {
        // Proves the config key is consulted rather than the 20 being hardcoded — the same reason
        // the living-attacker gate has an off-switch test.
        when(experienceConfig.getUnarmoredMaxAwardsPerAttacker()).thenReturn(0);
        final ServerPlayerEntity player = unarmoredPlayer();
        when(unarmored.onDamageTaken(anyDouble())).thenReturn(900F);
        final DamageSource source = mobAttack(zombie());

        for (int i = 0; i < 40; i++) {
            EntityDamageListener.maybeAwardUnarmoredXp(player, source, 9F);
        }

        verify(unarmored, times(40)).onDamageTaken(anyDouble());
    }

    // --- Thorny Skin ---------------------------------------------------------------------------------

    /**
     * The damage source a sting is expected to arrive as: {@code playerAttack(victim)}.
     *
     * <p>Stubbed rather than matched loosely with {@code any()}, because the attribution is the part
     * that could go wrong invisibly — a sting credited to nobody still damages the mob but stops
     * being the player's kill, which changes drops, XP and every downstream combat hook.
     */
    private DamageSource stingSource;

    /** A zombie close enough to be stung: alive, in a server world, with damage sources available. */
    private ZombieEntity stingableZombie(ServerPlayerEntity victim) {
        final ZombieEntity zombie = zombie();
        stingSource = mock(DamageSource.class);
        final DamageSources damageSources = mock(DamageSources.class);
        lenient().when(damageSources.playerAttack(victim)).thenReturn(stingSource);
        final ServerWorld world = mock(ServerWorld.class);
        lenient().when(world.getDamageSources()).thenReturn(damageSources);
        lenient().when(zombie.isAlive()).thenReturn(true);
        lenient().when(zombie.getEntityWorld()).thenReturn(world);
        return zombie;
    }

    @Test
    void aMeleeAttackerIsStungThroughTheRealDispatch() {
        // Driven through onModifyAppliedDamage rather than the private handler, for the reason the
        // XP arm is: a gate-only test passes with the call site deleted.
        final ServerPlayerEntity player = unarmoredPlayer();
        final ZombieEntity assailant = stingableZombie(player);
        when(unarmored.thornsReady(true)).thenReturn(true);
        when(unarmored.getThornsDamage(true)).thenReturn(1.0);

        EntityDamageListener.onModifyAppliedDamage(player, mobAttack(assailant), 6F);

        verify(assailant).damage(eq(stingSource), eq(1.0F));
    }

    @Test
    void aProjectileAttackerIsOutOfReach() {
        // Melee only, and the gate that achieves it is the DIRECT damager being living. A skeleton
        // 30 blocks away is credited as the attacker but its arrow is what touched you.
        final ServerPlayerEntity player = unarmoredPlayer();
        final ZombieEntity shooter = stingableZombie(player);
        when(unarmored.thornsReady(true)).thenReturn(true);
        lenient().when(unarmored.getThornsDamage(true)).thenReturn(1.0);

        final DamageSource arrow = mock(DamageSource.class);
        lenient().when(arrow.getAttacker()).thenReturn(shooter);
        lenient().when(arrow.getSource()).thenReturn(mock(ArrowEntity.class));
        lenient().when(arrow.isOf(any())).thenReturn(false);
        lenient().when(arrow.isIn(any())).thenReturn(false);

        EntityDamageListener.onModifyAppliedDamage(player, arrow, 6F);

        verify(shooter, never()).damage(any(), anyFloat());
    }

    @Test
    void anArmouredPlayerStingsNobody() {
        // Same one rule the whole skill hangs off. Without this, Thorny would be a free thorns
        // enchantment on a fully-plated player.
        final ServerPlayerEntity player = unarmoredPlayer();
        when(player.getEquippedStack(EquipmentSlot.CHEST))
                .thenReturn(new ItemStack(Items.IRON_CHESTPLATE));
        final ZombieEntity assailant = stingableZombie(player);
        lenient().when(unarmored.thornsReady(true)).thenReturn(true);
        lenient().when(unarmored.getThornsDamage(true)).thenReturn(1.0);

        EntityDamageListener.onModifyAppliedDamage(player, mobAttack(assailant), 6F);

        verify(assailant, never()).damage(any(), anyFloat());
    }

    @Test
    void aLockedThornySkinStingsNobody() {
        final ServerPlayerEntity player = unarmoredPlayer();
        final ZombieEntity assailant = stingableZombie(player);
        when(unarmored.thornsReady(true)).thenReturn(false);

        EntityDamageListener.onModifyAppliedDamage(player, mobAttack(assailant), 6F);

        verify(assailant, never()).damage(any(), anyFloat());
    }

    @Test
    void theStingLeavesTheHitTheVictimTakesAlone() {
        // Thorny is a pure side effect on the attacker; it must not quietly become damage reduction.
        final ServerPlayerEntity player = unarmoredPlayer();
        when(unarmored.thornsReady(true)).thenReturn(true);
        when(unarmored.getThornsDamage(true)).thenReturn(1.0);

        assertEquals(6F, EntityDamageListener.onModifyAppliedDamage(player,
                mobAttack(stingableZombie(player)), 6F), EPSILON);
    }

    // --- the wiring itself --------------------------------------------------------------------------

    @Test
    void theDamageSeamLeavesTheHitItselfAlone() {
        // Unarmored's XP arm must be a pure side effect: it reads the pre-armour figure and pays,
        // but the damage the player takes is vanilla's business (Iron Skin does its work through an
        // attribute, not by rewriting this number).
        final ServerPlayerEntity player = unarmoredPlayer();
        final DamageSource source = mobAttack(zombie());
        EntityDamageListener.recordPreArmorDamage(player, source, 9F);

        assertEquals(3F, EntityDamageListener.onModifyAppliedDamage(player, source, 3F), EPSILON);
    }

    @Test
    void fallDamageStillReachesTheRollArmWithTheGateOn() {
        // The Unarmored branch sits above the fall/blast/dodge dispatch, so the thing to prove is
        // that it does not swallow it: a fall must still be routed to Agility Roll (which here has
        // no manager, so the damage comes back untouched) rather than being consumed as "not an
        // Unarmored source" and returned early.
        final ServerPlayerEntity player = unarmoredPlayer();
        final DamageSource fall = mock(DamageSource.class);
        lenient().when(fall.getAttacker()).thenReturn(null);
        lenient().when(fall.getSource()).thenReturn(null);
        lenient().when(fall.isOf(any())).thenReturn(false);
        lenient().when(fall.isIn(any())).thenReturn(false);
        when(fall.isIn(DamageTypeTags.IS_FALL)).thenReturn(true);

        assertEquals(6F, EntityDamageListener.onModifyAppliedDamage(player, fall, 6F), EPSILON);
        verify(unarmored, never()).onDamageTaken(anyDouble());
        verify(mmoPlayer).getMovementManager();
    }
}
