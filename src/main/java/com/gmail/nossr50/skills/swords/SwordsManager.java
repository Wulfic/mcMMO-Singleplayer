package com.gmail.nossr50.skills.swords;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.datatypes.skills.ToolType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.skills.RankUtils;

/**
 * Swords skill manager (Phase 10.3 port). Only the Stab damage math and the activation/unlock gates
 * survive; the effect bodies that touch Bukkit entities, metadata, or the Folia scheduler are
 * dropped until the combat/entity + metadata adapters land.
 *
 * <p>Dropped until the combat phase:
 * <ul>
 *   <li>{@code processRupture} / {@code canUseRupture}'s effect — starts a repeating bleed task
 *       ({@code RuptureTask}) tracked via entity metadata + the Folia scheduler, and notifies a
 *       {@code Player} defender;</li>
 *   <li>{@code counterAttackChecks} / {@code canUseCounterAttack} — deals reflected damage through
 *       {@code CombatUtils} against a raw {@code LivingEntity};</li>
 *   <li>{@code serratedStrikes} — the {@code CombatUtils} ability AoE.</li>
 * </ul>
 * The static {@code Swords} modifiers (counter/serrated) went with those bodies and were not ported.
 */
public class SwordsManager extends SkillManager {
    public SwordsManager(McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.SWORDS);
    }

    public boolean canActivateAbility() {
        return mmoPlayer.getToolPreparationMode(ToolType.SWORD) && Permissions.serratedStrikes(
                getPlayer());
    }

    public boolean canUseStab() {
        return Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.SWORDS_STAB)
                && RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.SWORDS_STAB);
    }

    public boolean canUseRupture() {
        return Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.SWORDS_RUPTURE)
                && RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.SWORDS_RUPTURE);
    }

    public boolean canUseSerratedStrike() {
        if (!RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.SWORDS_SERRATED_STRIKES)) {
            return false;
        }

        return mmoPlayer.getAbilityMode(SuperAbilityType.SERRATED_STRIKES);
    }

    public double getStabDamage() {
        int rank = RankUtils.getRank(getPlayer(), SubSkillType.SWORDS_STAB);

        if (rank > 0) {
            double baseDamage = McMMOMod.getAdvancedConfig().getStabBaseDamage();
            double rankMultiplier = McMMOMod.getAdvancedConfig().getStabPerRankMultiplier();
            return (baseDamage + (rank * rankMultiplier));
        }

        return 0;
    }
}
