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
import com.gmail.nossr50.platform.PlatformLivingEntity;
import com.gmail.nossr50.platform.ParticleEffectUtils;
import com.gmail.nossr50.util.sounds.SoundManager;
import com.gmail.nossr50.util.sounds.SoundType;
import com.gmail.nossr50.platform.text.TextUtils;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
    public static void processCallOfTheWild(McMMOPlayer mmoPlayer, ServerPlayer player) {
        final TamingManager taming = mmoPlayer.getTamingManager();
        if (!taming.canCallOfTheWild() || !taming.summonDebounceElapsed()) {
            return;
        }

        final ItemStack inHand = player.getMainHandItem();
        final String itemId = BuiltInRegistries.ITEM.getId(inHand.getItem()).getPath();
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
                    inHand.getHoverName().getString());
            return;
        }

        final TransientEntityTracker tracker = McMMOMod.getTransientEntityTracker();
        Vec3 spawnPos = player.position().add(1.0, 0.0, 1.0);
        int amountSummoned = 0;

        for (int i = 0; i < summon.getEntitiesSummoned(); i++) {
            if (tracker.countActiveOfType(player.getUUID(), type) >= summon.getSummonCap()) {
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
            inHand.shrink(summon.getItemAmountRequired());
        }
    }

    private static void spawnSummon(ServerPlayer player, CallOfTheWildType type,
            TamingSummon summon, Vec3 pos) {
        final ServerLevel world = (ServerLevel) player.level();
        final Mob entity = createEntity(type, world);

        entity.snapTo(pos.x(), pos.y(), pos.z(), player.getYRot(), 0.0F);
        // Natural randomisation (variant/markings/base attributes) — the only public path to vary a
        // horse's look, its setters being private. Must precede our own stat overrides below.
        entity.finalizeSpawn(world, world.getCurrentDifficultyAt(entity.blockPosition()), EntitySpawnReason.EVENT,
                null);
        entity.setBaby(false); // legacy setAdult()
        applyOwnership(entity, player);
        applyTypeStats(entity, type);
        entity.setPersistenceRequired(); // legacy setRemoveWhenFarAway(false)
        entity.setCustomName(TextUtils.toText(LocaleLoader.getString("Taming.Summon.Name.Format",
                player.getName().getString(), type.getDisplayName())));

        world.addFreshEntity(entity);
        ParticleEffectUtils.playCallOfTheWildEffect(new PlatformLivingEntity(entity));

        final CotwSummon tracked = new CotwSummon(entity, type, player.getUUID(),
                McMMOMod.getTransientEntityTracker(), summon.getSummonLifespan());
        McMMOMod.getTransientEntityTracker().addSummon(player.getUUID(), tracked);
    }

    private static Mob createEntity(CallOfTheWildType type, ServerLevel world) {
        return switch (type) {
            case WOLF -> new Wolf(EntityType.WOLF, world);
            case CAT -> new Cat(EntityType.CAT, world);
            case HORSE -> new Horse(EntityType.HORSE, world);
        };
    }

    private static void applyOwnership(Mob entity, ServerPlayer player) {
        if (entity instanceof TamableAnimal tameable) {
            tameable.tame(player); // wolves + cats: sets tamed and owner together
        } else if (entity instanceof AbstractHorse horse) {
            horse.setTamed(true);
            horse.setOwner(player);
        }
    }

    private static void applyTypeStats(Mob entity, CallOfTheWildType type) {
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
                final AttributeInstance jumpAttr =
                        entity.getAttribute(Attributes.JUMP_STRENGTH);
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
        final AttributeInstance attr = entity.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(maxHealth);
        }
    }

    /**
     * Legacy {@code TamingManager#attackTarget}: on a combat hit, every nearby tamed, un-sitting wolf
     * the player owns re-targets the entity they just struck. The own-pet guard stops a player siccing
     * their pack on one of their own animals.
     */
    public static void attackTarget(ServerPlayer player, LivingEntity target) {
        if (target instanceof TamableAnimal tameable && tameable.isOwnedBy(player)) {
            return;
        }

        final AABB searchBox = player.getBoundingBox().inflate(5.0);
        final List<Wolf> wolves = player.level()
                .getEntitiesOfClass(Wolf.class, searchBox, wolf -> true);
        for (Wolf wolf : wolves) {
            if (wolf.isTame() && wolf.isOwnedBy(player) && !wolf.isOrderedToSit()) {
                wolf.setTarget(target);
            }
        }
    }
}
