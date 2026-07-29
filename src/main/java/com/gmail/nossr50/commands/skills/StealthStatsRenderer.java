package com.gmail.nossr50.commands.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.skills.stealth.StealthManager;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mcstats stealth} — Padfoot's sneak speed, Assassin's backstab multiplier and Smoke Bomb's
 * duration.
 *
 * <p>None of the three is a chance, which is unusual for this port: every number here is a
 * deterministic level-scaled value read straight off {@link StealthManager}, so what the screen shows
 * is exactly what the mechanic will do rather than an expectation over rolls.
 *
 * <p><b>Padfoot renders as a percentage because that is literally what the attribute is.</b> Vanilla's
 * {@code sneaking_speed} is a fraction of walking speed (base {@code 0.3}), and Padfoot adds to it, so
 * the shipped {@code 0.7} bonus reads honestly as "+70%" — and the sum landing on 100% is vanilla's
 * own clamp, i.e. sneaking at a full walk. It is not a multiplier on your current speed.
 *
 * <p><b>Assassin renders as a multiplier</b> ({@code 2.00x}) rather than a bonus percentage, because
 * the manager hands back {@code 1 + bonus} and it multiplies the <em>whole</em> melee total — crits
 * included. Showing "100%" would invite reading it as an additive bonus on the base weapon damage.
 */
public final class StealthStatsRenderer extends SkillStatsRenderer {

    private StealthManager stealth;

    private boolean canPadfoot;
    private boolean canAssassin;
    private boolean canSmokeBomb;

    public StealthStatsRenderer() {
        super(PrimarySkillType.STEALTH);
    }

    @Override
    protected void dataCalculations(float skillValue) {
        stealth = mmoPlayer.getStealthManager();

        canPadfoot = hasUnlocked(SubSkillType.STEALTH_PADFOOT);
        canAssassin = hasUnlocked(SubSkillType.STEALTH_ASSASSIN);
        canSmokeBomb = hasUnlocked(SubSkillType.STEALTH_SMOKE_BOMB);
    }

    @Override
    protected List<String> statsDisplay(float skillValue) {
        final List<String> messages = new ArrayList<>();
        if (stealth == null) {
            return messages; // Profile not loaded; the header and sub-skill list still render.
        }

        if (canPadfoot) {
            messages.add(getStatMessage(SubSkillType.STEALTH_PADFOOT,
                    percent.format(stealth.getPadfootSpeedBonus())));
        }
        if (canAssassin) {
            messages.add(getStatMessage(SubSkillType.STEALTH_ASSASSIN,
                    decimal.format(stealth.getAssassinDamageMultiplier()) + "x"));
        }
        if (canSmokeBomb) {
            // Ticks are an implementation unit; the player thinks in seconds.
            messages.add(getStatMessage(SubSkillType.STEALTH_SMOKE_BOMB,
                    decimal.format(stealth.getSmokeBombDurationTicks() / 20.0D) + "s"));
        }

        return messages;
    }
}
