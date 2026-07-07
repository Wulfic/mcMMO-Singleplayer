package com.gmail.nossr50.skills;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.util.text.ConfigStringUtils;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the skill + XP reward for breaking a block, from the configured per-block XP tables
 * (CONVERSION_TODO Phase 3, block-break XP path). This is the config-driven core the deferred
 * per-skill block bodies ({@code processWoodcuttingBlockXP}, {@code miningBlockCheck}, …) all fold
 * onto: they add bonus-drop / super-ability side effects (which still need the item-spawn adapter),
 * but the base XP award is exactly {@link ExperienceConfig#getXp(PrimarySkillType, String)}, which
 * every one of those bodies calls.
 *
 * <p>Kept MC-free (keys off a registry-path string, not a vanilla {@code Block}) so the routing is
 * unit-testable against the real bundled {@code experience.yml}.
 */
public final class BlockBreakXp {

    /**
     * The gathering skills that earn XP from breaking blocks, in resolution order. A vanilla block
     * belongs to at most one of these in the config, so the first non-zero match is authoritative;
     * the order only guards the theoretical case of an operator adding a block to two skills.
     */
    private static final List<PrimarySkillType> GATHERING_SKILLS = List.of(
            PrimarySkillType.MINING,
            PrimarySkillType.WOODCUTTING,
            PrimarySkillType.EXCAVATION,
            PrimarySkillType.HERBALISM);

    /** A resolved block-break XP reward: which skill earns it and how much. */
    public record Reward(@NotNull PrimarySkillType skill, int xp) {
    }

    private BlockBreakXp() {
    }

    /**
     * Resolve the XP reward for breaking a block.
     *
     * @param blockRegistryId the broken block's vanilla registry id (e.g. {@code "minecraft:oak_log"}
     *                        or a bare {@code "oak_log"}; the namespace is stripped)
     * @return the reward, or {@code null} if the block grants no skill XP (or configs aren't loaded)
     */
    public static @Nullable Reward resolve(@NotNull String blockRegistryId) {
        final ExperienceConfig config = McMMOMod.getExperienceConfig();
        if (config == null) {
            return null;
        }
        final String materialConfigString = ConfigStringUtils.getMaterialConfigString(blockRegistryId);
        for (PrimarySkillType skill : GATHERING_SKILLS) {
            final int xp = config.getXp(skill, materialConfigString);
            if (xp > 0) {
                return new Reward(skill, xp);
            }
        }
        return null;
    }
}
