package com.gmail.nossr50.util;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
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

    /**
     * Whether a given sub-skill is enabled for a player. In the Bukkit plugin this gated each
     * sub-skill behind an {@code mcmmo.ability.<skill>.<subskill>} permission node. Per the Phase 6
     * decision, gameplay checks default to "allowed" in singleplayer — the lone player always has
     * every sub-skill enabled.
     *
     * @param player the player (unused — retained to mirror the legacy call sites)
     * @param subSkillType the sub-skill being checked
     * @return always {@code true} — no permission backend in singleplayer
     */
    public static boolean isSubSkillEnabled(@Nullable PlatformPlayer player,
            @NotNull SubSkillType subSkillType) {
        return true;
    }

    /**
     * Whether a player may use a given sub-skill. The Bukkit plugin gated this on the
     * {@code mcmmo.ability.<skill>.<subskill>} node; per the Phase 6 decision gameplay checks
     * default to "allowed" in singleplayer. Distinct from {@link #isSubSkillEnabled} only in the
     * legacy node it mirrored (Maces/Spears call sites used this variant).
     *
     * @param player the player (unused — retained to mirror the legacy call sites)
     * @param subSkillType the sub-skill being checked
     * @return always {@code true} — no permission backend in singleplayer
     */
    public static boolean canUseSubSkill(@Nullable PlatformPlayer player,
            @NotNull SubSkillType subSkillType) {
        return true;
    }

    /**
     * Berserk super-ability activation ({@code mcmmo.ability.unarmed.berserk}). A gameplay
     * activation check, so it defaults to "allowed" in singleplayer.
     *
     * @param player the player (unused — retained to mirror the legacy call sites)
     * @return always {@code true} — no permission backend in singleplayer
     */
    public static boolean berserk(@Nullable PlatformPlayer player) {
        return true;
    }

    /**
     * Serrated Strikes super-ability activation ({@code mcmmo.ability.swords.serratedstrikes}).
     * A gameplay activation check, so it defaults to "allowed" in singleplayer.
     *
     * @param player the player (unused — retained to mirror the legacy call sites)
     * @return always {@code true} — no permission backend in singleplayer
     */
    public static boolean serratedStrikes(@Nullable PlatformPlayer player) {
        return true;
    }

    /**
     * Skull Splitter super-ability activation ({@code mcmmo.ability.axes.skullsplitter}). A
     * gameplay activation check, so it defaults to "allowed" in singleplayer.
     *
     * @param player the player (unused — retained to mirror the legacy call sites)
     * @return always {@code true} — no permission backend in singleplayer
     */
    public static boolean skullSplitter(@Nullable PlatformPlayer player) {
        return true;
    }

    /**
     * Demolitions Expertise sub-skill ({@code mcmmo.ability.mining.demolitionsexpertise}), which
     * reduces Blast Mining self-damage. A gameplay check, so it defaults to "allowed".
     *
     * @param player the player (unused — retained to mirror the legacy call sites)
     * @return always {@code true} — no permission backend in singleplayer
     */
    public static boolean demolitionsExpertise(@Nullable PlatformPlayer player) {
        return true;
    }

    /**
     * Bigger Bombs sub-skill ({@code mcmmo.ability.mining.biggerbombs}), which widens the Blast
     * Mining radius. A gameplay check, so it defaults to "allowed".
     *
     * @param player the player (unused — retained to mirror the legacy call sites)
     * @return always {@code true} — no permission backend in singleplayer
     */
    public static boolean biggerBombs(@Nullable PlatformPlayer player) {
        return true;
    }

    /**
     * Blast Mining remote detonation ({@code mcmmo.ability.mining.blastmining.detonate}). A gameplay
     * activation check, so it defaults to "allowed" in singleplayer.
     *
     * @param player the player (unused — retained to mirror the legacy call sites)
     * @return always {@code true} — no permission backend in singleplayer
     */
    public static boolean remoteDetonation(@Nullable PlatformPlayer player) {
        return true;
    }

    /**
     * Green Terra super-ability activation ({@code mcmmo.ability.herbalism.greenterra}). A
     * gameplay activation check, so it defaults to "allowed" in singleplayer.
     *
     * @param player the player (unused — retained to mirror the legacy call sites)
     * @return always {@code true} — no permission backend in singleplayer
     */
    public static boolean greenTerra(@Nullable PlatformPlayer player) {
        return true;
    }

    /**
     * The Salvage enchant-bypass perk ({@code mcmmo.perks.bypass.salvageenchant}), which guaranteed
     * full enchant extraction. Like {@link #lucky}, it's an opt-in perk node no player holds in
     * singleplayer, so it never applies.
     *
     * @param player the player (unused — retained to mirror the legacy call sites)
     * @return always {@code false} — no perk-permission backend in singleplayer
     */
    public static boolean hasSalvageEnchantBypassPerk(@Nullable PlatformPlayer player) {
        return false;
    }
}
