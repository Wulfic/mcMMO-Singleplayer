package com.gmail.nossr50.datatypes.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.RankConfig;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.movement.Medium;
import com.gmail.nossr50.util.skills.RankUtils;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The coupling that {@link SuperAbilityType#SECOND_WIND}'s <b>nominal</b> sub-skill binding rests on.
 *
 * <p>Second Wind is the one super ability with three sub-skills — {@code PARKOUR_SECOND_WIND},
 * {@code SWIMMING_SECOND_WIND} and {@code FLYING_SECOND_WIND}, one per medium, because a sub-skill's
 * parent is derived from its enum name prefix and no single constant can span three parents. It is
 * still <em>one</em> ability with one cooldown, so {@code subSkillTypeDefinition} can only name one
 * of the three, and it names Parkour's.
 *
 * <p>That single answer is correct for every medium <b>only because all three unlock at the same
 * level.</b> This class is the "and a test says so" that the comment on that binding promises.
 *
 * <p>⚠️ <b>Written 2026-08-17, one phase late.</b> The binding shipped carrying a comment naming this
 * exact class and method, and the class did not exist — a comment that names its own test is not
 * evidence the test exists. Rank plumbing here is the real {@link RankConfig} loaded from the bundled
 * {@code skillranks.yml}, so what is asserted is what ships rather than a mock.
 *
 * <p><b>Not here:</b> "every super ability has a non-null binding" is NOT a property of this enum —
 * five abilities are deliberate placeholders with no sub-skill at all. That distinction, and the
 * non-null assertion over the <em>implemented</em> ones, lives in {@code PlaceholderSuperAbilityTest},
 * which derives the placeholder set instead of hand-keeping it. Asserting it again here was tried and
 * was simply wrong.
 */
class SuperAbilityTypeTest {

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
    }

    /**
     * The guard A-2 depends on: every medium's Second Wind unlocks at the same level as the one the
     * nominal binding names.
     *
     * <p>Deliberately asserts <b>equality with the binding's own answer</b> rather than the literal
     * 250. A literal would still pass if the binding were repointed at some unrelated sub-skill that
     * happens to unlock at 250; equality fails on divergence however it arrives — a re-balanced
     * ladder, a repointed binding, or a fourth medium.
     */
    @Test
    void allSecondWindSubSkillsUnlockAtTheSameLevel() {
        final SubSkillType nominal = SuperAbilityType.SECOND_WIND.getSubSkillTypeDefinition();
        assertNotNull(nominal, "SECOND_WIND must keep a non-null nominal binding");

        final int nominalUnlock = RankUtils.getRankUnlockLevel(nominal, 1);

        for (Medium medium : Medium.values()) {
            final SubSkillType perMedium = medium.secondWindSubSkill();
            assertEquals(nominalUnlock, RankUtils.getRankUnlockLevel(perMedium, 1),
                    () -> "Second Wind is ONE ability with ONE static subSkillTypeDefinition ("
                            + nominal + "), so RankUtils#getSuperAbilityUnlockRequirement gives every "
                            + "medium that one answer. " + medium + " now unlocks at a DIFFERENT "
                            + "level via " + perMedium + ", so that answer is silently wrong for "
                            + medium + ". Either put the three back on the same unlock level, or "
                            + "make the binding medium-aware at both call sites (RankUtils"
                            + "#getSuperAbilityUnlockRequirement and McMMOPlayer"
                            + "#checkAbilityActivation) before changing it.");
        }
    }

    /**
     * The binding must name one of the three real per-medium constants.
     *
     * <p>Separate from the level check on purpose: the level check alone would accept a binding
     * pointed at, say, {@code MINING_SUPER_BREAKER} if that happened to share an unlock level. This
     * pins <em>identity</em>, so the pair together say "the right constant, and the levels agree".
     */
    @Test
    void theNominalBindingIsOneOfTheThreePerMediumSecondWinds() {
        final Set<SubSkillType> perMedium = EnumSet.noneOf(SubSkillType.class);
        for (Medium medium : Medium.values()) {
            perMedium.add(medium.secondWindSubSkill());
        }
        assertEquals(Medium.values().length, perMedium.size(),
                "each medium must own a DISTINCT Second Wind sub-skill; two mediums sharing one "
                        + "constant means one of them is gated on a skill the player does not earn "
                        + "by travelling that way");
        assertTrue(perMedium.contains(SuperAbilityType.SECOND_WIND.getSubSkillTypeDefinition()),
                () -> "SECOND_WIND's nominal binding is "
                        + SuperAbilityType.SECOND_WIND.getSubSkillTypeDefinition()
                        + ", which is not any medium's Second Wind sub-skill (" + perMedium + "). "
                        + "The binding is what RankUtils reports as the ability's unlock "
                        + "requirement, so it must name a constant a player can actually unlock.");
    }

}
