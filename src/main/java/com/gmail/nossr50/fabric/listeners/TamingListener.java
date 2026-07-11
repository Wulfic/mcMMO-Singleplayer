package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.skills.taming.TamingManager;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.text.ConfigStringUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * The K7 Taming XP hook: awards Taming XP when a player tames an animal (CONVERSION_TODO §B). Replaces
 * the XP slice of legacy {@code EntityListener#onEntityTame}.
 *
 * <p>Vanilla has no Fabric event for taming, so this is driven by two mixins on the tame-by-player
 * entry points — {@code TameableEntity#setTamedBy} (wolves/cats/parrots) and
 * {@code AbstractHorseEntity#bondWithPlayer} (horses/donkeys/mules/llamas/camels). Both funnel into
 * {@link #onEntityTamed(PlayerEntity, Entity)}. The tamed entity's registry path is turned into the
 * config-entity string ({@code minecraft:wolf} → {@code "Wolf"}) that
 * {@code ExperienceConfig.getTamingXP} is keyed on; the MC-free award itself lives on
 * {@link TamingManager#awardTamingXP(String)}.
 *
 * <p>The dropped legacy surface (NPC/egg-mob/spawner-mob exclusion metadata flags, WorldGuard) has no
 * singleplayer analogue. The {@code PLAYER_TAMED_MOB} flag the legacy listener set was only read by
 * those dropped exclusion paths, so it is dropped too.
 */
public final class TamingListener {

    private TamingListener() {
    }

    /**
     * Award Taming XP to the owner for a freshly tamed animal. Gated to server players with loaded
     * mcMMO data; a no-op otherwise. Called from the tame mixins.
     *
     * @param owner the player who tamed the animal (mixin passes the vanilla {@code PlayerEntity})
     * @param tamed the entity that was tamed
     */
    public static void onEntityTamed(PlayerEntity owner, Entity tamed) {
        if (!(owner instanceof ServerPlayerEntity serverPlayer)) {
            return; // client-side / null owner — the authoritative award happens on the server.
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(serverPlayer.getUuid());
        if (mmoPlayer == null) {
            return; // data not loaded (e.g. mid-join).
        }
        final TamingManager taming = mmoPlayer.getTamingManager();
        if (taming == null) {
            return;
        }

        final String entityConfigString = ConfigStringUtils.getConfigEntityTypeString(
                Registries.ENTITY_TYPE.getId(tamed.getType()).getPath());
        taming.awardTamingXP(entityConfigString);
    }
}
