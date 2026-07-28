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

## The five skills

| Plan | Skill | XP trigger | Headline risk |
|---|---|---|---|
| [agility.md](agility.md) | **Agility** — the merged movement skill (**renamed Acrobatics** + Sprinting + Swimming + Flying) | falling/dodging + distance sprinted, swum, glided | It's a **rename of a shipped skill**: save-file key migration. Then 10 sub-skills and 4 XP sources on one bar |
| [husbandry.md](husbandry.md) | **Husbandry** (+ Shearing) | the livestock lifecycle — breed, raise, feed, shear, hive, milk/brush | Fabric exposes a callback for none of them — six new mixins. `onGrowUp` fires on both age transitions **and** on chunk load; hooking `Shearable#sheared` would ship an AFK dispenser wool farm |
| [stealth.md](stealth.md) | **Stealth** (Sneaking) | distance sneaked | "Thief" mob-blindness is hard + overlaps vanilla; anti-AFK critical |
| [unarmored.md](unarmored.md) | **Unarmored** | damage taken w/ no armor | Managed armor attribute + equip/unequip reactivity |
| [hunter.md](hunter.md) | **Hunter** (Mob Mastery) | mob kills | Per-mob kill counters are a **net-new persistence shape**; mob farms trivially cap a permanent +6 damage buff unless spawn-origin gating is ported |

> **The movement plans went six → four.** `swimming.md` and `flying.md` are **deleted**; the old
> sprint-only `agility.md` is **rewritten**. All three are folded into the single merged
> [agility.md](agility.md) — see **D5**. [hunter.md](hunter.md) was added later (2026-07-28) and is
> unrelated to that merge.

Read this file first, then the per-skill file. This file owns everything they share: the two pieces
of **net-new foundation**, the **"add a PrimarySkillType" checklist**, and the **cross-cutting design
decisions** that need a human ruling before anyone writes a manager.

Note that after D5, **Husbandry**, **Stealth**, **Unarmored** and **Hunter** are the new
`PrimarySkillType`s — the checklist below applies to them in full, and to Agility only for the
*rename* half (new sub-skills, new configs, new locale keys; no new enum constant). **Hunter
additionally needs a persistence shape the checklist does not cover** (an open-ended, string-keyed
per-mob counter map) — see [hunter.md](hunter.md) D-HU2.

---

## ⚠️ Cross-cutting design decisions (need a ruling before coding)

These are the forks that change the *shape* of multiple plans. Resolve them once, here, and record the
ruling in memory (`[[conversion-overview]]` style). Each per-skill file assumes the **recommended**
answer unless told otherwise.

### D5 — Merge the movement skills into one, and rename Acrobatics → Agility (2026-07-25)

> ✅ **USER DIRECTION, 2026-07-25:** Sprinting, Swimming and Flying are **not** three new skills. They
> are folded into the existing **Acrobatics** skill, which is **renamed `AGILITY`**. One primary skill
> covers all four movement domains: falling, land, water, air. Plans redrawn accordingly.

What this changes, concretely:

- **`swimming.md` and `flying.md` are deleted**; the sprint-only `agility.md` is rewritten as the merged
  plan. Nothing was lost — the vanilla-overlap analysis (old D-SW2), the velocity-nudge-vs-mixin call
  (old D-F1), and every cut ability carried into the new file.
- **Pass 2 drops from six new skills to three new skills + one rename.** Three fewer `PrimarySkillType`
  registrations, three fewer XP curves, three fewer locale/config blocks, three fewer `/mcstats` screens.
- **F1 gets simpler** (see below): one dispatch target, one distance accumulator, one anti-AFK guard set
  instead of four.
- **F2 gets safer:** one speed-modifier identity (`mcmmo:agility_fleet_footed`) instead of four
  independently leak-able ones. Leftover modifiers are the #1 failure mode of this class of feature —
  merging removes three of the four ways to hit it.
- **New risk, which did not exist before:** `PrimarySkillType.ACROBATICS` is the **save-file key**
  (`FlatFileProfileStore.java:92-93`) and the **milestone advancement file name**
  (`Milestones.java:131-132`). Renaming it silently zeroes every existing profile's skill unless a
  legacy-key read alias lands with it. This is why the rename ships **alone** as Stage 0, with its own
  regression test and its own playtest, before a single new mechanic.
- **New cost:** Agility becomes the largest skill in the mod (10 sub-skills, 4 XP sources, one level
  bar). Sub-skill folding (Fleet Footed, Second Wind) and an explicit XP budget handle it — see D-AG3
  and D-AG6 in [agility.md](agility.md).
- ✅ **Stealth does NOT join** (user ruling, 2026-07-25). It stays its own skill — its payoff is
  *not-being-seen*, not locomotion. The Padfoot ↔ Fleet Footed speed overlap still has to be resolved
  (separate modifier identities, never both live, **verify sneak-swimming**) — see D-AG5.
- ✅ **XP is speed-normalised** (user ruling, 2026-07-25). Agility pays **XP per second of qualifying
  travel**, with each tick's distance **clamped at that medium's reference speed** — so a fast medium
  cannot outpay a slow one, and no speed buff (rockets, Depth Strider, Speed II, or **Fleet Footed
  itself**) becomes an XP multiplier. The budget is deliberately wide: ~89–170 h of continuous travel
  to max, per medium. Full formula, measured reference speeds and the derived numbers are in
  [agility.md](agility.md) §"XP: the speed-normalised budget".

### D1 — Child skill vs standalone skill (Husbandry, ~~Swimming, Flying~~)

> ✅ **DECIDED 2026-07-24 (user ruling): STANDALONE skills.** Husbandry, Swimming and Flying are full
> primary skills with their own XP. Do **not** add them to `SkillTools.isChildSkill`. Locked; do not
> re-open.
>
> ⚠️ **Superseded in part by D5 (2026-07-25):** Swimming and Flying no longer exist as skills at all —
> they are domains inside **Agility**. The ruling still stands for **Husbandry**, and the reasoning below
> is still why Agility's movement domains feed one real XP pool rather than a derived child level.

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
- **Stealth → Smoke Bomb**, **Agility → Dart / Limitless / Aquaman**: fine as cooldowned actives.
  **Keep**, on the existing super-ability infra. Post-D5 the last three are one ability with three
  bodies (`SECOND_WIND`) rather than three abilities — see D-AG2 in [agility.md](agility.md).

### D3 — Overlap with vanilla and with each other

Call these out to the user; they are balance questions, not bugs:
- Sprint combat bonus (Agility → Smash) vs sneak combat bonus (Stealth → Assassin). Mutually exclusive
  states, so it's fine — but say so deliberately.
- **Agility → Fleet Footed (sneak-adjacent) vs Stealth → Padfoot**: post-D5 these are the same mechanic
  on the same attribute. They need separate modifier identities and a stated exclusivity rule (D-AG5).
- Swim speed vs vanilla **Depth Strider** / **Dolphin's Grace**; Lead Lungs vs **Respiration**;
  glide speed vs elytra physics and **firework rockets** — all now one skill's problem (D-AG4).
- Unarmored "free armor" vs actually wearing armor — the wiki's "stacks and doubles" clause is
  incoherent; see [unarmored.md](unarmored.md) D-U1.

### D4 — XP curve & balance pass

All of them use the **global** mcMMO level curve (`FormulaManager`) with a **per-skill XP modifier** in
`experience.yml`. The shipped curve is LINEAR, `base 1020` / `multiplier 20`
(`experience.yml:145-147`), so total XP to RetroMode level *N* is **`10N² + 1010N`** — **11,010,000 to
max at level 1000**. Use that number; do not eyeball XP values.

Distance-based XP will either trickle or firehose depending on the per-block value, so **Agility does
not use a per-block value at all** — it pays per *second* of travel with a per-medium speed clamp
(D5, and [agility.md](agility.md) §"XP: the speed-normalised budget"). **Stealth should use the same
mechanism** for sneak distance rather than inventing a second model; sneaking has a well-defined
reference speed (1.295 b/s) and the same "every speed buff is an XP multiplier" trap via Padfoot.
Every plan has a balance section; the numbers in them are starting points pending §G measurement.

---

## 🧱 Net-new shared foundation (build these FIRST)

**Agility** (all three of its new domains) and **Stealth** are **continuous-state / passive-tick**
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

Then classify the **medium** (LAND while sprinting / WATER while swimming / AIR while gliding) and
dispatch the horizontal distance to **one** call — `AgilityManager.onMovementTick(medium, distance)` —
plus `StealthManager.onSneakTick(distance)` for the sneak case.

> **Post-D5:** this used to be a four-way dispatch to four managers with four accumulators. It is now
> two targets and, for Agility, **one** accumulator and **one** guard set covering all three media. If
> you find yourself writing per-medium duplicate anti-AFK code, you have missed the point of the merge.

**Anti-exploit / anti-AFK (this is load-bearing, not optional):**
- **Ignore vehicle movement** (`hasVehicle()`) — no leveling by boat/horse/minecart.
- **Reject teleports / impossible deltas** — if Δ > a sane per-tick cap (e.g. > 10 blocks), treat as a
  teleport and skip (also resets `lastPos`).
- **Require real movement** — sneaking-in-place or sprinting-into-a-wall (Δ≈0) pays nothing. The wiki
  literally jokes "Sticky keys op" for Sneaking; do not let a rubber band on the W key farm a skill.
- **Distance is accumulated, XP is granted in whole chunks** (so we don't award fractional XP every
  tick and thrash the XP pipeline / dirty flag). Keep a per-player `distanceAccumulator`.
- **F1 reports the raw Δ and the medium — it does NOT convert to XP.** The speed clamp that turns
  distance into credited *seconds* is MC-free math and lives in `AgilityManager.creditedSeconds(...)`
  where it can be unit-tested. F1 owns only the platform-y guards above. Getting this split wrong is
  how the clamp ends up untestable inside a tick handler.
- Mirror the Acrobatics exploit throttle idea (`BlockLocationHistory`, `AcrobaticsManager.java:48`) if a
  specific loop-farm shows up in play-testing.

**Design note:** keep the tracker MC-typed (it touches `ServerPlayerEntity`), and keep each manager's
`onXxxTick(distance, stateFlags)` **MC-free** so the XP + buff math stays unit-testable — same split as
every Pass-1 manager (`AcrobaticsManager` is the model).

**Cost:** this runs 20×/s × online players. In singleplayer that's ~1 player, so it's cheap, but write
it as if it weren't — no per-tick config parsing, no per-tick allocation. Cache config reads (the
Alchemy Catalysis per-tick-config-read trap, see `[[alchemy-catalysis]]`, applies here verbatim).

### F2 — `SkillAttributeService` (managed attribute modifiers)

Unarmored (armor), Agility (Fleet Footed speed, land + water) and Stealth (Padfoot speed) all apply
`EntityAttributeModifier`s. A leftover modifier is a **permanent buff / stacking bug** — the #1 way this
class of feature breaks. Post-D5 there are **three** modifier identities to get right, not six. Build
one helper that:

- **Applies/removes idempotently**, keyed by a stable `Identifier` per skill+effect
  (`mcmmo:agility_fleet_footed`, `mcmmo:stealth_padfoot`, `mcmmo:unarmored_leather_skin`). Re-applying
  updates in place; it never stacks. Agility's air body is **velocity**, not an attribute — it does not
  go through this service.
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
2. **Agility Stage 0 — the `ACROBATICS` → `AGILITY` rename, alone, with the save-file migration.**
   Zero new mechanics. It is pure risk with zero new feature value, so it must not be entangled with
   anything: if a profile comes back zeroed you need to know it was the rename. Ships with its own
   old-profile regression test and its own client playtest. See [agility.md](agility.md) Stage 0.
3. **F1 `PlayerMovementTracker` + F2 `SkillAttributeService`** with unit tests. No skill behaviour yet —
   just the foundation, proven idempotent and AFK-proof.
4. **Unarmored** — event-driven (damage taken) for XP and needs only F2, so it validates the attribute
   service without the tick-sampler risk. (Can also run before step 3 if you want F2 exercised first.)
5. **Agility Stages 2–5** — Land, then Water, then Air, then Second Wind. Each domain lands fully
   before the next.
6. **Stealth** — leans on F1 and F2; do it after Agility's Land domain has proven both in a live world.
7. **Husbandry** — independent of F1/F2 (event-driven), but needs six new mixins; can be built in
   parallel by a second dev at any point. It has its own internal stage order (0–6) in
   [husbandry.md](husbandry.md), and its breed half (stages 1–2) and harvest half (stages 3–4) are
   independent of each other, so either can ship first.

One skill (or, for Agility, one **stage**) lands **fully** — code + config + locale + unit tests + green
boot + §G rows *played* — before the next starts. No half-wired skills sitting in the tree; that was the
whole lesson of Pass 1's "boot-verified, never played" debt.
