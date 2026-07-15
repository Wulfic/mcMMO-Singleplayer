package com.gmail.nossr50.platform;

import com.gmail.nossr50.datatypes.skills.ToolType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.util.BlockUtils;
import com.gmail.nossr50.util.ItemUtils;
import java.util.UUID;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
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

    // --- Super-ability activation support (K6) ------------------------------

    /**
     * Whether the stack currently in the main hand matches {@code toolType} (Bukkit
     * {@code ToolType#inHand(getInventory().getItemInMainHand())}). Keeps the MC-typed
     * {@link ItemStack} inspection in the platform layer so the MC-free super-ability activation
     * trigger on {@link com.gmail.nossr50.datatypes.player.McMMOPlayer} can gate on a pure
     * {@link ToolType}.
     */
    public boolean isHoldingTool(@NotNull ToolType toolType) {
        return toolType.inHand(handle.getMainHandStack());
    }

    /**
     * Whether the main-hand stack is the given vanilla item, named Bukkit-style or as a namespaced
     * id (Bukkit {@code getInventory().getItemInMainHand().getType() == <material>}). Resolves the
     * name through {@link Materials}, so an unknown name is simply "not held" rather than a crash —
     * used for config-named items such as the Blast Mining detonator.
     */
    public boolean isHoldingItem(@NotNull String itemName) {
        final ItemStack held = handle.getMainHandStack();
        return Materials.item(itemName).map(held::isOf).orElse(false);
    }

    /**
     * Whether the main-hand stack counts as "unarmed" for the Unarmed skill (Bukkit
     * {@code ItemUtils.isUnarmed(player.getInventory().getItemInMainHand())}) — an empty hand, or
     * any non-tool item when the {@code Unarmed_Items_As_Unarmed} config is on.
     *
     * <p>Lives here rather than being split onto the caller because the caller
     * ({@code UnarmedManager#canDeflect}) holds no {@link ItemStack} of its own — the same reason
     * {@code MiningManager#canDetonate} kept its held-item half via {@link #isHoldingItem}. That
     * keeps the whole gate MC-free and mockable. (A platform adapter calling an mcMMO util is the
     * established shape — see {@link #isHoldingTool} and {@link #isLookingAtTree}.)
     */
    public boolean isUnarmed() {
        return ItemUtils.isUnarmed(handle.getMainHandStack());
    }

    /**
     * Whether the player is currently looking at a tree block within reach (Bukkit
     * {@code BlockUtils.isPartOfTree(player.getTargetBlock(null, 100))}). Used by the shared-axe
     * "tool ready" messaging ({@code McMMOPlayer#processAxeToolMessages}) to decide whether a raised
     * axe is readying Tree Feller vs Skull Splitter. Ray-casts 100 blocks along the player's look
     * vector; a miss (air / fluid / entity) or a non-tree block yields {@code false}.
     */
    public boolean isLookingAtTree() {
        HitResult hit = handle.raycast(100.0D, 1.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        return BlockUtils.isPartOfTree(getWorld().getBlockState(pos));
    }

    // --- Super/Giga Breaker dig-speed boost (K3 enchant-write / K4) ----------

    /**
     * Config-string key under {@code minecraft:custom_data} that stashes the tool's pre-boost
     * Efficiency level, mirroring the legacy {@code NSK_SUPER_ABILITY_BOOSTED_ITEM} persistent-data key.
     * Its presence marks a stack as super-ability boosted; its value restores the original level when
     * the boost is removed.
     */
    private static final String SUPER_ABILITY_BOOST_KEY = "mcmmo:super_ability_boosted";

    /**
     * Apply the Super/Giga Breaker dig-speed boost to the main-hand tool (legacy
     * {@code SkillUtils.handleAbilitySpeedIncrease} enchant-buff path, the default with the bundled
     * {@code hidden.yml}). No-op unless the main hand is a pickaxe or shovel. Bumps the tool's
     * Efficiency by {@code enchantBuff} levels and stashes the pre-boost level in a
     * {@code custom_data} marker so {@link #removeSuperAbilityBoostFromMainHand()} /
     * {@link #removeSuperAbilityBoostsFromInventory()} can restore it exactly when the ability ends.
     *
     * @param enchantBuff the number of Efficiency levels to add (advanced.yml {@code EnchantBuff})
     */
    public void applySuperAbilityDigBoost(int enchantBuff) {
        final ItemStack stack = handle.getMainHandStack();
        if (!canBeDigBoosted(stack)) {
            return;
        }
        final RegistryEntry<Enchantment> efficiency = efficiencyEntry();
        final int originalDigSpeed = EnchantmentHelper.getLevel(efficiency, stack);
        EnchantmentHelper.apply(stack, builder -> builder.set(efficiency,
                originalDigSpeed + enchantBuff));
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack,
                nbt -> nbt.putInt(SUPER_ABILITY_BOOST_KEY, originalDigSpeed));
    }

    /**
     * Remove the dig-speed boost from the main-hand tool (legacy {@code SkillUtils.removeAbilityBuff}
     * on the held item). Called before (re)activating so a stale boost can't stack its Efficiency.
     */
    public void removeSuperAbilityBoostFromMainHand() {
        removeSuperAbilityBoostFromStack(handle.getMainHandStack());
    }

    /**
     * Remove the dig-speed boost from every stack in the player's inventory (legacy
     * {@code SkillUtils.removeAbilityBoostsFromInventory}). Run when Super/Giga Breaker ends so a
     * boosted tool that was moved out of the main hand is still cleaned up.
     */
    public void removeSuperAbilityBoostsFromInventory() {
        final PlayerInventory inventory = handle.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            removeSuperAbilityBoostFromStack(inventory.getStack(slot));
        }
    }

    /**
     * Undo the boost on one stack: only touches boosted pickaxes/shovels. Restores the stashed
     * original Efficiency level (or strips Efficiency entirely if the tool had none pre-boost) and
     * clears the marker. Mirrors legacy {@code ItemMetadataUtils.removeBonusDigSpeedOnSuperAbilityTool}.
     */
    private void removeSuperAbilityBoostFromStack(@NotNull ItemStack stack) {
        if (stack.isEmpty() || !canBeDigBoosted(stack)) {
            return;
        }
        final NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return;
        }
        final NbtCompound nbt = customData.copyNbt();
        if (!nbt.contains(SUPER_ABILITY_BOOST_KEY)) {
            return; // not a boosted stack.
        }
        final int originalDigSpeed = nbt.getInt(SUPER_ABILITY_BOOST_KEY, 0);
        final RegistryEntry<Enchantment> efficiency = efficiencyEntry();
        if (originalDigSpeed > 0) {
            EnchantmentHelper.apply(stack, builder -> builder.set(efficiency, originalDigSpeed));
        } else {
            EnchantmentHelper.apply(stack,
                    builder -> builder.remove(entry -> entry.matchesKey(Enchantments.EFFICIENCY)));
        }
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack,
                marker -> marker.remove(SUPER_ABILITY_BOOST_KEY));
    }

    /** Legacy {@code ItemUtils.canBeSuperAbilityDigBoosted}: only pickaxes and shovels dig-boost. */
    private static boolean canBeDigBoosted(@NotNull ItemStack stack) {
        return ItemUtils.isPickaxe(stack) || ItemUtils.isShovel(stack);
    }

    /** Resolve the {@code Efficiency} enchantment entry from the world's dynamic registry. */
    private @NotNull RegistryEntry<Enchantment> efficiencyEntry() {
        return getWorld().getRegistryManager()
                .getOrThrow(RegistryKeys.ENCHANTMENT)
                .getOrThrow(Enchantments.EFFICIENCY);
    }

    // --- Stopgap raw accessors (pending dedicated adapters) ------------------

    /** Stopgap: raw vanilla inventory until the ItemStack adapter lands. */
    public @NotNull PlayerInventory getInventory() {
        return handle.getInventory();
    }
}
