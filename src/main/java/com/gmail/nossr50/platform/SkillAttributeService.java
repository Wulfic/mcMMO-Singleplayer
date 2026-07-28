package com.gmail.nossr50.platform;

import com.gmail.nossr50.fabric.McMMOMod;
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
 * The single owner of every {@link EntityAttributeModifier} mcMMO applies to a player (F2).
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
 * <p>Not every buff belongs here: Agility's air (elytra) body writes velocity directly, because
 * gliding is velocity-driven with no attribute behind it ({@code LivingEntity#travelGliding},
 * bytecode-verified). Attributes only.
 */
public final class SkillAttributeService {

    private SkillAttributeService() {
    }

    /**
     * Every attribute modifier mcMMO can apply, as (attribute, id) pairs.
     *
     * <p>Enumerating them is what makes {@link #clearAll(ServerPlayerEntity)} possible: teardown has
     * to be able to remove a buff without knowing which skill applied it or why. Adding a managed
     * buff means adding a constant here — if it is not in this enum, it is not cleaned up on logout.
     */
    public enum Managed {
        /**
         * Agility → Fleet Footed, land body. A percentage bonus on top of the vanilla sprint
         * multiplier, live only while sprinting.
         */
        AGILITY_FLEET_FOOTED_LAND(EntityAttributes.MOVEMENT_SPEED, "agility_fleet_footed",
                Operation.ADD_MULTIPLIED_TOTAL),

        /**
         * Agility → Fleet Footed, water body. Targets {@code WATER_MOVEMENT_EFFICIENCY} rather than
         * {@code MOVEMENT_SPEED} because — bytecode-verified in
         * {@code LivingEntity#travelInWater} — swim speed is a flat {@code 0.02} that movement speed
         * only contributes to <em>in proportion to</em> this attribute:
         * {@code g += (getMovementSpeed() - g) * waterMovementEfficiency}. With no Depth Strider the
         * efficiency is 0 and a movement-speed buff moves a swimming player not at all. This is the
         * same attribute Depth Strider uses, so the two stack additively and the config cap is what
         * stops a max-Agility Depth Strider III player from becoming silly.
         */
        AGILITY_FLEET_FOOTED_WATER(EntityAttributes.WATER_MOVEMENT_EFFICIENCY,
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
         *       Same free-ceiling property {@link #AGILITY_FLEET_FOOTED_WATER} gets from
         *       {@code WATER_MOVEMENT_EFFICIENCY}.</li>
         *   <li><b>It shares no attribute with Fleet Footed</b>, so D-AG5's "two skills fighting over
         *       one attribute" concern is structurally impossible here rather than carefully
         *       avoided.</li>
         * </ul>
         * Additive because the vanilla default (0.3) is the thing being raised toward 1.0; a
         * multiplicative operation would make the same config number mean different speeds as vanilla
         * retunes its default.
         */
        STEALTH_PADFOOT(EntityAttributes.SNEAKING_SPEED, "stealth_padfoot", Operation.ADD_VALUE);

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
    public static void set(@NotNull ServerPlayerEntity player, @NotNull Managed buff, double amount) {
        final EntityAttributeInstance instance = player.getAttributeInstance(buff.attribute);
        if (instance == null) {
            // A player always has these attributes; a null here means the attribute was not
            // registered for this entity type, which is a wiring bug rather than a game state.
            McMMOMod.LOGGER.warn("Player {} has no {} attribute instance; skipping mcMMO buff {}.",
                    player.getName().getString(), buff.id(), buff.name());
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
    public static boolean isApplied(@NotNull ServerPlayerEntity player, @NotNull Managed buff) {
        final EntityAttributeInstance instance = player.getAttributeInstance(buff.attribute);
        return instance != null && instance.getModifier(buff.id()) != null;
    }

    /**
     * The value of this managed buff on the player, or {@code 0} when it is not applied. Test seam —
     * lets a test distinguish "removed" from "applied at zero" without reaching into vanilla.
     */
    public static double appliedValue(@NotNull ServerPlayerEntity player, @NotNull Managed buff) {
        final EntityAttributeInstance instance = player.getAttributeInstance(buff.attribute);
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
    public static void clearAll(@NotNull ServerPlayerEntity player) {
        for (Managed buff : Managed.values()) {
            final EntityAttributeInstance instance = player.getAttributeInstance(buff.attribute);
            if (instance != null) {
                instance.removeModifier(buff.id());
            }
        }
    }
}
