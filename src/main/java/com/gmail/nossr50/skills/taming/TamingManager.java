package com.gmail.nossr50.skills.taming;

import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformLivingEntity;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Taming skill manager (Phase 10.3 port, legacy 572 lines). Taming is overwhelmingly an
 * entity/summon skill, so its portable numeric core is thin: the sub-skill rank+permission gates and
 * the pure damage-modifier math (Gore, Sharpened Claws, Thick Fur, Shock Proof) survive as pure
 * functions, plus the Beast Lore horse-jump-strength conversion extracted from the display path.
 * Every body that touches a live {@code Wolf}/{@code Horse}/{@code LivingEntity}, spawns a summon,
 * mutates entity health/velocity, or reads the transient-entity tracker is deferred until the entity
 * adapters and Phase 11 scheduler land — same convention as
 * {@link com.gmail.nossr50.skills.fishing.FishingManager} and
 * {@link com.gmail.nossr50.skills.herbalism.HerbalismManager}.
 *
 * <p>Unlike {@code FishingManager}, this constructor performs <b>no</b> eager config read, so it
 * needs no {@code McMMOPlayerTest} fixture change: the legacy {@code init()} only primed the summon
 * timestamp and the transient-entity tracker/static COTW caches, all of which live on the deferred
 * summon path.
 *
 * <p><b>Deferred until the entity/item adapters + Phase 11 scheduler land (PORT Phase 10/11):</b>
 * <ul>
 *   <li>{@code awardTamingXP} — needs the {@code LivingEntity} target, the internal
 *       {@code McMMOPlayerTameEntityEvent} on the new event bus, and
 *       {@code ExperienceConfig.getTamingXP(entityConfigString)};</li>
 *   <li>{@code processEnvironmentallyAware}/{@code pummel}/{@code attackTarget}/{@code beastLore} —
 *       need an entity-teleport adapter (Environmentally Aware), a velocity-from-a-non-player-source
 *       adapter plus particles (Pummel), a nearby-entity target sweep ({@code attackTarget}), and a
 *       right-click-entity hook ({@code beastLore});</li>
 *   <li>the whole Call of the Wild summon path ({@code summonOcelot}/{@code summonWolf}/
 *       {@code summonHorse}/{@code processCallOfTheWild}/{@code spawnCOTWEntity}/{@code isCOTWItem}/
 *       {@code cleanupAllSummons}) — needs the entity-spawn adapter, the transient-entity tracker,
 *       the {@code CallOfTheWildType}/{@code TamingSummon} datatypes, and {@code Permissions.callOfTheWild};</li>
 *   <li>{@code processThickFurFire} — the fire-extinguish half is MC-typed glue and lives on the
 *       listener, since it mutates the wolf rather than computing anything.</li>
 * </ul>
 *
 * <p>The damage-modifier bodies are now driven by {@code fabric.listeners.EntityDamageListener} on
 * the K1 seam: Gore/Sharpened Claws/Fast Food Service on the wolf-<em>attacker</em> arm (legacy
 * {@code CombatUtils#processTamingCombat}), and Thick Fur/Shock Proof/Holy Hound on the
 * wolf-<em>defender</em> arm (legacy {@code EntityListener#onEntityDamage}'s {@code Tameable} switch).
 */
public class TamingManager extends SkillManager {

    public TamingManager(@NotNull McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.TAMING);
    }

    /**
     * Award Taming XP for taming an animal (legacy {@code awardTamingXP(LivingEntity)}). The MC-typed
     * caller ({@code fabric.listeners.TamingListener}) resolves the tamed entity's config-entity
     * string (e.g. {@code "Wolf"}); this looks up the per-entity XP and awards it, keeping the manager
     * MC-free.
     *
     * <p>The legacy cancellable {@code McMMOPlayerTameEntityEvent} (which could veto/adjust the award)
     * is dropped — there are no singleplayer listeners for it (K5 EventUtils; no-op with a breadcrumb).
     *
     * @param entityConfigString the config-entity string of the tamed animal
     */
    public void awardTamingXP(@NotNull String entityConfigString) {
        final int xp = McMMOMod.getExperienceConfig().getTamingXP(entityConfigString);
        if (xp <= 0) {
            return; // entity has no configured Taming XP (e.g. tamed a type mcMMO doesn't reward).
        }
        applyXpGain(xp, XPGainReason.PVE, XPGainSource.SELF);
    }

    public boolean canUseThickFur() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.TAMING_THICK_FUR)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.TAMING_THICK_FUR);
    }

    public boolean canUseEnvironmentallyAware() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.TAMING_ENVIRONMENTALLY_AWARE)
                && Permissions.isSubSkillEnabled(getPlayer(),
                SubSkillType.TAMING_ENVIRONMENTALLY_AWARE);
    }

    public boolean canUseShockProof() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.TAMING_SHOCK_PROOF)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.TAMING_SHOCK_PROOF);
    }

    /**
     * Preserves the legacy quirk exactly: Holy Hound is unlocked by the <em>Environmentally Aware</em>
     * rank ladder but gated on the <em>Holy Hound</em> enable node.
     */
    public boolean canUseHolyHound() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.TAMING_ENVIRONMENTALLY_AWARE)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.TAMING_HOLY_HOUND);
    }

    public boolean canUseFastFoodService() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.TAMING_FAST_FOOD_SERVICE)
                && Permissions.isSubSkillEnabled(getPlayer(),
                SubSkillType.TAMING_FAST_FOOD_SERVICE);
    }

    public boolean canUseSharpenedClaws() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.TAMING_SHARPENED_CLAWS)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.TAMING_SHARPENED_CLAWS);
    }

    public boolean canUseGore() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.TAMING_GORE)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.TAMING_GORE);
    }

    public boolean canUseBeastLore() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.TAMING_BEAST_LORE)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.TAMING_BEAST_LORE);
    }

    /**
     * The bonus (extra) damage Gore adds. Legacy {@code gore(target, damage)} — the {@code target}
     * argument is unused (see below), so this is the whole of it: apply the configured Gore modifier
     * and return only the <em>additional</em> damage over the base hit.
     *
     * <p><b>Gore does not roll.</b> Every other proc-shaped sub-skill gates on
     * {@code ProbabilityUtil.isSkillRNGSuccessful}; Gore simply applies whenever it is unlocked, so an
     * owner past the unlock level gets the modifier on <em>every</em> wolf hit. That is faithful:
     * upstream's {@code gore()} is byte-identical to the vendored one (checked against
     * {@code mcMMO-Dev/mcMMO@master} — no roll, no bleed). See CONVERSION_TODO §F for the dead
     * {@code Skills.Taming.Gore.ChanceMax}/{@code MaxBonusLevel} config this leaves stranded, and why
     * inventing the roll here would be a deviation rather than a fix.
     *
     * @param damage the initial (base) damage
     * @return the extra damage Gore contributes on top of {@code damage}
     */
    public double gore(double damage) {
        return (damage * McMMOMod.getAdvancedConfig().getGoreModifier()) - damage;
    }

    /**
     * Fast Food Service: a summoned wolf heals itself for the damage it just dealt. Ports legacy
     * {@code fastFoodService(Wolf, double)} — the RNG gate and the heal, kept MC-free by taking the
     * wolf through {@link PlatformLivingEntity} (the same shape Rupture's DoT uses).
     *
     * <p>Legacy's {@code health < maxHealth} guard is preserved rather than folded into the
     * {@code min}: it keeps a full-health wolf from being written to at all.
     *
     * @param wolf the wolf that landed the hit
     * @param damage the damage the wolf dealt, which it heals for
     */
    public void fastFoodService(@NotNull PlatformLivingEntity wolf, double damage) {
        if (!ProbabilityUtil.isSkillRNGSuccessful(SubSkillType.TAMING_FAST_FOOD_SERVICE,
                mmoPlayer)) {
            return;
        }

        final float health = wolf.getHealth();
        final float maxHealth = wolf.getMaxHealth();
        if (health < maxHealth) {
            wolf.setHealth((float) Math.min(health + damage, maxHealth));
        }
    }

    /**
     * Holy Hound: a wolf is healed by the magic/poison/wither damage that would have hurt it. Ports
     * legacy {@code Taming.processHolyHound(Wolf, double)}.
     *
     * <p>Note there is no RNG gate and no {@code health < maxHealth} guard — the {@code min} clamp is
     * the whole of it, exactly as legacy wrote it. The caller still applies the damage: legacy heals
     * and lets the hit land, so the wolf nets zero rather than being made immune.
     *
     * <p>Dropped: the {@code EntityEffect.WOLF_HEARTS} particle — no particle adapter yet, the same
     * deferral as Dodge, Greater Impact and Rupture's bleed particles.
     *
     * @param wolf the wolf taking the damage
     * @param damage the incoming damage, which is healed back
     */
    public void processHolyHound(@NotNull PlatformLivingEntity wolf, double damage) {
        wolf.setHealth((float) Math.min(wolf.getHealth() + damage, wolf.getMaxHealth()));
    }

    /**
     * @return the flat bonus damage granted by Sharpened Claws
     */
    public double sharpenedClaws() {
        return McMMOMod.getAdvancedConfig().getSharpenedClawsBonus();
    }

    /**
     * Pure damage-reduction math extracted from legacy {@code Taming.processThickFur(Wolf, double)}:
     * a summoned wolf absorbs incoming damage divided by the Thick Fur modifier. The {@code WOLF_SHAKE}
     * effect the static also played is deferred with the Wolf-mutating call site (PORT Phase 10/11).
     *
     * @param damage the incoming damage
     * @return the reduced damage
     */
    public double processThickFur(double damage) {
        return damage / McMMOMod.getAdvancedConfig().getThickFurModifier();
    }

    /**
     * Pure damage-reduction math extracted from legacy {@code Taming.processShockProof(Wolf, double)}:
     * incoming (lightning/explosion) damage divided by the Shock Proof modifier. The {@code WOLF_SHAKE}
     * effect is deferred with the Wolf-mutating call site (PORT Phase 10/11).
     *
     * @param damage the incoming damage
     * @return the reduced damage
     */
    public double processShockProof(double damage) {
        return damage / McMMOMod.getAdvancedConfig().getShockProofModifier();
    }

    /**
     * The Beast Lore horse-stats conversion buried in legacy {@code beastLore}: it read a horse's raw
     * jump-strength attribute and converted it to the in-game jump height shown to the player. Pure
     * function of the raw attribute value (same "buried pure decision" extraction as Fishing's
     * {@code resolveMasterAnglerWaitTimes}); the eventual Beast Lore message body becomes a thin
     * wrapper that reads the live attribute and formats the result. Formula taken verbatim from
     * <a href="https://minecraft.wiki/w/Horse#Jump_strength">the wiki</a>.
     *
     * @param rawJumpStrength the horse's raw jump-strength attribute value
     * @return the converted jump strength (blocks) to display
     */
    public static double beastLoreHorseJumpStrength(double rawJumpStrength) {
        return -0.1817584952 * Math.pow(rawJumpStrength, 3)
                + 3.689713992 * Math.pow(rawJumpStrength, 2)
                + 2.128599134 * rawJumpStrength
                - 0.343930367;
    }
}
