package com.gmail.nossr50.platform;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import net.minecraft.server.network.ServerPlayerEntity;
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
