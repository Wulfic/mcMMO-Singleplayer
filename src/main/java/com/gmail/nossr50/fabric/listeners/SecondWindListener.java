package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.runnables.skills.AbilityDisableTask;
import com.gmail.nossr50.skills.movement.MovementManager;
import com.gmail.nossr50.skills.movement.Medium;
import com.gmail.nossr50.skills.movement.SecondWindResult;
import com.gmail.nossr50.util.Misc;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.sounds.SoundManager;
import com.gmail.nossr50.util.sounds.SoundType;
import java.util.List;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Agility's super ability, <b>Second Wind</b> — one cooldowned active with three bodies, dispatched
 * on how the player is moving when they fire it.
 *
 * <table>
 *   <caption>Bodies</caption>
 *   <tr><th>State</th><th>Body</th><th>Effect</th></tr>
 *   <tr><td>sprinting on land</td><td>Dart</td>
 *       <td>forward lunge; entities in the path take damage and heavy knockback</td></tr>
 *   <tr><td>in water</td><td>Aquaman</td>
 *       <td>Strength + Regeneration + Night Vision for the ability's duration</td></tr>
 *   <tr><td>gliding</td><td>Limitless</td><td>upward and forward burst</td></tr>
 *   <tr><td>anything else</td><td>—</td>
 *       <td>refused, <b>without consuming the cooldown</b></td></tr>
 * </table>
 *
 * <p><b>Why one ability instead of three.</b> Three actives would be three enum constants, three
 * cooldown slots, three config blocks, three locale blocks and three {@code /mcability} lines, for
 * what is — from the player's seat — one button. Sharing one {@link SuperAbilityType} also means the
 * water and air bodies could be added later with no enum, config or locale churn at all.
 *
 * <p><b>Not routed through {@code McMMOPlayer#checkAbilityActivation}.</b> That method resolves the
 * skill's {@code ToolType} and dereferences it; Agility is the mod's first super ability with no
 * tool behind it, so its entry in the tool map is {@code null} and that path would fail. The
 * activation sequence is reproduced here instead — rank gate, cooldown check, notify, stamp the
 * deactivation timestamp, schedule {@link AbilityDisableTask} — minus the tool-preparation half,
 * which has no meaning for an item-triggered ability.
 *
 * <p><b>Refusing must not burn the cooldown.</b> A player who fires this standing still has to be
 * able to try again immediately; charging them a full cooldown for a mistimed press would make the
 * ability feel broken. Hence every gate below returns before the timestamp is stamped.
 */
public final class SecondWindListener {

    private SecondWindListener() {
    }

    /** Vertical component of the Limitless burst, as a fraction of its forward magnitude. */
    private static final double LIMITLESS_LIFT = 0.5;

    /** How far either side of the lunge path an entity can be and still be caught by Dart. */
    private static final double DART_HIT_RADIUS = 1.5;

    public static void register() {
        UseItemCallback.EVENT.register(SecondWindListener::onUseItem);
    }

    private static InteractionResult onUseItem(Player player, Level world, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || world.isClientSide()
                || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(player.getUUID());
        if (mmoPlayer == null) {
            return InteractionResult.PASS;
        }
        if (!mmoPlayer.getPlayer().isHoldingItem(triggerItem())) {
            return InteractionResult.PASS;
        }
        tryActivate(mmoPlayer, serverPlayer);
        // Always PASS: the trigger item is never consumed and mcMMO is observing the click, not
        // replacing it. A feather has no vanilla use action to suppress anyway.
        return InteractionResult.PASS;
    }

    private static String triggerItem() {
        return McMMOMod.getGeneralConfig() == null
                ? "FEATHER"
                : McMMOMod.getGeneralConfig().getSecondWindItem();
    }

    /**
     * How long a Second Wind fired in {@code medium} runs for, in seconds — scaled on the skill the
     * player earns by travelling through <em>that</em> medium.
     *
     * <p>⚠️ <b>This used to read {@code AGILITY}, the mean of Parkour, Swimming and Flying</b>, and
     * moving it is a behaviour change rather than a rename. A swimmer with Swimming 900 and nothing
     * in the other two had an Agility of 300 and was scaled on that; they are now scaled on 900. That
     * is the same correction the 2026-08-17 unlock flattening made to what <em>gates</em> each body —
     * the duration was simply the half no gate test reads, so it survived the first pass.
     *
     * <p>Package-visible and pure over {@code (McMMOPlayer, Medium)} so the mapping from medium to
     * skill is testable without a world: mock a different length per skill and the answer names which
     * one was asked.
     */
    static int durationTicks(@NotNull McMMOPlayer mmoPlayer, @NotNull Medium medium) {
        return mmoPlayer.calculateAbilityActivationTicks(medium.primarySkill(),
                SuperAbilityType.SECOND_WIND);
    }

    private static void tryActivate(@NotNull McMMOPlayer mmoPlayer,
            @NotNull ServerPlayer player) {
        if (mmoPlayer.getAbilityMode(SuperAbilityType.SECOND_WIND)) {
            return; // Already running.
        }
        if (!mmoPlayer.getAbilityUse()) {
            return; // Player toggled abilities off with /mcability.
        }

        final Medium medium = PlayerMovementTracker.classifyMedium(player);
        if (medium == null) {
            // Standing still, walking, or riding something. Tell them why and leave the cooldown
            // untouched so the next press — while actually moving — works.
            NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_MESSAGE,
                    "Movement.Skills.SecondWind.NotMoving");
            return;
        }

        final MovementManager agility = mmoPlayer.getMovementManager();
        if (agility == null) {
            return;
        }

        final int timeRemaining = mmoPlayer.calculateTimeRemaining(SuperAbilityType.SECOND_WIND);
        if (timeRemaining > 0) {
            NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.ABILITY_COOLDOWN,
                    "Skills.TooTired", String.valueOf(timeRemaining));
            return;
        }

        final int ticks = durationTicks(mmoPlayer, medium);
        final SecondWindResult result = agility.computeSecondWind(medium, ticks);
        if (result == null) {
            // This medium's rank is not unlocked — the player has Second Wind but not, say, its
            // water body yet. Also a free refusal.
            NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_MESSAGE,
                    "Movement.Skills.SecondWind.Locked");
            return;
        }

        activate(mmoPlayer, player, result, ticks);
    }

    private static void activate(@NotNull McMMOPlayer mmoPlayer, @NotNull ServerPlayer player,
            @NotNull SecondWindResult result, int ticks) {
        if (mmoPlayer.useChatNotifications()) {
            NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUPER_ABILITY,
                    SuperAbilityType.SECOND_WIND.getAbilityOn());
        }
        SoundManager.worldSendSound(mmoPlayer.getPlayer(), SoundType.ABILITY_ACTIVATED_GENERIC);

        mmoPlayer.setAbilityDATS(SuperAbilityType.SECOND_WIND,
                System.currentTimeMillis() + ((long) ticks * Misc.TIME_CONVERSION_FACTOR));
        mmoPlayer.setAbilityMode(SuperAbilityType.SECOND_WIND, true);
        McMMOMod.getScheduler().runLater(
                new AbilityDisableTask(mmoPlayer, SuperAbilityType.SECOND_WIND),
                (long) ticks * Misc.TICK_CONVERSION_FACTOR);

        switch (result.medium()) {
            case LAND -> dart(player, result);
            case WATER -> aquaman(player, result);
            case AIR -> limitless(player, result);
        }
    }

    /**
     * Dart: launch the player along their look vector and hit whatever is in the way.
     *
     * <p>The lunge is applied first so the sweep and the movement describe the same path; entities
     * are collected from a box around that path rather than by a raycast, so a lunge past someone's
     * shoulder still connects instead of requiring pixel-accurate aim.
     */
    private static void dart(@NotNull ServerPlayer player, @NotNull SecondWindResult result) {
        final Vec3 look = player.getLookAngle().normalize();
        final Vec3 lunge = look.scale(result.magnitude());
        setVelocity(player, player.getDeltaMovement().add(lunge.x, Math.max(0.1, lunge.y * 0.5), lunge.z));

        final ServerLevel world = (ServerLevel) player.level();
        final Vec3 from = player.position();
        final Vec3 to = from.add(look.scale(result.dartRange()));
        final AABB path = new AABB(from, to).inflate(DART_HIT_RADIUS);

        final List<Entity> hits = world.getEntities(player, path,
                entity -> entity instanceof LivingEntity && entity.isAlive());
        for (Entity entity : hits) {
            final LivingEntity target = (LivingEntity) entity;
            target.hurtServer(world, world.damageSources().playerAttack(player),
                    (float) result.dartDamage());
            // Knock the target away from the player, along the horizontal lunge direction.
            target.knockback(result.dartKnockback(), -look.x, -look.z);
        }
    }

    /** Aquaman: a timed underwater buff. Duration is the ability's own length. */
    private static void aquaman(@NotNull ServerPlayer player,
            @NotNull SecondWindResult result) {
        final int duration = result.durationTicks();
        final int amplifier = (int) result.magnitude();
        player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, duration, amplifier));
        player.addEffect(
                new MobEffectInstance(MobEffects.REGENERATION, duration, amplifier));
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, duration, 0));
        // Dolphin's Grace is the swim-speed half of the fantasy and is a status effect rather than an
        // attribute, so it composes with Fleet Footed's water modifier instead of fighting it.
        player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, duration, 0));
    }

    /** Limitless: a forward-and-up burst for a gliding player. */
    private static void limitless(@NotNull ServerPlayer player,
            @NotNull SecondWindResult result) {
        final Vec3 look = player.getLookAngle().normalize();
        final double boost = result.magnitude();
        setVelocity(player, player.getDeltaMovement()
                .add(look.x * boost, look.y * boost + LIMITLESS_LIFT, look.z * boost));
    }

    /**
     * Set a player's velocity <em>and make their own client honour it</em>.
     *
     * <p>Setting velocity server-side is not enough on its own. {@code EntityTrackerEntry} publishes
     * velocity through {@code sendToListeners}, and for a player entity the listeners are the
     * <em>other</em> nearby players — the moving player never receives their own velocity update
     * (bytecode-verified). Their client would keep simulating the old velocity and the server would
     * accept its position, so the impulse would silently do nothing. Sending the packet explicitly is
     * what Bukkit's {@code Player#setVelocity} does, and it is correct here because these are
     * one-shot impulses; the same approach per-tick would fight the client's own prediction, which is
     * why the continuous air bonuses go through a shared-movement mixin instead.
     */
    private static void setVelocity(@NotNull ServerPlayer player, @NotNull Vec3 velocity) {
        player.setDeltaMovement(velocity);
        player.needsSync = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }
}
