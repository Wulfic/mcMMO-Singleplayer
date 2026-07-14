package com.gmail.nossr50.skills.excavation;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.treasure.ExcavationTreasure;
import com.gmail.nossr50.datatypes.treasure.ItemSpec;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Excavation skill manager (Phase 10.4 port). Holds the MC-free excavation decisions: the
 * treasure-table lookup ({@link #getTreasures(String)}), the rank-driven Archaeology rewards, and the
 * treasure roll ({@link #rollTreasureRewards(String)} — legacy {@code excavationBlockCheck} +
 * {@code processExcavationBonusesOnBlock}, minus the base block XP the block-break listener already
 * awards). The MC-typed spawning of those treasures/orbs and the Giga Drill Breaker super ability
 * (legacy {@code gigaDrillBreaker}: two extra treasure rolls + shovel durability) live in
 * {@code BlockBreakListener}, which builds each {@link com.gmail.nossr50.datatypes.treasure.ItemSpec}
 * into a live stack via {@code platform/ItemSpecBuilder}.
 *
 * <p><b>Deferred:</b> {@code printExcavationDebug} (chat-dumps a block's treasures; needs a live
 * block and has no singleplayer command surface yet).
 */
public class ExcavationManager extends SkillManager {
    public ExcavationManager(McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.EXCAVATION);
    }

    /**
     * The treasures that could drop when excavating the given block.
     *
     * @param blockRegistryPath the block's vanilla registry path (e.g. {@code "dirt"})
     */
    public @NotNull List<ExcavationTreasure> getTreasures(@NotNull String blockRegistryPath) {
        return Excavation.getTreasures(blockRegistryPath);
    }

    /**
     * The MC-free outcome of rolling a broken block's Excavation treasure table (Phase 10.4
     * item-spawn): the {@link ItemSpec}s that dropped, the bonus Excavation XP they carry, and the
     * per-drop Archaeology experience-orb amounts to spawn. Kept MC-free so the roll stays
     * unit-testable; the caller ({@code BlockBreakListener}) builds each {@link ItemSpec} into a live
     * stack via {@code platform/ItemSpecBuilder} and spawns the orbs.
     */
    public record ExcavationRewards(@NotNull List<ItemSpec> treasures, int treasureXp,
            @NotNull List<Integer> experienceOrbs) {

        public static final ExcavationRewards EMPTY =
                new ExcavationRewards(List.of(), 0, List.of());

        /** Whether nothing dropped. */
        public boolean isEmpty() {
            return treasures.isEmpty() && experienceOrbs.isEmpty() && treasureXp == 0;
        }
    }

    /**
     * Rolls the Excavation treasure table for a broken block, mirroring legacy {@code
     * excavationBlockCheck} + {@code processExcavationBonusesOnBlock} — minus the base block XP, which
     * the block-break listener already awards via {@code BlockBreakXp} (re-applying it here would
     * double-count). For each treasure whose drop level the player has reached, an independent RNG roll
     * decides whether it drops; each dropped treasure then rolls the Archaeology experience-orb chance
     * separately. A block with no treasure table yields {@link ExcavationRewards#EMPTY}.
     *
     * <p>The item/orb spawns themselves need vanilla types and are done by the caller; this method only
     * makes the MC-free decisions. RNG rolls aren't unit-tested (no injectable seam); the deterministic
     * empty-table and drop-level short-circuits are.
     *
     * @param blockRegistryId the broken block's vanilla registry id (namespaced or bare)
     * @return the treasures/XP/orbs to spawn, or {@link ExcavationRewards#EMPTY}
     */
    public @NotNull ExcavationRewards rollTreasureRewards(@NotNull String blockRegistryId) {
        if (!Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.EXCAVATION_ARCHAEOLOGY)) {
            return ExcavationRewards.EMPTY;
        }
        final List<ExcavationTreasure> treasures = getTreasures(blockRegistryId);
        if (treasures.isEmpty()) {
            return ExcavationRewards.EMPTY;
        }

        final int skillLevel = getSkillLevel();
        final List<ItemSpec> drops = new ArrayList<>();
        final List<Integer> orbs = new ArrayList<>();
        int xp = 0;

        for (ExcavationTreasure treasure : treasures) {
            if (skillLevel >= treasure.getDropLevel()
                    && ProbabilityUtil.isStaticSkillRNGSuccessful(PrimarySkillType.EXCAVATION,
                    mmoPlayer, treasure.getDropProbability())) {
                // Each dropped treasure independently rolls the Archaeology XP-orb chance (legacy
                // processExcavationBonusesOnBlock does this per treasure, not once per block).
                if (ProbabilityUtil.isStaticSkillRNGSuccessful(PrimarySkillType.EXCAVATION,
                        mmoPlayer, getArchaelogyExperienceOrbChance())) {
                    orbs.add(getExperienceOrbsReward());
                }
                xp += treasure.getXp();
                drops.add(treasure.getDrop());
            }
        }

        if (drops.isEmpty()) {
            return ExcavationRewards.EMPTY;
        }
        return new ExcavationRewards(List.copyOf(drops), xp, List.copyOf(orbs));
    }

    public int getExperienceOrbsReward() {
        return getArchaeologyRank();
    }

    public double getArchaelogyExperienceOrbChance() {
        return getArchaeologyRank() * 2;
    }

    public int getArchaeologyRank() {
        return RankUtils.getRank(getPlayer(), SubSkillType.EXCAVATION_ARCHAEOLOGY);
    }
}
