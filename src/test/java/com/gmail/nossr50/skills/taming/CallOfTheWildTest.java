package com.gmail.nossr50.skills.taming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.datatypes.skills.subskills.taming.CallOfTheWildType;
import com.gmail.nossr50.datatypes.skills.subskills.taming.TamingSummon;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for the MC-free {@link CallOfTheWild} lookup tables. */
class CallOfTheWildTest {

    private static CallOfTheWild tableOf(TamingSummon... summons) {
        final Map<CallOfTheWildType, TamingSummon> map = new EnumMap<>(CallOfTheWildType.class);
        for (TamingSummon summon : summons) {
            map.put(summon.getCallOfTheWildType(), summon);
        }
        return new CallOfTheWild(map);
    }

    @Test
    void summonForItemMatchesTheConfiguredItem() {
        final CallOfTheWild table = tableOf(
                new TamingSummon(CallOfTheWildType.WOLF, "bone", 10, 1, 240, 2),
                new TamingSummon(CallOfTheWildType.CAT, "cod", 10, 1, 240, 1),
                new TamingSummon(CallOfTheWildType.HORSE, "apple", 10, 1, 240, 1));

        assertEquals(CallOfTheWildType.WOLF,
                table.summonForItem("bone").orElseThrow().getCallOfTheWildType());
        assertEquals(CallOfTheWildType.HORSE,
                table.summonForItem("apple").orElseThrow().getCallOfTheWildType());
        assertTrue(table.summonForItem("stick").isEmpty());
    }

    @Test
    void itemLookupIsCaseInsensitive() {
        final CallOfTheWild table =
                tableOf(new TamingSummon(CallOfTheWildType.WOLF, "bone", 10, 1, 240, 2));

        // A held stack's id ("bone") and a config material ("BONE") must resolve to the same summon.
        assertTrue(table.isCOTWItem("BONE"));
        assertTrue(table.isCOTWItem("bone"));
        assertFalse(table.isCOTWItem("cod"));
    }

    @Test
    void fromConfigReadsEachSectionAndNormalisesTheMaterialName() {
        final GeneralConfig config = mock(GeneralConfig.class);
        stubSummon(config, "Wolf", "BONE", 10, 1, 240, 2);
        stubSummon(config, "Ocelot", "COD", 10, 1, 240, 1);
        stubSummon(config, "Horse", "APPLE", 10, 1, 240, 1);

        final CallOfTheWild table = CallOfTheWild.fromConfig(config);

        // CAT reads the legacy "Ocelot" config section, but a real cod item id is lower-case "cod".
        final TamingSummon cat = table.summonForItem("cod").orElseThrow();
        assertEquals(CallOfTheWildType.CAT, cat.getCallOfTheWildType());
        assertEquals("cod", cat.getItemId());
        assertEquals(1, cat.getSummonCap());

        final TamingSummon wolf = table.getSummon(CallOfTheWildType.WOLF);
        assertEquals("bone", wolf.getItemId());
        assertEquals(10, wolf.getItemAmountRequired());
        assertEquals(2, wolf.getSummonCap());
    }

    private static void stubSummon(GeneralConfig config, String entry, String material, int cost,
            int amount, int length, int cap) {
        when(config.getTamingCOTWMaterialName(entry)).thenReturn(material);
        when(config.getTamingCOTWCost(entry)).thenReturn(cost);
        when(config.getTamingCOTWAmount(entry)).thenReturn(amount);
        when(config.getTamingCOTWLength(entry)).thenReturn(length);
        when(config.getTamingCOTWMaxAmount(entry)).thenReturn(cap);
    }
}
