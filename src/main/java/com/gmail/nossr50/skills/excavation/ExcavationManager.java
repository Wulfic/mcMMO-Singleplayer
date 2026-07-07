package com.gmail.nossr50.skills.excavation;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.treasure.ExcavationTreasure;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.skills.RankUtils;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Excavation skill manager (Phase 10.4 port). The rank-driven Archaeology math and treasure-table
 * lookup survive; the block-break drop/spawn bodies are deferred until the item-spawn adapter lands.
 *
 * <p><b>Dropped until the item-spawn / block adapters (PORT Phase 10):</b>
 * <ul>
 *   <li>{@code excavationBlockCheck} / {@code processExcavationBonusesOnBlock} — roll the treasures
 *       returned by {@link #getTreasures(String)} and spawn them + vanilla XP orbs via {@code
 *       Misc.spawnItem}/{@code spawnExperienceOrb}, then award block XP from {@code ExperienceConfig}.
 *       They need a live block + the {@link com.gmail.nossr50.datatypes.treasure.ItemSpec}→ItemStack
 *       builder;</li>
 *   <li>{@code gigaDrillBreaker} — double block-check + {@code SkillUtils.handleDurabilityChange} on
 *       the held tool (needs the ItemStack durability path);</li>
 *   <li>{@code printExcavationDebug} — chat-dumps a block's treasures (needs a live block).</li>
 * </ul>
 * All of these are pure downstream of the two things this port <i>does</i> deliver: the treasure
 * table ({@link #getTreasures(String)}) and the Archaeology rank rewards below.
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
