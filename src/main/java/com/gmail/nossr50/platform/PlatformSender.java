package com.gmail.nossr50.platform;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Adapter over a Brigadier {@link ServerCommandSource}, replacing {@code org.bukkit.command
 * .CommandSender} (73 references). Mined usage is tiny: {@code sendMessage} (107) and
 * {@code getName} (9), so the wrapper stays small.
 *
 * <p>A source may or may not be a player (console/command block). {@link #getPlayer()} exposes
 * the player when present, mirroring Bukkit's {@code sender instanceof Player} checks.
 *
 * <p>Permissions: 1.21.11 replaced integer permission levels with a {@code PermissionPredicate}
 * on the source ({@code getPermissions()} / {@code withPermissions()}). Bukkit's
 * {@code hasPermission(String)} / {@code isOp()} are remapped in CONVERSION_TODO.md Phase 6
 * (op-level / config toggle / always-allow); not modeled here yet — use {@link #unwrap()}.
 */
public final class PlatformSender {

    private final ServerCommandSource handle;

    public PlatformSender(@NotNull ServerCommandSource handle) {
        this.handle = handle;
    }

    /** The wrapped Brigadier source. */
    public @NotNull ServerCommandSource unwrap() {
        return handle;
    }

    /** Display name of the source (player name, "Server", command-block name, ...). */
    public @NotNull String getName() {
        return handle.getName();
    }

    /** Bukkit {@code sendMessage}: non-broadcast chat feedback. Text is the locked target type. */
    public void sendMessage(@NotNull Text message) {
        handle.sendFeedback(() -> message, false);
    }

    /** Error-styled feedback (Bukkit red {@code sendMessage} convention). */
    public void sendError(@NotNull Text message) {
        handle.sendError(message);
    }

    /** Whether this source is a player (Bukkit {@code sender instanceof Player}). */
    public boolean isPlayer() {
        return handle.isExecutedByPlayer();
    }

    /** The player behind this source, or {@code null} for console/command-block sources. */
    public @Nullable PlatformPlayer getPlayer() {
        final ServerPlayerEntity player = handle.getPlayer();
        return player == null ? null : new PlatformPlayer(player);
    }
}
