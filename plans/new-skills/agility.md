# Agility (Sprinting) — Plan

**Read [00-OVERVIEW.md](00-OVERVIEW.md) first.** This file lists only Agility-specific work; the
boilerplate registration checklist and the shared foundation (F1 `PlayerMovementTracker`,
F2 `SkillAttributeService`) live there. Agility is a **standalone primary skill** (D1) and **depends on
F1 and F2**.

Wiki source: `raw_site_text.md` §"Sprinting or 'Agility'".

## Concept

The movement counterpart to Acrobatics: Acrobatics is about *falling and dodging*, Agility is about
*sprinting*. You level it by sprinting real distance. It pays off in cheaper sprinting, more speed, and
a nasty sprint-charge. It is a `MISC_SKILLS` skill.

**XP source:** horizontal distance sprinted, sampled by F1 (`AgilityManager.onSprintTick`). Only counts
when `isSprinting()` **and** actually moving **and** not in a vehicle. Sprinting on the spot into a wall
pays nothing.

## Sub-skills

Ranks/levels below are authored in **RetroMode** numbers (see overview checklist item 12). "Cap at
1000" everywhere is the RetroMode max level.

| Sub-skill | Type | Mechanic | Scaling (wiki → proposed) | Unlock | Risk |
|---|---|---|---|---|---|
| **Athlete** | passive | Sprinting costs less hunger | Sprint exhaustion scales from vanilla down toward walk-cost at 1000. Proposed: `exhaustion *= (1 - level/1000 * MaxReduction)`, `MaxReduction` config (wiki wants full walk-cost, i.e. ~1.0; cap it lower, e.g. 0.75, so sprinting is never free). | 1 | Low |
| **Dash** | passive | Move-speed bonus while sprinting | +1%/40 levels, cap **+25%** at 1000. Managed `GENERIC_MOVEMENT_SPEED` modifier via F2, applied only while sprinting. | 1 | Med (attribute lifecycle) |
| **Smash** | passive | Sprint-attacks crit / extra knockback | +1%/40 levels chance, cap **+25%** at 1000. Rolls in the combat path only when the attacker is sprinting. | some (e.g. 25) | Low |
| **Dart** | **active** | Right-click while sprinting → forward burst that damages + knocks back mobs in the path; stun at high level | Cooldowned active ability. Damage/knockback scale with level; "stun" = brief Slowness on hit at high rank. | e.g. 250 | Med (raycast + active infra) |
| ~~**Multi-jump**~~ | — | double-jump @250, triple @350 | **CUT (D2).** No server-side jump hook; fighting vanilla movement = desync/rubber-band. Do not attempt in v1. | — | **Unshippable** |

## MC-free core (`AgilityManager extends SkillManager`)

Model on `AcrobaticsManager` (`skills/acrobatics/AcrobaticsManager.java`). Zero Minecraft imports.

- `float onSprintTick(double distance)` — accumulate distance, return XP to award for whole blocks
  crossed (the accumulator/anti-AFK lives in F1; the *per-block XP value* + config read live here).
  Award via `applyXpGain(xp, XPGainReason.PVE, XPGainSource.SELF)`.
- `double getDashSpeedBonus()` — `min(0.25, (level/40) * 0.01)` (config-driven max + increment).
- `boolean rollSmash()` — `ProbabilityUtil.isSkillRNGSuccessful(SubSkillType.AGILITY_SMASH, mmoPlayer)`;
  gate on `RankUtils.hasUnlockedSubskill` + `Permissions.isSubSkillEnabled` (the standard pair).
- `double getAthleteExhaustionMultiplier()` — `1 - min(MaxReduction, level/1000 * MaxReduction)`.
- `boolean canDart()` / `DartResult computeDart(...)` — deterministic damage/knockback/stun given level;
  keep the RNG (if any) injected so it's testable.

## MC-typed trigger layer

1. **XP + Dash speed** — F1 dispatches `onSprintTick`. In the same tick, F2 applies/removes the Dash
   speed modifier based on `isSprinting()` and `getDashSpeedBonus()`. **Remove the modifier the tick
   sprinting stops** — do not leave a sprint-speed buff on a standing player.
2. **Athlete (hunger)** — intercept sprint exhaustion. Vanilla adds sprint exhaustion in
   `HungerManager#addExhaustion` (verify the exact call site with `scripts/javap-mc.sh`). Mixin
   `@ModifyArg`/`@ModifyVariable` on the exhaustion added while the player `isSprinting()`, scaled by
   `getAthleteExhaustionMultiplier()`. **Gate on isSprinting so you don't discount all exhaustion.**
3. **Smash** — in `EntityDamageListener` (the existing combat path), when the attacker is a player who
   `isSprinting()` and `rollSmash()` succeeds: force a crit (bonus damage) and/or bump knockback. Fold
   into the existing melee bonus flow; don't add a second damage mixin.
4. **Dart** — active ability. Right-click-item detection while sprinting (a `UseItemCallback` /
   interaction listener). On activation: raycast forward N blocks, hit `LivingEntity`s in the path,
   apply damage + a strong forward knockback velocity to them (and a lunge velocity to the player), and
   at high rank a short Slowness `StatusEffect` for the "stun." Runs on the super-ability cooldown infra
   (`McMMOPlayer` cooldown methods, `[[phase-11-2-superability-cooldown]]`). Add a `SuperAbilityType`
   entry (overview checklist item 3) or, if it's a pure instant with its own cooldown, a lighter
   cooldown — decide during design, but reuse existing cooldown storage, don't invent a new one.

## Registration specifics

- `PrimarySkillType.AGILITY`; `MISC_SKILLS`.
- `SubSkillType`: `AGILITY_ATHLETE`, `AGILITY_DASH`, `AGILITY_SMASH`, `AGILITY_DART`. Ranks per table.
- `SuperAbilityType.DART` (+ `subSkillTypeDefinition = AGILITY_DART`) if built as a super ability.
- `experience.yml`: `Agility` XP modifier + XP-bar color + a `Sprinting`/AFK exploit toggle (reuse the
  F1 anti-AFK gate; a config flag lets it be tuned/disabled).
- `advanced.yml`: Dash max %/increment, Smash max chance, Athlete max reduction, Dart damage/range/stun.
- Locale block `Agility.*`.

## Balance / XP tuning (nothing here is final)

- **Distance XP is the whole ballgame.** Too high and a player laps a track to max it; too low and it
  never moves. Start conservative (e.g. small XP per block sprinted) and tune against a §G number.
- Dash +25% move speed stacks *on top of* the vanilla sprint multiplier — it will feel fast. Verify it
  doesn't blow past server movement checks (rubber-banding) at max rank.
- Athlete must never make sprinting *free* — cap `MaxReduction < 1.0`.

## Testing

- **Unit (MC-free):** `getDashSpeedBonus` at levels 0/40/1000 (0 / 1% / 25% clamp); `rollSmash` with
  pinned RNG (0 → always, 100 → never); `getAthleteExhaustionMultiplier` clamps; Dart damage/stun table.
- **F2 idempotency:** applying Dash twice doesn't stack; stopping sprint removes it; logout clears it.
- **§G rows (add to play-test):** sprint 200 blocks → Agility XP delta; at rank, sprint feels faster and
  the speed drops the instant you stop; sprinting depletes hunger visibly slower at high Athlete; a
  sprint-attack occasionally crits/knocks back (Smash); Dart lunges you forward and flings a mob.
- **Regression:** old profile loads with Agility defaulted (overview item 19).

## Cuts / deferrals

- **Multi-jump — CUT** (D2). If ever revisited it needs a client-side mod or a movement mixin fighting
  anti-cheat; out of scope.
- Dart "stun" is a short Slowness, not a true stun (no vanilla stun state) — document as a deviation.
