package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.subskills.acrobatics.RollResult;
import com.gmail.nossr50.skills.acrobatics.AcrobaticsManager;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.sounds.SoundManager;
import com.gmail.nossr50.util.sounds.SoundType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;

/**
 * The K1/K2 damage hook: mcMMO's window into the vanilla damage pipeline. Driven by a mixin on
 * {@link LivingEntity#modifyAppliedDamage(DamageSource, float)} (see
 * {@code fabric.mixin.LivingEntityDamageMixin}) rather than a Fabric event, because mcMMO needs to
 * <em>modify</em> the applied damage (Acrobatics Roll reduces fall damage) and Fabric's
 * {@code ServerLivingEntityEvents.ALLOW_DAMAGE} is a cancel-only veto.
 *
 * <p>Currently wired: <b>K2 — fall damage → Acrobatics Roll</b>. The combat on-hit sub-skills (K1:
 * Counter Attack, Armor Impact, Rupture, Taming damage modifiers, …) attach to this same entry point
 * as they are ported, branching on the {@link DamageSource} type / attacker.
 */
public final class EntityDamageListener {

    private EntityDamageListener() {
    }

    /**
     * Invoked from the {@code modifyAppliedDamage} mixin for every living-entity hit. Returns the
     * (possibly reduced) damage to apply. Only server players landing fall damage are affected today;
     * everything else passes through untouched.
     *
     * @param entity the entity taking damage
     * @param source the damage source
     * @param amount the vanilla post-armor/enchantment damage that would be applied
     * @return the damage mcMMO wants applied instead (equal to {@code amount} when it does not act)
     */
    public static float onModifyAppliedDamage(LivingEntity entity, DamageSource source, float amount) {
        if (amount <= 0 || !(entity instanceof ServerPlayerEntity serverPlayer)) {
            return amount;
        }
        if (source.isIn(DamageTypeTags.IS_FALL)) {
            return handleFallDamage(serverPlayer, amount);
        }
        return amount;
    }

    private static float handleFallDamage(ServerPlayerEntity serverPlayer, float amount) {
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(serverPlayer.getUuid());
        if (mmoPlayer == null) {
            return amount; // data not loaded (e.g. mid-join).
        }
        final AcrobaticsManager acrobatics = mmoPlayer.getAcrobaticsManager();
        if (acrobatics == null) {
            return amount;
        }

        // The manager awards XP + tracks the landing block internally; it hands back the outcome so we
        // can apply the damage reduction + feedback (the MC-typed half) here.
        final RollResult result = acrobatics.processFallDamage(amount);
        if (result == null || !result.isRollSuccess()) {
            return amount;
        }

        NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_MESSAGE,
                result.isGraceful() ? "Acrobatics.Ability.Proc" : "Acrobatics.Roll.Text");
        SoundManager.sendCategorizedSound(mmoPlayer.getPlayer(), SoundType.ROLL_ACTIVATED,
                SoundCategory.PLAYERS, 0.5F);
        return (float) result.getModifiedDamage();
    }
}
