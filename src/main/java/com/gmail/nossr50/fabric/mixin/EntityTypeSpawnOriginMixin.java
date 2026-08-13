package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.platform.MobOrigins;
import java.util.Optional;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hunter's D-HU1 anti-farm gate, first half: records why a mob does not count toward mob mastery, at
 * the moment it is created.
 *
 * <p>Two injectors, and the second is not redundant on every band. {@link #mcmmo$stampSpawnOrigin}
 * writes the marker at creation; {@link #mcmmo$restampAfterNbtRead} writes it again once the NBT
 * read has finished, because on a Minecraft version whose newest available fabric-api still ships
 * {@code data-attachment-api} 1.6.2, that read <b>erases</b> the first write. See that method.
 *
 * <h2>⚠️ Why this target and not {@code MobEntity#initialize}</h2>
 * {@code initialize(ServerWorldAccess, LocalDifficulty, SpawnReason, EntityData)} is the obvious
 * choice and is what the plan implied — both spawner logics call it explicitly with their reason. It
 * would have been wrong. 57 classes override it in 1.21.11 and exactly one does not call
 * {@code super}: {@code CaveSpiderEntity}, whose entire override is
 *
 * <pre>{@code   0: aload 4
 *   2: areturn}</pre>
 *
 * a bare pass-through that skips {@code SpiderEntity}'s jockey and potion-effect logic. Every cave
 * spider in the game would therefore have escaped the gate — and a mineshaft cave-spider spawner is
 * among the most commonly built grinders there is, so the miss would have landed precisely on the case
 * the gate exists for, while passing any test written with a zombie.
 *
 * <p>{@code EntityType#create(World, SpawnReason)} is the factory that all of it bottoms out in (the
 * four verified call chains are listed in {@link MobOrigins}). It is an instance method on a class with
 * no vanilla subclasses, so nothing can override it away, and its body ignores the
 * {@code SpawnReason} it is handed — vanilla passes the reason down purely so callers further up can
 * branch on it — which makes reading it here free of behavioural risk.
 *
 * <h2>The descriptor is load-bearing</h2>
 * {@code EntityType} declares two {@code create} methods. The six-argument one delegates to this one,
 * so injecting into both would double-stamp; the full descriptor picks the single lower method. The
 * generic {@code T} erases to {@code Entity}.
 *
 * <p>Note this fires for {@code SpawnReason.LOAD} as well, i.e. for every mob in every chunk that
 * loads. That is why {@link MobOrigins#stampOnSpawn} writes nothing for a qualifying origin: the work
 * on the hot path is one switch and one boolean, and a mob whose marker is about to be restored from
 * NBT must not have it overwritten first.
 */
@Mixin(EntityType.class)
public abstract class EntityTypeSpawnOriginMixin {

    @Inject(
            method = "create(Lnet/minecraft/world/World;Lnet/minecraft/entity/SpawnReason;)"
                    + "Lnet/minecraft/entity/Entity;", allow = 2,
            at = @At("RETURN"))
    private void mcmmo$stampSpawnOrigin(World world, SpawnReason reason,
            CallbackInfoReturnable<Entity> cir) {
        // The return value is null when the entity type sits behind a disabled feature flag;
        // stampOnSpawn handles that, along with the client-side and non-living cases.
        MobOrigins.stampOnSpawn(world, reason, cir.getReturnValue());
    }

    /**
     * Re-stamps after the NBT read, because on this band the read <b>erases</b> what
     * {@link #mcmmo$stampSpawnOrigin} just wrote.
     *
     * <h2>The defect this exists for</h2>
     * Fabric's {@code data-attachment-api} at {@code 1.6.2} — the version this band's newest
     * available fabric-api ships, so there is no upgrade out of it — assigns the deserialized
     * attachment map <b>unconditionally</b> in {@code fabric_readAttachmentsFromNbt}:
     *
     * <pre>{@code   this.fabric_dataAttachments = deserializeAttachmentData(nbt, registries);}</pre>
     *
     * and {@code deserializeAttachmentData} returns {@code null} when the NBT carries no
     * attachments. Later releases early-return on that null and keep what the entity already had.
     * So here, {@code Entity#readNbt} wipes the whole attachment map — and every NBT-carrying spawn
     * path ({@code /summon}, mob spawners, trial spawners, chunk load) runs
     * {@code create(World, SpawnReason)} <em>then</em> {@code readNbt}, in that order. The mob-origin
     * marker was written and immediately destroyed, so every disqualifying origin read as
     * {@code NATURAL} and the anti-farm gate was open.
     *
     * <h2>⚠️ Why here and not at {@code Entity#readNbt} RETURN</h2>
     * A second injector on {@code readNbt} is the general fix and is the wrong one: it would land on
     * the same target as fabric's own injector, and which of the two runs last is a question of
     * cross-mod mixin priority. That is not a guarantee worth betting a silent exploit gate on —
     * when it loses, nothing fails, nothing logs, and the farm simply works again.
     *
     * <p>{@code getEntityFromNbt} is strictly <em>outside</em> {@code readNbt}, which makes it
     * ordering-proof rather than merely ordered. Read off this band's merged jar, its body is
     *
     * <pre>{@code   fromNbt(nbt).map(type -> type.create(world, reason))}
     * {@code   → Util.ifPresentOrElse(opt, entity -> entity.readNbt(nbt), () -> log)}</pre>
     *
     * so the read has completed before the single {@code areturn} this binds to.
     *
     * <h2>The spawn-egg path needs nothing, and that was measured</h2>
     * A gate that closes {@code /summon} but leaves spawn eggs open would be worse than no gate,
     * because it reads as fixed. It does not arise: {@code EntityType#nbtCopier} short-circuits when
     * the {@code ENTITY_DATA} component is empty, which is every ordinary spawn egg, and when the
     * component <em>is</em> present {@code NbtComponent#applyToEntity} does
     * {@code writeNbt(new NbtCompound()) → copyFrom(nbt) → readNbt} — a round trip that
     * re-serializes the live attachment map, so the marker survives its own erasure.
     *
     * <p>Calling {@link MobOrigins#stampOnSpawn} rather than anything bespoke is deliberate: it
     * writes nothing for an origin that qualifies, so {@code SpawnReason.LOAD} still leaves alone
     * the marker fabric has just restored from a previous session's NBT.
     */
    @Inject(
            method = "getEntityFromNbt(Lnet/minecraft/nbt/NbtCompound;Lnet/minecraft/world/World;"
                    + "Lnet/minecraft/entity/SpawnReason;)Ljava/util/Optional;", allow = 1,
            at = @At("RETURN"))
    private static void mcmmo$restampAfterNbtRead(NbtCompound nbt, World world, SpawnReason reason,
            CallbackInfoReturnable<Optional<Entity>> cir) {
        final Optional<Entity> loaded = cir.getReturnValue();
        if (loaded == null || loaded.isEmpty()) {
            return; // malformed or unknown entity id — vanilla has already logged and skipped it.
        }
        MobOrigins.stampOnSpawn(world, reason, loaded.get());
    }
}
