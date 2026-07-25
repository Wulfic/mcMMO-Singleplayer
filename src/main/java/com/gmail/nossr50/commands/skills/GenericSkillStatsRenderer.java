package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Fallback {@code /mcstats <skill>} renderer for skills that don't yet have a dedicated
 * {@link SkillStatsRenderer} subclass: it still shows the shared header (name, XP-gain method,
 * level/XP) and the full sub-skill list with ranks, just without the bespoke per-skill effect lines.
 * Lets every skill be queried while the detailed renderers are ported one at a time.
 */
public final class GenericSkillStatsRenderer extends SkillStatsRenderer {

    public GenericSkillStatsRenderer(@NotNull PrimarySkillType skill) {
        super(skill);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        // No bespoke effect values to pre-compute.
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        return List.of();
    }
}
