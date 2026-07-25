package com.gmail.nossr50.commands.skills;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.gmail.nossr50.skills.acrobatics.AcrobaticsManager;
import com.gmail.nossr50.skills.alchemy.AlchemyManager;
import com.gmail.nossr50.skills.archery.ArcheryManager;
import com.gmail.nossr50.skills.axes.AxesManager;
import com.gmail.nossr50.skills.crossbows.CrossbowsManager;
import com.gmail.nossr50.skills.excavation.ExcavationManager;
import com.gmail.nossr50.skills.fishing.FishingManager;
import com.gmail.nossr50.skills.herbalism.HerbalismManager;
import com.gmail.nossr50.skills.maces.MacesManager;
import com.gmail.nossr50.skills.mining.MiningManager;
import com.gmail.nossr50.skills.repair.RepairManager;
import com.gmail.nossr50.skills.salvage.SalvageManager;
import com.gmail.nossr50.skills.smelting.SmeltingManager;
import com.gmail.nossr50.skills.spears.SpearsManager;
import com.gmail.nossr50.skills.swords.SwordsManager;
import com.gmail.nossr50.skills.taming.TamingManager;
import com.gmail.nossr50.skills.tridents.TridentsManager;
import com.gmail.nossr50.skills.unarmed.UnarmedManager;
import com.gmail.nossr50.skills.woodcutting.WoodcuttingManager;
import com.gmail.nossr50.util.McTestRegistries;
import com.gmail.nossr50.util.player.UserManager;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
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

    /** An unparsed legacy colour code: the simplified {@code &} form the locale files are written in. */
    private static final Pattern LEGACY_CODE = Pattern.compile("&[0-9a-fk-orA-FK-OR]");

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
        when(mmoPlayer.getSwordsManager()).thenReturn(new SwordsManager(mmoPlayer));
        when(mmoPlayer.getAxesManager()).thenReturn(new AxesManager(mmoPlayer));
        when(mmoPlayer.getUnarmedManager()).thenReturn(new UnarmedManager(mmoPlayer));
        when(mmoPlayer.getCrossbowsManager()).thenReturn(new CrossbowsManager(mmoPlayer));
        when(mmoPlayer.getTridentsManager()).thenReturn(new TridentsManager(mmoPlayer));
        when(mmoPlayer.getMacesManager()).thenReturn(new MacesManager(mmoPlayer));
        when(mmoPlayer.getSpearsManager()).thenReturn(new SpearsManager(mmoPlayer));
        when(mmoPlayer.getRepairManager()).thenReturn(new RepairManager(mmoPlayer));
        when(mmoPlayer.getSalvageManager()).thenReturn(new SalvageManager(mmoPlayer));
        when(mmoPlayer.getSmeltingManager()).thenReturn(new SmeltingManager(mmoPlayer));
        when(mmoPlayer.getWoodcuttingManager()).thenReturn(new WoodcuttingManager(mmoPlayer));
        when(mmoPlayer.getHerbalismManager()).thenReturn(new HerbalismManager(mmoPlayer));
        when(mmoPlayer.getArcheryManager()).thenReturn(new ArcheryManager(mmoPlayer));
        when(mmoPlayer.getAcrobaticsManager()).thenReturn(new AcrobaticsManager(mmoPlayer));
        when(mmoPlayer.getTamingManager()).thenReturn(new TamingManager(mmoPlayer));
        when(mmoPlayer.getFishingManager()).thenReturn(new FishingManager(mmoPlayer));
        when(mmoPlayer.getAlchemyManager()).thenReturn(new AlchemyManager(mmoPlayer));
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
    void combatRenderersEmitAStatsSectionAtMaxLevel() {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.SWORDS)).thenReturn(1000);
        assertTrue(anyLineContains(render(new SwordsStatsRenderer()), "Stats"),
                "Swords shows effect stats at max level");

        when(mmoPlayer.getSkillLevel(PrimarySkillType.AXES)).thenReturn(1000);
        assertTrue(anyLineContains(render(new AxesStatsRenderer()), "Stats"),
                "Axes shows effect stats at max level");

        when(mmoPlayer.getSkillLevel(PrimarySkillType.UNARMED)).thenReturn(1000);
        assertTrue(anyLineContains(render(new UnarmedStatsRenderer()), "Stats"),
                "Unarmed shows effect stats at max level");
    }

    @Test
    void weaponAndTamingRenderersEmitAStatsSectionAtMaxLevel() {
        for (PrimarySkillType s : List.of(PrimarySkillType.ARCHERY, PrimarySkillType.CROSSBOWS,
                PrimarySkillType.TRIDENTS, PrimarySkillType.MACES, PrimarySkillType.SPEARS,
                PrimarySkillType.TAMING)) {
            when(mmoPlayer.getSkillLevel(s)).thenReturn(1000);
            assertTrue(anyLineContains(render(SkillStatsRenderer.forSkill(s)), "Stats"),
                    s.name() + " shows effect stats at max level");
        }
    }

    @Test
    void miscRenderersEmitAStatsSectionAtMaxLevel() {
        for (PrimarySkillType s : List.of(PrimarySkillType.ACROBATICS, PrimarySkillType.REPAIR,
                PrimarySkillType.SALVAGE, PrimarySkillType.SMELTING)) {
            when(mmoPlayer.getSkillLevel(s)).thenReturn(1000);
            assertTrue(anyLineContains(render(SkillStatsRenderer.forSkill(s)), "Stats"),
                    s.name() + " shows effect stats at max level");
        }
    }

    @Test
    void noRenderedLineLeaksARawColourCode() {
        // Regression: sub-skill lines are built with the simplified "&8"/"&a"/"&7" codes the locale
        // files use, but TextUtils only understands section signs — without normalising first they
        // reached the client as literal "&8Clean Cuts &7- Locked" text. Text#getString() drops applied
        // styles, so any surviving "&<code>" here is an unparsed code, not a real colour.
        // Levels 0 / 500 / 1000 cover the locked, ranked, and fully-unlocked line shapes.
        for (PrimarySkillType s : PrimarySkillType.values()) {
            for (int level : new int[] {0, 500, 1000}) {
                when(mmoPlayer.getSkillLevel(s)).thenReturn(level);
                for (String line : render(SkillStatsRenderer.forSkill(s))) {
                    assertFalse(LEGACY_CODE.matcher(line).find(),
                            s.name() + " @" + level + " leaked an unparsed colour code: " + line);
                }
            }
        }
    }

    @Test
    void everySkillResolvesToANonNullRenderer() {
        // Guards the forSkill switch: every PrimarySkillType maps to a renderer (dedicated or the
        // generic fallback), none throws or returns null.
        for (PrimarySkillType s : PrimarySkillType.values()) {
            assertNotNull(SkillStatsRenderer.forSkill(s), s.name() + " must resolve to a renderer");
        }
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
