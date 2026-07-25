package com.gmail.nossr50.util.experience;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;

/**
 * Creates the concrete {@link ExperienceBar} for a skill. Exists so {@link ExperienceBarManager} can
 * be constructed with the real {@link ExperienceBarWrapper} in production
 * ({@code ExperienceBarWrapper::new}) and with a fake in unit tests, keeping the manager's
 * scheduling/visibility logic Minecraft-free.
 */
@FunctionalInterface
public interface ExperienceBarFactory {

    ExperienceBar create(PrimarySkillType skill, McMMOPlayer mmoPlayer);
}
