package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.runnables.skills.AbilityDisableTask;
import com.gmail.nossr50.skills.husbandry.HusbandryManager;
import com.gmail.nossr50.util.Misc;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.sounds.SoundManager;
import com.gmail.nossr50.util.sounds.SoundType;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

/**
 * Husbandry's super ability, <b>Herdsman's Call</b> — sound the horn and the herd answers.
 *
 * <p>Structurally the third sibling of {@link SecondWindListener} and {@link SmokeBombListener}: the
 * same {@code UseItemCallback} seam, the same hand-rolled activation sequence, and the same rule that a
 * refusal must never burn the cooldown. A separate class rather than another body on either of those,
 * because the three belong to different skills and share no state.
 *
 * <p><b>⚠️ The trigger item must differ from the other two actives'.</b> All three listen on this one
 * event, so a shared item activates one and prints another's refusal — a bug that reads as a broken
 * ability rather than as a config collision. Shipped as {@code GOAT_HORN} against Second Wind's
 * {@code FEATHER} and Smoke Bomb's {@code GUNPOWDER}, and a test pins the three apart.
 *
 * <h2>What the ability actually does, and where each part lives</h2>
 * Three effects, and only one of them is here:
 * <ul>
 *   <li><b>The herd follows you</b> without you holding food — {@code PlayerMovementTracker}, because
 *       that is the mod's only per-tick per-player sweep and this needs to re-path every tick.</li>
 *   <li><b>Every harvest verb ignores its cooldown</b> — one check inside
 *       {@code HusbandryListener}'s cooldown gate, so it cannot be wired into milking and forgotten
 *       for brushing.</li>
 *   <li><b>Every harvest double-yields</b> — one check inside
 *       {@link HusbandryManager#rollBonusHarvestDrop()}, which all four verbs already route their
 *       bonus through, so the super reaches all four for free.</li>
 * </ul>
 * Nothing about the ability lives at a harvest call site, which is the point: the super multiplies four
 * verbs, and four copies of "if the super is active" is four chances to miss one.
 *
 * <p><b>No {@code checkAbilityActivation}.</b> That path dereferences the skill's {@code ToolType},
 * and Husbandry has none — four of its six verbs use a different tool and two use none at all. Same
 * reason Second Wind and Smoke Bomb hand-roll their activation.
 */
public final class HerdsmansCallListener {

    private HerdsmansCallListener() {
    }

    public static void register() {
        UseItemCallback.EVENT.register(HerdsmansCallListener::onUseItem);
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
        // Always PASS: the horn is never consumed and mcMMO is observing the click rather than
        // replacing it, so a goat horn still sounds exactly as vanilla intends. That is a large part of
        // why the horn was chosen — the ability comes with its own noise for free.
        return TypedActionResult.pass(player.getStackInHand(hand));
    }

    private static String triggerItem() {
        return McMMOMod.getGeneralConfig() == null
                ? "GOAT_HORN"
                : McMMOMod.getGeneralConfig().getHerdsmansCallItem();
    }

    private static void tryActivate(@NotNull McMMOPlayer mmoPlayer,
            @NotNull ServerPlayerEntity player) {
        if (mmoPlayer.getAbilityMode(SuperAbilityType.HERDSMANS_CALL)) {
            return; // Already sounding.
        }
        if (!mmoPlayer.getAbilityUse()) {
            return; // Player toggled abilities off with /mcability.
        }

        final HusbandryManager husbandry = mmoPlayer.getHusbandryManager();
        if (husbandry == null || !husbandry.canHerdsmansCall()) {
            return; // Rank not reached, or the sub-skill is disabled. Silent, like every rank gate.
        }

        final int timeRemaining = mmoPlayer.calculateTimeRemaining(SuperAbilityType.HERDSMANS_CALL);
        if (timeRemaining > 0) {
            NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.ABILITY_COOLDOWN,
                    "Skills.TooTired", String.valueOf(timeRemaining));
            return;
        }

        activate(mmoPlayer, durationTicks(mmoPlayer, husbandry));
    }

    /**
     * How long the call lasts, in ticks.
     *
     * <p>Two knobs feed this and both are live, which is deliberate — a shipped setting that turns out
     * to change nothing is a recurring defect in this port rather than a harmless leftover. The
     * standard super-ability length machinery scales the ability with skill level and honours
     * {@code Ability_Length}, {@code Ability_Length_Cap} and {@code Max_Seconds.Herdsmans_Call};
     * {@code advanced.yml}'s {@code HerdsmansCall.DurationTicks} is a <b>floor</b>, so the ability is
     * worth pressing the moment its rank unlocks instead of lasting two seconds.
     */
    static int durationTicks(@NotNull McMMOPlayer mmoPlayer, @NotNull HusbandryManager husbandry) {
        final int seconds = mmoPlayer.calculateAbilityActivationTicks(PrimarySkillType.HUSBANDRY,
                SuperAbilityType.HERDSMANS_CALL);
        return Math.max(seconds * Misc.TICK_CONVERSION_FACTOR,
                husbandry.getHerdsmansCallDurationTicks());
    }

    private static void activate(@NotNull McMMOPlayer mmoPlayer, int durationTicks) {
        if (mmoPlayer.useChatNotifications()) {
            NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUPER_ABILITY,
                    SuperAbilityType.HERDSMANS_CALL.getAbilityOn());
        }
        SoundManager.worldSendSound(mmoPlayer.getPlayer(), SoundType.ABILITY_ACTIVATED_GENERIC);

        final long millis = (long) durationTicks * Misc.TIME_CONVERSION_FACTOR
                / Misc.TICK_CONVERSION_FACTOR;
        mmoPlayer.setAbilityDATS(SuperAbilityType.HERDSMANS_CALL, System.currentTimeMillis() + millis);
        mmoPlayer.setAbilityMode(SuperAbilityType.HERDSMANS_CALL, true);
        McMMOMod.getScheduler().runLater(
                new AbilityDisableTask(mmoPlayer, SuperAbilityType.HERDSMANS_CALL), durationTicks);
        // No effect is applied to the player: all three of this ability's effects are read from
        // getAbilityMode() by the code that needs them, so there is no state to unwind when it ends.
        // AbilityDisableTask clearing the mode IS the whole teardown.
    }
}
