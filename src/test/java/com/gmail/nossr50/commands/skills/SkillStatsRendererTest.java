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
import com.gmail.nossr50.skills.agility.AgilityManager;
import com.gmail.nossr50.skills.alchemy.AlchemyManager;
import com.gmail.nossr50.skills.archery.ArcheryManager;
import com.gmail.nossr50.skills.axes.AxesManager;
import com.gmail.nossr50.skills.crossbows.CrossbowsManager;
import com.gmail.nossr50.skills.excavation.ExcavationManager;
import com.gmail.nossr50.skills.fishing.FishingManager;
import com.gmail.nossr50.skills.herbalism.HerbalismManager;
import com.gmail.nossr50.skills.hunter.HunterManager;
import com.gmail.nossr50.skills.husbandry.HusbandryManager;
import com.gmail.nossr50.skills.maces.MacesManager;
import com.gmail.nossr50.skills.mining.MiningManager;
import com.gmail.nossr50.skills.repair.RepairManager;
import com.gmail.nossr50.skills.salvage.SalvageManager;
import com.gmail.nossr50.skills.smelting.SmeltingManager;
import com.gmail.nossr50.skills.spears.SpearsManager;
import com.gmail.nossr50.skills.stealth.StealthManager;
import com.gmail.nossr50.skills.swords.SwordsManager;
import com.gmail.nossr50.skills.taming.TamingManager;
import com.gmail.nossr50.skills.tridents.TridentsManager;
import com.gmail.nossr50.skills.unarmed.UnarmedManager;
import com.gmail.nossr50.skills.unarmored.UnarmoredManager;
import com.gmail.nossr50.skills.woodcutting.WoodcuttingManager;
import com.gmail.nossr50.util.McTestRegistries;
import com.gmail.nossr50.util.player.UserManager;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /** A field rather than a local: the Hunter tests below stub the kill map on it. */
    private PlayerProfile profile;

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

        profile = mock(PlayerProfile.class);
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
        when(mmoPlayer.getAgilityManager()).thenReturn(new AgilityManager(mmoPlayer));
        when(mmoPlayer.getTamingManager()).thenReturn(new TamingManager(mmoPlayer));
        when(mmoPlayer.getFishingManager()).thenReturn(new FishingManager(mmoPlayer));
        when(mmoPlayer.getAlchemyManager()).thenReturn(new AlchemyManager(mmoPlayer));
        // Pass 2. Without these the renderers below see a null manager and silently emit no stats,
        // which would make every assertion in pass2RenderersEmitAStatsSectionAtMaxLevel vacuous.
        when(mmoPlayer.getHusbandryManager()).thenReturn(new HusbandryManager(mmoPlayer));
        when(mmoPlayer.getStealthManager()).thenReturn(new StealthManager(mmoPlayer));
        when(mmoPlayer.getUnarmoredManager()).thenReturn(new UnarmoredManager(mmoPlayer));
        when(mmoPlayer.getHunterManager()).thenReturn(new HunterManager(mmoPlayer));
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
        for (PrimarySkillType s : List.of(PrimarySkillType.AGILITY, PrimarySkillType.REPAIR,
                PrimarySkillType.SALVAGE, PrimarySkillType.SMELTING)) {
            when(mmoPlayer.getSkillLevel(s)).thenReturn(1000);
            assertTrue(anyLineContains(render(SkillStatsRenderer.forSkill(s)), "Stats"),
                    s.name() + " shows effect stats at max level");
        }
    }

    @Test
    void pass2RenderersEmitAStatsSectionAtMaxLevel() {
        // Regression: all four of these shipped without a dedicated renderer and fell through to
        // GenericSkillStatsRenderer, so their .Stat locale keys were written but never read and the
        // screens showed a header and a sub-skill list with no effect values at all.
        for (PrimarySkillType s : List.of(PrimarySkillType.HUSBANDRY, PrimarySkillType.STEALTH,
                PrimarySkillType.UNARMORED, PrimarySkillType.PARKOUR, PrimarySkillType.HUNTER)) {
            when(mmoPlayer.getSkillLevel(s)).thenReturn(1000);
            assertTrue(anyLineContains(render(SkillStatsRenderer.forSkill(s)), "Stats"),
                    s.name() + " shows effect stats at max level");
        }
    }

    @Test
    void hunterRendersBothAxesAndEverySubSkillStatLabel() {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.HUNTER)).thenReturn(1000);
        when(profile.getAllMobKills()).thenReturn(new LinkedHashMap<>(Map.of(
                "minecraft:zombie", 12_004, "minecraft:creeper", 40)));
        when(profile.getMobKills("minecraft:zombie")).thenReturn(12_004);
        when(profile.getMobKills("minecraft:creeper")).thenReturn(40);

        final List<String> lines = render(new HunterStatsRenderer());

        // The horizontal axis: the two totals and the league table underneath them.
        for (String label : List.of("Creatures Hunted", "Creatures Mastered", "Zombie", "12,004",
                "Mastery 3", "Creeper", "no mastery yet")) {
            assertTrue(anyLineContains(lines, label),
                    "Hunter stats missing '" + label + "'; lines=" + lines);
        }
        // The vertical axis, plus the sub-skill whose whole job is telling the player how to use it.
        for (String label : List.of("Trophy Chance", "Highest Tier Reached", "4/4", "Quarry Sense")) {
            assertTrue(anyLineContains(lines, label),
                    "Hunter stats missing '" + label + "'; lines=" + lines);
        }
        assertFalse(anyLineContains(lines, "!Hunter"),
                "a stat line resolved to a locale miss; lines=" + lines);
    }

    @Test
    void anUnknownMobIdIsShownAsItsIdAndNeverAsAPig() {
        // ⚠️ Registries.ENTITY_TYPE is a DefaultedRegistry: its get(Identifier) answers an unknown id
        // with the registry DEFAULT — minecraft:pig — instead of null. The kill map deliberately
        // stores raw strings and resolves them only here (stage 2, so an uninstalled mod cannot cost
        // a player their profile), which makes this screen the one place such keys surface. Read
        // through get(), somebody's 4,000 modded kills would be filed under "Pig", plausibly and
        // silently. Mutation check: swap getOptionalValue for get and this is the test that reddens.
        when(mmoPlayer.getSkillLevel(PrimarySkillType.HUNTER)).thenReturn(1000);
        when(profile.getAllMobKills())
                .thenReturn(new LinkedHashMap<>(Map.of("somemod:dread_beast", 3_000)));
        when(profile.getMobKills("somemod:dread_beast")).thenReturn(3_000);

        final List<String> lines = render(new HunterStatsRenderer());

        assertTrue(anyLineContains(lines, "somemod:dread_beast"),
                "an unresolvable creature keeps its raw id; lines=" + lines);
        assertFalse(anyLineContains(lines, "Pig"),
                "the DefaultedRegistry default must never stand in for a missing mob; lines=" + lines);
    }

    @Test
    void aHunterWhoHasKilledNothingIsToldSoRatherThanShownAnEmptyBlock() {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.HUNTER)).thenReturn(1000);

        final List<String> lines = render(new HunterStatsRenderer());

        assertTrue(anyLineContains(lines, "Nothing hunted yet"), "lines=" + lines);
        assertFalse(anyLineContains(lines, "Creatures Mastered"),
                "no league table without a log; lines=" + lines);
    }

    @Test
    void husbandryRendersEverySubSkillStatLabel() {
        // Pins that each of the six Husbandry stat lines is actually reached — a renderer that
        // emitted only the first would still satisfy the "Stats" header assertion above. The labels
        // are the locale .Stat / .Stat.Extra values, so this also proves those keys resolve rather
        // than rendering as "!Husbandry.SubSkill...!".
        when(mmoPlayer.getSkillLevel(PrimarySkillType.HUSBANDRY)).thenReturn(1000);

        final List<String> lines = render(new HusbandryStatsRenderer());

        for (String label : List.of("Multi-Breed Reach", "Additional Animals Bred", "Twin Chance",
                "Growth Acceleration", "Double Feed Chance", "Bonus Yield Chance",
                "Tool Durability Save Chance")) {
            assertTrue(anyLineContains(lines, label),
                    "Husbandry stats missing the '" + label + "' line; lines=" + lines);
        }
        assertFalse(anyLineContains(lines, "!Husbandry"),
                "a stat line resolved to a locale miss; lines=" + lines);
    }

    @Test
    void husbandryAtZeroShowsNoEffectStats() {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.HUSBANDRY)).thenReturn(0);

        final List<String> lines = render(new HusbandryStatsRenderer());

        assertTrue(anyLineContains(lines, "Locked"), "locked sub-skills are marked Locked");
        assertFalse(anyLineContains(lines, "Twin Chance"),
                "no effect stats before anything is unlocked; lines=" + lines);
    }

    @Test
    void parkourShowsBothTheRollAndTheGracefulRollChance() {
        // GitHub #4. Two regressions in one screen:
        //  1. Roll moved from AGILITY to PARKOUR (2026-08-03) so its odds are shown beside the level
        //     that actually moves them. Rendering it under /mcstats agility again would show the
        //     three-skill mean and re-create the confusion the move fixed.
        //  2. "Graceful Roll Chance" (the .Stat.Extra label) has existed in the shipped locale since
        //     the Bukkit port and was NEVER rendered by anything, so the doubled number a sneaking
        //     player actually rolls against was invisible everywhere in the game.
        when(mmoPlayer.getSkillLevel(PrimarySkillType.PARKOUR)).thenReturn(500);

        final List<String> lines = render(new ParkourStatsRenderer());

        assertTrue(anyLineContains(lines, "Roll Chance"),
                "the plain roll chance must be shown; lines=" + lines);
        assertTrue(anyLineContains(lines, "Graceful Roll Chance"),
                "the doubled sneaking chance must be shown too; lines=" + lines);
        // 500/1000 * 100 = 50%, and graceful is exactly double it. Asserting the values (not just
        // the labels) is what stops the two lines silently rendering the same number.
        assertTrue(anyLineContains(lines, "50.00%"), "plain roll at Parkour 500; lines=" + lines);
        assertTrue(anyLineContains(lines, "100.00%"), "graceful roll at Parkour 500; lines=" + lines);
        assertFalse(anyLineContains(lines, "!Parkour"),
                "a stat line resolved to a locale miss; lines=" + lines);
    }

    @Test
    void agilityNoLongerRendersRoll() {
        // The other half of the move: Roll must not appear on both screens. Dodge stays, because it
        // is still gated on Agility's mean.
        when(mmoPlayer.getSkillLevel(PrimarySkillType.AGILITY)).thenReturn(1000);

        final List<String> lines = render(new AgilityStatsRenderer());

        assertTrue(anyLineContains(lines, "Dodge"), "Dodge is still Agility's; lines=" + lines);
        assertFalse(anyLineContains(lines, "Roll Chance"),
                "Roll belongs to /mcstats parkour now; lines=" + lines);
    }

    @Test
    void swimmingAndFlyingDeliberatelyFallBackToTheGenericRenderer() {
        // NOT an oversight, and pinned so it is not "fixed" into an empty stats section later:
        // Swimming and Flying own no sub-skills of their own. They are parents of the child skill
        // Agility, and every movement effect is an AGILITY_* sub-skill gated on that averaged level,
        // so it renders under /mcstats agility instead.
        for (PrimarySkillType s : List.of(PrimarySkillType.SWIMMING, PrimarySkillType.FLYING)) {
            assertTrue(SkillStatsRenderer.forSkill(s) instanceof GenericSkillStatsRenderer,
                    s.name() + " has no sub-skills of its own, so the generic renderer is correct");

            when(mmoPlayer.getSkillLevel(s)).thenReturn(1000);
            final List<String> lines = render(SkillStatsRenderer.forSkill(s));
            assertFalse(lines.isEmpty(), s.name() + " still renders a header");
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
