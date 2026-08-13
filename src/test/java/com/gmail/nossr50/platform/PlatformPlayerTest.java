package com.gmail.nossr50.platform;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link PlatformPlayer#rebind} — the fix for a session-long stale-handle bug.
 *
 * <p>A {@link PlatformPlayer} is built once per login and handed to the player's {@code McMMOPlayer},
 * every skill manager, and every scheduled ability task. But vanilla's
 * {@code PlayerManager#respawnPlayer} does not reuse the {@link ServerPlayerEntity}: it calls
 * {@code ServerWorld.removePlayer(old, reason)} and constructs a replacement (bytecode-verified
 * against 1.21.11), on both the death path and the End-exit path. Without a rebind, every MC-typed
 * call for the rest of the session — sounds, notifications, main-hand reads, the Super/Giga Breaker
 * dig-boost sweep — targets a removed entity and silently does nothing.
 *
 * <p>Runs under the {@code fabric-loader-junit} registry harness because mocking a
 * {@link ServerPlayerEntity} loads the entity class hierarchy.
 */
class PlatformPlayerTest {

    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID OTHER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    @BeforeAll
    static void bootstrapRegistries() {
        com.gmail.nossr50.util.McTestRegistries.bootstrap();
    }

    private static ServerPlayerEntity entity(UUID uuid, String name) {
        final ServerPlayerEntity handle = mock(ServerPlayerEntity.class);
        when(handle.getUuid()).thenReturn(uuid);
        when(handle.getName()).thenReturn(Text.literal(name));
        return handle;
    }

    /**
     * Pins {@link PlatformPlayer#toVanilla} — the single seam where mcMMO's Minecraft-free
     * {@link PlatformSoundCategory} meets vanilla's enum (Phase 2 of multi-version support).
     *
     * <p>The mapping is eleven hand-written switch arms, and a copy-paste slip in any one of them
     * (say {@code case VOICE -> SoundCategory.AMBIENT}) is completely silent: the sound still plays,
     * just on the wrong volume slider, which no other test and no boot check would notice.
     *
     * <p>So this asserts the <em>property</em> rather than re-listing the table: every platform
     * constant must map to the vanilla constant of the <b>same name</b>. Driving it from
     * {@code values()} — never a hard-coded list — is what makes it catch a newly added constant
     * too, and keeps it from going vacuous the way a table-driven guard does.
     *
     * <p><b>BAND: the mirror enum is deliberately WIDER than this band's vanilla.</b> Vanilla gained
     * a {@code UI} category later, and {@link PlatformSoundCategory} keeps it on every band so that
     * MC-free skill code compiles identically everywhere — shrinking the mirror per band would move
     * the break out of {@code platform/} and into skill code, which is the one thing Phase 2's
     * boundary exists to prevent. {@code PlatformPlayer#toVanilla} therefore maps a category this
     * band's vanilla does not have onto {@code MASTER}, which is what vanilla itself did before the
     * separate slider existed. The same-name rule still governs every category that <em>does</em>
     * exist here, so this test enforces it for those and pins the deliberate exception for the rest —
     * rather than being weakened to "maps to something".
     */
    @Test
    void everyPlatformSoundCategoryMapsToTheVanillaConstantOfTheSameName() {
        int matchedByName = 0;
        for (final PlatformSoundCategory category : PlatformSoundCategory.values()) {
            final SoundCategory sameName = vanillaNamed(category.name());
            if (sameName == null) {
                assertSame(SoundCategory.MASTER, PlatformPlayer.toVanilla(category),
                        "vanilla has no SoundCategory." + category.name() + " on this Minecraft "
                                + "version, so PlatformSoundCategory." + category.name()
                                + " must fall to MASTER — the slider vanilla itself used before that "
                                + "category existed — and never to some other arbitrary category");
                continue;
            }
            matchedByName++;
            assertSame(sameName, PlatformPlayer.toVanilla(category),
                    "PlatformSoundCategory." + category.name()
                            + " must map to vanilla SoundCategory." + category.name()
                            + " — a mis-mapped arm silently plays mcMMO's sounds on the wrong "
                            + "volume slider");
        }

        // Anti-vacuity, and the reason the loop above is not just "maps to anything": if the by-name
        // lookup were broken, every category would take the MASTER arm and the same-name rule would
        // go untested. Every vanilla category must have been reached through it.
        assertEquals(SoundCategory.values().length, matchedByName,
                "the same-name rule was only exercised for " + matchedByName + " of "
                        + SoundCategory.values().length + " vanilla categories. The rest fell to the "
                        + "band exception, which means this test is no longer proving the mapping.");
    }

    /** This band's vanilla constant of that name, or {@code null} if the version has none. */
    private static SoundCategory vanillaNamed(String name) {
        for (final SoundCategory vanilla : SoundCategory.values()) {
            if (vanilla.name().equals(name)) {
                return vanilla;
            }
        }
        return null;
    }

    /**
     * The converse of the test above, and the reason it is not vacuous: it only proves the mapping
     * is <em>total</em> if the two enums have the same constants in the first place. A vanilla
     * category that mcMMO never mirrored (as {@code UI} nearly was — it is easy to forget and
     * {@code javap} is the only reliable way to enumerate them) would leave a category unreachable
     * from skill code without anything failing.
     */
    @Test
    void theMirrorEnumCoversEveryVanillaSoundCategory() {
        for (final SoundCategory vanilla : SoundCategory.values()) {
            assertDoesNotThrow(() -> PlatformSoundCategory.valueOf(vanilla.name()),
                    "vanilla SoundCategory." + vanilla.name()
                            + " has no PlatformSoundCategory mirror — skill code cannot name it");
        }
    }

    @Test
    void rebindSwapsInTheReplacementEntityForTheSamePlayer() {
        final ServerPlayerEntity beforeDeath = entity(PLAYER_ID, "Steve");
        final ServerPlayerEntity afterRespawn = entity(PLAYER_ID, "Steve");
        final PlatformPlayer player = new PlatformPlayer(beforeDeath);

        player.rebind(afterRespawn);

        assertSame(afterRespawn, player.unwrap(),
                "after a respawn the wrapper must point at the entity vanilla just built");
    }

    @Test
    void rebindKeepsTheWrapperIdentitySoCapturedReferencesKeepWorking() {
        final PlatformPlayer player = new PlatformPlayer(entity(PLAYER_ID, "Steve"));
        // Stands in for AbilityCooldownTask / AbilityDisableTask, which capture this object directly
        // and must keep working across a death that happens mid-ability.
        final PlatformPlayer capturedByAScheduledTask = player;

        player.rebind(entity(PLAYER_ID, "Steve"));

        assertSame(player.unwrap(), capturedByAScheduledTask.unwrap(),
                "rebinding in place, not rebuilding, is what keeps scheduled tasks live");
    }

    @Test
    void rebindRefusesAnEntityBelongingToADifferentPlayer() {
        final ServerPlayerEntity original = entity(PLAYER_ID, "Steve");
        final PlatformPlayer player = new PlatformPlayer(original);

        player.rebind(entity(OTHER_ID, "Alex"));

        assertSame(original, player.unwrap(),
                "a mis-wired caller must not redirect one player's skill side effects onto another");
    }
}
