package com.gmail.nossr50.fabric.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.skills.MeleeDamageBonus.MeleeWeapon;
import com.gmail.nossr50.platform.ItemUtils;
import com.gmail.nossr50.util.McTestRegistries;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
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
        for (Item item : BuiltInRegistries.ITEM) {
            if (BuiltInRegistries.ITEM.getKey(item).getPath().endsWith("_spear")) {
                spears.add(item);
            }
        }
        return spears;
    }

    @Test
    void everyVanillaSpearIsClassifiedAsASpear() {
        final List<Item> spears = registeredSpears();

        // Spears arrive in 1.21.11. On the mc/1.21.10 band there are none, and the honest assertion
        // there is a different one — not a skip, which would quietly delete this coverage on every
        // older band at once.
        if (spears.isEmpty()) {
            assertTrue(McTestRegistries.itemRegistryIsPopulated(),
                    "no spears found AND the registry looks empty — that is a broken bootstrap, not "
                            + "a Minecraft version without spears");
            assertTrue(McTestRegistries.optionalVanillaItem("iron_spear").isEmpty(),
                    "registeredSpears() found nothing but iron_spear resolves — the id-path scan is "
                            + "broken, which is exactly how GitHub #7 stayed hidden");
            return;
        }

        assertEquals(7, spears.size(),
                "1.21.11 ships seven spears (wooden/stone/copper/iron/golden/diamond/netherite) — "
                        + "if this count moved, MaterialMapStore#fillSpears needs the new id too");

        for (Item spear : spears) {
            final ItemStack held = new ItemStack(spear);
            assertTrue(ItemUtils.isSpear(held),
                    () -> BuiltInRegistries.ITEM.getKey(spear) + " is not in MaterialMapStore's spear set");
            assertEquals(MeleeWeapon.SPEAR, EntityDamageListener.classifyMainHand(held),
                    () -> BuiltInRegistries.ITEM.getKey(spear) + " must dispatch to the Spears skill");
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
        // Resolved through the registry rather than named as Items.IRON_SPEAR: the constant does not
        // exist below 1.21.11, so naming it makes the whole TEST TREE fail to compile on the
        // mc/1.21.10 band -- a build break, not a red test, and one the Phase 1 probe could not
        // predict because it never indexed static constants or src/test/java.
        final Item ironSpear = McTestRegistries.optionalVanillaItem("iron_spear").orElse(null);
        if (ironSpear == null) {
            assertTrue(McTestRegistries.itemRegistryIsPopulated(),
                    "iron_spear absent AND the registry looks empty — broken bootstrap");
            assertTrue(registeredSpears().isEmpty(),
                    "iron_spear does not resolve but other spears do — inconsistent registry view");
            return;
        }
        assertNotEquals(MeleeWeapon.OTHER,
                EntityDamageListener.classifyMainHand(new ItemStack(ironSpear)),
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
