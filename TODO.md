# Multi-Version Support — Development TODO

**Scope:** Fabric only. Target: **every stable `1.21.x` (12) and every stable `26.x` (4)** = 16
versions. NeoForge/Forge deferred (see bottom).

**Strategy:** branch-per-band (ruling **R-a**). `master` **is** the newest band; `mc/**` exists only
for older bands and is cut by hand. A **band** = a contiguous range of MC versions across which
mcMMO's touched surface is identical, measured by `scripts/probe-bands.py` against a 1386-record
manifest — a lookup, not a judgment call.

> Phases 0–7 are complete and archived at
> [plans/completed/TODO-multiversion-phases-0-7.md](plans/completed/TODO-multiversion-phases-0-7.md).
> Everything below is forward work. The archive is the only place the per-phase findings live in
> full; memory files under `.agent/memory/` carry the gotchas.

---

## What ships today — 4 branches, **7 of 12** `1.21.x` versions

⚠️ **There is no per-version jar and there never was. One jar covers a band**, via the range in its
own `fabric.mod.json`. Read from each branch (`git show <branch>:src/main/resources/fabric.mod.json`),
not retyped:

| Branch | MC versions covered | `depends.minecraft` | Released tag |
|---|---|---|---|
| `master` | `1.21.11` | `~1.21.11` | `mc1.21.11-v2.2.050-build.26` |
| `mc/1.21.10` | `1.21.9`, `1.21.10` | `>=1.21.9 <1.21.11` | `mc1.21.10-v2.2.050-build.25` |
| `mc/1.21.8` | `1.21.6`, `1.21.7`, `1.21.8` | `>=1.21.6 <1.21.9` | `mc1.21.8-v2.2.050-build.24` |
| `mc/1.21.5` | `1.21.5` | `>=1.21.5 <1.21.6` | `mc1.21.5-v2.2.050-build.27` |

🔑 **So `1.21.6`, `1.21.7` and `1.21.9` are NOT missing** — they are covered by the `mc/1.21.8` and
`mc/1.21.10` jars respectively. Nothing needs building for them.

## What is genuinely missing — **5 `1.21.x` versions + all 4 `26.x`**

| Band | MC versions | Manifest rows (absent · sig-changed) | Status |
|---|---|---|---|
| `1.21.4` | `1.21.4` | 15 · 27 = **42** | ⬜ not cut |
| `1.21.3` | `1.21.2`, `1.21.3` | 16 · 28 = **44** | ⬜ not cut |
| `1.21.1` | `1.21`, `1.21.1` | 65 · 60 = **125** | ⬜ not cut — 🔴 see R-m |
| `26.x` | `26.1`, `26.1.1`, `26.1.2`, `26.2` | n/a — **full yarn→official rename** | ⬜ not cut |

**Four branches to cut.** They were excluded by ruling R-b's `1.21.5` floor, which **R-l now
supersedes** — not by any technical finding except the one at band `1.21.1`.

⚠️⚠️ **Read a row count as *rows to look at*, never as work to do.** The three completed bands are
the calibration, and the table over-predicts by 4–6×:

| Band | Table said | Real code changes |
|---|---|---|
| `mc/1.21.10` | 10 | **1** |
| `mc/1.21.8` | 32 | **6** |
| `mc/1.21.5` | 27 | **~8**, two of them redesigns |

Most signature deltas are benign (a covariant return, an added overload). What the table *cannot*
price is the difference between a **signature change** and an **absence**: `getEntityWorld` cost
`mc/1.21.5` **3** broken sites and `mc/1.21.8` **57**, from the same one row.

---

## RULINGS

Carried forward and still binding: **R-a** branch-per-band · **R-c/P2-a…e** full platform seal ·
**R-d** playtest stays on master builds · **R-e** `26.x` is its own mini-project · **R-f** master =
newest band · **R-g** `.github/` is master-only · **R-h** pushes are mine once gates are green ·
**R-i/R-j/R-k** docs are byte-identical on every branch, live wiki is never pushed.

| # | Question | Ruling |
|---|---|---|
| **R-l** | Support floor (2026-08-12) | ✅ **RULED (owner, 2026-08-12) — supersedes R-b's `1.21.5` floor.** Floor moves to **`1.21`**; ship all 12 `1.21.x` + all 4 `26.x`. R-b dropped `1.21`–`1.21.4` to dodge the component-API cliff; that cliff is real but it is confined to band `1.21.1`, and `1.21.4`/`1.21.3` were never touched by it. *Was recorded PROPOSED; ruled when the owner directed Phase 8 to start, which exists only under this floor.* |
| **R-m** | Band `1.21.1`'s three absent subsystems | ✅ **RULED (owner, 2026-08-12): capability-probe and disable.** Ship the band with Farmer's/Fisherman's Diet, Unarmored, Agility and Stealth switched off rather than reimplemented. Reimplementing three seams against the 2024 API is larger than the other three bands combined, and 6.4's stop-loss would have justified dropping the versions entirely — this keeps them shipping. |
| **R-n** | Is `.agent/` committed? (2026-08-12) | ✅ **RULED (owner): NO — the gitignore was right, the docs were wrong.** Memory stays local to one working copy; `AGENTS.md` and both `save-memory` skill copies were corrected. ⚠️ `.claude/`, `.github/`, `CLAUDE.md` and `.mcp.json` are ignored too — **`AGENTS.md` is the only tracked agent-facing file in the repo.** Two consequences nothing warns you about: **a fresh clone has no memory and no skills**, and **memory cannot drift between branches because it does not travel with them** — one tree serves every checkout, so entries must name the branch they describe rather than say *"here"*. Anything another checkout needs goes in `TODO.md`, `AGENTS.md`, or the commit message. |

### R-m — what it actually takes (⚠️ the Spears precedent only half-applies)

Band `1.21.1` is not a rename job. Three subsystems are **absent**, not reshaped, and each backs a
shipped player-facing feature:

| Absent on `1.21`/`1.21.1` | Backs |
|---|---|
| `FoodComponent#onConsume`, `ConsumableComponent`, `DataComponentTypes#CONSUMABLE` | **Farmer's Diet + Fisherman's Diet** — the eating seam, whole |
| The entire `EntityAttributes#*` family (8 records: `ARMOR`, `MOVEMENT_SPEED`, `MAX_HEALTH`, `ATTACK_DAMAGE`, `JUMP_STRENGTH`, `SNEAKING_SPEED`, `WATER_MOVEMENT_EFFICIENCY`) | **Unarmored** (`ARMOR` *is* its mechanic), **Agility**, `SkillAttributeService` |
| `PlayerInput` + `ServerPlayerEntity#getPlayerInput` | **Stealth** — its real server-side key-state seam |

Plus `ExplosionImpl`, `AbstractCowEntity`, `AbstractBoatEntity`, `EntityConversionContext` and
`LivingEntity#travelGliding`. This is **exactly the R6 component-API cliff predicted at Phase 1.5**,
landing where 1.5 measured it — at `1.21.2`, so `1.21.2`+ is clear and only this band pays.

It also **trips stop-loss 6.4**: 125 rows against the largest completed band's 32 is **3.9×**, and
the rule says stop and re-scope rather than push through. R-m is that re-scope.

⚠️⚠️ **A probe alone CANNOT implement this, and the difference from Spears is load-bearing.**
`SkillAvailability#probe` works for Spears because the absent things are **registry entries** —
`Items.IRON_SPEAR` is a lookup that misses, so one expression is correct on every band and `master`
never changes. Here the absent things are **types, static fields and mixin targets**, which are
*compile-time* and *classload-time* facts:

| Absence | Fails as | So the band branch must |
|---|---|---|
| `EntityAttributes#ARMOR` and 7 siblings | **compile error** | resolve it in `platform/` (`SkillAttributeService`) — the code cannot merely be gated at runtime |
| `PlayerInput` class + `getPlayerInput()` | **compile error** | same, inside `fabric/`/`platform/` |
| `FoodComponent#onConsume` | **mixin fails to apply → boot failure** | the mixin must be removed or re-seamed on the band, not left present-but-inert |

🔑 That last row is the dangerous one. An unresolvable `@At` target does not degrade gracefully — on
`mc/1.21.5` **two** of them took out `Blocks.<clinit>`, then `Items.<clinit>`, and cascaded into
**302 failing tests across 34 unrelated classes**. Read the root cause, never the count.

**So R-m is two pieces of work, not one:**

1. **On the band branch** — make it compile and boot: resolve the attribute and `PlayerInput`
   absences in `fabric/`/`platform/`, and remove or re-seam the eating-seam mixin.
2. **On `master` first** — a `SkillGating` switch for each affected skill, so all six meanings of
   "disabled" close at once (no XP, no procs, no super ability, no XP bar, no `/mcstats` line, no
   plaques) and `/mcstats <skill>` explains it is the *Minecraft version*, not `coreskills.yml`.
   ⚠️ Per `ConfigRetunes`, **flipping a shipped config default is not sufficient** —
   `copyMissingDefaults` back-fills only absent keys, so a changed default reaches nobody who has
   already run the mod once.

⚠️ **Whatever drives the gate must be provable in BOTH directions on EVERY band.** The first Spears
wiring test was vacuous — it asserted the skill was *enabled* on `master`, which a completely absent
gate satisfies just as well, and the disabling half is unreachable from the branch the code is
written on. Take the inputs as arguments and add a `setForTesting` seam, as `SkillAvailability` now
does. *(Fifth vacuous-guard sighting in this project — assume the sixth is this one.)*

---

## Phase 8 — the three sub-floor `1.21.x` bands

Order is cheapest-first and **each branch is cut from `master`, never from the previous band** —
otherwise band N inherits band N−1's back-compat fixes and the diffs stop being independent. The
*learning* transfers even though the branch does not: `1.21.4`'s absent set is very nearly a subset
of `1.21.3`'s, so the second cut should be fast.

### Per-band recipe (Phase 4′, unchanged)

- [ ] **8.x.1** `git switch -c mc/<band>` off `master`.
- [ ] **8.x.2** First commit pins that band's toolchain in `gradle.properties` **and nothing else**:
      `minecraft_version`, `yarn_mappings`, `loader_version`, `fabric_version`, ModMenu, Cloth.
      ⚠️ **Look the yarn build number up** — it is not derivable from the version
      (`1.21` → `build.9`, `1.21.1` → `build.3`, `1.21.2` → `build.1`, `1.21.3` → `build.2`,
      `1.21.4` → `build.8`).
- [ ] **8.x.3** `fabric.mod.json` `depends.minecraft` = the band's **range**, not its newest version.
- [ ] **8.x.4** ⚠️ **Decide `.github/` deliberately.** A branch cut from `master` today inherits
      **no** `.github/`, because R-g removed it there — so it silently never releases. `mc/1.21.5`
      hit exactly this by accident of cut timing. Either force-add the three files its siblings track
      (`.github/` is in `.gitignore`, so `git add -f`; un-ignoring instead sweeps in 12 files no
      branch tracks) or accept manual release, but make it a choice.
- [ ] **8.x.5** Compile. Work errors against `BAND_TABLE.md`. **Fix inside `fabric/` and `platform/`
      only** — `PlatformBoundaryGuardTest` must stay green. Phase 2's blast-radius cap has now held
      on two real MC API breaks.
- [ ] **8.x.6** 🔑 **Ask first, every band: can `master` absorb the difference instead?** Widening
      `CHEAT_COMMAND` to `java.util.function.Predicate` on `master` cut `mc/1.21.10`'s whole
      main-source diff to one token. It fails when there is no overlapping name on both sides
      (`getEntityWorld`), so ask, don't assume.
- [ ] **8.x.7** Run the full ship gate (below). Then push.
- [ ] **8.x.8** Back-port anything that belongs on `master` **to `master` first**, then to every
      other band with `Backport-of:` trailers.

### The bands

- [ ] **8.1 — `mc/1.21.4`** (`1.21.4` only; 42 rows). The cheapest and the one to prove the loop on,
      because it is the last band `config-id-audit.py` can still audit.
      **Cut, ported, committed at `bfb1c11d6`; ship gate 6 of 7.** Two gameplay defects block it —
      see [8.1a](#81a--the-two-gameplay-smoke-defects-blocking-the-band) below.
- [ ] **8.2 — `mc/1.21.3`** (`1.21.2`, `1.21.3`; 44 rows). ⚠️ Blocked on **8.4** for a full gate run.
      ⚠️ **Expect the mob-origin defect again** — see 8.1a.A. Bands are cut from `master`, never
      from each other, so this branch inherits **no** fix for it, and an older band pins an older
      fabric-api, i.e. an *older* `data-attachment-api` than the broken 1.6.2. Check
      `fabric_readAttachmentsFromNbt` for the unconditional assign before trusting
      `combat-egg-control`, and note that every static gate — build, boot-check, mixin audit —
      is **blind** to it.
- [ ] **8.3 — `mc/1.21.1`** (`1.21`, `1.21.1`; 125 rows). Per **R-m**, two pieces: the `SkillGating`
      switches land on **`master` first**, then the band branch resolves the compile/mixin absences.
      Blocked on **8.4** for a full gate run. Do this band **last** — it is the only one that changes
      `master`.

### 8.1a — the two gameplay-smoke defects blocking the band

`gameplay-smoke.sh` on `mc/1.21.4` scores **27 passed, 2 failed**. Both are silent in-game
behaviour: 0 ERROR lines, 0 mixin failures, 1706/0/0 tests, and all six other gates green. **Both
are band-specific and were measured, not assumed** — a detached-worktree build of `master`
`f73031ed9` scored **29/29** on `1.21.11`, so both fixes belong on this branch and *not* on
`master`. That is the sanctioned exception to "fixes land on `master` first": there is nothing on
`master` to fix.

#### A — `combat-egg-control`: the mob-origin stamp is erased before it is ever read

**Root cause (proven from bytecode, not inferred).** This band's fabric-api pins
`data-attachment-api 1.6.2`, whose `fabric_readAttachmentsFromNbt` assigns the deserialized map
**unconditionally**; master's 1.8.48 early-returns when that map is null.
`deserializeAttachmentData` returns null when the NBT carries no attachments, so on this band
`Entity#readNbt` **wipes the whole attachment map**. mcMMO stamps `MOB_ORIGIN` at
`EntityType#create(World, SpawnReason)`, which runs *before* the NBT read on every NBT-carrying
spawn — so `/summon`, spawners and chunk load all produce a mob that reads as `NATURAL`. ⚠️ There
is **no version bump out of it**: `0.119.4+1.21.4` is the newest fabric-api that exists for this MC.

- [x] **8.1a.A1 Measure the spawn-egg path** — the one thing the root-cause note left open, because a
      gate that closes `/summon` and leaves spawn eggs open is worse than none. **It was never
      exposed.** `nbtCopier` short-circuits on an empty `entity_data` component (every ordinary spawn
      egg), and when the component *is* present `NbtComponent#applyToEntity` does
      `writeNbt(fresh) → copyFrom(nbt) → readNbt` — a **round-trip that re-serializes the live
      attachment map**, so the stamp survives its own erasure. Read off the `1.21.4` merged jar.
- [x] **8.1a.A2 Re-stamp at `EntityType#getEntityFromNbt` RETURN.** Verified ordering-proof from the
      same disassembly: the body is `fromNbt(nbt).map(type -> type.create(world, reason))` followed
      by `Util.ifPresentOrElse(opt, entity -> entity.readNbt(nbt), …)`, so `readNbt` has completed
      before RETURN. ⚠️ **Not** a second injector on `Entity#readNbt` — that races fabric's own
      injector at the same target, and cross-mod mixin priority is not something to bet a silent
      exploit gate on. Reuse `MobOrigins#stampOnSpawn` unchanged: it writes nothing for a qualifying
      origin, so `SpawnReason.LOAD` still leaves the marker fabric just restored from NBT alone.
- [x] **8.1a.A3 Answer 8.x.6 explicitly: `master` does NOT absorb this.** Master has no defect; a
      redundant injector there would add mixin surface and `allow=` audit churn on four branches to
      no effect. Band-local, inside `fabric/mixin/`, so `PlatformBoundaryGuardTest` stays green.
- [ ] **8.1a.A4 A guard test that fails when it is reverted.** ⚠️ No Mockito test can see this —
      the defect lives in fabric-api's bytecode and the fix in an injection point. Follow
      `guards/ArmadilloBrushDispenserExclusionTest`: assert from **this band's bytecode** that
      `EntityType#getEntityFromNbt` really does call `Entity#readNbt` (so the re-stamp point is
      genuinely after the erase), and from **source** that an injector selects it at RETURN.
      Anti-vacuity count assertions first — "found nothing" and "the scan is broken" render
      identically. **Mutation-prove it** before believing it.
- [x] **8.1a.A5** `mixin-allow-audit.py --mc 1.21.4 --check` — **62/62 PASS**, `allow=1 computed=1`
      on the new injector, exactly as the disassembly predicted (one `areturn`). ⚠️ **This band now
      has 62 injectors, not the 61 the ship-gate list below quotes** — that number is `master`'s.

#### B — `repair`: the anvil pays nothing. NOT root-caused

`REPAIR` stays 0 across the two-click phase. ⚠️ **`repair-control` passing is worth nothing** — it
asserts REPAIR does *not* move, which a completely dead listener satisfies.

**What is now ruled out, so the search space is the repair path itself:**

- **The event fires, server-side, on this band.** `cook-campfire` passes, and `onCampfireCook`
  returns early unless `CookingListener#onUseBlock` — a `UseBlockCallback` with the same
  `instanceof ServerPlayerEntity` gate — recorded the campfire owner first. ⚠️ The earlier note
  justified this with *"cook-campfire rides the same event"*; the XP itself comes from
  `CampfireCookMixin`, so state the owner-map instead. Same conclusion, sound reason.
- **The aim and the block position are correct.** `cook-campfire` targets the identical block at
  `2 -60 0` with the identical `_look`.
- **Fabric's dispatch is unchanged.** `ServerPlayerInteractionManagerMixin`'s `interactBlock` HEAD
  injector is byte-for-byte the same logic in events-interaction `4.0.4` (this band) and `4.0.36`
  (master); only unrelated `LocalCapture` → `@Local` churn differs.
- **The port did not touch it.** `bfb1c11d6` changes no file on this path.

- [x] **8.1a.B1 Probe with instrumentation.** Every candidate exit — `anvilKindAt` failing to
      resolve the configured anvil block, `repairableInHand` missing the `repair.yml` entry,
      `checkConfirmation` never confirming, or one of `performRepair`'s guards (unbreakable, level,
      `startDurability <= 0`, absent repair material) — returns **silently to a player-facing
      notification the server log never sees**. Build with temporary logging at each, run the
      harness, read the log. ⚠️ **Copy `logs/latest.log` out before probing** — re-using
      `build/gameplay-smoke/<ver>/` rotates it and the run being diagnosed ends up gzipped.
- [x] **8.1a.B2 No mcMMO fix was needed; the instrumentation was stripped** and the listener is
      byte-identical to `bfb1c11d6`. Verified by `git diff` before and after.
- [x] **8.1a.B3 RESOLVED — it is the HARNESS, and the evidence is a 9-run study: 8 × 29/29,
      1 × 27/29.** The one failure is **not** the `repair` phase and **not** an mcMMO defect: it is
      `combat-fist` + `combat-sword` being handed a **structure-spawned** cow, which
      `Nether_Portal.Multiplier: 0` correctly prices at zero. The anti-farm gate was working.
      Full signature + the one-line scenario fix in `gotchas.md` 2026-08-13.
      ⚠️ The original `repair` failure remains a single unexplained event — instrumentation cleared
      the whole repair path and it has passed **9 consecutive runs** since.

- [ ] **8.1a.B4 🔴 OWNER CALL — harden `gameplay-smoke.sh`'s combat phases.** `scripts/` is
      `master`-first tooling back-ported to four bands, so this is scope, not a blocker for 8.1.
      🔴 **It affects every band already shipped**: `mc/1.21.5`, `mc/1.21.8` and `mc/1.21.10` each
      passed gate 6 exactly **once**. The failure mode is a **false red, never a false green**, so
      nothing shipped broken — but "29/29" is weaker evidence than it reads.

- [x] **8.1a.C Full seven-gate re-run — all green.** 1️⃣ `build` **1706/0/0**, forced with
      `test --rerun --no-build-cache` because both `UP-TO-DATE` and `FROM-CACHE` are *restored*
      results, not executed ones. 2️⃣ `mixin-allow-audit` **62/62**. 3️⃣ `boot-check` PASSED, 0 ERROR,
      0 mixin failures, canary rejected. 4️⃣ `config-id-audit` exit 0, 638/689 = 92.6%, 0
      dead-everywhere. 5️⃣ `brew-smoke` PASSED **with its vanilla control discriminating**.
      6️⃣ `gameplay-smoke` **29/29**. 7️⃣ `drift-audit --self-test` PASSED.
- [ ] **8.1a.D 🔴 OWNER CALL — the push is a PUBLISH.** `.github/` is tracked on this branch (R-g
      removed it from `master` only), so a `src/`-touching push **cuts a release**. R-h ruled pushes
      are the agent's once gates are green, but it was ruled when a push no longer released. **Ask.**
      Then `drift-audit.py --master master` — ⚠️ it audits `origin/master`, so push first.

### 8.4 — 🔴 BLOCKER: `config-id-audit.py` cannot audit below `1.21.4`

- [ ] Give `config-id-audit.py` a registry-id source that works below `1.21.4`, or the `1.21.3` and
      `1.21.1` bands ship with **one of the seven gates missing**.

`assets/minecraft/items/` was added in `1.21.4`; below that the script **refuses to report rather
than calling every item absent** (`config-id-audit.py:310`) — correct behaviour, and it means 689 id
references across six config files go unchecked on two of the three new bands. That gate is what
found three live XP holes on `master` that no compiler, test or boot log could see.

⚠️⚠️ **Both obvious replacements are already ruled wrong and neither fails visibly:** `javap Blocks`
yields **yarn field names, not registry ids** (and `CopperBlockSet` hides several ids behind one
field), and `lang/en_us.json` **keeps stale keys through renames** — `1.21.11` still carries
`block.minecraft.chain`, so the exact bug the audit found reads as clean there. Find a third source
(the data generator's `reports/registries.json` is the candidate) and **prove it against a version
where the current source also works**, so the two can be compared before the old one is left behind.
`--self-test` and the 95% control floor must both still pass.

---

## Phase 9 — the `26.x` band

**Its own mini-project (R-e). Do not absorb it into a sweep.** Gated behind Phase 8 delivering at
least one completed ordinary band, so the loop is known to work before the hard band starts.

From `26.1` Minecraft **ships unobfuscated** — verified against the real artifact (`26.2` server
jar: 7,434 `net/minecraft/*` classes, zero obfuscated names). Mappings are absent because they are
no longer *needed*, not because tooling is missing.

**But Mojang names are not yarn names, and the schemes differ structurally:**

| | official (`26.x`) | yarn (what this mod is written in) |
|---|---|---|
| item stack | `net.minecraft.world.item.ItemStack` | `net.minecraft.item.ItemStack` |
| server player | `net.minecraft.server.level.ServerPlayer` | `net.minecraft.server.network.ServerPlayerEntity` |
| food | `net.minecraft.world.food.FoodProperties` | `net.minecraft.component.type.FoodComponent` |

So this band is a **wholesale rename of the entire MC-facing surface**: ~164 imports, 42 mixins, 44
method selectors, 19 `@At` descriptors, plus every MC type named in a method body.

🔑🔑 **This is what vindicated R-a.** No preprocessor directive can bridge an identifier rename of
this size; a branch is the only honest representation.

- [ ] **9.1 Derive the yarn→official translation table.** Yarn's `v2` mappings carry
      `official → intermediary → named` columns, so the table can be **derived** for `1.21.11` and
      largely reused rather than hand-written. **Confirm this before budgeting the rename as manual** —
      it is the difference between a script and a month.
- [ ] **9.2 Toolchain.** `26.x` needs a newer Loom than our **1.17.13**, and `build.gradle:30` pins
      `net.fabricmc:yarn:${yarn_mappings}:v2`, which 404s for every `26.x`. ⚠️ **Confirm exact plugin
      coordinates at the time of the attempt — do not pin from this note, it will be stale.** As of
      2026-08-10: stable `1.17.19`, active track `1.18.0-alpha.*` (moves daily).
- [ ] **9.3 Translate the tooling, not just the source.** `scripts/mc-surface.txt` is yarn-named and
      **does not apply to this band**, so `probe-bands.py` cannot probe it at all until 9.1 lands.
      `mixin-allow-audit.py` and `extract-mc-surface.py` read the same names. **The band cannot run
      its own gates until its tooling speaks official names.**
- [ ] **9.4** Cut `mc/26.x` per the 8.x recipe; `depends.minecraft` covers `26.1`–`26.2`.
- [ ] **9.5** Full ship gate. Expect `boot-check.sh` and `gameplay-smoke.sh` to need version-specific
      fixture work (Carpet build, command syntax).

⚠️ `26.1 > 1.21.11` sorts correctly under semver, so version *predicates* need no special-casing.
The obstacle was never the version string.

---

## The ship gate — run per band, before every push

Nothing enforces this list any more (R-g retired CI). **It is a person running seven commands.**

1. `./gradlew build` exit 0 — suite green, and the **count should match `master`** (~1705). A lower
   count means something was disabled to get there.
2. `python scripts/mixin-allow-audit.py --mc <version> --check` — 61/61. A `MISMATCH` is a fact to
   record, not a bug to suppress.
3. `scripts/boot-check.sh <jar> <version>` — 0 ERROR, 0 mixin failures, canary rejected.
4. `python scripts/config-id-audit.py --check` — 0 dead-everywhere. ⚠️ **Unavailable below `1.21.4`
   until 8.4 lands.**
5. `scripts/brew-smoke.sh` — passes **with** its vanilla control failing.
6. `scripts/gameplay-smoke.sh` — 29/29, and `GAMEPLAY_SMOKE_CONTROL=1` must **fail**.
7. `python scripts/drift-audit.py --self-test` **then** `--master master` — **0 MISSING on every
   band**. ⚠️ It audits `origin/master`, so **push first, then audit**.

⚠️⚠️ **`drift-audit.py` does not track a `scripts/`-only commit**, and tooling is exactly what a new
band needs to run its own gates. **Cherry-pick tooling to each new band deliberately**, or the band
silently cannot probe itself.

---

## Risk register

| # | Risk | State |
|---|---|---|
| R1 | Band count makes "all versions" unviable | ✅ **CLOSED** — 7 bands total, 4 branches left to cut |
| R2 | CI time explodes | **Downgraded** — branches build independently. Trigger: ~30 min per band |
| R3 | Version-specific code leaks into skill logic | ✅ **CLOSED** — 26 → 0 leak sites; `PlatformBoundaryGuardTest` held on two real API breaks |
| R4 | Silent mixin misbinding via dropped `@Slice` | ✅ **CLOSED** — `allow = N` on all 61 injectors, measured from bytecode |
| R5 | Item-ID drift silently disables config rows | ⚠️ **PARTIAL** — `config-id-audit.py` + per-band test, but **blind below `1.21.4`**. See 8.4 |
| R6 | Component-API cliff needs reimplementation | 🔴 **NOW IN SCOPE under R-l.** Confined to band `1.21.1`; it is what R-m decides |
| R7 | Live playtest disrupted | ✅ Phase 0 tag + instance backup |
| **R8** | **A fix lands on `master` and is silently never back-ported** | 🔴 **REOPENED.** Closed on three legs; two were removed in one working tree by R-g. Detection is now *"somebody remembers to run the script"*. **Each new band multiplies this** — 4 bands today, 8 after Phase 9 |
| **R9** | **`drift-audit.py` is blind to docs drift** | 🔴 **OPEN.** It classifies a `README.md`/`wiki/`-only commit as not-propagatable and ignores it — its own self-test asserts that deliberately. R-j made docs part of what every band ships, so a forgotten docs fix now serves a wrong page to that band's players. Interim check: `git diff --name-only master <band> -- README.md wiki/` must print nothing |
| **R10** | **Two branches resolving to the same `minecraft_version`** | **Dormant, not fixed.** The tag-reaping sweep that made it fatal was `release.yml`'s, gone from `master` under R-g — but the three bands still carry it. Keep the one-band-one-version rule |

---

## Standing rules that keep biting

- **Fixes land on `master` FIRST**, always. A fix authored directly on a band branch is a defect.
  Every band-propagation commit carries `Backport-of: <sha>`; a `master` commit that must not
  propagate says `Backport-not-needed: <reason>` **in the commit that made the decision**.
- **Never pin a comment to the build's Minecraft version.** A dated observation (*"removed in
  1.21.11"*) stays true; a claim about what this build targets goes false silently on the next cut.
  Four have already rotted, one of them cited as the *reason* for absent code (GitHub #7).
- **Never resolve a band difference by changing `minecraft_version` on `master`.**
- **A guard that has never failed is not known to work.** Every script here carries a `--self-test`
  or a control run, and refuses to report until it passes — because *"found nothing"* and *"there is
  nothing to find"* render identically.
- **Caveat-expiry pass** on every docs change: grep the **symptom**, not the file you edited. One
  wiki serves all four bands, so *"X works in \<version\>"* reads as *"X works for you"* three bands
  down.

---

## Deferred (explicitly out of scope)

- **NeoForge / Forge.** Blocked on `platform/` being real interfaces — today `PlatformPlayer`,
  `PlatformBlock`, `PlatformItem` and 7 others are `public final class` importing `net.minecraft`
  directly. A final class cannot have a second platform implementation. Never caught because
  Mockito 5's inline mock maker mocks final classes happily.
- **Versions below `1.21`.** Not requested.
- **Snapshot targets** (`26.3-snapshot-*`). Revisit once `26.3` is stable.
- **Test-suite split by cost** (old Phase 4.4). Trigger: any band's build exceeding ~30 min.
- **Trophy Hunter gameplay proof.** Wiring-proven on `mc/1.21.8` and `mc/1.21.5` but not
  gameplay-proven — it is rank-gated and the smoke player is Hunter 0. First thing to add if
  `gameplay-smoke.sh` is extended.
