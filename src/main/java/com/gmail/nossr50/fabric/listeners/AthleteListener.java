package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.agility.AgilityManager;
import com.gmail.nossr50.util.player.UserManager;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Agility → <b>Athlete</b>: sprinting costs less hunger.
 *
 * <p>Driven by {@link com.gmail.nossr50.fabric.mixin.HungerManagerExhaustionMixin} on
 * {@code HungerManager#addExhaustion}, which is the single funnel every exhaustion source in the
 * game passes through. That makes it the right place to hook and the wrong place to be careless:
 * mining, jumping, swimming, taking damage and natural regeneration all arrive here too, so the
 * discount is gated on the player <em>currently sprinting</em>. Without that gate Athlete would
 * quietly halve the hunger cost of playing the game at all.
 *
 * <p>{@link HungerManager} carries no back-reference to its owner, so the player is resolved by
 * identity against the online list. In singleplayer that is a one-element scan; it also doubles as
 * the server-side gate, since a client-side hunger manager matches nothing and is left alone (hunger
 * is server-authoritative and synced to the client, so discounting it twice would be wrong anyway).
 */
public final class AthleteListener {

    private AthleteListener() {
    }

    /**
     * Scale one exhaustion event by the player's Athlete bonus.
     *
     * @param hungerManager the manager the exhaustion is being applied to
     * @param exhaustion    the exhaustion vanilla wants to add
     * @return the reduced exhaustion, or {@code exhaustion} unchanged when Athlete does not apply
     */
    public static float scaleExhaustion(@NotNull HungerManager hungerManager, float exhaustion) {
        if (exhaustion <= 0) {
            return exhaustion;
        }
        final ServerPlayerEntity player = ownerOf(hungerManager);
        if (player == null || !player.isSprinting()) {
            return exhaustion;
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(player.getUuid());
        if (mmoPlayer == null) {
            return exhaustion;
        }
        final AgilityManager agility = mmoPlayer.getAgilityManager();
        if (agility == null) {
            return exhaustion;
        }
        return (float) (exhaustion * agility.getAthleteExhaustionMultiplier());
    }

    /**
     * The online player this hunger manager belongs to, or {@code null} if it belongs to none —
     * which covers the client-side manager and anything that runs outside a world session.
     */
    private static @Nullable ServerPlayerEntity ownerOf(@NotNull HungerManager hungerManager) {
        final MinecraftServer server = McMMOMod.getServer();
        if (server == null) {
            return null;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getHungerManager() == hungerManager) {
                return player;
            }
        }
        return null;
    }
}
