package com.gmail.nossr50.fabric.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.skills.repair.RepairManager;
import com.gmail.nossr50.skills.repair.repairables.Repairable;
import com.gmail.nossr50.skills.repair.repairables.RepairableManager;
import com.gmail.nossr50.skills.salvage.salvageables.Salvageable;
import com.gmail.nossr50.skills.salvage.salvageables.SalvageableManager;
import com.gmail.nossr50.util.McTestRegistries;
import com.gmail.nossr50.util.player.UserManager;
import java.util.UUID;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The anvil dispatch in {@link RepairSalvageListener} — specifically <em>who the click belongs to</em>,
 * which is the half that no manager test can reach.
 *
 * <p>The bug these tests exist for was reported from play: right-clicking the repair anvil with
 * damaged armour equipped the armour instead of repairing it, which makes Repair unusable on the gear
 * that needs it most. The cause is that {@code UseBlockCallback} fires on <em>both</em> logical sides
 * and the listener only ever answered on the server one; a client-side {@code PASS} lets the client
 * fall through from "use block" to "use item", and for armour that means vanilla equips it out of the
 * hand before the second click of the confirmation gate can land.
 *
 * <p>So the first test drives the callback with a plain (non-server) player — the client-side fire —
 * and asserts the click is claimed. The rest pin the boundary of that claim: it must not swallow
 * clicks that are not mcMMO's, or the fix would break every ordinary right-click made while standing
 * over an iron block.
 */
class RepairSalvageListenerTest {

    @BeforeAll
    static void bootstrapRegistries() {
        McTestRegistries.bootstrap();
    }

    /** Where the anvil sits. Any position; the tests stub the block state at it directly. */
    private static final BlockPos ANVIL_POS = new BlockPos(4, 64, -7);

    private GeneralConfig generalConfig;
    private Level world;
    private McMMOPlayer mmoPlayer;
    private RepairManager repairManager;

    @BeforeEach
    void setUp() {
        // The shipped anvil materials rather than Mockito's null: resolving the anvil is the first
        // thing the dispatch does, so a fixture that left them unset would test nothing at all.
        generalConfig = mock(GeneralConfig.class);
        lenient().when(generalConfig.getRepairAnvilMaterialName()).thenReturn("IRON_BLOCK");
        lenient().when(generalConfig.getSalvageAnvilMaterialName()).thenReturn("GOLD_BLOCK");
        McMMOMod.setGeneralConfig(generalConfig);

        // An iron chestplate is both repairable and salvageable; every other item resolves to null,
        // which is what "mcMMO does not work on this" looks like to the dispatch.
        final RepairableManager repairables = mock(RepairableManager.class);
        lenient().when(repairables.getRepairable("iron_chestplate"))
                .thenReturn(mock(Repairable.class));
        McMMOMod.setRepairableManager(repairables);

        final SalvageableManager salvageables = mock(SalvageableManager.class);
        lenient().when(salvageables.getSalvageable("iron_chestplate"))
                .thenReturn(mock(Salvageable.class));
        McMMOMod.setSalvageableManager(salvageables);

        world = mock(Level.class);
        placeAnvil(Blocks.STONE);
    }

    @AfterEach
    void tearDown() {
        if (mmoPlayer != null) {
            UserManager.cleanupPlayer(mmoPlayer);
            mmoPlayer = null;
        }
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRepairableManager(null);
        McMMOMod.setSalvageableManager(null);
    }

    // --- the reported bug -----------------------------------------------------

    /**
     * The regression guard. A client-side fire over the repair anvil holding repairable armour has to
     * claim the click; answering {@code PASS} there is what let vanilla equip the armour mid-repair.
     */
    @Test
    void clientSideFireOnTheRepairAnvilClaimsTheClick() {
        placeAnvil(Blocks.IRON_BLOCK);

        final InteractionResult result = RepairSalvageListener.onUseBlock(clientPlayer(damagedChestplate()),
                world, InteractionHand.MAIN_HAND, anvilHit());

        assertEquals(InteractionResult.SUCCESS, result,
                "the client fire must claim the anvil click, or the client falls through to "
                        + "\"use item\" and equips the armour being repaired");
    }

    /** The salvage anvil is the same story on the same seam, so it gets the same guard. */
    @Test
    void clientSideFireOnTheSalvageAnvilClaimsTheClick() {
        placeAnvil(Blocks.GOLD_BLOCK);

        final InteractionResult result = RepairSalvageListener.onUseBlock(clientPlayer(damagedChestplate()),
                world, InteractionHand.MAIN_HAND, anvilHit());

        assertEquals(InteractionResult.SUCCESS, result);
    }

    // --- the boundary of the claim -------------------------------------------

    /**
     * An iron block is an ordinary building block. Claiming clicks made over one with an item mcMMO
     * does not repair would break equipping armour, eating, and casting a rod anywhere near it — a
     * worse bug than the one being fixed.
     */
    @Test
    void clientSideFireWithAnItemMcmmoDoesNotWorkOnPasses() {
        placeAnvil(Blocks.IRON_BLOCK);

        final InteractionResult result = RepairSalvageListener.onUseBlock(
                clientPlayer(new ItemStack(Items.GOLDEN_APPLE)), world, InteractionHand.MAIN_HAND, anvilHit());

        assertEquals(InteractionResult.PASS, result);
    }

    /** An empty hand over the anvil is not an anvil action either. */
    @Test
    void clientSideFireWithAnEmptyHandPasses() {
        placeAnvil(Blocks.IRON_BLOCK);

        final InteractionResult result = RepairSalvageListener.onUseBlock(clientPlayer(ItemStack.EMPTY),
                world, InteractionHand.MAIN_HAND, anvilHit());

        assertEquals(InteractionResult.PASS, result);
    }

    /** Holding repairable gear is not enough — the block has to be one of mcMMO's anvils. */
    @Test
    void clientSideFireOnAnOrdinaryBlockPasses() {
        placeAnvil(Blocks.STONE);

        final InteractionResult result = RepairSalvageListener.onUseBlock(clientPlayer(damagedChestplate()),
                world, InteractionHand.MAIN_HAND, anvilHit());

        assertEquals(InteractionResult.PASS, result);
    }

    /** The off-hand dispatch stays ignored, so one right-click cannot arm the confirmation twice. */
    @Test
    void offHandFirePasses() {
        placeAnvil(Blocks.IRON_BLOCK);

        final InteractionResult result = RepairSalvageListener.onUseBlock(clientPlayer(damagedChestplate()),
                world, InteractionHand.OFF_HAND, anvilHit());

        assertEquals(InteractionResult.PASS, result);
    }

    // --- the server side still does the work ---------------------------------

    /**
     * The server-side fire must still reach the repair flow: the client's claim is only a suppression
     * of vanilla's fall-through, and if this dispatch were lost the click would become a no-op that
     * looks exactly like the fix working.
     */
    @Test
    void serverSideFireOnTheRepairAnvilArmsTheConfirmation() {
        placeAnvil(Blocks.IRON_BLOCK);
        final ServerPlayer player = trackedServerPlayer(damagedChestplate());
        // First click of the pair: arms + prompts, repairs nothing.
        when(repairManager.checkConfirmation(true)).thenReturn(false);

        final InteractionResult result =
                RepairSalvageListener.onUseBlock(player, world, InteractionHand.MAIN_HAND, anvilHit());

        verify(repairManager).checkConfirmation(true);
        assertEquals(InteractionResult.SUCCESS, result,
                "the click is claimed whether it repaired or merely armed");
    }

    // --- fixture -------------------------------------------------------------

    /** Put {@code block} at {@link #ANVIL_POS} as far as the dispatch can tell. */
    private void placeAnvil(Block block) {
        lenient().when(world.getBlockState(ANVIL_POS)).thenReturn(block.getDefaultState());
    }

    /** A right-click landing on the top face of the block at {@link #ANVIL_POS}. */
    private static BlockHitResult anvilHit() {
        return new BlockHitResult(Vec3.ofCenter(ANVIL_POS), Direction.UP, ANVIL_POS, false);
    }

    /** Damaged gear: what a player actually walks up to the anvil holding. */
    private static ItemStack damagedChestplate() {
        final ItemStack stack = new ItemStack(Items.IRON_CHESTPLATE);
        stack.setDamage(100);
        return stack;
    }

    /**
     * The client-side fire's player: a {@link PlayerEntity} that is <em>not</em> a
     * {@link ServerPlayerEntity}, which is precisely how the listener tells the two fires apart.
     */
    private static Player clientPlayer(ItemStack mainHand) {
        final Player player = mock(Player.class);
        lenient().when(player.getMainHandStack()).thenReturn(mainHand);
        return player;
    }

    /** A server-side player with an mcMMO profile behind them, tracked in {@link UserManager}. */
    private ServerPlayer trackedServerPlayer(ItemStack mainHand) {
        final UUID uuid = UUID.randomUUID();

        final ServerPlayer player = mock(ServerPlayer.class);
        lenient().when(player.getUuid()).thenReturn(uuid);
        lenient().when(player.getMainHandStack()).thenReturn(mainHand);

        repairManager = mock(RepairManager.class);
        final PlatformPlayer platformPlayer = mock(PlatformPlayer.class);
        lenient().when(platformPlayer.getUniqueId()).thenReturn(uuid);
        mmoPlayer = mock(McMMOPlayer.class);
        lenient().when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        lenient().when(mmoPlayer.getRepairManager()).thenReturn(repairManager);
        UserManager.track(mmoPlayer);
        return player;
    }
}
