package com.gmail.nossr50.util.experience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.platform.scheduler.TickScheduler;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins the Minecraft-free show / re-arm / hide logic of {@link ExperienceBarManager}. Uses a real
 * {@link TickScheduler} (pumped by hand) so the fade timing is exercised end-to-end, a fake
 * {@link ExperienceBar} that records visibility, and a mocked {@link ExperienceConfig} so no live
 * server or config file is needed.
 */
class ExperienceBarManagerTest {

    /** Short fade so the tests can pump a handful of ticks; production default is 30s (600 ticks). */
    private static final long HIDE_DELAY_TICKS = 3;

    private McMMOPlayer mmoPlayer;
    private ExperienceConfig config;
    private TickScheduler scheduler;
    private Map<PrimarySkillType, FakeBar> bars;
    private ExperienceBarManager manager;

    @BeforeEach
    void setUp() {
        mmoPlayer = mock(McMMOPlayer.class);
        config = mock(ExperienceConfig.class);
        scheduler = new TickScheduler();
        bars = new EnumMap<>(PrimarySkillType.class);

        // Bars enabled everywhere unless a test overrides.
        when(config.isExperienceBarsEnabled()).thenReturn(true);
        when(config.isExperienceBarEnabled(any())).thenReturn(true);

        final ExperienceBarFactory factory = (skill, player) -> {
            final FakeBar bar = new FakeBar();
            bars.put(skill, bar);
            return bar;
        };
        manager = new ExperienceBarManager(mmoPlayer, factory, scheduler, config, HIDE_DELAY_TICKS);
    }

    @Test
    void showsBarAndSetsProgressOnGain() {
        when(mmoPlayer.getProgressInCurrentSkillLevel(PrimarySkillType.MINING)).thenReturn(0.5);

        manager.updateExperienceBar(PrimarySkillType.MINING);

        final FakeBar bar = bars.get(PrimarySkillType.MINING);
        assertTrue(bar.visible, "bar should be visible right after a gain");
        assertEquals(0.5, bar.lastProgress, "progress should mirror the skill-level progress");
    }

    @Test
    void hidesAfterDelayOfInactivity() {
        manager.updateExperienceBar(PrimarySkillType.MINING);
        final FakeBar bar = bars.get(PrimarySkillType.MINING);

        pump(HIDE_DELAY_TICKS - 1);
        assertTrue(bar.visible, "bar should still be up before the delay elapses");

        pump(1);
        assertFalse(bar.visible, "bar should fade after the full inactivity delay");
    }

    @Test
    void reArmsHideTimerOnRepeatGain() {
        manager.updateExperienceBar(PrimarySkillType.MINING);
        final FakeBar bar = bars.get(PrimarySkillType.MINING);

        // Almost fade, then gain again: the pending hide must be cancelled and a fresh full delay armed.
        pump(HIDE_DELAY_TICKS - 1);
        manager.updateExperienceBar(PrimarySkillType.MINING);

        pump(HIDE_DELAY_TICKS - 1);
        assertTrue(bar.visible, "a repeat gain must reset the fade timer, keeping the bar up");

        pump(1);
        assertFalse(bar.visible, "bar fades only after the delay measured from the last gain");
    }

    @Test
    void childSkillBarsAreSuppressed() {
        manager.updateExperienceBar(PrimarySkillType.SALVAGE);
        manager.updateExperienceBar(PrimarySkillType.SMELTING);

        assertNull(bars.get(PrimarySkillType.SALVAGE), "child skill Salvage should never build a bar");
        assertNull(bars.get(PrimarySkillType.SMELTING),
                "child skill Smelting should never build a bar");
    }

    @Test
    void globallyDisabledShowsNothing() {
        when(config.isExperienceBarsEnabled()).thenReturn(false);

        manager.updateExperienceBar(PrimarySkillType.MINING);

        assertNull(bars.get(PrimarySkillType.MINING), "no bar when XP bars are globally disabled");
    }

    @Test
    void perSkillDisabledShowsNothing() {
        when(config.isExperienceBarEnabled(PrimarySkillType.MINING)).thenReturn(false);

        manager.updateExperienceBar(PrimarySkillType.MINING);

        assertNull(bars.get(PrimarySkillType.MINING), "no bar when this skill's bar is disabled");
    }

    @Test
    void hideAllHidesEveryBarAndCancelsPendingFades() {
        manager.updateExperienceBar(PrimarySkillType.MINING);
        manager.updateExperienceBar(PrimarySkillType.HERBALISM);

        manager.hideAll();

        assertFalse(bars.get(PrimarySkillType.MINING).visible, "hideAll must hide the Mining bar");
        assertFalse(bars.get(PrimarySkillType.HERBALISM).visible,
                "hideAll must hide the Herbalism bar");

        // The pending fades were cancelled, so pumping past the delay does nothing (no exceptions,
        // no re-hide churn) — the bars simply stay hidden.
        pump(HIDE_DELAY_TICKS + 2);
        assertFalse(bars.get(PrimarySkillType.MINING).visible, "bars stay hidden after hideAll");
    }

    private void pump(long ticks) {
        for (long i = 0; i < ticks; i++) {
            scheduler.tick();
        }
    }

    /** Records the last progress + current visibility so the tests can assert show/hide behaviour. */
    private static final class FakeBar implements ExperienceBar {
        private double lastProgress = -1;
        private boolean visible;

        @Override
        public void setProgress(double progress) {
            this.lastProgress = progress;
        }

        @Override
        public void show() {
            this.visible = true;
        }

        @Override
        public void hide() {
            this.visible = false;
        }
    }
}
