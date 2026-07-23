package com.gmail.nossr50.util;

/**
 * Misc constants and helpers. The legacy {@code Misc} was a grab-bag of Bukkit item/entity spawn
 * helpers ({@code spawnItem}/{@code spawnItems*}), NPC/Villager detection, and the mod-name set
 * used to skip modded mobs. Those all sit on {@code org.bukkit.*} entity/location types and are
 * replaced by the {@code platform/} spawn adapters as the skill bodies that need them port
 * (CONVERSION_TODO Phase 10/11) — so only the MC-free time/tick conversion constants are ported
 * here for now (they drive the super-ability cooldown/duration math and the scheduler delays).
 */
public final class Misc {

    /** Milliseconds per second — converts between wall-clock millis and the second-granularity
     *  ability-deactivation timestamps stored in {@link com.gmail.nossr50.datatypes.player.PlayerProfile}. */
    public static final int TIME_CONVERSION_FACTOR = 1000;

    /** Game ticks per second — converts an ability duration in seconds to scheduler ticks. */
    public static final int TICK_CONVERSION_FACTOR = 20;

    /** Seconds after a respawn during which XP-farm-prone payouts are withheld. Paired with
     *  {@link com.gmail.nossr50.datatypes.player.McMMOPlayer#getRespawnATS()} — in singleplayer the
     *  only live consumer is the Acrobatics Dodge XP gate (legacy's other one is the PvP combat-XP
     *  branch, unreachable here). */
    public static final int PLAYER_RESPAWN_COOLDOWN_SECONDS = 5;

    private Misc() {
    }

    // PORT Phase 10/11: the Bukkit spawn/entity body (spawnItem, spawnItemsBonusAmount,
    // spawnItemsFromCollection, isNPCIncludingVillagers, getBlockCenter, the modded-mob name set,
    // SKILL_MESSAGE_MAX_SENDING_DISTANCE) is retargeted onto the platform/ item-spawn + entity
    // adapters alongside the skill bodies that call it. Not needed by the cooldown core.
}
