package com.gmail.nossr50.platform;

import com.gmail.nossr50.fabric.McMMOMod;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

/**
 * The single owner of every {@link EntityAttributeModifier} mcMMO applies to an entity (F2).
 *
 * <p>Continuous-state skills — Agility's Fleet Footed, later Stealth's Padfoot and Unarmored — buff
 * a player only <em>while</em> some condition holds, which means something has to take the buff back
 * off again. A modifier that outlives its condition is a permanent, stacking, save-game-visible
 * buff, and it is by a wide margin the most common way this class of feature breaks. Routing every
 * one of them through this service means there is exactly one place that knows what mcMMO has
 * applied and exactly one place that can clear it.
 *
 * <p>Three properties make that safe:
 * <ul>
 *   <li><b>Identity, not accumulation.</b> Each buff has a stable {@link Identifier}
 *       ({@link Managed}); re-applying replaces the existing modifier in place rather than adding a
 *       second one, so a per-tick caller cannot stack itself into orbit. Re-applying the same value
 *       is a no-op, which matters because this runs 20×/s.</li>
 *   <li><b>Temporary, never persistent.</b> {@link EntityAttributeInstance#addTemporaryModifier}
 *       writes to a different map than {@code persistentModifiers}, and only the latter is
 *       serialized (bytecode-verified). So even a modifier this service somehow fails to remove dies
 *       with the entity instead of being written into the save file, where no later code fix could
 *       reach it. That is the difference between a bug and an unrecoverable save.</li>
 *   <li><b>Re-derived, never assumed.</b> Callers must re-apply from live state every tick rather
 *       than tracking "is it on?" themselves. Respawning and leaving the End both construct a
 *       <em>new</em> {@link ServerPlayerEntity} ({@code PlayerManager#respawnPlayer}), silently
 *       discarding every modifier on the old one — so cached "already applied" state goes wrong on
 *       the first death, while re-deriving self-heals on the next tick.</li>
 * </ul>
 *
 * <p>Not every buff belongs here: Fleet Footed's air (elytra) body writes velocity directly, because
 * gliding is velocity-driven with no attribute behind it ({@code LivingEntity#travelGliding},
 * bytecode-verified). Attributes only.
 */
public final class SkillAttributeService {

    private SkillAttributeService() {
    }

    /**
     * Every attribute modifier mcMMO can apply, as (attribute, id) pairs.
     *
     * <p>Enumerating them is what makes {@link #clearAll(LivingEntity)} possible: teardown has
     * to be able to remove a buff without knowing which skill applied it or why. Adding a managed
     * buff means adding a constant here — if it is not in this enum, it is not cleaned up on logout.
     */
    public enum Managed {
        /**
         * Parkour → Fleet Footed, land body. A percentage bonus on top of the vanilla sprint
         * multiplier, live only while sprinting.
         *
         * <p>🔴 <b>The id string is FROZEN and must never be changed</b> (ruling A-9). The
         * constant was called {@code AGILITY_FLEET_FOOTED_LAND} until 2026-08-17, and renaming the
         * CONSTANT is free — but this <em>string</em> is written into every player's entity NBT as
         * the modifier's identifier. {@link #clearAll(LivingEntity)} removes only the ids it still
         * knows, so a renamed id strands the old modifier permanently: an unremovable speed buff on
         * everyone who has already played. Rename the constant, never the literal.
         */
        MOVEMENT_FLEET_FOOTED_LAND(EntityAttributes.GENERIC_MOVEMENT_SPEED, "agility_fleet_footed",
                Operation.ADD_MULTIPLIED_TOTAL),

        /**
         * Swimming → Fleet Footed, water body. 🔴 Its id string is FROZEN too — see
         * {@link #MOVEMENT_FLEET_FOOTED_LAND}. Targets {@code WATER_MOVEMENT_EFFICIENCY} rather than
         * {@code MOVEMENT_SPEED} because — bytecode-verified in
         * {@code LivingEntity#travelInWater} — swim speed is a flat {@code 0.02} that movement speed
         * only contributes to <em>in proportion to</em> this attribute:
         * {@code g += (getMovementSpeed() - g) * waterMovementEfficiency}. With no Depth Strider the
         * efficiency is 0 and a movement-speed buff moves a swimming player not at all. This is the
         * same attribute Depth Strider uses, so the two stack additively and the config cap is what
         * stops a max-Swimming Depth Strider III player from becoming silly.
         */
        MOVEMENT_FLEET_FOOTED_WATER(EntityAttributes.GENERIC_WATER_MOVEMENT_EFFICIENCY,
                "agility_fleet_footed_water", Operation.ADD_VALUE),

        /**
         * Stealth → Padfoot. Targets {@code SNEAKING_SPEED} rather than {@code MOVEMENT_SPEED}, and
         * that choice does three jobs at once — bytecode-verified from {@code EntityAttributes},
         * where it is a {@code ClampedEntityAttribute("sneaking_speed", 0.3, 0.0, 1.0)} consumed by
         * {@code ClientPlayerEntity} behind {@code shouldSlowDown() = isInSneakingPose() ||
         * isCrawling()}:
         * <ul>
         *   <li><b>It only applies while crouched or crawling, by construction.</b> No add/remove
         *       dance is needed to keep the buff off a walking player — vanilla simply stops reading
         *       the attribute. (It also speeds up crawling through a 1-block gap. Intended.)</li>
         *   <li><b>Vanilla's own maximum of 1.0 is the ceiling</b>, which is full walking speed, so
         *       no configuration of {@code MaxSneakSpeedBonus} can make sneaking outrun walking.
         *       Same free-ceiling property {@link #MOVEMENT_FLEET_FOOTED_WATER} gets from
         *       {@code WATER_MOVEMENT_EFFICIENCY}.</li>
         *   <li><b>It shares no attribute with Fleet Footed</b>, so D-AG5's "two skills fighting over
         *       one attribute" concern is structurally impossible here rather than carefully
         *       avoided.</li>
         * </ul>
         * Additive because the vanilla default (0.3) is the thing being raised toward 1.0; a
         * multiplicative operation would make the same config number mean different speeds as vanilla
         * retunes its default.
         */
        STEALTH_PADFOOT(EntityAttributes.PLAYER_SNEAKING_SPEED, "stealth_padfoot", Operation.ADD_VALUE),

        /**
         * Unarmored → Iron Skin. The innate "skin" armour, live only while all four armour slots are
         * empty.
         *
         * <p>{@code ARMOR} is the whole mechanic rather than a proxy for it — bytecode-verified,
         * {@code LivingEntity#getArmor()} is literally {@code floor(getAttributeValue(ARMOR))} and
         * {@code applyArmorToDamage} feeds that straight into {@code DamageUtil.getDamageLeft}. So
         * the skin mitigates by exactly the same arithmetic a real armour set does, with no parallel
         * reduction path to keep in step.
         *
         * <p>Three consequences worth stating, all of them free:
         * <ul>
         *   <li><b>It shows on the vanilla armour HUD.</b> The attribute is registered
         *       {@code setTracked(true)}, so its value is synced to the client and the chestplate
         *       icons fill in as the tiers unlock. That answers the plan's open cosmetic question —
         *       no custom display is needed, and the player gets the feedback for nothing.</li>
         *   <li><b>Vanilla's own clamp is the ceiling.</b> {@code ARMOR} is a
         *       {@code ClampedEntityAttribute("armor", 0.0, 0.0, 30.0)}, so no configuration of the
         *       tier table can push a player past what the game already permits. Same free-ceiling
         *       property {@link #MOVEMENT_FLEET_FOOTED_WATER} and {@link #STEALTH_PADFOOT} get.</li>
         *   <li><b>Damage that ignores armour still ignores this.</b> {@code applyArmorToDamage}
         *       skips the whole computation for {@code BYPASSES_ARMOR} sources, so the void, starve
         *       and magic damage are as lethal to a maxed skin as to a diamond set.</li>
         * </ul>
         *
         * <p>Additive, and it has to be: a player's base {@code ARMOR} is {@code 0} and worn pieces
         * contribute their own modifiers, so a multiplicative operation on an unarmored player would
         * scale zero and the entire sub-skill would be an inert no-op that still applied cleanly.
         *
         * <p>No {@code ARMOR_TOUGHNESS} companion, deliberately (ruled). Toughness is what blunts
         * <em>large</em> hits, so withholding it is precisely what keeps real armour worth wearing:
         * diamond skin (20/0) still takes noticeably more from a heavy blow than a diamond set
         * (20/8), which also keeps its enchantments.
         */
        UNARMORED_IRON_SKIN(EntityAttributes.GENERIC_ARMOR, "unarmored_iron_skin", Operation.ADD_VALUE),

        /**
         * Taming → the pet combat mode's engage range. The <b>only managed buff that is not applied
         * to a player</b>, which is why this service is typed on {@link LivingEntity} rather than
         * {@code ServerPlayerEntity}.
         *
         * <p>It exists because "my pets ignore what I shoot" was never about the weapon. A wolf's
         * base {@code FOLLOW_RANGE} is {@code 16.0} — {@code WolfEntity.createWolfAttributes()} sets
         * only movement speed, max health and attack damage, so the follow range is inherited from
         * {@code MobEntity.createMobAttributes()} (bytecode-verified). {@code MeleeAttackGoal#canStart}
         * asks {@code navigation.findPathTo(target, 0)}, and that number sizes <em>both</em> the
         * pathfinder's search limit and the {@code ChunkCache} it is handed, so past 16 blocks the
         * path comes back null and the goal never starts. The wolf stands next to you holding a
         * target it will never walk to. A melee kill happens at ~3 blocks; a bow kill at 20–40.
         *
         * <p>Additive, because the base value is what is being raised and a multiplicative operation
         * would make the same config number mean different distances if Mojang retunes the default.
         *
         * <p>⚠️ <b>{@code EntityNavigation#setMaxFollowRange} is the cleaner lever and is deliberately
         * NOT used.</b> It does not exist below 1.21.2 — {@code javap} on the 1.21 merged jar shows
         * only {@code rangeMultiplier}/{@code setRangeMultiplier} — so it is a <em>compile error</em>
         * on an older band rather than a check that quietly answers wrong. The attribute ports.
         * {@code setRangeMultiplier} is a trap for a different reason: it scales the search limit but
         * not the {@code ChunkCache} sizing, so the pathfinder would search past its own block data.
         *
         * <p>⚠️ Cost is superlinear — 16 → 32 takes the search box from ~32³ to ~48³, about 3.4× the
         * volume, per repath, per pet. Hence the config cap, and hence "only while engaged".
         */
        TAMING_PET_ENGAGE_RANGE(EntityAttributes.GENERIC_FOLLOW_RANGE, "taming_pet_engage",
                Operation.ADD_VALUE);

        private final RegistryEntry<EntityAttribute> attribute;
        private final Identifier id;
        private final Operation operation;

        Managed(RegistryEntry<EntityAttribute> attribute, String path, Operation operation) {
            this.attribute = attribute;
            this.id = Identifier.of(McMMOMod.MOD_ID, path);
            this.operation = operation;
        }

        public @NotNull Identifier id() {
            return id;
        }

        public @NotNull Operation operation() {
            return operation;
        }
    }

    /**
     * Bring a managed buff to {@code amount}, applying, updating or removing it as needed.
     *
     * <p>Idempotent by construction: an amount equal to what is already applied does nothing, a
     * different amount replaces the modifier in place, and an amount of zero removes it entirely
     * (rather than leaving a no-op modifier attached, which would be indistinguishable from a leak
     * when debugging). Callers therefore need no "was it on?" bookkeeping — they just state the
     * value the current tick's state implies, every tick, including {@code 0}.
     *
     * @param player the player to buff
     * @param buff   which managed modifier to set
     * @param amount the modifier value in the units of {@link Managed#operation()}; {@code 0}
     *               removes it
     */
    public static void set(@NotNull LivingEntity entity, @NotNull Managed buff, double amount) {
        final EntityAttributeInstance instance = entity.getAttributeInstance(buff.attribute);
        if (instance == null) {
            // A player always has these attributes; a null here means the attribute was not
            // registered for this entity type, which is a wiring bug rather than a game state.
            McMMOMod.LOGGER.warn("{} has no {} attribute instance; skipping mcMMO buff {}.",
                    entity.getName().getString(), buff.id(), buff.name());
            return;
        }

        final EntityAttributeModifier existing = instance.getModifier(buff.id());
        if (amount == 0.0) {
            if (existing != null) {
                instance.removeModifier(buff.id());
            }
            return;
        }
        if (existing != null) {
            if (existing.value() == amount && existing.operation() == buff.operation()) {
                return; // Already exactly right — the common case on a per-tick caller.
            }
            instance.removeModifier(buff.id());
        }
        instance.addTemporaryModifier(
                new EntityAttributeModifier(buff.id(), amount, buff.operation()));
    }

    /** Whether this managed buff is currently applied to the player. Test/diagnostic seam. */
    public static boolean isApplied(@NotNull LivingEntity entity, @NotNull Managed buff) {
        final EntityAttributeInstance instance = entity.getAttributeInstance(buff.attribute);
        return instance != null && instance.getModifier(buff.id()) != null;
    }

    /**
     * The value of this managed buff on the player, or {@code 0} when it is not applied. Test seam —
     * lets a test distinguish "removed" from "applied at zero" without reaching into vanilla.
     */
    public static double appliedValue(@NotNull LivingEntity entity, @NotNull Managed buff) {
        final EntityAttributeInstance instance = entity.getAttributeInstance(buff.attribute);
        if (instance == null) {
            return 0.0;
        }
        final EntityAttributeModifier modifier = instance.getModifier(buff.id());
        return modifier == null ? 0.0 : modifier.value();
    }

    /**
     * Strip every mcMMO-managed modifier from this player.
     *
     * <p>Called on disconnect so a buff can never be observed by whatever the player becomes next.
     * It is belt-and-braces rather than the primary defence — the modifiers are temporary and the
     * per-tick callers re-derive from live state — but it is cheap and it makes the invariant
     * "mcMMO owns nothing on a player who is not online" trivially true.
     */
    public static void clearAll(@NotNull LivingEntity entity) {
        for (Managed buff : Managed.values()) {
            final EntityAttributeInstance instance = entity.getAttributeInstance(buff.attribute);
            if (instance != null) {
                instance.removeModifier(buff.id());
            }
        }
    }
}
