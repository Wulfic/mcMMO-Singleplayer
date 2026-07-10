package com.gmail.nossr50.skills.taming;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.Permissions;
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
 *   <li>{@code fastFoodService}/{@code processEnvironmentallyAware}/{@code pummel}/
 *       {@code attackTarget}/{@code beastLore} — need live {@code Wolf}/{@code LivingEntity}
 *       mutation, {@code ProbabilityUtil} RNG, particle/sound/notification adapters, and (for
 *       Environmentally Aware) the Folia teleport scheduler;</li>
 *   <li>the whole Call of the Wild summon path ({@code summonOcelot}/{@code summonWolf}/
 *       {@code summonHorse}/{@code processCallOfTheWild}/{@code spawnCOTWEntity}/{@code isCOTWItem}/
 *       {@code cleanupAllSummons}) — needs the entity-spawn adapter, the transient-entity tracker,
 *       the {@code CallOfTheWildType}/{@code TamingSummon} datatypes, and {@code Permissions.callOfTheWild};</li>
 *   <li>the {@code Taming} static helpers touching a live {@code Wolf} ({@code processThickFurFire},
 *       {@code processHolyHound}, {@code canPreventDamage}) — the pure modifier-division parts are
 *       extracted here as {@link #processThickFur(double)}/{@link #processShockProof(double)}.</li>
 * </ul>
 */
public class TamingManager extends SkillManager {

    /**
     * Bleed ticks applied by Gore. Legacy {@code Taming.goreBleedTicks} — equivalent to rank 1 in
     * Rupture; consumed by the deferred Gore body that applies the DoT (PORT Phase 11).
     */
    public static final int GORE_BLEED_TICKS = 2;

    public TamingManager(@NotNull McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.TAMING);
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
     * argument was unused for the math, so this is the pure part: applying the configured Gore
     * modifier and returning only the <em>additional</em> damage over the base hit.
     *
     * @param damage the initial (base) damage
     * @return the extra damage Gore contributes on top of {@code damage}
     */
    public double gore(double damage) {
        return (damage * McMMOMod.getAdvancedConfig().getGoreModifier()) - damage;
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
