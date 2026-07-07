package com.gmail.nossr50.skills.archery;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;

/**
 * Archery skill manager (Phase 10.3 port). Only the Skill Shot damage path and the two "can use"
 * unlock gates survive — see {@link Archery} for the numeric core.
 *
 * <p>Dropped until the combat phase (they take raw Bukkit entities/projectiles that need a
 * platform/ adapter):
 * <ul>
 *   <li>{@code canDaze}/{@code daze} — Daze only targets another {@code Player}; singleplayer has
 *       no other players, so it is effectively dead. It also teleports/pitches the defender and
 *       applies a nausea potion, all Bukkit-only.</li>
 *   <li>{@code distanceXpBonusMultiplier} — reads fired-location projectile metadata.</li>
 *   <li>{@code retrieveArrows} — mutates projectile metadata and feeds the arrow tracker.</li>
 * </ul>
 */
public class ArcheryManager extends SkillManager {
    public ArcheryManager(McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.ARCHERY);
    }

    public boolean canSkillShot() {
        if (!RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.ARCHERY_SKILL_SHOT)) {
            return false;
        }

        return Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.ARCHERY_SKILL_SHOT);
    }

    public boolean canRetrieveArrows() {
        if (!RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.ARCHERY_ARROW_RETRIEVAL)) {
            return false;
        }

        return Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.ARCHERY_ARROW_RETRIEVAL);
    }

    /**
     * Calculates the damage to deal after Skill Shot has been applied.
     *
     * @param oldDamage the raw damage value of this arrow before we modify it
     * @return the boosted damage if Skill Shot activates, otherwise {@code oldDamage} unchanged
     */
    public double skillShot(double oldDamage) {
        if (ProbabilityUtil.isNonRNGSkillActivationSuccessful(SubSkillType.ARCHERY_SKILL_SHOT,
                mmoPlayer)) {
            return Archery.getSkillShotBonusDamage(getPlayer(), oldDamage);
        } else {
            return oldDamage;
        }
    }
}
