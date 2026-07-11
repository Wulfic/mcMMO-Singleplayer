package com.gmail.nossr50.platform;

import com.gmail.nossr50.fabric.McMMOMod;
import java.util.UUID;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.NotNull;

/**
 * Adapter over a vanilla {@link ServerPlayerEntity}, replacing {@code org.bukkit.entity.Player}
 * (217 references — the single heaviest Bukkit surface in mcMMO).
 *
 * <p>Only the mined top-usage subset of the Bukkit {@code Player} API is wrapped (see
 * CONVERSION_TODO.md Phase 2 / memory {@code phase-2-adapter-layer}): identity, world/position,
 * vitals, movement/mode state, and message sending. Ported code holds a {@link PlatformPlayer}
 * and calls the mimicked methods; where a call needs the full vanilla surface, use
 * {@link #unwrap()}.
 *
 * <p>Deliberately NOT yet wrapped (each needs its own grounded adapter first):
 * <ul>
 *   <li>{@code getInventory()} / equipment (57 refs) — needs the ItemStack adapter. Raw
 *       {@link #getInventory()} is exposed as a stopgap returning the vanilla inventory.</li>
 *   <li>Bukkit metadata ({@code get/set/has/removeMetadata}, ~24 refs) — Bukkit's entity
 *       metadata has no vanilla equivalent; it maps to a transient side-table (WeakHashMap
 *       keyed by entity) or Fabric's data-attachment API, designed in its own step.</li>
 * </ul>
 *
 * <p>Singleplayer note: {@code sendMessage} already targets {@link Text}, the locked text type
 * (no Adventure), so messaging maps 1:1. Server-side only ({@link ServerPlayerEntity}).
 */
public final class PlatformPlayer {

    private final ServerPlayerEntity handle;

    public PlatformPlayer(@NotNull ServerPlayerEntity handle) {
        this.handle = handle;
    }

    /** The wrapped vanilla player. Use when the mimicked surface is insufficient. */
    public @NotNull ServerPlayerEntity unwrap() {
        return handle;
    }

    // --- Identity -----------------------------------------------------------

    /** Player name (Bukkit {@code getName()}). {@link ServerPlayerEntity#getName()} returns
     *  {@link Text}, so this flattens it to a plain string. */
    public @NotNull String getName() {
        return handle.getName().getString();
    }

    public @NotNull UUID getUniqueId() {
        return handle.getUuid();
    }

    // --- World / position ---------------------------------------------------

    public @NotNull ServerWorld getWorld() {
        // getEntityWorld() replaced Bukkit getWorld(); for a ServerPlayerEntity it is a ServerWorld.
        return (ServerWorld) handle.getEntityWorld();
    }

    public @NotNull BlockPos getBlockPos() {
        return handle.getBlockPos();
    }

    public @NotNull Vec3d getPos() {
        return handle.getEntityPos();
    }

    // --- Vitals -------------------------------------------------------------

    public float getHealth() {
        return handle.getHealth();
    }

    public float getMaxHealth() {
        return handle.getMaxHealth();
    }

    /** Bukkit {@code isValid()}/{@code isOnline()} collapse to alive-and-in-world here. */
    public boolean isAlive() {
        return handle.isAlive();
    }

    // --- Mode / movement state ---------------------------------------------

    public @NotNull GameMode getGameMode() {
        return handle.interactionManager.getGameMode();
    }

    public boolean isCreative() {
        return handle.isCreative();
    }

    public boolean isSpectator() {
        return handle.isSpectator();
    }

    public boolean isSneaking() {
        return handle.isSneaking();
    }

    /**
     * Bukkit {@code Player#isBlocking()}: whether the player is actively raising a shield. Maps to
     * vanilla {@link net.minecraft.entity.LivingEntity#isBlocking()}. Consumed by the Acrobatics
     * Dodge gate, which suppresses a dodge while the player is blocking.
     */
    public boolean isBlocking() {
        return handle.isBlocking();
    }

    // --- Messaging (Text = locked target type) ------------------------------

    /** Bukkit {@code sendMessage} (127 refs). Chat message. */
    public void sendMessage(@NotNull Text message) {
        handle.sendMessage(message);
    }

    /** Action-bar / overlay message (Bukkit {@code sendActionBar}). */
    public void sendActionBar(@NotNull Text message) {
        handle.sendMessage(message, true);
    }

    // --- Sound (Bukkit Player#playSound / World#playSound) -------------------

    /**
     * Plays a sound at this player's position. Replaces Bukkit's
     * {@code Player#playSound(Location, Sound, SoundCategory, volume, pitch)} and
     * {@code World#playSound(...)}; in singleplayer the "only this player hears it" vs. "everyone
     * nearby hears it" distinction collapses (one listener), so both route here — spatialized at the
     * player via {@link ServerWorld#playSound}. The {@code soundRegistryId} is a namespaced sound id
     * (e.g. {@code minecraft:block.anvil.place}, from {@link com.gmail.nossr50.util.sounds.SoundType});
     * an unknown id is logged and skipped rather than thrown, so a bad custom id never breaks gameplay.
     *
     * @param soundRegistryId namespaced vanilla sound id
     * @param category volume-slider category the sound obeys
     * @param volume final volume (already master-scaled by the caller)
     * @param pitch final pitch
     */
    public void playSound(@NotNull String soundRegistryId, @NotNull SoundCategory category,
            float volume, float pitch) {
        Identifier id = Identifier.tryParse(soundRegistryId);
        if (id == null || !Registries.SOUND_EVENT.containsId(id)) {
            McMMOMod.LOGGER.warn("No vanilla sound for id '{}'", soundRegistryId);
            return;
        }
        SoundEvent soundEvent = Registries.SOUND_EVENT.get(id);
        Vec3d pos = getPos();
        // except = null → all players in range hear it (the one player, in singleplayer).
        getWorld().playSound(null, pos.x, pos.y, pos.z, soundEvent, category, volume, pitch);
    }

    // --- Held items ---------------------------------------------------------

    /**
     * The stack in the main hand (Bukkit {@code getInventory().getItemInMainHand()}). Consumed by
     * the super-ability activation trigger and tool-type detection ({@link
     * com.gmail.nossr50.datatypes.skills.ToolType#inHand}). Returns an empty {@link ItemStack} (never
     * null) when the hand is empty, matching vanilla {@link net.minecraft.entity.LivingEntity#getMainHandStack()}.
     */
    public @NotNull ItemStack getMainHandStack() {
        return handle.getMainHandStack();
    }

    /** The stack in the off hand (Bukkit {@code getInventory().getItemInOffHand()}); empty when none. */
    public @NotNull ItemStack getOffHandStack() {
        return handle.getOffHandStack();
    }

    // --- Acrobatics fall/roll support (K2) ----------------------------------

    /**
     * Whether either hand holds an Ender Pearl. Consumed by the Acrobatics exploit check (throwing
     * pearls to trigger fall damage is a known XP-farm). Bukkit
     * {@code ItemUtils.hasItemInEitherHand(player, Material.ENDER_PEARL)}.
     */
    public boolean hasEnderPearlInEitherHand() {
        return handle.getMainHandStack().isOf(Items.ENDER_PEARL)
                || handle.getOffHandStack().isOf(Items.ENDER_PEARL);
    }

    /**
     * Whether the player is riding an entity (Bukkit {@code Player#isInsideVehicle()} →
     * {@link net.minecraft.entity.Entity#hasVehicle()}). Consumed by the Acrobatics exploit check
     * (fall damage while mounted is disallowed for XP).
     */
    public boolean isInsideVehicle() {
        return handle.hasVehicle();
    }

    /**
     * Whether the player's boots (any equipped armor, since Feather Falling only rolls on boots) carry
     * the Feather Falling enchantment. Consumed by the Roll XP calculation, which boosts fall XP for
     * players who invested in fall-damage gear. Resolves the enchantment from the world's dynamic
     * registry; if the enchantment registry is somehow absent the check degrades to {@code false}.
     */
    public boolean hasFeatherFallingBoots() {
        RegistryEntry<Enchantment> featherFalling = getWorld().getRegistryManager()
                .getOrThrow(RegistryKeys.ENCHANTMENT)
                .getOrThrow(Enchantments.FEATHER_FALLING);
        return EnchantmentHelper.getEquipmentLevel(featherFalling, handle) > 0;
    }

    /**
     * The packed key ({@link BlockPos#asLong()}) of the block the player is standing in, used by the
     * Acrobatics fall-location history to throttle repeat XP farming on the same block.
     */
    public long getFeetBlockKey() {
        return handle.getBlockPos().asLong();
    }

    // --- Stopgap raw accessors (pending dedicated adapters) ------------------

    /** Stopgap: raw vanilla inventory until the ItemStack adapter lands. */
    public @NotNull PlayerInventory getInventory() {
        return handle.getInventory();
    }
}
