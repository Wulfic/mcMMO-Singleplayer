package com.gmail.nossr50.util.random;

import static com.gmail.nossr50.datatypes.skills.PrimarySkillType.ACROBATICS;
import static com.gmail.nossr50.datatypes.skills.PrimarySkillType.MINING;
import static com.gmail.nossr50.datatypes.skills.SubSkillType.ACROBATICS_DODGE;
import static com.gmail.nossr50.datatypes.skills.SubSkillType.AXES_ARMOR_IMPACT;
import static com.gmail.nossr50.datatypes.skills.SubSkillType.AXES_GREATER_IMPACT;
import static com.gmail.nossr50.datatypes.skills.SubSkillType.MINING_DOUBLE_DROPS;
import static com.gmail.nossr50.datatypes.skills.SubSkillType.TAMING_FAST_FOOD_SERVICE;
import static com.gmail.nossr50.datatypes.skills.SubSkillType.UNARMED_ARROW_DEFLECT;
import static com.gmail.nossr50.util.random.ProbabilityTestUtils.assertProbabilityExpectations;
import static com.gmail.nossr50.util.random.ProbabilityUtil.calculateCurrentSkillProbability;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Exercises the Phase 10.2 {@link ProbabilityUtil} port MC-free. The legacy suite extended a
 * MockBukkit {@code MMOTestEnvironment}; here {@link AdvancedConfig} is a Mockito mock wired through
 * {@link McMMOMod#setAdvancedConfig} (replacing {@code mcMMO.p.getAdvancedConfig()}) and the player
 * is a mocked {@link McMMOPlayer} whose skill level is stubbed directly. The dropped
 * {@code SubSkillEvent} fire (no singleplayer listeners) leaves these value/odds assertions intact.
 */
class ProbabilityUtilTest {

    static final double impactChance = 11D;
    static final double greaterImpactChance = 0.007D;
    static final double fastFoodChance = 45.5D;

    private AdvancedConfig advancedConfig;
    private McMMOPlayer mmoPlayer;

    @BeforeEach
    void setUp() {
        advancedConfig = mock(AdvancedConfig.class);
        when(advancedConfig.getImpactChance()).thenReturn(impactChance);
        when(advancedConfig.getGreaterImpactChance()).thenReturn(greaterImpactChance);
        when(advancedConfig.getFastFoodChance()).thenReturn(fastFoodChance);
        McMMOMod.setAdvancedConfig(advancedConfig);

        mmoPlayer = mock(McMMOPlayer.class);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setAdvancedConfig(null);
    }

    /** Stub the player's level for the parent skill so dynamic probability resolves deterministically. */
    private void atSkillLevel(PrimarySkillType skill, int level) {
        when(mmoPlayer.getSkillLevel(skill)).thenReturn(level);
    }

    private static Stream<Arguments> staticChanceSkills() {
        return Stream.of(
                // static probability, % of time for success
                Arguments.of(AXES_ARMOR_IMPACT, impactChance),
                Arguments.of(AXES_GREATER_IMPACT, greaterImpactChance),
                Arguments.of(TAMING_FAST_FOOD_SERVICE, fastFoodChance)
        );
    }

    @ParameterizedTest
    @MethodSource("staticChanceSkills")
    void staticChanceSkillsShouldSucceedAsExpected(SubSkillType subSkillType,
            double expectedWinPercent)
            throws InvalidStaticChance {
        Probability staticRandomChance = ProbabilityUtil.getStaticRandomChance(subSkillType);
        assertProbabilityExpectations(expectedWinPercent, staticRandomChance);
    }

    @Test
    void isSkillRNGSuccessfulShouldBehaveAsExpected() {
        // Given
        when(advancedConfig.getMaximumProbability(UNARMED_ARROW_DEFLECT)).thenReturn(20D);
        when(advancedConfig.getMaxBonusLevel(UNARMED_ARROW_DEFLECT)).thenReturn(0);

        final Probability probability = ProbabilityUtil.getSkillProbability(
                UNARMED_ARROW_DEFLECT, mmoPlayer);
        assertEquals(0.2D, probability.getValue());
        assertProbabilityExpectations(20, probability);
    }

    private static Stream<Arguments> provideSkillProbabilityTestData() {
        return Stream.of(
                // skillLevel, floor, ceiling, maxBonusLevel, expectedValue

                // 20% chance at skill level 1000
                Arguments.of(1000, 0, 20, 1000, 0.2),
                // 10% chance at skill level 500
                Arguments.of(500, 0, 20, 1000, 0.1),
                // 5% chance at skill level 250
                Arguments.of(250, 0, 20, 1000, 0.05),
                // 0% chance at skill level 0
                Arguments.of(0, 0, 20, 1000, 0.0),
                // 0% chance at skill level 1000
                Arguments.of(1000, 0, 0, 1000, 0.0),
                // 1% chance at skill level 1000
                Arguments.of(1000, 0, 1, 1000, 0.01)
        );
    }

    @ParameterizedTest
    @MethodSource("provideSkillProbabilityTestData")
    void testCalculateCurrentSkillProbability(double skillLevel, double floor, double ceiling,
            double maxBonusLevel,
            double expectedValue) {
        // When
        final Probability probability = calculateCurrentSkillProbability(skillLevel, floor, ceiling,
                maxBonusLevel);

        // Then
        assertEquals(expectedValue, probability.getValue());
    }

    @Test
    void getRNGDisplayValuesShouldReturn10PercentForDodge() {
        // Given
        when(advancedConfig.getMaximumProbability(ACROBATICS_DODGE)).thenReturn(20D);
        when(advancedConfig.getMaxBonusLevel(ACROBATICS_DODGE)).thenReturn(1000);
        atSkillLevel(ACROBATICS, 500);

        // When & Then
        final String[] rngDisplayValues = ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                ACROBATICS_DODGE);
        assertEquals("10.00%", rngDisplayValues[0]);
    }

    @Test
    void getRNGDisplayValuesShouldReturn20PercentForDodge() {
        // Given
        when(advancedConfig.getMaximumProbability(ACROBATICS_DODGE)).thenReturn(20D);
        when(advancedConfig.getMaxBonusLevel(ACROBATICS_DODGE)).thenReturn(1000);
        atSkillLevel(ACROBATICS, 1000);

        // When & then
        final String[] rngDisplayValues = ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                ACROBATICS_DODGE);
        assertEquals("20.00%", rngDisplayValues[0]);
    }

    @Test
    void getRNGDisplayValuesShouldReturn0PercentForDodge() {
        // Given
        when(advancedConfig.getMaximumProbability(ACROBATICS_DODGE)).thenReturn(20D);
        when(advancedConfig.getMaxBonusLevel(ACROBATICS_DODGE)).thenReturn(1000);
        atSkillLevel(ACROBATICS, 0);

        // When & then
        final String[] rngDisplayValues = ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                ACROBATICS_DODGE);
        assertEquals("0.00%", rngDisplayValues[0]);
    }

    @Test
    void getRNGDisplayValuesShouldReturn10PercentForDoubleDrops() {
        // Given
        when(advancedConfig.getMaximumProbability(MINING_DOUBLE_DROPS)).thenReturn(100D);
        when(advancedConfig.getMaxBonusLevel(MINING_DOUBLE_DROPS)).thenReturn(1000);
        atSkillLevel(MINING, 100);

        // When & Then
        final String[] rngDisplayValues = ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                MINING_DOUBLE_DROPS);
        assertEquals("10.00%", rngDisplayValues[0]);
    }

    @Test
    void getRNGDisplayValuesShouldReturn50PercentForDoubleDrops() {
        // Given
        when(advancedConfig.getMaximumProbability(MINING_DOUBLE_DROPS)).thenReturn(100D);
        when(advancedConfig.getMaxBonusLevel(MINING_DOUBLE_DROPS)).thenReturn(1000);
        atSkillLevel(MINING, 500);

        // When & Then
        final String[] rngDisplayValues = ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                MINING_DOUBLE_DROPS);
        assertEquals("50.00%", rngDisplayValues[0]);
    }

    @Test
    void getRNGDisplayValuesShouldReturn100PercentForDoubleDrops() {
        // Given
        when(advancedConfig.getMaximumProbability(MINING_DOUBLE_DROPS)).thenReturn(100D);
        when(advancedConfig.getMaxBonusLevel(MINING_DOUBLE_DROPS)).thenReturn(1000);
        atSkillLevel(MINING, 1000);

        // When & Then
        final String[] rngDisplayValues = ProbabilityUtil.getRNGDisplayValues(mmoPlayer,
                MINING_DOUBLE_DROPS);
        assertEquals("100.00%", rngDisplayValues[0]);
    }
}
