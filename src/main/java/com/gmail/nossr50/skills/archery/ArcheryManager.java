package com.gmail.nossr50.skills.archery;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.platform.MetadataStore;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

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
 *   <li>{@code distanceXpBonusMultiplier} — reads fired-location projectile metadata, and feeds a
 *       <em>per-hit</em> XP multiplier this port has no consumer for (combat XP is paid per kill).</li>
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
     * Rolls Arrow Retrieval at <em>launch</em> time, deciding whether this arrow is retrievable if it
     * later strikes something (legacy {@code EntityListener#onProjectileLaunch}).
     *
     * <p>Deliberately <em>not</em> gated by {@link #canRetrieveArrows()}: legacy rolls here but checks
     * the rank/permission gate at hit time instead, so a player who ranks up mid-flight can still
     * collect an arrow they were not yet entitled to when they loosed it. Faithful, and the ordering
     * costs nothing — an unclaimed mark is simply never read.
     *
     * @return whether this arrow should be marked as retrievable
     */
    public boolean rollArrowRetrieval() {
        return ProbabilityUtil.isSkillRNGSuccessful(SubSkillType.ARCHERY_ARROW_RETRIEVAL, mmoPlayer);
    }

    /**
     * Credits a struck entity with one retrievable arrow, if the arrow that hit it was marked at
     * launch (legacy {@code retrieveArrows}). The arrows are handed out when the entity dies — see
     * {@link Archery#arrowRetrievalCheck}.
     *
     * <p>The mark is cleared on the first hit, which is legacy's "only 1 entity per projectile" rule:
     * a Piercing arrow passing through a line of mobs must not credit each of them.
     *
     * <p>Both sides are addressed by {@link UUID} rather than platform entities: the mark and the
     * count both live on the UUID-keyed {@code MetadataStore}, so nothing here needs a live entity and
     * the whole cycle stays unit-testable outside the Knot harness.
     *
     * @param targetId     the entity the arrow struck
     * @param projectileId the arrow that struck it
     */
    public void retrieveArrows(@NotNull UUID targetId, @NotNull UUID projectileId) {
        if (!MetadataStore.has(projectileId, Archery.TRACKED_ARROW_KEY)) {
            return;
        }
        Archery.incrementTrackerValue(targetId);
        MetadataStore.remove(projectileId, Archery.TRACKED_ARROW_KEY);
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
