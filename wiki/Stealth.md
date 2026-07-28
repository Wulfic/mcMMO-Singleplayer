# Stealth

**New in this port** — Stealth does not exist in upstream mcMMO.

**How you train it:** sneak, on the ground, under your own power.

---

## Earning XP

Stealth uses the **same speed-normalised model** as the [movement skills](Movement-Skills#how-movement-xp-works): you are paid per *second* of qualifying sneak-travel, with each tick's distance clamped at a reference speed.

| Setting (`experience.yml` → `Experience_Values.Stealth.Sneak`) | Default |
|---|---|
| `Baseline_Xp_Per_Second` | **25.0** |
| `Reference_Speed` | **1.295** blocks/s |

25 XP/s is **the highest continuous rate in the mod** — it maxes a RetroMode level-1000 skill in about **122 hours** of continuous sneaking, versus ~204 h for Parkour on land. That's deliberate: sneaking is slow, tedious, and something players do in short bursts rather than to get anywhere.

The reference speed is derived as vanilla walk speed (4.317) × the `sneaking_speed` attribute default (0.3). At the default baseline that's ~19.3 XP per block — again, a **derived** number. Don't tune it.

### Why the clamp matters even more here

**Padfoot raises the vanilla `sneaking_speed` attribute from 0.3 toward 1.0** — so a maxed player sneaks more than **three times faster** than an unranked one. (Fleet Footed, for comparison, is worth about 20 %.) Paid per block, Padfoot would be a 3.3× multiplier on its own XP rate. Under the clamp, a maxed Padfoot player earns the same XP per second and simply covers more ground doing it.

### What does *not* pay

| | Why |
|---|---|
| **Crouch-swimming** | It's faster than the sneak reference speed, so it would always sit at the clamp — making "hold shift in a water current" the optimal way to level. Ground only. |
| **Being carried** | Boats, minecarts, pistons, water currents. See below. |
| **Standing still while sneaking** | No distance, no XP. |

### The anti-AFK gate

`experience.yml` → `ExploitFix.Stealth.Require_Movement_Input: true`

Sneak-distance is the most farmable XP source in the mod — a taped-down shift key in a water current, on a moving boat, or on a piston loop all change your position without you being at the keyboard.

This gate reads your **actual server-side input state**, so being *carried* earns nothing while walking under your own power earns normally.

> **Leave this on.** It exists as an escape hatch, not a balance knob: if a client ever stopped sending input packets, the gate would silently reduce Stealth XP to **zero** rather than merely mis-tune it. If Stealth is earning nothing at all, this is the first thing to test — see [Troubleshooting](Troubleshooting).

---

## Sub-skills

RetroMode unlock levels shown.

### Padfoot — unlocks at 1

**Sneak almost as fast as you walk.**

Raises the vanilla `sneaking_speed` attribute, up to `MaxSneakSpeedBonus: 0.7` at max level.

That attribute is a clamped one (0.3 default, hard ceiling 1.0), and **vanilla's own ceiling is walking speed** — so no value you put in the config can make sneaking faster than walking. The shipped 0.7 lands a maxed player exactly at walking speed.

It also applies **while crawling**, since vanilla uses the same attribute for the crawling pose. Squeezing through a 1-block gap is faster too.

### Assassin — unlocks at 150

**Strike from the shadows for far greater damage.**

A backstab: hit harder while sneaking, *if you haven't been hit recently*.

| Setting | Default | Meaning |
|---|---|---|
| `MaxDamageBonus` | **1.0** | A **fraction, not a multiplier** — 1.0 means *double damage* at max level. |
| `NoDamageWindowTicks` | **100** | How long you must go without taking damage for a hit to count. 100 ticks = 5 seconds. |

The 5-second window is tuned so you **cannot trade blows and keep stabbing**, but it's short enough to break contact and re-enter stealth inside a single fight.

> ⚠️ **The damage bonus is multiplicative**, so it compounds with your weapon skill's own on-hit bonus *and* with a vanilla crit. That's the point of the sub-skill — and it's also the single most likely thing in this skill to be over-tuned. **Test it against an armoured mob before trusting it.**

**Assassin cannot stack with Agility's Smash.** Smash requires sprinting and Assassin requires sneaking, and you can't do both at once.

### Smoke Bomb — unlocks at 250

**Vanish in a puff of nothing at all.** Stealth's super ability.

Right-click while holding **gunpowder** (`Skills.Stealth.Smoke_Bomb_Item`, never consumed). Applies vanilla **Invisibility for 100 ticks (5 s)**, with no firework and no particle burst. Cooldown **240 s**.

> **Vanilla Invisibility does not hide armour or held items.** A fully armoured player using this is still very visible. That is vanilla behaviour, not a bug — and since you'll often be using Stealth alongside [Unarmored](Unarmored), it may not come up.

Because it's item-triggered rather than tool-readied, it does **not** use the two-step ready/activate gesture — see [Super Abilities](Super-Abilities#item-triggered-abilities).

> ⚠️ The Smoke Bomb item **must differ from Agility's Second Wind item**. Both actives listen on the same use-item event, so sharing an item fires one and prints the other's refusal message.

---

## Not implemented

**Thief** — "mobs notice you less while sneaking" — is deliberately **absent**, not present-and-disabled. It needs a mixin on mob target selection. A dead enum constant with no ranks, no config and no behaviour reads as a half-wired sub-skill to everything that iterates the skill list, `/mcstats` included, so it isn't there at all.

---

## Tuning

| File | Section |
|---|---|
| `experience.yml` | `Experience_Values.Stealth.Sneak` — baseline and reference speed |
| `experience.yml` | `ExploitFix.Stealth.Require_Movement_Input` |
| `advanced.yml` | `Skills.Stealth.{Padfoot,Assassin,SmokeBomb}` |
| `skillranks.yml` | `Stealth.*` — unlock levels |
| `config.yml` | `Skills.Stealth` — level cap, `Smoke_Bomb_Item` |
| `config.yml` | `Abilities.Cooldowns.Smoke_Bomb` |
