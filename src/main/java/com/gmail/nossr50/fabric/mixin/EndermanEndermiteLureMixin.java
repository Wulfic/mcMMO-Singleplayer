package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.MetadataStore;
import com.gmail.nossr50.platform.CombatUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.EndermiteEntity;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks an enderman that has been lured by an endermite, so killing it pays no combat XP — mcMMO's
 * replacement for Bukkit's {@code EntityTargetLivingEntityEvent}
 * ({@code ExploitFix.EndermanEndermiteFarms}, legacy {@code EntityListener#onEntityTargetEntity}).
 *
 * <p>An endermite in a minecart is the standard way to build an enderman grinder: endermen aggro it
 * from a distance, walk to a fixed spot and queue up to be killed. Nothing about the mobs
 * <em>themselves</em> is unusual — they spawn naturally, so the {@code MobOrigin} gate reads them as
 * {@code NATURAL} — which is why this needs a flag of its own.
 *
 * <p>⚠️ <b>Upstream stamps this flag and never reads it.</b> {@code EXPLOITED_ENDERMEN} appears in
 * exactly one place in legacy — the write — and no arm of legacy's own mob-flag XP chain in
 * {@code CombatUtils} mentions it, in any version. So upstream's endermite gate has never done
 * anything. It is made real here rather than ported faithfully-dead because the key ships in this
 * port's {@code experience.yml} promising exactly this protection; a switch on a mechanism that does
 * not exist is worse than no switch (GitHub #9).
 *
 * <p><b>The seam:</b> {@code MobEntity#setTarget(LivingEntity)} is public and is the single funnel
 * every targeting path bottoms out in, so it catches the goal-driven aggro, retaliation and any
 * other route without needing to know which one fired. {@code EndermanEntity} does not override it,
 * hence the mixin sits on {@code MobEntity} and filters by type — two {@code instanceof} checks on a
 * call that only happens when a mob changes its mind about who to attack.
 */
@Mixin(MobEntity.class)
public abstract class EndermanEndermiteLureMixin {

    @Inject(method = "setTarget(Lnet/minecraft/entity/LivingEntity;)V", at = @At("HEAD"))
    private void mcmmo$flagEndermiteLuredEnderman(LivingEntity target, CallbackInfo ci) {
        if (!(target instanceof EndermiteEntity)) {
            return;
        }
        final MobEntity self = (MobEntity) (Object) this;
        if (!(self instanceof EndermanEntity)) {
            return;
        }
        // Checked at the write as well as at the read so the flag is never stamped at all while the
        // gate is off -- an operator who runs with it disabled pays no bookkeeping for it.
        final ExperienceConfig config = McMMOMod.getExperienceConfig();
        if (config == null || !config.isEndermanEndermiteFarmingPrevented()) {
            return;
        }
        if (!MetadataStore.has(self, CombatUtils.ENDERMITE_LURED_KEY)) {
            MetadataStore.setFlag(self, CombatUtils.ENDERMITE_LURED_KEY);
        }
    }
}
