package com.gmail.nossr50.platform;

import com.gmail.nossr50.skills.hunter.HunterManager;
import com.gmail.nossr50.util.text.ConfigStringUtils;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.DefaultAttributeRegistry;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.Registries;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves a creature's Hunter tier — the Minecraft-typed half of D-HU5. {@link HunterManager} owns
 * the rule and the exception lookup; this owns the three reads the rule needs, and the decision about
 * <em>where</em> to read them from.
 *
 * <p>Structured exactly like {@link MobOrigins}, for the same reason: the vocabulary and the
 * arithmetic stay unit-testable without a world, and everything that needs a Minecraft type lives in
 * one small class.
 *
 * <h2>⚠️ The stats come from the ENTITY TYPE, never from the entity that died</h2>
 * {@link DefaultAttributeRegistry} holds each type's base attribute container. Reading the live
 * victim's {@code getMaxHealth()} / {@code getAttributeValue(ATTACK_DAMAGE)} instead would be both
 * wrong and unstable:
 *
 * <ul>
 *   <li><b>Equipment counts toward the live value.</b> A zombie that spawned holding an iron sword
 *       carries that sword's modifier on its own {@code ATTACK_DAMAGE}, so it would out-rank an
 *       identical bare-handed zombie. Tier is a property of the <em>species</em>, not of the
 *       individual, and a player must be able to learn it.</li>
 *   <li><b>Several mobs roll their health on spawn.</b> {@code HorseEntity#initialize} picks a value
 *       from a range; the type's base is a single fixed number.</li>
 *   <li><b>Difficulty and potion effects move the live value</b>, and a hard-mode zombie is not a
 *       different creature.</li>
 * </ul>
 *
 * <p>Modded mobs are covered for free: Fabric's {@code FabricDefaultAttributeRegistry} writes into
 * this same registry, so a modded creature is read exactly like a vanilla one.
 *
 * @see <a href="file:../../../../../../../plans/new-skills/hunter.md">plans/new-skills/hunter.md</a>
 */
public final class MobTiers {

    private MobTiers() {
    }

    /** The Hunter tier of the creature that just died. */
    public static int tierOf(@NotNull LivingEntity entity) {
        return tierOf(entity.getType());
    }

    /**
     * The Hunter tier of a creature type: the operator's override if one is configured, else derived
     * from the type's own spawn group, health and attack damage.
     *
     * <p>A type with no attribute definition at all reads as {@code 0} health and {@code 0} damage
     * and therefore lands in the lowest tier its hostility allows. That is the failure direction the
     * skill wants — see {@link HunterManager#deriveTier}.
     */
    public static int tierOf(@NotNull EntityType<?> type) {
        // SpawnGroup is the type-level spelling of "is this a monster". The instance-level
        // alternative is `entity instanceof Monster`, which CombatUtils#categoryOf uses -- but that
        // needs an entity, and it agrees with SpawnGroup.MONSTER for every vanilla mob anyway
        // (including the awkward ones: shulker is a GolemEntity, hoglin an AnimalEntity, ghast a
        // FlyingEntity, and all three are SpawnGroup.MONSTER).
        final boolean hostile = type.getSpawnGroup() == SpawnGroup.MONSTER;

        double maxHealth = 0.0D;
        double attackDamage = 0.0D;
        if (DefaultAttributeRegistry.hasDefinitionFor(type)) {
            @SuppressWarnings("unchecked")
            final DefaultAttributeContainer attributes =
                    DefaultAttributeRegistry.get((EntityType<? extends LivingEntity>) type);
            maxHealth = attributes.getBaseValue(EntityAttributes.GENERIC_MAX_HEALTH);
            // has() first: ATTACK_DAMAGE is genuinely absent on several types (the ender dragon has
            // none at all), and getBaseValue on a missing attribute is not a question worth asking.
            if (attributes.has(EntityAttributes.GENERIC_ATTACK_DAMAGE)) {
                attackDamage = attributes.getBaseValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
            }
        }

        return HunterManager.resolveTier(configKeyOf(type), hostile, maxHealth, attackDamage);
    }

    /**
     * The key a creature's tier override is filed under: the house per-entity config form,
     * {@code Wither_Skeleton}.
     *
     * <p>⚠️ <b>Deliberately not the full registry id the kill counter uses.</b> The two key spaces
     * answer different questions and the difference is not an oversight:
     *
     * <ul>
     *   <li>The {@code kills:} map is machine-written into the player's profile, is open-ended, and
     *       has to survive two mods shipping a creature of the same name — so it keys on
     *       {@code minecraft:zombie}, namespace included.</li>
     *   <li>This table is <b>hand-written by an operator</b> in {@code advanced.yml}, next to
     *       {@code experience.yml}'s {@code Combat.Multiplier} table which has used this exact form
     *       for a decade. A namespaced key would also need quoting in YAML — {@code minecraft:ghast:
     *       3} is not a thing you can type — which is a poor trade for a collision two mods would
     *       have to go out of their way to cause.</li>
     * </ul>
     */
    static @NotNull String configKeyOf(@NotNull EntityType<?> type) {
        return ConfigStringUtils.getConfigEntityTypeString(
                Registries.ENTITY_TYPE.getId(type).getPath());
    }
}
