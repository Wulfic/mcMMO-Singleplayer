package com.gmail.nossr50.datatypes.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.util.skills.SkillTools;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises the ported {@link PlayerProfile} data model (skill levels, XP, cooldowns, diminished-
 * returns queue) MC-free. {@link ExperienceConfig} is wired through {@link McMMOMod} because the XP
 * paths ({@code registerXpGain}, {@code getXpToLevel}) read it; {@link McMMOMod#getGeneralConfig()}
 * is left un-wired, so the non-cumulative Standard curve is exercised.
 *
 * <p>Child skills (SALVAGE/SMELTING) have no entry in the skill/XP maps, so
 * {@code getSkillLevel}/{@code getSkillXpLevelRaw} on a child are intentionally not called here —
 * {@code getChildSkillLevel} additionally reads the (un-wired) {@code GeneralConfig} level cap.
 */
class PlayerProfileTest {

    private static final UUID UID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void wireExperienceConfig(@TempDir Path dataFolder) {
        McMMOMod.setExperienceConfig(new ExperienceConfig(dataFolder));
    }

    @Test
    void freshProfileStartsAtZeroLevelAndXp() {
        final PlayerProfile profile = new PlayerProfile("p", UID, 0);
        assertEquals(0, profile.getSkillLevel(PrimarySkillType.MINING));
        assertEquals(0, profile.getSkillXpLevel(PrimarySkillType.MINING));
        assertEquals(0F, profile.getSkillXpLevelRaw(PrimarySkillType.MINING));
    }

    @Test
    void startingLevelAppliesToEveryNonChildSkill() {
        final PlayerProfile profile = new PlayerProfile("p", UID, 5);
        for (PrimarySkillType skill : SkillTools.NON_CHILD_SKILLS) {
            assertEquals(5, profile.getSkillLevel(skill), skill + " should start at 5");
        }
    }

    @Test
    void addXpAccumulatesRawAndFlooredXp() {
        final PlayerProfile profile = new PlayerProfile("p", UID, 0);
        profile.addXp(PrimarySkillType.MINING, 123.5F);
        assertEquals(123.5F, profile.getSkillXpLevelRaw(PrimarySkillType.MINING));
        assertEquals(123, profile.getSkillXpLevel(PrimarySkillType.MINING));
    }

    @Test
    void addLevelsAndModifySkillClampAndResetXp() {
        final PlayerProfile profile = new PlayerProfile("p", UID, 5);
        profile.addLevels(PrimarySkillType.MINING, 3);
        assertEquals(8, profile.getSkillLevel(PrimarySkillType.MINING));

        profile.setSkillXpLevel(PrimarySkillType.MINING, 50F);
        profile.modifySkill(PrimarySkillType.MINING, 10);
        assertEquals(10, profile.getSkillLevel(PrimarySkillType.MINING));
        assertEquals(0F, profile.getSkillXpLevelRaw(PrimarySkillType.MINING),
                "modifySkill resets XP to 0");

        profile.modifySkill(PrimarySkillType.MINING, -1);
        assertEquals(0, profile.getSkillLevel(PrimarySkillType.MINING), "levels never go negative");
    }

    @Test
    void removeXpReducesRawXp() {
        final PlayerProfile profile = new PlayerProfile("p", UID, 0);
        profile.setSkillXpLevel(PrimarySkillType.MINING, 100F);
        profile.removeXp(PrimarySkillType.MINING, 40F);
        assertEquals(60F, profile.getSkillXpLevelRaw(PrimarySkillType.MINING));
    }

    @Test
    void uniqueDataAndChimaeraWingRoundTrip() {
        final PlayerProfile profile = new PlayerProfile("p", UID, 0);
        assertEquals(0, profile.getChimaerWingDATS());
        profile.setUniqueData(UniqueDataType.CHIMAERA_WING_DATS, 42);
        assertEquals(42, profile.getUniqueData(UniqueDataType.CHIMAERA_WING_DATS));
        assertEquals(42, profile.getChimaerWingDATS());
    }

    @Test
    void abilityCooldownsDefaultToZero() {
        final PlayerProfile profile = new PlayerProfile("p", UID, 0);
        for (SuperAbilityType ability : SuperAbilityType.values()) {
            assertEquals(0, profile.getAbilityDATS(ability), ability + " DATS should default to 0");
        }
    }

    @Test
    void registeredXpGainSurvivesPurgeUntilItExpires() {
        final PlayerProfile profile = new PlayerProfile("p", UID, 0);
        profile.registerXpGain(PrimarySkillType.MINING, 10F);
        assertEquals(10F, profile.getRegisteredXpGain(PrimarySkillType.MINING));
        // The diminished-returns window (from experience.yml) is minutes long, so nothing has
        // expired yet — the rolling total is unchanged after a purge.
        profile.purgeExpiredXpGains();
        assertEquals(10F, profile.getRegisteredXpGain(PrimarySkillType.MINING));
    }

    @Test
    void getXpToLevelDelegatesToFormulaManagerOnNonCumulativeCurve() {
        final PlayerProfile profile = new PlayerProfile("p", UID, 0);
        final int expected = McMMOMod.getFormulaManager()
                .getXPtoNextLevel(0, McMMOMod.getExperienceConfig().getFormulaType());
        assertTrue(expected > 0, "level-0 XP-to-next should be positive");
        assertEquals(expected, profile.getXpToLevel(PrimarySkillType.MINING));
    }

    @Test
    void childSkillXpLevelIsZeroAndAddXpSplitsToParents() {
        final PlayerProfile profile = new PlayerProfile("p", UID, 0);
        assertEquals(0, profile.getSkillXpLevel(PrimarySkillType.SMELTING));

        profile.addXp(PrimarySkillType.SMELTING, 100F);
        float distributed = 0F;
        for (PrimarySkillType parent
                : McMMOMod.getSkillTools().getChildSkillParents(PrimarySkillType.SMELTING)) {
            distributed += profile.getSkillXpLevelRaw(parent);
        }
        assertEquals(100F, distributed, 0.001F, "child XP is divided across its parent skills");
    }
}
