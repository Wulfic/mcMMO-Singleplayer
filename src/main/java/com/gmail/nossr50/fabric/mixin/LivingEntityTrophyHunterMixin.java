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
 * <h2>⚠️⚠️ BAND: the no-recursion property is STRUCTURAL on newer versions and is NOT available here</h2>
 * Where a 4-argument {@code dropLoot} exists, this class injects the <b>3-argument</b> one and the
 * bonus roll re-invokes the <b>4-argument</b> one, whose whole body is a single {@code generateLoot}
 * call — so it can never re-enter the injected method, and no guard is needed.
 *
 * <p><b>This band has only the 3-argument overload</b>, verified against its own merged jar:
 *
 * <pre>
 *   protected void dropLoot(ServerWorld, DamageSource, boolean)     &lt;- injected here AND re-invoked
 * </pre>
 *
 * So the bonus roll must re-enter the very method being injected, and without a guard that is an
 * unbounded loop: each roll triggers another roll and the creature's loot duplicates until the stack
 * dies. <b>That is why this band carries a re-entrancy flag rather than a rename</b>, and why the flag
 * must not be "simplified" away — removing it produces an item bomb, not a silent no-op, and neither
 * the compiler nor a signature probe nor the mixin audit can see it. The same redesign was needed on
 * every band below the 4-argument overload's introduction.
 *
 * <p>Because the re-invoked method is {@code protected} here, it is {@code @Shadow}n rather than
 * called on the cast reference.
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
     * <p>⚠️⚠️ <b>BAND: the bonus roll re-enters this very method</b> — see the class javadoc. The
     * re-entrancy flag below is load-bearing, not defensive: without it the loot duplicates without
     * bound. Do not remove it while porting this file forward.
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
