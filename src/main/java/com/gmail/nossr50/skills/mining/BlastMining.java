package com.gmail.nossr50.skills.mining;

import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.util.skills.RankUtils;

/**
 * Blast Mining constants + rank→config lookups (Phase 10.3 port).
 *
 * <p>Only the MC-free config/rank surface survives. The legacy PvP damage handler
 * {@code processBlastMiningExplosion(EntityDamageByEntityEvent, TNTPrimed, Player)} is dropped:
 * it read Bukkit event damage, TNT metadata and looked the attacker up through the server player
 * registry — none of which exist here, and PvP is out of scope for singleplayer anyway.
 * ({@code BLAST_MINING_PVP_DAMAGE_CAP} went with it.)
 */
public class BlastMining {
    public static final int MAXIMUM_REMOTE_DETONATION_DISTANCE = 100;

    public static double getBlastRadiusModifier(int rank) {
        return McMMOMod.getAdvancedConfig().getBlastRadiusModifier(rank);
    }

    public static double getBlastDamageDecrease(int rank) {
        return McMMOMod.getAdvancedConfig().getBlastDamageDecrease(rank);
    }

    public static int getDemolitionExpertUnlockLevel() {
        for (int i = 0; i < SubSkillType.MINING_BLAST_MINING.getNumRanks() - 1; i++) {
            if (getBlastDamageDecrease(i + 1) > 0) {
                return RankUtils.getRankUnlockLevel(SubSkillType.MINING_BLAST_MINING, i + 1);
            }
        }

        return 0;
    }

    public static int getBiggerBombsUnlockLevel() {
        for (int i = 0; i < SubSkillType.MINING_BLAST_MINING.getNumRanks() - 1; i++) {
            if (getBlastRadiusModifier(i + 1) > 0) {
                return RankUtils.getRankUnlockLevel(SubSkillType.MINING_BLAST_MINING, i + 1);
            }
        }

        return 0;
    }
}
