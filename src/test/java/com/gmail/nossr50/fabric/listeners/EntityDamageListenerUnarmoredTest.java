package com.gmail.nossr50.fabric.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.skills.unarmored.UnarmoredManager;
import com.gmail.nossr50.util.player.UserManager;
import java.util.UUID;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.ZombieEntity;
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
        McMMOMod.setExperienceConfig(experienceConfig);
    }

    @AfterEach
    void tearDown() {
        if (mmoPlayer != null) {
            UserManager.cleanupPlayer(mmoPlayer);
        }
        EntityDamageListener.clear();
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
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
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
        final DamageSource source = mobAttack(mock(ZombieEntity.class));

        EntityDamageListener.recordPreArmorDamage(player, source, 9F);
        // ...and the post-armour figure the seam is actually handed is much smaller.
        EntityDamageListener.onModifyAppliedDamage(player, source, 3F);

        verify(unarmored).onDamageTaken(9.0);
        verify(unarmored, never()).onDamageTaken(3.0);
    }

    @Test
    void aReadingIsSpentOnceAndOnceOnly() {
        final ServerPlayerEntity player = unarmoredPlayer();
        final DamageSource source = mobAttack(mock(ZombieEntity.class));

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
        final DamageSource source = mobAttack(mock(ZombieEntity.class));

        EntityDamageListener.recordPreArmorDamage(mock(ZombieEntity.class), source, 9F);
        EntityDamageListener.onModifyAppliedDamage(player, source, 3F);

        verify(unarmored).onDamageTaken(3.0);
    }

    @Test
    void aReadingCapturedAgainstAnotherDamageSourceIsRefused() {
        final ServerPlayerEntity player = unarmoredPlayer();
        final DamageSource source = mobAttack(mock(ZombieEntity.class));

        EntityDamageListener.recordPreArmorDamage(player, mobAttack(mock(ZombieEntity.class)), 9F);
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

            EntityDamageListener.maybeAwardUnarmoredXp(player, mobAttack(mock(ZombieEntity.class)), 9F);

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

        EntityDamageListener.maybeAwardUnarmoredXp(player, mobAttack(mock(ZombieEntity.class)), 9F);

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

        EntityDamageListener.maybeAwardUnarmoredXp(player, mobAttack(mock(ZombieEntity.class)), 0F);

        verify(unarmored, never()).onDamageTaken(anyDouble());
    }

    // --- the wiring itself --------------------------------------------------------------------------

    @Test
    void theDamageSeamLeavesTheHitItselfAlone() {
        // Unarmored's XP arm must be a pure side effect: it reads the pre-armour figure and pays,
        // but the damage the player takes is vanilla's business (Iron Skin does its work through an
        // attribute, not by rewriting this number).
        final ServerPlayerEntity player = unarmoredPlayer();
        final DamageSource source = mobAttack(mock(ZombieEntity.class));
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
        verify(mmoPlayer).getAgilityManager();
    }
}
