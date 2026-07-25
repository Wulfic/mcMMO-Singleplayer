package com.gmail.nossr50.util.experience;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.scheduler.ScheduledTask;
import com.gmail.nossr50.platform.scheduler.TaskScheduler;
import com.gmail.nossr50.util.Misc;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Shows, refreshes, and auto-hides one player's mcMMO XP bars — one {@link ExperienceBar} per skill.
 *
 * <p>Legacy {@code ExperienceBarManager}, ported for singleplayer. Each XP gain calls
 * {@link #updateExperienceBar}, which shows/updates that skill's bar and (re)arms a hide task; the
 * bar fades once the player stops training that skill for {@code Hide_Delay_Seconds}
 * (default 10 — legacy hard-coded 3). Every time the player gains more XP in the same skill the
 * pending hide is cancelled and re-scheduled, so the bar only disappears after a real lull.
 *
 * <p>All Minecraft types are kept out of this class: the bar itself is the injected
 * {@link ExperienceBar} seam (real {@link ExperienceBarWrapper} in production, a fake in tests), the
 * timer is the MC-free {@link TaskScheduler}, and the enable/colour config is an injected
 * {@link ExperienceConfig}. That makes the show / re-arm / hide logic fully unit-testable with no
 * live server. One manager is created lazily per {@link McMMOPlayer}.
 */
public class ExperienceBarManager {

    private final McMMOPlayer mmoPlayer;
    private final ExperienceBarFactory barFactory;
    private final TaskScheduler scheduler;
    private final ExperienceConfig config;
    private final long hideDelayTicks;

    private final Map<PrimarySkillType, ExperienceBar> experienceBars =
            new EnumMap<>(PrimarySkillType.class);
    private final Map<PrimarySkillType, ScheduledTask> hideTasks =
            new EnumMap<>(PrimarySkillType.class);

    /** Skills whose bar is suppressed entirely (child skills by default). */
    private final Set<PrimarySkillType> disabledBars = EnumSet.noneOf(PrimarySkillType.class);
    /** Skills whose bar stays up (no hide task is armed). Reserved for a future toggle command. */
    private final Set<PrimarySkillType> alwaysVisible = EnumSet.noneOf(PrimarySkillType.class);

    /** Production wiring: real boss-bar factory, the server-tick scheduler, and live config. */
    public ExperienceBarManager(@NotNull McMMOPlayer mmoPlayer) {
        this(mmoPlayer, ExperienceBarWrapper::new, McMMOMod.getScheduler(),
                McMMOMod.getExperienceConfig(),
                (long) McMMOMod.getExperienceConfig().getExperienceBarHideDelaySeconds()
                        * Misc.TICK_CONVERSION_FACTOR);
    }

    /** Test seam: inject the bar factory, scheduler, config, and hide delay (in ticks). */
    ExperienceBarManager(@NotNull McMMOPlayer mmoPlayer, @NotNull ExperienceBarFactory barFactory,
            @NotNull TaskScheduler scheduler, @NotNull ExperienceConfig config, long hideDelayTicks) {
        this.mmoPlayer = mmoPlayer;
        this.barFactory = barFactory;
        this.scheduler = scheduler;
        this.config = config;
        this.hideDelayTicks = hideDelayTicks;

        // Child skills (Salvage, Smelting) derive their level from their parents and never gain XP
        // directly, so their progress is always 1.0 — legacy hid their bars by default, so do we.
        disabledBars.add(PrimarySkillType.SALVAGE);
        disabledBars.add(PrimarySkillType.SMELTING);
    }

    /**
     * Show (creating on first use) and refresh {@code skill}'s XP bar, then (re)arm its hide task.
     * No-op when the bar is disabled globally, disabled for this skill, or suppressed as a child
     * skill.
     */
    public void updateExperienceBar(@NotNull PrimarySkillType skill) {
        if (disabledBars.contains(skill)
                || !config.isExperienceBarsEnabled()
                || !config.isExperienceBarEnabled(skill)) {
            return;
        }

        final ExperienceBar bar =
                experienceBars.computeIfAbsent(skill, s -> barFactory.create(s, mmoPlayer));
        bar.setProgress(mmoPlayer.getProgressInCurrentSkillLevel(skill));
        bar.show();

        rescheduleHide(skill);
    }

    /** Cancel any pending hide for {@code skill} and arm a fresh one (unless the bar is pinned). */
    private void rescheduleHide(@NotNull PrimarySkillType skill) {
        final ScheduledTask existing = hideTasks.remove(skill);
        if (existing != null) {
            existing.cancel();
        }

        if (alwaysVisible.contains(skill)) {
            return;
        }

        // A cancelled scheduler task never runs, so the running hide is always the currently-mapped
        // one; it clears its own bookkeeping entry when it fires.
        final ScheduledTask task = scheduler.runLater(() -> {
            hideExperienceBar(skill);
            hideTasks.remove(skill);
        }, hideDelayTicks);
        hideTasks.put(skill, task);
    }

    /** Hide {@code skill}'s bar if it has one; the bar object is kept for cheap re-show. */
    public void hideExperienceBar(@NotNull PrimarySkillType skill) {
        final ExperienceBar bar = experienceBars.get(skill);
        if (bar != null) {
            bar.hide();
        }
    }

    /** Hide every bar and cancel all pending hide tasks (e.g. on logout / world close). */
    public void hideAll() {
        for (ScheduledTask task : hideTasks.values()) {
            task.cancel();
        }
        hideTasks.clear();
        for (ExperienceBar bar : experienceBars.values()) {
            bar.hide();
        }
    }
}
