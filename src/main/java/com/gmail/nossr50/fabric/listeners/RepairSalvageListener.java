package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.Materials;
import com.gmail.nossr50.skills.repair.RepairManager;
import com.gmail.nossr50.skills.repair.repairables.Repairable;
import com.gmail.nossr50.skills.repair.repairables.RepairableManager;
import com.gmail.nossr50.skills.salvage.SalvageManager;
import com.gmail.nossr50.skills.salvage.salvageables.Salvageable;
import com.gmail.nossr50.skills.salvage.salvageables.SalvageableManager;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;
import com.gmail.nossr50.util.skills.SkillGating;
import com.gmail.nossr50.util.sounds.SoundManager;
import com.gmail.nossr50.util.sounds.SoundType;
import com.gmail.nossr50.util.text.StringUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The K7 anvil hook: performs mcMMO Repair and Salvage when a player right-clicks the configured
 * anvil block while holding a repairable/salvageable item (CONVERSION_TODO §B). Ports the XP +
 * repair-action slice of legacy {@code RepairManager#handleRepair} / {@code SalvageManager#handleSalvage}
 * plus the {@code PlayerInteractListener} anvil dispatch.
 *
 * <p>mcMMO's anvils are ordinary vanilla blocks (an <em>iron block</em> for Repair, a
 * <em>gold block</em> for Salvage, both configurable via {@code config.yml}), not the vanilla anvil
 * screen. A right-click with a damaged, repairable/salvageable item in the main hand triggers the
 * action; the click is consumed ({@link ActionResult#SUCCESS}) so the block is not (re)placed and no
 * other interaction runs. Repair earns Repair XP and restores durability; Salvage grants no XP (it
 * recovers crafting materials).
 *
 * <p><b>The click has to be claimed on both logical sides.</b> {@link UseBlockCallback} fires on the
 * client as well, and per its contract a client-side {@code PASS} lets the client fall through from
 * "use block" to "use item" — which, for the very items this listener exists to repair, means
 * vanilla <em>equips the armour</em> (or casts the rod, or raises the shield) straight out of the
 * hand the anvil click was meant to act on. The armour then sits in an armour slot, so the second
 * click of the confirmation gate has nothing to repair and the skill is unusable. Returning
 * {@code SUCCESS} from the client-side fire cancels that fall-through and still sends the
 * block-interaction packet the server side acts on. The client-side fire therefore stops at the
 * identity test ({@link #anvilKindAt} + {@link #isAnvilAction}) and never touches player state:
 * both sides gate on the same lookup, so they cannot disagree about whose click it was.
 *
 * <p>The pure math stays MC-free on {@link RepairManager}/{@link SalvageManager} (durability/yield
 * calculation, XP award, confirmation gate); this listener owns the MC-typed half: block/anvil
 * identity, the held {@link ItemStack} reads (durability, unbreakable, stack size), the
 * repair-material inventory scan/consumption, the Super Repair RNG roll, the durability write, the
 * salvaged-item consumption + result spawn, and sounds.
 *
 * <p>Both arcane sub-skills are wired here too: {@link #applyArcaneForging} (repairing may cost the
 * item enchantments) and {@link #buildArcaneSalvageBook} (salvaging may return an enchanted book).
 * Their per-enchantment decisions stay MC-free on the managers; this owns the enchantment-component
 * reads and writes.
 *
 * <p><b>Deferred</b> (breadcrumbs inline): the custom-model-data reject
 * ({@code CustomItemSupportConfig} unported), the repair/salvage-check events (K5, no singleplayer
 * listeners), and the block-place "you placed an anvil" notification (cosmetic, Pass 2).
 */
public final class RepairSalvageListener {

    private RepairSalvageListener() {
    }

    /** Register the anvil-use interaction hook. Called once at mod load from {@code McMMOMod}. */
    public static void register() {
        UseBlockCallback.EVENT.register(RepairSalvageListener::onUseBlock);
    }

    /** Which of mcMMO's two anvils a click landed on. */
    enum AnvilKind {
        REPAIR,
        SALVAGE
    }

    /**
     * Right-click a block → if it is an mcMMO anvil and the held item is one the matching skill works
     * on, claim the click and (server side) perform the action. Package-private so the test can drive
     * the real dispatch rather than the predicates alone.
     */
    static InteractionResult onUseBlock(Player player, Level world, InteractionHand hand,
            BlockHitResult hitResult) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS; // avoid the off-hand dispatch double-firing.
        }

        final BlockPos pos = hitResult.getBlockPos();
        final AnvilKind kind = anvilKindAt(world, pos);
        if (kind == null || !isAnvilAction(kind, player.getMainHandStack())) {
            return InteractionResult.PASS; // not an mcMMO anvil action — let vanilla have the click.
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            // Client-side fire: claim the click so the client does not fall through to "use item"
            // and equip/use the item out of our hand. No player state is touched here — the
            // confirmation clock and the action itself belong to the server-side fire below.
            return InteractionResult.SUCCESS;
        }

        return switch (kind) {
            case REPAIR -> handleRepairInteraction(serverPlayer);
            case SALVAGE -> handleSalvageInteraction(serverPlayer, world, pos);
        };
    }

    /**
     * "You have placed an anvil" — the one-shot hint fired when a player places one of mcMMO's two
     * anvil blocks. Ports legacy {@code BlockListener#onBlockPlace}'s Repair/Salvage arms
     * ({@code BlockListener:329-335}), and is called from {@code BlockPlaceMixin}.
     *
     * <p>⚠️ Both this and the two {@code placedAnvilCheck} bodies it calls were recorded as deferred
     * "cosmetic, Pass 2" work blocked on adapters that already existed. The visible symptom was four
     * live-looking {@code config.yml} switches — {@code Skills.{Repair,Salvage}.Anvil_Messages} and
     * {@code …Anvil_Placed_Sounds} — that no code read (2026-08-06 wiring audit, item 4(c)).
     *
     * <p>Unlike the click path there is no held-item test: legacy notifies on placement regardless of
     * what the player is carrying, because the whole point is to explain the block they just put down.
     */
    public static void onAnvilPlaced(@NotNull Level world, @NotNull BlockPos pos,
            @Nullable Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        final AnvilKind kind = anvilKindAt(world, pos);
        if (kind == null) {
            return;
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(serverPlayer.getUuid());
        if (mmoPlayer == null) {
            return;
        }
        switch (kind) {
            case REPAIR -> {
                if (SkillGating.isSkillEnabled(PrimarySkillType.REPAIR)) {
                    mmoPlayer.getRepairManager().placedAnvilCheck();
                }
            }
            case SALVAGE -> {
                if (SkillGating.isSkillEnabled(PrimarySkillType.SALVAGE)) {
                    mmoPlayer.getSalvageManager().placedAnvilCheck();
                }
            }
        }
    }

    /** The mcMMO anvil at {@code pos}, or {@code null} when that block is neither anvil. */
    private static @Nullable AnvilKind anvilKindAt(Level world, BlockPos pos) {
        final Block clicked = world.getBlockState(pos).getBlock();

        final Block repairAnvil = anvilBlock(
                McMMOMod.getGeneralConfig().getRepairAnvilMaterialName());
        if (repairAnvil != null && clicked == repairAnvil) {
            return AnvilKind.REPAIR;
        }

        final Block salvageAnvil = anvilBlock(
                McMMOMod.getGeneralConfig().getSalvageAnvilMaterialName());
        if (salvageAnvil != null && clicked == salvageAnvil) {
            return AnvilKind.SALVAGE;
        }

        return null;
    }

    /**
     * Whether mcMMO claims an anvil click made with {@code held} — that is, whether the matching
     * skill's config knows how to work on the item. This is the whole of the client-side decision and
     * the server side gates on the same lookup, so a click is either mcMMO's on both sides or on
     * neither. Anything else (durability, level, materials on hand) is a <em>failure of a claimed
     * action</em>, reported to the player by {@link #performRepair}/{@link #performSalvage}, not a
     * reason to hand the click back to vanilla.
     */
    private static boolean isAnvilAction(AnvilKind kind, ItemStack held) {
        return switch (kind) {
            case REPAIR -> repairableInHand(held) != null;
            case SALVAGE -> salvageableInHand(held) != null;
        };
    }

    /**
     * The {@code repair.yml} entry covering the held item, or {@code null} when Repair has nothing to
     * act on: an empty hand, an item no entry covers, or configs that never loaded (no world
     * session).
     */
    private static @Nullable Repairable repairableInHand(ItemStack held) {
        if (held.isEmpty()) {
            return null;
        }
        final RepairableManager manager = McMMOMod.getRepairableManager();
        return manager == null ? null : manager.getRepairable(itemPath(held));
    }

    /** The {@code salvage.yml} counterpart of {@link #repairableInHand}. */
    private static @Nullable Salvageable salvageableInHand(ItemStack held) {
        if (held.isEmpty()) {
            return null;
        }
        final SalvageableManager manager = McMMOMod.getSalvageableManager();
        return manager == null ? null : manager.getSalvageable(itemPath(held));
    }

    /** A stack's registry path ({@code minecraft:iron_sword} → {@code iron_sword}), the config key. */
    private static String itemPath(ItemStack stack) {
        return BuiltInRegistries.ITEM.getId(stack.getItem()).getPath();
    }

    /**
     * The repair-anvil right-click flow: resolve the player + held item, gate on the item being
     * repairable and the double-click confirmation, then perform the repair. Returns
     * {@link ActionResult#SUCCESS} once the click is claimed for repair (so vanilla does not also act
     * on it), {@link ActionResult#PASS} when the held item is not something mcMMO repairs.
     */
    private static InteractionResult handleRepairInteraction(ServerPlayer serverPlayer) {
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(serverPlayer.getUuid());
        if (mmoPlayer == null) {
            return InteractionResult.PASS;
        }

        // Re-resolved on this side rather than carried over from onUseBlock's gate: the client fires
        // first and the two sides hold different ItemStack instances, so the hand is read where it is
        // acted on.
        final ItemStack item = serverPlayer.getMainHandStack();
        final Repairable repairable = repairableInHand(item);
        if (repairable == null) {
            return InteractionResult.PASS; // the held item is not repairable — let vanilla have the click.
        }

        final RepairManager repairManager = mmoPlayer.getRepairManager();

        // Double-click confirmation: the first click within the window only arms + prompts.
        if (repairManager.checkConfirmation(true)) {
            performRepair(serverPlayer, mmoPlayer, repairManager, item, itemPath(item), repairable);
        }
        return InteractionResult.SUCCESS; // claim the click whether we repaired or merely armed.
    }

    /** Port of legacy {@code handleRepair}: the guards, material consumption, XP, and durability write. */
    private static void performRepair(ServerPlayer serverPlayer, McMMOPlayer mmoPlayer,
            RepairManager repairManager, ItemStack item, String itemPath, Repairable repairable) {
        // Unbreakable items cannot be repaired.
        if (item.contains(DataComponents.UNBREAKABLE)) {
            NotificationManager.sendPlayerInformation(mmoPlayer,
                    NotificationType.SUBSKILL_MESSAGE_FAILED, "Anvil.Unbreakable");
            return;
        }

        // Level requirement.
        final int minimumRepairableLevel = repairable.getMinimumLevel();
        if (repairManager.getSkillLevel() < minimumRepairableLevel) {
            NotificationManager.sendPlayerInformation(mmoPlayer,
                    NotificationType.SUBSKILL_MESSAGE_FAILED, "Repair.Skills.Adept",
                    String.valueOf(minimumRepairableLevel), StringUtils.getPrettyString(itemPath));
            return;
        }

        // Do not repair an item that is already at full durability.
        final short startDurability = (short) item.getDamage();
        if (startDurability <= 0) {
            NotificationManager.sendPlayerInformation(mmoPlayer,
                    NotificationType.SUBSKILL_MESSAGE_FAILED, "Repair.Skills.FullDurability");
            return;
        }

        // The repair material must exist as a vanilla item and be present in the inventory.
        final Optional<Item> repairItemOpt = Materials.item(repairable.getRepairMaterial());
        if (repairItemOpt.isEmpty()) {
            return; // misconfigured repairable — nothing to consume.
        }
        final Item repairItem = repairItemOpt.get();
        final Inventory inventory = serverPlayer.getInventory();
        int materialSlot = findMaterialSlot(inventory, repairItem, false);
        if (materialSlot < 0) {
            notifyMissingRepairMaterial(mmoPlayer, repairable);
            return;
        }

        // Do not repair stacked items.
        if (item.getCount() != 1) {
            NotificationManager.sendPlayerInformation(mmoPlayer,
                    NotificationType.SUBSKILL_MESSAGE_FAILED, "Repair.Skills.StackedItems");
            return;
        }

        // Clear a live Super/Giga Breaker dig-speed buff off the tool before repairing it (legacy
        // `SkillUtils.removeAbilityBuff(item)`). Without this the repair preserves the temporary
        // Efficiency levels as if they were the tool's own, making the buff permanent. Passed the
        // stack we already hold rather than re-reading the main hand, so the two can never diverge.
        mmoPlayer.getPlayer().removeSuperAbilityBoost(item);

        // Enchanted repair materials: a player may hold an enchanted copy of the repair material
        // (creative, /enchant, or a datapack-granted item). Unless advanced.yml allows consuming them,
        // fall back to the first *unenchanted* stack, and refuse the repair when only enchanted ones
        // are on hand — legacy reports the identical "you need more material" failure either way.
        if (!McMMOMod.getAdvancedConfig().getAllowEnchantedRepairMaterials()
                && isEnchanted(inventory.getStack(materialSlot))) {
            materialSlot = findMaterialSlot(inventory, repairItem, true);
            if (materialSlot < 0) {
                notifyMissingRepairMaterial(mmoPlayer, repairable);
                return;
            }
        }

        // Arcane Forging: the repair may cost the item some or all of its enchantments. Legacy gates
        // the whole check on May_Lose_Enchants (off ⇒ repairs never touch enchantments) and on the
        // repair enchant-bypass perk, both outside the per-enchantment roll.
        if (repairManager.isArcaneForgingEnchantLossEnabled()
                && !Permissions.hasRepairEnchantBypassPerk(mmoPlayer.getPlayer())) {
            applyArcaneForging(mmoPlayer, repairManager, item);
        }

        final int baseRepairAmount = repairable.getBaseRepairDurability();
        final boolean superRepair = rollSuperRepair(mmoPlayer, repairManager);
        final short newDurability =
                repairManager.repairCalculate(startDurability, baseRepairAmount, superRepair);

        // Consume one repair material.
        inventory.removeStack(materialSlot, 1);

        // Award Repair XP (MC-free formula on the manager).
        repairManager.awardRepairXp(startDurability, newDurability, repairable);

        // Anvil sounds.
        if (McMMOMod.getGeneralConfig().getRepairAnvilUseSoundsEnabled()) {
            SoundManager.sendSound(mmoPlayer.getPlayer(), SoundType.ANVIL);
            SoundManager.sendSound(mmoPlayer.getPlayer(), SoundType.ITEM_BREAK);
        }

        // Repair the item.
        item.setDamage(newDurability);
    }

    /**
     * Arcane Forging (legacy {@code RepairManager#addEnchants}): repairing an enchanted item rolls
     * each enchantment separately, and it may survive intact, drop a level, or be stripped. This is
     * the MC-typed half — read the stack's enchantments, apply the outcome
     * {@link RepairManager#resolveEnchantOutcome} picks, and report the overall result.
     *
     * <p>Note the harsh legacy rule preserved here: a player with <em>no</em> Arcane Forging rank
     * loses every enchantment on the item. Repairing enchanted gear below the first rank threshold
     * (Repair 100 in RetroMode) is a guaranteed total loss, not merely a poor keep-chance.
     *
     * <p>Both RNG draws are made eagerly, where legacy drew the downgrade roll only after the keep
     * roll had already succeeded. The draws are independent and the port's RNG is unseeded, so this
     * costs at most one extra draw per enchantment and cannot shift the outcome distribution.
     */
    private static void applyArcaneForging(McMMOPlayer mmoPlayer, RepairManager repairManager,
            ItemStack item) {
        final ItemEnchantments enchants = EnchantmentHelper.getEnchantments(item);
        if (enchants.isEmpty()) {
            return; // an unenchanted item has nothing to lose.
        }

        // Administrative bypass — never held in singleplayer, but kept for faithfulness.
        if (Permissions.arcaneBypass(mmoPlayer.getPlayer())) {
            NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_MESSAGE,
                    "Repair.Arcane.Perfect");
            return;
        }

        // No Arcane Forging rank ⇒ the arcane energies are lost entirely.
        if (!repairManager.canKeepEnchants()) {
            EnchantmentHelper.set(item, ItemEnchantments.DEFAULT);
            NotificationManager.sendPlayerInformation(mmoPlayer,
                    NotificationType.SUBSKILL_MESSAGE_FAILED, "Repair.Arcane.Lost");
            return;
        }

        final boolean allowUnsafe = allowUnsafeEnchantments();
        final int startingCount = enchants.getSize();
        final Map<Holder<Enchantment>, Integer> survivors = new LinkedHashMap<>();
        boolean downgraded = false;

        for (Holder<Enchantment> enchantment : enchants.getEnchantments()) {
            // Legacy clamps an over-levelled ("unsafe") enchantment down to the vanilla maximum
            // before rolling, so a repair also launders illegally-high levels unless allowed.
            int level = enchants.getLevel(enchantment);
            if (!allowUnsafe) {
                level = Math.min(level, enchantment.value().getMaxLevel());
            }

            final RepairManager.ArcaneOutcome outcome = repairManager.resolveEnchantOutcome(level,
                    ProbabilityUtil.isStaticSkillRNGSuccessful(PrimarySkillType.REPAIR, mmoPlayer,
                            repairManager.getKeepEnchantChance()),
                    ProbabilityUtil.isStaticSkillRNGSuccessful(PrimarySkillType.REPAIR, mmoPlayer,
                            100.0D - repairManager.getDowngradeEnchantChance()));

            switch (outcome) {
                case KEPT -> survivors.put(enchantment, level);
                case DOWNGRADED -> {
                    survivors.put(enchantment, level - 1);
                    downgraded = true;
                }
                case LOST -> { /* stripped: simply not carried over to the new component. */ }
            }
        }

        EnchantmentHelper.set(item, componentOf(survivors));

        if (survivors.isEmpty()) {
            NotificationManager.sendPlayerInformationChatOnly(mmoPlayer, "Repair.Arcane.Fail");
        } else if (downgraded || survivors.size() < startingCount) {
            NotificationManager.sendPlayerInformationChatOnly(mmoPlayer, "Repair.Arcane.Downgrade");
        } else {
            NotificationManager.sendPlayerInformationChatOnly(mmoPlayer, "Repair.Arcane.Perfect");
        }
    }

    /**
     * The salvage-anvil right-click flow: resolve the player + held item, gate on the item being
     * salvageable and the double-click confirmation, then perform the salvage.
     */
    private static InteractionResult handleSalvageInteraction(ServerPlayer serverPlayer,
            Level world, BlockPos pos) {
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(serverPlayer.getUuid());
        if (mmoPlayer == null) {
            return InteractionResult.PASS;
        }

        // Re-resolved on this side for the same reason as in handleRepairInteraction.
        final ItemStack item = serverPlayer.getMainHandStack();
        final Salvageable salvageable = salvageableInHand(item);
        if (salvageable == null) {
            return InteractionResult.PASS; // the held item is not salvageable.
        }

        final SalvageManager salvageManager = mmoPlayer.getSalvageManager();

        if (salvageManager.checkConfirmation(true)) {
            performSalvage(serverPlayer, mmoPlayer, salvageManager, item, itemPath(item), salvageable,
                    world, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Port of legacy {@code handleSalvage}: the guards, salvage-yield math, item consumption, and
     * result spawn. Salvage grants no XP (mcMMO's Salvage is a material-recovery skill); the reward is
     * the returned crafting materials. The yield math ({@link SalvageManager#calculateSalvageableAmount}
     * + {@link SalvageManager#getSalvageLimit}) is MC-free; this owns the item reads/spawn.
     */
    private static void performSalvage(ServerPlayer serverPlayer, McMMOPlayer mmoPlayer,
            SalvageManager salvageManager, ItemStack item, String itemPath, Salvageable salvageable,
            Level world, BlockPos pos) {
        // Unbreakable items cannot be salvaged.
        if (item.contains(DataComponents.UNBREAKABLE)) {
            NotificationManager.sendPlayerInformation(mmoPlayer,
                    NotificationType.SUBSKILL_MESSAGE_FAILED, "Anvil.Unbreakable");
            return;
        }

        // Level requirement.
        final int minimumSalvageableLevel = salvageable.getMinimumLevel();
        if (salvageManager.getSkillLevel() < minimumSalvageableLevel) {
            NotificationManager.sendPlayerInformation(mmoPlayer,
                    NotificationType.REQUIREMENTS_NOT_MET, "Salvage.Skills.Adept.Level",
                    String.valueOf(minimumSalvageableLevel), StringUtils.getPrettyString(itemPath));
            return;
        }

        // Yield scales with how damaged the item is, capped by Scrap Collector.
        int potentialSalvageYield = SalvageManager.calculateSalvageableAmount(item.getDamage(),
                salvageable.getMaximumDurability(), salvageable.getMaximumQuantity());
        if (potentialSalvageYield <= 0) {
            NotificationManager.sendPlayerInformation(mmoPlayer,
                    NotificationType.SUBSKILL_MESSAGE_FAILED, "Salvage.Skills.TooDamaged");
            return;
        }
        potentialSalvageYield = Math.min(potentialSalvageYield,
                SalvageManager.getSalvageLimit(mmoPlayer.getPlayer()));

        // The salvage material must resolve to a real vanilla item.
        final Optional<Item> salvageItemOpt = Materials.item(salvageable.getSalvageMaterial());
        if (salvageItemOpt.isEmpty()) {
            return; // misconfigured salvageable — nothing to yield.
        }

        // Arcane Salvage: an enchanted item may also yield a book holding what could be extracted.
        // Built before the item is consumed, since its enchantments are the source.
        final ItemStack enchantBook = buildArcaneSalvageBook(mmoPlayer, salvageManager, item);

        NotificationManager.sendPlayerInformationChatOnly(mmoPlayer, "Salvage.Skills.Lottery.Normal",
                String.valueOf(potentialSalvageYield), StringUtils.getPrettyString(itemPath));

        // Consume the salvaged item (salvage only ever operates on a single, non-stacking item).
        serverPlayer.setStackInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        // Pop the recovered materials out of the top of the anvil.
        if (enchantBook != null) {
            Block.dropStack(world, pos.up(), enchantBook);
        }
        Block.dropStack(world, pos.up(), new ItemStack(salvageItemOpt.get(), potentialSalvageYield));

        if (McMMOMod.getGeneralConfig().getSalvageAnvilUseSoundsEnabled()) {
            SoundManager.sendSound(mmoPlayer.getPlayer(), SoundType.ITEM_BREAK);
        }
        NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_MESSAGE,
                "Salvage.Skills.Success");
    }

    /**
     * Arcane Salvage (legacy {@code SalvageManager#arcaneSalvageCheck}): salvaging an enchanted item
     * may return an enchanted book carrying the enchantments that survived extraction, each at full
     * or one reduced level. This is the MC-typed half — read the stack's enchantments, apply the
     * outcome {@link SalvageManager#resolveEnchantOutcome} picks, and build the book.
     *
     * <p>Returns {@code null} when there is no book to give: an unenchanted item, a player without
     * the Arcane Salvage rank, or every enchantment failing its roll. As in {@code applyArcaneForging},
     * both RNG draws are made eagerly.
     *
     * @return the enchanted book to drop, or {@code null} if nothing was extracted
     */
    private static @Nullable ItemStack buildArcaneSalvageBook(McMMOPlayer mmoPlayer,
            SalvageManager salvageManager, ItemStack item) {
        final ItemEnchantments enchants = EnchantmentHelper.getEnchantments(item);
        if (enchants.isEmpty()) {
            return null; // an unenchanted item yields no book (legacy skips the check entirely).
        }

        if (!salvageManager.canArcaneSalvage()) {
            NotificationManager.sendPlayerInformationChatOnly(mmoPlayer,
                    "Salvage.Skills.ArcaneFailed");
            return null;
        }

        final boolean allowUnsafe = allowUnsafeEnchantments();
        final Map<Holder<Enchantment>, Integer> extracted = new LinkedHashMap<>();
        int arcaneFailureCount = 0;
        boolean downgraded = false;

        for (Holder<Enchantment> enchantment : enchants.getEnchantments()) {
            // Unlike Arcane Forging this clamp only bounds what the book receives — the source item
            // is being destroyed, so there is nothing to write the clamped level back to.
            int level = enchants.getLevel(enchantment);
            if (!allowUnsafe) {
                level = Math.min(level, enchantment.value().getMaxLevel());
            }

            final SalvageManager.ArcaneOutcome outcome = salvageManager.resolveEnchantOutcome(level,
                    ProbabilityUtil.isStaticSkillRNGSuccessful(PrimarySkillType.SALVAGE, mmoPlayer,
                            salvageManager.getExtractFullEnchantChance()),
                    ProbabilityUtil.isStaticSkillRNGSuccessful(PrimarySkillType.SALVAGE, mmoPlayer,
                            salvageManager.getExtractPartialEnchantChance()));

            switch (outcome) {
                case FULL -> extracted.put(enchantment, level);
                case PARTIAL -> {
                    extracted.put(enchantment, level - 1);
                    downgraded = true;
                }
                case FAILED -> arcaneFailureCount++;
            }
        }

        if (salvageManager.failedAllEnchants(arcaneFailureCount, enchants.getSize())) {
            NotificationManager.sendPlayerInformationChatOnly(mmoPlayer,
                    "Salvage.Skills.ArcaneFailed");
            return null;
        }
        if (downgraded) {
            NotificationManager.sendPlayerInformationChatOnly(mmoPlayer,
                    "Salvage.Skills.ArcanePartial");
        }

        // An enchanted book carries its enchantments as STORED_ENCHANTMENTS, not ENCHANTMENTS —
        // the legacy EnchantmentStorageMeta.addStoredEnchant(.., ignoreLevelRestriction=true) that
        // built this book maps onto the component builder, which applies no level restriction.
        // Set explicitly rather than via EnchantmentHelper.set: the two are equivalent here
        // (bytecode-verified — its private getEnchantmentsComponentType picks STORED_ENCHANTMENTS
        // exactly when the stack isOf(ENCHANTED_BOOK)), but naming the component documents the
        // book/tool distinction that applyArcaneForging relies on the helper to make for it.
        final ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.set(DataComponents.STORED_ENCHANTMENTS, componentOf(extracted));
        return book;
    }

    /**
     * Build an {@link ItemEnchantmentsComponent} from a resolved enchantment→level map. Used for
     * both the repaired item's surviving enchantments and the salvage book's extracted ones.
     */
    private static ItemEnchantments componentOf(
            Map<Holder<Enchantment>, Integer> levels) {
        final ItemEnchantments.Mutable builder =
                new ItemEnchantments.Mutable(ItemEnchantments.DEFAULT);
        levels.forEach(builder::set);
        return builder.build();
    }

    /**
     * experience.yml {@code ExploitFix.UnsafeEnchantments} — whether over-vanilla enchantment levels
     * survive a repair/salvage untouched. Defaults to {@code false} (clamp them) when the config has
     * not loaded, matching the shipped default.
     */
    private static boolean allowUnsafeEnchantments() {
        final ExperienceConfig experienceConfig = McMMOMod.getExperienceConfig();
        return experienceConfig != null && experienceConfig.allowUnsafeEnchantments();
    }

    /**
     * The Super Repair proc: gated on the sub-skill being unlocked + enabled, then a skill RNG roll.
     * Kept in the listener (not the manager) because the roll has no test seam per the port's RNG
     * convention; the deterministic doubling lives in {@link RepairManager#repairCalculate}. Notifies
     * the player on a success, mirroring legacy {@code checkPlayerProcRepair}.
     */
    private static boolean rollSuperRepair(McMMOPlayer mmoPlayer, RepairManager repairManager) {
        if (!RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.REPAIR_SUPER_REPAIR)
                || !Permissions.isSubSkillEnabled(mmoPlayer.getPlayer(),
                SubSkillType.REPAIR_SUPER_REPAIR)) {
            return false;
        }
        if (ProbabilityUtil.isSkillRNGSuccessful(SubSkillType.REPAIR_SUPER_REPAIR, mmoPlayer)) {
            NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_MESSAGE,
                    "Repair.Skills.FeltEasy");
            return true;
        }
        return false;
    }

    /** Resolve the anvil {@link Block} from its config material name, or {@code null} if invalid. */
    private static Block anvilBlock(String materialName) {
        return Materials.block(materialName).orElse(null);
    }

    /**
     * First inventory slot holding {@code material}, or {@code -1} if none. Scans every slot (matching
     * legacy {@code PlayerInventory#contains}); {@link PlayerInventory#removeStack(int, int)} then
     * consumes one from the returned slot.
     *
     * @param requireUnenchanted skip enchanted stacks, for the
     *     {@code Repair.AllowEnchantedRepairMaterials} avoidance pass (legacy's second, filtered scan)
     */
    private static int findMaterialSlot(Inventory inventory, Item material,
            boolean requireUnenchanted) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            final ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty() || !stack.isOf(material)) {
                continue;
            }
            if (requireUnenchanted && isEnchanted(stack)) {
                continue;
            }
            return slot;
        }
        return -1;
    }

    /**
     * Whether a candidate repair material carries any enchantment. Legacy read Bukkit's
     * {@code ItemStack#getEnchantments()} (item enchantments only); {@link EnchantmentHelper} also
     * treats an enchanted book's {@code STORED_ENCHANTMENTS} as enchanted, which is the intent of the
     * knob (do not consume an enchanted item as scrap) and only reachable via a custom repair.yml.
     */
    private static boolean isEnchanted(ItemStack stack) {
        return !EnchantmentHelper.getEnchantments(stack).isEmpty();
    }

    /**
     * The shared "you don't have the repair material" failure, sent both when the player holds none at
     * all and when {@code Repair.AllowEnchantedRepairMaterials} rejects every copy they do hold —
     * legacy sends the same {@code Skills.NeedMore.Extra} message on both paths.
     */
    private static void notifyMissingRepairMaterial(McMMOPlayer mmoPlayer, Repairable repairable) {
        final String prettyName = repairable.getRepairMaterialPrettyName() != null
                ? repairable.getRepairMaterialPrettyName()
                : StringUtils.getPrettyString(repairable.getRepairMaterial());
        NotificationManager.sendPlayerInformation(mmoPlayer,
                NotificationType.SUBSKILL_MESSAGE_FAILED, "Skills.NeedMore.Extra", prettyName, "");
    }
}
