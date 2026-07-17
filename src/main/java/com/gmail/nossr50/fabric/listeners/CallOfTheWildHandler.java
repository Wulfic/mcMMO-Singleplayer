package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.subskills.taming.CallOfTheWildType;
import com.gmail.nossr50.datatypes.skills.subskills.taming.TamingSummon;
import com.gmail.nossr50.fabric.CotwSummon;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.skills.taming.TamingManager;
import com.gmail.nossr50.util.TransientEntityTracker;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.sounds.SoundManager;
import com.gmail.nossr50.util.sounds.SoundType;
import com.gmail.nossr50.util.text.TextUtils;
import java.util.List;
import java.util.Optional;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * The MC-typed Call-of-the-Wild glue: spawning the summoned animals and the {@code attackTarget}
 * ("sic your wolves") sweep. Ports the entity-touching half of legacy {@code TamingManager}
 * ({@code processCallOfTheWild} / {@code spawnCOTWEntity} / {@code spawnWolf}/{@code spawnCat}/
 * {@code spawnHorse} / {@code attackTarget}); the server-free decisions live on
 * {@link TamingManager} (the rank/permission gate + the anti-autofire debounce) and the
 * {@link com.gmail.nossr50.skills.taming.CallOfTheWild} lookup tables, so this class owns only the
 * inventory read, the entity construction and the tracker interaction.
 *
 * <p>Wolves and cats are {@link TameableEntity} ({@code setTamedBy}); horses are
 * {@link AbstractHorseEntity} ({@code setTame} + {@code setOwner}) — legacy's single Bukkit
 * {@code Tameable#setOwner} splits across the two class hierarchies here. Every summon is spawned via
 * {@link MobEntity#initialize} first (the only public way to randomise a horse's colour/markings, whose
 * setters are private) and then made adult, tamed, persistent, named and health-set, matching legacy's
 * per-type spawn methods. Cats are <em>not</em> variant-randomised beyond {@code initialize}: legacy
 * only randomised the deprecated {@code Ocelot} type, never the modern cat.
 */
public final class CallOfTheWildHandler {

    private CallOfTheWildHandler() {
    }

    /**
     * Legacy {@code TamingManager#processCallOfTheWild}: if the player is holding enough of a COTW item,
     * summon the animals (up to the per-type cap), pay the item cost, and notify. The trigger
     * ({@code SuperAbilityListener}) has already confirmed a sneaking left-click; the gate + debounce
     * are checked here so every entry path shares them.
     */
    public static void processCallOfTheWild(McMMOPlayer mmoPlayer, ServerPlayerEntity player) {
        final TamingManager taming = mmoPlayer.getTamingManager();
        if (!taming.canCallOfTheWild() || !taming.summonDebounceElapsed()) {
            return;
        }

        final ItemStack inHand = player.getMainHandStack();
        final String itemId = Registries.ITEM.getId(inHand.getItem()).getPath();
        final Optional<TamingSummon> maybeSummon = McMMOMod.getCallOfTheWild().summonForItem(itemId);
        if (maybeSummon.isEmpty()) {
            return; // not a summoning item.
        }

        final TamingSummon summon = maybeSummon.get();
        final CallOfTheWildType type = summon.getCallOfTheWildType();

        if (inHand.getCount() < summon.getItemAmountRequired()) {
            final int difference = summon.getItemAmountRequired() - inHand.getCount();
            NotificationManager.sendPlayerInformationChatOnly(mmoPlayer,
                    "Taming.Summon.COTW.NeedMoreItems", String.valueOf(difference),
                    inHand.getName().getString());
            return;
        }

        final TransientEntityTracker tracker = McMMOMod.getTransientEntityTracker();
        Vec3d spawnPos = player.getEntityPos().add(1.0, 0.0, 1.0);
        int amountSummoned = 0;

        for (int i = 0; i < summon.getEntitiesSummoned(); i++) {
            if (tracker.countActiveOfType(player.getUuid(), type) >= summon.getSummonCap()) {
                NotificationManager.sendPlayerInformationChatOnly(mmoPlayer,
                        "Taming.Summon.COTW.Limit", String.valueOf(summon.getSummonCap()),
                        type.getDisplayName());
                break;
            }

            spawnPos = spawnPos.add(1.0, 0.0, 0.0);
            spawnSummon(player, type, summon, spawnPos);

            if (summon.getSummonLifespan() > 0) {
                NotificationManager.sendPlayerInformationChatOnly(mmoPlayer,
                        "Taming.Summon.COTW.Success.WithLifespan", type.getDisplayName(),
                        String.valueOf(summon.getSummonLifespan()));
            } else {
                NotificationManager.sendPlayerInformationChatOnly(mmoPlayer,
                        "Taming.Summon.COTW.Success.WithoutLifespan", type.getDisplayName());
            }
            SoundManager.sendSound(mmoPlayer.getPlayer(), SoundType.ABILITY_ACTIVATED_GENERIC);
            amountSummoned++;
        }

        // Legacy pays the item cost once, only if at least one animal was actually summoned.
        if (amountSummoned >= 1) {
            inHand.decrement(summon.getItemAmountRequired());
        }
    }

    private static void spawnSummon(ServerPlayerEntity player, CallOfTheWildType type,
            TamingSummon summon, Vec3d pos) {
        final ServerWorld world = (ServerWorld) player.getEntityWorld();
        final MobEntity entity = createEntity(type, world);

        entity.refreshPositionAndAngles(pos.getX(), pos.getY(), pos.getZ(), player.getYaw(), 0.0F);
        // Natural randomisation (variant/markings/base attributes) — the only public path to vary a
        // horse's look, its setters being private. Must precede our own stat overrides below.
        entity.initialize(world, world.getLocalDifficulty(entity.getBlockPos()), SpawnReason.EVENT,
                null);
        entity.setBaby(false); // legacy setAdult()
        applyOwnership(entity, player);
        applyTypeStats(entity, type);
        entity.setPersistent(); // legacy setRemoveWhenFarAway(false)
        entity.setCustomName(TextUtils.toText(LocaleLoader.getString("Taming.Summon.Name.Format",
                player.getName().getString(), type.getDisplayName())));

        world.spawnEntity(entity);

        final CotwSummon tracked = new CotwSummon(entity, type, player.getUuid(),
                McMMOMod.getTransientEntityTracker(), summon.getSummonLifespan());
        McMMOMod.getTransientEntityTracker().addSummon(player.getUuid(), tracked);
    }

    private static MobEntity createEntity(CallOfTheWildType type, ServerWorld world) {
        return switch (type) {
            case WOLF -> new WolfEntity(EntityType.WOLF, world);
            case CAT -> new CatEntity(EntityType.CAT, world);
            case HORSE -> new HorseEntity(EntityType.HORSE, world);
        };
    }

    private static void applyOwnership(MobEntity entity, ServerPlayerEntity player) {
        if (entity instanceof TameableEntity tameable) {
            tameable.setTamedBy(player); // wolves + cats: sets tamed and owner together
        } else if (entity instanceof AbstractHorseEntity horse) {
            horse.setTame(true);
            horse.setOwner(player);
        }
    }

    private static void applyTypeStats(MobEntity entity, CallOfTheWildType type) {
        switch (type) {
            case WOLF -> setMaxHealth(entity, 20.0);
            case HORSE -> {
                // Legacy: random 15..30 HP, and a jump strength clamped into the config band.
                final double maxHealth = 15.0 + (entity.getRandom().nextDouble() * 15.0);
                setMaxHealth(entity, maxHealth);
                final double rolled = Math.min(entity.getRandom().nextDouble(),
                        entity.getRandom().nextDouble()) * 2.0;
                final double jump = Math.max(McMMOMod.getAdvancedConfig().getMinHorseJumpStrength(),
                        Math.min(rolled, McMMOMod.getAdvancedConfig().getMaxHorseJumpStrength()));
                final EntityAttributeInstance jumpAttr =
                        entity.getAttributeInstance(EntityAttributes.JUMP_STRENGTH);
                if (jumpAttr != null) {
                    jumpAttr.setBaseValue(jump);
                }
            }
            case CAT -> {
                // No stat override: legacy left the cat at its natural stats.
            }
        }
        entity.setHealth(entity.getMaxHealth());
    }

    private static void setMaxHealth(LivingEntity entity, double maxHealth) {
        final EntityAttributeInstance attr = entity.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(maxHealth);
        }
    }

    /**
     * Legacy {@code TamingManager#attackTarget}: on a combat hit, every nearby tamed, un-sitting wolf
     * the player owns re-targets the entity they just struck. The own-pet guard stops a player siccing
     * their pack on one of their own animals.
     */
    public static void attackTarget(ServerPlayerEntity player, LivingEntity target) {
        if (target instanceof TameableEntity tameable && tameable.isOwner(player)) {
            return;
        }

        final Box searchBox = player.getBoundingBox().expand(5.0);
        final List<WolfEntity> wolves = player.getEntityWorld()
                .getEntitiesByClass(WolfEntity.class, searchBox, wolf -> true);
        for (WolfEntity wolf : wolves) {
            if (wolf.isTamed() && wolf.isOwner(player) && !wolf.isSitting()) {
                wolf.setTarget(target);
            }
        }
    }
}
