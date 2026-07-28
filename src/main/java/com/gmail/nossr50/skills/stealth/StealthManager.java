package com.gmail.nossr50.skills.stealth;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.skills.RankUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Stealth — the rogue skill. XP comes from horizontal distance covered while sneaking; the payoff is
 * moving faster while crouched (Padfoot), hitting harder out of stealth (Assassin), and a cooldowned
 * vanishing act (Smoke Bomb).
 *
 * <p><b>MC-free by construction</b>, like every other manager: the platform layer decides what state
 * the player is in and this class owns the arithmetic and the gates, so all of it is unit-testable.
 *
 * <h2>Relationship to Agility</h2>
 * Stealth is a standalone primary skill, <em>not</em> a fourth Agility movement domain (ruled
 * 2026-07-25) — its payoff is not-being-seen rather than locomotion. Three seams still touch
 * Agility, and each is settled deliberately rather than by luck:
 * <ul>
 *   <li><b>XP never double-pays.</b> Agility's medium classifier returns "no medium" for a sneaking
 *       player, in every medium, so a tick of travel feeds exactly one of the two skills. Sneaking
 *       is Stealth's sensor and crouched travel pays Agility nothing.</li>
 *   <li><b>Padfoot and Fleet Footed cannot both be live</b>, for the same reason — and post-2026-07-27
 *       they no longer even contend for an attribute: Fleet Footed's land body is
 *       {@code movement_speed} while Padfoot is {@code sneaking_speed}, a separate vanilla attribute
 *       that only applies while crouched or crawling. D-AG5 resolves to "structurally impossible"
 *       rather than "carefully avoided".</li>
 *   <li><b>Assassin and Agility's Smash are mutually exclusive states</b> — you cannot sprint and
 *       sneak at once — so the two combat bonuses can never stack. Intended, not accidental.</li>
 * </ul>
 *
 * <h2>Not shipped in v1</h2>
 * <b>Thief</b> (mobs notice you less while sneaking) is deferred, not disabled: it needs a mixin on
 * mob target selection, and there is no enum constant, config key or locale string for it. Adding a
 * dead sub-skill would make {@code /mcstats} advertise a mechanic that does nothing.
 */
public class StealthManager extends SkillManager {

    public StealthManager(McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.STEALTH);
    }

    /**
     * The sneak-XP tuning for this session, snapshotted on first use.
     *
     * <p>Lazy for two reasons that pull the same way: managers are constructed before a config is
     * guaranteed wired (and unit tests build them with none at all), and this is read 20×/s so it
     * must not be a live YAML walk — the Alchemy Catalysis per-tick-config-read trap. A manager
     * lives exactly as long as one player session, which is exactly as long as one loaded config.
     */
    private StealthXpSettings xpSettings;

    /**
     * Fractional sneak XP not yet handed to the XP pipeline.
     *
     * <p>A tick of sneaking is worth well under one XP, and pushing a fraction through
     * {@code beginXpGain} 20×/s would churn the level-up check, the diminished-returns ledger and
     * the profile dirty flag for nothing. Whole XP is flushed; the remainder rides along to the next
     * tick so nothing is lost to truncation.
     */
    private double xpAccumulator;

    /** The sneak-XP tuning for this session, snapshotted on first use. */
    public @NotNull StealthXpSettings xpSettings() {
        StealthXpSettings local = xpSettings;
        if (local == null) {
            local = StealthXpSettings.fromConfig();
            xpSettings = local;
        }
        return local;
    }

    /** Test seam: install an explicit tuning snapshot instead of reading the live config. */
    public void setXpSettings(@NotNull StealthXpSettings settings) {
        this.xpSettings = settings;
    }

    /**
     * Credit one tick of qualifying sneak-travel.
     *
     * <p>The caller (the movement tracker) has already established that this tick is legitimate —
     * the player is sneaking, on the ground, not in a vehicle, not teleporting, actually moved, and
     * actually pressing a movement key. This method owns only the clamp and the payout.
     *
     * @param distance horizontal distance moved this tick, in blocks
     * @return the whole XP awarded this tick — usually {@code 0}, since a tick is worth a fraction
     *         of one XP and the remainder is accumulated
     */
    public float onSneakTick(double distance) {
        xpAccumulator += xpSettings().xpFor(distance);
        if (xpAccumulator < 1.0) {
            return 0F;
        }
        final float whole = (float) Math.floor(xpAccumulator);
        xpAccumulator -= whole;
        applyXpGain(whole, XPGainReason.PVE, XPGainSource.SELF);
        return whole;
    }

    /**
     * Seconds of travel credited for a tick's distance — the speed clamp. Exposed because it is the
     * single most important thing in the skill to be able to assert on.
     */
    public double creditedSeconds(double distance) {
        return xpSettings().creditedSeconds(distance);
    }

    // --- Sub-skill 1: Padfoot -------------------------------------------------------------------

    public boolean canPadfoot() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.STEALTH_PADFOOT)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.STEALTH_PADFOOT);
    }

    /**
     * How much to add to the vanilla {@code sneaking_speed} attribute while the player is sneaking.
     *
     * <p>Vanilla's own clamp on that attribute ({@code max 1.0}, i.e. full walking speed) is the
     * real ceiling, so there is no clamp of our own here and no configuration of
     * {@code MaxSneakSpeedBonus} can make sneaking outrun walking.
     *
     * @return the additive bonus, or {@code 0} when the sub-skill is locked or disabled
     */
    public double getPadfootSpeedBonus() {
        if (!canPadfoot()) {
            return 0.0;
        }
        final AdvancedConfig advanced = McMMOMod.getAdvancedConfig();
        if (advanced == null) {
            return 0.0;
        }
        return scaleToLevel(advanced.getPadfootMaxSneakSpeedBonus(),
                advanced.getPadfootMaxBonusLevel());
    }

    // --- Sub-skill 2: Assassin ------------------------------------------------------------------

    public boolean canAssassin() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.STEALTH_ASSASSIN)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.STEALTH_ASSASSIN);
    }

    /**
     * How long the player must have gone without taking damage for a backstab to count (D-S3).
     *
     * @return the window in ticks; never negative, so a nonsensical config cannot invert the gate
     *         into "only works while being hit"
     */
    public int getAssassinNoDamageWindowTicks() {
        final AdvancedConfig advanced = McMMOMod.getAdvancedConfig();
        return advanced == null ? 100 : Math.max(0, advanced.getAssassinNoDamageWindowTicks());
    }

    /**
     * Whether a hit thrown right now is a backstab.
     *
     * <p>Deterministic and fully parameterised — no RNG, no live entity — because this gate is the
     * whole of Assassin's behaviour and the thing most worth being able to prove. Both inputs are
     * resolved by the caller, which holds the attacking entity.
     *
     * @param sneaking           whether the attacker is sneaking
     * @param ticksSinceLastHit  ticks since the attacker last took damage; {@link Long#MAX_VALUE}
     *                           when they have never been hit this session
     * @return {@code true} when the damage bonus applies
     */
    public boolean assassinReady(boolean sneaking, long ticksSinceLastHit) {
        return sneaking && canAssassin() && ticksSinceLastHit >= getAssassinNoDamageWindowTicks();
    }

    /**
     * The multiplier to apply to a backstab's outgoing damage.
     *
     * <p>Never below {@code 1.0}: an Assassin roll must be incapable of <em>reducing</em> a hit, no
     * matter what is in the config. Callers may apply this unconditionally on a successful
     * {@link #assassinReady} without re-checking.
     *
     * @return {@code 1 + bonus}, scaled to level
     */
    public double getAssassinDamageMultiplier() {
        if (!canAssassin()) {
            return 1.0;
        }
        final AdvancedConfig advanced = McMMOMod.getAdvancedConfig();
        if (advanced == null) {
            return 1.0;
        }
        final double bonus = Math.max(0.0, advanced.getAssassinMaxDamageBonus());
        return 1.0 + scaleToLevel(bonus, advanced.getAssassinMaxBonusLevel());
    }

    // --- Sub-skill 3: Smoke Bomb ----------------------------------------------------------------

    public boolean canSmokeBomb() {
        return RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.STEALTH_SMOKE_BOMB)
                && Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.STEALTH_SMOKE_BOMB);
    }

    /**
     * How long Smoke Bomb's invisibility lasts.
     *
     * <p>Floored at one tick so a zeroed config produces a very short ability rather than one that
     * burns its cooldown and applies a zero-duration effect, which reads to a player as "the button
     * is broken".
     *
     * @return the duration in ticks
     */
    public int getSmokeBombDurationTicks() {
        final AdvancedConfig advanced = McMMOMod.getAdvancedConfig();
        return advanced == null ? 100 : Math.max(1, advanced.getSmokeBombDurationTicks());
    }
}
