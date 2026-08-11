package com.gmail.nossr50.util.experience;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.platform.ExperienceBarWrapper;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.scheduler.ScheduledTask;
import com.gmail.nossr50.platform.scheduler.TaskScheduler;
import com.gmail.nossr50.util.Misc;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
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

    /**
     * Currently-shown bars in least-recently-trained order — the eviction queue for the on-screen
     * cap.
     *
     * <p>A {@link LinkedHashSet} rather than a list because a skill must appear exactly once no
     * matter how many times it is refreshed: re-training a skill re-inserts it at the young end
     * (remove, then add), so "least recently trained" stays exactly the iteration order and the
     * oldest entry is always the head.
     */
    private final Set<PrimarySkillType> visibleBars = new LinkedHashSet<>();

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

        // Legacy hid Salvage's and Smelting's bars by default, and that is kept: both are niche,
        // trained in short bursts alongside whatever produced the materials, and a bar for them
        // would mostly be crowding out the skill the player is actually watching.
        //
        // Agility is deliberately NOT in this list even though it is also a child skill. It is the
        // umbrella over Parkour, Swimming and Flying — the thing a player thinks of themselves as
        // levelling while moving — so its bar is wanted, and it is only meaningful because
        // McMMOPlayer#getProgressInCurrentSkillLevel now averages a child skill's parents instead of
        // returning a flat 1.0. Suppress that averaging and this becomes a permanently full bar.
        disabledBars.add(PrimarySkillType.SALVAGE);
        disabledBars.add(PrimarySkillType.SMELTING);
    }

    /**
     * Show (creating on first use) and refresh {@code skill}'s XP bar, then (re)arm its hide task.
     * No-op when the bar is disabled globally, disabled for this skill, or suppressed as a child
     * skill.
     */
    public void updateExperienceBar(@NotNull PrimarySkillType skill) {
        showBar(skill);
        // A child skill earns no XP of its own — its level is the mean of its parents' — so it would
        // never show a bar at all if it waited for a gain of its own. Training a parent IS training
        // it, so a parent's gain refreshes the child's bar too. Salvage and Smelting are unaffected:
        // their bars are suppressed below, and this is a no-op for them.
        for (PrimarySkillType child : McMMOMod.getSkillTools().getChildSkillsOf(skill)) {
            showBar(child);
        }
    }

    /** Show/refresh one skill's bar, arm its fade, and enforce the on-screen cap. */
    private void showBar(@NotNull PrimarySkillType skill) {
        if (disabledBars.contains(skill)
                || !config.isExperienceBarsEnabled()
                || !config.isExperienceBarEnabled(skill)) {
            return;
        }

        final ExperienceBar bar =
                experienceBars.computeIfAbsent(skill, s -> barFactory.create(s, mmoPlayer));
        bar.setProgress(mmoPlayer.getProgressInCurrentSkillLevel(skill));
        bar.show();

        // Re-insert at the young end so "least recently trained" stays the iteration order.
        visibleBars.remove(skill);
        visibleBars.add(skill);
        enforceVisibleCap();

        rescheduleHide(skill);
    }

    /**
     * Hide the least recently trained bars until at most {@code Max_Visible} remain.
     *
     * <p>Evicts the <em>oldest</em> rather than refusing the newest: the bar a player wants on screen
     * is the skill they just used, so a cap that suppressed new bars would hide exactly the wrong
     * one. Pinned bars are skipped — an explicitly pinned bar outranks a recency cap, and letting the
     * cap evict one would make {@code alwaysVisible} a lie.
     *
     * <p>A loop rather than a single eviction because the cap can drop between calls (a config
     * reload, or a future command), leaving more bars up than it now allows.
     */
    private void enforceVisibleCap() {
        final int max = config.getMaxVisibleExperienceBars();
        if (max <= 0) {
            return; // Documented as "no limit".
        }
        final var iterator = visibleBars.iterator();
        int over = visibleBars.size() - max;
        while (over > 0 && iterator.hasNext()) {
            final PrimarySkillType oldest = iterator.next();
            if (alwaysVisible.contains(oldest)) {
                continue;
            }
            hideExperienceBar(oldest);
            iterator.remove();
            over--;
        }
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
            // Drop it from the eviction queue too — a faded bar is not on screen, so it must not
            // count against the cap or be "evicted" again later.
            visibleBars.remove(skill);
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
        visibleBars.clear();
    }
}
