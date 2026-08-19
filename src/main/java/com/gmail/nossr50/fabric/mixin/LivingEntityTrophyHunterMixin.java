package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.HunterListener;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
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
 * <h2>⚠️⚠️ The no-recursion property, and why it is an explicit guard here</h2>
 * A second roll is made by re-invoking the creature's own loot drop, so this handler can re-enter
 * itself. How that is prevented is <b>version-specific</b>, and getting it wrong produces an item
 * bomb rather than a silent no-op — which is why it is spelled out rather than left to be re-derived.
 *
 * <p>Where {@code dropLoot} is split into two overloads — a table-resolving one that delegates to a
 * generating one whose entire body is a single {@code generateLoot} call — injecting into the
 * <em>resolving</em> overload and re-invoking the <em>generating</em> one cannot loop, structurally
 * and for free.
 *
 * <p><b>This version has no such split:</b> {@code dropLoot(DamageSource, boolean)} is the only
 * overload, so re-invoking it from a {@code TAIL} injection on itself would recurse until the stack
 * dies. The structural property is therefore replaced by an explicit one — {@link #mcmmo$inBonusRoll},
 * a re-entrancy flag set around the bonus invocation and cleared in a {@code finally}. It is an
 * instance field, so it cannot leak between two creatures dying in the same tick.
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

    /** Guards {@link #mcmmo$trophyHunterBonusRoll} against re-entering itself via the bonus roll. */
    @Unique
    private boolean mcmmo$inBonusRoll;

    /**
     * The creature's own loot drop. Shadowed rather than called on the cast reference because it is
     * {@code protected} at this version; the bonus roll re-invokes it to produce a second roll.
     */
    @Shadow
    protected abstract void dropLoot(DamageSource source, boolean causedByPlayer);

    /**
     * Offer the kill to Trophy Hunter once vanilla has finished dropping the creature's loot.
     *
     * <p>⚠️ {@code TAIL} binds to the last return instruction, so a creature that exits early with
     * nothing to drop never reaches the roll. The bind count is a per-version bytecode fact — see
     * {@code scripts/mixin-allow-audit.py}, which is a ship gate precisely because {@code allow = N}
     * cannot be carried across versions by hand.
     *
     * <p>The re-entrancy check is not belt-and-braces: at this version the bonus roll calls the very
     * method this is injected into. See the class doc.
     */
    @Inject(method = "dropLoot(Lnet/minecraft/entity/damage/DamageSource;Z)V", allow = 1,
            at = @At("TAIL"))
    private void mcmmo$trophyHunterBonusRoll(DamageSource source, boolean causedByPlayer,
            CallbackInfo ci) {
        if (mcmmo$inBonusRoll) {
            return;
        }
        final LivingEntity self = (LivingEntity) (Object) this;
        // causedByPlayer is passed straight through so the bonus roll sees exactly the loot conditions
        // the first roll did -- Looting, player-kill-only drops and the killer's luck all included.
        HunterListener.onLootDropped(self, source, () -> {
            mcmmo$inBonusRoll = true;
            try {
                dropLoot(source, causedByPlayer);
            } finally {
                mcmmo$inBonusRoll = false;
            }
        });
    }
}
