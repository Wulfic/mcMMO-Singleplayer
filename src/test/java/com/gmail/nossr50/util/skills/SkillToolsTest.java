package com.gmail.nossr50.util.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.datatypes.skills.ToolType;
import org.junit.jupiter.api.Test;

/**
 * MC-free coverage for the ported {@link SkillTools} relationship registry and the two enums it
 * binds together ({@link SubSkillType}, {@link SuperAbilityType}). All of this is derived from the
 * enum constants + the (English) locale bundle, so it constructs standalone with no world session
 * and no loaded config — the config-backed getters are exercised via {@code GeneralConfigTest}.
 */
class SkillToolsTest {

    private final SkillTools skillTools = new SkillTools();

    @Test
    void everySubSkillResolvesToItsNamePrefixParent() {
        for (SubSkillType subSkill : SubSkillType.values()) {
            PrimarySkillType parent = skillTools.getPrimarySkillBySubSkill(subSkill);
            assertNotNull(parent, () -> subSkill + " has no parent skill");
            String prefix = subSkill.name().substring(0, subSkill.name().indexOf('_'));
            assertTrue(parent.name().equalsIgnoreCase(prefix),
                    () -> subSkill + " -> " + parent + " but prefix was " + prefix);
        }
    }

    @Test
    void primarySkillChildrenContainTheirSubSkills() {
        assertTrue(skillTools.getSubSkills(PrimarySkillType.MINING)
                .contains(SubSkillType.MINING_SUPER_BREAKER));
        assertTrue(skillTools.getSubSkills(PrimarySkillType.SWORDS)
                .contains(SubSkillType.SWORDS_SERRATED_STRIKES));
    }

    @Test
    void everySuperAbilityResolvesToAParent() {
        for (SuperAbilityType ability : SuperAbilityType.values()) {
            assertNotNull(skillTools.getPrimarySkillBySuperAbility(ability),
                    () -> ability + " has no parent skill");
        }
        assertSame(PrimarySkillType.MINING,
                skillTools.getPrimarySkillBySuperAbility(SuperAbilityType.SUPER_BREAKER));
        assertSame(PrimarySkillType.MINING,
                skillTools.getPrimarySkillBySuperAbility(SuperAbilityType.BLAST_MINING));
    }

    @Test
    void mainActivatedAbilityIgnoresBlastMining() {
        // Mining owns both SUPER_BREAKER and BLAST_MINING; the "main" (tool-readying) ability
        // map must resolve to SUPER_BREAKER, not BLAST_MINING.
        assertSame(SuperAbilityType.SUPER_BREAKER,
                skillTools.getSuperAbility(PrimarySkillType.MINING));
    }

    @Test
    void toolMapMatchesLegacyAssignments() {
        assertSame(ToolType.PICKAXE, skillTools.getPrimarySkillToolType(PrimarySkillType.MINING));
        assertSame(ToolType.AXE, skillTools.getPrimarySkillToolType(PrimarySkillType.AXES));
        assertSame(ToolType.AXE, skillTools.getPrimarySkillToolType(PrimarySkillType.WOODCUTTING));
        assertSame(ToolType.FISTS, skillTools.getPrimarySkillToolType(PrimarySkillType.UNARMED));
        // Skills without a readying tool have no mapping.
        assertNull(skillTools.getPrimarySkillToolType(PrimarySkillType.ARCHERY));
    }

    @Test
    void childSkillClassification() {
        assertTrue(SkillTools.isChildSkill(PrimarySkillType.SALVAGE));
        assertTrue(SkillTools.isChildSkill(PrimarySkillType.SMELTING));
        assertFalse(SkillTools.isChildSkill(PrimarySkillType.MINING));
        // 19 skills, 2 of them children.
        assertEquals(2, skillTools.getChildSkills().size());
        assertEquals(17, SkillTools.NON_CHILD_SKILLS.size());
    }

    @Test
    void childSkillParents() {
        assertEquals(SkillTools.SALVAGE_PARENTS,
                skillTools.getChildSkillParents(PrimarySkillType.SALVAGE));
        assertEquals(SkillTools.SMELTING_PARENTS,
                skillTools.getChildSkillParents(PrimarySkillType.SMELTING));
        assertThrows(IllegalArgumentException.class,
                () -> skillTools.getChildSkillParents(PrimarySkillType.MINING));
    }

    @Test
    void combatSkillsPinnedToCurrentGameVersion() {
        // MC 1.21.11 target: Spears + Maces present, 9 combat skills total.
        assertEquals(9, skillTools.getCombatSkills().size());
        assertTrue(skillTools.getCombatSkills().contains(PrimarySkillType.SPEARS));
        assertTrue(skillTools.getCombatSkills().contains(PrimarySkillType.MACES));
    }

    @Test
    void matchSkillIsCaseInsensitiveAndNullForUnknown() {
        assertSame(PrimarySkillType.MINING, skillTools.matchSkill("mining"));
        assertSame(PrimarySkillType.WOODCUTTING, skillTools.matchSkill("WoodCutting"));
        assertNull(skillTools.matchSkill("not_a_skill"));
        assertNull(skillTools.matchSkill("all"));
    }

    @Test
    void superAbilityToStringAndPrettyName() {
        assertEquals("Super_Breaker", SuperAbilityType.SUPER_BREAKER.toString());
        assertEquals("Super Breaker", SuperAbilityType.SUPER_BREAKER.getName());
    }

    @Test
    void subSkillNiceNameStripsParentPrefix() {
        assertEquals("SuperBreaker",
                SubSkillType.MINING_SUPER_BREAKER.getNiceNameNoSpaces(
                        SubSkillType.MINING_SUPER_BREAKER));
        // getParentSkill routes through the lazily-built McMMOMod.getSkillTools().
        assertSame(PrimarySkillType.MINING, SubSkillType.MINING_SUPER_BREAKER.getParentSkill());
    }
}
