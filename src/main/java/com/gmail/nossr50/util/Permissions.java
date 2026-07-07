package com.gmail.nossr50.util;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.platform.PlatformPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Singleplayer replacement for mcMMO's Bukkit permission gate.
 *
 * <p>The Bukkit plugin drove hundreds of {@code mcmmo.*} permission nodes through a
 * {@code Permissible} (a permissions plugin, op status, etc.). Singleplayer has no permission
 * backend, so — per the Phase 6 decision — permission checks collapse to fixed answers: gameplay
 * checks default to "allowed" and the opt-in "perk" nodes default to "not granted".
 *
 * <p>Only the surface the currently-ported code needs lives here; the remaining nodes
 * ({@code isSubSkillEnabled}, activation/tool perks, XP perks, etc.) get ported the same way as the
 * skills that reference them land. PORT Phase 6 — revisit if a config toggle should back any of these.
 */
public final class Permissions {

    private Permissions() {}

    /**
     * The "lucky" perk boosted a player's skill RNG by a fixed multiplier. It was gated behind the
     * {@code mcmmo.perks.lucky.<skill>} permission node, which no player holds in singleplayer, so
     * luck is never applied.
     *
     * @param player the player (unused — retained to mirror the legacy call sites)
     * @param skill the skill the luck perk would apply to
     * @return always {@code false} — no perk-permission backend in singleplayer
     */
    public static boolean lucky(@Nullable PlatformPlayer player, @NotNull PrimarySkillType skill) {
        return false;
    }
}
