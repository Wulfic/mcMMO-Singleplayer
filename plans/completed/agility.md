# Agility — Plan (the merged movement skill)

**Read [00-OVERVIEW.md](00-OVERVIEW.md) first.** This file supersedes the former `agility.md`
(Sprinting), `swimming.md` and `flying.md` — those three are **deleted**; their content is folded in
here. See D5 in the overview for the merge ruling.

---

## ✅ BUILD STATUS — Stages 0–5 are CODE-COMPLETE (2026-07-25)

All six stages are implemented, unit-tested, suite-green (850) and boot-verified (clean `runServer`
boot + verified shutdown, 0 exceptions, 0 mixin failures). **Nothing here has been played yet** —
the §G rows at the bottom of this file are the outstanding work, and the three reference speeds in
particular are still estimates.

Two things the plan assumed turned out to be **wrong**, both settled by bytecode rather than by
guessing. Read these before touching the water or air bodies:

### ⚠️ D-AG4 ANSWERED: `movement_speed` does NOT move a swimmer

`LivingEntity#travelInWater` computes swim speed as a flat `0.02`, and movement speed only
contributes *in proportion to* `WATER_MOVEMENT_EFFICIENCY`:

```
g = 0.02F;
waterEff = getAttributeValue(WATER_MOVEMENT_EFFICIENCY);   // 0 without Depth Strider
if (!isOnGround()) waterEff *= 0.5F;
if (waterEff > 0) { f += (0.546F - f) * waterEff;  g += (getMovementSpeed() - g) * waterEff; }
```

So with no Depth Strider the efficiency is **0** and a movement-speed modifier moves a swimming
player **not at all**. Fleet Footed's water body therefore targets `WATER_MOVEMENT_EFFICIENCY`
directly. Consequence worth knowing: that attribute is a `ClampedEntityAttribute` with **max 1.0**,
and Depth Strider III already reaches it — so a DS III player gains nothing from Fleet Footed water.
That is vanilla's cap doing the "cap it so it isn't silly" job for free, not a bug.

### ⚠️ The air body could NOT be a tick-sweep velocity write

The plan called for nudging velocity from F1 each tick. That does not work for a player's *own*
client: `EntityTrackerEntry` publishes velocity via `TrackerPacketSender#sendToListeners`, and for a
player entity the listeners are the **other** nearby players — the moving player never receives their
own velocity update. Server-side writes would be silently overwritten by the client's flight
simulation.

Resolved by splitting the two cases:
- **Continuous** air bonuses (Fleet Footed air, Glide) use `LivingEntityGlideMixin`, a
  `@ModifyExpressionValue` on `calcGlidingVelocity`'s return inside `travelGliding`. Both logical
  sides run it and compute the same factor, so there is nothing to sync and nothing to rubber-band.
- **One-shot** impulses (Second Wind's Dart and Limitless) set velocity and send an explicit
  `EntityVelocityUpdateS2CPacket` — what Bukkit's `Player#setVelocity` does. Correct for an impulse,
  wrong per-tick.

### Where each piece landed

| Piece | Home |
|---|---|
| XP clamp + budget | `skills/agility/MovementXpSettings` (MC-free, the highest-value tests) |
| Per-medium enum | `skills/agility/Medium` (priority AIR > WATER > LAND) |
| All 10 sub-skills' math | `AgilityManager` (still zero Minecraft imports) |
| F1 sampler | `fabric/listeners/PlayerMovementTracker` |
| F2 attribute service | `platform/SkillAttributeService` (temporary modifiers — can never reach the save file) |
| Athlete | `HungerManagerExhaustionMixin` → `AthleteListener` (gated on `isSprinting()`) |
| Smash | `EntityDamageListener#applySprintSmash` (existing melee seam, no new mixin) |
| Fleet Footed air + Glide | `LivingEntityGlideMixin` → `GlideListener` |
| Lake Raider | `BlockBreakListener#awardLakeRaiderTreasure` (reuses the Excavation tables) |
| Second Wind | `fabric/listeners/SecondWindListener` (does NOT use `checkAbilityActivation` — no tool) |
| Rename migration | `util/skills/SkillRenames` + `FlatFileProfileStore` + `ConfigLoader` warning |

Agility is **not a new skill.** It is the **existing, shipped, Pass-1 `ACROBATICS` skill renamed**, then
grown three new movement domains. That distinction drives the whole plan: Stage 0 is a rename with a
save-file migration, not a greenfield feature.

Wiki sources: `raw_site_text.md` §"Sprinting or 'Agility'", §"Swimming", §"Swimming, alternate idea",
§"Flying".

---

## ⚠️ 2026-07-27 RESTRUCTURE (user ruling) — Agility is now a CHILD skill

**Agility no longer earns XP.** It keeps all ten sub-skills, but its *level* — which is what every
rank gate reads — is derived as the mean of three new primary skills:

| New primary skill | Earns XP from | Feeds |
|---|---|---|
| **Parkour** | sprinting on land, **plus all Fall-domain XP** (Roll / Graceful Roll / Dodge) | Agility |
| **Swimming** | swimming | Agility |
| **Flying** | elytra gliding | Agility |

```
Agility level = (Parkour + Swimming + Flying) / 3
```

So 1000 Flying with 0 Parkour and 0 Swimming is **Agility 333**, and reaching Agility 1000 means
reaching 1000 in all three. This is the *existing* child-skill model (Salvage, Smelting) —
`PlayerProfile.getChildSkillLevel` was already `sum(parents) / parents.size()`, so no new formula was
invented, only new wiring.

**Consequences, stated plainly:**

- **A specialist cannot unlock their own domain's perks.** A pure flier caps at Agility 333, which
  locks Glide (350), Fleet Footed air (400) and Solar Wings (750) — three of the ten sub-skills —
  behind levelling the other two domains. **This is the intent**: the perks are an all-rounder's
  reward. Ruled, not an oversight.
- **Fall XP goes to Parkour alone** (`AgilityManager.EPISODIC_XP_SKILL`), not split three ways.
  Landing well is a land-movement thing, and splitting it would mean falling off a cliff trains your
  swimming. A player who only flies therefore gets nothing from Roll or Dodge either.
- **Existing Agility progress zeroes out.** A child skill has no save key — the profile store only
  reads and writes `NON_CHILD_SKILLS` — so `skills.AGILITY` is ignored on load and never rewritten.
  The `ACROBATICS` read-alias in `SkillRenames` was retired with it (the mechanism stays, the entry
  goes). Ruled: no migration.
- **Max power level rises by 2000** — Agility drops out of the power-level sum (children don't count)
  and three new skills enter it.
- **Child skills now fire milestone plaques.** They never did: a child levels without reaching the XP
  path, so `checkXp` only ever snapshotted the skill that literally levelled. Agility's ten sub-skill
  rank plaques would have gone permanently silent. `snapshotMilestones` now tracks the levelled skill
  *plus every child derived from it* — which quietly fixes Salvage and Smelting too.

---

## Concept

**One skill for how you move.** Four domains, one derived level, three XP pools:

| Domain | Trigger | XP goes to | Status |
|---|---|---|---|
| **Fall** | fall damage, being hit | **Parkour** | Acrobatics — **already built, playtested** (Roll, Graceful Roll, Dodge) |
| **Land** | sprinting (**not** walking, **not** crouched) | **Parkour** | new |
| **Water** | swimming (**not** crouched) | **Swimming** | new |
| **Air** | elytra gliding | **Flying** | new |

Why this is the right shape and not just a naming exercise:

- The three planned skills were **mechanically the same skill three times** — sample distance per tick,
  award XP per block, apply a speed modifier, gate on a rank. Three managers, three XP curves, three
  anti-AFK guards, three attribute-modifier lifecycles = three chances to leak a permanent speed buff.
- It **shrinks F1** (`PlayerMovementTracker`) from a four-way dispatcher to a single
  `AgilityManager.onMovementTick(medium, distance)` call with one accumulator and one guard set. The
  anti-AFK work is done once and protects all four sources.
- Acrobatics is currently a **two-sub-skill skill with no super ability** — one of the thinnest in the
  port. This gives it a full roster and a reason to level past Dodge.
- `/mcstats` goes from four sparse movement screens to one dense, sectioned one.

**Cost, stated honestly:** ten sub-skills on one level ladder is the **largest skill in the mod**, and
four XP sources feeding one bar is a real balance problem (D-AG6). Both are managed below; neither is
free.

---

## ⚠️ Stage 0 — the rename (do this ALONE, first, and play it)

`ACROBATICS` → `AGILITY` touches the enum name that **is the save-file key**. Do this as its own
landed, tested, playtested change with **zero new mechanics**. If Stage 0 and Stage 2 land together and
a profile comes back with a zeroed skill, you will not know which change did it.

### Java

| File | Change |
|---|---|
| `datatypes/skills/PrimarySkillType.java` | `ACROBATICS` → `AGILITY` (stays first alphabetically) |
| `datatypes/skills/SubSkillType.java` | `ACROBATICS_DODGE(1)` → `AGILITY_DODGE(1)`, `ACROBATICS_ROLL` → `AGILITY_ROLL`, comment block `/* ACROBATICS */` → `/* AGILITY */` |
| `skills/acrobatics/` | → `skills/agility/`; `AcrobaticsManager` → `AgilityManager`, `Acrobatics` → `Agility` (the static math helper) |
| `datatypes/skills/subskills/acrobatics/` | → `subskills/agility/` (`DodgeResult`, `RollResult`) |
| `datatypes/player/McMMOPlayer.java` | `case ACROBATICS -> new AcrobaticsManager(this)` (line ~170); `getAcrobaticsManager()` → `getAgilityManager()` (line ~204); the manager↔skill javadoc at line ~201 |
| `util/skills/SkillTools.java` | `MISC_SKILLS` entry (line ~105) |
| `commands/skills/AcrobaticsStatsRenderer.java` | → `AgilityStatsRenderer`; `SkillStatsRenderer.java:76` switch case |
| `config/AdvancedConfig.java` | `Skills.Acrobatics.*` keys (lines ~50-75, ~548-558) + the validation `reason.add(...)` strings |
| `config/experience/ExperienceConfig.java` | `ExploitFix.Acrobatics` (line 133), `Experience_Values.Acrobatics.*` (lines ~339-351, ~451-459); `isAcrobaticsExploitingPrevented()` → `isAgilityExploitingPrevented()` |
| `config/GeneralConfig.java` | `Skills.Acrobatics.Prevent_Dodge_Lightning`, `Skills.Acrobatics.XP_After_Teleport_Cooldown` (lines 411-417) |
| `fabric/client/modmenu/McMMOSettings.java` | the two skill-name arrays (lines 36, 43) |
| Callers/javadoc | `EntityDamageListener`, `LivingEntityDamageMixin`, `RuptureTask`, `ProbabilityUtil`, `Misc`, `PlatformPlayer`, `BlockLocationHistory` |

### Resources

- `experience.yml` — `ExploitFix.Acrobatics` (27), `Experience_Bars.Acrobatics` (60), the
  `Skill_Modifiers` entry (182), the `Diminished_Returns` entry (214), `Experience_Values.Acrobatics`
  (245).
- `advanced.yml` — `Skills.Acrobatics.{Dodge,Roll}` (110+).
- `skillranks.yml` — `Acrobatics.Dodge` (105).
- `config.yml` — lines 226, 247, 369.
- `coreskills.yml` — `Acrobatics:` (5).
- `locale_en_US.properties` — `JSON.Acrobatics`, `JSON.Acrobatics.Roll.*`, `Overhaul.Name.Acrobatics`,
  `XPBar.Acrobatics`, the `Acrobatics.*` block (17 keys, 141-159), `Commands.XPGain.Acrobatics`
  (→ retitle "Falling" to "Movement"), `Guides.Acrobatics.*` (974-977, rewrite for the new roster in a
  later stage — rekey now, rewrite content when the domains land).
- `data/mcmmo/advancement/milestone/{level,rank,maxed}/acrobatics.json` → `agility.json` ×3, and the
  `title`/`description` text inside each. `Milestones.key()` is `skill.name().toLowerCase(ROOT)`
  (`Milestones.java:131-132`), so the **file name must match the new enum name exactly** or every
  milestone plaque silently stops firing.

### The three migrations — none of these are optional

1. **Save files (the dangerous one).** `FlatFileProfileStore` persists by `skill.name()`
   (`FlatFileProfileStore.java:92-93,148-149`) with a default fallback. After the rename, an existing
   profile's `skills.ACROBATICS: 47` is simply **not read** and the player silently starts Agility at
   the starting level. Fix: in the load path, when `skills.AGILITY` is **absent** and `skills.ACROBATICS`
   is **present**, read the legacy key (same for `experience.`). Write only the new key — the next save
   drops the orphan naturally. **Add a load-an-old-profile regression test** with a checked-in fixture
   YAML that has the legacy keys; assert level *and* XP survive. The dev worlds under `run/saves/` are
   the live test bed — they already contain `ACROBATICS` entries.
2. **On-disk configs.** `copyMissingDefaults` back-fills only **absent** keys (see `[[xp-boss-bar]]`) —
   so a world's existing `config/mcmmo/*.yml` gains the new `Agility` blocks while the old `Acrobatics`
   blocks stay as dead orphans, and **any value the player tuned under the old key is silently
   ignored.** Recommended: don't write a config-rewriter; emit a one-line `LogUtils` warning at load
   when a legacy `Acrobatics` key is still present, telling the player to move their tuning. Cheap,
   honest, no rewrite risk on a file we don't own.
3. **Granted advancements.** `mcmmo:milestone/*/acrobatics` ids cease to exist; vanilla drops unknown
   entries from the player advancement file with a log line. Harmless — the player re-earns the rank
   plaque once. Document it, don't fix it.

### Stage 0 done-criteria

Full suite green · `runServer` boot clean (gate the piped `stop` on `Done (`, never a sleep —
`[[placed-block-persistence]]`) · migration regression test green · ModMenu key-validation test green
(it will catch every config key you missed — that is exactly what it's for, `[[modmenu-integration]]`)
· **a real client run** on an existing `run/saves` world confirming the old Acrobatics level and XP are
intact under the new name, Roll/Dodge still fire, and the XP bar reads `Agility Lv.X`.

---

## Sub-skill roster (10)

Ranks are authored in **RetroMode** numbers (overview checklist item 12); Standard is the ÷10 view.

| # | Sub-skill | Domain | Type | Mechanic | Unlock | Status |
|---|---|---|---|---|---|---|
| 1 | **Roll** (+ Graceful Roll) | Fall | passive | Negate fall damage; sneak doubles odds + threshold | 1 | **shipped** |
| 2 | **Dodge** | Fall | passive | Halve incoming combat damage | 1 | **shipped** |
| 3 | **Fleet Footed** | all 3 | passive | Move faster in whatever medium you're travelling through — 3 ranks, one per medium | 1 / 200 / 400 | new |
| 4 | **Athlete** | Land | passive | Sprinting costs less hunger | 50 | new |
| 5 | **Smash** | Land | passive | Sprint-attacks crit / extra knockback | 150 | new |
| 6 | **Lead Lungs** | Water | passive | Hold breath far longer underwater | 250 | new |
| 7 | **Second Wind** | all 3 | **super** | One cooldowned active, dispatched on your movement state — 3 ranks, one per medium body | 250 / 500 / 750 | new |
| 8 | **Glide** | Air | passive | Descend slower while gliding | 350 | new |
| 9 | **Lake Raider** | Water | passive | Underwater block-break treasure | 500 | new (**cuttable**) |
| 10 | **Solar Wings** | Air | passive | Elytra slowly repairs in daylight | 750 | new |

Something unlocks roughly every 50–250 levels — the ladder is the point of merging.

### Sub-skill 3 — Fleet Footed (replaces Dash + Swim Training + Wind Walker)

Three separate "you move faster in medium X" passives under one skill would mean three rank ladders,
three config blocks, three locale blocks and **three attribute-modifier identities to leak**. One
sub-skill, three ranks (land @1, water @200, air @400), one config block with a **per-medium cap**.

**Wrinkle, stated up front:** the implementation is not uniform across the three.

- **Land** — a managed `movement_speed` modifier via F2, applied only while `isSprinting()`.
- **Water** — *verify first* that `movement_speed` actually moves swim speed in 1.21.11 (D-AG4). If it
  doesn't, the water body is a Dolphin's-Grace-style effect or a velocity nudge instead.
- **Air** — elytra flight is **velocity-driven, not attribute-driven**. The air body is a per-tick
  look-vector velocity nudge, not a modifier.

So: one sub-skill, one rank ladder, one config block, **two application mechanisms**. That is a fair
trade against three of everything, but write it down in the class javadoc so the next reader isn't
surprised.

### Sub-skill 7 — Second Wind (replaces Dart + Aquaman + Limitless)

Every other skill in the mod has **at most one** super ability. Three actives on one skill means three
enums, three cooldown slots, three locale blocks, three `/mcability` lines — for what is, from the
player's seat, "the Agility button." One `SuperAbilityType.SECOND_WIND`, dispatched on movement state:

| State on activation | Body | Effect |
|---|---|---|
| sprinting on land | **Dart** | forward lunge; raycast N blocks, damage + strong knockback to `LivingEntity`s hit; brief Slowness at high rank ("stun") |
| in water | **Aquaman** | Strength + Regeneration + Night Vision for a scaling duration, **while in water** |
| gliding | **Limitless** | upward + forward burst, sustained speed for a scaling duration |
| anything else | — | refuse with a "you need to be moving" notification and **do not consume the cooldown** |

**Trigger:** right-click while holding a **Feather** (config-settable item id, **not consumed**). It
matches the milestone advancement icon, it's cheap, and it's one trigger instead of the wiki's Raw
Cod / Eye of Ender split. Cooldown/duration ride the existing super-ability infra
(`[[phase-11-2-superability-cooldown]]`).

**v1 may ship the Dart body only** and add the water/air bodies in Stage 3/4 with **zero enum, config
or locale churn** — that's the main structural payoff of one dispatching ability over three.

### Cut — do not build, do not add dead enums

| Cut | Why |
|---|---|
| ~~Multi-jump~~ | No clean server-side jump hook; fighting vanilla movement = desync/rubber-band. (D2, ruled) |
| ~~Winged Drill / Demon Wings~~ | Noclip mining. Not server-authoritative, grief/dupe vector. Its own multi-week project if ever. (D2, ruled) |
| ~~Bombing Jet~~ | Auto-lit TNT while flying. Griefy; if ever built, config-off, not v1. (D2, ruled) |
| ~~Dash / Swim Training / Wind Walker~~ | Folded into **Fleet Footed**. |
| ~~Dart / Aquaman / Limitless~~ (as separate abilities) | Folded into **Second Wind** as its three bodies. |
| ~~Water Sprint / Diving / Underwater Survival~~ | Wiki "alternate idea" duplicates of Fleet Footed + Lead Lungs. Do not ship eight overlapping water knobs. |

---

## MC-free core (`AgilityManager extends SkillManager`)

Grown from the existing `AcrobaticsManager` — keep every current method (`processFallDamage`,
`rollCheck`, `canGainRollXP`, `canRoll`, `canDodge`, `processDodge`, `dodgeCheck`, `calculateRollXP`,
the exploit throttle) and add:

```
float  onMovementTick(Medium medium, double distance)  // LAND | WATER | AIR — one entry point
double creditedSeconds(Medium medium, double distance) // the speed clamp, split out to be provable
double getFleetFootedBonus(Medium medium)              // clamped, per-medium cap; 0 if that rank is locked
double getAthleteExhaustionMultiplier()                // 1 - min(maxReduction, level * perLevel); never 0
boolean rollSmash()                                    // ProbabilityUtil, standard rank+permission gate
int    getLeadLungsAirTopUp()                          // air ticks restored/preserved per tick submerged
boolean rollLakeRaiderTreasure()                       // pinned-RNG testable
double getGlideDescentReduction()                      // clamped downward-velocity factor
boolean canSolarWings()                                // rank gate; repair rate is config
SecondWindResult computeSecondWind(Medium medium)      // damage/knockback/duration/effect levels by level+medium
```

`Medium` is a small MC-free enum in the manager's package. **Zero Minecraft imports**, same as today —
that property is what makes all of the above unit-testable, and it is the single rule this class has
never broken. Don't break it for a `Vec3d`.

---

## MC-typed trigger layer

1. **All movement XP + Fleet Footed** — F1 classifies the medium (sprinting / in-water / gliding),
   rejects vehicles, teleports and Δ≈0, and calls `onMovementTick(medium, distance)` once. In the same
   tick, F2 applies or removes the **single** `mcmmo:agility_fleet_footed` speed modifier from
   `getFleetFootedBonus(medium)` — **removed the tick the medium ends.** The air body writes velocity
   instead (see above) and must touch velocity **only while `isGliding()`**, or you will make a walking
   player float.
2. **Athlete** — mixin the sprint exhaustion in `HungerManager#addExhaustion`
   (`@ModifyArg`/`@ModifyVariable`), scaled by `getAthleteExhaustionMultiplier()`, **gated on
   `isSprinting()`** so you don't discount every source of exhaustion. Verify the call site with
   `scripts/javap-mc.sh` (`[[javap-mc-script]]`); cap slice-anchored injectors with `allow=N`
   (`[[mixin-slice-allow-guard]]`).
3. **Smash** — fold into the **existing** melee path in `EntityDamageListener`: attacker is a player,
   `isSprinting()`, `rollSmash()` → bonus damage + knockback. Do not add a second damage mixin; the
   damage seam is already inside `damage()` (see `[[combat-xp-model-decision]]`).
4. **Roll / Dodge** — unchanged; already live through `modifyAppliedDamage`.
5. **Lead Lungs** — F1 tops up air while submerged, respecting the vanilla max-air cap and stacking
   sanely with Respiration (D-AG4). Prefer the per-tick top-up over mixing the air-decrement — simpler,
   and it degrades gracefully. Verify the air API with `javap-mc.sh`.
6. **Lake Raider** — in `BlockBreakListener`, when the player is submerged and the roll succeeds, drop
   treasure through the existing `ItemSpecBuilder`/`TreasureConfig` machinery
   (`[[phase-3-woodcutting-excavation-drops]]`). Do not reinvent an item-spawn path.
7. **Glide** — in F1's gliding branch, scale the negative-Y velocity component by
   `getGlideDescentReduction()`. Velocity nudge, not a `travel`/gravity mixin (D-AG3 fallback).
8. **Solar Wings** — in F1's sweep: worn elytra is damaged, rank unlocked, `world.isDay()` and
   `world.isSkyVisible(pos)` → heal a config durability amount, doubled on the ground. Rate-limit hard;
   this must be a trickle or elytra durability stops being a resource.
9. **Second Wind** — a use-item listener in `fabric/listeners/` registered in `McMMOMod.onInitialize`;
   dispatch on the F1-classified medium; refuse (no cooldown burn) when stationary.

---

## Registration specifics

- `PrimarySkillType.AGILITY` (rename); `MISC_SKILLS` (already there).
- `SubSkillType`: rename `AGILITY_DODGE(1)`, `AGILITY_ROLL`; add `AGILITY_FLEET_FOOTED(3)`,
  `AGILITY_ATHLETE`, `AGILITY_SMASH`, `AGILITY_LEAD_LUNGS`, `AGILITY_SECOND_WIND(3)`, `AGILITY_GLIDE`,
  `AGILITY_LAKE_RAIDER`, `AGILITY_SOLAR_WINGS`. None collide with a `PrimarySkillType` name (the
  warning at the top of that file).
- `SuperAbilityType.SECOND_WIND` — the 6-arg locale constructor, plus
  `SECOND_WIND.subSkillTypeDefinition = SubSkillType.AGILITY_SECOND_WIND` in the static block
  (`SuperAbilityType.java:115-118`). It then flows through `buildSuperAbilityMaps()` automatically —
  **verify**, and note Agility is the mod's first non-tool-gated super ability, so check
  `buildPrimarySkillToolMap()` doesn't assume one.
- `experience.yml` — rekey `Agility`; add `Experience_Values.Agility.{Sprint,Swim,Glide}` per-block
  values alongside the existing `{Dodge,Roll,Fall,FeatherFall_Multiplier}`; keep the single
  `Diminished_Returns` and `Skill_Modifiers` entries (now covering all four sources — see D-AG6);
  extend `ExploitFix.Agility` to cover the movement AFK gate.
- `skillranks.yml` — the 8 new sub-skills' unlock ladders (table above), Standard + RetroMode.
- `advanced.yml` — `Skills.Agility.{FleetFooted,Athlete,Smash,LeadLungs,SecondWind,Glide,LakeRaider,SolarWings}`
  plus the existing `{Dodge,Roll}`.
- `config.yml` — `Agility.Enabled`; the Second Wind trigger item id.
- Locale — the `Agility.*` block: `SkillName`, per-sub-skill `.Name`/`.Description`/`.Stat`, and
  `Agility.Skills.SecondWind.{On,Off,Other.On,Refresh,Other.Off}`. Mirror an existing block's key shape
  exactly; the parser is strict. **Never hand a hand-built string to `TextUtils.toText` — normalise `&`
  codes through `LocaleLoader.addColors` first** (`[[mcstats-per-skill-command]]`, first playtest bug).
- ModMenu — register every new key with the key-validation test.

### `/mcstats agility` will be the longest screen in the mod

Ten sub-skills in one flat list is unreadable. Render `AgilityStatsRenderer` in **four labelled
sections** — Falling / Land / Water / Air — with Fleet Footed and Second Wind showing one line per
unlocked medium rank. Grep an existing renderer for the section idiom before inventing one
(`[[mcstats-per-skill-command]]`).

---

## Design decisions (need a ruling — recommended answer assumed by this plan)

- **D-AG1 — rename migration.** *Recommended: alias-read the legacy save key + warn on orphan config
  keys.* The alternative (accept the reset) throws away every playtest profile in `run/saves` and any
  real world. Cost of the alias is ~10 lines and one fixture test.
- **D-AG2 — one Second Wind or three actives?** *Recommended: one, context-dispatched.* Three separate
  actives are three cooldowns and three config/locale blocks for one player-facing button. Overrule if
  you specifically want three distinct named abilities on the ability list.
- **D-AG3 — Fleet Footed unified or three speed passives?** *Recommended: unified (3 ranks).* Overrule
  if per-medium unlock flavour matters more than one modifier identity — but understand the cost is
  three leak-able modifiers, which is the #1 failure mode of this whole class of feature.
- **D-AG4 — vanilla overlap + API verification.** Balance calls, not bugs. **Verify** whether
  `movement_speed` actually affects swim speed in 1.21.11 (`javap-mc.sh`) before designing the water
  body. Then decide the stacking rules: Fleet Footed vs **Depth Strider** / **Dolphin's Grace**, Lead
  Lungs vs **Respiration**, air Fleet Footed + Glide vs **firework rockets**. Recommended: stack
  additively but **cap**, so a Depth Strider III + max Agility player is fast, not silly, and Lead Lungs
  + Respiration III approaches but doesn't trivially exceed infinite air.
- **D-AG5 — does Stealth join too?** ✅ **RULED 2026-07-25 (user): NO. Stealth stays its own skill.**
  Sneaking is distance-sampled too, but its payoff (mob aggro, backstab) is a *not-being-seen* fantasy,
  not a locomotion one, and folding it in would push Agility to 14 sub-skills. Locked; do not re-open.
  **The overlap still has to be resolved:** Stealth's **Padfoot** (sneak speed) and Agility's **Fleet
  Footed** are the same mechanic on the same attribute. Padfoot uses its **own** modifier identity
  (`mcmmo:stealth_padfoot`), the two are never both live, and **sneak-swimming must be verified** — it
  is a real state, and it is the one case where "you can't sneak and travel a medium at once" might not
  hold. See [stealth.md](stealth.md) D-S2.
- **D-AG6 — the XP budget.** ✅ **RULED 2026-07-25 (user): budget widened substantially, and every
  medium's payout is normalised against its own average top speed so nothing levels ridiculously fast.**
  This is a big enough piece of design to get its own section — see **"XP: the speed-normalised
  budget"** below. It is now the load-bearing balance decision of the whole skill.

---

## XP: the speed-normalised budget (D-AG6)

### The trap this exists to avoid

Paying a flat *XP-per-block* is wrong, and wrong in a way that compounds:

1. **Fast media firehose.** An elytra covers ground ~5× faster than sprinting and ~10× faster than
   swimming. At any shared per-block rate, gliding levels the skill an order of magnitude faster than
   swimming for strictly less effort.
2. **Every speed buff becomes an XP multiplier.** Depth Strider, Dolphin's Grace, Speed potions, ice
   boats, firework rockets — all of them raise blocks-per-second, so all of them raise XP-per-second.
3. **The skill accelerates its own levelling.** This is the killer: **Fleet Footed makes you faster,
   which earns you more XP per second, which levels Fleet Footed.** A positive feedback loop inside a
   single skill. That alone is enough to make levelling "ridiculously fast," and no amount of tuning
   the per-block constant fixes a feedback loop.

### The rule

> **Distance is the sensor. Time is the currency.**
> Agility pays **XP per second of qualifying travel**, and a tick's distance is **clamped at the
> medium's reference speed** before it is credited.

```
refDist        = referenceSpeed(medium) / 20              // blocks the reference speed covers in a tick
creditedSecs   = min(distance, refDist) / referenceSpeed(medium)
xp             = baselineXpPerSecond * mediumMultiplier(medium) * creditedSecs
```

- Travelling **at or above** the reference speed → the full rate, never more. All three problems above
  die at once: rockets, Depth Strider, Speed II and Fleet Footed itself stop being XP multipliers.
- Travelling **slower** → pro-rata. Wading, a slow glide and a gentle jog still pay, proportionally.
- **Standing still → zero**, which F1's Δ≈0 guard already enforces.
- **Per-block XP is a derived quantity.** Nobody hand-tunes it; it falls out of the reference speed.

**The clamp is MC-free math and belongs in `AgilityManager`, not F1.** F1 owns only the platform-y
guards (vehicle, teleport-scale delta, Δ≈0). The clamp is the part most likely to be got wrong, so it
must be the part that is unit-testable.

### Reference speeds — ⚠️ MEASURE THESE, DO NOT SHIP THEM ON MY WORD

Every one of these is a **config value**, not a constant, precisely so tuning is a YAML edit and the
§G playtest can correct them. Starting values:

| Medium | Qualifying state | Reference speed | Confidence |
|---|---|---|---|
| **Land** | `isSprinting()` + moving | **5.61** b/s (walk 4.317 × 1.3) | High — well-known vanilla value |
| **Water** | `isTouchingWater()` + moving | **3.16** b/s (swim pose, no enchants) | **Low — measure it** |
| **Air** | `isGliding()` | **30.0** b/s (unboosted cruise) | **Low — measure it**; rocket-boosted and dive speeds are far higher, which is exactly what the clamp is for |

**Sprint-jumping** is ~27% faster than flat sprinting and is what players actually do. Under the clamp
it pays *the same as sprinting* — deliberate. Do not "fix" this.

### Baseline and the resulting budget

Total XP to reach RetroMode level *N* on the shipped LINEAR curve (`base 1020`, `multiplier 20`,
`experience.yml:145-147`) is `sum(1020 + 20L)` = **`10N² + 1010N`**, so **max level 1000 = 11,010,000
XP**. Everything below derives from that number.

**`Baseline_Xp_Per_Second: 15.0`** (halved 2026-07-27 — see below), with per-medium multipliers
**Land 1.0 / Water 1.15 / Air 0.6** (water is slow and tedious, air is near-effortless and covers the
world):

| Medium | XP/s | ⇒ derived XP/block | Hours of *nothing but this* to max |
|---|---|---|---|
| Land | 15.0 | 2.67 | **~204 h** |
| Water | 17.25 | 5.46 | **~178 h** |
| Air | 9.0 | 0.30 | **~340 h** |

Note the derived per-block figures: air pays **~9× less per block** than land — 5.3× of that from the
speed normalisation and 1.67× from the multiplier. That ratio is *computed*, not guessed, and it stays
correct automatically if you retune a reference speed.

### ⚠️ 2026-07-27 retune (user ruling) — halved, and two states pay nothing

The baseline went **30.0 → 15.0**, doubling every time-to-max above. Rationale: movement is the most
*passive* source in the mod — it pays for playing the game normally, with no tool, no resource and no
decision — so it must not out-earn a skill you have to actively work at.

Two movement states were also ruled out entirely, and they are **not** media at any rate:

- **Walking pays nothing.** Only `isSprinting()` counts on land. It was already this way; it is now
  written down as intentional rather than incidental, because "add a walk medium at a tenth" was
  considered and **rejected** — simply existing in the world must never level a skill.
- **Crouched movement pays nothing, in every medium.** This one was a real leak: sneaking excluded
  itself on land for free (you cannot sneak and sprint), but holding shift to sink is still
  `isTouchingWater()`, so **crouch-swimming used to earn Agility XP**. Sneaking is the Stealth skill's
  sensor and one movement state must not feed two skills — the same double-pay problem the
  AIR > WATER > LAND priority exists to solve, one level up. Fixed in `classifyMedium`, so it also
  drops Fleet Footed and refuses Second Wind while crouched, which pre-settles the Padfoot overlap in
  D-AG5 in exactly one place.

**Guardrail — the actual definition of "not ridiculously fast":** no single source may take a player
from 0 to max in under **80 hours** of doing only that thing. All three clear it.

The ladder still opens quickly, because the linear curve is cheap early (level 1 costs 1,020 XP; level
1000 costs 21,020). Continuous-land-travel times to each unlock:

| Unlock | Level | Cumulative XP | ≈ Time |
|---|---|---|---|
| Fleet Footed (land) | 1 | 1,020 | ~1 min |
| Athlete | 50 | 75,500 | ~1.4 h |
| Smash | 150 | 376,500 | ~7 h |
| Fleet Footed (water) | 200 | 602,000 | ~11 h |
| Lead Lungs · **Second Wind** | 250 | 877,500 | ~16 h |
| Glide | 350 | 1,578,500 | ~29 h |
| Fleet Footed (air) | 400 | 2,004,000 | ~37 h |
| Lake Raider · SW water body | 500 | 3,005,000 | ~56 h |
| Solar Wings · SW air body | 750 | 6,382,500 | ~118 h |
| Max | 1000 | 11,010,000 | ~204 h |

Real play is faster than the right-hand column — you are never travelling 100% of the time, but fall
and dodge XP stack on top.

### ⚠️ The merge changed what the *existing* Acrobatics XP values buy

`experience.yml:245+` currently ships `Dodge: 800`, `Roll: 600`, `Fall: 600` — **not** the 120/80/120
in `ExperienceConfig`'s Java defaults; the YAML wins. These are **multipliers on damage**, not flat
awards: `xp = damage × modifier` (damage clamped to 20 for falls), doubled again by
`FeatherFall_Multiplier: 2.0`. So a 20-damage graceful roll in Feather Falling boots is **24,000 XP** —
the equivalent of ~13 minutes of sprinting, from one jump.

Those numbers were balanced when Acrobatics had **two** sub-skills and one skill's worth of payoff.
They now buy ten sub-skills across four domains. `AgilityManager.canGainRollXP()`'s lengthening
cooldown is the only thing throttling repeat falls. **Re-measure the episodic side against the 11.01M
budget in §G and expect to cut these values**, or the fastest route to max Agility will be a
water-bucket tower, not moving at all.

### The `Diminished_Returns` governor

`Enabled: false` in the shipped YAML, threshold `20000` per 10 min (= 33 XP/s) — coincidentally right
on top of the 30 XP/s baseline, so switching it on would throttle Agility almost immediately. If it is
ever enabled, **Agility's threshold must be raised** (it aggregates four sources where every other
skill has one), otherwise Agility alone gets punished. The clamp is the real governor; diminishing
returns is a backstop, not the plan.

### Config shape

```yaml
Experience_Values:
    Agility:
        Dodge: 800            # existing — re-measure (see above)
        Roll: 600             # existing — re-measure
        Fall: 600             # existing — re-measure
        FeatherFall_Multiplier: 2.0
        Movement:
            Baseline_Xp_Per_Second: 15.0
            Reference_Speed:        # blocks/second — MEASURED, not guessed
                Land:  5.61
                Water: 3.16
                Air:   30.0
            Medium_Multiplier:
                Land:  1.0
                Water: 1.15
                Air:   0.6
```

Cache these reads — this is per-tick code, and the Alchemy Catalysis per-tick-config-read trap
(`[[alchemy-catalysis]]`) applies verbatim.

---

## Balance / other tuning

- **One medium per tick, priority AIR > WATER > LAND.** A player can be sprinting *and* in water, or
  gliding *into* water. Pick exactly one and never double-pay; state the priority in the manager
  javadoc and unit-test the overlap cases.
- **Anti-AFK is now single-point critical** — and single-point *fixable*. A bubble elevator, a soul-sand
  column, flowing water and a firework circuit all move the player without input. F1's guards (no
  vehicle, reject teleport-scale deltas, require real Δ) cover all four sources at once, and the speed
  clamp caps the damage even if one leaks. Add a §G row that actively tries to cheese each.
- **Athlete must never make sprinting free** — cap `MaxReduction < 1.0`.
- **Fleet Footed on land stacks on top of the vanilla sprint multiplier** — verify max rank doesn't trip
  movement checks and rubber-band. (It no longer feeds its own XP — see the clamp.)
- **Glide near max reduction can make landing weird** — confirm you can still descend and terrain-follow.
- **Solar Wings must be a slow trickle** or elytra durability stops mattering.

---

## Build stages — one lands *fully* before the next starts

Each stage = code + config + locale + unit tests + green suite + clean boot + its §G rows played. No
half-wired domains sitting in the tree; that was the entire lesson of Pass 1's "boot-verified, never
played" debt.

| Stage | Content | Depends on |
|---|---|---|
| **0** | **The rename + save migration. No new mechanics.** | Pass-1 §G playtest done |
| **1** | **F1 `PlayerMovementTracker` + F2 `SkillAttributeService`** (overview), tested, no skill behaviour yet | 0 |
| **2** | **Land**: sprint XP + Fleet Footed(land) + Athlete + Smash | 1 |
| **3** | **Water**: swim XP + Fleet Footed(water) + Lead Lungs [+ Lake Raider] | 2 |
| **4** | **Air**: glide XP + Fleet Footed(air) + Glide + Solar Wings | 2 |
| **5** | **Second Wind**: Dart body, then the water + air bodies | 2–4 |

Stage 1 is materially smaller than the old plan: one dispatch target, one accumulator, one modifier
identity.

---

## Testing

**Unit (MC-free, `AgilityManager`)** — everything the current `AcrobaticsManagerTest` covers stays
green under the new names, plus: `getFleetFootedBonus` per medium at levels 0/mid/cap (0 → … → clamp,
and **0 when that medium's rank is locked**); `getAthleteExhaustionMultiplier` clamps and never returns
0; `rollSmash` and `rollLakeRaiderTreasure` with pinned RNG (0 → always, 100 → never);
`getLeadLungsAirTopUp` scaling + cap; `getGlideDescentReduction` clamp; `canSolarWings` gate;
`computeSecondWind` per medium.

**Unit — the XP clamp (D-AG6), the highest-value tests in the skill):**
- `creditedSeconds` at **exactly** the reference speed → `1/20` s; at **half** → `1/40` s; at **10×**
  (a rocket boost, Dolphin's Grace, an ice-boat-scale delta) → still `1/20` s, **never more**;
  at 0 → 0.
- The anti-feedback-loop property, asserted directly: **the same tick-distance pays the same XP at
  level 1 and at level 1000.** Fleet Footed must not be able to raise its own XP rate. If this test
  ever goes red, the loop is back.
- `onMovementTick` XP/second matches `Baseline_Xp_Per_Second × Medium_Multiplier` for each medium, and
  the derived XP-per-block matches the table above (2.67 land / 5.46 water / 0.30 air at defaults).
- Medium priority: a tick that is simultaneously gliding + in water + sprinting pays **once**, as AIR.
- Walking pays nothing; **crouching pays nothing in every medium** (`PlayerMovementTrackerTest`).
- Budget regression: `10N² + 1010N` at N=1000 is 11,010,000, and land-only time-to-max stays ≥ 80 h at
  the shipped defaults — a cheap arithmetic test that fails loudly if someone "just bumps" the baseline.

**Migration (Stage 0, non-negotiable)** — fixture profile with `skills.ACROBATICS` / `experience.ACROBATICS`
loads with the level *and* XP intact under `AGILITY`; a profile with **both** keys prefers the new one;
a profile with **neither** defaults cleanly (overview item 19).

**F1** — zero-delta pays nothing in every medium; vehicle (boat/horse/minecart) pays nothing; a
teleport-scale delta is rejected and resets `lastPos`; a bubble-elevator ride pays nothing.

**F2** — the Fleet Footed modifier is idempotent under re-application; removed the tick the medium ends;
cleared on logout **and** on the entity-recreation paths (respawn / End-exit both build a new
`ServerPlayerEntity` — `[[respawn-stale-handle]]`).

**§G rows** (add to a Pass-2 `PLAYTEST_G2.md`):
- Stage 0: existing world keeps its Acrobatics level/XP as Agility; Roll and Dodge still fire; XP bar
  reads `Agility Lv.X`; `/mcstats agility` renders; milestone plaque still pops.
- **Measure the three reference speeds** (D-AG6) — sprint, swim and unboosted glide over a
  known distance, timed — and write the real numbers into `experience.yml`. Everything else in the XP
  budget is downstream of these three.
- **Clamp verification, in-world:** a timed minute of sprinting and a timed minute of *rocket-boosted*
  gliding both pay their medium's stated XP/second; Depth Strider III / Dolphin's Grace / Speed II do
  **not** increase XP per second. Then the loop check: level up Fleet Footed and confirm XP/second is
  unchanged.
- Sprint 200 blocks → XP delta matches ~5.35/block; sprinting into a wall pays nothing; sprint-jumping
  pays the same as sprinting; hunger drains visibly slower at Athlete rank; a sprint-attack
  occasionally crits/flings (Smash); speed drops the instant you stop.
- Swim 200 blocks → XP delta matches ~10.9/block; a bubble elevator farms nothing; breath lasts much
  longer (Lead Lungs); swimming is faster and the bonus drops the instant you leave water; underwater
  block-breaks occasionally drop treasure (Lake Raider). **Check sneak-swimming** — confirm Padfoot and
  Fleet Footed don't both apply (D-AG5).
- Glide 1000 blocks → XP delta matches ~0.60/block; walking/falling without an elytra pays nothing;
  glides last longer and are faster without floating on landing; a damaged elytra slowly repairs in
  daylight (Solar Wings); firework + Fleet Footed(air) isn't game-breaking (D-AG4).
- **Episodic re-measure:** roll off a 20-block drop in Feather Falling boots and record the XP against
  the 11.01M budget; farm falls for 10 minutes and confirm `canGainRollXP()`'s lengthening cooldown
  makes it a worse rate than simply travelling. If it doesn't, cut `Roll`/`Fall`.
- Second Wind: lunges while sprinting, buffs in water, boosts while gliding, **refuses without burning
  the cooldown** when standing still; shows the On/Off/Refresh messages.

---

## Cuts / deferrals (summary)

- Multi-jump, Winged Drill / Demon Wings, Bombing Jet — **cut** (D2, already ruled). No dead enums.
- Dash / Swim Training / Wind Walker — folded into **Fleet Footed**.
- Dart / Aquaman / Limitless — folded into **Second Wind**'s three bodies; the water and air bodies may
  slip to Stage 3/4 with no config churn.
- Water Sprint / Diving / Underwater Survival — dropped as duplicates.
- **Lake Raider is the cut candidate if the roster is too big** — it's an Excavation-flavoured mechanic
  wearing a movement skill's coat, and it's the only sub-skill here that doesn't change how you move.
- If velocity-nudge Glide feels bad in play-test, the fallback is a `travel`/gravity mixin — scope it as
  a follow-up, don't let it block Stages 2–3.
