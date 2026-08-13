package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.HunterListener;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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
 * <h2>⚠️⚠️ BAND: the structural no-recursion property does NOT exist here</h2>
 * On versions that ship a <b>4-argument</b> {@code dropLoot}, this class injects the 3-arg method and
 * re-invokes the 4-arg one, and the two-method split <em>is</em> the reason it cannot loop — no flag
 * needed. <b>This band has only the 3-arg method</b>, so the bonus roll re-enters the injected method
 * itself and the loop is prevented by an explicit re-entrancy flag instead
 * ({@code mcmmo$inTrophyRoll}, below). Same guarantee, bought deliberately rather than for free.
 *
 * <p>⚠️ Two things follow. The flag is <b>load-bearing</b> — delete it and every kill duplicates its
 * loot until the stack dies. And the description below is a record of the <em>other</em> shape, kept
 * because it explains why the flag is needed here; it does not describe this band's code. Verified
 * against the 1.21.11 merged jar:
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
     * <p>⚠️⚠️ <b>BAND: there is no 4-argument {@code dropLoot} here</b>, so the structural
     * no-recursion property described in the class javadoc — inject the 3-arg, re-invoke the 4-arg —
     * <b>is not available on this band</b>. The bonus roll has to re-enter the very method being
     * injected, which without a guard is an unbounded loop: every roll triggers another roll and the
     * creature's loot duplicates until the stack dies. That is why this is a re-entrancy flag rather
     * than a rename. <b>Do not "simplify" it back</b> — removing the flag reintroduces an item bomb
     * that no compiler and no signature probe can see.
     *
     * <p>On this band the re-invoked method is {@code protected}, so unlike the newest band it must be
     * {@code @Shadow}n rather than called on the cast reference.
     */
    @Inject(method = "dropLoot(Lnet/minecraft/server/world/ServerWorld;"
                    + "Lnet/minecraft/entity/damage/DamageSource;Z)V", allow = 1,
            at = @At("TAIL"))
    private void mcmmo$trophyHunterBonusRoll(ServerWorld world, DamageSource source,
            boolean causedByPlayer, CallbackInfo ci) {
        // The bonus roll re-enters this very method (see above), so the nested pass must not schedule
        // a roll of its own. Without this the loot duplicates until the stack dies.
        if (mcmmo$inTrophyRoll) {
            return;
        }
        final LivingEntity self = (LivingEntity) (Object) this;
        // causedByPlayer is passed straight through so the bonus roll sees exactly the loot conditions
        // the first roll did -- Looting, player-kill-only drops and the killer's luck all included.
        HunterListener.onLootDropped(self, source, () -> {
            mcmmo$inTrophyRoll = true;
            try {
                mcmmo$dropLoot(world, source, causedByPlayer);
            } finally {
                // finally, not a trailing assignment: a loot table that throws must not leave this
                // creature flagged forever -- it would silently never earn a bonus roll again.
                mcmmo$inTrophyRoll = false;
            }
        });
    }

    /** Set only while the bonus roll re-enters {@code dropLoot}; see the javadoc above. */
    @Unique
    private boolean mcmmo$inTrophyRoll;

    /**
     * Vanilla's own 3-arg {@code dropLoot} — the one this class injects into, re-entered for the bonus
     * roll behind {@link #mcmmo$inTrophyRoll}.
     *
     * <p>{@code @Shadow} carries a {@code mcmmo$} prefix so the shadow does not collide with the
     * target's own member while still resolving to it.
     */
    @Shadow(prefix = "mcmmo$")
    protected abstract void mcmmo$dropLoot(ServerWorld world, DamageSource source,
            boolean causedByPlayer);
}
