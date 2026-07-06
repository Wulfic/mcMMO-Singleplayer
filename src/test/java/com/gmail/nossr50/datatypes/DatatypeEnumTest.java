package com.gmail.nossr50.datatypes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.gmail.nossr50.datatypes.experience.FormulaType;
import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import org.junit.jupiter.api.Test;

/**
 * MC-free unit coverage for the ported core datatype vocabulary (Phase 10 groundwork).
 * These enums carry the skill / experience vocabulary that commands and skill modules build on.
 */
class DatatypeEnumTest {

    @Test
    void primarySkillTypeHasAllNineteenSkills() {
        // Guards against an accidental add/drop when the enum is touched later.
        assertEquals(19, PrimarySkillType.values().length);
    }

    @Test
    void xpGainReasonLookupIsCaseInsensitive() {
        assertSame(XPGainReason.PVE, XPGainReason.getXPGainReason("pve"));
        assertSame(XPGainReason.PVE, XPGainReason.getXPGainReason("PVE"));
        assertSame(XPGainReason.COMMAND, XPGainReason.getXPGainReason("CoMmAnD"));
    }

    @Test
    void xpGainReasonReturnsNullForUnknown() {
        assertNull(XPGainReason.getXPGainReason("not_a_reason"));
    }

    @Test
    void formulaTypeParsesKnownValues() {
        assertSame(FormulaType.LINEAR, FormulaType.getFormulaType("LINEAR"));
        assertSame(FormulaType.EXPONENTIAL, FormulaType.getFormulaType("EXPONENTIAL"));
    }

    @Test
    void formulaTypeFallsBackToUnknown() {
        // Legacy contract: unparseable input degrades to UNKNOWN rather than throwing.
        assertSame(FormulaType.UNKNOWN, FormulaType.getFormulaType("garbage"));
        // Case-sensitive by design (valueOf), so lowercase is not a match.
        assertSame(FormulaType.UNKNOWN, FormulaType.getFormulaType("linear"));
    }

    @Test
    void notificationTypeToStringUsesNiceName() {
        assertEquals("ExperienceGain", NotificationType.XP_GAIN.toString());
        assertEquals("LevelUps", NotificationType.LEVEL_UP_MESSAGE.toString());
    }
}
