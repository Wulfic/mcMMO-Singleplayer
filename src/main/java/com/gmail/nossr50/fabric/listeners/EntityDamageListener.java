package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.skills.subskills.movement.DodgeResult;
import com.gmail.nossr50.datatypes.skills.subskills.movement.RollResult;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.platform.MetadataStore;
import com.gmail.nossr50.platform.PlatformLivingEntity;
import com.gmail.nossr50.skills.LimitBreak;
import com.gmail.nossr50.skills.MeleeDamageBonus;
import com.gmail.nossr50.skills.MeleeDamageBonus.MeleeWeapon;
import com.gmail.nossr50.skills.movement.MovementManager;
import com.gmail.nossr50.skills.archery.Archery;
import com.gmail.nossr50.skills.archery.ArcheryManager;
import com.gmail.nossr50.skills.axes.AxesManager;
import com.gmail.nossr50.skills.crossbows.CrossbowsManager;
import com.gmail.nossr50.skills.hunter.HunterManager;
import com.gmail.nossr50.skills.maces.MacesManager;
import com.gmail.nossr50.skills.spears.SpearsManager;
import com.gmail.nossr50.skills.swords.SwordsManager;
import com.gmail.nossr50.skills.stealth.StealthManager;
import com.gmail.nossr50.skills.taming.TamingManager;
import com.gmail.nossr50.skills.tridents.TridentsManager;
import com.gmail.nossr50.skills.unarmed.UnarmedManager;
import com.gmail.nossr50.skills.unarmored.UnarmoredManager;
import com.gmail.nossr50.platform.ItemUtils;
import com.gmail.nossr50.platform.MobTiers;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.platform.CombatUtils;
import com.gmail.nossr50.platform.ParticleEffectUtils;
import com.gmail.nossr50.util.sounds.SoundManager;
import com.gmail.nossr50.util.sounds.SoundType;
import com.gmail.nossr50.platform.text.TextUtils;
import java.util.HashMap;
import java.util.Map;
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
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.LlamaEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;
import com.gmail.nossr50.platform.PlatformSoundCategory;

/**
 * The K1/K2 damage hook: mcMMO's window into the vanilla damage pipeline. Driven by a mixin on
 * {@link LivingEntity#modifyAppliedDamage(DamageSource, float)} (see
 * {@code fabric.mixin.LivingEntityDamageMixin}) rather than a Fabric event, because mcMMO needs to
 * <em>modify</em> the applied damage (Agility Roll reduces fall damage) and Fabric's
 * {@code ServerLivingEntityEvents.ALLOW_DAMAGE} is a cancel-only veto.
 *
 * <p>Currently wired: <b>K2 — fall damage → Agility Roll</b>, the defender half of <b>K1 — combat
 * damage → Agility Dodge</b> (attacker resolved via {@link DamageSource#getAttacker()}), and the
 * attacker half of <b>K1 — melee weapon on-hit damage bonuses</b> (Swords Stab / Axe Mastery /
 * Unarmed Steel Arm + Berserk / Maces Crush / Spear Mastery, composed MC-free in
 * {@link MeleeDamageBonus}), and the
 * on-hit <em>effect</em> sub-skills: <b>Swords Rupture</b> (bleed DoT — see {@link #maybeProcessRupture})
 * and the two combat super abilities, <b>Serrated Strikes</b> and <b>Skull Splitter</b> (AoE — see
 * {@link #maybeProcessSerratedStrikes} / {@link #maybeProcessSkullSplitter}), and — on the defender
 * side again — <b>Swords Counter Attack</b> (see {@link #maybeProcessCounterAttack}) and
 * <b>Unarmored Thorny Skin</b> (see {@link #maybeProcessThornySkin}) — and, after a
 * mace hit the target survives, <b>Maces Cripple</b> (Slowness — see {@link #maybeProcessCripple}),
 * and on any spear hit <b>Spears Momentum</b> (Speed on the <em>attacker</em> — see
 * {@link #maybeProcessMomentum}). The Axes
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
 *
 * <p>And one branch needs a reading the {@code modifyAppliedDamage} seam cannot give it at all:
 * <b>Unarmored</b>'s XP is paid on the damage as it was <em>before</em> armor mitigation (see
 * {@link #maybeAwardUnarmoredXp}), because the skill's own Iron Skin bonus is armor and would
 * otherwise throttle the XP that grants it. That value is captured a few bytecodes upstream by a
 * second injector on {@code applyArmorToDamage} and joined to this one through
 * {@link #recordPreArmorDamage} — so the same mixin class has two entry points into this listener.
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
     * arrow; see {@link #isArrowDeflected}), the two <b>bone-inspection</b> sub-skills — Taming's
     * <b>Beast Lore</b> and Hunter's <b>Quarry Sense</b>, which share one dispatcher (see
     * {@link #maybeInspect}) — and Taming's <b>Environmentally
     * Aware</b> FALL arm (a tamed wolf's fall damage is negated; see {@link #isEnvironmentallyAwareFall}).
     * Environmentally Aware's other environmental causes only teleport the wolf and leave the hit
     * intact, so they ride the reduce-only mixin instead (see {@link #handleWolfDamage}).
     *
     * <p>Package-private rather than private so the tests can drive the <b>real</b> dispatcher
     * instead of the branch methods it calls — otherwise a branch could be proved in full and then
     * quietly dropped from this method, which is the "gate proved, call site deleted" trap the port
     * has walked into before.
     *
     * @return {@code false} to cancel the hit, {@code true} to let it proceed
     */
    static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (entity instanceof ServerPlayerEntity serverPlayer) {
            return !isArrowDeflected(serverPlayer, source);
        }
        if (maybeInspect(entity, source)) {
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
     * The shared entry point for both <b>bone-inspection</b> sub-skills: a player who left-clicks a
     * creature while holding a bone reads it instead of hitting it, and the blow is cancelled.
     *
     * <p>Taming's <b>Beast Lore</b> (legacy's) and Hunter's <b>Quarry Sense</b> (stage 7, D-HU7) are
     * the same gesture on the same seam, so they dispatch from one place and can appear in one
     * message. A player with both, sneaking, bone in hand, hitting their own wolf gets the beast's
     * vitals <em>and</em> their hunt log against wolves in a single readout.
     *
     * <h2>⚠️ Quarry Sense needs the player to be SNEAKING and Beast Lore does not</h2>
     * The plan's D-HU7 said "reuse Taming's Beast Lore renderer", which is cheap and right. Reusing
     * its <b>gate</b> is not: that gate is {@code entity instanceof Tameable}, and the creatures
     * Hunter counts — zombie, skeleton, creeper, spider — are precisely the ones it excludes. Quarry
     * Sense therefore has to work on <em>anything</em> a player can kill, and the moment it does, the
     * cancelled blow becomes a hazard rather than a curiosity: <b>a bone is a skeleton's own drop</b>,
     * so a player who picks one up and is then set upon cannot swing back. Nobody has ever punched a
     * wolf by accident, which is why Beast Lore never had this problem and why its own trigger is left
     * exactly as it was.
     *
     * <p>Crouching is the unambiguous "I meant that" modifier, it cannot happen in a panic, and it
     * costs a keypress the player is already within arm's reach to make. Stealth's Assassin also fires
     * on a crouched melee hit, but only ever loses a backstab worth 1.0 damage here — the gate needs
     * a bone in the main hand, so an armed assassin is untouched.
     *
     * <p>Armour stands are excluded from Quarry Sense (never from Beast Lore, which cannot see one):
     * they are not creatures anybody hunts, their count is permanently zero, and sneak-hitting one is
     * how a player dismantles it.
     *
     * @return {@code true} if the creature was inspected (the caller should cancel the hit)
     */
    private static boolean maybeInspect(LivingEntity entity, DamageSource source) {
        // Legacy's entry conditions, unchanged and shared: a *direct* melee swing (legacy's
        // `entityType == EntityType.PLAYER`, i.e. the player is the direct damager, so a bone cannot
        // inspect by proxy through a projectile) thrown by a player holding a bone.
        if (!(source.getAttacker() instanceof ServerPlayerEntity attacker)
                || source.getSource() != attacker) {
            return false;
        }
        if (!attacker.getMainHandStack().isOf(Items.BONE)) {
            return false;
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(attacker.getUuid());
        if (mmoPlayer == null) {
            return false; // data not loaded (e.g. mid-join).
        }

        final TamingManager taming = mmoPlayer.getTamingManager();
        final boolean beastLore =
                entity instanceof Tameable && taming != null && taming.canUseBeastLore();

        final HunterManager hunter = mmoPlayer.getHunterManager();
        final boolean quarrySense = attacker.isSneaking()
                && !(entity instanceof ArmorStandEntity)
                && hunter != null && hunter.canQuarrySense();

        if (!beastLore && !quarrySense) {
            return false;
        }

        final StringBuilder message = new StringBuilder();
        if (beastLore) {
            message.append(beastLore(entity));
        }
        if (quarrySense) {
            if (!message.isEmpty()) {
                message.append('\n');
            }
            message.append(quarrySenseLore(hunter, entity.getType().getName().getString(),
                    HunterListener.masteryKeyOf(entity), MobTiers.tierOf(entity)));
        }
        attacker.sendMessage(TextUtils.toText(message.toString()));
        return true;
    }

    /**
     * Builds the Beast Lore stat readout, porting legacy {@code TamingManager#beastLore}.
     * MC-typed display glue: it reads the target's live health, tamed owner and (for the horse family)
     * movement-speed / jump-strength attributes, and hands the jump attribute to the already-extracted
     * pure conversion {@link TamingManager#beastLoreHorseJumpStrength}. The message is assembled as a
     * legacy {@code §}-coded string exactly as upstream did, and parsed into {@link Text} by the
     * caller — which may have a Quarry Sense block to append to it first.
     *
     * <p>{@link Tameable#getOwner()} returns {@code null} unless the animal is tamed and its owner is
     * resolvable, so it stands in for legacy's {@code isTamed() && getOwner() != null}. Llamas are
     * excluded from the horse block just as legacy excluded them (they carry no rideable jump/speed
     * stats worth showing).
     */
    private static String beastLore(LivingEntity target) {
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
                && horse.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH) != null) {
            final double jumpStrength = TamingManager.beastLoreHorseJumpStrength(
                    horse.getAttributeValue(EntityAttributes.GENERIC_JUMP_STRENGTH));
            final double speed = horse.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED) * 43;
            message += "\n" + LocaleLoader.getString("Combat.BeastLoreHorseSpeed", speed)
                    + "\n" + LocaleLoader.getString("Combat.BeastLoreHorseJumpStrength", jumpStrength);
        }

        return message;
    }

    /**
     * Builds Hunter <b>Quarry Sense</b>'s readout: what this player knows about this creature.
     *
     * <h2>Why it takes four plain values instead of the entity</h2>
     * Every Minecraft read this block needs — the creature's display name, its registry id, its
     * Hunter tier — is done by the caller, so the composition itself is registry-free and drivable
     * from a plain unit test across all six of its branches. Two of those reads are also the exact
     * two the port has had drift on before: the mastery key is
     * {@link HunterListener#masteryKeyOf} (never a locally re-derived id — the counters and the damage
     * bonus already share that one function for the same reason), and the tier is
     * {@link MobTiers#tierOf} (never a live health read).
     *
     * <p>Four lines, and each one answers a question the player would otherwise have to guess at:
     * how many have I killed, what is that worth, how many more until it is worth more, and does my
     * Trophy Hunter rank even reach this creature. The last is the reason the tier is shown at all —
     * a tier number with nothing hanging off it would be trivia.
     *
     * @param hunter       the viewing player's Hunter manager
     * @param creatureName the creature's display name, e.g. {@code Zombie}
     * @param mobId        the creature's full registry id, the key its counter is filed under
     * @param mobTier      the creature's Hunter tier, 1-4
     */
    static String quarrySenseLore(HunterManager hunter, String creatureName, String mobId,
            int mobTier) {
        final int kills = hunter.getKills(mobId);
        final int tier = hunter.masteryTier(kills);

        final StringBuilder lore = new StringBuilder()
                .append(LocaleLoader.getString("Hunter.SubSkill.QuarrySense.Lore", creatureName))
                .append('\n')
                .append(LocaleLoader.getString("Hunter.SubSkill.QuarrySense.Lore.Slain", kills))
                .append(' ');

        lore.append(tier <= 0
                ? LocaleLoader.getString("Hunter.SubSkill.QuarrySense.Lore.Unmastered")
                : LocaleLoader.getString("Hunter.SubSkill.QuarrySense.Lore.Mastery", tier,
                        String.valueOf(hunter.masteryDamageBonus(kills))));

        final int toNext = hunter.killsToNextMasteryTier(kills);
        lore.append('\n').append(toNext <= 0
                ? LocaleLoader.getString("Hunter.SubSkill.QuarrySense.Lore.Capped")
                : LocaleLoader.getString("Hunter.SubSkill.QuarrySense.Lore.Next", toNext, tier + 1));

        // The tier line carries the trophy state because on its own the tier is trivia: what a player
        // wants to know standing in front of a creature is whether their rank reaches it.
        lore.append('\n').append(LocaleLoader.getString("Hunter.SubSkill.QuarrySense.Lore.Tier",
                mobTier,
                LocaleLoader.getString(hunter.canTrophyHunt(mobTier)
                        ? "Hunter.SubSkill.QuarrySense.Lore.Trophy.Unlocked"
                        : "Hunter.SubSkill.QuarrySense.Lore.Trophy.Locked")));

        return lore.toString();
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
        // Read (and clear) the pre-armor figure first, before anything below can deal nested damage
        // and overwrite the stash — a Serrated Strikes AoE, a Counter Attack and Thorny Skin all
        // re-enter the damage pipeline from inside this method, and each of those runs its own
        // applyArmorToDamage. Taking the reading here is what makes the join single-frame.
        final float preArmorDamage = consumePreArmorDamage(entity, source, amount);

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
        // Call of the Wild's "sic your pets on it", for any ranged hit. ⚠️ DELIBERATELY ITS OWN
        // STATEMENT rather than a block inside applyProjectileAttackBonus, which is where it used to
        // live and where it was wrong twice over — see sicPetsOnRangedHit. It adds no damage, so it
        // is not part of the running total and nothing below depends on its position.
        sicPetsOnRangedHit(entity, source);
        // Pass 2: Agility Smash. Rides the same melee seam rather than adding a second damage mixin,
        // but deliberately outside the weapon-classified arm above — Smash is about the *sprint*, so
        // it applies whatever is in the player's hand, including nothing.
        result = applySprintSmash(entity, source, result);

        // Pass 2: Stealth Assassin. Sibling of Smash on the same seam and mutually exclusive with it
        // by construction — a player cannot sprint and sneak at once — so at most one of the two
        // fires for any swing. Runs after Smash so a backstab multiplies the whole melee total.
        result = applyAssassin(entity, source, result);

        // Pass 2: Hunter Mob Mastery. ⚠️ LAST IN THIS CHAIN, AND THE POSITION IS LOAD-BEARING.
        // Assassin above multiplies the *whole* running melee total, so a Hunter bonus added before
        // it would be multiplied too — "+3.0 damage against zombies" would silently become +3.0 ×
        // backstab × crit against a crouching player. Landing it here makes the number on the tin the
        // number that lands. It is also the only sibling keyed on the *target's* identity rather than
        // the attacker's state, so nothing below it could want to read a pre-Hunter figure.
        // Pinned by EntityDamageListenerHunterTest#theMasteryBonusIsAddedAfterAssassinMultiplies —
        // swap these two lines and that test, and only that test, goes red.
        result = applyHunterMastery(entity, source, result);

        // K1 defender / K2 branch: the entity *taking* damage is a player — fall damage feeds
        // Agility Roll, an incoming entity hit feeds Agility Dodge.
        if (entity instanceof ServerPlayerEntity serverPlayer) {
            // Stamp the Assassin recency window before anything can reduce or cancel the damage
            // (D-S3). Deliberately every incoming source, not just combat: a player who just took
            // fall damage or stepped in lava is not lurking in the shadows either.
            recordDamageTaken(serverPlayer);
            // Pass 2: Unarmored XP. Sits outside the fall / blast / dodge dispatch below because it
            // is not one of those cases — it pays for *being hit while bare*, whatever hit you — and
            // it is paid on the pre-armor reading rather than on `result`, so neither Iron Skin nor
            // a successful Dodge can shrink the XP for the blow that earned it.
            maybeAwardUnarmoredXp(serverPlayer, source, preArmorDamage);
            // ...and its payoff half. Outside the dispatch below for the same reason, and it must
            // stay outside: a Dodge that halves the hit does not make the attacker any less punched,
            // and gating the sting on losing the dodge roll would make the sub-skill fire on a
            // condition the player cannot see.
            maybeProcessThornySkin(serverPlayer, source);
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

    // --- Unarmored: the pre-armor damage reading -------------------------------------------------

    /**
     * One entity's incoming damage as it was <em>before</em> vanilla's armor mitigation, together
     * with the identities it was captured against.
     *
     * <p>Both identities are held so the consumer can refuse a reading that is not demonstrably the
     * one it is looking at. Cheap insurance against the only thing that could break the join — some
     * other mod, or a future vanilla refactor, calling {@code applyArmorToDamage} somewhere that is
     * not immediately followed by {@code modifyAppliedDamage} on the same hit.
     */
    private record PreArmorDamage(LivingEntity entity, DamageSource source, float amount) {
    }

    /**
     * The most recent pre-armor reading on this thread, set by {@code LivingEntityDamageMixin} and
     * consumed a few bytecodes later by {@link #onModifyAppliedDamage}.
     *
     * <p>Thread-local rather than a field or a map because the whole lifetime of the value is one
     * pair of adjacent calls inside a single {@code applyDamage} frame — the same
     * {@code CombatUtils.IN_MCMMO_DAMAGE} / {@code SmeltingListener.VANILLA_XP_MULTIPLIER} shape
     * this port uses everywhere it has to join two injectors.
     */
    private static final ThreadLocal<PreArmorDamage> PRE_ARMOR_DAMAGE = new ThreadLocal<>();

    /**
     * Stash the damage {@code entity} is about to have its armor applied to. Called from the
     * {@code applyArmorToDamage} HEAD injector; see that method for why the seam exists at all.
     */
    public static void recordPreArmorDamage(LivingEntity entity, DamageSource source, float amount) {
        PRE_ARMOR_DAMAGE.set(new PreArmorDamage(entity, source, amount));
    }

    /**
     * Take the pre-armor reading for this hit, clearing it so it can never be read twice or leak
     * into an unrelated one.
     *
     * @param fallback what to report when no matching reading was captured
     * @return the pre-armor damage, or {@code fallback} if the stash is missing or belongs to some
     *         other entity or damage source
     */
    private static float consumePreArmorDamage(LivingEntity entity, DamageSource source,
            float fallback) {
        final PreArmorDamage stashed = PRE_ARMOR_DAMAGE.get();
        PRE_ARMOR_DAMAGE.remove();
        if (stashed == null || stashed.entity() != entity || stashed.source() != source) {
            // Degrading to the post-armor amount pays *less* XP rather than none, so a broken join
            // shows up as a skill that levels slowly — not one that silently never levels, which is
            // indistinguishable from the feature not being wired at all.
            return fallback;
        }
        return stashed.amount();
    }

    // --- Unarmored: XP ---------------------------------------------------------------------------

    /**
     * Unarmored XP: a player hit while every armor slot is empty is paid for the blow.
     *
     * <p>Three gates, in cost order — the cheap arithmetic first, the config read next, the profile
     * lookup last, because this runs on every hit any player takes.
     *
     * <p>Paid on {@code preArmorDamage} for the reason spelled out on
     * {@code UnarmoredManager#getUnarmoredXp}: Iron Skin is itself armor, so metering the XP after
     * armor would have the skill throttle its own progress exactly when the grind is longest.
     */
    static void maybeAwardUnarmoredXp(ServerPlayerEntity serverPlayer, DamageSource source,
            float preArmorDamage) {
        if (preArmorDamage <= 0 || !isUnarmoredXpSource(serverPlayer, source)) {
            return;
        }
        if (!PlatformLivingEntity.isUnarmored(serverPlayer)) {
            return;
        }
        if (!unarmoredXpUncapped(source.getAttacker())) {
            return; // this attacker has already paid out its share (see the cap below).
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(serverPlayer.getUuid());
        if (mmoPlayer == null) {
            return; // data not loaded (e.g. mid-join).
        }
        final UnarmoredManager unarmored = mmoPlayer.getUnarmoredManager();
        if (unarmored == null) {
            return;
        }
        if (unarmored.onDamageTaken(preArmorDamage) > 0) {
            incrementUnarmoredTracker(source.getAttacker());
        }
    }

    /** Transient per-attacker counter of how many Unarmored XP awards it has paid out. */
    private static final String UNARMORED_TRACKER_KEY = "mcmmo:unarmored_tracker";

    /**
     * Whether {@code attacker} has any Unarmored XP awards left in it.
     *
     * <p><b>This is the gate that decides whether the skill's balance means anything</b>, and
     * {@code Require_Living_Attacker} does not do it: a zombie <em>is</em> a living attacker, so one
     * of them hitting a player through a slab — while a stack of golden carrots regenerates the
     * damage as fast as it lands — is a fully passive ~250 XP/s, which reaches RetroMode 1000 in
     * roughly twelve hours against a design budget of ninety-two. The XP has to come out of a fight
     * the player is actually having.
     *
     * <p>Capping <em>per attacker</em> rather than per second is what preserves the legitimate case.
     * A long, genuinely dangerous fight is a handful of hits from each of several mobs and never
     * comes near the cap; the farm is thousands of hits from the <em>same</em> mob, and it dies at
     * the first hit past the limit. What is left of the exploit — cycling fresh mobs onto yourself
     * fast enough to matter — is a thing a player has to stand there and survive, which is the
     * activity the skill exists to reward.
     *
     * <p>Same shape and the same {@link MetadataStore} mechanism as Agility Dodge's cap, which is
     * legacy's own answer to this exact problem, but keyed on the {@link LivingEntity} attacker
     * rather than on {@code MobEntity}: Dodge only ever paid against mobs, whereas Unarmored pays
     * for any living attacker and would otherwise leave the non-mob ones uncapped.
     *
     * <p>The counter outlives the mob — nothing clears {@link MetadataStore} per entity, only
     * {@code clearAll} at server stop — so the store grows by one small entry per distinct attacker
     * for the length of a session. That is the same (deliberate, documented) trade Dodge makes: it
     * is a few dozen bytes per mob, and expiring the counter would make the cap <em>weaker</em>
     * rather than stronger. Legacy scheduled a one-minute cleanup task; not porting it leaves a
     * strictly tighter gate, which is the direction to err in for an anti-farm measure.
     *
     * @param attacker the damage source's attacker; {@code null} when the living-attacker gate is
     *                 switched off, in which case there is nothing to key a counter on and the cap
     *                 cannot apply
     */
    private static boolean unarmoredXpUncapped(Entity attacker) {
        final int max = McMMOMod.getExperienceConfig().getUnarmoredMaxAwardsPerAttacker();
        if (max <= 0 || attacker == null) {
            return true; // cap disabled, or no attacker identity to count against.
        }
        final Integer count = MetadataStore.get(attacker, UNARMORED_TRACKER_KEY, Integer.class);
        return count == null || count < max;
    }

    /** Bump the per-attacker Unarmored XP counter after an award that actually paid. */
    private static void incrementUnarmoredTracker(Entity attacker) {
        if (attacker == null
                || McMMOMod.getExperienceConfig().getUnarmoredMaxAwardsPerAttacker() <= 0) {
            return;
        }
        final Integer count = MetadataStore.get(attacker, UNARMORED_TRACKER_KEY, Integer.class);
        MetadataStore.set(attacker, UNARMORED_TRACKER_KEY, count == null ? 1 : count + 1);
    }

    // --- Unarmored: Thorny Skin ------------------------------------------------------------------

    /**
     * Unarmored Thorny Skin: something that punched a bare-skinned player gets a little of it back.
     *
     * <p><b>Melee only, and the gate that achieves that is the same one Counter Attack uses</b> —
     * requiring the <em>direct</em> damager ({@code getSource()}, not {@code getAttacker()}) to be a
     * living entity. A skeleton's arrow, a ghast's fireball and a Blast Mining charge all arrive with
     * a non-living direct source, so they are excluded by construction rather than by an
     * ever-lengthening list of damage types to skip. Fall, fire, cactus and drowning have no direct
     * damager at all and fall out of the same test.
     *
     * <p><b>No proc roll and no notification, both deliberate.</b> The reflect is capped at half a
     * heart, so it does not need a chance gate to stay fair — and a sub-skill that fires on every hit
     * would spam the action bar into uselessness if it announced itself. The player sees it as the
     * attacker taking chip damage, which is what it is. (Counter Attack messages because it is a
     * <em>roll</em>: telling the player their gamble paid off is the whole point.)
     *
     * <p>No {@code canCombatSkillsTrigger} check, unlike Counter Attack: Unarmored is a
     * {@code MISC_SKILLS} defensive skill and deliberately not in {@code COMBAT_SKILLS}, so the
     * {@code Enabled_For_PVE} / {@code Enabled_For_PVP} switches do not address it. Consulting them
     * anyway would make an unrelated config toggle silently disable a defensive passive.
     *
     * <p>Recursion is closed upstream rather than here: {@link CombatUtils#safeDealDamage} refuses to
     * run inside an mcMMO-dealt hit, and {@link #onModifyAppliedDamage} turns such hits away before
     * this branch is reached. Two unarmored players could otherwise sting each other forever.
     */
    private static void maybeProcessThornySkin(ServerPlayerEntity serverPlayer, DamageSource source) {
        // The *direct* damager: a projectile's shooter is not standing close enough to be stung.
        if (!(source.getSource() instanceof LivingEntity assailant) || assailant == serverPlayer) {
            return;
        }
        if (!PlatformLivingEntity.isUnarmored(serverPlayer)) {
            return;
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(serverPlayer.getUuid());
        if (mmoPlayer == null) {
            return;
        }
        final UnarmoredManager unarmored = mmoPlayer.getUnarmoredManager();
        if (unarmored == null || !unarmored.thornsReady(true)) {
            return;
        }
        CombatUtils.safeDealDamage(assailant, unarmored.getThornsDamage(true), serverPlayer);
    }

    /**
     * Whether this hit is the kind Unarmored is willing to pay for — the skill's one real exploit
     * gate ({@code ExploitFix.Unarmored.Require_Living_Attacker}, on by default).
     *
     * <p>"XP for taking damage" is otherwise the most passive farm in the mod: stand in a cactus, a
     * fire or a berry bush with a stack of food and level up while doing something else. Requiring a
     * living attacker means the XP has to come out of an actual fight, which is the thing the skill
     * exists to reward.
     *
     * <p><b>The attacker must also not be the victim.</b> That clause is not decoration: a player is
     * a {@link LivingEntity}, so without it their own primed TNT — and, worse, their own Blast Mining
     * charge, which is a repeatable mining loop that Demolitions Expertise exists to make survivable
     * — would read as a living attacker and pay full XP for blowing yourself up on purpose.
     */
    private static boolean isUnarmoredXpSource(ServerPlayerEntity victim, DamageSource source) {
        if (!McMMOMod.getExperienceConfig().isUnarmoredLivingAttackerRequired()) {
            return true; // gate off: every damage cause pays (play-testing / diagnosis only).
        }
        final Entity attacker = source.getAttacker();
        return attacker instanceof LivingEntity && attacker != victim;
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
    /**
     * Whether this target is a decoration rather than a fight — an armour stand or a mannequin
     * ({@code ExploitFix.PreventArmorStandInteraction} / {@code PreventMannequinInteraction}, legacy
     * {@code EntityListener#onEntityDamageByEntity}'s two skips).
     *
     * <p>Both are {@link LivingEntity}s that stand still and never fight back, so without this every
     * combat skill would train on one: an armour stand in a hole is an XP source that needs no food,
     * no armour and no attention. <b>A mannequin is the newer and worse of the two</b> — this port had
     * no handling for it at all, and it is a {@code PlayerLikeEntity}, so it reaches the combat paths
     * looking far more like a real opponent than an armour stand does.
     *
     * <h2>🔑 The mannequin is matched by registry id, the armour stand by {@code instanceof}</h2>
     * Not an inconsistency. The mannequin does not exist in every Minecraft version this mod
     * supports, so {@code net.minecraft.entity.decoration.MannequinEntity} is not always a class that
     * can be named — and where it cannot, {@code instanceof MannequinEntity} is a <b>compile error</b>
     * that takes the build down rather than a check that quietly answers false. An id keeps the
     * question answerable everywhere: on a version with no mannequin nothing is registered under that
     * id, so nothing matches, which is the right answer. The armour stand has shipped for a decade
     * and needs no such care. Same reasoning as {@code HunterListener}'s golem exclusion.
     *
     * <p>Replaces five hard-coded {@code instanceof ArmorStandEntity} checks that were spread across
     * the damage paths. The mechanic was right and the <em>config key was never read</em> — turning
     * {@code PreventArmorStandInteraction} off did nothing whatever, which is the defect GitHub #9's
     * audit exists to find. One predicate means the next combat path added gets both entity types and
     * both switches for free, instead of a sixth copy that remembers only the armour stand.
     *
     * <p>Fails closed when no config is loaded: excluded, matching the shipped default of both keys.
     */
    private static boolean isTargetDummy(LivingEntity target) {
        final ExperienceConfig config = McMMOMod.getExperienceConfig();
        if (target instanceof ArmorStandEntity) {
            return config == null || config.isArmorStandInteractionPrevented();
        }
        if (MANNEQUIN_ID.equals(Registries.ENTITY_TYPE.getId(target.getType()))) {
            return config == null || config.isMannequinInteractionPrevented();
        }
        return false;
    }

    /**
     * The mannequin's registry id — see {@link #isTargetDummy} for why this is an id and not a class.
     *
     * <p>Compared as an {@link Identifier} rather than as a bare path string: {@code "mannequin"} on
     * its own would also match another mod's {@code othermod:mannequin}, which is not the vanilla
     * decoration this rule is about. Resolved the safe direction — entity to id, never id to entity —
     * so the defaulted-registry trap that once turned every unknown mob into a {@code PIG} cannot
     * apply here.
     */
    private static final Identifier MANNEQUIN_ID = Identifier.ofVanilla("mannequin");

    /**
     * Call of the Wild's {@code attackTarget}: a ranged hit points the shooter's nearby pets at
     * whatever they just struck.
     *
     * <h2>Why this is its own method, and not the block it used to be</h2>
     * It lived inside {@link #applyProjectileAttackBonus}, and it inherited two of that method's
     * early returns as bugs rather than as rules — neither of which had anything to do with siccing
     * pets:
     * <ul>
     *   <li><b>A thrown trident sicced nothing.</b> The trident arm returns to
     *       {@code applyTridentImpale} <em>before</em> the sic was reached, so throwing a trident —
     *       unambiguously a ranged weapon — left the pack standing there. Reordering the block would
     *       have fixed this one case and left the shape that caused it.</li>
     *   <li><b>Nothing but arrows and tridents reached the code at all.</b> That method opens on
     *       {@code instanceof PersistentProjectileEntity}, correctly, because it computes Archery,
     *       Crossbows and Impale bonuses and those only exist for arrows and tridents. A snowball,
     *       an egg, a splash potion or a fired firework is invisible to it — and each of those is
     *       still the player hitting a mob from a distance.</li>
     * </ul>
     *
     * <p>So the fix is separation, not reordering: the damage-bonus method keeps its narrow
     * projectile type because its <em>maths</em> needs one, and the sic asks the only question it
     * actually cares about — <b>did this player hit that mob with something they threw or fired?</b>
     * {@code ProjectileEntity} is the common ancestor of arrows, tridents, snowballs, eggs, thrown
     * potions and firework rockets (verified against the merged jar).
     *
     * <p>Two conditions carried over unchanged, both deliberate:
     * <ul>
     *   <li><b>The creeper skip.</b> Sending the pack at a creeper is sending the pack to be blown
     *       up, next to its owner. Vanilla's own {@code canAttackWithOwner} makes the same refusal.</li>
     *   <li><b>{@code isTargetDummy} first.</b> An armour stand or a mannequin is not a fight.</li>
     * </ul>
     *
     * <p>No Taming rank gate and no {@code McMMOPlayer} lookup: legacy gated this on a permission
     * that is always granted in singleplayer, so a resolved shooter is the whole condition — which
     * also means a pet still answers a shot fired during the window before a profile loads.
     */
    private static void sicPetsOnRangedHit(LivingEntity target, DamageSource source) {
        if (!(source.getSource() instanceof ProjectileEntity projectile)) {
            return; // A melee hit, or environmental damage.
        }
        if (!(projectile.getOwner() instanceof ServerPlayerEntity shooter)) {
            return; // A dispenser, a skeleton, or a wild projectile — nobody's pets to sic.
        }
        if (isTargetDummy(target) || target instanceof CreeperEntity) {
            return;
        }
        CallOfTheWildHandler.attackTarget(shooter, target);
    }

    private static float applyProjectileAttackBonus(LivingEntity target, DamageSource source,
            float amount) {
        if (!(source.getSource() instanceof PersistentProjectileEntity projectile)) {
            return amount; // not a projectile hit.
        }
        if (!(projectile.getOwner() instanceof ServerPlayerEntity shooter)) {
            return amount; // wild/dispenser projectile, or not fired by this player.
        }
        if (isTargetDummy(target)) {
            return amount;
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
        // Limit Break is added AFTER Skill Shot, because Skill Shot replaces the damage rather than
        // adding to it — ahead of it the bonus would simply be discarded. Legacy's ordering, and
        // unlike the melee arms this one is NOT scaled by attack strength: an arrow already in
        // flight has no swing left to charge. Same asymmetry the ranged Impale arm below preserves.
        boostedDamage += LimitBreak.bonusDamage(mmoPlayer,
                SubSkillType.ARCHERY_ARCHERY_LIMIT_BREAK);
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
        // After Powered Shot for the same reason as Archery's, and unscaled for the same reason.
        boostedDamage += LimitBreak.bonusDamage(mmoPlayer,
                SubSkillType.CROSSBOWS_CROSSBOWS_LIMIT_BREAK);
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
        // Unscaled here but attack-strength-scaled on the melee path — that split is legacy's own
        // (processTridentCombatRanged vs processTridentCombatMelee) and is the same reason Impale
        // itself is scaled in MeleeDamageBonus and flat here.
        boostedDamage += LimitBreak.bonusDamage(mmoPlayer,
                SubSkillType.TRIDENTS_TRIDENTS_LIMIT_BREAK);
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
        if (isTargetDummy(target)) {
            return amount;
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
        // Legacy's COMBAT-path super-ability activation. processSwordCombat / processAxeCombat /
        // processUnarmedCombat each open with `if (manager.canActivateAbility())
        // mmoPlayer.checkAbilityActivation(<skill>)`, which is what flips a *readied* tool into an
        // *active* super ability when the player strikes a mob rather than a block. Without it
        // Serrated Strikes and Skull Splitter can never activate at all — they have no block path,
        // unlike the five block-struck abilities in SuperAbilityListener#onAttackBlock.
        //
        // Position is legacy's and load-bearing: activation runs BEFORE the damage bonus, so the
        // activating hit is itself buffed (Berserk scales the very swing that turned it on) and is
        // itself eligible for the AoE arms below.
        maybeActivateSuperAbility(mmoPlayer, weapon);

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
        } else if (weapon == MeleeWeapon.SPEAR) {
            maybeProcessMomentum(mmoPlayer);
        }

        // Per-hit combat XP, paid on the *boosted* damage — legacy ends every processXCombat with
        // this, after event.setDamage(boostedDamage), and its health-diff measured what actually
        // landed. No multiplier on the melee path (legacy's 3-arg processCombatXP overload).
        CombatUtils.processCombatXP(mmoPlayer, target, skillOf(weapon), boostedDamage);
        return boostedDamage;
    }

    /**
     * Agility <b>Smash</b>: a sprinting player's melee hit can land extra damage and heavy knockback.
     *
     * <p>Shares the melee seam with {@link #applyAttackerWeaponBonus} but is gated differently on
     * purpose. The weapon arm bails out for {@code MeleeWeapon.OTHER}, because a pickaxe has no
     * Swords bonus; Smash has nothing to do with what is being held — the sub-skill is "you hit hard
     * because you were running" — so it fires with a torch, a block, or an empty hand.
     *
     * <p>Runs <em>after</em> the weapon arm, which means the weapon skill's per-hit combat XP is paid
     * on the pre-Smash damage. That is the intended attribution: the extra damage came from Agility,
     * so it should not inflate the Swords XP for the same swing.
     *
     * <p>Deliberately no interaction with vanilla's own sprint-attack knockback — this stacks on top,
     * which is exactly what the sub-skill is for.
     */
    private static float applySprintSmash(LivingEntity target, DamageSource source, float amount) {
        if (!(source.getAttacker() instanceof ServerPlayerEntity attacker)) {
            return amount;
        }
        // Direct melee only, same test as the weapon arm: a projectile's direct source is the
        // projectile, and Thorns is not a swing.
        if (source.getSource() != attacker || source.isOf(DamageTypes.THORNS)) {
            return amount;
        }
        if (!attacker.isSprinting() || isTargetDummy(target)) {
            return amount;
        }

        final McMMOPlayer mmoPlayer = UserManager.getPlayer(attacker.getUuid());
        if (mmoPlayer == null) {
            return amount;
        }
        final MovementManager agility = mmoPlayer.getMovementManager();
        if (agility == null || !agility.rollSmash()) {
            return amount;
        }

        final double knockback = agility.getSmashKnockback();
        if (knockback > 0) {
            // Away from the attacker: takeKnockback's x/z are the vector *from* the source, and it
            // negates them internally, so pass the attacker-to-target direction as-is.
            target.takeKnockback(knockback,
                    attacker.getX() - target.getX(), attacker.getZ() - target.getZ());
        }
        NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_MESSAGE,
                "Parkour.SubSkill.Smash.Proc");
        return amount + (float) agility.getSmashBonusDamage();
    }

    /**
     * Server tick at which each player last took damage — Assassin's "before taking damage" window
     * (D-S3).
     *
     * <p>A side table rather than an entity field because respawning and leaving the End both
     * construct a <em>new</em> {@code ServerPlayerEntity}, and the window should survive neither of
     * those as entity state nor be lost by them. Keyed by UUID, which does survive both.
     */
    private static final Map<UUID, Integer> LAST_DAMAGED_TICK = new HashMap<>();

    /** Stamp the current server tick as this player's most recent damage. */
    static void recordDamageTaken(@NotNull ServerPlayerEntity player) {
        final MinecraftServer server = player.getEntityWorld().getServer();
        if (server != null) {
            LAST_DAMAGED_TICK.put(player.getUuid(), server.getTicks());
        }
    }

    /**
     * Ticks since this player last took damage, or {@link Long#MAX_VALUE} if they have not been hit
     * this session.
     *
     * <p>{@code MinecraftServer#getTicks()} is the clock rather than {@code World#getTimeOfDay()},
     * which {@code /time set} moves backwards — that would hand a player a permanent backstab, or
     * withhold one for a whole in-game day, depending on which way they set it.
     */
    static long ticksSinceDamageTaken(@NotNull ServerPlayerEntity player) {
        final Integer last = LAST_DAMAGED_TICK.get(player.getUuid());
        if (last == null) {
            return Long.MAX_VALUE;
        }
        final MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) {
            return Long.MAX_VALUE;
        }
        // Clamped at zero: getTicks() is an int and wraps after ~3.4 years of uptime. A negative
        // window would silently disable the sub-skill rather than merely mistiming it once.
        return Math.max(0L, (long) server.getTicks() - last);
    }

    /** Drop the Assassin damage-recency window for a player who has left. */
    public static void forgetPlayer(@NotNull UUID uuid) {
        LAST_DAMAGED_TICK.remove(uuid);
    }

    /** Drop every player's damage-recency window (server stop). */
    public static void clear() {
        LAST_DAMAGED_TICK.clear();
        // Belt-and-braces, not a fix for a known leak: the pre-armor stash is cleared on every read,
        // so the only way one survives is a hit whose applyArmorToDamage ran and whose
        // modifyAppliedDamage did not. That would strand a single entity + damage source reference
        // on the server thread, which in singleplayer outlives the world the player just left.
        PRE_ARMOR_DAMAGE.remove();
    }

    /**
     * Stealth <b>Assassin</b>: a melee hit thrown while crouched, by someone who has not been hit
     * recently, is a backstab and lands for a multiple of its normal damage.
     *
     * <p>Gated like {@link #applySprintSmash} and for the same reason — the sub-skill is "you struck
     * from the shadows", not "you struck with a sword" — so it fires with whatever is in hand,
     * including nothing.
     *
     * <p><b>Multiplicative, and applied to the running total</b>, so it scales the weapon skill's
     * on-hit bonus and Smash along with the base swing. That is deliberate (a backstab multiplies
     * the whole blow) and it is also the most likely thing in this skill to be over-tuned: it
     * compounds with vanilla critical hits too. Flagged for play-testing against an armoured mob.
     *
     * <p>The recency half of the gate is what stops it being a free permanent damage buff for anyone
     * willing to fight crouched: take a single hit and it is off for
     * {@code NoDamageWindowTicks}. Per-hit combat XP is not re-paid here — the weapon arm already
     * paid it on the pre-Assassin damage, and the extra came from Stealth, not from the weapon.
     */
    static float applyAssassin(LivingEntity target, DamageSource source, float amount) {
        if (!(source.getAttacker() instanceof ServerPlayerEntity attacker)) {
            return amount;
        }
        // Direct melee only, same test as the weapon arm: a projectile's direct source is the
        // projectile, and Thorns is not a swing.
        if (source.getSource() != attacker || source.isOf(DamageTypes.THORNS)) {
            return amount;
        }
        if (!attacker.isSneaking() || isTargetDummy(target)) {
            return amount;
        }

        final McMMOPlayer mmoPlayer = UserManager.getPlayer(attacker.getUuid());
        if (mmoPlayer == null) {
            return amount;
        }
        final StealthManager stealth = mmoPlayer.getStealthManager();
        if (stealth == null
                || !stealth.assassinReady(true, ticksSinceDamageTaken(attacker))) {
            return amount;
        }

        NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_MESSAGE,
                "Stealth.SubSkill.Assassin.Proc");
        return (float) (amount * stealth.getAssassinDamageMultiplier());
    }

    /**
     * Hunter <b>Mob Mastery</b>: flat bonus damage against a creature this player has personally
     * killed enough of (stage 4, D-HU3/D-HU4).
     *
     * <p>Third sibling of {@link #applySprintSmash} and {@link #applyAssassin} on this seam, and a
     * sibling rather than a new arm of {@code MeleeDamageBonus} for the reason those two are: that
     * class dispatches on the <em>weapon in hand</em>, and mastery has nothing to do with what you are
     * holding. It is in fact the only bonus here keyed on the <b>target's identity</b> — the same
     * creature is worth the same bonus to a fist as to a netherite sword, which is why the flat figure
     * is deliberately small (a bare fist at tier 3 hits for 4.0, four times its base).
     *
     * <h2>Which hits qualify</h2>
     * Melee swings and the player's own projectiles — arrows, bolts, thrown tridents — mirroring
     * exactly the two K1 arms above that already accept them, and no wider. Explicitly excluded:
     * <ul>
     *   <li><b>The wolf-bite arm.</b> A wolf's hit is credited to the wolf, so gate 1 drops it for
     *       free; Taming's Sharpened Claws and Gore already own that damage and adding Hunter would
     *       double-dip on one bite.</li>
     *   <li><b>Everything else a player can be blamed for</b> — their lit TNT, a splash potion, a
     *       Blast Mining charge. Each is attributable to the player and none is a hunt; a flat bonus
     *       on every tick of a lingering cloud is an exploit wearing a sub-skill's name.</li>
     *   <li><b>Thorns</b>, for the same reason Smash and Assassin refuse it: being punched is not
     *       swinging.</li>
     * </ul>
     *
     * <p><b>Spawn origin is deliberately NOT re-checked here.</b> Stage 1's marker gates what a kill
     * is <em>worth</em>, not what a hit is worth: a spawner zombie is still a zombie, and knowledge
     * earned in the wild does not evaporate when the next one arrives on a mineshaft floor. Refusing
     * the bonus there would close no farm — the farm earns no mastery either way — while making the
     * damage a player sees depend on an invisible property of the mob they are hitting.
     *
     * <p>No notification and no XP. A message on every qualifying swing would be spam at exactly the
     * moment the player is busiest; the milestone is announced once, when it is crossed
     * ({@code HunterListener}). And no combat XP is re-paid, matching Smash and Assassin — the weapon
     * arm already paid it on the pre-bonus figure, and this damage came from Hunter, not the weapon.
     */
    static float applyHunterMastery(LivingEntity target, DamageSource source, float amount) {
        // Gate 1, and the same one HunterListener opens with: the hit has to be the player's. For a
        // projectile getAttacker() resolves back to the shooter, so this admits both halves at once
        // — and keeping the two entry conditions identical is what stops "the kill counted" and "the
        // bonus applied" drifting apart.
        if (!(source.getAttacker() instanceof ServerPlayerEntity attacker)) {
            return amount;
        }
        if (isTargetDummy(target)) {
            return amount;
        }

        // Melee is the direct-source test the weapon arm, Smash and Assassin all use; Thorns is
        // credited to the wearer but is not a swing.
        final boolean melee = source.getSource() == attacker && !source.isOf(DamageTypes.THORNS);
        if (!melee && !isProjectileFrom(source, attacker)) {
            return amount;
        }
        if (!CombatUtils.canCombatSkillsTrigger(PrimarySkillType.HUNTER, target)) {
            return amount;
        }

        final McMMOPlayer mmoPlayer = UserManager.getPlayer(attacker.getUuid());
        if (mmoPlayer == null) {
            return amount; // data not loaded (e.g. mid-join).
        }
        final HunterManager hunter = mmoPlayer.getHunterManager();
        if (hunter == null) {
            return amount;
        }

        final double bonus = hunter.masteryDamageBonusForHit(
                HunterListener.masteryKeyOf(target), melee);
        return bonus <= 0 ? amount : amount + (float) bonus;
    }

    /**
     * Whether this damage was delivered by a projectile {@code attacker} fired themselves.
     *
     * <p>Owner identity rather than a bare {@code instanceof ServerPlayerEntity}: the responsible
     * entity has already been resolved, and requiring the two to agree means a projectile whose
     * attribution and ownership disagree (a mod re-crediting a shot mid-flight) contributes nothing
     * rather than paying the wrong player's mastery.
     */
    private static boolean isProjectileFrom(DamageSource source, ServerPlayerEntity attacker) {
        return source.getSource() instanceof PersistentProjectileEntity projectile
                && projectile.getOwner() == attacker;
    }

    /**
     * The combat half of the super-ability activation trigger: a strike on a living entity with a
     * readied sword / axe / fist activates Serrated Strikes / Skull Splitter / Berserk. Ports the
     * {@code canActivateAbility()} guard that opens legacy's {@code processSwordCombat},
     * {@code processAxeCombat} and {@code processUnarmedCombat}.
     *
     * <p>The block half lives in {@code SuperAbilityListener#onAttackBlock} and covers the five
     * abilities that are struck onto a block (Green Terra, Tree Feller, Super Breaker, Giga Drill
     * Breaker, Berserk). Berserk is deliberately in <em>both</em>: legacy activates Unarmed off a
     * punched mob as well as a punched block, and {@code checkAbilityActivation} is idempotent — it
     * returns immediately when the ability is already running.
     *
     * <p>Only these three skills have a combat activation upstream. Maces and Tridents have no super
     * ability, and Archery/Crossbows are not melee.
     *
     * <p>Package-private rather than private so the weapon→skill dispatch can be unit-tested MC-free
     * (both parameters are MC-free). Note what that test can and cannot prove: it pins the mapping and
     * the {@code canActivateAbility()} gate, but <b>not</b> that {@code applyAttackerWeaponBonus} still
     * calls this — that call site is what was missing in the first place, and confirming it needs a
     * real swing (§G session 2, items SS/SK).
     */
    static void maybeActivateSuperAbility(McMMOPlayer mmoPlayer, MeleeWeapon weapon) {
        switch (weapon) {
            case SWORD -> {
                final SwordsManager swords = mmoPlayer.getSwordsManager();
                if (swords != null && swords.canActivateAbility()) {
                    mmoPlayer.checkAbilityActivation(PrimarySkillType.SWORDS);
                }
            }
            case AXE -> {
                final AxesManager axes = mmoPlayer.getAxesManager();
                if (axes != null && axes.canActivateAbility()) {
                    mmoPlayer.checkAbilityActivation(PrimarySkillType.AXES);
                }
            }
            case UNARMED -> {
                final UnarmedManager unarmed = mmoPlayer.getUnarmedManager();
                if (unarmed != null && unarmed.canActivateAbility()) {
                    mmoPlayer.checkAbilityActivation(PrimarySkillType.UNARMED);
                }
            }
            case MACE, TRIDENT, SPEAR, OTHER -> {
                // No super ability on these skills — legacy has no activation call in their combat
                // paths either. SPEARS_SUPER_ABILITY exists as a registered SuperAbilityType constant
                // but is a placeholder with no behaviour, exactly like the Maces and Tridents ones.
            }
        }
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
     * Spears <b>Momentum</b>: a spear hit may grant the attacker a short Speed burst. Ports the
     * {@code spearsManager.potentiallyApplyMomentum()} line that closes legacy
     * {@code CombatUtils#processSpearsCombat}, just before its combat XP.
     *
     * <p>Unlike Cripple there is no survival check and no target argument at all — Momentum buffs the
     * player who swung, so killing the mob with the same hit does not cancel it. That asymmetry is
     * legacy's: {@code processCripple(target)} is guarded by
     * {@code target.getHealth() - getFinalDamage() > 0}, {@code potentiallyApplyMomentum()} is not
     * guarded by anything.
     */
    private static void maybeProcessMomentum(McMMOPlayer mmoPlayer) {
        final SpearsManager spears = mmoPlayer.getSpearsManager();
        if (spears == null) {
            return;
        }
        spears.processMomentum(mmoPlayer.getAttackStrength());
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
            case SPEAR -> PrimarySkillType.SPEARS;
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
     *
     * <p><b>Spears dispatch off the held item here, where legacy dispatched off the damage type</b>
     * ({@code event.getDamageSource().getDamageType() == "spear"}). The two are the same test in
     * 1.21.11: {@code Item.Settings.spear(…)} stamps every one of the seven vanilla spears with
     * {@code DataComponentTypes.DAMAGE_TYPE = DamageTypes.SPEAR}, and {@code PlayerEntity#attack}
     * builds its source through {@code ItemStack#getDamageSource(weaponStack)}, so a spear swing is
     * spear-typed <em>because</em> a spear is held (all bytecode-verified). Keying on the item keeps
     * every arm of this chain the same shape.
     *
     * <p>⚠️ This arm was missing until GitHub #7, on the belief — written into the comment that used
     * to sit here, and into the wiki — that neither the {@code spear} damage type nor any spear item
     * existed in 1.21.11 and that a Spears arm would therefore be dead code. Both exist:
     * {@code Items.WOODEN_SPEAR}…{@code NETHERITE_SPEAR}, the {@code minecraft:spears} item tag, and
     * {@code data/minecraft/damage_type/spear.json} all ship in the merged jar. The belief was true of
     * an earlier MC version and was never re-checked, so a spear classified as {@code OTHER} and the
     * whole skill — XP included — paid nothing.
     */
    @VisibleForTesting
    static MeleeWeapon classifyMainHand(ItemStack held) {
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
        if (ItemUtils.isSpear(held)) {
            return MeleeWeapon.SPEAR;
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
        final MovementManager agility = mmoPlayer.getMovementManager();
        if (agility == null) {
            return amount;
        }

        // The manager awards XP + tracks the landing block internally; it hands back the outcome so we
        // can apply the damage reduction + feedback (the MC-typed half) here.
        final RollResult result = agility.processFallDamage(amount);
        if (result == null || !result.isRollSuccess()) {
            return amount;
        }

        NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_MESSAGE,
                // Moved off the retired Agility root 2026-08-17. Roll is a PARKOUR sub-skill and
                // these two are LITERALS -- no enum-derived check would have caught a stale root.
                result.isGraceful()
                        ? "Parkour.SubSkill.GracefulRoll.Proc"
                        : "Parkour.SubSkill.Roll.Proc");
        SoundManager.sendCategorizedSound(mmoPlayer.getPlayer(), SoundType.ROLL_ACTIVATED,
                PlatformSoundCategory.PLAYERS, 0.5F);
        return (float) result.getModifiedDamage();
    }

    /** Transient per-mob counter of how many dodge-XP awards it has handed out (anti-farm cap). */
    private static final String DODGE_TRACKER_KEY = "mcmmo:dodge_tracker";
    /** Legacy cap: a single mob only pays out dodge XP six times (count 0..5 inclusive). */
    private static final int DODGE_XP_MAX_AWARDS = 5;

    /**
     * K1 defender branch: a player taking a hit from an entity may Dodge, reducing the damage and
     * (against an eligible mob) gaining Agility XP. Mirrors legacy
     * {@code CombatUtils.processCombatAttack}'s dodge path. (The attacker-side melee weapon bonuses
     * are handled separately in {@link #applyAttackerWeaponBonus}.)
     */
    private static float handleDodge(ServerPlayerEntity serverPlayer, Entity attacker, float amount) {
        // Lightning dodge can be excluded by config (legacy Agility.dodgeLightningDisabled).
        if (attacker instanceof LightningEntity
                && McMMOMod.getGeneralConfig().getDodgeLightningDisabled()) {
            return amount;
        }

        final McMMOPlayer mmoPlayer = UserManager.getPlayer(serverPlayer.getUuid());
        if (mmoPlayer == null) {
            return amount;
        }
        final MovementManager agility = mmoPlayer.getMovementManager();
        if (agility == null) {
            return amount;
        }

        // Only mobs grant dodge XP, and only up to the per-mob cap; the manager still reduces damage
        // when the attacker is XP-ineligible, it just pays nothing.
        final boolean xpEligible = attacker instanceof MobEntity && dodgeXpUncapped((MobEntity) attacker);

        final DodgeResult result = agility.processDodge(amount, xpEligible);
        if (result == null) {
            return amount; // no dodge — leave the hit untouched.
        }

        if (result.getXpGain() > 0) {
            incrementDodgeTracker((MobEntity) attacker);
        }
        if (mmoPlayer.useChatNotifications()) {
            NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_MESSAGE,
                    "Parkour.Combat.Proc");
        }
        ParticleEffectUtils.playDodgeEffect(mmoPlayer.getPlayer());
        // PORT: legacy also scheduled MobDodgeMetaCleanup to expire the tracker after a minute. That
        // is a refinement — without it the transient tracker just persists for the mob's session
        // lifetime, which is a stricter (still correct) anti-farm cap. Deferred.
        return (float) result.getModifiedDamage();
    }

    /** Whether {@code mob} has not yet hit the dodge-XP award cap. */
    private static boolean dodgeXpUncapped(MobEntity mob) {
        if (!McMMOMod.getExperienceConfig().isMovementExploitingPrevented()) {
            return true; // exploit prevention off → uncapped.
        }
        final Integer count = MetadataStore.get(mob, DODGE_TRACKER_KEY, Integer.class);
        return count == null || count <= DODGE_XP_MAX_AWARDS;
    }

    /** Bump the per-mob dodge-XP counter after a successful, XP-paying dodge. */
    private static void incrementDodgeTracker(MobEntity mob) {
        if (!McMMOMod.getExperienceConfig().isMovementExploitingPrevented()) {
            return;
        }
        final Integer count = MetadataStore.get(mob, DODGE_TRACKER_KEY, Integer.class);
        MetadataStore.set(mob, DODGE_TRACKER_KEY, count == null ? 1 : count + 1);
    }
}
