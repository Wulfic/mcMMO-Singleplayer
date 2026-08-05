package com.gmail.nossr50.fabric.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.skills.MeleeDamageBonus.MeleeWeapon;
import com.gmail.nossr50.util.ItemUtils;
import com.gmail.nossr50.util.McTestRegistries;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link EntityDamageListener#classifyMainHand}, the held-item → weapon-skill dispatch that
 * decides which skill's bonus damage, on-hit effects and per-hit XP a melee swing pays.
 *
 * <p><b>Why this exists.</b> GitHub #7. The Spears arm was missing, on a comment (and a wiki page)
 * asserting that no spear item existed in 1.21.11 — an MC fact that was true once and never
 * re-checked. Everything else in the skill was built and shipped: the manager, the ranks, the config
 * block, the {@code /mcstats} renderer, the milestone advancements, the locale strings. All of it was
 * unreachable because a spear classified as {@link MeleeWeapon#OTHER}, which
 * {@code applyAttackerWeaponBonus} returns on immediately — so the skill paid no XP either, and could
 * never leave level 0.
 *
 * <p>{@link #everyVanillaSpearIsClassifiedAsASpear()} is deliberately driven off
 * {@link Registries#ITEM} rather than a hand-written list of the seven ids: that is the shape of
 * assertion that would have caught the original defect, and it goes red on its own if Mojang adds an
 * eighth spear rather than waiting for someone to re-check the belief.
 */
class MeleeWeaponClassificationTest {

    @BeforeAll
    static void bootstrap() {
        McTestRegistries.bootstrap();
    }

    /** Every registered item whose id path ends in {@code _spear} — vanilla's seven, from the registry. */
    private static List<Item> registeredSpears() {
        final List<Item> spears = new ArrayList<>();
        for (Item item : Registries.ITEM) {
            if (Registries.ITEM.getId(item).getPath().endsWith("_spear")) {
                spears.add(item);
            }
        }
        return spears;
    }

    @Test
    void everyVanillaSpearIsClassifiedAsASpear() {
        final List<Item> spears = registeredSpears();
        assertEquals(7, spears.size(),
                "1.21.11 ships seven spears (wooden/stone/copper/iron/golden/diamond/netherite) — "
                        + "if this count moved, MaterialMapStore#fillSpears needs the new id too");

        for (Item spear : spears) {
            final ItemStack held = new ItemStack(spear);
            assertTrue(ItemUtils.isSpear(held),
                    () -> Registries.ITEM.getId(spear) + " is not in MaterialMapStore's spear set");
            assertEquals(MeleeWeapon.SPEAR, EntityDamageListener.classifyMainHand(held),
                    () -> Registries.ITEM.getId(spear) + " must dispatch to the Spears skill");
        }
    }

    /**
     * The exact failure the reporter hit: a spear used to fall through every arm to {@code OTHER},
     * and {@code applyAttackerWeaponBonus} bails out on {@code OTHER} before the XP call. Asserted
     * separately from the positive case so deleting the {@code isSpear} arm cannot be papered over by
     * a mapping change elsewhere.
     */
    @Test
    void aSpearIsNotAnUnrecognisedItem() {
        assertNotEquals(MeleeWeapon.OTHER,
                EntityDamageListener.classifyMainHand(new ItemStack(Items.IRON_SPEAR)),
                "a spear must not fall through to OTHER — that pays no bonus and no XP");
    }

    /**
     * The reference point: the spear arm must not have widened anything else. {@code isUnarmed}
     * matches any non-tool item with {@code Unarmed_Items_As_Unarmed} on, so an arm inserted in the
     * wrong place is easy to hide — these pin that the other five answers are unchanged.
     */
    @Test
    void theOtherWeaponArmsAreUnchanged() {
        assertEquals(MeleeWeapon.SWORD,
                EntityDamageListener.classifyMainHand(new ItemStack(Items.IRON_SWORD)));
        assertEquals(MeleeWeapon.AXE,
                EntityDamageListener.classifyMainHand(new ItemStack(Items.DIAMOND_AXE)));
        assertEquals(MeleeWeapon.MACE,
                EntityDamageListener.classifyMainHand(new ItemStack(Items.MACE)));
        assertEquals(MeleeWeapon.TRIDENT,
                EntityDamageListener.classifyMainHand(new ItemStack(Items.TRIDENT)));
        assertEquals(MeleeWeapon.OTHER,
                EntityDamageListener.classifyMainHand(new ItemStack(Items.DIAMOND_PICKAXE)),
                "a pickaxe is not a weapon mcMMO trains");
    }
}
