package com.gmail.nossr50.fabric.listeners;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.player.PlayerProfile;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.util.McTestRegistries;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers {@link SuperAbilityListener#offhandBlocksActivation} — upstream's off-hand rule, and the
 * switch that now decides whether it runs at all.
 *
 * <p><b>The bug this file exists for.</b> A player was found on 2026-08-06 with 33 torches in their
 * off hand and <em>every super ability in the mod</em> silently dead — for four days, across four
 * sessions, with nothing in any log to say why. Readying is step 1 of 2, and
 * {@code McMMOPlayer#checkAbilityActivation} is only ever reached through
 * {@code getToolPreparationMode(tool)}, so this single condition takes out Super Breaker, Giga Drill
 * Breaker, Tree Feller, Green Terra, Berserk, Serrated Strikes and Skull Splitter <b>at once</b>,
 * from both the block arm and the air arm. Upstream's rule is aimed at an off-hand shield-raise or
 * food-eat arming a tool by accident; a torch in the off hand is simply how mining is done.
 *
 * <p><b>What is actually hard here.</b> Not the switch. It is that the rule can only be judged
 * against the <i>shipped default</i> — a getter default nobody exercises is how the last four of
 * these defects shipped — so {@link #shippedDefaultLetsAnOffhandTorchReadyTools()} runs against a
 * real {@link GeneralConfig} loaded from the bundled {@code config.yml} rather than a mock that
 * would happily agree with whatever the code does.
 */
class SuperAbilityListenerOffhandTest {

    private static final UUID UID = UUID.fromString("00000000-0000-0000-0000-0000000000cd");

    private GeneralConfig config;

    @BeforeAll
    static void bootstrap() {
        McTestRegistries.bootstrap();
    }

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        // A real config over a temp dir, so it holds the values config.yml actually ships; spied so
        // enableRule() can flip the one key without a mock that would agree with anything.
        config = spy(new GeneralConfig(dataFolder));
        McMMOMod.setGeneralConfig(config);
        // Constructing an McMMOPlayer builds its skill managers, which read these two.
        McMMOMod.setExperienceConfig(new ExperienceConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setExperienceConfig(null);
        McMMOMod.setAdvancedConfig(null);
    }

    // --- the fix: the shipped default ---------------------------------------

    @Test
    void shippedDefaultLetsAnOffhandTorchReadyTools() {
        // ⚠️ THE regression. This is the exact live state that broke: a torch in the off hand, both
        // feet on the ground, not sneaking. Under upstream's rule that is a hard "no" to readying
        // anything, forever, with no feedback.
        assertFalse(SuperAbilityListener.offhandBlocksActivation(
                        player(Items.DIAMOND_PICKAXE, Items.TORCH, false, false)),
                "with the shipped default an off-hand torch must not block readying — that one "
                        + "condition switches off every super ability in the mod, silently");
    }

    @Test
    void shippedDefaultIsOff() {
        // Pinned separately from the behaviour above so that flipping the shipped value fails here
        // with a message about the DEFAULT, not about torches.
        assertFalse(config.getOffhandBlocksReadying(),
                "Abilities.Activation.Offhand_Blocks_Readying must ship false — see GeneralConfig");
    }

    @Test
    void offhandNeverBlocksAnythingWhileTheSwitchIsOff() {
        // Not just torches: nothing in the off hand may block, or the fix is item-specific by
        // accident and a shield/food/map reopens the issue.
        for (net.minecraft.world.item.Item offhand : new net.minecraft.world.item.Item[] {
                Items.TORCH, Items.SHIELD, Items.COOKED_BEEF, Items.DIAMOND_PICKAXE, Items.MAP}) {
            assertFalse(SuperAbilityListener.offhandBlocksActivation(
                            player(Items.DIAMOND_PICKAXE, offhand, false, false)),
                    offhand + " in the off hand must not block readying while the switch is off");
        }
    }

    // --- the switch still buys you upstream's rule ---------------------------

    @Test
    void switchOnRestoresTheUpstreamRule() {
        enableRule();
        assertTrue(SuperAbilityListener.offhandBlocksActivation(
                        player(Items.DIAMOND_PICKAXE, Items.TORCH, false, false)),
                "with the switch on, upstream's rule must apply unchanged");
    }

    @Test
    void upstreamsTwoExemptionsSurviveTheSwitch() {
        enableRule();
        // Legacy's condition is `offHand != AIR && !isInsideVehicle() && !isSneaking()`. Dropping
        // either exemption while porting the switch would make the opt-in stricter than upstream.
        assertFalse(SuperAbilityListener.offhandBlocksActivation(
                        player(Items.DIAMOND_PICKAXE, Items.TORCH, true, false)),
                "sneaking is upstream's escape hatch from the off-hand rule");
        assertFalse(SuperAbilityListener.offhandBlocksActivation(
                        player(Items.DIAMOND_PICKAXE, Items.TORCH, false, true)),
                "riding something is upstream's other escape hatch from the off-hand rule");
    }

    @Test
    void anEmptyOffhandNeverBlocksEitherWay() {
        assertFalse(SuperAbilityListener.offhandBlocksActivation(
                emptyOffhand()));
        enableRule();
        assertFalse(SuperAbilityListener.offhandBlocksActivation(emptyOffhand()),
                "an empty off hand is not an off-hand item even under upstream's rule");
    }

    @Test
    void noConfigMeansNoRule() {
        // Between world sessions and in the headless boot the config is unbound. Failing closed here
        // would switch every super ability off in exactly the situation where nobody asked for it.
        McMMOMod.setGeneralConfig(null);
        assertFalse(SuperAbilityListener.offhandBlocksActivation(
                        player(Items.DIAMOND_PICKAXE, Items.TORCH, false, false)),
                "an unbound config must not resurrect the rule");
    }

    // --- ⚠️ the converse: the OFF hand can never ready anything ---------------

    @Test
    void aToolInTheOffHandReadiesNothing() {
        // The user's explicit ruling alongside the switch: mcMMO tools are read from the MAIN hand
        // only. Relaxing the block-rule must not turn the off-hand slot into a second tool slot, so
        // a pickaxe in the off hand with a torch in the main hand arms nothing.
        assertFalse(SuperAbilityListener.wouldHaveReadiedATool(
                        player(Items.TORCH, Items.DIAMOND_PICKAXE, false, false)),
                "a pickaxe in the OFF hand must not count as a readyable tool — readying reads the "
                        + "main hand only");
    }

    @Test
    void theMainHandStillDecidesWhatWouldHaveBeenReadied() {
        assertTrue(SuperAbilityListener.wouldHaveReadiedATool(
                player(Items.DIAMOND_PICKAXE, Items.TORCH, false, false)));
        assertTrue(SuperAbilityListener.wouldHaveReadiedATool(
                        player(null, Items.TORCH, false, false)),
                "an empty main hand is ToolType.FISTS — Berserk's ready, so it counts");
        assertFalse(SuperAbilityListener.wouldHaveReadiedATool(
                        player(Items.COBBLESTONE, Items.TORCH, false, false)),
                "a block in the main hand would never have readied anything, so claiming the off "
                        + "hand blocked a ready would be a lie");
    }

    // --- the hint throttle ---------------------------------------------------

    @Test
    void theHintSpeaksOnceThenStaysQuietForFiveMinutes() {
        // Un-throttled this fires on EVERY right-click — one message per torch placed. That is the
        // failure mode that makes players mute the channel, which makes the mechanic silent again.
        final McMMOPlayer mmoPlayer = mmoPlayer();
        final long start = 1_000_000L;

        assertTrue(mmoPlayer.claimOffhandBlockedHint(start), "the first blocked ready must speak");
        assertFalse(mmoPlayer.claimOffhandBlockedHint(start + 1));
        assertFalse(mmoPlayer.claimOffhandBlockedHint(start + 299_999L));
        assertTrue(mmoPlayer.claimOffhandBlockedHint(start + 300_000L),
                "after the interval it may speak again");
    }

    @Test
    void aFreshPlayerAlwaysGetsTheFirstHint() {
        // The zero-init sentinel: a player whose session starts at any clock value must still get
        // hint number one, or the very first person to hit this learns nothing.
        assertTrue(mmoPlayer().claimOffhandBlockedHint(0L));
        assertTrue(mmoPlayer().claimOffhandBlockedHint(System.currentTimeMillis()));
    }

    // --- helpers ------------------------------------------------------------

    /**
     * Turn upstream's rule on. A spy over the <em>real</em> config rather than a mock of it, so every
     * other getter this path touches keeps answering with the shipped value — {@code config.config}
     * is package-protected on {@code ConfigLoader} and out of reach from here.
     */
    private void enableRule() {
        doReturn(true).when(config).getOffhandBlocksReadying();
    }

    private static McMMOPlayer mmoPlayer() {
        final PlatformPlayer platformPlayer = mock(PlatformPlayer.class);
        when(platformPlayer.getName()).thenReturn("TestPlayer");
        when(platformPlayer.getUniqueId()).thenReturn(UID);
        return new McMMOPlayer(platformPlayer, new PlayerProfile("TestPlayer", UID, 0));
    }

    private static ServerPlayer emptyOffhand() {
        return player(Items.DIAMOND_PICKAXE, null, false, false);
    }

    /** @param mainHand / {@code offHand} {@code null} for an empty slot. */
    private static ServerPlayer player(net.minecraft.world.item.Item mainHand,
            net.minecraft.world.item.Item offHand, boolean sneaking, boolean riding) {
        final ServerPlayer player = mock(ServerPlayer.class);
        when(player.getMainHandStack())
                .thenReturn(mainHand == null ? ItemStack.EMPTY : new ItemStack(mainHand));
        when(player.getOffHandStack())
                .thenReturn(offHand == null ? ItemStack.EMPTY : new ItemStack(offHand));
        when(player.isSneaking()).thenReturn(sneaking);
        when(player.hasVehicle()).thenReturn(riding);
        return player;
    }
}
