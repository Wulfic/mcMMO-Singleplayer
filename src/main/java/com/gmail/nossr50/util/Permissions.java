package com.gmail.nossr50.util;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.util.skills.SkillGating;
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
 * <p><b>GitHub #10 took the "revisit if a config toggle should back any of these" up.</b> The
 * gameplay checks are no longer unconditionally {@code true}: they now answer to the per-skill
 * master switch in {@code coreskills.yml} via {@link SkillGating}, which is the closest thing
 * singleplayer has to the {@code mcmmo.ability.<skill>.<subskill>} node they were ported from — one
 * switch per skill rather than per node, because that is what the issue asked for. The <em>perk</em>
 * nodes ({@link #lucky}, the bypasses) stay hard {@code false}: they were never grantable here, and a
 * skill being switched off cannot make one grantable.
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
     * sub-skill behind an {@code mcmmo.ability.<skill>.<subskill>} permission node. Singleplayer has no permission backend, so
     * this now answers to the parent skill's master switch in {@code coreskills.yml} (GitHub #10):
     * allowed unless the player switched the whole skill off.
     *
     * @param player the player (unused — retained to mirror the legacy call sites)
     * @param subSkillType the sub-skill being checked
     * @return {@code true} unless the parent skill is switched off in {@code coreskills.yml}
     */
    public static boolean isSubSkillEnabled(@Nullable PlatformPlayer player,
            @NotNull SubSkillType subSkillType) {
        return SkillGating.isSubSkillEnabled(subSkillType);
    }

    /**
     * Whether a player may use a given sub-skill. The Bukkit plugin gated this on the
     * {@code mcmmo.ability.<skill>.<subskill>} node; singleplayer has no permission backend, so it
     * answers to the parent skill's master switch (GitHub #10). Distinct from
     * {@link #isSubSkillEnabled} only in the legacy node it mirrored (Maces/Spears used this variant).
     *
     * @param player the player (unused — retained to mirror the legacy call sites)
     * @param subSkillType the sub-skill being checked
     * @return {@code true} unless the parent skill is switched off in {@code coreskills.yml}
     */
    public static boolean canUseSubSkill(@Nullable PlatformPlayer player,
            @NotNull SubSkillType subSkillType) {
        return SkillGating.isSubSkillEnabled(subSkillType);
    }

    /**
     * Berserk super-ability activation ({@code mcmmo.ability.unarmed.berserk}). A gameplay check:
     * allowed unless the skill is switched off in {@code coreskills.yml}.
     *
     * @param player the player (unused — retained to mirror the legacy call sites)
     * @return {@code true} unless the skill is switched off in {@code coreskills.yml}
     */
    public static boolean berserk(@Nullable PlatformPlayer player) {
        return SkillGating.isSkillEnabled(PrimarySkillType.UNARMED);
    }

    /**
     * Serrated Strikes super-ability activation ({@code mcmmo.ability.swords.serratedstrikes}).
     * A gameplay check:
     * allowed unless the skill is switched off in {@code coreskills.yml}.
     *
     * @param player the player (unused — retained to mirror the legacy call sites)
     * @return {@code true} unless the skill is switched off in {@code coreskills.yml}
     */
    public static boolean serratedStrikes(@Nullable PlatformPlayer player) {
        return SkillGating.isSkillEnabled(PrimarySkillType.SWORDS);
    }

    /**
     * Skull Splitter super-ability activation ({@code mcmmo.ability.axes.skullsplitter}). A gameplay check:
     * allowed unless the skill is switched off in {@code coreskills.yml}.
     *
     * @param player the player (unused — retained to mirror the legacy call sites)
     * @return {@code true} unless the skill is switched off in {@code coreskills.yml}
     */
    public static boolean skullSplitter(@Nullable PlatformPlayer player) {
        return SkillGating.isSkillEnabled(PrimarySkillType.AXES);
    }

    /**
     * Demolitions Expertise sub-skill ({@code mcmmo.ability.mining.demolitionsexpertise}), which
     * reduces Blast Mining self-damage. A gameplay check:
     * allowed unless the skill is switched off in {@code coreskills.yml}.
     *
     * @param player the player (unused — retained to mirror the legacy call sites)
     * @return {@code true} unless the skill is switched off in {@code coreskills.yml}
     */
    public static boolean demolitionsExpertise(@Nullable PlatformPlayer player) {
        return SkillGating.isSkillEnabled(PrimarySkillType.MINING);
    }

    /**
     * Bigger Bombs sub-skill ({@code mcmmo.ability.mining.biggerbombs}), which widens the Blast
     * Mining radius. A gameplay check:
     * allowed unless the skill is switched off in {@code coreskills.yml}.
     *
     * @param player the player (unused — retained to mirror the legacy call sites)
     * @return {@code true} unless the skill is switched off in {@code coreskills.yml}
     */
    public static boolean biggerBombs(@Nullable PlatformPlayer player) {
        return SkillGating.isSkillEnabled(PrimarySkillType.MINING);
    }

    /**
     * Blast Mining remote detonation ({@code mcmmo.ability.mining.blastmining.detonate}). A gameplay check:
     * allowed unless the skill is switched off in {@code coreskills.yml}.
     *
     * @param player the player (unused — retained to mirror the legacy call sites)
     * @return {@code true} unless the skill is switched off in {@code coreskills.yml}
     */
    public static boolean remoteDetonation(@Nullable PlatformPlayer player) {
        return SkillGating.isSkillEnabled(PrimarySkillType.MINING);
    }

    /**
     * Green Terra super-ability activation ({@code mcmmo.ability.herbalism.greenterra}). A gameplay check:
     * allowed unless the skill is switched off in {@code coreskills.yml}.
     *
     * @param player the player (unused — retained to mirror the legacy call sites)
     * @return {@code true} unless the skill is switched off in {@code coreskills.yml}
     */
    public static boolean greenTerra(@Nullable PlatformPlayer player) {
        return SkillGating.isSkillEnabled(PrimarySkillType.HERBALISM);
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

    /**
     * The Repair enchant-bypass perk ({@code mcmmo.perks.bypass.repairenchant}), which exempted a
     * player from Arcane Forging's enchantment loss entirely. Like {@link #lucky}, it's an opt-in
     * perk node no player holds in singleplayer, so it never applies.
     *
     * @param player the player (unused — retained to mirror the legacy call sites)
     * @return always {@code false} — no perk-permission backend in singleplayer
     */
    public static boolean hasRepairEnchantBypassPerk(@Nullable PlatformPlayer player) {
        return false;
    }

    /**
     * Arcane Salvage sub-skill ({@code mcmmo.ability.salvage.arcanesalvage}), which extracts an
     * enchanted book from a salvaged item. A gameplay check:
     * allowed unless the skill is switched off in {@code coreskills.yml}.
     *
     * @param player the player (unused — retained to mirror the legacy call sites)
     * @return {@code true} unless the skill is switched off in {@code coreskills.yml}
     */
    public static boolean arcaneSalvage(@Nullable PlatformPlayer player) {
        return SkillGating.isSkillEnabled(PrimarySkillType.SALVAGE);
    }

    /**
     * The Arcane Forging bypass node ({@code mcmmo.bypass.arcanebypass}), which made a repair keep
     * every enchantment at full level regardless of Arcane Forging rank. An administrative bypass
     * rather than a gameplay check — nobody holds it in singleplayer, so it never applies and the
     * ordinary keep/downgrade/lose roll always runs.
     *
     * @param player the player (unused — retained to mirror the legacy call sites)
     * @return always {@code false} — no permission backend in singleplayer
     */
    public static boolean arcaneBypass(@Nullable PlatformPlayer player) {
        return false;
    }
}
