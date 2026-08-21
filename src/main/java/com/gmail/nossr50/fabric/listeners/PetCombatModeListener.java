package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.subskills.taming.PetCombatMode;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.platform.Materials;
import com.gmail.nossr50.platform.text.TextUtils;
import com.gmail.nossr50.skills.taming.TamingManager;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.sounds.SoundManager;
import com.gmail.nossr50.util.sounds.SoundType;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The gesture that flips a player's {@link PetCombatMode}: sneak and right-click a pet you own while
 * holding the configured item (a bone by default).
 *
 * <h2>The gesture was free of mcMMO and not free of vanilla</h2>
 * A bone is already triple-booked by this mod, but never on this gesture — Call of the Wild is
 * sneak-<em>left</em>-click a block, Beast Lore is left-click a tameable, and Hunter's Quarry Sense is
 * sneak-left-click a creature. All three are left-click. What <em>does</em> own the right-click is
 * vanilla: a bone is not a wolf breeding item, so {@code WolfEntity#interactMob} falls through to the
 * sit branch. So the click has to be <b>claimed</b>, not merely observed, and the documented cost is
 * that while a bone is in your main hand a sneak-right-click changes the stance instead of sitting
 * the pet. A plain right-click, or any other item, still sits it.
 *
 * <h2>🔴 Claimed on BOTH logical sides</h2>
 * {@link UseEntityCallback} fires on the client too, and its own javadoc is the spec: it is hooked in
 * <em>before</em> the spectator check, ahead of {@code PlayerEntity#interact} → {@code Entity#interact}
 * → {@code interactMob}, and any return other than {@link ActionResult#PASS} cancels further
 * processing. That gives three behaviours, only one of which is correct:
 * <ul>
 *   <li><b>{@code PASS} on the client</b> — the client falls through to its own local prediction and
 *       the pet visibly sits, then un-sits when the server disagrees. The rubber-banding is the
 *       whole of the {@code RepairSalvageListener} bug one level up.</li>
 *   <li><b>{@code FAIL} on the client</b> — worse: it cancels the interaction packet, so the server
 *       never hears about the gesture at all and the toggle silently does nothing.</li>
 *   <li><b>{@code CONSUME} on both</b> — the client suppresses its prediction and still sends the
 *       packet; the server does the work. This is the one.</li>
 * </ul>
 *
 * <p>The rule that fix established applies verbatim here: <b>both sides gate on the same lookup</b>,
 * so a click is mcMMO's on both or on neither. {@link #isToggleGesture} is that lookup, it is
 * MC-typed but side-free, and it is the only thing the client-side fire runs. Every mutation — the
 * profile write, the message, the sound — is behind the {@code ServerPlayerEntity} check.
 * {@code TameableEntity#isOwner} is safe to ask on the client because the owner is a tracked, synced
 * field.
 *
 * <h2>An unresolved profile still consumes</h2>
 * If {@link UserManager} has no {@link McMMOPlayer} yet (a click during join, before the profile
 * loads), this still returns {@code CONSUME} and tells the player to try again. Handing the click
 * back mid-decision is precisely the fall-through that produced the repair-anvil bug: the client has
 * already been told the click was claimed, so a server-side {@code PASS} makes the two sides
 * disagree about whose click it was and sits the pet on a gesture that was supposed to toggle it.
 *
 * <p>The stance itself is player-wide (ruling R-2) — the clicked pet only proves intent and
 * ownership — which is why every string this sends is plural and player-scoped. See
 * {@link PetCombatMode}.
 */
public final class PetCombatModeListener {

    private PetCombatModeListener() {
    }

    /** Register the pet-interaction hook. Called once at mod load from {@code McMMOMod}. */
    public static void register() {
        UseEntityCallback.EVENT.register(PetCombatModeListener::onUseEntity);
    }

    /**
     * Right-click an entity → if this is the toggle gesture on a pet the player owns, claim the click
     * and (server side) flip the player's pet combat stance.
     *
     * <p>Package-private so the test can drive the real dispatch rather than the predicates alone. A
     * predicate-only test passes with the {@link #register} call deleted, which is the
     * {@code respawn-stale-handle} lesson.
     *
     * @param hitResult vanilla's precise hit position; unused here (the gesture is about the entity,
     *                  not where on it you clicked) and nullable in the callback's contract
     */
    static InteractionResult onUseEntity(Player player, Level world, InteractionHand hand, Entity entity,
            @Nullable EntityHitResult hitResult) {
        if (!isToggleGesture(player, hand, entity)) {
            return InteractionResult.PASS; // Not our gesture — let vanilla sit the pet as usual.
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            // Client-side fire: claim the click so the client does not predict the sit-toggle, and
            // touch no state. The server-side fire below owns the actual flip. See the class doc.
            return InteractionResult.CONSUME;
        }

        final McMMOPlayer mmoPlayer = UserManager.getPlayer(serverPlayer.getUuid());
        if (mmoPlayer == null) {
            // Profile not loaded (mid-join). Still CONSUME: the client has already suppressed its
            // prediction, so PASSing here would sit the pet on a gesture that was claimed.
            //
            // ⚠️ Sent straight to the entity, NOT through NotificationManager. Every one of its
            // methods takes a @Nullable McMMOPlayer and returns silently when it is null — which is
            // exactly the state we are in — so routing this through it would log the problem and
            // tell the player nothing, leaving the toggle looking simply broken.
            McMMOMod.LOGGER.debug("Pet combat-mode toggle by {} arrived before their profile loaded;"
                            + " consuming the click and asking them to retry.",
                    serverPlayer.getName().getString());
            serverPlayer.sendMessage(TextUtils.toText(LocaleLoader.getString("Profile.PendingLoad")));
            return InteractionResult.CONSUME;
        }

        final TamingManager taming = mmoPlayer.getTamingManager();
        if (taming == null) {
            // A resolved player with no Taming manager is a wiring bug, not a game state — but the
            // click is already claimed, so log it loudly and consume rather than desyncing the sit.
            McMMOMod.LOGGER.warn("Player {} has no TamingManager; pet combat-mode toggle ignored.",
                    serverPlayer.getName().getString());
            return InteractionResult.CONSUME;
        }

        announce(mmoPlayer, taming.togglePetCombatMode());
        SoundManager.sendSound(mmoPlayer.getPlayer(), SoundType.TOOL_READY);
        return InteractionResult.CONSUME;
    }

    /**
     * The identity test, run identically on both logical sides.
     *
     * <p>Deliberately asks nothing that only a server knows. Sneaking, the main-hand stack and a
     * tameable's owner are all synced to the client, so the two sides cannot disagree about whose
     * click this is — which is the entire safety property behind returning {@code CONSUME} on both.
     *
     * <p>Package-private for the test, which needs to assert the negative cases (no bone, not
     * sneaking, someone else's pet, an untamed mob) without constructing a server.
     */
    static boolean isToggleGesture(@NotNull Player player, @NotNull InteractionHand hand,
            @NotNull Entity entity) {
        if (hand != InteractionHand.MAIN_HAND) {
            return false; // Avoid the off-hand dispatch double-firing the toggle.
        }
        if (!isFeatureEnabled()) {
            return false;
        }
        if (!player.isSneaking()) {
            return false; // A plain right-click still belongs to vanilla's sit-toggle.
        }
        if (!(entity instanceof TamableAnimal pet) || !pet.isTamed() || !pet.isOwner(player)) {
            return false; // Someone else's pet, or not a pet at all.
        }
        final Item toggleItem = toggleItem();
        return toggleItem != null && player.getMainHandStack().isOf(toggleItem);
    }

    /**
     * Tells the player which stance their <em>pets</em> — plural, player-wide — are now in, with the
     * one-line explanation of what that means.
     *
     * <p>⚠️ The wording is the feature's most likely bug report. The gesture is aimed at one animal
     * and the stance is player-wide (R-2), so a message saying "this wolf" would be actively wrong
     * for every other pet the player owns. {@code PetCombatModeLocaleTest} pins it.
     */
    private static void announce(@NotNull McMMOPlayer mmoPlayer, @NotNull PetCombatMode mode) {
        NotificationManager.sendPlayerInformationChatOnly(mmoPlayer, "Taming.PetMode.Toggled",
                LocaleLoader.getString(mode.localeKey()));
        NotificationManager.sendPlayerInformationChatOnly(mmoPlayer, mode.localeKey() + ".Detail");
    }

    /**
     * Whether the feature is on. Null-guarded like {@code HerdsmansCallListener#triggerItem}: the
     * config is absent in unit tests that exercise the dispatch without booting the mod, and a
     * feature defaulting to on there is what lets those tests drive the real callback.
     */
    private static boolean isFeatureEnabled() {
        return McMMOMod.getGeneralConfig() == null
                || McMMOMod.getGeneralConfig().isPetCombatModeEnabled();
    }

    /**
     * The configured toggle item, or {@code null} when the name does not resolve to a real item.
     *
     * <p>Resolving to null rather than throwing means a typo'd config makes the gesture inert — the
     * pet sits as vanilla intends — instead of taking down every entity interaction in the game.
     */
    private static @Nullable Item toggleItem() {
        final String name = McMMOMod.getGeneralConfig() == null
                ? "BONE"
                : McMMOMod.getGeneralConfig().getPetCombatModeToggleItem();
        return Materials.item(name).orElse(null);
    }
}
