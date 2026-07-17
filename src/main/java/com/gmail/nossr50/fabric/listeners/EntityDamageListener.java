package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.subskills.acrobatics.DodgeResult;
import com.gmail.nossr50.datatypes.skills.subskills.acrobatics.RollResult;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.platform.MetadataStore;
import com.gmail.nossr50.platform.PlatformLivingEntity;
import com.gmail.nossr50.skills.MeleeDamageBonus;
import com.gmail.nossr50.skills.MeleeDamageBonus.MeleeWeapon;
import com.gmail.nossr50.skills.acrobatics.AcrobaticsManager;
import com.gmail.nossr50.skills.archery.Archery;
import com.gmail.nossr50.skills.archery.ArcheryManager;
import com.gmail.nossr50.skills.axes.AxesManager;
import com.gmail.nossr50.skills.crossbows.CrossbowsManager;
import com.gmail.nossr50.skills.maces.MacesManager;
import com.gmail.nossr50.skills.swords.SwordsManager;
import com.gmail.nossr50.skills.taming.TamingManager;
import com.gmail.nossr50.skills.tridents.TridentsManager;
import com.gmail.nossr50.skills.unarmed.UnarmedManager;
import com.gmail.nossr50.util.ItemUtils;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.skills.CombatUtils;
import com.gmail.nossr50.util.sounds.SoundManager;
import com.gmail.nossr50.util.sounds.SoundType;
import com.gmail.nossr50.util.text.TextUtils;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.LlamaEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;

/**
 * The K1/K2 damage hook: mcMMO's window into the vanilla damage pipeline. Driven by a mixin on
 * {@link LivingEntity#modifyAppliedDamage(DamageSource, float)} (see
 * {@code fabric.mixin.LivingEntityDamageMixin}) rather than a Fabric event, because mcMMO needs to
 * <em>modify</em> the applied damage (Acrobatics Roll reduces fall damage) and Fabric's
 * {@code ServerLivingEntityEvents.ALLOW_DAMAGE} is a cancel-only veto.
 *
 * <p>Currently wired: <b>K2 — fall damage → Acrobatics Roll</b>, the defender half of <b>K1 — combat
 * damage → Acrobatics Dodge</b> (attacker resolved via {@link DamageSource#getAttacker()}), and the
 * attacker half of <b>K1 — melee weapon on-hit damage bonuses</b> (Swords Stab / Axe Mastery /
 * Unarmed Steel Arm + Berserk / Maces Crush, composed MC-free in {@link MeleeDamageBonus}), and the
 * on-hit <em>effect</em> sub-skills: <b>Swords Rupture</b> (bleed DoT — see {@link #maybeProcessRupture})
 * and the two combat super abilities, <b>Serrated Strikes</b> and <b>Skull Splitter</b> (AoE — see
 * {@link #maybeProcessSerratedStrikes} / {@link #maybeProcessSkullSplitter}), and — on the defender
 * side again — <b>Swords Counter Attack</b> (see {@link #maybeProcessCounterAttack}) — and, after a
 * mace hit the target survives, <b>Maces Cripple</b> (Slowness — see {@link #maybeProcessCripple}). The Axes
 * target-inspecting sub-skills (<b>Armor Impact</b> / <b>Greater Impact</b> / <b>Critical
 * Strikes</b>) ride the attacker branch inside {@link MeleeDamageBonus}, since they feed the same
 * damage total. <b>Taming</b>'s damage modifiers ride both branches: a tamed wolf's bite carries its
 * owner's Gore / Sharpened Claws / Fast Food Service (see {@link #applyWolfAttackBonus}), and a hit
 * on that wolf is softened by Thick Fur / Shock Proof / Holy Hound (see {@link #handleWolfDamage}).
 * The <b>projectile</b> weapon skills ride the attacker branch too, keyed on the damaging projectile
 * rather than the player: a bow arrow's <b>Skill Shot</b>, a crossbow bolt's <b>Powered Shot</b> and a
 * thrown trident's <b>Impale</b> (see {@link #applyProjectileAttackBonus}), plus Archery's
 * <b>Arrow Retrieval</b> credit (see {@link #applyArcheryBonus}; the launch mark and the death drop
 * live on {@link ProjectileListener}).
 *
 * <p>Every attacker arm also pays that skill's <b>per-hit combat XP</b> as its closing act, exactly
 * where legacy's {@code processXCombat} methods did (see
 * {@link CombatUtils#processCombatXP}). Damage mcMMO deals itself never reaches these arms — the
 * {@code isProcessingMcMMODamage} guard below turns it away — so a Serrated Strikes AoE or a Rupture
 * tick pays no XP, matching legacy's custom-damage marker.
 *
 * <p>Some branches do <em>not</em> ride the mixin — Unarmed's <b>Arrow Deflect</b>, Taming's
 * <b>Beast Lore</b> and Environmentally Aware's FALL arm (dispatched from {@link
 * #onAllowDamage}) — because they cancel the hit outright, so they ride Fabric's cancel-only
 * {@code ServerLivingEntityEvents.ALLOW_DAMAGE} veto — hence this class has a {@link #register()}
 * as well as a mixin entry point.
 */
public final class EntityDamageListener {

    /**
     * Legacy's {@code processCombatXP(mmoPlayer, target, TAMING, 3)}: a wolf's bite trains its owner's
     * Taming at triple rate — the whole point being that you are not swinging the weapon yourself.
     */
    private static final double WOLF_ASSIST_XP_MULTIPLIER = 3.0;

    private EntityDamageListener() {
    }

    /**
     * Subscribe the branches of this listener that need to <em>veto</em> a hit outright rather than
     * reduce it — Unarmed's Arrow Deflect, Taming's Beast Lore and Environmentally Aware's FALL arm
     * (see {@link #onAllowDamage}). Everything else here is driven by the {@code modifyAppliedDamage}
     * mixin, which cannot cancel.
     */
    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(EntityDamageListener::onAllowDamage);
    }

    /**
     * Fabric's cancel-only {@code ALLOW_DAMAGE} veto: the dispatcher for every mcMMO damage branch
     * that must abort a hit outright rather than merely reduce it (the {@code modifyAppliedDamage}
     * mixin can only reduce). Legacy expressed all of these as {@code event.setCancelled(true)}, and
     * like Bukkit's cancel this fires before knockback, i-frames and the hurt sound — returning
     * {@code 0} from the mixin would zero the damage but still knock back, burn the i-frame window and
     * consume the arrow, so the veto is the faithful seam, not a workaround.
     *
     * <p>Branches, in dispatch order: Unarmed's <b>Arrow Deflect</b> (a bare-handed player swats an
     * arrow; see {@link #isArrowDeflected}), Taming's <b>Beast Lore</b> (a player bone-whacks a
     * tameable animal to inspect it; see {@link #maybeBeastLore}) and Taming's <b>Environmentally
     * Aware</b> FALL arm (a tamed wolf's fall damage is negated; see {@link #isEnvironmentallyAwareFall}).
     * Environmentally Aware's other environmental causes only teleport the wolf and leave the hit
     * intact, so they ride the reduce-only mixin instead (see {@link #handleWolfDamage}).
     *
     * @return {@code false} to cancel the hit, {@code true} to let it proceed
     */
    private static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (entity instanceof ServerPlayerEntity serverPlayer) {
            return !isArrowDeflected(serverPlayer, source);
        }
        if (maybeBeastLore(entity, source)) {
            return false; // inspected with a bone — the blow is cancelled.
        }
        if (entity instanceof WolfEntity wolf && isEnvironmentallyAwareFall(wolf, source)) {
            return false;
        }
        return true;
    }

    /**
     * Unarmed Arrow Deflect: a bare-handed player may swat an incoming arrow out of the air. Ports
     * legacy {@code EntityListener#onEntityDamageByEntity}'s deflect arm plus
     * {@code UnarmedManager#deflectCheck}. It lands earlier than Dodge, matching legacy: the deflect
     * arm sits ahead of {@code processCombatAttack}, so a deflected arrow is never also dodged.
     *
     * @return {@code true} if the arrow was deflected (the caller should cancel the hit)
     */
    private static boolean isArrowDeflected(ServerPlayerEntity serverPlayer, DamageSource source) {
        // Legacy checks the *direct* damager (`event.getDamager()`) for `instanceof Arrow`, which in
        // Bukkit is specifically a regular/tipped arrow — its sibling types (SpectralArrow, Trident)
        // implement AbstractArrow, not Arrow, so they were never deflectable. ArrowEntity draws that
        // same line here: its siblings extend PersistentProjectileEntity alongside it, not from it.
        if (!(source.getSource() instanceof ArrowEntity)) {
            return false;
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(serverPlayer.getUuid());
        if (mmoPlayer == null) {
            return false; // data not loaded (e.g. mid-join).
        }
        final UnarmedManager unarmed = mmoPlayer.getUnarmedManager();
        if (unarmed == null || !unarmed.canDeflect() || !unarmed.rollArrowDeflect()) {
            return false;
        }
        NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_MESSAGE,
                "Combat.ArrowDeflect");
        return true;
    }

    /**
     * Taming Environmentally Aware, FALL arm: a tamed wolf whose owner has the sub-skill takes no fall
     * damage at all (legacy's {@code case FALL: event.setCancelled(true)}). The wolf's other
     * environmental causes teleport it clear via {@link #handleWolfDamage} instead; only FALL cancels.
     *
     * @return {@code true} if the fall damage should be negated (the caller should cancel the hit)
     */
    private static boolean isEnvironmentallyAwareFall(WolfEntity wolf, DamageSource source) {
        if (!source.isIn(DamageTypeTags.IS_FALL)) {
            return false;
        }
        if (!(wolf.getOwner() instanceof ServerPlayerEntity owner)) {
            return false; // wild wolf (getOwner() is null unless tamed).
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(owner.getUuid());
        if (mmoPlayer == null) {
            return false;
        }
        final TamingManager taming = mmoPlayer.getTamingManager();
        return taming != null && taming.canUseEnvironmentallyAware();
    }

    /**
     * Taming Beast Lore: a player who left-clicks a tameable animal while holding a bone inspects it
     * instead of hitting it. Ports the {@code target instanceof Tameable} + {@code heldItem == BONE}
     * arm of legacy {@code CombatUtils#processCombatAttack}, which prints the beast's stats and
     * {@code event.setCancelled(true)}s the blow. Only a <em>direct</em> melee swing counts (legacy's
     * {@code entityType == EntityType.PLAYER}, i.e. the player is the direct damager), so a bone can't
     * inspect via a projectile.
     *
     * @return {@code true} if the animal was inspected (the caller should cancel the hit)
     */
    private static boolean maybeBeastLore(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof Tameable)) {
            return false; // Beast Lore only inspects tameable animals.
        }
        if (!(source.getAttacker() instanceof ServerPlayerEntity attacker)
                || source.getSource() != attacker) {
            return false; // not a direct melee swing by a player.
        }
        if (!attacker.getMainHandStack().isOf(Items.BONE)) {
            return false;
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(attacker.getUuid());
        if (mmoPlayer == null) {
            return false;
        }
        final TamingManager taming = mmoPlayer.getTamingManager();
        if (taming == null || !taming.canUseBeastLore()) {
            return false;
        }
        sendBeastLore(attacker, entity);
        return true;
    }

    /**
     * Builds and sends the Beast Lore stat readout, porting legacy {@code TamingManager#beastLore}.
     * MC-typed display glue: it reads the target's live health, tamed owner and (for the horse family)
     * movement-speed / jump-strength attributes, and hands the jump attribute to the already-extracted
     * pure conversion {@link TamingManager#beastLoreHorseJumpStrength}. The message is assembled as a
     * legacy {@code §}-coded string exactly as upstream did, then parsed once into {@link Text}.
     *
     * <p>{@link Tameable#getOwner()} returns {@code null} unless the animal is tamed and its owner is
     * resolvable, so it stands in for legacy's {@code isTamed() && getOwner() != null}. Llamas are
     * excluded from the horse block just as legacy excluded them (they carry no rideable jump/speed
     * stats worth showing).
     */
    private static void sendBeastLore(ServerPlayerEntity viewer, LivingEntity target) {
        final Tameable beast = (Tameable) target;
        String message = LocaleLoader.getString("Combat.BeastLore") + " ";

        final LivingEntity owner = beast.getOwner();
        if (owner != null) {
            message += LocaleLoader.getString("Combat.BeastLoreOwner", owner.getName().getString())
                    + " ";
        }

        message += LocaleLoader.getString("Combat.BeastLoreHealth", target.getHealth(),
                target.getMaxHealth());

        // Mules & donkeys share the horse's jump/speed stats; llamas do not.
        if (target instanceof AbstractHorseEntity horse && !(target instanceof LlamaEntity)
                && horse.getAttributeInstance(EntityAttributes.JUMP_STRENGTH) != null) {
            final double jumpStrength = TamingManager.beastLoreHorseJumpStrength(
                    horse.getAttributeValue(EntityAttributes.JUMP_STRENGTH));
            final double speed = horse.getAttributeValue(EntityAttributes.MOVEMENT_SPEED) * 43;
            message += "\n" + LocaleLoader.getString("Combat.BeastLoreHorseSpeed", speed)
                    + "\n" + LocaleLoader.getString("Combat.BeastLoreHorseJumpStrength", jumpStrength);
        }

        viewer.sendMessage(TextUtils.toText(message));
    }

    /**
     * Invoked from the {@code modifyAppliedDamage} mixin for every living-entity hit. Returns the
     * (possibly reduced) damage to apply. Only server players landing fall damage are affected today;
     * everything else passes through untouched.
     *
     * @param entity the entity taking damage
     * @param source the damage source
     * @param amount the vanilla post-armor/enchantment damage that would be applied
     * @return the damage mcMMO wants applied instead (equal to {@code amount} when it does not act)
     */
    public static float onModifyAppliedDamage(LivingEntity entity, DamageSource source, float amount) {
        if (amount <= 0) {
            return amount;
        }
        // Damage mcMMO is dealing itself (a Serrated Strikes / Skull Splitter AoE) must not be fed
        // back through mcMMO's own on-hit processing: the AoE attributes its damage to the player,
        // so without this it would read as a fresh swing and re-fire the very ability that is
        // dealing it. Legacy guards its damage handlers the same way, via a custom-damage marker on
        // the target (see CombatUtils#isProcessingMcMMODamage for why a ThreadLocal replaces it).
        if (CombatUtils.isProcessingMcMMODamage()) {
            return amount;
        }

        float result = amount;

        // K1 attacker branch: a player landing a *melee* hit adds their weapon skill's on-hit damage
        // bonus. Runs first so a PvP defender's Dodge (below) reduces the already-boosted damage.
        result = applyAttackerWeaponBonus(entity, source, result);
        // ...and the other half of legacy's attacker dispatch: the damager is the player's *wolf*,
        // which adds the owner's Taming bonuses. Legacy branches on the damager's type in one
        // if/else-if chain, so at most one of these two can ever fire.
        result = applyWolfAttackBonus(entity, source, result);
        // ...and the projectile arm of that same dispatch: the damager is the player's arrow,
        // crossbow bolt or thrown trident (Archery Skill Shot / Crossbows Powered Shot / Trident
        // Impale). Mutually exclusive with the two branches above — a hit's direct source is exactly
        // one entity type — so at most one of the three fires.
        result = applyProjectileAttackBonus(entity, source, result);

        // K1 defender / K2 branch: the entity *taking* damage is a player — fall damage feeds
        // Acrobatics Roll, an incoming entity hit feeds Acrobatics Dodge.
        if (entity instanceof ServerPlayerEntity serverPlayer) {
            if (source.isIn(DamageTypeTags.IS_FALL)) {
                result = handleFallDamage(serverPlayer, result);
            } else if (canReduceOwnBlast(serverPlayer, source)) {
                // Blast Mining self-damage. Legacy returns out of its combat handler once
                // Demolitions Expertise has taken the hit, so this must pre-empt Dodge below —
                // a player is not "dodging" their own charge.
                result = handleOwnBlastDamage(serverPlayer, result);
            } else {
                final Entity attacker = source.getAttacker();
                if (attacker != null) {
                    result = handleDodge(serverPlayer, attacker, result);
                }
                // Counter Attack reflects damage but does not change what the player takes, so it
                // runs last and returns nothing. Legacy's ordering, preserved: it reads the damage
                // back *after* Dodge has written to it, so a dodged hit counters for less.
                maybeProcessCounterAttack(serverPlayer, source, result);
            }
        } else if (entity instanceof WolfEntity wolf) {
            // Legacy's sibling `else if (livingEntity instanceof Tameable pet)` arm: the player's own
            // wolf is taking damage, and Taming may soften or undo it.
            result = handleWolfDamage(wolf, source, result);
        }
        return result;
    }

    /**
     * K1 attacker branch, Taming half: a tamed wolf's bite carries its owner's Taming bonuses. Ports
     * legacy {@code CombatUtils#processTamingCombat} (reached from the {@code entityType ==
     * EntityType.WOLF} arm of {@code processCombatAttack}).
     *
     * <p>Order is legacy's: Fast Food Service heals the wolf for the <em>unboosted</em> damage it just
     * dealt, then Sharpened Claws adds its flat bonus, then Gore multiplies the <em>initial</em>
     * damage and contributes only the difference. Gore reading {@code amount} rather than the running
     * total is why the two are additive rather than compounding.
     *
     * <p>{@code getOwner()} is used deliberately here, unlike in {@link
     * CombatUtils#canCombatSkillsTrigger} where it is avoided: that method only needs to know
     * <em>whether</em> an animal is tamed (so an unloaded owner must not read as "wild"), whereas this
     * one needs the owner themselves and has nothing to do if they are not present — exactly what
     * legacy's {@code wolf.getOwner() instanceof Player} did.
     *
     * <p>Dropped from the legacy body:
     * <ul>
     *   <li>{@code master.isOnline() && master.isValid()} — the {@link UserManager} lookup below is
     *       the singleplayer equivalent (no profile loaded, nothing to do);</li>
     *   <li>{@code Misc.isNPCEntityExcludingVillagers(master)} and
     *       {@code doesPlayerHaveSkillPermission} — the NPC helpers were not ported (Phase 9) and the
     *       skill-permission check was dropped at Phase 6/10 (see the breadcrumb in {@link
     *       com.gmail.nossr50.util.skills.SkillTools}), as on every other attacker arm here.</li>
     * </ul>
     *
     * <p>Pummel rides here too (see {@link TamingManager#processPummel}): it flings the target along
     * the wolf's look direction on a successful roll but does not feed the damage total, so it runs as
     * a side effect rather than contributing to {@code boostedDamage}.
     *
     * <p>The arm closes with legacy's {@code processCombatXP(mmoPlayer, target, TAMING, 3)} — the
     * wolf-assisted Taming XP that the port's old per-kill model could not express at all.
     */
    private static float applyWolfAttackBonus(LivingEntity target, DamageSource source,
            float amount) {
        // Legacy keys off painSource (the *direct* damager), so a wolf's own bite — not, say, an
        // arrow that happens to have a wolf as its owner — is what counts.
        if (!(source.getSource() instanceof WolfEntity wolf)) {
            return amount;
        }
        if (!(wolf.getOwner() instanceof ServerPlayerEntity master)) {
            return amount; // wild wolf, or one whose owner is not this player.
        }
        if (!CombatUtils.canCombatSkillsTrigger(PrimarySkillType.TAMING, target)) {
            return amount;
        }

        final McMMOPlayer mmoPlayer = UserManager.getPlayer(master.getUuid());
        if (mmoPlayer == null) {
            return amount; // data not loaded (e.g. mid-join).
        }
        final TamingManager taming = mmoPlayer.getTamingManager();
        if (taming == null) {
            return amount;
        }

        final PlatformLivingEntity platformWolf = new PlatformLivingEntity(wolf);
        if (taming.canUseFastFoodService()) {
            taming.fastFoodService(platformWolf, amount);
        }

        // Pummel: called unconditionally, matching legacy's processTamingCombat — the rank gate and
        // the static chance roll live inside the manager. It flings the target along the wolf's look
        // direction but never touches the damage total, so it sits between Fast Food Service and the
        // damage bonuses exactly as legacy sequences it.
        taming.processPummel(new PlatformLivingEntity(target), platformWolf);

        double boostedDamage = amount;
        if (taming.canUseSharpenedClaws()) {
            boostedDamage += taming.sharpenedClaws();
        }
        if (taming.canUseGore()) {
            boostedDamage += taming.gore(amount);
        }

        // Wolf-assisted Taming XP, at legacy's ×3 multiplier (processTamingCombat's closing line).
        // This is one of the two things the old per-kill XP model structurally could not pay: that
        // listener only fired when the *killer* was a player, so a wolf's kill paid nothing at all.
        CombatUtils.processCombatXP(mmoPlayer, target, PrimarySkillType.TAMING, boostedDamage,
                WOLF_ASSIST_XP_MULTIPLIER);
        return (float) boostedDamage;
    }

    /**
     * K1 attacker branch, projectile half: a player's arrow, crossbow bolt or thrown trident carries
     * that skill's on-hit damage bonus. Ports the {@code painSource instanceof Trident} /
     * {@code instanceof AbstractArrow} arms of legacy {@code CombatUtils#processCombatAttack} plus
     * {@code processArcheryCombat} / {@code processCrossbowsCombat} / {@code processTridentCombatRanged}.
     *
     * <p>Dispatch mirrors legacy: a thrown {@link TridentEntity} is peeled off first (Bukkit's
     * {@code Trident} also implements {@code AbstractArrow}, and legacy's if/else-if tests it before
     * the arrow arm), then everything else that is a {@link PersistentProjectileEntity} — a regular or
     * spectral arrow — is Archery unless it was fired from a crossbow, in which case it is Crossbows.
     * Bukkit's {@code AbstractArrow#isShotFromCrossbow()} was removed in 1.21.11, so the weapon that
     * fired the projectile is read from {@link PersistentProjectileEntity#getWeaponStack()} instead —
     * a genuinely nullable field, hence {@link #isCrossbowShot} rather than a bare {@code isOf} call.
     *
     * <p>Each arm pays its skill's per-hit XP, and the Archery/Crossbows arms scale theirs by the
     * shot's range (see {@link #distanceXpMultiplier}). Archery additionally scales by bow draw force
     * ({@link Archery#bowForceMultiplier}, stamped at launch by {@code BowShootMixin}); Crossbows does
     * not, legacy hardcoding its force to {@code 1.0}. Limit Break is dropped across every combat skill
     * in this port (PvP-only in singleplayer, and its {@code AllowPVE} switch defaults off), so it is
     * not applied here either; and Daze only targets another player, of which singleplayer has none.
     */
    private static float applyProjectileAttackBonus(LivingEntity target, DamageSource source,
            float amount) {
        if (!(source.getSource() instanceof PersistentProjectileEntity projectile)) {
            return amount; // not a projectile hit.
        }
        if (!(projectile.getOwner() instanceof ServerPlayerEntity shooter)) {
            return amount; // wild/dispenser projectile, or not fired by this player.
        }
        if (target instanceof ArmorStandEntity) {
            return amount; // legacy skips armor stands on every combat path (processCombatAttack top).
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(shooter.getUuid());
        if (mmoPlayer == null) {
            return amount; // data not loaded (e.g. mid-join).
        }

        if (projectile instanceof TridentEntity) {
            return applyTridentImpale(mmoPlayer, target, amount);
        }
        if (isCrossbowShot(projectile)) {
            return applyPoweredShot(mmoPlayer, target, projectile, amount);
        }
        return applyArcheryBonus(mmoPlayer, target, projectile, amount);
    }

    /**
     * Whether this projectile was loosed from a crossbow rather than a bow (Crossbows vs Archery).
     * {@code AbstractArrow#isShotFromCrossbow()} was removed in 1.21.11, so the firing weapon is read
     * from the arrow's own record instead.
     *
     * <p>The null guard is load-bearing, not defensive noise: {@code getWeaponStack()} returns a
     * genuinely nullable field — vanilla's {@code readCustomData} restores it with
     * {@code orElse(null)}, and the {@code (EntityType, World)} constructor leaves it null — so a
     * player-owned arrow that never went through {@code RangedWeaponItem} (summoned with an
     * {@code Owner} tag, restored from a world saved before the field existed, or spawned and adopted
     * by another mod — the case legacy's own "some plugins spawn arrows and assign them to players"
     * comment describes) would otherwise NPE here, inside the vanilla damage pipeline. A missing
     * weapon reads as a bow shot, which is the correct fallback: "not a crossbow → Archery".
     */
    private static boolean isCrossbowShot(PersistentProjectileEntity projectile) {
        final ItemStack weapon = projectile.getWeaponStack();
        return weapon != null && weapon.isOf(Items.CROSSBOW);
    }

    /**
     * Archery: a bow-fired arrow's <b>Skill Shot</b> damage bonus and <b>Arrow Retrieval</b> credit
     * (legacy {@code processArcheryCombat}).
     *
     * <p>Skill Shot, Arrow Retrieval and the XP award are independent, as they are upstream — each
     * sits in its own {@code if}, so a player whose Skill Shot is locked (or disabled) still collects
     * their arrows and still earns Archery XP. Retrieval only credits the target here; the arrows
     * themselves drop when it dies (see {@link ProjectileListener}).
     */
    private static float applyArcheryBonus(McMMOPlayer mmoPlayer, LivingEntity target,
            PersistentProjectileEntity projectile, float amount) {
        if (!CombatUtils.canCombatSkillsTrigger(PrimarySkillType.ARCHERY, target)) {
            return amount;
        }
        final ArcheryManager archery = mmoPlayer.getArcheryManager();
        if (archery == null) {
            return amount;
        }

        if (archery.canRetrieveArrows()) {
            archery.retrieveArrows(target.getUuid(), projectile.getUuid());
        }

        float boostedDamage = amount;
        if (archery.canSkillShot()) {
            boostedDamage = (float) archery.skillShot(amount); // not additive — Skill Shot replaces it.
        }
        // Legacy pays `forceMultiplier * distanceMultiplier`. Bow force was stamped at launch by
        // `BowShootMixin`; an arrow that skipped that hook (or whose mark aged out) reads back the flat
        // 1.0 legacy defaulted it to, so the product degrades to distance-only rather than to zero.
        final double xpMultiplier = Archery.bowForceMultiplier(projectile.getUuid())
                * distanceXpMultiplier(target, projectile);
        CombatUtils.processCombatXP(mmoPlayer, target, PrimarySkillType.ARCHERY, boostedDamage,
                xpMultiplier);
        return boostedDamage;
    }

    /**
     * The fired-from-distance XP multiplier for a projectile hit — legacy's static
     * {@code ArcheryManager#distanceXpBonusMultiplier(target, arrow)}, which both the Archery and the
     * Crossbows arm call. This owns only the MC-typed reads (the struck entity's world and position);
     * the measurement itself is MC-free on {@link Archery}.
     */
    private static double distanceXpMultiplier(LivingEntity target,
            PersistentProjectileEntity projectile) {
        return Archery.distanceXpBonusMultiplier(projectile.getUuid(),
                target.getEntityWorld().getRegistryKey().getValue().toString(),
                target.getX(), target.getY(), target.getZ());
    }

    /**
     * Crossbows Powered Shot: a crossbow bolt's damage bonus (legacy {@code processCrossbowsCombat}),
     * plus the bolt's distance-scaled per-hit Crossbows XP.
     *
     * <p>The distance multiplier is the very same Archery static legacy calls from here. Legacy also
     * hardcodes {@code forceMultiplier = 1.0} on this arm — a crossbow is loosed at full power, so
     * there is no draw to scale by — which is why this arm is complete while Archery's still owes its
     * force half.
     */
    private static float applyPoweredShot(McMMOPlayer mmoPlayer, LivingEntity target,
            PersistentProjectileEntity projectile, float amount) {
        if (!CombatUtils.canCombatSkillsTrigger(PrimarySkillType.CROSSBOWS, target)) {
            return amount;
        }
        final CrossbowsManager crossbows = mmoPlayer.getCrossbowsManager();
        if (crossbows == null) {
            return amount;
        }

        float boostedDamage = amount;
        if (crossbows.canPoweredShot()) {
            boostedDamage = (float) crossbows.poweredShot(amount); // not additive — it replaces it.
        }
        CombatUtils.processCombatXP(mmoPlayer, target, PrimarySkillType.CROSSBOWS, boostedDamage,
                distanceXpMultiplier(target, projectile));
        return boostedDamage;
    }

    /**
     * Tridents Impale (ranged): a thrown trident's flat damage bonus (legacy
     * {@code processTridentCombatRanged}) plus its per-hit Tridents XP. Unlike the melee trident path,
     * the ranged bonus is <em>not</em> scaled by attack strength — a thrown trident has no swing to
     * charge.
     */
    private static float applyTridentImpale(McMMOPlayer mmoPlayer, LivingEntity target, float amount) {
        if (!CombatUtils.canCombatSkillsTrigger(PrimarySkillType.TRIDENTS, target)) {
            return amount;
        }
        final TridentsManager tridents = mmoPlayer.getTridentsManager();
        if (tridents == null) {
            return amount;
        }

        float boostedDamage = amount;
        if (tridents.canImpale()) {
            boostedDamage = amount + (float) tridents.impaleDamageBonus();
        }
        CombatUtils.processCombatXP(mmoPlayer, target, PrimarySkillType.TRIDENTS, boostedDamage);
        return boostedDamage;
    }

    /**
     * K1 defender branch, Taming half: the player's wolf is taking damage, and Taming may soften,
     * heal back or shrug it off depending on what hurt it. Ports the {@code Tameable} arm of legacy
     * {@code EntityListener#onEntityDamage}, including {@code Taming.canPreventDamage}'s
     * {@code isTamed() && owner instanceof Player && pet instanceof Wolf} gate — {@code getOwner()}
     * is null unless tamed, so matching {@link WolfEntity} and a {@link ServerPlayerEntity} owner is
     * that whole check.
     *
     * <p>Legacy switches on Bukkit's {@code DamageCause}, which has no modern counterpart; each arm is
     * mapped to the vanilla damage types Bukkit derived that cause from (see the helpers below). The
     * arms are mutually exclusive and every one of them {@code return}s, exactly as legacy's
     * {@code switch} did.
     *
     * <p>Environmentally Aware rides both seams: its {@code CONTACT}/{@code FIRE}/{@code HOT_FLOOR}/
     * {@code LAVA} arm teleports the wolf clear from here (see {@link #isEnvironmentallyAwareCause}),
     * while its {@code FALL} arm cancels the hit outright and so rides the {@code ALLOW_DAMAGE} veto
     * (see {@link #onAllowDamage}) rather than this reduce-only seam.
     */
    private static float handleWolfDamage(WolfEntity wolf, DamageSource source, float amount) {
        if (!(wolf.getOwner() instanceof ServerPlayerEntity owner)) {
            return amount; // wild wolf (getOwner() is null unless tamed).
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(owner.getUuid());
        if (mmoPlayer == null) {
            return amount;
        }
        final TamingManager taming = mmoPlayer.getTamingManager();
        if (taming == null) {
            return amount;
        }

        // ENTITY_ATTACK / PROJECTILE -> Thick Fur halves the hit.
        if (isEntityAttack(source) || source.isIn(DamageTypeTags.IS_PROJECTILE)) {
            if (taming.canUseThickFur()) {
                // Legacy additionally cancelled the event when the reduction bottomed out at 0; a
                // returned 0 is equivalent in effect (no health lost), as with Demolitions Expertise.
                return (float) Math.max(taming.processThickFur(amount), 0.0D);
            }
            return amount;
        }

        // FIRE_TICK -> Thick Fur snuffs the flames. Note this is vanilla ON_FIRE (*burning*), not the
        // IS_FIRE tag: that tag also covers IN_FIRE/CAMPFIRE, which are Bukkit's FIRE cause and
        // belong to the deferred Environmentally Aware arm, not to this one.
        if (source.isOf(DamageTypes.ON_FIRE)) {
            if (taming.canUseThickFur()) {
                new PlatformLivingEntity(wolf).extinguish();
            }
            return amount;
        }

        // CONTACT / FIRE / HOT_FLOOR / LAVA -> Environmentally Aware teleports the wolf back to its
        // owner (out of continued contact). The hit itself still lands — legacy's teleport arm neither
        // reduces nor cancels the damage; only the FALL arm cancels, and it rides the ALLOW_DAMAGE veto
        // (see onAllowDamage) since this seam cannot cancel. Note the FIRE half is IN_FIRE/CAMPFIRE,
        // distinct from the ON_FIRE (FIRE_TICK) burning DoT the Thick Fur arm above handles.
        if (isEnvironmentallyAwareCause(source)) {
            if (taming.canUseEnvironmentallyAware()) {
                taming.processEnvironmentallyAware(new PlatformLivingEntity(wolf), amount);
            }
            return amount;
        }

        // MAGIC / POISON / WITHER -> Holy Hound heals the wolf for what it took.
        if (isHolyHoundCause(source)) {
            if (taming.canUseHolyHound()) {
                taming.processHolyHound(new PlatformLivingEntity(wolf), amount);
            }
            return amount;
        }

        // BLOCK_EXPLOSION / ENTITY_EXPLOSION / LIGHTNING -> Shock Proof divides the hit down.
        if (source.isIn(DamageTypeTags.IS_EXPLOSION) || source.isIn(DamageTypeTags.IS_LIGHTNING)) {
            if (taming.canUseShockProof()) {
                return (float) Math.max(taming.processShockProof(amount), 0.0D);
            }
            return amount;
        }

        return amount;
    }

    /**
     * Bukkit's {@code ENTITY_ATTACK}: a melee blow from a mob or a player. Bukkit derived that cause
     * from these damage types; the projectile ones it mapped to {@code PROJECTILE} instead, which the
     * caller tests separately via {@code IS_PROJECTILE}.
     */
    private static boolean isEntityAttack(DamageSource source) {
        return source.isIn(DamageTypeTags.IS_PLAYER_ATTACK)
                || source.isOf(DamageTypes.MOB_ATTACK)
                || source.isOf(DamageTypes.MOB_ATTACK_NO_AGGRO);
    }

    /**
     * Bukkit's {@code MAGIC}, {@code POISON} and {@code WITHER} causes, which Holy Hound treats
     * alike. Note the three collapse to two tests here: vanilla deals Poison's damage as
     * {@link DamageTypes#MAGIC}, so Bukkit's separate {@code POISON} cause has no distinct damage
     * type to match on and is already covered.
     */
    private static boolean isHolyHoundCause(DamageSource source) {
        return source.isOf(DamageTypes.MAGIC)
                || source.isOf(DamageTypes.INDIRECT_MAGIC)
                || source.isOf(DamageTypes.WITHER);
    }

    /**
     * Bukkit's {@code CONTACT} / {@code FIRE} / {@code HOT_FLOOR} / {@code LAVA} causes, which
     * Environmentally Aware treats alike (teleport the wolf clear). {@code CONTACT} is cactus / sweet
     * berry bush / dripstone, and {@code FIRE} is the <em>standing-in-fire</em> cause
     * ({@link DamageTypes#IN_FIRE}/{@link DamageTypes#CAMPFIRE}) — deliberately not {@link
     * DamageTypes#ON_FIRE}, the burning DoT Bukkit called {@code FIRE_TICK} and that the Thick Fur
     * snuff arm handles instead.
     */
    private static boolean isEnvironmentallyAwareCause(DamageSource source) {
        return source.isOf(DamageTypes.CACTUS)
                || source.isOf(DamageTypes.SWEET_BERRY_BUSH)
                || source.isOf(DamageTypes.STALAGMITE)
                || source.isOf(DamageTypes.IN_FIRE)
                || source.isOf(DamageTypes.CAMPFIRE)
                || source.isOf(DamageTypes.HOT_FLOOR)
                || source.isOf(DamageTypes.LAVA);
    }

    /**
     * Whether this hit is the player's own Blast Mining charge going off <i>and</i> they have
     * Demolitions Expertise unlocked. Mirrors the gates in legacy
     * {@code BlastMining#processBlastMiningExplosion} that decide whether it handles the hit
     * (returns true) or lets normal combat processing continue (returns false).
     *
     * <p>Legacy's other branch — capping the damage another player's charge deals at 24 — is dropped
     * with the rest of PvP (see {@code BlastMining}'s javadoc): the only player a blast can hit here
     * is the one who set it off.
     */
    private static boolean canReduceOwnBlast(ServerPlayerEntity serverPlayer, DamageSource source) {
        final UUID detonator = BlastMiningListener.detonatorUuid(source.getSource());
        if (detonator == null || !detonator.equals(serverPlayer.getUuid())) {
            return false; // not an mcMMO charge, or not this player's.
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(serverPlayer.getUuid());
        return mmoPlayer != null && mmoPlayer.getMiningManager().canUseDemolitionsExpertise();
    }

    /**
     * Demolitions Expertise: reduce the damage the player's own Blast Mining charge deals to them,
     * by their rank's percentage (legacy {@code MiningManager#processDemolitionsExpertise}).
     */
    private static float handleOwnBlastDamage(ServerPlayerEntity serverPlayer, float amount) {
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(serverPlayer.getUuid());
        if (mmoPlayer == null) {
            return amount;
        }
        // TUNING (CONVERSION_TODO §F): as with the melee bonuses above, modifyAppliedDamage is
        // POST-armor, so the reduction compounds with armor rather than preceding it as in legacy.
        // Legacy additionally cancelled the hit outright when the reduction took it to <= 0; a
        // returned 0 here is equivalent in effect (no health lost).
        return (float) Math.max(mmoPlayer.getMiningManager().processDemolitionsExpertise(amount), 0.0D);
    }

    /**
     * K1 attacker branch: when a player lands a direct melee swing on a living entity, add the on-hit
     * damage bonus for the weapon in their main hand (Swords Stab / Axe Mastery / Unarmed Steel Arm +
     * Berserk). The bonus arithmetic lives MC-free in {@link MeleeDamageBonus}; this method owns the
     * MC-typed gating: attacker identity, the direct-melee check, and held-item classification.
     */
    private static float applyAttackerWeaponBonus(LivingEntity target, DamageSource source,
            float amount) {
        if (!(source.getAttacker() instanceof ServerPlayerEntity attacker)) {
            return amount; // environmental / mob-dealt damage.
        }
        // Only a direct melee swing: the *direct* source of the damage is the player themselves. A
        // ranged hit's direct source is the projectile; reflected Thorns damage is not a weapon swing.
        if (source.getSource() != attacker || source.isOf(DamageTypes.THORNS)) {
            return amount;
        }
        if (target instanceof ArmorStandEntity) {
            return amount; // legacy skips armor stands.
        }

        final McMMOPlayer mmoPlayer = UserManager.getPlayer(attacker.getUuid());
        if (mmoPlayer == null) {
            return amount; // data not loaded (e.g. mid-join).
        }

        final MeleeWeapon weapon = classifyMainHand(attacker.getMainHandStack());
        if (weapon == MeleeWeapon.OTHER) {
            return amount;
        }
        // Legacy gates each weapon's branch on the skill's Enabled_For_PVE/PVP switch before doing
        // anything. That gate was dropped when SkillTools was ported without an entity adapter, so
        // until now these switches did nothing on the attacker side; restored with the adapter.
        if (!CombatUtils.canCombatSkillsTrigger(skillOf(weapon), target)) {
            return amount;
        }
        // TUNING (CONVERSION_TODO §F): modifyAppliedDamage is POST-armor, so these bonuses bypass the
        // target's armor mitigation — a discrepancy vs legacy, which boosted the pre-armor damage.
        // Flagged for the tuning pass; the correct seam is a pre-armor hook once one exists.
        final PlatformLivingEntity platformTarget = new PlatformLivingEntity(target);
        final float boostedDamage = MeleeDamageBonus.applyBonus(mmoPlayer, weapon, amount,
                platformTarget);

        // Legacy's per-weapon ordering, preserved: the super-ability AoE fires after the damage
        // bonus is computed but before it is committed, and is passed the *unboosted* damage
        // (legacy hands it `event.getDamage()`, which it only overwrites via setDamage afterwards).
        //
        // PORT: legacy sequences the Axes AoE *between* Greater Impact and Critical Strikes rather
        // than after the whole chain as here. Equivalent: the AoE neither reads nor writes the
        // damage total (it is handed the unboosted amount either way) and it never touches the
        // primary target, so only the order of the player's own chat notifications differs.
        if (weapon == MeleeWeapon.SWORD) {
            maybeProcessSerratedStrikes(mmoPlayer, attacker, target, amount);
            maybeProcessRupture(mmoPlayer, target, boostedDamage);
        } else if (weapon == MeleeWeapon.AXE) {
            maybeProcessSkullSplitter(mmoPlayer, attacker, platformTarget, target, amount);
        } else if (weapon == MeleeWeapon.MACE) {
            maybeProcessCripple(mmoPlayer, target, boostedDamage);
        }

        // Per-hit combat XP, paid on the *boosted* damage — legacy ends every processXCombat with
        // this, after event.setDamage(boostedDamage), and its health-diff measured what actually
        // landed. No multiplier on the melee path (legacy's 3-arg processCombatXP overload).
        CombatUtils.processCombatXP(mmoPlayer, target, skillOf(weapon), boostedDamage);
        return boostedDamage;
    }

    /**
     * Swords Serrated Strikes: while the super ability is active, a sword hit also strikes nearby
     * entities for a fraction of the damage. Mirrors legacy {@code CombatUtils#processSwordCombat}'s
     * {@code canUseSerratedStrike} arm.
     */
    private static void maybeProcessSerratedStrikes(McMMOPlayer mmoPlayer,
            ServerPlayerEntity attacker, LivingEntity target, float damage) {
        final SwordsManager swords = mmoPlayer.getSwordsManager();
        if (swords == null || !swords.canUseSerratedStrike()) {
            return;
        }
        CombatUtils.applyAbilityAoE(attacker, mmoPlayer, target,
                swords.serratedStrikesDamage(damage), PrimarySkillType.SWORDS);
    }

    /**
     * Axes Skull Splitter: while the super ability is active, an axe hit also strikes nearby entities
     * for a fraction of the damage. Mirrors legacy {@code CombatUtils#processAxeCombat}'s
     * {@code canUseSkullSplitter} arm.
     */
    private static void maybeProcessSkullSplitter(McMMOPlayer mmoPlayer, ServerPlayerEntity attacker,
            PlatformLivingEntity platformTarget, LivingEntity target, float damage) {
        final AxesManager axes = mmoPlayer.getAxesManager();
        if (axes == null || !axes.canUseSkullSplitter(platformTarget)) {
            return;
        }
        CombatUtils.applyAbilityAoE(attacker, mmoPlayer, target, axes.skullSplitterDamage(damage),
                PrimarySkillType.AXES);
    }

    /**
     * Swords Rupture: a sword hit that leaves the target alive may start a bleed. Mirrors legacy
     * {@code CombatUtils#processSwordCombat}, which calls {@code processRupture} only once the
     * boosted damage is settled and only when the target survives the hit — there is no point
     * bleeding something this swing already kills, and legacy's Rupture can never land a killing
     * blow anyway.
     *
     * <p>{@code modifyAppliedDamage} runs before vanilla writes the new health, so reading
     * {@link LivingEntity#getHealth()} here gives the pre-hit health — the same value legacy's
     * {@code target.getHealth() - event.getFinalDamage()} check saw.
     */
    private static void maybeProcessRupture(McMMOPlayer mmoPlayer, LivingEntity target,
            float boostedDamage) {
        if (target.getHealth() - boostedDamage <= 0) {
            return; // the swing itself is lethal.
        }
        mmoPlayer.getSwordsManager().processRupture(new PlatformLivingEntity(target),
                mmoPlayer.getAttackStrength());
    }

    /**
     * Maces Cripple: a mace hit that leaves the target alive may apply Slowness. Mirrors legacy
     * {@code CombatUtils#processMacesCombat}, which calls {@code processCripple} only when
     * {@code target.getHealth() - event.getFinalDamage() > 0} — no point crippling something the swing
     * kills. As with Rupture, {@code modifyAppliedDamage} runs before vanilla writes the new health, so
     * reading {@link LivingEntity#getHealth()} gives the pre-hit value that check compared against.
     */
    private static void maybeProcessCripple(McMMOPlayer mmoPlayer, LivingEntity target,
            float boostedDamage) {
        if (target.getHealth() - boostedDamage <= 0) {
            return; // the swing itself is lethal.
        }
        final MacesManager maces = mmoPlayer.getMacesManager();
        if (maces == null) {
            return;
        }
        maces.processCripple(new PlatformLivingEntity(target), mmoPlayer.getAttackStrength());
    }

    /**
     * Swords Counter Attack: a player hit while holding a sword may reflect a fraction of the damage
     * back at their assailant. Ports legacy {@code CombatUtils#processCombatAttack}'s defender arm
     * plus {@code SwordsManager#counterAttackChecks}.
     *
     * <p>Only a <em>living, direct</em> damager can be countered — legacy passes {@code painSource}
     * (the damager itself, not the projectile's shooter) and its {@code canUseCounterAttack} requires
     * {@code instanceof LivingEntity}, so an arrow or a Blast Mining charge counters nothing.
     *
     * <p>⚠️ FIXED UPSTREAM BUG (CONVERSION_TODO §F #5, a new shape — <b>role inversion</b>): legacy
     * gates this on {@code canCombatSkillsTrigger(SWORDS, target)}, but in the defender arm
     * {@code target} is the <em>player</em>, not the entity being acted upon. That makes
     * {@code isPlayerOrTamed} unconditionally true, so a PvE counter against a mob is decided by
     * {@code Enabled_For_PVP} — an operator disabling Swords for PvP silently kills counter-attacks
     * against mobs, and one disabling it for PvE does not. Every other call site passes the entity the
     * skill acts upon; this one is ported to that intent ({@code assailant}). Both switches default to
     * {@code true}, so the shipped config behaves identically either way.
     *
     * @param damage the damage the player is taking, after any Dodge reduction
     */
    private static void maybeProcessCounterAttack(ServerPlayerEntity serverPlayer,
            DamageSource source, float damage) {
        // The *direct* damager, matching legacy's painSource (not painSourceRoot).
        if (!(source.getSource() instanceof LivingEntity assailant)) {
            return;
        }
        if (!ItemUtils.isSword(serverPlayer.getMainHandStack())) {
            return;
        }
        if (!CombatUtils.canCombatSkillsTrigger(PrimarySkillType.SWORDS, assailant)) {
            return;
        }

        final McMMOPlayer mmoPlayer = UserManager.getPlayer(serverPlayer.getUuid());
        if (mmoPlayer == null) {
            return;
        }
        final SwordsManager swords = mmoPlayer.getSwordsManager();
        if (swords == null || !swords.canUseCounterAttack() || !swords.rollCounterAttack()) {
            return;
        }

        CombatUtils.safeDealDamage(assailant, swords.counterAttackDamage(damage), serverPlayer);
        NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_MESSAGE,
                "Swords.Combat.Countered");
        // PORT: legacy also notified the countered attacker ("Swords.Combat.Counter.Hit"), which only
        // fires `if (attacker instanceof Player)` — dead in singleplayer, where the only player is the
        // one countering. Dropped with the rest of PvP.
    }

    /** The primary skill a melee weapon's on-hit bonuses belong to (legacy's per-weapon dispatch). */
    private static PrimarySkillType skillOf(MeleeWeapon weapon) {
        return switch (weapon) {
            case SWORD -> PrimarySkillType.SWORDS;
            case AXE -> PrimarySkillType.AXES;
            case MACE -> PrimarySkillType.MACES;
            case TRIDENT -> PrimarySkillType.TRIDENTS;
            case UNARMED -> PrimarySkillType.UNARMED;
            case OTHER -> throw new IllegalArgumentException("OTHER has no skill; gate it first");
        };
    }

    /**
     * Classify a held main-hand stack into the melee weapon whose bonus applies. The set and the order
     * are legacy's {@code processCombatAttack} dispatch chain, and the arms are mutually exclusive, so
     * the order is cosmetic — except that {@code isUnarmed} must come last, since with
     * {@code Unarmed_Items_As_Unarmed} on it matches any non-tool item and would otherwise swallow a
     * mace or a trident.
     *
     * <p>{@code OTHER} means "not a weapon mcMMO trains" (a pickaxe, a block, a bow used as a club),
     * and pays no bonus and no XP — matching legacy, whose dispatch simply has no arm for those.
     * Spears are the one gap: legacy routes them off a {@code SPEAR} damage type rather than the held
     * item, and this port has never paid Spears combat XP (CONVERSION_TODO §C).
     */
    private static MeleeWeapon classifyMainHand(ItemStack held) {
        if (ItemUtils.isSword(held)) {
            return MeleeWeapon.SWORD;
        }
        if (ItemUtils.isAxe(held)) {
            return MeleeWeapon.AXE;
        }
        if (ItemUtils.isMace(held)) {
            return MeleeWeapon.MACE;
        }
        if (ItemUtils.isTrident(held)) {
            return MeleeWeapon.TRIDENT;
        }
        if (ItemUtils.isUnarmed(held)) {
            return MeleeWeapon.UNARMED;
        }
        return MeleeWeapon.OTHER;
    }

    private static float handleFallDamage(ServerPlayerEntity serverPlayer, float amount) {
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(serverPlayer.getUuid());
        if (mmoPlayer == null) {
            return amount; // data not loaded (e.g. mid-join).
        }
        final AcrobaticsManager acrobatics = mmoPlayer.getAcrobaticsManager();
        if (acrobatics == null) {
            return amount;
        }

        // The manager awards XP + tracks the landing block internally; it hands back the outcome so we
        // can apply the damage reduction + feedback (the MC-typed half) here.
        final RollResult result = acrobatics.processFallDamage(amount);
        if (result == null || !result.isRollSuccess()) {
            return amount;
        }

        NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_MESSAGE,
                result.isGraceful() ? "Acrobatics.Ability.Proc" : "Acrobatics.Roll.Text");
        SoundManager.sendCategorizedSound(mmoPlayer.getPlayer(), SoundType.ROLL_ACTIVATED,
                SoundCategory.PLAYERS, 0.5F);
        return (float) result.getModifiedDamage();
    }

    /** Transient per-mob counter of how many dodge-XP awards it has handed out (anti-farm cap). */
    private static final String DODGE_TRACKER_KEY = "mcmmo:dodge_tracker";
    /** Legacy cap: a single mob only pays out dodge XP six times (count 0..5 inclusive). */
    private static final int DODGE_XP_MAX_AWARDS = 5;

    /**
     * K1 defender branch: a player taking a hit from an entity may Dodge, reducing the damage and
     * (against an eligible mob) gaining Acrobatics XP. Mirrors legacy
     * {@code CombatUtils.processCombatAttack}'s dodge path. (The attacker-side melee weapon bonuses
     * are handled separately in {@link #applyAttackerWeaponBonus}.)
     */
    private static float handleDodge(ServerPlayerEntity serverPlayer, Entity attacker, float amount) {
        // Lightning dodge can be excluded by config (legacy Acrobatics.dodgeLightningDisabled).
        if (attacker instanceof LightningEntity
                && McMMOMod.getGeneralConfig().getDodgeLightningDisabled()) {
            return amount;
        }

        final McMMOPlayer mmoPlayer = UserManager.getPlayer(serverPlayer.getUuid());
        if (mmoPlayer == null) {
            return amount;
        }
        final AcrobaticsManager acrobatics = mmoPlayer.getAcrobaticsManager();
        if (acrobatics == null) {
            return amount;
        }

        // Only mobs grant dodge XP, and only up to the per-mob cap; the manager still reduces damage
        // when the attacker is XP-ineligible, it just pays nothing.
        final boolean xpEligible = attacker instanceof MobEntity && dodgeXpUncapped((MobEntity) attacker);

        final DodgeResult result = acrobatics.processDodge(amount, xpEligible);
        if (result == null) {
            return amount; // no dodge — leave the hit untouched.
        }

        if (result.getXpGain() > 0) {
            incrementDodgeTracker((MobEntity) attacker);
        }
        if (mmoPlayer.useChatNotifications()) {
            NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_MESSAGE,
                    "Acrobatics.Combat.Proc");
        }
        // PORT: legacy also spawned ParticleEffectUtils.playDodgeEffect + scheduled MobDodgeMetaCleanup
        // to expire the tracker after a minute. Particles need a PlatformPlayer particle adapter; the
        // cleanup task is a refinement (without it the transient tracker just persists for the mob's
        // session lifetime, which is a stricter — still correct — anti-farm cap). Both deferred.
        return (float) result.getModifiedDamage();
    }

    /** Whether {@code mob} has not yet hit the dodge-XP award cap. */
    private static boolean dodgeXpUncapped(MobEntity mob) {
        if (!McMMOMod.getExperienceConfig().isAcrobaticsExploitingPrevented()) {
            return true; // exploit prevention off → uncapped.
        }
        final Integer count = MetadataStore.get(mob, DODGE_TRACKER_KEY, Integer.class);
        return count == null || count <= DODGE_XP_MAX_AWARDS;
    }

    /** Bump the per-mob dodge-XP counter after a successful, XP-paying dodge. */
    private static void incrementDodgeTracker(MobEntity mob) {
        if (!McMMOMod.getExperienceConfig().isAcrobaticsExploitingPrevented()) {
            return;
        }
        final Integer count = MetadataStore.get(mob, DODGE_TRACKER_KEY, Integer.class);
        MetadataStore.set(mob, DODGE_TRACKER_KEY, count == null ? 1 : count + 1);
    }
}
