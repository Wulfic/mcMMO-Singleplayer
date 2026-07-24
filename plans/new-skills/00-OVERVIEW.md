# New Skills — Overview, Shared Foundation & Registration Checklist

Source of ideas: `scripts/raw_site_text.md` (a scrape of
<https://mcmmo.fandom.com/wiki/Suggested_Skills>). These are **community suggestions**, not shipped
mcMMO features, so the wiki text is rough — half of it is jokes, half of it is unbalanced, and several
"abilities" are outright unshippable in a server-authoritative 1.21.11 world. Every plan file below
takes the wiki as a *starting point* and makes an actual engineering decision. Do not implement the
wiki verbatim.

This is Pass 2. Pass 1 (the Bukkit→Fabric port of the 19 stock skills) is feature-complete and
boot-verified — see `PLAYTEST_G.md`. **Do not start Pass 2 code until §G play-testing has actually
run.** Building six brand-new skills on top of a port that has never been played once is how you ship
six new skills' worth of bugs on top of an unverified base.

## The six skills

| Plan | Skill | Parent(s) per wiki | XP trigger | Headline risk |
|---|---|---|---|---|
| [agility.md](agility.md) | **Agility** (Sprinting) | standalone | distance sprinted | Multi-jump is unshippable; Dart needs a raycast active |
| [husbandry.md](husbandry.md) | **Husbandry** (+ Shearing) | Herbalism + Taming | breeding + shearing | No Fabric breed/shear event — needs mixins; child-vs-standalone fork |
| [stealth.md](stealth.md) | **Stealth** (Sneaking) | standalone | distance sneaked | "Thief" mob-blindness is hard + overlaps vanilla; anti-AFK critical |
| [unarmored.md](unarmored.md) | **Unarmored** | standalone | damage taken w/ no armor | Managed armor attribute + equip/unequip reactivity |
| [flying.md](flying.md) | **Flying** (Elytra) | Cartography + Acrobatics | distance elytra-flown | Winged Drill (noclip mining) must be cut from v1 |
| [swimming.md](swimming.md) | **Swimming** | Fishing + Acrobatics | distance swum | Overlaps Depth Strider / Dolphin's Grace; child-vs-standalone fork |

Read this file first, then the per-skill file. This file owns everything the six share: the two pieces
of **net-new foundation**, the **"add a PrimarySkillType" checklist**, and the **cross-cutting design
decisions** that need a human ruling before anyone writes a manager.

---

## ⚠️ Cross-cutting design decisions (need a ruling before coding)

These are the forks that change the *shape* of multiple plans. Resolve them once, here, and record the
ruling in memory (`[[conversion-overview]]` style). Each per-skill file assumes the **recommended**
answer unless told otherwise.

### D1 — Child skill vs standalone skill (Husbandry, Swimming, Flying)

> ✅ **DECIDED 2026-07-24 (user ruling): STANDALONE skills.** Husbandry, Swimming and Flying are full
> primary skills with their own XP. Do **not** add them to `SkillTools.isChildSkill`. Locked; do not
> re-open.

The wiki calls Husbandry (Herbalism+Taming), Swimming (Fishing+Acrobatics) and Flying
(Cartography+Acrobatics) **child skills**. In this codebase a child skill:
- has **no independent XP**; its level is *derived* from its parents (`PlayerProfile.getChildSkillLevel`),
- routes any `addXp` **into the parents, split evenly** (`PlayerProfile.addXp`, line 373-379),
- is hard-coded in `SkillTools.isChildSkill(...)` (currently only `SALVAGE`, `SMELTING`).

That model is **incompatible** with what the wiki actually describes: every one of these skills has its
own XP source (breeding, swimming distance, elytra distance) and its own level-scaled abilities. You
cannot have "gain XP by swimming" *and* "level is just the average of Fishing and Acrobatics."

**Recommendation: make all three STANDALONE primary skills with their own XP.** This is exactly what
upstream mcMMO did when it promoted Salvage and Smelting from child skills to full skills. Cleaner XP,
cleaner `/mcstats`, no divide-XP-into-parents surprises. The "child" label from the wiki is cosmetic
lore, not a mechanic.

**If instead you want true child skills** (level = f(parents), no own XP), then Husbandry/Swimming/Flying
lose their entire XP-source design and become pure passive-bonus layers on top of the parents — a much
smaller, much less interesting feature. Flag it now; do not discover it in code review.

→ **Every per-skill file below assumes STANDALONE.**

### D2 — Which mega-abilities get cut from v1

> ✅ **DECIDED 2026-07-24 (user ruling): all cuts approved.** Multi-jump, Winged Drill/Demon Wings and
> Bombing Jet are OUT of v1; Thief ships reduced or is deferred. No dead enums for the cut abilities.

Several wiki "abilities" are either unshippable or exploit factories in a server-authoritative world.
Default stance: **cut from v1, config-gated `false`, revisit later.** Per-skill files mark each.

- **Agility → Multi-jump** (double/triple jump): no clean server-side jump hook; fighting the vanilla
  movement checks means desync and rubber-banding. **CUT.**
- **Flying → Winged Drill / Demon Wings** (fly *through* stone/dirt/netherrack, auto-mining): this is
  noclip mining. It cannot be done server-authoritatively without the client desyncing hard, and it is
  a grief/dupe vector. **CUT from v1.** If ever built, it is its own multi-week project.
- **Flying → Bombing Jet** (drop auto-lit TNT while flying): shippable but griefy. **Config-off default.**
- **Stealth → Thief** (mobs literally cannot see you): partially doable (reduce mob follow-range while
  sneaking behind cover) but expensive per-tick and overlaps vanilla sneak mechanics. **Ship a reduced
  version or defer.**
- **Stealth → Smoke Bomb**, **Agility → Dart**, **Flying → Limitless**, **Swimming → Aquaman**: these
  are fine as cooldowned active abilities. **Keep**, build on the existing super-ability infra.

### D3 — Overlap with vanilla and with each other

Call these out to the user; they are balance questions, not bugs:
- Sprint combat bonus (Agility → Smash) vs sneak combat bonus (Stealth → Assassin) vs Acrobatics.
- Swimming speed vs vanilla **Depth Strider** / **Dolphin's Grace**; Lead Lungs vs **Respiration**.
- Flying speed/glide vs the elytra's own physics and **firework rockets**.
- Unarmored "free armor" vs actually wearing armor — the wiki's "stacks and doubles" clause is
  incoherent; see [unarmored.md](unarmored.md) D-U1.

### D4 — XP curve & balance pass

All six use the **global** mcMMO level curve (`FormulaManager`) with a **per-skill XP modifier** in
`experience.yml`. The wiki caps everything at level 1000 (RetroMode numbers). Do a deliberate balance
pass per skill — distance-based XP especially will either trickle or firehose depending on the
per-block XP value. Every plan has a "Balance / XP tuning" section; none of the numbers there are final.

---

## 🧱 Net-new shared foundation (build these FIRST)

Four of the six skills (Agility, Stealth, Swimming, Flying) are **continuous-state / passive-tick**
skills. Nothing in the Pass-1 port samples player movement per tick — every ported skill hooks a
discrete event (block break, entity damage, item use). These two components do not exist yet and are a
hard prerequisite. **Build and unit-test them before any movement skill.**

### F1 — `PlayerMovementTracker` (the per-tick sampler) — KEYSTONE

A single `ServerTickEvents.END_SERVER_TICK` sweep (register it next to the scheduler tick in
`McMMOMod.onInitialize`, `McMMOMod.java:183`) that, for each online `ServerPlayerEntity`, computes:

- **Δposition** since last tick (store `lastPos` per player; a `WeakHashMap<UUID,Vec3d>` or a field on
  the player session object).
- **Movement state flags** this tick: `isSprinting()`, `isSneaking()` + actually moving,
  `isSubmergedInWater()` / `isTouchingWater()`, `isGliding()` (elytra), `isOnGround`,
  `hasVehicle()`.

Then dispatch the horizontal distance + state to the relevant manager (`AgilityManager.onSprintTick`,
`StealthManager.onSneakTick`, `SwimmingManager.onSwimTick`, `FlyingManager.onFlyTick`).

**Anti-exploit / anti-AFK (this is load-bearing, not optional):**
- **Ignore vehicle movement** (`hasVehicle()`) — no leveling by boat/horse/minecart.
- **Reject teleports / impossible deltas** — if Δ > a sane per-tick cap (e.g. > 10 blocks), treat as a
  teleport and skip (also resets `lastPos`).
- **Require real movement** — sneaking-in-place or sprinting-into-a-wall (Δ≈0) pays nothing. The wiki
  literally jokes "Sticky keys op" for Sneaking; do not let a rubber band on the W key farm a skill.
- **Distance is accumulated, XP is granted per whole block** crossed (so we don't award fractional XP
  every tick and thrash the XP pipeline / dirty flag). Keep a per-player `distanceAccumulator`.
- Mirror the Acrobatics exploit throttle idea (`BlockLocationHistory`, `AcrobaticsManager.java:48`) if a
  specific loop-farm shows up in play-testing.

**Design note:** keep the tracker MC-typed (it touches `ServerPlayerEntity`), and keep each manager's
`onXxxTick(distance, stateFlags)` **MC-free** so the XP + buff math stays unit-testable — same split as
every Pass-1 manager (`AcrobaticsManager` is the model).

**Cost:** this runs 20×/s × online players. In singleplayer that's ~1 player, so it's cheap, but write
it as if it weren't — no per-tick config parsing, no per-tick allocation. Cache config reads (the
Alchemy Catalysis per-tick-config-read trap, see `[[alchemy-catalysis]]`, applies here verbatim).

### F2 — `SkillAttributeService` (managed attribute modifiers)

Unarmored (armor), Agility (Dash speed), Stealth (Padfoot speed) and Swimming (swim speed) all apply
`EntityAttributeModifier`s. A leftover modifier is a **permanent buff / stacking bug** — the #1 way this
class of feature breaks. Build one helper that:

- **Applies/removes idempotently**, keyed by a stable `Identifier` per skill+effect
  (`mcmmo:agility_dash_speed`, `mcmmo:unarmored_leather_skin`, …). Re-applying updates in place; it never
  stacks.
- **Clears ALL mcMMO modifiers on logout / disconnect** (`ServerPlayConnectionEvents.DISCONNECT`) and
  on the entity-recreation paths that already bit us — respawn and End-exit both route through
  `PlayerManager#respawnPlayer` and build a **new** `ServerPlayerEntity` (see
  `[[respawn-stale-handle]]`). A modifier applied to the old entity is gone; a modifier the tracker
  *thinks* is applied is now absent. The tracker must re-derive from state each tick, and the service
  must not assume the entity persists.
- Targets `GENERIC_MOVEMENT_SPEED`, `GENERIC_ARMOR`, `GENERIC_ARMOR_TOUGHNESS`, `GENERIC_MAX_HEALTH` as
  needed (verify the 1.21.11 registry keys with `scripts/javap-mc.sh`, see `[[javap-mc-script]]` — do
  not trust the attribute names from memory, they were renamed across versions).

**Unit-test the idempotency and the clear-on-logout paths directly** — these are exactly the bugs that
never show up in a boot smoke test and always show up on the third respawn in a live world.

---

## 📋 The "add a PrimarySkillType" checklist (referenced by every skill file)

Every standalone skill below has to do all of this. The per-skill files list only the *skill-specific*
work and point back here for the boilerplate. Work top-to-bottom; the enum + profile items must land
before anything references the manager.

**Datatypes / core:**
1. `datatypes/skills/PrimarySkillType.java` — add the constant (alphabetical block). This alone
   auto-extends `PlayerProfile.skills`/`skillsXp` (EnumMaps built from `.values()`, `PlayerProfile.java:33-35,73-74`),
   the `initSkillManagers()` loop (`McMMOPlayer.java:149`), and `SkillTools.NON_CHILD_SKILLS`.
2. `datatypes/skills/SubSkillType.java` — add each sub-skill constant with its rank count, under a
   `/* SKILLNAME */` comment block. **Warning already in the file: a sub-skill must not collide with any
   `PrimarySkillType` name** (static-import clash).
3. `datatypes/skills/SuperAbilityType.java` — **only if the skill has an active/super ability.** Add the
   6-arg constant (On / Off / Other.On / Refresh / Other.Off / SubSkill.Name locale keys, see
   `SuperAbilityType.java:14-20`) and wire `X.subSkillTypeDefinition = SubSkillType.…` in the static
   block (line 116-118).
4. `datatypes/skills/ToolType.java` — only if the skill is gated on holding a specific tool.

**Manager:**
5. New `skills/<skill>/<Skill>Manager.java extends SkillManager` — the **MC-free** core (imports zero
   Minecraft types; `AcrobaticsManager` is the reference).
6. `datatypes/player/McMMOPlayer.java` — add `case XXX -> new XxxManager(this)` to `initManager`
   (line 160) **and** the typed getter in the accessor block (line 189+).

**SkillTools wiring (`util/skills/SkillTools.java`):**
7. Add to exactly one category list — `COMBAT_SKILLS` / `GATHERING_SKILLS` / `MISC_SKILLS`
   (line 96-110). Movement skills → `MISC_SKILLS`.
8. If it has a super ability: it flows through `buildSuperAbilityMaps()` automatically via the
   `subSkillTypeDefinition` wiring — verify.
9. If tool-gated: add to `buildPrimarySkillToolMap()`.
10. **Do NOT** add to `isChildSkill` unless D1 was overruled to "child."

**Config (all four + maybe a per-skill file):**
11. `resources/experience.yml` — add the skill's **XP-gain modifier** (the per-skill multiplier the
    formula reads) and an **Experience_Bars** color block (see the `Acrobatics:` block at line 58). Add
    any **ExploitFix** toggle the skill needs (movement skills want an AFK/exploit flag).
12. `resources/skillranks.yml` — rank-unlock levels for every sub-skill. **Remember RetroMode ×10**
    (`PLAYTEST_G.md` §0): a "level 100" wiki unlock is `100` in RetroMode config but reads as level 10
    in Standard. Author the numbers in RetroMode and let the config scaler handle Standard.
13. `resources/advanced.yml` — per-sub-skill tuning (max chance, max-bonus-level, per-level increments).
    Its address convention is `SubSkillType.getAdvConfigAddress()`.
14. `resources/config.yml` — enable/disable flag for the skill (and for any config-gated ability from D2).

**Text:**
15. `resources/com/gmail/nossr50/locale/locale_en_US.properties` —
    `<Skill>.SkillName`, and for each sub-skill `<Skill>.SubSkill.<Name>.Name` / `.Description` / `.Stat`;
    ability `On`/`Off`/`Refresh` strings for any super ability. Grep an existing skill block (e.g.
    `Acrobatics.`) and mirror the key shape exactly — the locale parser is strict.

**MC-typed trigger layer:**
16. Listener / mixin / tick hook that fires the manager. For movement skills this is **F1**; for
    interaction/active abilities a listener in `fabric/listeners/` (register it in
    `McMMOMod.onInitialize`, line 187+); for anything mid-vanilla-logic a mixin. Cap slice-anchored
    injectors with `allow=N` (`[[mixin-slice-allow-guard]]`).

**Commands / display:**
17. Verify `/mcstats`, `/addxp <skill>`, `/addlevels <skill>` resolve the new enum (they iterate/parse
    `PrimarySkillType` — confirm the parse is by `name()` and the stats screen doesn't hard-code the 19).

**ModMenu / Cloth config:**
18. Register the new config keys with the ModMenu integration's **key-validation test**
    (`[[modmenu-integration]]`) — it fails the build on typo'd/unknown keys, which is a feature: it will
    catch a mismatch between your `experience.yml` keys and the editor. Add the new keys there.

**Persistence sanity:**
19. Old save files predate the new skill. Confirm the profile load path pre-populates defaults from
    `PrimarySkillType.values()` **before** merging saved data (`PlayerProfile` constructor line 73-74
    does exactly this, then `putAll` at 101-102), so an existing profile just defaults the new skill to
    the starting level instead of NPE-ing. **Add a load-an-old-profile regression test.**

**Tests (non-negotiable, per AGENTS.md):**
20. Unit tests for the MC-free manager math (formulas, gates, roll outcomes) — pin RNG the way
    `AcrobaticsManager` tests do. Registry-backed glue uses the fabric-loader-junit harness
    (`[[fabric-loader-junit-harness]]`).
21. Boot smoke test stays green (`./gradlew runServer`, gate the piped `stop` on `Done (` in the log —
    `[[placed-block-persistence]]` — never a sleep).
22. Add the skill's rows to `PLAYTEST_G.md` (or a Pass-2 `PLAYTEST_G2.md`): every sub-skill needs a
    "do the action, watch the delta" §G row. Boot-verified is not played.

---

## Suggested build order

1. **§G play-test of Pass 1** (blocker — do not skip).
2. **F1 `PlayerMovementTracker` + F2 `SkillAttributeService`** with unit tests. No skill yet — just the
   foundation, proven idempotent and AFK-proof.
3. **Unarmored** first among the new skills — it's event-driven (damage taken) for XP and only needs F2
   (not F1), so it validates the attribute service without the tick-sampler risk.
4. **Agility**, then **Stealth**, then **Swimming** — all lean on F1; do them in ascending mechanical
   risk.
5. **Husbandry** — independent of F1/F2 (event-driven), but needs new breed/shear mixins; can be built
   in parallel by a second dev.
6. **Flying** last — the elytra-physics work is the fiddliest and the most cut-down from the wiki.

One skill lands **fully** (code + config + locale + unit tests + green boot + §G rows) before the next
starts. No half-wired skills sitting in the tree — that was the whole lesson of Pass 1's "boot-verified,
never played" debt.
