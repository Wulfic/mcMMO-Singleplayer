package com.gmail.nossr50.util.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.CoreSkillsConfig;
import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.RankConfig;
import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.player.PlayerProfile;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.datatypes.skills.ToolType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.player.UserManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The per-skill enable/disable switch (GitHub #10) end to end.
 *
 * <p>The issue's own framing is that "a half-disabled skill is worse than none", so the suite is
 * organised by the six things disabling has to close — XP, sub-skill procs, super abilities, the XP
 * bar, {@code /mcstats} and milestone plaques — rather than by class. Almost every case is asserted
 * <em>off its reference point</em> as well (the same action, with the skill left on, still works),
 * because a gate that refuses everything satisfies the negative half on its own.
 */
class SkillGatingTest {

    private static final UUID UID = UUID.fromString("00000000-0000-0000-0000-0000000000c0");

    private Path dataFolder;
    private ExperienceConfig experienceConfig;
    private PlatformPlayer player;
    private PlayerProfile profile;
    private McMMOPlayer mmoPlayer;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        dataFolder = tempDir;
        experienceConfig = new ExperienceConfig(dataFolder);
        McMMOMod.setExperienceConfig(experienceConfig);
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));
        RankUtils.resetRankCache();

        player = mock(PlatformPlayer.class);
        when(player.getName()).thenReturn("TestPlayer");
        when(player.getUniqueId()).thenReturn(UID);
        when(player.isCreative()).thenReturn(false);
        // Neutralise the "supers only activate while sneaking" config gate, so the super-ability
        // cases below fail for the reason under test and not for that one.
        when(player.isSneaking()).thenReturn(true);

        profile = new PlayerProfile("TestPlayer", UID, 0);
        mmoPlayer = new McMMOPlayer(player, profile);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setExperienceConfig(null);
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setAdvancedConfig(null);
        McMMOMod.setRankConfig(null);
        McMMOMod.setCoreSkillsConfig(null);
        RankUtils.resetRankCache();
        UserManager.clearAll();
    }

    /**
     * Wire a {@code coreskills.yml} with exactly {@code disabled} switched off.
     *
     * <p>Written to disk before the config is constructed so it goes through the real load path —
     * {@code copyMissingDefaults} then back-fills every skill this file does not mention as enabled,
     * which is also what a returning player's part-written file looks like.
     */
    private void disable(PrimarySkillType... disabled) throws IOException {
        final StringBuilder yaml = new StringBuilder();
        for (PrimarySkillType skill : disabled) {
            yaml.append(com.gmail.nossr50.util.text.StringUtils.getCapitalized(skill.toString()))
                    .append(":\n    Enabled: false\n");
        }
        Files.writeString(dataFolder.resolve("coreskills.yml"), yaml.toString(),
                StandardCharsets.UTF_8);
        McMMOMod.setCoreSkillsConfig(new CoreSkillsConfig(dataFolder));
    }

    /** Raw XP worth just over one level in {@code skill}, after the config's XP modifiers. */
    private float oneLevelOfXp(PrimarySkillType skill) {
        final double modifier = experienceConfig.getFormulaSkillModifier(skill)
                * experienceConfig.getExperienceGainsGlobalMultiplier();
        return (float) (mmoPlayer.getXpToLevel(skill) / modifier) + 1f;
    }

    // --- the switch itself --------------------------------------------------

    @Test
    void everythingIsEnabledWhenNoConfigIsWired() {
        McMMOMod.setCoreSkillsConfig(null);

        // The failure direction matters more than it looks: this is the state of every MC-free unit
        // test, the headless boot and the gap between world sessions. Failing closed here would
        // silently switch the whole mod off exactly where nobody asked for anything to be off.
        for (PrimarySkillType skill : PrimarySkillType.values()) {
            assertTrue(SkillGating.isSkillEnabled(skill), skill + " with no config should be on");
        }
    }

    @Test
    void readsTheDisabledSkillAndLeavesTheRestAlone() throws IOException {
        disable(PrimarySkillType.MINING);

        assertFalse(SkillGating.isSkillEnabled(PrimarySkillType.MINING));
        // The reference point: disabling one skill must not disable its neighbours.
        assertTrue(SkillGating.isSkillEnabled(PrimarySkillType.WOODCUTTING));
        assertTrue(SkillGating.isSkillEnabled(PrimarySkillType.HERBALISM));
    }

    @Test
    void everyPrimarySkillHasAnEntryInTheShippedConfig() {
        final CoreSkillsConfig config = new CoreSkillsConfig(dataFolder);

        // Driven off the enum, never off a hand-kept list: a skill added later with no coreskills.yml
        // entry is the failure this pins. It still *defaults* to enabled, so the bug would otherwise
        // be invisible — the player simply would not find a switch for it.
        for (PrimarySkillType skill : PrimarySkillType.values()) {
            assertTrue(Files.exists(config.getFile()));
            assertTrue(config.isPrimarySkillEnabled(skill), skill + " should ship enabled");
        }
        final String shipped = readShippedDefault();
        for (PrimarySkillType skill : PrimarySkillType.values()) {
            assertTrue(shipped.contains(
                            com.gmail.nossr50.util.text.StringUtils.getCapitalized(skill.toString())
                                    + ":"),
                    "coreskills.yml is missing a section for " + skill);
        }
    }

    private String readShippedDefault() {
        try (var in = CoreSkillsConfig.class.getResourceAsStream("/coreskills.yml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("bundled coreskills.yml unreadable", e);
        }
    }

    @Test
    void aSubSkillFollowsItsParentSkill() throws IOException {
        disable(PrimarySkillType.MINING);

        assertFalse(SkillGating.isSubSkillEnabled(SubSkillType.MINING_DOUBLE_DROPS));
        assertTrue(SkillGating.isSubSkillEnabled(SubSkillType.WOODCUTTING_HARVEST_LUMBER));
    }

    // --- funnel 1: XP -------------------------------------------------------

    @Test
    void aDisabledSkillEarnsNoXp() throws IOException {
        disable(PrimarySkillType.MINING);

        mmoPlayer.beginXpGain(PrimarySkillType.MINING, oneLevelOfXp(PrimarySkillType.MINING),
                XPGainReason.PVE, XPGainSource.SELF);

        assertEquals(0, mmoPlayer.getSkillLevel(PrimarySkillType.MINING));
        assertEquals(0, mmoPlayer.getSkillXpLevelRaw(PrimarySkillType.MINING));
    }

    @Test
    void anEnabledSkillStillEarnsXpWhileAnotherIsDisabled() throws IOException {
        // The reference point for the case above: the gate must refuse the disabled skill only.
        disable(PrimarySkillType.MINING);

        mmoPlayer.beginXpGain(PrimarySkillType.WOODCUTTING,
                oneLevelOfXp(PrimarySkillType.WOODCUTTING), XPGainReason.PVE, XPGainSource.SELF);

        assertEquals(1, mmoPlayer.getSkillLevel(PrimarySkillType.WOODCUTTING));
    }

    @Test
    void aChildSkillGainSkipsOnlyTheDisabledParent() throws IOException {
        // A child skill earns no XP of its own, so a gain aimed at it is split across its parents.
        // A disabled Repair must refuse its share without swallowing Fishing's.
        // (Carried by Salvage since Agility, the three-parent case, was retired on 2026-08-17.)
        disable(PrimarySkillType.REPAIR);

        mmoPlayer.beginXpGain(PrimarySkillType.SALVAGE,
                2f * oneLevelOfXp(PrimarySkillType.FISHING), XPGainReason.PVE, XPGainSource.SELF);

        assertEquals(0, mmoPlayer.getSkillLevel(PrimarySkillType.REPAIR),
                "the disabled parent takes none of the split");
        assertTrue(mmoPlayer.getSkillLevel(PrimarySkillType.FISHING) >= 1,
                "an enabled parent still takes its share");
    }

    @Test
    void aDisabledChildSkillPaysNoneOfItsParents() throws IOException {
        // The other direction: switching the child off must not quietly redirect its XP into
        // parents that are still on. Nothing is earned at all.
        disable(PrimarySkillType.SALVAGE);

        mmoPlayer.beginXpGain(PrimarySkillType.SALVAGE,
                2f * oneLevelOfXp(PrimarySkillType.FISHING), XPGainReason.PVE, XPGainSource.SELF);

        assertEquals(0, mmoPlayer.getSkillLevel(PrimarySkillType.REPAIR));
        assertEquals(0, mmoPlayer.getSkillLevel(PrimarySkillType.FISHING));
    }

    @Test
    void theDirectApplyXpGainEntryPointIsGatedToo() throws IOException {
        // The XP pipeline has two public entry points that each own a copy of the child-skill split,
        // so it needs two gates and neither covers the other:
        //
        //   * beginXpGain's gate is what aDisabledChildSkillPaysNoneOfItsParents proves is needed —
        //     its split recurses into beginXpGain and never reaches applyXpGain;
        //   * applyXpGain's gate is what this proves is needed — it is public, it is what
        //     SkillManager-driven awards ultimately land on, and it can be called directly.
        //
        // Without this case, deleting applyXpGain's gate would leave the suite green.
        disable(PrimarySkillType.MINING);

        mmoPlayer.applyXpGain(PrimarySkillType.MINING, oneLevelOfXp(PrimarySkillType.MINING),
                XPGainReason.PVE, XPGainSource.SELF);

        assertEquals(0, mmoPlayer.getSkillLevel(PrimarySkillType.MINING));
        assertEquals(0, mmoPlayer.getSkillXpLevelRaw(PrimarySkillType.MINING));
    }

    // --- the frozen-parent ruling -------------------------------------------

    @Test
    void aDisabledParentStillCountsTowardItsChildAtItsFrozenLevel() throws IOException {
        // ⚠️ THE RULING (2026-08-05), and the reason it is not merely a detail. It was taken for
        // Agility, which was retired on 2026-08-17; the rule belongs to child skills as a class and
        // Salvage carries it now. The DIVISOR is the parent count and stays fixed: a disabled parent
        // keeps contributing whatever level it had reached, it simply stops climbing. So disabling a
        // skill can never raise a child's level, and can never lower it either.
        profile.addLevels(PrimarySkillType.REPAIR, 90);
        disable(PrimarySkillType.REPAIR);

        assertEquals(45, mmoPlayer.getSkillLevel(PrimarySkillType.SALVAGE),
                "(90 + 0) / 2 — the disabled parent keeps its frozen contribution");
        // The exploit this closes: dropping the disabled parent out of the average instead would read
        // 90 here, handing out 45 free Salvage levels and the perks gated behind them for switching a
        // skill OFF.
        assertNotEquals(90, mmoPlayer.getSkillLevel(PrimarySkillType.SALVAGE),
                "a disabled parent must not be excluded from the mean");
    }

    @Test
    void aDisabledParentStopsRaisingItsChild() throws IOException {
        profile.addLevels(PrimarySkillType.REPAIR, 90);
        disable(PrimarySkillType.REPAIR);

        mmoPlayer.beginXpGain(PrimarySkillType.REPAIR, 100f * oneLevelOfXp(PrimarySkillType.REPAIR),
                XPGainReason.PVE, XPGainSource.SELF);

        assertEquals(90, mmoPlayer.getSkillLevel(PrimarySkillType.REPAIR), "frozen, not reset");
        assertEquals(45, mmoPlayer.getSkillLevel(PrimarySkillType.SALVAGE));
    }

    // --- funnel 2: sub-skill procs -----------------------------------------

    @Test
    void rankBooleansRefuseADisabledSkill() throws IOException {
        profile.addLevels(PrimarySkillType.TRIDENTS, 1000);
        UserManager.track(mmoPlayer);
        assertTrue(RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.TRIDENTS_IMPALE),
                "reference point: maxed and enabled, Impale is unlocked");

        disable(PrimarySkillType.TRIDENTS);

        assertFalse(RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.TRIDENTS_IMPALE));
        assertFalse(RankUtils.hasReachedRank(1, mmoPlayer, SubSkillType.TRIDENTS_IMPALE));
    }

    @Test
    void theRankNumberItselfKeepsTellingTheTruth() throws IOException {
        // ⚠️ THE LANDMINE THIS DESIGN AVOIDS. The tempting shortcut is to make getRank answer 0 for a
        // disabled skill and let every rank gate fail on its own. Several AdvancedConfig getters index
        // a defaults array by rank - 1 while evaluating the fallback eagerly, so they throw at rank 0
        // (§F #9, Cripple, Spears' Momentum — three sightings). A fresh player never reaches those
        // call sites because an outer gate checks their skill LEVEL first; a level-1000 player who
        // disables the skill keeps that level and sails straight into them.
        //
        // So the gating is on the booleans only. This pins that.
        profile.addLevels(PrimarySkillType.TRIDENTS, 1000);
        disable(PrimarySkillType.TRIDENTS);

        assertTrue(RankUtils.getRank(mmoPlayer, SubSkillType.TRIDENTS_IMPALE) > 0,
                "getRank must NOT be forced to 0 — rank-indexed config getters throw there");
    }

    @Test
    void permissionPredicatesFollowTheSwitch() throws IOException {
        // ⚠️ GitHub #11 MADE THIS TEST'S canUseSubSkill LINE VACUOUS, AND ONLY THE FIX EXPOSED IT.
        // That predicate is now `enabled AND unlocked`, and this fixture's player is level 0 — so
        // the assertion below would have gone on passing at rank 0 with the switch deleted entirely.
        // Levelling Mining first is what makes it an assertion about the switch again. 🔑 A gate
        // gaining a second conjunct silently re-points every test that only asserted its negative.
        profile.addLevels(PrimarySkillType.MINING, 1000);
        UserManager.track(mmoPlayer);
        assertTrue(Permissions.canUseSubSkill(player, SubSkillType.MINING_DOUBLE_DROPS),
                "reference point: levelled and enabled, the sub-skill is usable");

        disable(PrimarySkillType.MINING, PrimarySkillType.UNARMED);

        assertFalse(Permissions.isSubSkillEnabled(player, SubSkillType.MINING_DOUBLE_DROPS));
        assertFalse(Permissions.canUseSubSkill(player, SubSkillType.MINING_DOUBLE_DROPS));
        assertFalse(Permissions.biggerBombs(player));
        assertFalse(Permissions.demolitionsExpertise(player));
        assertFalse(Permissions.remoteDetonation(player));
        assertFalse(Permissions.berserk(player));

        // Reference points: the untouched skills' predicates still answer yes.
        assertTrue(Permissions.isSubSkillEnabled(player, SubSkillType.WOODCUTTING_HARVEST_LUMBER));
        assertTrue(Permissions.greenTerra(player));
        assertTrue(Permissions.serratedStrikes(player));
        assertTrue(Permissions.skullSplitter(player));
    }

    @Test
    void canUseSubSkillAlsoRequiresTheUnlock() {
        // ⚠️ GitHub #11. The port replaced legacy's
        // `isSubSkillEnabled(player, sub) && RankUtils.hasUnlockedSubskill(player, sub)` with only
        // the first conjunct — the permission node was doing two jobs and one of them was dropped.
        // The three assertions below are the three states that distinguishes; the middle one is the
        // state that shipped Mining's Mother Lode from level 1.
        UserManager.track(mmoPlayer);

        assertTrue(Permissions.isSubSkillEnabled(player, SubSkillType.MINING_MOTHER_LODE),
                "the parent skill is switched on");
        assertFalse(Permissions.canUseSubSkill(player, SubSkillType.MINING_MOTHER_LODE),
                "…but at level 0 Mother Lode is not unlocked, so it may not be used");

        profile.addLevels(PrimarySkillType.MINING, 1000); // Mother Lode rank 1 (RetroMode).

        assertTrue(Permissions.canUseSubSkill(player, SubSkillType.MINING_MOTHER_LODE),
                "reference point: at the unlock level it IS usable — the gate is not simply always "
                        + "false, which is how the fix could 'pass' by breaking the sub-skill");
    }

    @Test
    void perkNodesStayUngrantableRegardlessOfTheSwitch() throws IOException {
        // The perk nodes were never grantable in singleplayer, and a skill being switched off is not
        // a reason to start granting one. Pins that the #10 pass did not reach into them.
        disable(PrimarySkillType.MINING);

        assertFalse(Permissions.lucky(player, PrimarySkillType.MINING));
        assertFalse(Permissions.lucky(player, PrimarySkillType.WOODCUTTING));
        assertFalse(Permissions.arcaneBypass(player));
        assertFalse(Permissions.hasRepairEnchantBypassPerk(player));
    }

    // --- funnel 3: super abilities -----------------------------------------

    @Test
    void aDisabledSkillReadiesNoTool() throws IOException {
        disable(PrimarySkillType.MINING);

        mmoPlayer.processAbilityActivation(PrimarySkillType.MINING);

        assertFalse(mmoPlayer.getToolPreparationMode(ToolType.PICKAXE),
                "a skill that is off must not announce 'you ready your pickaxe'");
    }

    @Test
    void aDisabledSkillCannotActivateItsSuperAbility() throws IOException {
        profile.addLevels(PrimarySkillType.MINING, 1000);
        disable(PrimarySkillType.MINING);

        mmoPlayer.checkAbilityActivation(PrimarySkillType.MINING);

        assertFalse(mmoPlayer.getAbilityMode(SuperAbilityType.SUPER_BREAKER),
                "maxed but disabled: Super Breaker must not switch on");
    }

    // --- funnel 6: milestone plaques ---------------------------------------

    @Test
    void aDisabledChildSkillFiresNoPlaqueWhenItsParentLevels() throws IOException {
        // The one plaque route the XP gate does not close by itself. A child's level is derived from
        // its parents and climbs without any XP of its own ever being applied, so a disabled child
        // would keep plaquing off an enabled parent's level-ups. Mirrors
        // McMMOPlayerTest#levellingAParentAlsoFiresItsChildSkillsMilestonePlaques, which pins the
        // enabled case on the same pair. (Carried by Salvage since Agility was retired 2026-08-17.)
        //
        // Repair 599 -> 600 takes Salvage 299 -> 300, so BOTH cross a 100-level plaque interval in
        // one gain -- which is what makes the suppressed half a real assertion rather than a gap.
        disable(PrimarySkillType.SALVAGE);
        profile.addLevels(PrimarySkillType.REPAIR, 599);
        assertEquals(299, mmoPlayer.getSkillLevel(PrimarySkillType.SALVAGE),
                "the derived level itself is untouched — only the plaque is suppressed");

        mmoPlayer.beginXpGain(PrimarySkillType.REPAIR, oneLevelOfXp(PrimarySkillType.REPAIR),
                XPGainReason.PVE, XPGainSource.SELF);

        assertEquals(600, mmoPlayer.getSkillLevel(PrimarySkillType.REPAIR));
        verify(player).grantMilestoneAdvancement("level/repair/expert", true);
        verify(player, never()).grantMilestoneAdvancement("level/salvage/adept", true);
    }
}
