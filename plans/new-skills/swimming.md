# Swimming — Plan

**Read [00-OVERVIEW.md](00-OVERVIEW.md) first.** Swimming is a **standalone primary skill** (D1 — wiki
calls it a child of Fishing+Acrobatics, but it has its own XP source). It **depends on F1** (tick
sampler) and **F2** (attribute service). It is `MISC_SKILLS`. The wiki has two overlapping idea sets
("Swimming" and "Swimming, alternate idea") plus a note that "almost everything [is] changeable in
config" — so this is very much a design-and-flesh-out skill, not a port.

Wiki source: `raw_site_text.md` §"Swimming" and §"Swimming, alternate idea".

## Concept

Reward the player for swimming. Level it by swimming real distance in water. Payoff: longer breath,
faster swimming, underwater treasure, and an in-water buff active. **Heavily overlaps vanilla Depth
Strider / Dolphin's Grace / Respiration** — that's the central balance question (D-SW2).

**XP source:** horizontal distance moved while in water and actually swimming (`isTouchingWater()` /
`isSubmergedInWater()` + moving + no vehicle), sampled by F1 (`SwimmingManager.onSwimTick`).

## Sub-skills (a merged, sane subset of the two wiki lists)

| Sub-skill | Type | Mechanic | Scaling (proposed) | Unlock | Risk |
|---|---|---|---|---|---|
| **Lead Lungs** | passive | Hold breath longer underwater | Slow air depletion / top up air with level, up to a large bonus (wiki: +1s/25 levels, cap +40s ≈ 7+ min). | 1 | Low–Med |
| **Swim Training** | passive | Faster in water | Managed `GENERIC_MOVEMENT_SPEED` (or a Dolphin's-Grace-like effect) while in water, scaling with level (wiki "0.1%/level"). Applied via F2 only while in water. | 1 | Med (attribute + overlap) |
| **Lake Raider** | passive | Chance to find treasure breaking blocks underwater | Roll on block-break while submerged; scales with level. Reuse `TreasureConfig`/`ItemSpecBuilder`. | some | Med |
| **Aquaman** | **active** | Right-click Raw Cod → Strength + Regen + Night Vision while in water, scaling duration | Cooldowned active; effects only while `isTouchingWater()` (wiki: 10s base, +1s/20 levels, cap ~60s). | e.g. 250 | Med (active infra) |

**Deferred/optional (from the "alternate idea" list — pick during design, don't build all):**
- *Water Sprint* (sprint-swim 150% speed but air/food drain 150%): overlaps Swim Training + Agility;
  fold into Swim Training or skip.
- *Diving* (sink faster while sneaking-falling in water): niche; optional.
- *Underwater Survival* (air + food drain slower): overlaps Lead Lungs; fold in or skip.

## Design decisions specific to Swimming

- **D-SW1 — which sub-skill set:** the two wiki lists overlap heavily. **Recommend the merged 4 above**
  (Lead Lungs, Swim Training, Lake Raider, Aquaman) and explicitly drop/fold the alternates. Don't ship
  eight near-duplicate air/speed knobs.
- **D-SW2 — vanilla overlap (the big one):** Depth Strider (speed), Dolphin's Grace (speed), Respiration
  (breath) already exist as enchants/effects. Decide whether Swimming **stacks** with them or is
  **redundant** for a geared player. Recommend: skill effects **stack additively but cap** so a Depth
  Strider III + Swim Training player isn't teleporting through the ocean, and Lead Lungs stacks with
  Respiration up to a cap. Flag to the user — this is a feel/balance call, not a code detail.
- **D-SW3 — anti-AFK:** water currents (bubble columns, flowing water, a soul-sand elevator) move the
  player without input — F1 must treat current-driven movement carefully or an AFK bubble elevator farms
  Swimming. Add a §G cheese row. Consider requiring a sprint/swim pose or a minimum input, not just
  position delta, if currents prove farmable.

## MC-free core (`SwimmingManager extends SkillManager`)

- `float onSwimTick(double distance)` — accumulate + per-block XP.
- `int getLeadLungsAirBonus()` / `int getAirTopUpPerTick()` — how much breath is preserved/restored.
- `double getSwimSpeedBonus()` — clamp-capped speed factor while in water.
- `boolean rollLakeRaiderTreasure()` / `Optional<ItemSpec> pickLakeTreasure(...)` — underwater treasure.
- `int getAquamanDurationTicks()` — active scaling.
- Standard `canX()` gates.

## MC-typed trigger layer

1. **XP + Swim Training speed:** F1 dispatches `onSwimTick` while in water; F2 applies/removes the swim
   speed modifier keyed on `isTouchingWater()`. **Remove it the tick the player leaves water.** (Or apply
   a Dolphin's-Grace-style effect instead of a raw attribute — verify which reads better; attribute is
   simpler to manage.)
2. **Lead Lungs:** intercept air depletion. Vanilla decrements air in `LivingEntity`/`ServerPlayerEntity`
   tick when submerged without Respiration. Either a mixin on the air-decrement (`@ModifyVariable` to slow
   it by level) or, simpler, an F1 per-tick top-up: when submerged and about to lose a bubble, restore
   air based on `getLeadLungsAirBonus()`. Respect the vanilla max-air cap; interact sanely with
   Respiration (D-SW2). Verify the air API with `scripts/javap-mc.sh`.
3. **Lake Raider:** in `BlockBreakListener`, when the player `isSubmergedInWater()` and
   `rollLakeRaiderTreasure()` succeeds, drop treasure from `pickLakeTreasure(...)`. Reuse the
   Excavation/Fishing treasure machinery; don't reinvent an item-spawn path.
4. **Aquaman:** active ability. Right-click Raw Cod → apply Strength + Regeneration + Night Vision for
   `getAquamanDurationTicks()`, **but the effects only make sense in water** — either gate activation on
   being in water or let them apply anywhere and note the deviation. Cooldowned via the super-ability
   infra. Add `SuperAbilityType.AQUAMAN` (+ `subSkillTypeDefinition`). Decide whether to consume the cod.

## Registration specifics

- `PrimarySkillType.SWIMMING`; `MISC_SKILLS`.
- `SubSkillType`: `SWIMMING_LEAD_LUNGS`, `SWIMMING_SWIM_TRAINING`, `SWIMMING_LAKE_RAIDER`,
  `SWIMMING_AQUAMAN`.
- `SuperAbilityType.AQUAMAN` (+ static-block `subSkillTypeDefinition = SWIMMING_AQUAMAN`).
- `experience.yml`: `Swimming` XP modifier + bar color + AFK/current exploit toggle (D-SW3).
- `advanced.yml`: Lead Lungs air bonus/increment + cap, Swim Training speed cap/increment, Lake Raider
  chance, Aquaman duration/cooldown/effect levels.
- A small treasure table for Lake Raider (inline in `advanced.yml` or a per-skill treasure config reusing
  `TreasureConfig`).
- Locale block `Swimming.*` (incl. Aquaman On/Off/Refresh).

## Balance / XP tuning

- Distance-swum XP: swimming is slower than sprinting, so per-block XP can be a touch higher than Agility,
  but tune against a §G number and against the bubble-elevator cheese (D-SW3).
- Swim Training + Depth Strider III: cap so it's fast, not silly (D-SW2).
- Lead Lungs stacking with Respiration III should approach but not trivially exceed "infinite air" unless
  that's the intended max-level fantasy — decide and cap.

## Testing

- **Unit (MC-free):** `getSwimSpeedBonus` clamp; `getLeadLungsAirBonus`/`getAirTopUpPerTick` scaling +
  cap; `rollLakeRaiderTreasure` pinned RNG; `getAquamanDurationTicks` scaling; per-block XP.
- **F1 anti-AFK:** current-driven movement doesn't farm XP (D-SW3); vehicle (boat) swimming pays nothing.
- **F2:** swim-speed modifier idempotent, removed on leaving water, cleared on logout/respawn.
- **§G rows:** swim 200 blocks → Swimming XP delta (and confirm a bubble elevator doesn't farm it); breath
  lasts noticeably longer at rank (Lead Lungs); swimming is faster (Swim Training) and the speed drops the
  instant you leave water; break blocks underwater → occasional treasure (Lake Raider); Aquaman off a cod
  gives the in-water buff on cooldown.
- **Regression:** old profile loads with Swimming defaulted.

## Cuts / deferrals

- The "alternate idea" sub-skills (Water Sprint, Diving, Underwater Survival) are **folded or dropped**
  (D-SW1) — do not ship eight overlapping knobs.
- If air-depletion mixing with Respiration gets hairy, ship Lead Lungs as a simple per-tick top-up capped
  below the Respiration-equivalent and refine later.
