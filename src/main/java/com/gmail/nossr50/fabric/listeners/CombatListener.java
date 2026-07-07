package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.skills.CombatXp;
import com.gmail.nossr50.util.player.UserManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Awards combat-skill XP when a player kills a living entity (CONVERSION_TODO Phase 3). Replaces the
 * XP slice of the legacy {@code EntityListener#onEntityDeath}/{@code CombatUtils#processCombatXP}.
 *
 * <p>Hooks Fabric's {@link ServerLivingEntityEvents#AFTER_DEATH}. The weapon in the killer's main
 * hand selects the skill ({@link CombatXp#weaponSkill}); the victim's type + category selects the
 * base XP ({@link CombatXp#baseXpForKill}). The deferred per-skill on-hit side effects (Serrated
 * Strikes bleed, Skull Splitter AoE, Archery daze, …) still need combat-damage hooks and land later.
 */
public final class CombatListener {

    private CombatListener() {
    }

    /** Register the kill hook. Called once at mod load from {@code McMMOMod#onInitialize}. */
    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register(CombatListener::onDeath);
    }

    private static void onDeath(LivingEntity victim, DamageSource damageSource) {
        final Entity attacker = damageSource.getAttacker();
        if (!(attacker instanceof ServerPlayerEntity killer)) {
            return; // not a player kill (environmental, mob-on-mob, …).
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(killer.getUuid());
        if (mmoPlayer == null) {
            return;
        }

        final PrimarySkillType skill = CombatXp.weaponSkill(
                Registries.ITEM.getId(killer.getMainHandStack().getItem()).toString());
        final double baseXp = CombatXp.baseXpForKill(
                Registries.ENTITY_TYPE.getId(victim.getType()).toString(), categoryOf(victim));
        if (baseXp <= 0) {
            return;
        }
        mmoPlayer.beginXpGain(skill, (float) baseXp, XPGainReason.PVE, XPGainSource.SELF);
    }

    private static CombatXp.MobCategory categoryOf(LivingEntity entity) {
        if (entity instanceof HostileEntity) {
            return CombatXp.MobCategory.MONSTER;
        }
        if (entity instanceof AnimalEntity) {
            return CombatXp.MobCategory.ANIMAL;
        }
        return CombatXp.MobCategory.OTHER;
    }
}
