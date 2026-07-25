package com.gmail.nossr50.commands.skills;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.RankConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.player.PlayerProfile;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.skills.excavation.ExcavationManager;
import com.gmail.nossr50.skills.mining.MiningManager;
import com.gmail.nossr50.util.McTestRegistries;
import com.gmail.nossr50.util.player.UserManager;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises the {@code /mcstats <skill>} renderer against the real bundled configs: the shared header
 * / sub-skill list (base {@link SkillStatsRenderer}), the Mining effect stats
 * ({@link MiningStatsRenderer}), and the {@link GenericSkillStatsRenderer} fallback. RetroMode is on
 * by default, so every Mining sub-skill has unlocked by level 1000 and none at level 0
 * ({@code skillranks.yml}).
 */
class SkillStatsRendererTest {

    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e7");

    private McMMOPlayer mmoPlayer;

    @BeforeAll
    static void bootstrapRegistries() {
        McTestRegistries.bootstrap();
    }

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));

        final PlatformPlayer platformPlayer = mock(PlatformPlayer.class);
        when(platformPlayer.getUniqueId()).thenReturn(PLAYER_ID);

        mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);

        final PlayerProfile profile = mock(PlayerProfile.class);
        when(profile.getSkillXpLevel(PrimarySkillType.MINING)).thenReturn(123);
        when(profile.getXpToLevel(PrimarySkillType.MINING)).thenReturn(456);
        when(mmoPlayer.getProfile()).thenReturn(profile);

        when(mmoPlayer.getMiningManager()).thenReturn(new MiningManager(mmoPlayer));
        when(mmoPlayer.getExcavationManager()).thenReturn(new ExcavationManager(mmoPlayer));
        UserManager.track(mmoPlayer);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
        McMMOMod.setAdvancedConfig(null);
        UserManager.clearAll();
    }

    private List<String> render(SkillStatsRenderer renderer) {
        final List<String> lines = new ArrayList<>();
        renderer.render(mmoPlayer, (Text t) -> lines.add(t.getString()));
        return lines;
    }

    private boolean anyLineContains(List<String> lines, String needle) {
        return lines.stream().anyMatch(line -> line.contains(needle));
    }

    @Test
    void miningAtMaxLevelShowsHeaderSubSkillsAndEffectStats() {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.MINING)).thenReturn(1000);

        final List<String> lines = render(new MiningStatsRenderer());

        assertTrue(anyLineContains(lines, "Mining"), "header carries the skill name");
        // The level line ("LVL: ... XP(...)"); the number itself is MessageFormat-grouped ("1,000"),
        // so assert the stable literal rather than the raw digits.
        assertTrue(anyLineContains(lines, "LVL"), "header shows the level line; lines=" + lines);
        assertTrue(anyLineContains(lines, "Super Breaker"), "sub-skill list names Super Breaker");
        // Effect stats: the Double Drop chance line (stat label from the locale).
        assertTrue(anyLineContains(lines, "Double Drop Chance"),
                "an unlocked skill shows its effect stats; lines=" + lines);
    }

    @Test
    void miningAtZeroShowsLockedSubSkillsAndNoEffectStats() {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.MINING)).thenReturn(0);

        final List<String> lines = render(new MiningStatsRenderer());

        assertTrue(anyLineContains(lines, "Locked"), "locked sub-skills are marked Locked");
        assertFalse(anyLineContains(lines, "Double Drop Chance"),
                "no effect stats before anything is unlocked");
    }

    @Test
    void gatheringRenderersEmitAStatsSectionAtMaxLevel() {
        // The stats-section header ("Stats") only appears when a dedicated renderer produced effect
        // lines — a robust discriminator from the generic fallback, which never emits it.
        when(mmoPlayer.getSkillLevel(PrimarySkillType.WOODCUTTING)).thenReturn(1000);
        assertTrue(anyLineContains(render(new WoodcuttingStatsRenderer()), "Stats"),
                "Woodcutting shows effect stats at max level");

        when(mmoPlayer.getSkillLevel(PrimarySkillType.EXCAVATION)).thenReturn(1000);
        assertTrue(anyLineContains(render(new ExcavationStatsRenderer()), "Stats"),
                "Excavation shows effect stats at max level");

        when(mmoPlayer.getSkillLevel(PrimarySkillType.HERBALISM)).thenReturn(1000);
        assertTrue(anyLineContains(render(new HerbalismStatsRenderer()), "Stats"),
                "Herbalism shows effect stats at max level");
    }

    @Test
    void genericRendererShowsHeaderAndSubSkillsForAnySkill() {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.SWORDS)).thenReturn(500);

        final List<String> lines =
                render(new GenericSkillStatsRenderer(PrimarySkillType.SWORDS));

        assertTrue(anyLineContains(lines, "Swords"), "generic header still names the skill");
        assertFalse(lines.isEmpty(), "generic renderer still emits the header + sub-skill list");
    }
}
