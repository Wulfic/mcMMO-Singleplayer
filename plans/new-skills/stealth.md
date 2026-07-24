# Stealth (Sneaking) — Plan

**Read [00-OVERVIEW.md](00-OVERVIEW.md) first.** Stealth is a **standalone primary skill** (D1) and
**depends on F1** (the tick sampler) and **F2** (attribute service). It is `MISC_SKILLS`.

Wiki source: `raw_site_text.md` §"Sneaking".

## Concept

The rogue skill: reward the player for moving while sneaking. It pays off in faster sneaking, being
harder for mobs to notice, and hitting harder out of stealth. The wiki jokes "Sticky keys op" — which is
exactly the exploit to design against: **you must not level this by taping down the shift key.**

**XP source:** horizontal distance moved **while sneaking and actually moving**, sampled by F1
(`StealthManager.onSneakTick`). Δ≈0 pays nothing. No vehicle. This anti-AFK gate is the single most
important part of the skill — see F1 in the overview.

## Sub-skills

| Sub-skill | Type | Mechanic | Scaling (proposed) | Unlock | Risk |
|---|---|---|---|---|---|
| **Padfoot** | passive | Sneak move-speed increases toward normal walk speed | Managed `GENERIC_MOVEMENT_SPEED` modifier (F2) applied only while sneaking; scales to ≈walk speed at cap. | 1 | Med (attribute lifecycle) |
| **Assassin** | passive | Damage while sneaking (and not recently hit) is boosted — a backstab | +% damage scaling with level; only when attacker `isSneaking()` and hasn't taken damage in the last N ticks (wiki: "before taking damage for a duration"). | some | Low–Med |
| **Thief** | passive | Mobs notice you less while sneaking | **Reduced version (D2):** cut mob target-acquisition range while sneaking (and more when behind cover). Full "invisible name / mobs literally blind" is expensive + overlaps vanilla. | some | **High** |
| **Smoke Bomb** | **active** | Brief invisibility, no firework | Cooldowned active: apply Invisibility `StatusEffect` for a scaling duration; no particles/fireworks. | e.g. 250 | Med (active infra) |

## Design decisions specific to Stealth

- **D-S1 — Thief scope:** the full wiki version (mobs cannot see you at all, name invisible behind one
  block) is a per-tick targeting override on nearby mobs — expensive and fighting vanilla LOS. Ship the
  **reduced** version: while sneaking, apply a `GENERIC_FOLLOW_RANGE`-style reduction / cancel new mob
  target acquisition within a shrinking radius. If even that's messy in play-test, **defer Thief** and
  ship Padfoot+Assassin+Smoke Bomb — those three are the fun ones. Flag.
- **D-S2 — overlap with vanilla & other skills:** vanilla already reduces your name-tag render distance
  while sneaking and darkens you. Assassin's sneak-damage overlaps Agility→Smash (sprint-damage) and
  Acrobatics conceptually — they are mutually exclusive states (you can't sprint and sneak at once), so
  it's fine, but call it out to the user as intended.
- **D-S3 — Assassin "before taking damage" window:** interpret as "no damage taken in the last N ticks."
  Track last-damaged-tick per player (a small field; the combat listener already sees incoming damage).

## MC-free core (`StealthManager extends SkillManager`)

- `float onSneakTick(double distance)` — accumulate + per-block XP (accumulator in F1).
- `double getPadfootSpeedBonus()` — scales sneak speed toward walk; clamp at the config max.
- `double getAssassinDamageMultiplier()` — `1 + min(maxBonus, level * perLevel)`.
- `boolean assassinReady(boolean sneaking, long ticksSinceLastHit)` — the backstab gate (deterministic,
  testable).
- `int getThiefRangeReduction()` — blocks of follow-range shaved while sneaking.
- `int getSmokeBombDurationTicks()` — scaling invisibility duration.
- Standard `canX()` gates.

## MC-typed trigger layer

1. **XP + Padfoot speed** — F1 dispatches `onSneakTick`; F2 applies/removes the Padfoot speed modifier
   keyed on `isSneaking()`. **Remove the instant sneaking stops.** (Note: vanilla already *slows* sneak
   movement; Padfoot is adding speed back, so verify the net feel — it should approach normal walk, not
   exceed it, unless config says otherwise.)
2. **Assassin** — in `EntityDamageListener`: when the attacker is a player who `isSneaking()` and
   `assassinReady(...)`, multiply the outgoing melee damage by `getAssassinDamageMultiplier()`. Reuse the
   existing melee-bonus flow. Track `ticksSinceLastHit` from the player's own incoming-damage events
   (D-S3).
3. **Thief (reduced)** — while sneaking, per-tick (via F1) reduce nearby hostile mob follow-range or
   cancel new target acquisition within `getThiefRangeReduction()`. Implement as either a managed
   `GENERIC_FOLLOW_RANGE` reduction on *self* isn't a thing (follow-range is the mob's attribute) — so
   this is a mixin/hook on mob target selection (`MobEntity` / `ActiveTargetGoal`) that checks whether the
   candidate target is a sneaking player with Thief and shrinks the effective range. **This is the risky
   bit; keep it behind D-S1.**
4. **Smoke Bomb** — active ability on the super-ability cooldown infra. On activation: apply Invisibility
   for `getSmokeBombDurationTicks()`, explicitly **without** the vanilla firework/particle burst. Add a
   `SuperAbilityType.SMOKE_BOMB` (+ `subSkillTypeDefinition`). Trigger: an activation input consistent
   with the port's other actives — likely sneak + a bound action; decide during design.

## Registration specifics

- `PrimarySkillType.STEALTH`; `MISC_SKILLS`.
- `SubSkillType`: `STEALTH_PADFOOT`, `STEALTH_ASSASSIN`, `STEALTH_THIEF`, `STEALTH_SMOKE_BOMB`.
- `SuperAbilityType.SMOKE_BOMB` (+ static-block `subSkillTypeDefinition = STEALTH_SMOKE_BOMB`).
- `experience.yml`: `Stealth` XP modifier + bar color + AFK exploit toggle (the anti-sticky-key gate).
- `advanced.yml`: Padfoot max speed, Assassin max bonus + no-damage window, Thief range reduction,
  Smoke Bomb duration/cooldown.
- Locale block `Stealth.*` (incl. Smoke Bomb On/Off/Refresh).

## Balance / XP tuning

- **Anti-AFK is the whole risk.** Distance-while-sneaking is trivially farmable with a stuck key + a
  water current or a walking-into-a-boat trick — F1's "reject vehicle + require real ground delta" gate
  must be tight. Add a §G row specifically trying to cheese it.
- Assassin damage on top of a sneak-crit could be huge — cap it and test against an armored mob.
- Smoke Bomb invisibility: remember armor/held-items stay visible in vanilla invisibility — document.

## Testing

- **Unit (MC-free):** `assassinReady` truth table (sneaking×recently-hit); `getAssassinDamageMultiplier`
  clamp; `getPadfootSpeedBonus` at levels; `getSmokeBombDurationTicks` scaling; `getThiefRangeReduction`.
- **F1 anti-AFK unit test:** zero-delta sneak tick pays zero XP; vehicle sneak pays zero; a teleport
  delta is rejected.
- **F2:** Padfoot modifier idempotent, removed on un-sneak, cleared on logout.
- **§G rows:** sneak-walk 200 blocks → Stealth XP (and confirm standing-still sneak pays nothing); sneak
  is faster at rank (Padfoot); a sneak-attack hits noticeably harder (Assassin); mobs aggro later while
  sneaking (Thief, if shipped); Smoke Bomb → you go invisible with no firework and it holds a cooldown.
- **Regression:** old profile loads with Stealth defaulted.

## Cuts / deferrals

- **Thief** may ship reduced or be deferred (D-S1). Padfoot + Assassin + Smoke Bomb are the MVP.
- The wiki's on-screen "《Detection Level》 scoreboard" (from the *Dagger* suggestion) is **not** part of
  Stealth — do not build a detection HUD.
