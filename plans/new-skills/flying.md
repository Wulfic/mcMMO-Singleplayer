# Flying (Elytra) — Plan

**Read [00-OVERVIEW.md](00-OVERVIEW.md) first.** Flying is a **standalone primary skill** (D1 — wiki
calls it a child of Cartography+Acrobatics, but Cartography isn't being built and it needs its own XP).
It **depends on F1** (tick sampler). It is `MISC_SKILLS`. **This is the most aggressively cut-down skill
of the six** — the wiki piles on noclip-mining and lava-flight; most of that is unshippable. Build the
sane core; leave the rest for a possible Phase 2.

Wiki source: `raw_site_text.md` §"Flying".

## Concept

Reward elytra flight. Level it by flying real distance with an elytra deployed. Payoff: better glide,
more speed, a burst active, and slow durability recovery. **The exotic "fly through blocks" abilities are
cut** (see D2 in the overview).

**XP source:** horizontal distance flown while `isGliding()` (elytra), sampled by F1
(`FlyingManager.onFlyTick`). Not while walking/falling without elytra; not in a vehicle.

## Sub-skills — v1 (ship these)

| Sub-skill | Type | Mechanic | Scaling (proposed) | Unlock | Risk |
|---|---|---|---|---|---|
| **Glide** | passive | Fall slower while gliding | Reduce downward velocity component while gliding, up to **25%** at 1000 (wiki 0.025%/level). Per-tick velocity nudge. | 1 | Med (physics feel) |
| **Wind Walker** | passive | Fly faster while gliding | Add forward (look-vector) velocity while gliding, up to **25%** at 1000. Per-tick velocity nudge. | 1 | Med (physics feel) |
| **Limitless** | **active** | Right-click Eye of Ender → upward boost + speed for a few seconds | Cooldowned active. Duration/speed scale with level (wiki: base 5s/+50%, up to 15s/+100%). | e.g. 250 | Med (active infra) |
| **Solar Wings** | passive | Elytra slowly repairs in sunlight | @750 (wiki). Per-tick durability heal when the player has sky access and it's day; faster on the ground in daylight. | 750 | Low |

## Sub-skills — CUT from v1 (D2)

| Sub-skill | Why cut |
|---|---|
| ~~**Winged Drill**~~ / ~~**Demon Wings** (mining half)~~ | Fly *through* stone/dirt/netherrack picking up drops = **noclip mining.** Cannot be done server-authoritatively without hard client desync; grief/dupe vector. **Do not build in v1.** If ever attempted it's its own multi-week project with a custom movement + break pipeline. |
| ~~**Bombing Jet**~~ | Drop auto-lit TNT while flying. Shippable but griefy; if built, **config-off by default** and clearly not v1. |
| ~~**Demon Wings** (lava-flight half)~~ | Fly through lava unharmed + upward. Niche, and entangled with the drill half. Defer. |

## Design decisions specific to Flying

- **D-F1 — velocity nudges vs a movement mixin:** Glide/Wind Walker can be done two ways: (a) per-tick
  `setVelocity`/`addVelocity` adjustments from F1 while `isGliding()`, or (b) a mixin on the elytra
  branch of `LivingEntity#travel`/`getEffectiveGravity`. **Recommend (a) velocity nudges first** — no
  mixin, easier to tune, easier to disable. Watch for client-prediction jitter; if the feel is bad,
  escalate to (b). Verify the gliding check and velocity API with `scripts/javap-mc.sh`.
- **D-F2 — overlap with firework rockets:** vanilla elytra + firework boosting already exists. Wind Walker
  stacks on top; make sure the combination isn't absurd (rocket + 25% is very fast). Test and cap.
- **D-F3 — anti-exploit:** distance-flown is farmable by circling. F1's vehicle/teleport guards apply;
  also consider not paying while stationary-gliding into a wall or hovering in a bubble. A firework-boost
  loop over a small area should trickle, not firehose — tune the per-block XP low.

## MC-free core (`FlyingManager extends SkillManager`)

- `float onFlyTick(double distance)` — accumulate + per-block XP.
- `double getGlideReduction()` — `min(0.25, level * perLevel)` downward-velocity reduction factor.
- `double getWindWalkerBoost()` — `min(0.25, level * perLevel)` forward-velocity factor.
- `int getLimitlessDurationTicks()` / `double getLimitlessSpeed()` — active scaling.
- `boolean canSolarWings()` — rank gate; the repair *amount* per tick is config.
- Standard `canX()` gates.

## MC-typed trigger layer

1. **XP + Glide + Wind Walker:** F1 dispatches `onFlyTick` while `isGliding()`. In the same tick apply
   the velocity nudges (D-F1): scale down the negative-Y velocity by `getGlideReduction()`, add
   `getWindWalkerBoost()` × look-vector to horizontal velocity. **Only while actually gliding** — never
   touch velocity otherwise, or you'll make the player float when walking.
2. **Limitless:** active ability. Right-click with an Eye of Ender → upward + forward speed boost for
   `getLimitlessDurationTicks()`. Cooldowned via the super-ability infra. Add `SuperAbilityType.LIMITLESS`
   (+ `subSkillTypeDefinition`). **Don't consume the Eye of Ender** (or decide to — flag; wiki implies a
   re-usable trigger).
3. **Solar Wings:** in F1's sweep, when the player wears a damaged elytra, `canSolarWings()`, has sky
   access, and it's day (`world.isDay()` + `world.isSkyVisible(pos)`), heal a config durability amount;
   double the rate when `isOnGround()`. Rate-limit so it's a slow trickle, not an insta-repair.

## Registration specifics

- `PrimarySkillType.FLYING`; `MISC_SKILLS`.
- `SubSkillType`: `FLYING_GLIDE`, `FLYING_WIND_WALKER`, `FLYING_LIMITLESS`, `FLYING_SOLAR_WINGS`.
  (Do **not** add enum constants for the cut abilities — no dead enums.)
- `SuperAbilityType.LIMITLESS` (+ static-block `subSkillTypeDefinition = FLYING_LIMITLESS`).
- `experience.yml`: `Flying` XP modifier + bar color + AFK/exploit toggle.
- `advanced.yml`: Glide max/increment, Wind Walker max/increment, Limitless duration/speed/cooldown,
  Solar Wings repair rate + unlock.
- `config.yml`: a `Flying.Enabled` flag; if Bombing Jet/Winged Drill are ever added, their **own**
  config-off flags (not now).
- Locale block `Flying.*` (incl. Limitless On/Off/Refresh).

## Balance / XP tuning

- Distance-flown XP: elytra covers ground *fast*, so per-block XP must be much smaller than the ground
  skills or Flying maxes in one long glide. Tune against a §G number (fly a measured 1000 blocks).
- Wind Walker + firework: verify combined top speed (D-F2). Glide reduction near 25% can make landings
  weird — test terrain-following and that you can still descend.
- Solar Wings must be a *slow* trickle or it removes elytra durability as a resource entirely.

## Testing

- **Unit (MC-free):** `getGlideReduction`/`getWindWalkerBoost` at levels 0/mid/1000 (0 → … → 0.25 clamp);
  `getLimitlessDurationTicks`/`getLimitlessSpeed` scaling; `canSolarWings` gate; per-block XP.
- **§G rows:** fly 1000 blocks with an elytra → Flying XP delta (and confirm walking/falling without
  elytra pays nothing); at rank, glides last longer (Glide) and are faster (Wind Walker) without floating
  when you land; Limitless off an Eye of Ender gives an upward burst on cooldown; a damaged elytra slowly
  repairs standing in daylight (Solar Wings); firework + Wind Walker isn't game-breakingly fast (D-F2).
- **Regression:** old profile loads with Flying defaulted.

## Cuts / deferrals

- **Winged Drill / Demon Wings / Bombing Jet — CUT from v1 (D2).** If the user wants noclip mining, that
  is a separate project with its own plan; it is not a sub-skill you bolt on. Say so plainly.
- If velocity-nudge glide feels bad in play-test, the fallback is the `travel`/gravity mixin (D-F1) — a
  bigger job; scope it as a follow-up, don't let it block the other three sub-skills.
