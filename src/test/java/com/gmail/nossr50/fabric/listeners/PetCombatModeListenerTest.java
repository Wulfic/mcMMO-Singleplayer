package com.gmail.nossr50.fabric.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.subskills.taming.PetCombatMode;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.skills.taming.TamingManager;
import com.gmail.nossr50.util.McTestRegistries;
import com.gmail.nossr50.util.player.UserManager;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link PetCombatModeListener} — the sneak-right-click gesture, and specifically <em>who the click
 * belongs to</em>.
 *
 * <p>This is {@link RepairSalvageListenerTest} one level up, and for the same reason: the callback
 * fires on both logical sides, and answering only on the server leaves the client running vanilla's
 * own prediction — here, visibly sitting the wolf and then snapping it back upright when the server
 * disagrees. So the first tests drive the callback with a plain (non-server) player and assert the
 * click is claimed, and the rest pin the boundary of that claim: a listener that swallowed every
 * entity right-click would break feeding, breeding, shearing, leashing and trading.
 *
 * <p>Every test drives the real {@code onUseEntity} dispatch rather than {@code isToggleGesture}
 * alone. A predicate-only suite goes green with {@link PetCombatModeListener#register} deleted and
 * the feature entirely unreachable in game — the {@code respawn-stale-handle} lesson.
 */
class PetCombatModeListenerTest {

    @BeforeAll
    static void bootstrapRegistries() {
        McTestRegistries.bootstrap();
    }

    private Level world;
    private McMMOPlayer mmoPlayer;
    private TamingManager tamingManager;

    @BeforeEach
    void setUp() {
        final GeneralConfig config = mock(GeneralConfig.class);
        lenient().when(config.isPetCombatModeEnabled()).thenReturn(true);
        lenient().when(config.getPetCombatModeToggleItem()).thenReturn("BONE");
        McMMOMod.setGeneralConfig(config);
        world = mock(Level.class);
    }

    @AfterEach
    void tearDown() {
        if (mmoPlayer != null) {
            UserManager.cleanupPlayer(mmoPlayer);
            mmoPlayer = null;
        }
        McMMOMod.setGeneralConfig(null);
    }

    // --- the both-sides claim -------------------------------------------------------------------

    /**
     * ⚠️⚠️ The regression guard. A client-side fire on the toggle gesture must claim the click.
     *
     * <p>{@code PASS} here lets the client predict vanilla's sit-toggle: the wolf visibly sits, then
     * pops back up a tick later when the server's state arrives. {@code FAIL} is worse — it cancels
     * the packet outright, so the server never hears the gesture and the toggle silently does
     * nothing at all.
     */
    @Test
    void clientSideFireOnTheGestureClaimsTheClick() {
        final InteractionResult result = PetCombatModeListener.onUseEntity(
                sneakingClientPlayer(new ItemStack(Items.BONE)), world, InteractionHand.MAIN_HAND,
                ownedWolf(), null);

        assertEquals(InteractionResult.CONSUME, result,
                "the client fire must claim the gesture, or the client predicts vanilla's sit-toggle");
    }

    /** The client-side fire must touch no state — the server fire owns the flip. */
    @Test
    void clientSideFireMutatesNothing() {
        final Player player = sneakingClientPlayer(new ItemStack(Items.BONE));
        // A tracked profile exists; the point is that the CLIENT fire must not reach it even so.
        trackedServerPlayer(new ItemStack(Items.BONE));

        PetCombatModeListener.onUseEntity(player, world, InteractionHand.MAIN_HAND, ownedWolf(), null);

        verify(tamingManager, never()).togglePetCombatMode();
    }

    // --- the server side does the work ----------------------------------------------------------

    @Test
    void serverSideFireTogglesTheMode() {
        final ServerPlayer player = trackedServerPlayer(new ItemStack(Items.BONE));
        when(tamingManager.togglePetCombatMode()).thenReturn(PetCombatMode.AGGRESSIVE);

        final InteractionResult result = PetCombatModeListener.onUseEntity(player, world, InteractionHand.MAIN_HAND,
                ownedWolf(player), null);

        verify(tamingManager).togglePetCombatMode();
        assertEquals(InteractionResult.CONSUME, result);
    }

    /**
     * A click that arrives before the profile has loaded still consumes.
     *
     * <p>The client has already suppressed its sit prediction by the time this runs, so answering
     * {@code PASS} here makes the two sides disagree about whose click it was and sits the pet on a
     * gesture that was claimed. That mid-decision fall-through is exactly the repair-anvil bug.
     */
    @Test
    void anUnloadedProfileStillConsumesTheClick() {
        // Deliberately NOT tracked in UserManager: this is the mid-join state.
        final ServerPlayer player = mock(ServerPlayer.class);
        lenient().when(player.getUuid()).thenReturn(UUID.randomUUID());
        lenient().when(player.getMainHandStack()).thenReturn(new ItemStack(Items.BONE));
        lenient().when(player.isSneaking()).thenReturn(true);
        lenient().when(player.getName()).thenReturn(Component.literal("mid-join"));

        final InteractionResult result = PetCombatModeListener.onUseEntity(player, world, InteractionHand.MAIN_HAND,
                ownedWolf(player), null);

        assertEquals(InteractionResult.CONSUME, result,
                "handing the click back mid-decision sits the pet on a gesture already claimed");
    }

    // --- the boundary of the claim --------------------------------------------------------------

    /**
     * ⚠️ The single most important negative. Without the sneak requirement this listener would
     * swallow <em>every</em> right-click on a pet made while holding a bone, and a player could never
     * sit their wolf again.
     */
    @Test
    void aPlainRightClickWithoutSneakingPasses() {
        final Player player = clientPlayer(new ItemStack(Items.BONE), false);

        assertSame(InteractionResult.PASS,
                PetCombatModeListener.onUseEntity(player, world, InteractionHand.MAIN_HAND, ownedWolf(), null));
    }

    /** Any other item is vanilla's click — feeding, breeding, shearing, leashing all still work. */
    @Test
    void sneakClickingWithTheWrongItemPasses() {
        final Player player = sneakingClientPlayer(new ItemStack(Items.WHEAT));

        assertSame(InteractionResult.PASS,
                PetCombatModeListener.onUseEntity(player, world, InteractionHand.MAIN_HAND, ownedWolf(), null));
    }

    /** An empty hand is not the gesture either. */
    @Test
    void sneakClickingWithAnEmptyHandPasses() {
        final Player player = sneakingClientPlayer(ItemStack.EMPTY);

        assertSame(InteractionResult.PASS,
                PetCombatModeListener.onUseEntity(player, world, InteractionHand.MAIN_HAND, ownedWolf(), null));
    }

    /**
     * Someone else's pet proves nothing about who is clicking, so the gesture must not claim it.
     * (Singleplayer today — but the check is one {@code isOwner} call and the alternative is a
     * listener that toggles your stance by clicking a wolf you have never met.)
     */
    @Test
    void sneakClickingAPetYouDoNotOwnPasses() {
        final Wolf someoneElsesWolf = mock(Wolf.class);
        lenient().when(someoneElsesWolf.isTamed()).thenReturn(true);
        lenient().when(someoneElsesWolf.isOwner(org.mockito.ArgumentMatchers.any()))
                .thenReturn(false);

        assertSame(InteractionResult.PASS, PetCombatModeListener.onUseEntity(
                sneakingClientPlayer(new ItemStack(Items.BONE)), world, InteractionHand.MAIN_HAND,
                someoneElsesWolf, null));
    }

    /** An untamed wolf is a wild animal, not a pet. */
    @Test
    void sneakClickingAnUntamedWolfPasses() {
        final Wolf wild = mock(Wolf.class);
        lenient().when(wild.isTamed()).thenReturn(false);
        lenient().when(wild.isOwner(org.mockito.ArgumentMatchers.any())).thenReturn(false);

        assertSame(InteractionResult.PASS, PetCombatModeListener.onUseEntity(
                sneakingClientPlayer(new ItemStack(Items.BONE)), world, InteractionHand.MAIN_HAND, wild, null));
    }

    /** A hostile mob is not a {@code TameableEntity} at all. */
    @Test
    void sneakClickingANonTameableMobPasses() {
        final Entity zombie = mock(Zombie.class);

        assertSame(InteractionResult.PASS, PetCombatModeListener.onUseEntity(
                sneakingClientPlayer(new ItemStack(Items.BONE)), world, InteractionHand.MAIN_HAND, zombie,
                null));
    }

    /** The off-hand dispatch stays ignored, so one right-click cannot toggle the stance twice. */
    @Test
    void offHandFirePasses() {
        assertSame(InteractionResult.PASS, PetCombatModeListener.onUseEntity(
                sneakingClientPlayer(new ItemStack(Items.BONE)), world, InteractionHand.OFF_HAND, ownedWolf(),
                null));
    }

    /**
     * The config switch has to reach the gesture, not only the sweep. A half-disabled state that
     * swallows the click but does nothing with it is worse than either extreme.
     */
    @Test
    void theGesturePassesWhenTheFeatureIsDisabled() {
        final GeneralConfig off = mock(GeneralConfig.class);
        lenient().when(off.isPetCombatModeEnabled()).thenReturn(false);
        lenient().when(off.getPetCombatModeToggleItem()).thenReturn("BONE");
        McMMOMod.setGeneralConfig(off);

        assertSame(InteractionResult.PASS, PetCombatModeListener.onUseEntity(
                sneakingClientPlayer(new ItemStack(Items.BONE)), world, InteractionHand.MAIN_HAND, ownedWolf(),
                null));
    }

    /**
     * A toggle item that does not resolve to a real item makes the gesture inert rather than taking
     * down every entity interaction in the game.
     */
    @Test
    void anUnresolvableToggleItemPasses() {
        final GeneralConfig typo = mock(GeneralConfig.class);
        lenient().when(typo.isPetCombatModeEnabled()).thenReturn(true);
        lenient().when(typo.getPetCombatModeToggleItem()).thenReturn("NOT_A_REAL_ITEM");
        McMMOMod.setGeneralConfig(typo);

        assertSame(InteractionResult.PASS, PetCombatModeListener.onUseEntity(
                sneakingClientPlayer(new ItemStack(Items.BONE)), world, InteractionHand.MAIN_HAND, ownedWolf(),
                null));
    }

    /** Cats are {@code TameableEntity} too, so the gesture reads on them even though only wolves act. */
    @Test
    void theGestureWorksOnAnyTameableYouOwn() {
        final Cat cat = mock(Cat.class);
        lenient().when(cat.isTamed()).thenReturn(true);
        lenient().when(cat.isOwner(org.mockito.ArgumentMatchers.any())).thenReturn(true);

        assertSame(InteractionResult.CONSUME, PetCombatModeListener.onUseEntity(
                sneakingClientPlayer(new ItemStack(Items.BONE)), world, InteractionHand.MAIN_HAND, cat, null));
    }

    // --- fixture --------------------------------------------------------------------------------

    private static Player clientPlayer(ItemStack mainHand, boolean sneaking) {
        final Player player = mock(Player.class);
        lenient().when(player.getMainHandStack()).thenReturn(mainHand);
        lenient().when(player.isSneaking()).thenReturn(sneaking);
        return player;
    }

    private static Player sneakingClientPlayer(ItemStack mainHand) {
        return clientPlayer(mainHand, true);
    }

    /** A tamed wolf that answers "yes" to any owner asked about it. */
    private static Wolf ownedWolf() {
        final Wolf wolf = mock(Wolf.class);
        lenient().when(wolf.isTamed()).thenReturn(true);
        lenient().when(wolf.isOwner(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        return wolf;
    }

    /** A tamed wolf owned by exactly {@code owner} and nobody else. */
    private static Wolf ownedWolf(Player owner) {
        final Wolf wolf = mock(Wolf.class);
        lenient().when(wolf.isTamed()).thenReturn(true);
        lenient().when(wolf.isOwner(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        lenient().when(wolf.isOwner(owner)).thenReturn(true);
        return wolf;
    }

    /** A sneaking server-side player holding {@code mainHand}, with a profile in {@link UserManager}. */
    private ServerPlayer trackedServerPlayer(ItemStack mainHand) {
        final UUID uuid = UUID.randomUUID();

        final ServerPlayer player = mock(ServerPlayer.class);
        lenient().when(player.getUuid()).thenReturn(uuid);
        lenient().when(player.getMainHandStack()).thenReturn(mainHand);
        lenient().when(player.isSneaking()).thenReturn(true);
        lenient().when(player.getName()).thenReturn(Component.literal("tester"));

        tamingManager = mock(TamingManager.class);
        final PlatformPlayer platformPlayer = mock(PlatformPlayer.class);
        lenient().when(platformPlayer.getUniqueId()).thenReturn(uuid);
        mmoPlayer = mock(McMMOPlayer.class);
        lenient().when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        lenient().when(mmoPlayer.getTamingManager()).thenReturn(tamingManager);
        UserManager.track(mmoPlayer);
        return player;
    }
}
