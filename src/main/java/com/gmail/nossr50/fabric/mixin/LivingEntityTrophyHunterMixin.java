package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.HunterListener;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hunter stage 6 — <b>Trophy Hunter</b>: a chance-gated second roll of the creature's own loot table.
 *
 * <p>Not a bespoke per-mob bonus table (a ~120-row data-authoring job that would ignore Looting and go
 * stale on every Minecraft release) and not rare-slot weighting (which needs loot-table
 * introspection). A second roll respects Looting for free, needs no new data, and means "more of what
 * that creature drops" — more rotten flesh, but also more gunpowder and ender pearls.
 *
 * <h2>⚠️⚠️ The no-recursion property, and why it belongs to THESE TWO METHODS specifically</h2>
 * The injection is on the <b>3-argument</b> {@code dropLoot} and the bonus roll re-invokes the
 * <b>4-argument</b> one. That is not stylistic — it is the entire reason this class cannot loop.
 * Verified against the 1.21.11 merged jar:
 *
 * <pre>
 *   protected void dropLoot(ServerWorld, DamageSource, boolean)                 &lt;- injected here
 *       getLootTableKey(); if empty return; dropLoot(world, source, flag, key)
 *
 *   public    void dropLoot(ServerWorld, DamageSource, boolean, RegistryKey)    &lt;- re-invoked
 *       generateLoot(world, source, flag, key, this::dropStack)   // offsets 0-16, nothing else
 * </pre>
 *
 * The 4-arg overload's whole body is one {@code generateLoot} call, so it can never re-enter the 3-arg
 * one. <b>Move this injection to the 4-arg overload and it recurses until the stack dies, duplicating
 * the loot all the way down.</b> Getting this wrong produces an item bomb, not a silent no-op, which is
 * why the shape is spelled out here instead of left to be re-derived.
 *
 * <h2>The funnel, checked rather than assumed</h2>
 * A binary grep of the merged jar for {@code dropLoot} returns exactly two classes: {@link LivingEntity}
 * and {@code MobEntity}. {@code MobEntity} does override the 3-arg method — but it calls
 * {@code super.dropLoot(...)} <em>first</em> and only then clears its one-shot {@code lootTable} field,
 * so this handler runs while the key is still resolvable. Nothing skips the super call, so unlike the
 * {@code initialize} seam stage 1 had to abandon there is no {@code CaveSpiderEntity}-shaped hole here.
 *
 * <h2>Why the second roll is genuinely independent</h2>
 * {@code LivingEntity#getLootTableSeed()} returns a hard {@code 0}, and
 * {@code LootContext.Builder#random(long)} <em>ignores</em> a zero seed rather than pinning the RNG to
 * it — so the bonus roll is a fresh roll, not a copy of the first. ({@code MobEntity} can return a
 * non-zero seed, but only when NBT set one, which in practice means a spawner-placed creature — refused
 * by the spawn-origin gate long before it reaches here.)
 *
 * <h2>⚠️ This fires for EVERY death, including ones with no killer</h2>
 * A creature drowning, burning or falling on the far side of the world reaches this method. The gating
 * lives in {@link HunterListener#onLootDropped}, behind the same four checks the kill counter passes,
 * because "is there loot here" and "did a player earn it" are different questions and only the second
 * has an answer worth acting on.
 *
 * @see <a href="file:../../../../../../../../plans/new-skills/hunter.md">plans/new-skills/hunter.md</a>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityTrophyHunterMixin {

    /**
     * Offer the kill to Trophy Hunter once vanilla has finished dropping the creature's loot.
     *
     * <p>⚠️ <b>{@code TAIL} rather than {@code RETURN}, and the difference is load-bearing:</b> the
     * 3-arg method has <b>two</b> return instructions, and the first is the early-out taken when the
     * creature has no loot table at all. {@code TAIL} binds to the last one, so a creature with nothing
     * to drop never reaches the roll — correct, and free.
     *
     * <p>Neither the 4-arg {@code dropLoot} nor {@code getLootTableKey} is {@code @Shadow}n: both are
     * public API on {@code LivingEntity}/{@code Entity}, so an ordinary call on the cast reference does
     * the same job with one less thing to drift.
     */
    @Inject(method = "dropLoot(Lnet/minecraft/server/level/ServerLevel;"
                    + "Lnet/minecraft/world/damagesource/DamageSource;Z)V", allow = 1,
            at = @At("TAIL"))
    private void mcmmo$trophyHunterBonusRoll(ServerLevel world, DamageSource source,
            boolean causedByPlayer, CallbackInfo ci) {
        final LivingEntity self = (LivingEntity) (Object) this;
        // causedByPlayer is passed straight through so the bonus roll sees exactly the loot conditions
        // the first roll did -- Looting, player-kill-only drops and the killer's luck all included.
        HunterListener.onLootDropped(self, source, () -> self.getLootTable()
                .ifPresent(key -> self.dropFromLootTable(world, source, causedByPlayer, key)));
    }
}
