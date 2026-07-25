package com.gmail.nossr50.util.experience;

/**
 * A single player's on-screen XP bar for one skill.
 *
 * <p>Minecraft-free seam between the {@link ExperienceBarManager} scheduling/visibility logic (which
 * is fully unit-testable) and the concrete {@link ExperienceBarWrapper} that renders through a
 * vanilla {@code ServerBossBar}. Tests inject a fake implementation via {@link ExperienceBarFactory}
 * so the show / re-arm / hide logic can be exercised without a live server or player entity.
 */
public interface ExperienceBar {

    /**
     * Set the fill fraction of the bar.
     *
     * @param progress skill-level progress in {@code [0, 1]}; implementations clamp out-of-range
     *                 values rather than throwing
     */
    void setProgress(double progress);

    /** Make the bar visible to the player (and, if needed, (re)subscribe the live player entity). */
    void show();

    /** Hide the bar without discarding it, so a later {@link #show()} is cheap. */
    void hide();
}
