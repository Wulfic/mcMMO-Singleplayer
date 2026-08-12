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
 * <h2>⚠️⚠️ BAND mc/1.21.8 — the no-recursion property is NOT structural here, it is a guard</h2>
 * Newer versions split this into two methods, and the split is what makes the bonus roll safe there:
 *
 * <pre>
 *   protected void dropLoot(ServerWorld, DamageSource, boolean)                 &lt;- injected here
 *       getLootTableKey(); if empty return; dropLoot(world, source, flag, key)
 *
 *   public    void dropLoot(ServerWorld, DamageSource, boolean, RegistryKey)    &lt;- master re-invokes
 *       generateLoot(world, source, flag, key, this::dropStack)
 * </pre>
 *
 * <b>Neither the 4-arg {@code dropLoot} nor {@code generateLoot} exists on this band.</b> Verified
 * against this band's merged jar: {@code LivingEntity} declares exactly one {@code dropLoot}, the
 * 3-arg one, and it builds the {@code LootWorldContext} and generates the loot <em>inline</em>. So
 * there is no lower-level entry point to re-enter, and re-invoking the only method there is means
 * re-entering the very method this injects into.
 *
 * <p>That is the item bomb the newer shape rules out for free: without a guard, each bonus roll
 * triggers another TAIL injection, which triggers another bonus roll, <b>duplicating the loot all the
 * way down until the stack dies</b>. The two honest options were (a) reimplement vanilla's ~50
 * instructions of loot-context assembly here, which silently rots the first time upstream adds a
 * context parameter, or (b) re-enter the real method behind a re-entrancy flag. <b>(b) is chosen</b>:
 * the second roll then goes through vanilla's own code, so Looting, luck, player-kill-only drops and
 * any future context parameter stay correct by construction.
 *
 * <p>🔑 The flag is a per-entity {@code @Unique} field, not a static or a {@code ThreadLocal}: loot
 * drops on the server thread, and per-entity state cannot leak between two creatures dying in the same
 * tick. It is reset in a {@code finally}, so a throwing loot table cannot leave an entity permanently
 * unable to earn a bonus roll.
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
     * <p>⚠️ On this band the re-invoked method is {@code protected}, so unlike master it must be
     * {@code @Shadow}n rather than called on the cast reference.
     */
    @Inject(method = "dropLoot(Lnet/minecraft/server/world/ServerWorld;"
                    + "Lnet/minecraft/entity/damage/DamageSource;Z)V", allow = 1,
            at = @At("TAIL"))
    private void mcmmo$trophyHunterBonusRoll(ServerWorld world, DamageSource source,
            boolean causedByPlayer, CallbackInfo ci) {
        // The bonus roll re-enters this very method (see the class javadoc), so the nested pass must
        // not schedule a roll of its own. Without this the loot duplicates until the stack dies.
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

    /** Set only while the bonus roll re-enters {@code dropLoot}; see the class javadoc. */
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
