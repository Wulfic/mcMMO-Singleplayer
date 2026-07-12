package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.Materials;
import com.gmail.nossr50.skills.repair.RepairManager;
import com.gmail.nossr50.skills.repair.repairables.Repairable;
import com.gmail.nossr50.skills.repair.repairables.RepairableManager;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;
import com.gmail.nossr50.util.sounds.SoundManager;
import com.gmail.nossr50.util.sounds.SoundType;
import com.gmail.nossr50.util.text.StringUtils;
import java.util.Optional;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;

/**
 * The K7 anvil hook: performs mcMMO Repair (and, in a later slice, Salvage) when a player
 * right-clicks the configured anvil block while holding a repairable/salvageable item
 * (CONVERSION_TODO §B). Ports the XP + repair-action slice of legacy
 * {@code RepairManager#handleRepair} plus the {@code PlayerInteractListener} anvil dispatch.
 *
 * <p>mcMMO's anvils are ordinary vanilla blocks (an <em>iron block</em> for Repair, a
 * <em>gold block</em> for Salvage, both configurable via {@code config.yml}), not the vanilla anvil
 * screen. A right-click with a damaged, repairable item in the main hand triggers a repair; the
 * click is consumed ({@link ActionResult#SUCCESS}) so the block is not (re)placed and no other
 * interaction runs.
 *
 * <p>The pure math stays MC-free on {@link RepairManager} (durability calculation, XP award,
 * confirmation gate); this listener owns the MC-typed half: block/anvil identity, the held
 * {@link ItemStack} reads (durability, unbreakable, stack size), the repair-material inventory scan
 * and consumption, the Super Repair RNG roll, and the final durability write + sounds.
 *
 * <p><b>Deferred</b> (breadcrumbs inline): the Salvage anvil path (next slice), Arcane Forging
 * enchant loss/downgrade (K3 enchant-transfer surface), the custom-model-data reject
 * ({@code CustomItemSupportConfig} unported), the enchanted-repair-material avoidance branch, the
 * repair-check event (K5, no singleplayer listeners), and the block-place "you placed an anvil"
 * notification (cosmetic, Pass 2).
 */
public final class RepairSalvageListener {

    private RepairSalvageListener() {
    }

    /** Register the anvil-use interaction hook. Called once at mod load from {@code McMMOMod}. */
    public static void register() {
        UseBlockCallback.EVENT.register(RepairSalvageListener::onUseBlock);
    }

    /** Right-click a block → if it is the repair anvil and the held item is repairable, repair it. */
    private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand,
            BlockHitResult hitResult) {
        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS; // avoid the off-hand dispatch double-firing.
        }
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.PASS; // client-side callback fire.
        }

        final Block clicked = world.getBlockState(hitResult.getBlockPos()).getBlock();

        final Block repairAnvil = anvilBlock(
                McMMOMod.getGeneralConfig().getRepairAnvilMaterialName());
        if (repairAnvil != null && clicked == repairAnvil) {
            return handleRepairInteraction(serverPlayer);
        }

        // PORT (next slice): the Salvage anvil (getSalvageAnvilMaterialName(), default GOLD_BLOCK) →
        // SalvageManager salvage action + material spawn.
        return ActionResult.PASS;
    }

    /**
     * The repair-anvil right-click flow: resolve the player + held item, gate on the item being
     * repairable and the double-click confirmation, then perform the repair. Returns
     * {@link ActionResult#SUCCESS} once the click is claimed for repair (so vanilla does not also act
     * on it), {@link ActionResult#PASS} when the held item is not something mcMMO repairs.
     */
    private static ActionResult handleRepairInteraction(ServerPlayerEntity serverPlayer) {
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(serverPlayer.getUuid());
        if (mmoPlayer == null) {
            return ActionResult.PASS;
        }

        final ItemStack item = serverPlayer.getMainHandStack();
        if (item.isEmpty()) {
            return ActionResult.PASS;
        }

        final RepairableManager repairableManager = McMMOMod.getRepairableManager();
        if (repairableManager == null) {
            return ActionResult.PASS; // configs not loaded (no world session).
        }

        final String itemPath = Registries.ITEM.getId(item.getItem()).getPath();
        final Repairable repairable = repairableManager.getRepairable(itemPath);
        if (repairable == null) {
            return ActionResult.PASS; // the held item is not repairable — let vanilla have the click.
        }

        final RepairManager repairManager = mmoPlayer.getRepairManager();

        // Double-click confirmation: the first click within the window only arms + prompts.
        if (repairManager.checkConfirmation(true)) {
            performRepair(serverPlayer, mmoPlayer, repairManager, item, itemPath, repairable);
        }
        return ActionResult.SUCCESS; // claim the click whether we repaired or merely armed.
    }

    /** Port of legacy {@code handleRepair}: the guards, material consumption, XP, and durability write. */
    private static void performRepair(ServerPlayerEntity serverPlayer, McMMOPlayer mmoPlayer,
            RepairManager repairManager, ItemStack item, String itemPath, Repairable repairable) {
        // Unbreakable items cannot be repaired.
        if (item.contains(DataComponentTypes.UNBREAKABLE)) {
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
        final int materialSlot = findMaterialSlot(serverPlayer.getInventory(), repairItem);
        if (materialSlot < 0) {
            final String prettyName = repairable.getRepairMaterialPrettyName() != null
                    ? repairable.getRepairMaterialPrettyName()
                    : StringUtils.getPrettyString(repairable.getRepairMaterial());
            NotificationManager.sendPlayerInformation(mmoPlayer,
                    NotificationType.SUBSKILL_MESSAGE_FAILED, "Skills.NeedMore.Extra", prettyName, "");
            return;
        }

        // Do not repair stacked items.
        if (item.getCount() != 1) {
            NotificationManager.sendPlayerInformation(mmoPlayer,
                    NotificationType.SUBSKILL_MESSAGE_FAILED, "Repair.Skills.StackedItems");
            return;
        }

        // PORT: SkillUtils.removeAbilityBuff(item) — clear a Super/Giga Breaker Efficiency buff before
        // repairing a boosted tool. Deferred; only matters when repairing mid-super-ability.
        // PORT: the enchanted-repair-material avoidance branch (getAllowEnchantedRepairMaterials) and
        // Arcane Forging enchant loss/downgrade (addEnchants) — both need the enchant-transfer surface.

        final int baseRepairAmount = repairable.getBaseRepairDurability();
        final boolean superRepair = rollSuperRepair(mmoPlayer, repairManager);
        final short newDurability =
                repairManager.repairCalculate(startDurability, baseRepairAmount, superRepair);

        // Consume one repair material.
        serverPlayer.getInventory().removeStack(materialSlot, 1);

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
     */
    private static int findMaterialSlot(PlayerInventory inventory, Item material) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            final ItemStack stack = inventory.getStack(slot);
            if (!stack.isEmpty() && stack.isOf(material)) {
                return slot;
            }
        }
        return -1;
    }
}
