package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.runnables.skills.AbilityDisableTask;
import com.gmail.nossr50.skills.stealth.StealthManager;
import com.gmail.nossr50.util.Misc;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.sounds.SoundManager;
import com.gmail.nossr50.util.sounds.SoundType;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

/**
 * Stealth's super ability, <b>Smoke Bomb</b> — right-click the trigger item and vanish.
 *
 * <p>Structurally a sibling of {@link SecondWindListener}: the same {@code UseItemCallback} seam, the
 * same hand-rolled activation sequence, and the same rule that a refusal must not burn the cooldown.
 * It is a separate class rather than another body on that one because the two abilities belong to
 * different skills and share no state; folding them together would mean one listener that has to
 * decide which skill a click was meant for.
 *
 * <p><b>⚠️ The trigger item must differ from Agility's {@code Second_Wind_Item}.</b> Both actives
 * listen on the same event, so a shared item would activate one of them and print the other's
 * refusal message — a bug that looks like a broken ability rather than a config collision. Shipped as
 * {@code GUNPOWDER} against Second Wind's {@code FEATHER}, and both are documented in
 * {@code config.yml}.
 *
 * <p><b>No firework, no particles.</b> The wiki version of this ability puffs smoke; vanilla
 * invisibility trails particles. Both are suppressed, and the particle half is not cosmetic: a
 * sparkling outline tells anyone looking exactly where the invisible player is, which defeats the
 * entire ability. The status icon is left on, since that is only visible to the player themselves.
 *
 * <p><b>Armour and held items stay visible</b> — that is vanilla invisibility's behaviour and it is
 * not worked around here. A player who wants to disappear properly takes their armour off first,
 * which is a real and interesting cost rather than a limitation to paper over.
 */
public final class SmokeBombListener {

    private SmokeBombListener() {
    }

    public static void register() {
        UseItemCallback.EVENT.register(SmokeBombListener::onUseItem);
    }

    private static TypedActionResult<ItemStack> onUseItem(PlayerEntity player, World world, Hand hand) {
        if (hand != Hand.MAIN_HAND || world.isClient()
                || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return TypedActionResult.pass(player.getStackInHand(hand));
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(player.getUuid());
        if (mmoPlayer == null) {
            return TypedActionResult.pass(player.getStackInHand(hand));
        }
        if (!mmoPlayer.getPlayer().isHoldingItem(triggerItem())) {
            return TypedActionResult.pass(player.getStackInHand(hand));
        }
        tryActivate(mmoPlayer, serverPlayer);
        // Always PASS: the trigger item is never consumed and mcMMO is observing the click, not
        // replacing it. Gunpowder has no vanilla use action to suppress anyway.
        return TypedActionResult.pass(player.getStackInHand(hand));
    }

    private static String triggerItem() {
        return McMMOMod.getGeneralConfig() == null
                ? "GUNPOWDER"
                : McMMOMod.getGeneralConfig().getSmokeBombItem();
    }

    private static void tryActivate(@NotNull McMMOPlayer mmoPlayer,
            @NotNull ServerPlayerEntity player) {
        if (mmoPlayer.getAbilityMode(SuperAbilityType.SMOKE_BOMB)) {
            return; // Already vanished.
        }
        if (!mmoPlayer.getAbilityUse()) {
            return; // Player toggled abilities off with /mcability.
        }

        final StealthManager stealth = mmoPlayer.getStealthManager();
        if (stealth == null || !stealth.canSmokeBomb()) {
            return; // Rank not reached, or the sub-skill is disabled. Silent, like every rank gate.
        }

        final int timeRemaining = mmoPlayer.calculateTimeRemaining(SuperAbilityType.SMOKE_BOMB);
        if (timeRemaining > 0) {
            NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.ABILITY_COOLDOWN,
                    "Skills.TooTired", String.valueOf(timeRemaining));
            return;
        }

        activate(mmoPlayer, player, durationTicks(mmoPlayer, stealth));
    }

    /**
     * How long the bomb lasts, in ticks.
     *
     * <p>Two knobs feed this and both are live, which is deliberate — a shipped setting that turns
     * out to change nothing is a recurring defect in this port, not a harmless leftover:
     * <ul>
     *   <li>the standard super-ability length machinery, so Smoke Bomb scales with skill level and
     *       honours {@code Ability_Length}, {@code Ability_Length_Cap} and
     *       {@code Max_Seconds.Smoke_Bomb} exactly like every other super ability;</li>
     *   <li>{@code advanced.yml}'s {@code SmokeBomb.DurationTicks} as a <b>floor</b>, so the ability
     *       is worth pressing the moment its rank unlocks rather than lasting a couple of seconds at
     *       the level you first earn it.</li>
     * </ul>
     *
     * <p>The same number drives the invisibility <em>and</em> the ability's own lifetime. Letting
     * those diverge would mean the player reads "Smoke Bomb has worn off" while still invisible, or
     * reappears while the ability claims to be running.
     */
    static int durationTicks(@NotNull McMMOPlayer mmoPlayer, @NotNull StealthManager stealth) {
        final int seconds = mmoPlayer.calculateAbilityActivationTicks(PrimarySkillType.STEALTH,
                SuperAbilityType.SMOKE_BOMB);
        return Math.max(seconds * Misc.TICK_CONVERSION_FACTOR, stealth.getSmokeBombDurationTicks());
    }

    private static void activate(@NotNull McMMOPlayer mmoPlayer, @NotNull ServerPlayerEntity player,
            int durationTicks) {
        if (mmoPlayer.useChatNotifications()) {
            NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUPER_ABILITY,
                    SuperAbilityType.SMOKE_BOMB.getAbilityOn());
        }
        SoundManager.worldSendSound(mmoPlayer.getPlayer(), SoundType.ABILITY_ACTIVATED_GENERIC);

        final long millis = (long) durationTicks * Misc.TIME_CONVERSION_FACTOR
                / Misc.TICK_CONVERSION_FACTOR;
        mmoPlayer.setAbilityDATS(SuperAbilityType.SMOKE_BOMB, System.currentTimeMillis() + millis);
        mmoPlayer.setAbilityMode(SuperAbilityType.SMOKE_BOMB, true);
        McMMOMod.getScheduler().runLater(
                new AbilityDisableTask(mmoPlayer, SuperAbilityType.SMOKE_BOMB), durationTicks);

        // (effect, duration, amplifier, ambient, showParticles, showIcon) — particles off so the
        // effect cannot betray the player it is hiding; icon on, since only they can see it.
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, durationTicks,
                0, false, false, true));
    }
}
