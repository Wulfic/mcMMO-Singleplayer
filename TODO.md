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

## What ships today — 5 branches, **8 of 12** `1.21.x` versions

⚠️ **There is no per-version jar and there never was. One jar covers a band**, via the range in its
own `fabric.mod.json`. Read from each branch (`git show <branch>:src/main/resources/fabric.mod.json`),
not retyped:

✅ **Read 2026-08-13 from the releases API, not retyped** — the whole *Released tag* column was
stale, still naming the `-build.<N>` tags that 10.7f and R-r reaped. Phase 10's naming has now
reached a player on **every** band:

| Branch | MC versions covered | `depends.minecraft` | Released tag | Published jar |
|---|---|---|---|---|
| `master` | `1.21.11` | `~1.21.11` | `mc1.21.11-v2.2.050` | `mcmmo-2.2.050+mc1.21.11.jar` |
| `mc/1.21.10` | `1.21.9`, `1.21.10` | `>=1.21.9 <1.21.11` | `mc1.21.10-v2.2.050` | `mcmmo-2.2.050+mc1.21.9-1.21.10.jar` |
| `mc/1.21.8` | `1.21.6`, `1.21.7`, `1.21.8` | `>=1.21.6 <1.21.9` | `mc1.21.8-v2.2.050` | `mcmmo-2.2.050+mc1.21.6-1.21.8.jar` |
| `mc/1.21.5` | `1.21.5` | `>=1.21.5 <1.21.6` | `mc1.21.5-v2.2.050` | `mcmmo-2.2.050+mc1.21.5.jar` |
| `mc/1.21.4` | `1.21.4` | `>=1.21.4 <1.21.5` | `mc1.21.4-v2.2.050` | `mcmmo-2.2.050+mc1.21.4.jar` |

⚠️ One dangling tag survives: `mc1.21.11-v2.2.050-build.3` (`afb2a6a6a`) has **no release attached**,
and the reaping sweep enumerates `gh release list`, so a bare tag is invisible to it. Harmless, but
it means *"the sweep keeps one tag per line"* is false — it keeps one **release** per line.
**R-t leaves it standing deliberately** — no sweep can reach it, so it needs its own decision.

🔴 **And the sweep did not keep one release per line either** — six **orphaned drafts** were found
2026-08-13 sitting alongside those five published releases. ✅ **FIXED and CLEAN as of `f18cbef82`
and its four back-ports: 0 drafts, 5 published, one per band.** See **§11.1** for the mechanism and
the observation that proved it.

🔑 **So `1.21.6`, `1.21.7` and `1.21.9` are NOT missing** — they are covered by the `mc/1.21.8` and
`mc/1.21.10` jars respectively. Nothing needs building for them.

## What is genuinely missing — **4 `1.21.x` versions + all 4 `26.x`**

| Band | MC versions | Manifest rows (absent · sig-changed) | Status |
|---|---|---|---|
| `1.21.3` | `1.21.2`, `1.21.3` | 16 · 28 = **44** | ⬜ not cut |
| `1.21.1` | `1.21`, `1.21.1` | 65 · 60 = **125** | ⬜ not cut — 🔴 see R-m |
| `26.x` | `26.1`, `26.1.1`, `26.1.2`, `26.2` | n/a — **full yarn→official rename** | ⬜ not cut |

**Three branches to cut.** They were excluded by ruling R-b's `1.21.5` floor, which **R-l now
supersedes** — not by any technical finding except the one at band `1.21.1`.

⚠️⚠️ **Read a row count as *rows to look at*, never as work to do.** The four completed bands are
the calibration, and the table over-predicts by 3–6×:

| Band | Table said | Real code changes |
|---|---|---|
| `mc/1.21.10` | 10 | **1** |
| `mc/1.21.8` | 32 | **6** |
| `mc/1.21.5` | 27 | **~8**, two of them redesigns |
| `mc/1.21.4` | 42 | **19 compile errors** across 15 main-source files, 16 of them pure renames |

Most signature deltas are benign (a covariant return, an added overload). What the table *cannot*
price is the difference between a **signature change** and an **absence**: `getEntityWorld` cost
`mc/1.21.5` **3** broken sites and `mc/1.21.8` **57**, from the same one row.

---

## RULINGS

Carried forward and still binding: **R-a** branch-per-band · **R-c/P2-a…e** full platform seal ·
**R-d** playtest stays on master builds · **R-e** `26.x` is its own mini-project · **R-f** master =
newest band · **R-g** (as narrowed by **R-r** §10.8 — `.github/` is back on `master`, holding those
three files and nothing else) · **R-h** pushes are mine once gates are green ·
**R-i/R-j/R-k** docs are byte-identical on every branch, live wiki is never pushed ·
**R-s/R-t/R-u** (2026-08-13, tabled in **§11.0**) — R-h re-granted on its own merits, the release
sweep cleans up its own orphans rather than a hand-delete, and `master`'s `~1.21.11` stays because
there is no `1.21.12`.

| # | Question | Ruling |
|---|---|---|
| **R-l** | Support floor (2026-08-12) | ✅ **RULED (owner, 2026-08-12) — supersedes R-b's `1.21.5` floor.** Floor moves to **`1.21`**; ship all 12 `1.21.x` + all 4 `26.x`. R-b dropped `1.21`–`1.21.4` to dodge the component-API cliff; that cliff is real but it is confined to band `1.21.1`, and `1.21.4`/`1.21.3` were never touched by it. *Was recorded PROPOSED; ruled when the owner directed Phase 8 to start, which exists only under this floor.* |
| **R-m** | Band `1.21.1`'s three absent subsystems | ✅ **RULED (owner, 2026-08-12): capability-probe and disable.** Ship the band with Farmer's/Fisherman's Diet, Unarmored, Agility and Stealth switched off rather than reimplemented. Reimplementing three seams against the 2024 API is larger than the other three bands combined, and 6.4's stop-loss would have justified dropping the versions entirely — this keeps them shipping. |
| **R-o** | Push the Phase 10 work? (2026-08-13) | ✅ **RULED (owner): push all five branches.** Four bands push = **four real GitHub releases**, under the new `+mc…` jar names and the de-suffixed `mc<VER>-v<mod version>` tags. ⚠️ `gh` is unauthenticated in this working copy and the `github` MCP has never connected, so the **push** is all that can be confirmed from here — the releases themselves must be read off the Actions tab. Land every pending change *before* pushing, or each band releases twice. |
| **R-p** | `2.2.050` vs `2.2.50` (§10.6.1) | ✅ **RULED (owner): keep `2.2.050`.** It mirrors upstream mcMMO's padded patch, and silently re-identifying the mod was never in scope for a naming task. The consequence is accepted and permanent: Fabric's semver parser normalises the padding away, so the **filename says `2.2.050+mc…` while ModMenu displays `2.2.50+mc…`**. Pre-existing — `2.2.050-build.28` already displayed as `2.2.50-build.28`. **§10.6.1 is closed.** |
| **R-q** | The `f73031ed9` drift (§8.1a.E) | ✅ **RULED (owner): a band-appropriate equivalent on each band**, carrying `Backport-of: f73031ed9`. Not a waiver file, not a retroactive `Backport-not-needed:` — the trailer stays the single mechanical answer to *"did this reach every band?"*, and `drift-audit.py` needs no new concept. ⚠️ **"Equivalent" is per band and had to be measured, not assumed** — see the ladder in 8.x.6: two bands take `master`'s expression verbatim, and `mc/1.21.8` provably **cannot**. |
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
- [ ] **8.x.4** ✅ **No longer a trap — but verify it, don't assume it.** A branch cut from `master`
      now **inherits** `.github/FUNDING.yml`, `workflows/release.yml` and `workflows/drift-audit.yml`,
      because **R-r** restored them there and tracked files survive a branch cut regardless of
      `.gitignore`. This is the reverse of the hazard that caught `mc/1.21.5`, which was cut after
      R-g had emptied `master` and so silently never released.
      ⚠️ Still run `git ls-tree -r --name-only HEAD -- .github` on the fresh branch and expect
      **exactly those three paths**. `.github/` remains in `.gitignore`, so anything absent must be
      re-added with `git add -f` **by explicit path** — never `git add -f .github`, which sweeps in
      the 12 untracked Copilot files no branch tracks.
- [ ] **8.x.5** Compile. Work errors against `BAND_TABLE.md`. **Fix inside `fabric/` and `platform/`
      only** — `PlatformBoundaryGuardTest` must stay green. Phase 2's blast-radius cap has now held
      on two real MC API breaks.
- [ ] **8.x.6** 🔑 **Ask first, every band: can `master` absorb the difference instead?** Widening
      `CHEAT_COMMAND` to `java.util.function.Predicate` on `master` cut `mc/1.21.10`'s whole
      main-source diff to one token. It fails when there is no overlapping name on both sides
      (`getEntityPos`), so ask, don't assume.

      ⚠️⚠️ **And then measure the absorption's actual reach — MC API availability is NOT monotonic.**
      `f73031ed9` absorbed the world accessor by writing `(ServerWorld) player.getEntityWorld()`,
      and its commit message claimed that made "one expression correct on every band". It does not.
      Measured 2026-08-13 with `scripts/javap-mc.sh` across all 12 cached merged jars, reading
      **both** `Entity` and `ServerPlayerEntity` because javap never lists inherited members:

      | MC | `Entity#getEntityWorld()` | `ServerPlayerEntity` covariant | the expression that compiles |
      |---|---|---|---|
      | `1.21` – `1.21.5` | ✅ returns `World` | ❌ none (`getServerWorld()` instead) | `(ServerWorld) getEntityWorld()` — cast **required** |
      | `1.21.6` – `1.21.8` | ❌ **absent** | `ServerWorld getWorld()` | `getWorld()` — the cast form **does not compile** |
      | `1.21.9` – `1.21.11` | ✅ | ✅ `ServerWorld getEntityWorld()` | either; the cast is a no-op |

      The accessor is **present at `1.21.5`, gone at `1.21.6`–`1.21.8`, back at `1.21.9`** — yarn
      mapping churn, not a linear deprecation. So an absorption verified against the newest and the
      oldest version in scope can still be **false in the middle**, and nothing on `master` would
      ever show it: `master` compiles either way. Check every band the expression claims to cover,
      not just the endpoints. 🔑 The payoff survives the correction — `mc/1.21.3` and `mc/1.21.1`
      will both carry **zero diff** on those lines, which is the same shape that collapsed
      `mc/1.21.10`'s diff to one token.
- [ ] **8.x.7** Run the full ship gate (below). Then push.
- [ ] **8.x.8** Back-port anything that belongs on `master` **to `master` first**, then to every
      other band with `Backport-of:` trailers.
- [ ] **8.x.9** Raise the weekly drift audit's floor: `--require-bands` in
      `.github/workflows/drift-audit.yml` goes to the **new** band count, on `master` first and then
      on every band. *(This was old 4′.4, struck by R-g and un-struck by **R-r**.)* The floor is what
      makes *"found no bands"* fail instead of reading as a clean audit. Leaving it stale is
      under-strict rather than noisy — the audit still passes — which is exactly why nothing will
      remind you to do it.

### The bands

- [x] **8.1 — `mc/1.21.4`** ✅ **SHIPPED.** Cut and ported at `bfb1c11d6`, all seven gates green,
      pushed at `88d472a44`. It proved the loop, and it is the last band `config-id-audit.py` can
      still audit. Findings in **8.1a** below — read A before cutting `1.21.3`.
- [x] **8.2 — `mc/1.21.3`** (`1.21.2`, `1.21.3`) ✅ **SHIPPED 2026-08-14** at `a4f39bfde`.
      Released `mc1.21.3-v2.2.050`, both jars attached, **verified from the API** (repo-wide: 6
      published, 0 drafts, nothing reaped). **Coverage is now continuous `1.21.2` → `1.21.11`.**
      Seven gates: build **1726/0/0** · mixin-allow exit 0 on **both** band versions · boot-check
      PASSED on **both** (same jar, sha256-confirmed) · config-id exit 0 (53 absent, 0
      dead-everywhere) · brew-smoke with a discriminating control · gameplay-smoke **29/29** with the
      control failing as it must · drift-audit **0 MISSING** on all five bands.
      ⚠️ **The mob-origin defect DID reproduce, exactly as predicted**, and on *both* versions — the
      `1.21.2` fabric-api (`0.106.1`) carries the identical unconditional `putfield`. Fixed at
      `bf2676292`; `combat-egg-control` now pays 0 where `combat-fist` pays 610.
      🔴 **The cut's real lesson is 8.2.5b: "20 compile errors → 0" was NOT the finish line.** Four
      more injectors were broken and *compiled perfectly*. Run gate 2 **before** gate 1 next time.
- [ ] **8.3 — `mc/1.21.1`** (`1.21`, `1.21.1`; 125 rows). Per **R-m**, two pieces: the `SkillGating`
      switches land on **`master` first**, then the band branch resolves the compile/mixin absences.
      ✅ **Unblocked — 8.4 shipped.** Do this band **last** — it is the only one that changes
      `master`.
      🔴 **Two extra renames now, from the Taming work below.** Record them here while the evidence
      is fresh, because both are *compile* failures on that band rather than checks that answer
      wrong:
      - `EntityAttributes.FOLLOW_RANGE` → **`GENERIC_FOLLOW_RANGE`** on `1.21` / `1.21.1`. This is
        the same rename `SkillAttributeService`'s existing `MOVEMENT_SPEED` reference hits, so both
        land in one edit.
      - `EntityNavigation#setMaxFollowRange` **does not exist below `1.21.2`** (`javap` on the 1.21
        merged jar shows only `rangeMultiplier` / `setRangeMultiplier`). That absence is *why* the
        reach fix uses the attribute rather than the navigation setter — nothing to port, but do not
        "simplify" it back on `master` either.

---

## Taming — pet combat mode + the ranged reach fix (owner track)

⚠️ **The plan lived in an untracked `fix.md`, which was deleted on 2026-08-18 once this section
carried everything it held** (owner ruling D-3). This section exists because the *back-port* is
multi-version work and `.agent/memory/` does not survive a clone — per **R-n**, a fact another checkout
must know belongs in a tracked file, and this is it. 🔑 The deletion is the reason the rulings table
below exists: gate 2 of the destructive-action procedure asked where the content survived, and for four
of the six rulings the honest answer was *nowhere*.

- [x] **Phases 1–7 ✅ DONE on `master`, 2026-08-17**, four commits, **not yet pushed**:
      `e0d7e054e` (guard fix) · `b070534a4` (stance + gesture) · `920f2d140` (targeting + reach +
      trigger gaps) · `381c10b2a` (`mc-surface.txt`).
      All seven gates green: mixin-allow `60 OK / 61` · build **1803 tests, 0 failures** ·
      `mc-surface` regenerated · config-id **0 dead-everywhere** · `boot-check` exit 0 on a real
      `1.21.11` server · `gameplay-smoke` **29/29** with the control failing as it must.
- [ ] 🔴 **LIVE PLAY-TEST — owner only.** No gate above can see this bug. Shoot a mob at ~25 blocks
      with a wolf at your heels **in passive mode** and watch it close, then toggle and watch the
      pack pick its own fight. Every claim in the reach fix is bytecode plus unit tests and **none of
      it has been watched in a world**. §G debt is real: the repair-anvil fix shipped green and
      unwatched. Budget 3 attempts on any failure, then stop and report.
- [ ] **Push, then `python scripts/drift-audit.py --self-test && … --master master`** — it audits
      `origin/master`, so an unpushed commit reads as clean.
- [ ] **Back-port all four commits to all five `mc/**` branches** with `Backport-of:` trailers. This
      is a **version-agnostic logic change** — the exact class that made up 11 of the last 12
      forgotten fixes. ⚠️ `mc-surface.txt` is a generated per-branch artifact: **regenerate it on
      each band, never cherry-pick it.**
- [ ] **Caveat-expiry pass.** Grep `README.md` and `wiki/` for the **symptom**, not the files
      touched: *"wolves"*, *"pets"*, *"sic"*, *"Call of the Wild"*, *"sitting"*. The wiki Taming page
      needs the new gesture, **the sit-toggle cost** (while a bone is in your main hand, sneak +
      right-click changes the stance instead of sitting the pet) and both stances. ⚠️ One wiki serves
      every band — state what a *player* does, never what version the build targets.

**The six rulings behind it** (owner, 2026-08-14 and 2026-08-17). Recorded here on 2026-08-18 when
`fix.md` was deleted: the "what shipped" bullets below carried the *mechanism*, but four of these
decisions and both of their consequences existed nowhere tracked, and a decision that lives only in a
deleted file did not happen.

| # | Ruling |
|---|---|
| R-1 | **Sneak + right-click the pet with a bone in hand.** The click is consumed so vanilla's sit-toggle does not also fire. |
| R-2 | One toggle sets **a player-level default every pet inherits.** No per-pet override. |
| R-3 | Aggressive targets are **hostiles minus creeper and ghast** — the same exclusions vanilla's own `WolfEntity#canAttackWithOwner` makes, so they are delegated rather than re-listed. |
| R-4 | The ranged fix goes as far as **fixing the reach *and* closing the trigger gaps**, not just one. |
| R-5 | The aggressive scan is **centred on the player, radius `32.0`** — not on each pet, and not 16. One box query per player per sweep whatever the pack size, and a pet that lagged behind cannot drag a mob home. ⚠️ **This makes `Aggressive_Radius` and `Engage_Range` the same number by default, and that is coherent rather than coincidence:** a mob found at 32 is only worth targeting if a pet can *path* 32. **Do not let one default drift without re-checking the other.** |
| R-6 | **Toggling to passive mid-fight lets the fight finish.** Passive gates only the *acquisition* of a new target; it never calls `setTarget(null)` — a pet frozen mid-swing with a zombie on it reads as a bug, and the reach boost is stance-independent anyway. ⚠️ **Consequence for the tests:** the passive-mode negative test must assert *"the sweep picked nothing"*, **never** *"the pet has no target"* — the latter passes for free and would stay green with the whole feature deleted. |

⚠️ **R-1 and R-2 are deliberately mismatched in scope and that is not a mistake.** The gesture aims at
one animal; the stance it flips is player-wide. The clicked pet only proves *intent and ownership* — so
**every player-facing string says "your pets", never "this wolf"**, or the first bug report is "I
toggled one wolf and the others changed too". A test pins the wording.

**What shipped, in one line each** (so a fresh clone can review the commits without `fix.md`):

- A **player-wide** pet stance, `UniqueDataType.PET_COMBAT_MODE`, `0 = PASSIVE`, unreadable values
  fail closed to `PASSIVE`. The gesture is per-pet but the stance is not — **every player-facing
  string says "your pets"**, and a test pins that wording.
- The gesture **claims its click on both logical sides** (`CONSUME`/`CONSUME`), because it is
  suppressing vanilla's wolf sit-toggle. Same rule as the repair anvil.
- Aggressive pets take the **nearest hostile to the player** (not to the pet) within 32. Candidates
  are **`Monster`, never `HostileEntity`** — the latter silently omits slime, magma cube, ghast,
  phantom and hoglin. Exclusions delegate to vanilla's `canAttackWithOwner`; the warden is the one
  explicit addition.
- The reported "pets ignore what you shoot" was **never the weapon**: a wolf's `FOLLOW_RANGE` is 16
  and `MeleeAttackGoal#canStart` bails when `findPathTo` returns null past it. Fixed with a
  **temporary** `FOLLOW_RANGE` modifier while engaged, in **both** stances.
- Three trigger gaps closed: a thrown trident sicced nothing, only arrows and tridents reached the
  code at all, and it required a loaded profile.
- ⚠️ Fixed a latent crash on the way: `PlayerProfile`'s 3-arg constructor seeded one
  `UniqueDataType` by hand while `getUniqueData` unboxed a `Map#get` to `long`, so the next constant
  added was an **NPE that took out saving for the entire profile**.

### 8.2′ — the `mc/1.21.3` plan (written 2026-08-14, before any code)

**Toolchain, resolved from the APIs rather than derived** (8.x.2 says look it up; `1.21.3+build.2`
happened to match this file's prediction, but it was confirmed against `meta.fabricmc.net`, not
trusted):

| Property | Value | Source / note |
|---|---|---|
| `minecraft_version` | `1.21.3` | newest in band, per every prior cut |
| `yarn_mappings` | `1.21.3+build.2` | `meta.fabricmc.net/v2/versions/yarn/1.21.3` — `build.2` is newest |
| `loader_version` | `0.19.3` | still the newest stable, and MC-independent — **unchanged** |
| `fabric_version` | `0.114.1+1.21.3` | Modrinth; `1.21.2` resolves to `0.106.1+1.21.2` — see the mob-origin note |
| `supported_minecraft_versions` | `1.21.2,1.21.3` | per-band, never copied |
| `depends.minecraft` | `>=1.21.2 <1.21.4` | the band's **range** |
| `modmenu_version` | `12.0.1` | lists `1.21.2,1.21.3` — one artifact covers the band |
| `cloth_config_version` | `16.0.143` | lists `1.21.2,1.21.3`; coordinate is `cloth-config-fabric`, no `+fabric` suffix in the property |

✅ **R-m's three absent subsystems are all PRESENT here**, read from `plans/BAND_TABLE.md`:
`EntityAttributes.*` (all 8), `ConsumableComponent`/`DataComponentTypes#CONSUMABLE`, and
`PlayerInput`. So this is an **ordinary rename-shaped band** — the component cliff really is confined
to `1.21.1`, exactly where Phase 1.5 measured it. Nothing here needs R-m's gating work.

#### 🔴 The ordering problem, and why the docs commit goes FIRST on `master`

`BandDocsMatchRealityTest` runs **inside gate 1**, so `mc/1.21.3` cannot go green until the support
floor moves. But R-i/R-j require the docs be **byte-identical on every branch**, and AGENTS.md
requires fixes land on `master` first. Those three pull against each other, and the naive order
(fix the docs on the band to make it green) violates two of them.

**Resolution — commit the docs change on `master` first, locally, then cut the band from that
commit.** The new branch then *inherits* correct docs with no back-port at all, and the other four
bands take an ordinary cherry-pick.

⚠️ **The window this deliberately accepts:** between the `master` docs commit and the band's release,
`master`'s README claims `1.21.2`/`1.21.3` are supported when no jar exists yet. It is bounded by
keeping the push to the end — **nothing is pushed until the band is green** — and it is the lesser
of the two errors: the inverse (a shipped band whose own docs deny it) is R9's *recorded* instance,
this one is a promise that comes true within the hour.

🔑 A docs-only `master` commit **does not release** — `README.md` and `wiki/` are outside
`release.yml`'s `paths:` filter (verified 2026-08-14, `release.yml:75-83`). So this costs no release.

⚠️ **`drift-audit.py` will neither demand nor confirm the docs back-port** — docs are deliberately
outside `PROPAGATABLE_PREFIXES` (R9). The check is by hand:
`git diff --name-only master <band> -- README.md wiki/` must print **nothing**, on all five bands.

#### 8.2 — the steps

- [ ] **8.2.0** On `master`: move the floor sentence `1.21.3` → **`1.21.1`** in **both**
      `README.md:44` and `wiki/Installation.md:28`, and add the new band to the three tables that
      enumerate bands (`README.md:9` version span, `:27-31` the jar/toolchain matrix, `:330-334` the
      branch list). Caveat-expiry pass greps the **symptom** — `1.21.4 – 1.21.11`, `1.21.4` as
      "oldest", `and older are not supported` — not the files edited.
      ⚠️ `everyVersionThisBandShipsAppearsInTheReadme` needs a real mention of **both** `1.21.2` and
      `1.21.3`; today `1.21.3` appears *only* inside the floor sentence being deleted, so removing it
      without adding the matrix row swaps one failure for another.
- [ ] **8.2.1** `git switch -c mc/1.21.3` off that `master` commit.
- [ ] **8.2.2** First commit pins the toolchain above in `gradle.properties` **and nothing else**.
- [ ] **8.2.3** `fabric.mod.json` `depends.minecraft` = `>=1.21.2 <1.21.4`.
- [ ] **8.2.4** `git ls-tree -r --name-only HEAD -- .github` → expect **exactly three** paths
      (`FUNDING.yml`, `workflows/release.yml`, `workflows/drift-audit.yml`), inherited via R-r.
      Then prove the inherited tooling actually runs here: `release-sweep-selftest.sh --mutate` 6/6
      with all four mutations *applied and* caught, and `ci-watch.sh --self-test` 6/6.
      ⚠️ **Never `git add -f .github`** — it sweeps in 12 untracked Copilot files no branch tracks.
- [ ] **8.2.5** Compile; work errors against `plans/BAND_TABLE.md`. Fix **only** inside `fabric/` and
      `platform/` — `PlatformBoundaryGuardTest` stays green.
- [ ] **8.2.6** Per 8.x.6, ask **first** whether `master` can absorb each difference — and measure the
      absorption's reach across **every** band it claims, not just the endpoints (the `getEntityWorld`
      lesson: present at `1.21.5`, absent `1.21.6`–`1.21.8`, back at `1.21.9`).
- [ ] **8.2.7** ⚠️ **Expect the mob-origin defect (8.1a.A).** This band pins fabric-api `0.114.1`,
      *older* than the `0.119.4` whose `data-attachment-api 1.6.2` carried the unconditional assign —
      so it is very likely present and possibly worse. Read `fabric_readAttachmentsFromNbt` from this
      band's own bytecode before trusting `combat-egg-control`. **Every static gate is blind to it**:
      build, boot-check and the mixin audit all pass with it live. If it reproduces, port the
      `EntityType#getEntityFromNbt` RETURN re-stamp **and** `MobOriginRestampSeamTest` from
      `mc/1.21.4` — band-only, since `master` has no such defect.
- [ ] **8.2.8** Full ship gate 1–7, then push, then gate 8. Gates 3/5/6 run against the **pinned**
      `1.21.3`, matching the `mc/1.21.8` and `mc/1.21.10` precedent.
      ⚠️ **`1.21.2` is therefore not boot-proven, and that is a stated narrowing, not a pass.**
      Normally harmless (one jar, one band, identical surface across all 1386 records) — but the
      mob-origin defect is a **fabric-api** defect, and `1.21.2` resolves a *different* fabric-api
      (`0.106.1` vs `0.114.1`). So if 8.2.7 reproduces, add a second `boot-check.sh <jar> 1.21.2` run.
- [ ] **8.2.9** Back-port the docs commit to the other four bands (no `Backport-of:` trailer is
      required by the auditor, but carry one anyway), then verify the byte-identity by hand on all
      five. Then raise `BAND_COUNT` **4 → 5** in `.github/workflows/drift-audit.yml` on `master`
      first and back-port to all five bands (8.x.9).
      ⚠️ **Order matters:** the floor must go to `5` only **after** `mc/1.21.3` is pushed, or the next
      weekly audit fails on a band that does not exist yet. A stale floor is *under*-strict and will
      not remind you; a premature one is a red run nobody is watching (R11).

#### 8.2 — blast radius

| Step | Touches | Lost if wrong | Comes back from |
|---|---|---|---|
| 8.2.0 | `README.md`, `wiki/Installation.md` on `master` | nothing — docs only, and **no release fires** (outside `paths:`) | `git revert`; `master` clean at `a7a4f13d5` |
| 8.2.1–8.2.7 | a **new** branch only | nothing — no existing branch is touched | `git branch -D mc/1.21.3` while unpushed |
| 8.2.8 push | **a new public release line** `mc1.21.3-v2.2.050` | a bad jar published under a new tag | the sweep only matches `mc1.21.3-v*`, so it **cannot reach another band**; delete the release + tag, or fix forward and re-push |
| 8.2.9 | 4 existing bands (docs) + 5 branches (`.github/`) | docs revert; a wrong `BAND_COUNT` makes the weekly audit red or vacuous | per-branch `git revert`; neither path is in `release.yml`'s `paths:`, so **no band re-releases** |

🔴 **8.2.8 is the only outward-facing step.** R10 is live — before pushing, confirm no other branch
resolves to `minecraft_version=1.21.3`, or the two runs delete each other's release.

### What I am NOT doing in 8.2

- **Not cutting from `mc/1.21.4`**, however tempting given the near-subset absent-set. Bands are cut
  from `master` so their diffs stay independent; inheriting `1.21.4`'s back-compat fixes would make
  this band's real cost unmeasurable and hide whichever of them is unnecessary here.
- **Not fixing the mob-origin defect on `master`.** `master` has no such defect; a redundant injector
  there would add mixin surface and `allow=` audit churn on six branches for nothing (asked and
  answered identically at 8.1a.A).
- **Not touching `minecraft_version` on `master`** to resolve any band difference — the standing rule.
- **Not starting `mc/1.21.1` (8.3) or Phase 9.** `1.21.1` is the only band that changes `master`
  (R-m) and goes last.

### 8.1a — what the `mc/1.21.4` cut found

Two gameplay defects blocked the band after every static gate had passed: 0 ERROR lines, 0 mixin
failures, 1706/0/0 tests. **Both were band-specific and measured, not assumed** — a detached-worktree
build of `master` `f73031ed9` scored 29/29 on `1.21.11`, so both fixes belonged on the branch.

#### A — the mob-origin stamp was erased before it was ever read

**Root cause, proven from bytecode.** That band's fabric-api pins `data-attachment-api 1.6.2`, whose
`fabric_readAttachmentsFromNbt` assigns the deserialized map **unconditionally**; master's 1.8.48
early-returns when that map is null. `deserializeAttachmentData` returns null when the NBT carries no
attachments, so on this band `Entity#readNbt` **wipes the whole attachment map**. mcMMO stamps
`MOB_ORIGIN` at `EntityType#create(World, SpawnReason)`, which runs *before* the NBT read on every
NBT-carrying spawn — so `/summon`, spawners and chunk load all produced a mob reading as `NATURAL`.
⚠️ There is **no version bump out of it**: `0.119.4+1.21.4` is the newest fabric-api for that MC.

- Spawn eggs were never exposed: `nbtCopier` short-circuits on an empty `entity_data` component, and
  when the component *is* present `NbtComponent#applyToEntity` does
  `writeNbt(fresh) → copyFrom(nbt) → readNbt` — a round-trip that re-serializes the live attachment
  map, so the stamp survives its own erasure.
- Fixed by re-stamping at `EntityType#getEntityFromNbt` RETURN, ordering-proof from the same
  disassembly. ⚠️ **Not** a second injector on `Entity#readNbt` — that races fabric's own injector at
  the same target, and cross-mod mixin priority is not something to bet a silent exploit gate on.
- `master` does **not** absorb this (step 8.x.6, asked and answered): master has no defect, and a
  redundant injector there would add mixin surface and `allow=` audit churn on five branches for
  nothing.
- ⚠️ **That band carries 62 injectors, not the 61 the ship gate below quotes.** 61 is `master`'s.
- ⬜ **Still open: a guard test that fails when the fix is reverted.** No Mockito test can see this —
  the defect lives in fabric-api's bytecode and the fix in an injection point. Follow
  `guards/ArmadilloBrushDispenserExclusionTest`: assert from **that band's bytecode** that
  `EntityType#getEntityFromNbt` really does call `Entity#readNbt`, and from source that an injector
  selects it at RETURN. Anti-vacuity count assertions first, then mutation-prove it.

#### B — the `repair` smoke failure was the HARNESS, not the mod

Resolved by a 9-run study: **8 × 29/29, 1 × 27/29**. The one failure was not the `repair` phase at
all — it was `combat-fist`/`combat-sword` being handed a **structure-spawned** cow, which
`Nether_Portal.Multiplier: 0` correctly prices at zero. The anti-farm gate was working.
⚠️ `repair-control` passing was worth nothing on its own: it asserts REPAIR does *not* move, which a
completely dead listener satisfies just as well. Fixed on `master` at `ec9b497f7` and back-ported to
every band — the combat phases now verify the target carries **no** `mcmmo:mob_origin` attachment, so
a structure-spawned mob reports INCONCLUSIVE instead of a false FAIL. Also closed a hole in
`--self-test`, which fed a **synthetic** log built from `requires_markers` itself, making a required
marker that no command emits invisible — and its symptom is not a failure but INCONCLUSIVE forever.

#### E — the `f73031ed9` drift ✅ CLOSED by **R-q**

`f73031ed9` was MISSING on `mc/1.21.5`, `mc/1.21.8` and `mc/1.21.10` — R8's exact shape. The owner
ruled: land a band-appropriate equivalent on each, carrying `Backport-of: f73031ed9`. **What
"equivalent" means had to be measured** (the ladder in 8.x.6), and it is not uniform:

| Band | Equivalent | Why |
|---|---|---|
| `mc/1.21.10` | `master`'s expression verbatim | covariant override exists at `1.21.9`; the cast is a no-op |
| `mc/1.21.5` | `master`'s expression verbatim | no covariant override at all — the cast is **required** |
| `mc/1.21.8` | **comment only** | `Entity#getEntityWorld()` is **absent**; `getWorld()` is the band's only accessor, so it keeps its own expression and carries the warning instead |

### 8.4 — ✅ SHIPPED: `config-id-audit.py` now audits every version in scope

- [x] Give `config-id-audit.py` a registry-id source that works below `1.21.4`, or the `1.21.3` and
      `1.21.1` bands ship with **one of the seven gates missing**.

**Done 2026-08-13.** All 12 versions audit clean: **689 ids, 0 dead-everywhere**, worst-case control
`1.21` at **91.3%** against the 80% floor. `scripts/mc-ids.txt` (12 versions, **30,225 ids**) is
generated by `scripts/extract-mc-ids.py` and guarded by `guards/ConfigIdManifestTest`.

🔑 **The gate got *more* portable, not just wider.** It used to read this machine's Loom cache, so
*"dead on every supported version"* silently meant *"dead on every version I happened to have built
against"* — a different question with a different answer on every checkout. It now reads a committed
manifest, so the verdict is identical everywhere and a band branch needs **no** local jar cache.

`assets/minecraft/items/` was added in `1.21.4`; below that the script **refuses to report rather
than calling every item absent** (`config-id-audit.py:310`) — correct behaviour, and it means 689 id
references across six config files go unchecked on two of the three new bands. That gate is what
found three live XP holes on `master` that no compiler, test or boot log could see.

⚠️⚠️ **Both obvious replacements are already ruled wrong and neither fails visibly:** `javap Blocks`
yields **yarn field names, not registry ids** (and `CopperBlockSet` hides several ids behind one
field), and `lang/en_us.json` **keeps stale keys through renames** — `1.21.11` still carries
`block.minecraft.chain`, so the exact bug the audit found reads as clean there.

#### 8.4.0 — the source, chosen by measurement (2026-08-13)

**Chosen: the vanilla data generator's `reports/registries.json`**, which is the registry itself
rather than a proxy for it. Four candidates were measured against the authoritative
`assets/minecraft/items/` set on `1.21.4` and `1.21.11` before anything was written:

| Candidate | Verdict | Measured |
|---|---|---|
| `reports/registries.json` | ✅ **exact** | item set **identical** both versions (1385 / 1505, zero diff either direction) |
| `assets/minecraft/models/item/` | ❌ | the `1.21.4` item-model split moved block items out — **795 of 1385** items have no such file there, and the relationship *changes at exactly the version the cross-proof needs*, so it can never be validated |
| class constant pools (`Items.class`) | ❌ | misses **all 112 spawn eggs** (built by string concatenation, no literal) and yields **370** junk tokens (`apply`, `armor`, `_spawn_egg`) |
| `javap` field names / `lang/en_us.json` | ❌ | already ruled wrong; see above |

🔑 **No download is needed and none should be added.** Loom already caches the official Mojang
**server bundler** jar for all 12 versions at `~/.gradle/caches/fabric-loom/<v>/minecraft-server.jar`,
and the bundler carries its own libraries, so
`java -DbundlerMainClass=net.minecraft.data.Main -jar <jar> --reports` runs **fully offline** in ~30s
per version. Verified on `1.21`, `1.21.3`, `1.21.4`, `1.21.11`.

🔑 **The swap also corrects a defect in the current source, which over-reports blocks by exactly 2 on
every version:** `item_frame` and `glow_item_frame` have `blockstates/` files and are **not blocks**
— they are entities whose models are declared that way. Constant across all 12 versions, and no
shipped config names either, so the correction changes no audit verdict. It is recorded because a
source that is wrong by a fixed amount reads as right forever.

#### 8.4.1 — the work

- [x] **8.4.1a** New `scripts/extract-mc-ids.py` (generator) → committed `scripts/mc-ids.txt`
      (manifest). Deliberately the **same generator/generated split as `extract-mc-surface.py` /
      `mc-surface.txt`**, and — unlike that pair — the manifest is a fact about *Minecraft*, not
      about our code, so it is **identical on every band and must NOT be re-derived per band**.
      That is the inverse of the trap in `generated-artifact-is-per-band`; state it in the header of
      the file itself, because the next person will assume the rule they already learned.
- [x] **8.4.1b** Cross-validation is **permanent, not a one-time proof**. Wherever both sources
      exist (`1.21.4`+), the generator asserts registry-item == asset-item exactly and
      registry-block == asset-block modulo the documented 2, and **refuses to write** on any other
      diff. This is what "prove it before leaving the old one behind" has to mean if the old source
      is not to rot unnoticed.
- [x] **8.4.1c** `config-id-audit.py` reads the manifest; the `_at_least_1_21_4` floor and its
      `SystemExit` go. ⚠️ The DEAD-EVERYWHERE comparison set moves from *"whatever this machine's
      Loom cache holds"* to *"every version in the manifest"* — deterministic, and the whole reason a
      band branch can now run gate #4 at all.
- [x] **8.4.1d** `--self-test` gains both directions: the manifest parser round-trips, **and** the
      cross-validation detector **fires** when fed a corrupted set. A guard that has never failed is
      not known to work — sixth sighting; assume this one is vacuous until it has failed once.
- [x] **8.4.1e** New `src/test/java/com/gmail/nossr50/guards/ConfigIdManifestTest.java`: assert the
      manifest's row for **this band's pinned version** matches the **live registry inside the
      build**. 🔑 That is a genuinely *third* independent authority — not the jar assets and not the
      data generator — and it runs inside `./gradlew build` (gate #1) on every band, so the manifest
      cannot rot in the one place a person has to remember to look.
- [x] **8.4.1f** Re-check `MIN_CONTROL_RESOLVE_RATE` (0.80). It was sized from `1.21.5` at 93.8%;
      `1.21`/`1.21.1` are now in scope and will be the worst band. **Measure the real rate and
      re-size or re-justify the floor** — do not leave a number sized from a version that is no
      longer the worst case.
- [x] **8.4.1g** Cherry-pick generator + manifest + test to all four bands with `Backport-of:`.
      ⚠️ `drift-audit.py` **does not track a `scripts/`-only commit**, so nothing will report this
      missing — and the Java test lands in `src/`, so on the bands that half **releases**.

#### 8.4.2 — blast radius

| Step | Touches | Lost if wrong | Comes back from |
|---|---|---|---|
| 8.4.1a–d | `scripts/` only | nothing — the audit is read-only over configs and jars | `git revert`; `master` clean at `ac5b62471` |
| 8.4.1e | new test file | nothing | delete the file |
| 8.4.1g | 4 band branches, incl. `src/` | **a band release fires** on the `src/` half | per-band `git revert`; the workflow deletes its own tag on failure |

The generator writes exactly one path (`scripts/mc-ids.txt`) and regenerates deterministically from
jars it only reads, so its own destructive surface is one overwrite of a committed, diffable file —
gated behind a printed per-version add/remove count rather than a silent write.

---

## Agility retirement — fold the last two sub-skills into Parkour / Swimming / Flying

**Owner ruling, 2026-08-17. Tier 2 — it removes a `PrimarySkillType` constant.** The four design
questions this section used to leave open were **asked and answered by the owner on 2026-08-17,
before the first edit**. They are recorded here rather than only in `.agent/memory/decisions.md`
because `.agent/` is gitignored and **does not travel to a band branch** — the back-port needs them.

**The decision:** `AGILITY` goes away as a skill. Its two surviving sub-skills split across the three
movement parents that already own everything else — **six single-rank sub-skills, not two 3-rank
ones**, because a sub-skill's parent is derived from its enum name prefix and one constant cannot
span three parents.

| Today | Becomes |
|---|---|
| `AGILITY_FLEET_FOOTED(3)` — land, water, air | `PARKOUR_FLEET_FOOTED(1)` · `SWIMMING_FLEET_FOOTED(1)` · `FLYING_FLEET_FOOTED(1)` |
| `AGILITY_SECOND_WIND(3)` — land, water, air | `PARKOUR_SECOND_WIND(1)` · `SWIMMING_SECOND_WIND(1)` · `FLYING_SECOND_WIND(1)` |

⚠️⚠️ **This overrules the stated reason those two were kept**, and the re-home answers it rather than
routing around it (see A-1). The 2026-08-10 restructure re-parented every *single-medium* sub-skill
and left exactly these two on Agility because each carries **one rank per medium**, gated by the mean
of Parkour/Swimming/Flying — *"there is no single parent whose level could gate all three of their
ranks"*. Dropping the child skill deletes that gate. A mechanical find-and-replace here ships
sub-skills that unlock at the wrong level and **nothing fails** — no compiler and no test reads a
balance number.

### The four rulings (A-1 … A-4)

**A-1 — Unlock levels: FLATTEN TO RANK 1's NUMBER.** Fleet Footed unlocks at **1** in each parent;
Second Wind at **250** in each (RetroMode; Standard is /10). The 1/200/400 and 250/500/750 ladders
encoded *unlock order* on one shared ladder; split across three parents there is no order left to
encode, and keeping them would make a swimmer wait until Swimming 200 for the perk a runner gets at
Parkour 1.
⚠️ **This is a buff for every specialist and the deliberate end of the all-rounder gate.** A pure
flier caps Agility at 333 today and can *never* reach the air ranks; afterwards they get air Fleet
Footed at Flying 1 and Limitless at Flying 250. **Say so in the wiki in plain words.**

**A-2 — Second Wind stays ONE `SuperAbilityType`**, its parent resolved from the medium at
activation. One cooldown slot, one `Second_Wind_Item`, one cooldown/duration block — from the
player's seat it stays a single button whose effect depends on where you are. Three constants were
rejected as a real buff (Dart on land → dive → Aquaman → glide out → Limitless, all chainable).
⚠️ `SECOND_WIND.subSkillTypeDefinition` is a **static single binding** with exactly two production
callers (`RankUtils#getSuperAbilityUnlockLevel` at `RankUtils.java:245`, and `McMMOPlayer.java:1234`)
plus `PlaceholderSuperAbilityTest`, which asserts it is never null. Keep it bound — to
`PARKOUR_SECOND_WIND` as the nominal constant — and add a medium-aware `subSkillFor(Medium)` beside
it. **Leaving the field null "to be resolved later" is an NPE on a path no test walks** — the exact
shape of the Taming `UniqueDataType` save-path defect.
🔑 **A-1 is what makes that nominal binding safe**: all three unlock at 250, so the one static answer
is correct for every medium. That is a *coupling*, not a coincidence — if the three ever diverge the
binding starts lying silently. **A test pins "all three `*_SECOND_WIND` unlock at the same level"**,
and its failure message must say why.

**A-3 — Rename `skills/agility/` → `skills/movement/`, `AgilityManager` → `MovementManager`**
(`datatypes/skills/subskills/agility/` moves too). `AgilityManager` has hosted every Parkour,
Swimming and Flying sub-skill since 2026-08-10 — it is the movement manager in all but name, and a
package named for a deleted skill is the rot this repo keeps getting burned by.
⚠️ **Cost accepted knowingly:** the rename makes the five band cherry-picks conflict-prone, so it
lands as its **own commit, separate from behaviour**, and last.

**A-4 — Delete the Agility advancement tree; reissue the rank plaques under the new parents.**
`milestone/skill/agility`, `milestone/level/agility/*`, `milestone/maxed/agility` and
`milestone/rank/agility_{fleet_footed,second_wind}/*` go. ⚠️ **Accepted player-visible cost:** an
earned Agility advancement silently vanishes from the player's list.

**A-5 — `/mcstats agility` gets a retirement message, not an unknown-skill error.** Keep exactly one
locale string saying the skill was retired and its perks now live under Parkour, Swimming and Flying.
**Why:** a player who levelled Agility for weeks and gets *"no such skill"* concludes the mod broke.
`Agility.SkillName` / `Overhaul.Name.Agility` / `XPBar.Agility` still go — the bar simply stops
appearing; this is one string and one branch in the command, not a retained skill.

**A-6 — the dead `Skills.Agility:` block STAYS in the player's `advanced.yml`; `ConfigRetunes` logs
that it is dead and moves on.** `ConfigRetunes` moves the *values* to the new per-parent paths (per
"Concrete target state" below) and then **leaves the orphan block alone**. **Why:** deleting a block
from a file the player owns is a write we cannot prove safe for every hand-edited config, and rule
zero prices that far above the tidiness it buys. Cost accepted: a dead `Skills.Agility:` section sits
in an existing player's config indefinitely, so the log line must say plainly that it is no longer
read.
⚠️ **This is a refusal to destroy, so it needs the CONVERSE test**, not a guard test: load a config
carrying `Skills.Agility:`, run the retune, and assert the block is **still there** and the new paths
are populated. Otherwise a later "cleanup" deletes it and nothing fails.

**A-7 — ONE combined back-port pass, at the very end** (owner, 2026-08-17). The four Taming commits
do **not** go to the bands now; they wait for the Agility retirement to finish and both tracks are
cherry-picked together.
⚠️ **Cost stated and accepted at the time of the ruling:** F's rename means the *Taming* picks will
conflict too, not just the Agility ones, and ~10 unpropagated commits is the exact accumulation that
made 11 of the last 12 forgotten fixes. **Mitigation, mandatory:** take **F as its own cherry-pick,
after every behaviour commit**, on every band.

**A-8 — the movement manager is keyed NOMINALLY on `PARKOUR`** (owner, 2026-08-18). `AgilityManager`
extends `SkillManager`, which requires a `PrimarySkillType`; with `AGILITY` gone no single skill
honestly owns a manager that hosts Parkour's, Swimming's and Flying's sub-skills. `PARKOUR` is named
because it already holds the manager's `EPISODIC_XP_SKILL` and A-2's nominal `SECOND_WIND` binding.
⚠️ **Verified inert before choosing it:** the manager never calls the inherited `getSkillLevel()`
or the 2-arg `scaleToLevel` — all four ramps pass an explicit skill (phase C) and every XP award
names its own destination.
⚠️⚠️ **The failure mode got QUIETER, not rarer.** Keyed on `AGILITY`, a passive that reached for
the inherited field scaled on the *mean* — wrong for everybody. Keyed on `PARKOUR` the same slip is
**correct for a runner** and silently wrong for a swimmer and a flier. Pinned by
`AgilityMovementTest#theNominalParkourKeyIsReadByNothing`, whose fixture is a swimmer at Parkour 0.
**Rejected:** a standalone field on `McMMOPlayer` (does not dodge the question — the ctor still needs
a skill) and a nullable `SkillManager#skill` (an NPE on a path no test walks, the exact Taming shape).

**A-9 — F renames the `Managed` constants but FREEZES their modifier id strings** (owner,
2026-08-18). `SkillAttributeService.Managed.AGILITY_FLEET_FOOTED_LAND/WATER` carry the ids
`"agility_fleet_footed"` / `"agility_fleet_footed_water"`, and **those strings are written into
player entity NBT**. `clearAll` removes only ids it still knows, so a renamed id **strands a
permanent speed buff** on every existing player. The constants become `MOVEMENT_FLEET_FOOTED_*`; the
id literals keep their old spelling forever, with a comment saying why they must never change.
**Rejected:** new ids plus a one-time legacy sweep — a write to live player entity state bought
purely for cosmetics, and the sweep needs its own guard and test or it *is* the stranding bug.

### Concrete target state

`skillranks.yml` — delete the `Agility:` block; add to each of the three parents:

```yaml
FleetFooted:  { Standard: { Rank_1: 1 },  RetroMode: { Rank_1: 1 } }
SecondWind:   { Standard: { Rank_1: 25 }, RetroMode: { Rank_1: 250 } }
```

`advanced.yml` — `Skills.Agility.FleetFooted.{Land,Water,Air}_MaxBonus` become one `MaxBonus` under
each parent (`0.20` Parkour, `0.50` Swimming, `0.15` Flying), each with its own `MaxBonusLevel`.
`Skills.Agility.SecondWind`'s bodies split to the medium that uses them — `Dart*` → Parkour,
`AquamanAmplifier` → Swimming, `LimitlessBoost` → Flying. The *ability-level* keys in `config.yml`
(`Second_Wind: 240` cooldown/duration, `Second_Wind_Item: FEATHER`) stay **single**, per A-2.
⚠️ **Every one of those is a moved path, so every one needs a `ConfigRetunes` entry** — the
2026-08-10 move already established the pattern *("mcMMO moves your values to the new paths on first
boot and tells you it did so")*. A changed shipped default reaches nobody who has run the mod once.

`locale_en_US.properties` — `Agility.SubSkill.{FleetFooted,SecondWind}.*` become the six
`<Parent>.SubSkill.<Name>.{Name,Description}` keys `SkillLocaleCompletenessTest` derives from the
enum. The five `SuperAbilityType` ability strings stay **one block** (A-2); they are literal keys in
the enum constructor, so re-home them to a neutral `Movement.Skills.SecondWind.*` — not to one
parent, which would read as a lie in the other two. Drop `Agility.SkillName`, `XPBar.Agility`,
`Overhaul.Name.Agility`.

**Persistence — RULED: the reader ignores the orphan, nothing migrates.** Agility is a child skill
with no stored level or XP, so a pre-2026-08-10 profile's dead `skills.AGILITY` / `experience.AGILITY`
key has nothing to migrate *to* — the two sub-skills' progress was never stored, it was derived.
`SkillRenames` stays empty; **deleting a constant is not renaming one.**
⚠️ **Prove both directions before believing it** (the Taming lesson): a test that loads a profile
carrying `skills.AGILITY` and asserts it round-trips — **loads without error AND saves without
writing the key back or NPEing.** The load path is the half everyone checks; the save path is where
the Taming defect was.

### Phases — one reviewable commit each, in this order

- [x] **A0 — persistence proof.** ✅ **Already in the suite and already green** — the guard did not
      need writing: `FlatFileProfileStoreTest#agilityProgressIsNotMigratedBecauseAChildSkillHasNoSaveKey`
      (`:160`) loads a profile carrying `skills.AGILITY: 63` / `experience.AGILITY: 100.0` and asserts
      the rewritten file contains **no** `AGILITY` at all. Both directions are structural, not
      incidental: load iterates `SkillTools.NON_CHILD_SKILLS` (a child skill is never read), and
      `saveProfile` builds a **fresh** `YamlConfiguration.empty()` (`FlatFileProfileStore.java:287`),
      so an orphan key is dropped on the next save rather than merged forward.
      ⚠️ **This test does not survive phase E untouched** — its line 177 asserts the *derived* Agility
      level via `PrimarySkillType.AGILITY` and stops compiling. When E lands, delete **only** that
      assertion and keep the two orphan-key ones; deleting the test wholesale would remove the only
      proof that a legacy key is still ignored.
- [x] **A — the six sub-skills.** ✅ **shipped `98f6a0bd4`**, suite green at 1803 tests. Add the new `SubSkillType` constants and the `skillranks.yml`
      blocks; repoint `MovementManager#canFleetFoot/canSecondWind` from `Medium#fleetFootedRank()` to
      the medium's own constant. `Medium` gains `fleetFootedSubSkill()` / `secondWindSubSkill()` and
      **loses `fleetFootedRank()`** — delete it rather than leaving it unused, it is the thing that
      encoded the dissolved ladder.
- [x] **B — Second Wind's medium-aware binding** — ✅ shipped `b7bfb4ba5`, suite 1809. + the "all three unlock alike" guard (A-2).
      ⚠️ **Two things the plan did not know, both found by reading the code after A landed:**
      **(B-i) The guard A-2 depends on does not exist.** `SuperAbilityType.java:191` carries the comment
      *"Pinned by `SuperAbilityTypeTest#allSecondWindSubSkillsUnlockAtTheSameLevel`"* and **there is no
      such test class**. Phase A shipped a comment asserting a guard it never wrote — the binding is
      unpinned right now. Write it, and assert the nominal binding's answer *equals every medium's*
      answer rather than re-asserting the literal 250, so the guard fails on divergence however it
      arrives.
      **(B-ii) The ability DURATION still reads `AGILITY`, the mean.**
      `SecondWindListener.java:135` calls `calculateAbilityActivationTicks(PrimarySkillType.AGILITY, …)`,
      so how *long* Second Wind runs is scaled on the mean of the three skills even though A already
      made what *gates* it per-medium. Phase E deletes that constant, so this line must move — and it
      is **a behaviour change, not a rename**: a Swimming-900 / Parkour-0 / Flying-0 specialist scales
      on 300 today and on **900** afterwards. Taken under **A-1** (specialists stop being taxed by the
      mean) rather than re-asked; it is the *fifth* consequence of A-1 and belongs in the wiki sentence
      A-1 already owes. Duration becomes `medium.primarySkill()`.
      🔑 This is the shape the plan warned about — *"a mechanical find-and-replace ships sub-skills that
      unlock at the wrong level and nothing fails"* — except it landed on the **duration**, which no
      gate test reads.
- [x] **C — config + locale + `ConfigRetunes` path moves.** ✅ shipped `6d2a40d84`, suite 1813. ⚠️ ModMenu rows land with the code that
      READS the key, never with the key — `CatalogueKeysReachCodeTest` is right to refuse them early.
- [x] **D — renderers.** ✅ shipped `38644b380`, suite 1814. Delete `AgilityStatsRenderer`; Parkour/Swimming/Flying renderers each gain a
      Fleet Footed row and a Second Wind row showing **that medium's** state.
      ⚠️ **A-4's advancement deletion MOVED OUT of D and into E** — tried in D, and
      `MilestoneAdvancementResourcesTest` correctly refused it. That guard derives the expected
      datapack from `Milestones`, which is driven by `PrimarySkillType.values()`, so while `AGILITY`
      is still a constant its advancement tree is *required* to exist. Deleting the JSONs before the
      enum is not "early", it is **red**. Third instance of the same pressure phase A hit: removing an
      enum constant forces every reader into one commit.
      🔑 Also: `SkillStatsRenderer#calculateLength` resolved the ability via `getSuperAbility(skill)`,
      a **one-to-one** map that answers `null` for two of the three movement skills — an NPE the
      moment a swimmer opened their stats screen, not a misprint. It now takes the ability by name.
- [x] **E — remove `PrimarySkillType.AGILITY`** — ✅ shipped, suite **1813, 0 failures**. — **now also carries A-4** (delete the Agility
      advancement tree: `milestone/skill/agility`, `milestone/level/agility/*`,
      `milestone/maxed/agility`) **and A-5** (the `/mcstats agility` retirement message, which needs
      the command branch that only exists once the constant is gone).
      ⚠️ **Fix `scripts/gen-milestone-advancements.sh` in the SAME commit** — it holds `[agility]`
      rows in its `ICON` and `ROLE` tables and `rm -rf`s its output directory before regenerating, so
      a stale row re-creates the tree on the next run. This trap has already fired once here, from the
      other side, in 2026-07-27.
      Also remove and `SkillTools.AGILITY_PARENTS`, `isChildSkill`'s
      `AGILITY` arm, `MISC_SKILLS`, `getPrimarySkill(SECOND_WIND)`, `coreskills.yml`, `experience.yml`.
      🔑 **Audit against `PrimarySkillType.values()`, never against the diff.**
- [x] **F1 — the LIVE config paths off the retired skill's name.** ✅ shipped `03cacda3d`, suite 1815. Split out of E on 2026-08-18 and
      given its own commit, because it is a **path migration**, not an enum removal, and it reuses
      C's proven `SkillRenames.MovedPath` + `ConfigLoader#migrateOnePath` mechanism.
      E deleted only what was **dead** (`coreskills.yml`'s `Agility:` row, `Experience_Bars.Agility`).
      Still live and still named for a skill `/mcstats agility` now calls retired:
      `Experience_Values.Agility.{Dodge,Roll,Fall,FeatherFall_Multiplier,Movement.*}`,
      `ExploitFix.Agility`, `Skills.Agility.{Level_Cap,XP_After_Teleport_Cooldown,Second_Wind_Item}`.
      ⚠️ Target the neutral **`Movement.*`** root C already established for Second Wind's locale
      strings — not any one parent, which would read as a lie in the other two. Every move needs a
      `MovedPath` entry (a changed shipped default reaches nobody who has run the mod once) and the
      **converse** test A-6 demands: the orphan block must still be there afterwards.
- [x] **F — the rename (A-3), mechanical and behaviour-free.** ✅ shipped `97b0e972f`, suite 1818; git reports all 14 as renames. Its own commit, so a band can take it
      cleanly. ⚠️ Carries **A-9**: rename the `Managed` constants, freeze their id strings.
- [x] **G — gates, ALL GREEN 2026-08-18** (mixin-allow 60 OK/61 → build → mc-surface 1415 records, **no diff** — a rename of our own classes does not move the MC contact surface → config-id self-test PASS + **0 dead-everywhere** → boot-check 1.21.11 exit 0, 0 ERROR, 0 mixin failures → gameplay-smoke **29/29** with the control failing as it must and the scorer self-test 8/8; `combat-sword` passed first try this run, against its stated ~1-in-3 cold flake rate). Original order: `mixin-allow-audit.py --check` **before** `./gradlew build` (javac
      is blind to mixin selector breakage); then `classes testClasses` → `extract-mc-surface.py`;
      `config-id-audit.py --self-test` then the audit; `boot-check.sh`; `gameplay-smoke.sh` with
      `GAMEPLAY_SMOKE_CONTROL=1` proving the control still fails.
      ⚠️ `gameplay-smoke.sh` scores a **super ability** — Second Wind's re-home is exactly the kind of
      change that breaks it, and its `combat-sword` leg is flaky 1-in-3 cold. State the base rate.
- [x] **H — mutation pass, 6 mutations, 2026-08-18.** ✅ **5 of 6 predicted exactly; the 6th over-caught.** M1 (ramp reaches for the nominal PARKOUR key) reddened both A-8 guards and — as predicted — left `SkillStatsRendererTest#eachParentScreenShowsItsOwnMediumsFleetFootedNumber` GREEN, because its all-parents-at-1000 fixture cannot discriminate: that predicted green is the evidence A-8 needed its own guard. M2 (a live token in RETIRED_SKILLS), M3 (unfreezing a modifier id), M4 (dropping the nested-block MovedPath) and M5 ("tidying away" a key A-6 keeps) each reddened exactly their predicted set. M6 (un-suppressing a child bar) reddened one test MORE than predicted — `childSkillBarsAreSuppressed`, which reaches `disabledBars` by updating the CHILD directly rather than through the parent-propagation loop. Prediction error, not a defect: 13th sighting. Every mutation was proved to change bytes before its verdict was believed. Original note: 🔑🔑 The signal is *"the red I got is the red I
      predicted"*, not *"all caught"*. **Predict what each mutation should break before running it**,
      and confirm the mutation actually changed compiled behaviour before believing a red test.

### Blast radius and rollback

Every phase is one commit on `master` with a green build behind it, so **rollback is
`git revert <sha>` of that commit** — no history rewrite, nothing irreversible. The only deletions
are tracked files (advancement JSONs, `AgilityStatsRenderer`, the `Agility:` config blocks), all
recoverable from the commit that removed them. **Nothing here touches a player's save**: the profile
change is *not writing a key that was already dead*.
⚠️ The one unrecoverable-shaped step is **F, the rename** — `git mv` across ~30 files. It goes last,
alone, on a clean tree, and is verified with `git status` showing renames rather than delete+add.

**Destructive steps actually taken, with their verified rollback (E, 2026-08-18):**

| Step | Blast radius | Rollback (verified available) |
|---|---|---|
| A-4 — deleted the Agility advancement tree | 7 tracked JSONs: `milestone/level/agility/{apprentice,adept,expert,master,grandmaster}.json`, `milestone/maxed/agility.json`, `milestone/skill/agility.json`. Nothing untracked, nothing outside `advancement/milestone`. | `git checkout 38644b380 -- src/main/resources/data/mcmmo/advancement/milestone/level/agility src/main/resources/data/mcmmo/advancement/milestone/maxed/agility.json src/main/resources/data/mcmmo/advancement/milestone/skill/agility.json` |

All five gates were run before the `git rm`: target resolved by `find` (7 files, listed), every file
proved tracked at `38644b380` by `git ls-files --error-unmatch`, `git rm -n` dry-run printed exactly
those 7 and nothing else, scope narrowed to three named paths rather than a glob, and the undo above
was written before the command ran. **No player save is touched anywhere in this phase** — the
profile change is *not writing a key that was already dead*.

### What I am **not** doing

- **Not preserving the all-rounder gate in any form.** A-1 ends it deliberately. Being an all-rounder
  still earns *more* perks (all six instead of two); it is no longer a *gate* on any one of them.
- **Not re-scaling the Second Wind bodies.** Dart/Aquaman/Limitless keep their current magnitudes;
  only what gates them moves.
- **Not touching Roll, Dodge, Athlete, Smash, Snow Walker, Lead Lungs, Lake Raider, Glide or Solar
  Wings.** They were re-parented on 2026-08-10 and are already correct.
- **Not adding a `ParkourManager`/`SwimmingManager`/`FlyingManager` split.** One movement manager,
  as today — three managers would mean three lazy-construction sites for one behaviour family.
- **Not migrating the orphan `skills.AGILITY` key.** Ruled above; there is nothing to migrate to.

### 🔴 THE DOC PASS IS NOW THE TOP OF THE REMAINING WORK (audited 2026-08-18, NOT done)

`/mcstats agility` now tells a player the skill was retired. **README.md and 13 wiki pages still
document it as live** — 64 mentions. Three classes, and only the first is merely untidy:

1. **Actively wrong and player-actionable.** `README.md:194` names `Skills.Agility.Second_Wind_Item`
   as the config key to edit. That path moved in F1; a player following the README edits a dead key
   and nothing happens. Same shape as the GitHub #7 defect.
2. **Actively wrong about mechanics.** `README.md:139` and `wiki/Skills.md:31,35` describe Agility as
   a live child skill whose perks carry *"one rank per medium"* gated on *"the three-skill mean"*, and
   `README.md:191` still says *"Rank 1 unlocks land, 2 water, 3 air"*. A-1 deleted that ladder — Fleet
   Footed is Parkour/Swimming/Flying 1 and Second Wind 250 in each, separately.
3. **The A-1 sentence owed since phase A and still written nowhere.** Say in plain words that this is
   a **buff for specialists** and the deliberate end of the all-rounder gate: a pure flier capped
   Agility at 333 and could never reach the air ranks; they now get air Fleet Footed at Flying 1 and
   Limitless at Flying 250. It is owed **five** consequences now — the flattened unlocks (A-1),
   Second Wind's duration following the medium (B-ii), and the four ramps that stopped reading the
   mean (phase C).

⚠️ **Audit the roster against `PrimarySkillType.values()`, never against the diff.** A *removed*
skill is invisible to every incremental doc edit, exactly as an *added* one was for Cooking.
⚠️ **One wiki serves every band**, so say what a player does, never what version the build targets.

⚠️ **`wiki/Configuration.md` is down to 4 hits, not 10.** The Skills-tab work (2026-08-18) deleted
the *"Disabling one of Agility's parents"* subsection — it sat directly under the section that work
rewrote and described a skill that no longer exists — and replaced it with the Salvage/Smelting child
note that section actually needed. The rest of that file is still owed.

Files with hits: `README.md` (12), `wiki/Movement-Skills.md` (16), `wiki/Configuration.md` (~4, was 10),
`wiki/Skills.md` (7), `wiki/Troubleshooting.md` (4), `wiki/XP-and-Levelling.md` (4),
`wiki/Super-Abilities.md` (3), `wiki/Differences-from-mcMMO.md` (2), `wiki/Stealth.md` (2), and one
each in `Commands.md`, `Home.md`, `Hunter.md`, `Husbandry.md`, `Optional-Integrations.md`.

### The rulings taken before this pass started (owner, 2026-08-18)

| # | Question | Ruling |
|---|---|---|
| D-1 | How much of the Acrobatics → Agility → retired history survives | **One migration section, present tense everywhere else.** Every page describes today only: three movement skills, per-medium Fleet Footed and Second Wind. The chain survives in exactly one place — `wiki/Troubleshooting.md`'s "my Agility is gone" entry — cross-linked from `wiki/Configuration.md`'s migration section. Rationale: the word "Agility" on ten pages is ten future caveat-expiry liabilities, and an upgrading player only needs to find the answer once. |
| D-2 | Does the doc work ride the A-7 back-port | **README rides; the wiki does not.** `README.md` is per-branch, so after the code back-port every band's README describes a retired skill as live — it gets its own commit and a `Backport-of:` trailer on all five bands. `wiki/` is one publication serving every band; five identical copies is the instrument AGENTS.md already rejects. |
| D-3 | `fix.md` (untracked Taming plan, repo root) | **Delete**, once `TODO.md` and `.agent/memory/` are confirmed to carry its rulings. Guarded per rule zero: resolve, prove recoverable, confirm. |
| D-4 | The `Acrobatics` → `Agility` rename warning | **Point it at the real destinations, and give the table its first test.** Found while verifying ground truth for this pass, not by the suite. |

### D-a — the code defect this pass turned up (do this FIRST; the docs describe it)

`SkillRenames.LEGACY_CONFIG_SECTIONS` still maps `Acrobatics` → `Agility`, and
`ConfigLoader#warnOnRenamedSections` interpolates that value into *"move them to 'Agility'"*.

**Not data loss, and say so rather than overstating it.** `migrateMovedPaths` runs on
`Skills.Agility.*` and fires on the boot *after* the player renames, so the two-step genuinely
lands. What is wrong is that the message names an intermediate that is no longer a skill — it asks
a player to create a section the very next boot deletes.

- [ ] The destination becomes the live one: `Skills.Parkour` / `Skills.Swimming` / `Skills.Flying`
      for sub-skill tuning, `Skills.Movement` for `Second_Wind_Item` and
      `XP_After_Teleport_Cooldown`. The value is interpolated into a fixed format string, so this
      needs the message reworded, not just the map entry swapped.
- [ ] **The guard that table has never had:** every destination named by `legacyConfigSections()`
      and `legacyConfigPaths()` must resolve to something the code still reads — no destination may
      name a `PrimarySkillType` that is absent from `values()`. This is the exact defect shape the
      repo keeps re-finding (a pointer aimed at something dead), and nothing in the suite looks for
      it. Must go red when `Agility` is put back as a destination.
- [ ] 🔑 Ordering matters: `README.md:369` quotes this warning verbatim, so the code fact has to be
      settled before D-b writes the sentence about it.

### D-b — `README.md` (12 hits, one of them player-actionable)

- [ ] `:194` `Skills.Agility.Second_Wind_Item` → **`Skills.Movement.Second_Wind_Item`**. Verified
      live at `GeneralConfig.java:422`. This is the GitHub #7 shape — a config path a player is
      told to edit that reaches nothing — and it is the single highest-value line in the pass.
- [ ] `:122` child-skills row: drop Agility, keep Salvage and Smelting.
- [ ] `:130` + `:136-139` the "New in this port" table: the three movement skills stop *"feeding
      Agility"* and own their own Fleet Footed and Second Wind; the `Agility (child)` row goes.
      ⚠️ `:130` says *"Eight primary skills"* — re-count against `PrimarySkillType.values()`, do
      not adjust the number by arithmetic on the diff.
- [ ] `:191` Second Wind row: *"Rank 1 unlocks land, 2 water, 3 air"* is dead. It is now **one rank
      per medium, each gated on that medium's own skill** — Fleet Footed at level 1, Second Wind at
      250 (RetroMode, which is the shipped default; the Standard numbers are 1 and 25).
- [ ] `:360` play-test caveat: *"the restructured Agility"* → the movement skills.
- [ ] `:365-370` the Acrobatics carry-over note: rewrite against whatever D-a lands, and add the
      second half that is now true — an `Agility:` section is likewise no longer read.
- [ ] **The A-1 sentence, owed since phase A and still written nowhere.** In plain words: this is a
      **buff for specialists** and the deliberate end of the all-rounder gate. A pure flier capped
      Agility at 333 and could never reach the air ranks at all; they now get air Fleet Footed at
      Flying 1 and Limitless at Flying 250. It owes **five** consequences — the flattened unlocks
      (A-1), Second Wind's duration following the medium (B-ii), and the four ramps that stopped
      reading the mean (phase C).

### D-c — `wiki/Movement-Skills.md` (15 hits; this is a rewrite, not an edit)

Every structural element of the page is about the retired skill: the title, the tree diagram at
`:14`, the "Agility's sub-skills" section at `:85`, and **every row of the tuning table at
`:145-155`**. Verified current paths, all four moves confirmed in `SkillRenames`:

| Was | Is |
|---|---|
| `Experience_Values.Agility.Movement` | `Experience_Values.Movement.Travel` |
| `Experience_Values.Agility.{Dodge,Roll,Fall,FeatherFall_Multiplier}` | `Experience_Values.Movement.*` |
| `Skills.Agility.{FleetFooted,SecondWind}` (advanced.yml) | `Skills.{Parkour,Swimming,Flying}.{FleetFooted,SecondWind}` |
| `Agility.*` (skillranks.yml) | `{Parkour,Swimming,Flying}.{FleetFooted,SecondWind}` |
| `Skills.Agility.Second_Wind_Item` / `.XP_After_Teleport_Cooldown` | `Skills.Movement.*` |
| `ExploitFix.Agility` | `ExploitFix.Movement` |
| `Skills.Agility.{Level_Cap,Enabled_For_PVP,Enabled_For_PVE}` | **gone** — per-skill keys with no skill |

- [ ] ⚠️ `:155` is not a path fix but a **mechanics** fix: *"Capping `Skills.Agility.Level_Cap` caps
      what Fleet Footed and Second Wind can reach"* describes a cap that no longer exists. Each of
      the six sub-skills now answers to its own parent's `Level_Cap`.

### D-d — the other 12 wiki pages

Two are dead config keys and rank as actively wrong, not untidy — the exploit-fix switch was
**renamed**, so a player editing the documented key changes nothing:

- [ ] `wiki/XP-and-Levelling.md:108` and `wiki/Configuration.md:217` — `Agility` → **`Movement`**
      (confirmed at `experience.yml:57`).
- [ ] `wiki/Super-Abilities.md:69` — `Skills.Agility.Second_Wind_Item` → `Skills.Movement.*`, and
      the owning-skill column stops naming Agility.
- [ ] `wiki/Troubleshooting.md:113` — `Experience_Values.Agility.Movement.Reference_Speed` →
      `Experience_Values.Movement.Travel.Reference_Speed`.
- [ ] `wiki/Troubleshooting.md:32` — **this is the one page D-1 keeps.** Heading becomes
      *"Acrobatics is gone / Agility is gone"* and it carries the whole chain in one place.
- [ ] `wiki/Configuration.md:266,272` — the migration section; cross-link Troubleshooting per D-1.
- [ ] `wiki/Skills.md` (7), `wiki/XP-and-Levelling.md:152,158,196`, `wiki/Stealth.md:77,89`,
      `wiki/Differences-from-mcMMO.md:13,23`, `wiki/Super-Abilities.md:77,168`, and one line each in
      `Commands.md:49`, `Home.md:38`, `Hunter.md:133`, `Husbandry.md:7` — present tense per D-1.
      ⚠️ `Skills.md:35,276,298` and `Differences-from-mcMMO.md:23` are where the *reasoning* about
      the mean lives; those want rewriting to the A-1 argument, not deleting.

### D-e — the shipped resources, which the audit never grepped

The 2026-08-18 caveat-expiry pass was run over `README.md` + `wiki/` only. The symptom leaked into
files players read and edit directly. ⚠️ **Do not touch the migration notes** (*"moved here from
`Skills.Agility` on 2026-08-10"*) — those are correct history and are exactly what tells a player
where their old tuning went. Only these four are stale:

- [ ] `config.yml:246` and `:282` — *"Must differ from **Agility's** `Second_Wind_Item`"*. Attributes
      a live key to a retired skill and implies the dead path.
- [ ] `advanced.yml:951` — *"Cannot stack with **Agility's** Smash"*. Smash has been a **Parkour**
      sub-skill since 2026-08-10; this one was already stale before the retirement. Same sentence is
      duplicated at `wiki/Stealth.md:77`.
- [ ] `experience.yml:1056` — a cross-reference to *"the comment under
      `Experience_Values.Agility.Movement`"*, a path that no longer exists. Broken pointer.
- [ ] `experience.yml:237` — *"PINK is Repair/Salvage/Smelting … **Agility**/Stealth"*, a colour
      allocation naming a bar that was retired with the skill.

### D-f — the roster audit, against `values()` and never against the diff

- [ ] 26 constants in `PrimarySkillType`; `AGILITY` is confirmed absent. Audit the wiki roster
      against that list, not against this diff — **a removed skill is invisible to every
      incremental doc edit**, exactly as an added one was for Cooking (six commits, zero mentions in
      16 wiki files).
- [ ] ⚠️ One wiki serves every band: say what a player does, never what version this build targets.

### D-g — gates and the commit split

- [ ] Staged commits, and the split is load-bearing for D-2: **D-a its own commit** (code + test),
      **README its own commit** (it back-ports), **wiki its own commit** (it does not), **resources
      its own commit** (they back-port).
- [ ] `./gradlew build` — D-a touches code, so the full suite runs and the new guard must be shown
      red before green.
- [ ] `python scripts/config-id-audit.py --self-test` then the audit — D-e edits shipped YAML.
- [ ] `mixin-allow-audit.py --check` and `extract-mc-surface.py` are **not** expected to move; run
      them anyway and say so, rather than asserting it.

### D-h — `fix.md`, per D-3 and rule zero

- [ ] Resolve the target (`git status` shows exactly one untracked path), prove it is recoverable
      (its rulings R-1..R-6 are in `TODO.md`'s Taming block and `.agent/memory/`; the file is
      untracked, so **git cannot bring it back** — copy it to the scratchpad first), quote the exact
      command, then delete. Narrow scope: the one named file, never a glob.

### Then, and this is not optional

- [ ] **Back-port to all five `mc/**` branches** with `Backport-of:` trailers. Version-agnostic logic,
      same class as the Taming work — and this one is bigger, so it is more likely to be forgotten,
      not less. ⚠️ `mc-surface.txt` is regenerated per band, never cherry-picked.
      🔑 Take **F (the rename) as its own cherry-pick**, after the behaviour commits, or every one of
      them conflicts.
- [ ] **Caveat-expiry pass** on `README.md` and `wiki/` — grep the **symptom** (*"Agility"*,
      *"Fleet Footed"*, *"Second Wind"*, *"child skill"*, *"all-rounder"*, *"mean of"*), and audit the
      wiki roster against `PrimarySkillType.values()`. A removed skill is invisible to every
      incremental doc edit, exactly as an added one was for Cooking. ⚠️ One wiki serves every band.
- [ ] Full ship gate per band before each push.

---

## The Skills tab — the per-skill master switches reach the config screen (owner-requested 2026-08-18)

**Tier 1.** Six files, no new mechanic, nothing destructive. The whole subsystem behind this already
ships: `coreskills.yml` carries a `<Skill>.Enabled` row for every constant, and `SkillGating`
enforces all six meanings of "disabled" (no XP, no procs, no super, no XP bar, no `/mcstats`, no
plaques). **The only thing missing is the control.** Today a player who wants Fishing off must find
and hand-edit a YAML file — which is precisely the gap the ModMenu editor exists to close, and the
one tab it never got.

### Rulings taken (owner, 2026-08-18)

| # | Question | Ruling |
|---|---|---|
| S-1 | When a toggle applies | **Next world load only.** Identical to the contract every other tab already documents (`ConfigDocument` writes the yml; `ConfigBootstrap` reloads on world load). No live-reload path, no server-thread hop. ⚠️ Consequence: a player toggling from the pause menu sees *nothing change*, so the tab description and every row's tooltip must say so — an unexplained no-op reads as a bug. |
| S-2 | A command as a second path | **No. The ModMenu tab only.** Accepted and stated: Cloth and ModMenu are optional, so a player with neither still hand-edits `coreskills.yml`. `wiki/Optional-Integrations.md` already documents that fallback for every other tab. |
| S-3 | A skill this MC version cannot furnish | **Show the row, locked, and say why.** `SkillAvailability` force-disables it whatever the config says, so an editable toggle would be a control that cannot do anything — the exact "lying switch" shape `CatalogueKeysReachCodeTest` exists to catch. ⚠️ A locked row must also **not write**: silently rewriting a value the player never touched is worse than the missing control. |

⚠️ **S-3's known limit, stated rather than papered over.** `SkillAvailability.probe()` runs from
`onServerStarting`, so a screen opened from the **title screen** (no world ever loaded this session)
sees every skill as supported and renders every row editable. That is the same one-answer-per-process
design that exists to stop the probe being decided by test scheduling, and weakening it for a
cosmetic lock would be the worse trade. The lock is a courtesy in-game; `/mcstats <skill>` remains
the authority, and it already names the Minecraft version as the reason.

### The two traps this has to be built around, both already stepped on in this repo

1. ⚠️⚠️ **The roster must come from `PrimarySkillType.values()`, never a transcribed array.**
   `McMMOSettings` holds three hand-kept rosters (`XP_MULTIPLIER_SKILLS`, `LEVEL_CAP_SKILLS`,
   `COOLDOWN_ABILITIES`) and **two of the three have already shipped wrong** — Cooking reached
   neither array, and `Herdsmans_Call` shipped a working ability with a live config key and no
   slider. Each was found by an audit, not by the suite. A fourth transcription would fail the same
   way, silently, the next time a skill lands. Generate the rows.
2. ⚠️⚠️ **`CatalogueKeysReachCodeTest` will call every one of these rows DEAD, and it is not wrong
   to.** `CoreSkillsConfig#loadKeys` reads the key as `config.getBoolean(enabledPath(skill), true)` —
   there is no string literal anywhere for `CONFIG_READ` to match, and `readersOf`'s concat-prefix
   fallback needs a literal prefix that also does not exist. This is the ungreppable-dynamic-key
   shape (`XPBar.<Skill>` again). **Do not widen the regex** — an exemption plus a *stronger*
   replacement guard, or the exemption is just a hole.

### Phases

- [x] **S-a — the catalogue rows.** ✅ shipped `McMMOSettings`: add `CORESKILLS_YML`, a `CAT_SKILLS` tab, and
      rows built by looping `PrimarySkillType.values()` with the path from
      `CoreSkillsConfig.enabledPath(skill)` — the same method `/mcstats` already quotes at the
      player, so the screen and the message can never disagree. Label = capitalized constant (it is
      the yml key the tooltip tells them to look for). Every row's tooltip states: disabling is
      total, the level is kept, and **it applies on the next world load**.
      ⚠️ `SALVAGE` and `SMELTING` are child skills and DO get rows — unlike the XP-multiplier tab,
      where a child's slider would be dead, `SkillGating` genuinely gates them. Their tooltip says
      which parent they belong to.
      ⚠️ `PrimarySkillType` is Minecraft-free, so the catalogue stays unit-testable with no game
      bootstrap. Keep it that way: `SkillAvailability` is NOT imported here.
- [x] **S-b — the screen.** ✅ shipped `ClothConfigScreenBuilder`: for a coreskills row, ask
      `SkillAvailability.isSkillSupported`; when false, append the reason to the tooltip, give the
      entry a **no-op save consumer**, and call `setEditable(false)` on the built entry.
      ✅ `setEditable(boolean)` verified present on `AbstractConfigListEntry` in **all six** Cloth
      versions the bands pin — 16.0.143, 17.0.144, 18.0.145, 19.0.147, 20.0.149 and master's
      21.11.153 (`javap` over the Loom-cached jars, 2026-08-18). The back-port cannot break on it.
      ⚠️ The lock DECISION is a pure function on the Minecraft-free side so it can be tested; only
      the one line that hands it `SkillAvailability::isSkillSupported` is untested, and that is
      stated rather than hidden.
- [x] **S-c — the guards.** ✅ shipped — suite **1825**, 0 failures (was 1818) New `CoreSkillsCatalogueTest`, plus edits to the two existing ones:
      - `McMMOSettingsTest#everyKeyExistsInBundledDefaultsWithMatchingType` — add the
        `coreskills.yml` case, or it hits its own `fail("unknown config file")`.
      - **Both directions of the roster**: every `values()` constant has exactly one row, AND every
        `*.Enabled` root section in the shipped `coreskills.yml` maps to a live constant. The second
        half is what would catch a resurrected `Agility:` row — and it is the direction the three
        hand-kept arrays have never had.
      - **The behavioural one, and the only one that proves the tab does anything**: write `false`
        through a real `ConfigSession` into a temp dir, `saveAll()`, then construct a
        `CoreSkillsConfig` over that directory and assert `isPrimarySkillEnabled` is now false —
        **with a positive co-assertion that a second, untouched skill is still true**, so a
        blanket-false bug cannot pass as a success.
      - **The lock**: fed a predicate that calls one skill unsupported, that row locks and the others
        do not, and the locked row writes nothing.
      - `CatalogueKeysReachCodeTest` — skip `coreskills.yml` rows with the reason written in, and
        replace what was lost: assert the exemption set is **exactly** the coreskills rows (so it
        cannot quietly grow), and that `isPrimarySkillEnabled` is named by production code outside
        the config package. If `SkillGating` ever stops calling it, that reddens.
- [x] **S-d — docs, and the caveat-expiry pass.** ✅ shipped `wiki/Optional-Integrations.md` grows a **Skills**
      row in the tab table; `wiki/Configuration.md` and `README.md` stop implying hand-editing is the
      only way in. ⚠️ Two lines in that same tab table are already stale — it credits General with
      "level-up broadcasts" (a switch culled for having no getter) and Experience with "the Agility
      … per-second rates" (retired 2026-08-17). Fix both while inside that table; the wider Agility
      doc pass stays where it is scoped.
      ⚠️ One wiki serves every band, so the Skills row says what the tab does, never what MC version
      this build targets.
- [ ] **S-e — back-port.** Rides with the A-7 combined back-port, not separately. Version-agnostic
      logic, five branches, `Backport-of:` trailers.

### Mutation pass — 5 mutations, 4 predicted exactly, 1 over-caught

| # | Mutation | Predicted | Actual |
|---|---|---|---|
| M1 | drop `MINING` from the generated row loop | 3 red | **4 red** — `childSkillRowsExplainWhereTheirLevelComesFrom` also fell, because its `rowFor(MINING)` helper throws when the row is absent. Explained, not a defect. |
| M2 | invert the lock in `booleanSaveConsumer` | `aLockedRowDoesNotWriteAnything` + `togglingARowOff…` | exactly that |
| M3 | drop the negation in `isLockedByVersion` | `onlyAnUnfurnishableSkillRowIsLocked` | exactly that |
| M4 | delete "next world load" from the tooltip | `everySkillHasExactlyOneMasterSwitchRow` | exactly that |
| M5 | widen the exemption to `config.yml` | `theDynamicKeyExemptionCoversOnlyCoreskills` | exactly that |

⚠️⚠️ **The first parse of the mutation results was wrong and looked plausible.** A regex reading
`<testcase name=…>` up to the next `<failure>` mis-attributed every failure, because a *passing*
testcase is written self-closing (`<testcase …/>`) and has no closing tag for the lookahead to stop
at — so a failure was credited to whichever earlier test happened to precede it. The counts were
right and only the names were wrong, which is exactly why it survived a first reading. **Parse the
JUnit XML with an XML parser** (`xml.etree`), never a regex. 10th-sighting shape: the tell was M3 and
M5 reporting red tests that could not possibly relate to the mutation.

### Verified, but only in the way this task allows

- ✅ Suite **1825**, 0 failures. ✅ `extract-mc-surface.py --self-test` PASS, `--check` **1415 records,
  manifest byte-identical** — renaming nothing and adding no MC call, as expected for a client screen
  built from Cloth widgets that were already in use.
- 🔴 **Never watched in game.** Nothing here has been seen rendered: not the tab, not a locked row,
  not the greyed-out state. The Cloth API call is `setEditable(false)` on the built entry, verified
  present in all six pinned Cloth versions by `javap`, but "the method exists" is not "the row looks
  disabled". Worth a look on the next live play-test, alongside the Taming items.
- ⚠️ **One line is deliberately untested**, and it is the one that binds the lock to reality:
  `McMMOSettings.isLockedByVersion(setting, SkillAvailability::isSkillSupported)` in
  `ClothConfigScreenBuilder`. Everything either side of it has a test; that composition needs a
  screen, and the screen needs Minecraft.

### What this is NOT doing

- **No live reload** (S-1). The screen does not touch a running world's `CoreSkillsConfig`.
- **No command** (S-2), and no per-sub-skill switch — `CoreSkillsConfig` documents at length why the
  finer surface was deliberately not revived for GitHub #10.
- **No change to `SkillGating`, `coreskills.yml`, or any enforcement.** This is a control over a
  mechanism that already works; if anything about the mechanism needs changing, that is a different
  task and gets its own plan.

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

## Phase 10 — jar naming and release versioning (owner-requested 2026-08-13)

**The ask:** the uploaded jar should name the Minecraft versions it supports, and the mod version
should mean something. Target host is **CurseForge**, which attaches **one file to many game
versions** — so this is a *labelling* change, not a build-matrix change. Ruled with the owner:
one jar per band, range-named, `+mc<lo>-<hi>`, and the run-number suffix **dropped**.

### 10.0 — the three defects, named separately

They are not one problem and they do not have one fix:

1. **The jar filename carries no Minecraft version at all.** `mcmmo-2.2.050-build.28.jar`. The MC
   version exists only in the git tag and inside `fabric.mod.json`. This is the manual-rename tax.
2. **`build.<N>` is `GITHUB_RUN_NUMBER`, which is repo-global, not per-branch.** The five branches
   sit at `build.28` / `.27` / `.24` / `.25` / `.26` for **identical mod code**. Five numbers that
   read like five versions and are not comparable in any direction. This is the "no coherent
   versioning" complaint, and it is the load-bearing one.
3. **`2.2.050` is not the version the mod reports.** Fabric's semver parser normalises the padded
   patch — `Version.parse("2.2.050").getFriendlyString()` → **`2.2.50`** (measured against
   `fabric-loader-0.19.3`, 2026-08-13). So the filename would say `2.2.050+mc1.21.4` while ModMenu
   says `2.2.50+mc1.21.4`. Already true today (`2.2.050-build.28` displays as `2.2.50-build.28`).
   ⚠️ **Left as-is pending an owner ruling** — `2.2.050` mirrors upstream mcMMO's padded scheme, and
   silently re-identifying the mod is not in scope for a naming task. See 10.6.

### 10.1 — the scheme

| Band | `supported_minecraft_versions` | Jar |
|---|---|---|
| `master` | `1.21.11` | `mcmmo-2.2.050+mc1.21.11.jar` |
| `mc/1.21.10` | `1.21.9,1.21.10` | `mcmmo-2.2.050+mc1.21.9-1.21.10.jar` |
| `mc/1.21.8` | `1.21.6,1.21.7,1.21.8` | `mcmmo-2.2.050+mc1.21.6-1.21.8.jar` |
| `mc/1.21.5` | `1.21.5` | `mcmmo-2.2.050+mc1.21.5.jar` |
| `mc/1.21.4` | `1.21.4` | `mcmmo-2.2.050+mc1.21.4.jar` |

Single-version band → bare `+mc<ver>`. Multi-version band → `+mc<lo>-<hi>`. Local dev builds keep
`-SNAPSHOT`, which lands *before* the `+`: `2.2.050-SNAPSHOT+mc1.21.4` (correct semver ordering —
pre-release, then build metadata).

✅ **All five strings verified loader-parseable** against `fabric-loader-0.19.3` before this plan was
written, including the range and `-SNAPSHOT` forms. Not assumed — run and read.

### 10.2 — `master` first (gradle side)

- [x] **10.2a** Add `supported_minecraft_versions=<csv>` to `gradle.properties`, next to
      `minecraft_version`, with a comment saying it is the **band's** coverage and that
      `fabric.mod.json`'s `depends.minecraft` is the other half of the same fact.
- [x] **10.2b** `build.gradle`: derive the `+mc…` label from that property and append it to
      `version`. ⚠️ Resolve at **configuration** time — `processResources` already carries the
      configuration-cache scar (dereferencing `project` in an execution-time closure is rejected);
      the same rule binds here.
- [x] **10.2c** New guard `src/test/java/com/gmail/nossr50/guards/BandVersionLabelTest.java`,
      following `MixinAllowCoverageTest`'s shape (heavy *why* javadoc + explicit converse checks):
      - every version in the property **satisfies** `depends.minecraft`, using Fabric's own
        `VersionPredicate` engine — not a regex over the range string;
      - the list is sorted, contiguous-as-declared, and its endpoints match the predicate's bounds,
        so a range **wider** than the list is caught too (the direction a one-way check misses);
      - the computed label round-trips through `Version.parse` — the check run by hand for 10.1,
        made permanent so a future band cannot ship an unparseable version;
      - **converse:** a deliberately-wrong list must make the detector fire. A guard that has never
        failed is not known to work.
      ⚠️ Must **not** live in `com.gmail.nossr50.fabric.mixin` — that package is claimed by the Mixin
      transformer under Knot and a test there fails to *load*, not to assert.
- [x] **10.2d** README: the band table gains the jar name per band. Caveat-expiry pass — grep the
      **symptom** (`build.`, `mcmmo-2.2.050`), not the files edited.
      ⚠️ **The first pass missed ten spots and they were caught a session later.** Grepping
      `build.`/`mcmmo-2.2.050` finds where the *new* scheme is described; it does not find where the
      *old* one still is. `README.md` and `wiki/Building-from-Source.md` both still documented the
      tag as `mc<ver>-v<mod ver>-build.<n>` — the exact suffix 10.4a had just deleted. Grep for what
      the change **removed**, not only for what it added. The same pass also had to fix six pages
      that named `1.21.5` as the oldest band, and three that **enumerated bands by name** for a
      version-gated feature — rewritten to state the Minecraft version the feature needs, because an
      enumeration goes stale on every single cut and that is precisely why these had.

### 10.3 — the four bands (gradle side)

Cherry-pick 10.2 onto `mc/1.21.10`, `mc/1.21.8`, `mc/1.21.5`, `mc/1.21.4`, each with a
`Backport-of: <master sha>` trailer, each with its **own** `supported_minecraft_versions` — the value
is per-band and must not be copied. Same trap as the generated MC-surface manifest: the *generator*
back-ports, the *value* is re-derived per band.

### 10.4 — 🔴 `release.yml` could not land on `master` first *(true when done; **fixed** by R-r)*

**At the time:** `master` had no `.github/` at all (ruling R-g), so `release.yml` existed **only** on
the four band branches, and the CI half of this work structurally could not obey "fixes land on
`master` first" — there was no file on `master` to fix. That was an R-g consequence, not a shortcut.

✅ **No longer true.** **R-r** (§10.8) restored the file on `master`, so the next `release.yml` change
lands there first like everything else, and 10.4b's "four hand-edits with nothing checking they
agree" stops being the only option. The four band edits below stay as the historical record of how
this one was done.

- [x] **10.4a** On each of the four bands, edit `.github/workflows/release.yml`: drop
      `-build.${GITHUB_RUN_NUMBER}` from the computed version, and fix the hard-coded asset paths in
      the *Publish release* step (`build/libs/mcmmo-${…}.jar`) — **those paths break the moment the
      jar is renamed**, and the step `exit 1`s on a missing asset, so this is the one change that
      fails loudly rather than silently.

      ✅ **RULED: the tag keeps its `mc<VER>-v` prefix** and only loses the `-build.<N>` suffix —
      `mc1.21.4-v2.2.050`, not `v2.2.050+mc1.21.4`. Tempting to make the tag match the jar name, but
      the prefix is load-bearing twice over and matching the filename buys nothing a git ref needs:
        - the *"delete previous release on this Minecraft line"* sweep matches `mc<VER>-v*`. Keeping
          the prefix means it still matches the four existing `mc1.21.*-v2.2.050-build.*` releases, so
          they are reaped automatically on the next release. **This retires 10.6.2 entirely** — a new
          tag shape would have orphaned them and left manual cleanup;
        - **R10** (no two branches on one `minecraft_version`) is detected through that same prefix.
      So the jar name and the tag deliberately differ. The jar is what a player reads; the tag is what
      the release automation keys on.
- [x] **10.4b** State the exception in each commit body (no `master` parent exists, and why).
      ⚠️ `drift-audit.py` walks **`master`** commits looking for `Backport-of:` trailers, so a
      band-only commit is **invisible** to it — no false alarm, and no protection either. Four
      hand-edits with nothing checking they agree.
- [x] **10.4c** Decide what replaces per-push uniqueness. Dropping the run number means two pushes
      at the same `mod_version` produce the **same** tag. The existing *"delete previous release on
      this Minecraft line"* sweep already keeps exactly one release per line, and `git tag -f`
      already force-moves the tag, so replacement is the current behaviour — but the commit sha in
      the release notes becomes the **only** way to tell two builds of `2.2.050` apart.

### 10.4′ — 🔴 ORDERING: on a band, 10.3 and 10.4 are ONE commit

Landing the gradle rename on a band **without** the `release.yml` fix breaks that band's next
release. `release.yml` builds with `-Pmod_version=…-build.${RUN}` and then looks for the literal path
`build/libs/mcmmo-${RELEASE_VERSION}.jar`. After the rename the jar is
`mcmmo-…-build.${RUN}+mc1.21.4.jar`, the path misses, and the *Publish release* step `exit 1`s —
which trips the cleanup step and deletes the tag it had already pushed.

That failure is **loud and self-cleaning**, not silent, and it is the reason 10.4a is worth doing in
the same commit rather than trusting a follow-up. `master` was exempt at the time — it had no
workflow, so 10.2 landed there alone. ⚠️ **That exemption is gone under R-r:** `master` now carries
`release.yml` too, so this ordering rule applies to all five branches from here on.

### 10.5 — blast radius and rollback

| Step | Touches | Lost if wrong | Comes back from |
|---|---|---|---|
| 10.2 | `gradle.properties`, `build.gradle`, new test, `README.md` | nothing — jar name only | `git revert`; `master` is pushed and clean at `ec9b497f7` |
| 10.3 | same four files × 4 bands | nothing | per-band `git revert` |
| 10.4 | `release.yml` × 4 bands | **a release run could tag then fail to find its asset** | revert the file; the workflow already deletes its own tag on build/publish failure |

⚠️ **10.4 is the only step that can touch a published artifact.** The `--cleanup-tag` sweep deletes
releases by `mc<VER>-v*` prefix. ✅ **Resolved by keeping that prefix** (see 10.4a): the old
`mc1.21.*-v2.2.050-build.*` releases stay matched and get reaped on the next release, so there is no
orphaned-release cleanup. Had the tag been reshaped to `v<version>+mc<label>`, the sweep would have
stopped matching them silently — the release would have looked fine and the stale one would have
stayed up.

### 10.6 — open questions for the owner

1. ~~**`2.2.050` vs `2.2.50`**~~ ✅ **CLOSED by R-p (owner, 2026-08-13): keep `2.2.050`.** The
   filename/ModMenu disagreement is accepted and permanent, and it is pre-existing rather than
   introduced here. Defect 3 in §10.0 stands as documented behaviour, not as an open item.
2. ~~**Old releases.**~~ ✅ **Retired by the 10.4a tag ruling** — the prefix is unchanged, so the
   existing `…-build.*` releases are reaped automatically. No manual cleanup.
3. ~~**`master` publishes nothing.**~~ ✅ **CLOSED by R-r (owner, 2026-08-13), §10.8.** It was the
   real one: the newest band — the one players are most likely to want — was the only band with no
   release automation, which defeated the point of coherent upload naming exactly where it is most
   visible. `.github/` is restored on `master`; the workflow needed no edit to serve it.

### 10.7 — 🔴 FOUND 2026-08-13: 10.2c shipped broken and blocked every release

**Phase 10 has never produced a release.** Every band's *Build* step has failed since the rename
landed. Found by querying the Actions API, not by anything in this repo — `git ls-remote --tags`
still shows only the pre-Phase-10 `mc1.21.*-v2.2.050-build.{24,25,27,28}` tags, so the four
published releases are still the **old** jars under the **old** names.

**Root cause — the guard asserted a string the release path cannot produce.**
`release.yml` builds with `-Pmod_version=<base minus -SNAPSHOT>`. But
`BandVersionLabelTest.theComputedVersionCarriesTheBandLabelAndIsLoaderParseable()` rebuilt its
expectation from `mod_version` **as written in `gradle.properties`**:

| | value |
|---|---|
| what the build produced (`mcmmo.build.version`) | `2.2.050+mc1.21.4` |
| what the test computed from disk | `2.2.050-SNAPSHOT+mc1.21.4` |

Reproduced locally at the exact failing commit (`dc94689485`, detached worktree, CI's own command):
**1 failure in 1715 tests**, that assertion, that message.

🔑🔑 **The shape to remember: a guard can be green on every developer machine and red on the only
invocation that ships.** Nobody passes `-Pmod_version` by hand, so the two operands agreed locally
and the guard looked solid on five branches. The gate that would have caught it — ship-gate step 1,
`./gradlew build` — **does not reproduce CI**, because it omits the `-P` override CI always passes.

- [x] **10.7a** `build.gradle` hands over the **resolved** `mod_version` as `mcmmo.build.modVersion`.
- [x] **10.7b** The label assertion compares against the resolved value, not the file's text.
- [x] **10.7c** New `theResolvedModVersionIsTheDeclaredOneOrItsReleaseForm()` keeps the
      file-vs-build agreement check the old assertion was making *by accident* — permitting exactly
      one difference (the `-SNAPSHOT` strip) and nothing else, so a stale configuration cache or a
      hand-typed override is still caught.
- [x] **10.7d** Converse `theModVersionRuleAcceptsOnlyTheSnapshotStrip()` drives the rule with values
      no real build produces. Without it the new rule is a tautology on every dev machine — which is
      exactly how the defect it replaces survived review.
- [x] **10.7e** ⚠️ **Ship-gate step 1 now runs CI's invocation**, not a bare `./gradlew build`.
      A gate that does not reproduce the release command cannot certify a release.
- [x] **10.7f** ✅ Back-ported to all four bands (`Backport-of: a1266ad53`), each **verified on its own
      band with its own release invocation** before commit: **1721 / 1720 / 1719 / 1719** tests
      (`.4`/`.5`/`.8`/`.10`), 0 failures. Drift audit: self-test green, then **0 MISSING**, each band's
      propagated count up by exactly one.

      ✅ **All four then released for real** — the first time Phase 10's naming reached a player:

      | Tag | Published asset | Old release |
      |---|---|---|
      | `mc1.21.4-v2.2.050` | `mcmmo-2.2.050+mc1.21.4.jar` | `-build.28` **reaped** |
      | `mc1.21.5-v2.2.050` | `mcmmo-2.2.050+mc1.21.5.jar` | `-build.27` **reaped** |
      | `mc1.21.8-v2.2.050` | `mcmmo-2.2.050+mc1.21.6-1.21.8.jar` | `-build.24` **reaped** |
      | `mc1.21.10-v2.2.050` | `mcmmo-2.2.050+mc1.21.9-1.21.10.jar` | `-build.25` **reaped** |

      The 10.4a ruling is confirmed by observation, not argument: the tag kept its `mc<VER>-v` prefix,
      so the reaping sweep still matched the old `-build.*` releases and cleaned them up with no
      manual step.
- [x] **10.7g** ✅ **CLOSED 2026-08-13 by R-r** — `mc1.21.11-v2.2.050` is published carrying
      `mcmmo-2.2.050+mc1.21.11.jar`, and `-build.26` was reaped automatically. **Phase 10 is
      complete: all five bands ship a Minecraft-labelled jar.** The original finding follows.
      🔴 **`master`'s release was the ONLY stale one** — `mc1.21.11-v2.2.050-build.26`,
      still carrying the old un-labelled `mcmmo-2.2.050-build.26.jar`. Under R-g `master` has no
      workflow, so it did not re-release with its siblings and **the newest band is the one band whose
      download still has no Minecraft version in its name** — the exact complaint Phase 10 exists to
      fix, now surviving only where it is most visible. ✅ **RULED by R-r — see §10.8.**

### 10.8 — R-r: restore release automation on `master` (owner-ruled 2026-08-13)

| # | Question | Ruling |
|---|---|---|
| **R-r** | 10.7g — how does `master` release? (2026-08-13) | ✅ **RULED (owner): restore `.github/` on `master`.** Force-add the same three files every band tracks. This **narrows R-g rather than reverting it**: R-g's finding was that `master` carried workflow files nothing used, and its accepted cost was that the newest band stopped releasing and R8 lost its automated leg. Both costs are now measured rather than predicted (§10.7, R11), and the owner priced them above the tidiness. R-g survives as *"`.github/` holds these three files and nothing else"*. |

🔑 **`release.yml` needs ZERO edits to work on `master`.** It already lists `master` in
`on.push.branches`, and its one-branch-per-MC-line check already has an explicit `master` case. The
*Publish release* step **globs** `build/libs/mcmmo-*.jar` rather than predicting the name, so
`mcmmo-2.2.050+mc1.21.11.jar` is discovered as-is. Restoring it verbatim is therefore not a
shortcut — it is the correct diff, and it is provably zero against all four bands (all three files
are byte-identical on every band: `release.yml` `e4e0976c3`, `drift-audit.yml` `9293ea921`,
`FUNDING.yml` `78f498c09`).

🔑🔑 **The bigger prize is `drift-audit.yml`, and it is why this is worth more than one jar rename.**
GitHub runs `schedule` triggers **only from the default branch** (`origin/HEAD → origin/master`,
verified). So the weekly audit sitting on all four band branches has **never fired since R-g** — it
is not merely un-run, it is structurally unreachable there. Restoring the file on `master` is what
actually revives **R8's third leg**, and it is the only leg that does not depend on somebody
remembering.

⚠️⚠️ **But restoring it verbatim would restore a guard that CANNOT FAIL.** The audit step passes
`--require-bands ${{ github.event.inputs.require_bands || '0' }}`, and on a `schedule` event
`github.event.inputs` is null — so the weekly run resolves to **`--require-bands 0`**, no floor at
all. The file's own header says that floor is the only thing separating a broken audit from a clean
one: a fetch that finds zero band branches exits 0, identical to success. Seventh vacuous-guard
sighting in this project.

- [x] **10.8a** ✅ `3b1ddff53`. Force-add the three files to `master` **by explicit path** — `.github/` stays in
      `.gitignore` (un-ignoring sweeps in the 12 Copilot files no branch tracks).
      ⚠️ `master`'s working copy already holds `.github/copilot-instructions.md` and
      `.github/skills/**`. **Never `git add -f .github`** — that commits the whole Copilot tree,
      which R-n forbids. One path per `add`, then read `git status` before committing.
- [x] **10.8b** ✅ `c3351998a` on `master`. Fix the floor: the scheduled run gets **`--require-bands 4`**, the real band count.
      Verified in **both** directions against the real repo before wiring it up — `4` → exit 0,
      `5` → exit 2 *"expected at least 5 band branch(es), found 4"*. The count lives in exactly one
      place (workflow-level `BAND_COUNT`) and the dispatch input deliberately has **no default**,
      because a literal there would be a second copy of the number and the two would drift.
      A stale floor is *under*-strict, never over-strict — cutting band #5 leaves `5 >= 4` passing,
      so it degrades quietly and cannot false-alarm. That is also why it needs a recipe step
      (**8.x.9** below) rather than trust. Lands on `master`, then back-ports to all four bands with
      `Backport-of:` — the file is inert there, but R-i keeps the branches byte-identical and a
      divergent copy is what the next cut would inherit.
- [x] **10.8c** ✅ **Gates 1–4 green on `master` (2026-08-13), run before the push, not after.**
      - **1** `-Pmod_version=2.2.050` → `BUILD SUCCESSFUL`, **1719 / 0 / 0**, matching `master`'s
        expected count. ⚠️ **The first run reported `> Task :test FROM-CACHE`** — green without
        executing anything; re-run under `--no-build-cache cleanTest test` for the real number, and
        `BandVersionLabelTest` (the guard that broke every release in 10.7) passes 11/11 under the
        release value. Hole written into ship-gate step 1 above.
      - **2** `mixin-allow-audit.py --mc 1.21.11 --check` → **61/61**, no injector resolving to 0.
      - **3** `boot-check.sh build/libs/mcmmo-2.2.050+mc1.21.11.jar 1.21.11` → **PASSED**, 0 ERROR,
        0 mixin failures, canary rejected. Run against **the exact artifact being published**.
      - **4** `config-id-audit.py --check` → **0 dead-everywhere**; control 688/689 (**99.9%**) vs the
        80% floor; the one miss (`Chain`) correctly classified *live on an older band*.
      - ⬜ **5 (`brew-smoke`) and 6 (`gameplay-smoke`) deliberately NOT run, and that is a narrowing
        of the gate, not a pass.** These three commits touch `.github/` and two `.md` files — zero
        `src/`, zero `gradle*`, zero configs — so the jar's *contents* are identical to what those
        gates last cleared on `master`. Stated rather than skipped silently; if the next `master`
        push touches code, both are mandatory again.
- [x] **10.8d** ✅ Pushed `0840cef8e`. The push itself released — `.github/workflows/release.yml` is
      inside `release.yml`'s own `paths:` filter, so adding the file triggered the workflow that
      publishes. There was no separate "release" action to take and no way to land it quietly.
- [x] **10.8e** ✅ **VERIFIED FROM OUTSIDE THE REPO. 10.7g is closed.**
      - run: `Build & Release` on **`master`** — `completed / success` at `0840cef8e`. That the
        workflow fired on `master` at all is the proof R-r took effect; nothing ran there before.
      - release: **`mc1.21.11-v2.2.050`**, assets `mcmmo-2.2.050+mc1.21.11.jar` +
        `-sources.jar`. **Every band now ships a Minecraft-labelled jar** — the complaint Phase 10
        exists to fix no longer survives anywhere.
      - reaped: `mc1.21.11-v2.2.050-build.26` gone from both the release list and the tag list,
        automatically, confirming the 10.4a tag-prefix ruling a second time.
      - ⚠️ `-build.3` **survives as a bare tag** — see the note under the band table.
- [x] **10.8g** ✅ Back-ported the floor fix to all four bands (`Backport-of: c3351998a`), each
      verified byte-identical to `master`'s copy. ⚠️ **The auditor cannot check this one.**
      `PROPAGATABLE_PREFIXES = ("src/", "gradle.properties", "build.gradle", "settings.gradle")`, so
      a `.github/`-only commit is classified not-propagatable — drift-audit neither demanded the
      back-port nor confirmed it. **That is R9's shape extended to CI config, and it is worse than
      docs drift**: a divergent `release.yml` silently changes how a band *ships*. Folded into R9.
      ⚠️ The band pushes deliberately triggered **no** releases — neither `.gitignore` nor
      `drift-audit.yml` is in `release.yml`'s `paths:` filter. Confirmed against the API after.
- [x] **10.8f** ✅ Done. Caveat-expiry pass. Grep the **symptom** — `weekly`, `no workflow`, `R-g`,
      `nothing runs on push` — not the files edited. Known live claims that go false: `AGENTS.md`
      ("the weekly run is gone", "nothing runs on push"), TODO `8.x.4` (a new band now **inherits**
      `.github/`, flipping the trap), `10.4`/`10.4′` ("`master` has no `.github/` at all"),
      the ship-gate preamble ("R-g retired CI"), and risk rows **R8**, **R10**, **R11**.
      ✅ `README.md` and `wiki/Building-from-Source.md` need **no** edit — both already say releases
      are published per band, a claim that was quietly false for `master` and becomes true here.
      *(`plans/completed/**` is an archive of what was decided then; it is not swept.)*

#### 10.8 — blast radius

| Step | Touches | Lost if wrong | Comes back from |
|---|---|---|---|
| 10.8a–b | 3 new files on `master`, 1 edited × 4 bands | nothing locally | `git rm --cached` the three paths; bands `git revert` |
| 10.8d | **a published GitHub release** | the `mc1.21.11-v2.2.050-build.26` release + tag are **reaped** by the sweep | the jar is rebuildable from that tag's commit; the sweep runs **only after a successful publish**, so a failed build leaves the old release standing and deletes only its own new tag |

⚠️ **The one destructive edge is the reaping sweep**, and it is the intended effect: it deletes every
`mc1.21.11-v*` release except the one just published. Ordering is fail-safe — publish succeeds
*first*, then reap — so a red build never reaches the sweep.

⚠️ **It reaps TWO tags on this line, not the one `10.7g` names.** Resolved from `git ls-remote --tags`
before the push rather than assumed, and `--cleanup-tag` deletes the tag along with the release, so
these SHAs are the entire recovery path:

| Reaped tag | Commit | Recovery |
|---|---|---|
| `mc1.21.11-v2.2.050-build.26` | `34aad16f2` | `git checkout 34aad16f2` → build → re-upload |
| `mc1.21.11-v2.2.050-build.3` | `afb2a6a6a` | `git checkout afb2a6a6a` → build → re-upload |

Neither is worth restoring — both are the un-labelled jars 10.7g exists to retire — but *"it is
recoverable"* is only true while the commit is written down somewhere the deleted tag isn't.

#### 10.8 — ⚠️ R-r invalidated the premise R-h was granted on ✅ **re-ruled by R-s**

**R-h** (2026-08-12) delegated pushes to the agent, and stated its own reason: *"Supersedes the
earlier 'owner keeps pushes' standing rule, **which existed because a band push released**. Under
R-g it no longer does."* R-r makes a push release again on all five branches, so the condition that
retired the old rule is gone and R-h now rested on a premise that is false. Flagged rather than
silently re-interpreted in either direction — **the owner confirmed that specific push in the moment
(2026-08-13)**, which is not the same as R-h surviving. ✅ **Re-ruled 2026-08-13 as R-s (below):
R-h stands, on its own merits rather than on the dead premise.**

### What I am NOT doing

- **No per-version duplicate jars.** Ruled out with the owner: CurseForge attaches one file to many
  game versions, so byte-identical copies would be dead weight. The band range in the filename is
  the whole fix.
- **Not deriving `depends.minecraft` from the new property.** Tempting single-source-of-truth, but it
  needs `fabric.mod.json` templating through `processResources` and would change what the loader
  reads. The 10.2c guard makes the two representations *provably* agree, which buys the same safety
  without touching mod metadata.
- ~~**Not restoring `.github/` on `master`**~~ — ✅ **superseded by R-r (owner, 2026-08-13), §10.8.**
  It was correctly out of scope for a *naming* task; the owner then ruled it in once §10.7 and R11
  measured what R-g's accepted cost actually was.
- **Not touching `mod_version` itself**, pending 10.6.1.
- **No Modrinth/CurseForge publish automation.** Not asked for.

---

## Phase 11 — pre-cut cleanups (2026-08-13, before `mc/1.21.3`)

Three items, found by querying GitHub from outside the repo now that `gh` is authenticated here for
the first time. **All three are invisible to every gate the repo runs** — that is the thread joining
them, and it is R11's shape rather than a coincidence.

### 11.0 — three owner rulings

| # | Question | Ruling |
|---|---|---|
| **R-s** | Does R-h survive R-r? (2026-08-13) | ✅ **RULED (owner): R-h STANDS — the agent pushes once the ship gate is green**, and a push publishing a real release is accepted. R-h is re-granted on its own merits rather than inheriting the dead *"a push no longer releases"* premise, so §10.8's flag is resolved rather than carried. ⚠️ The obligation that comes with it: **report what released**, read from the API rather than assumed, because R11 is still open and a red run reports nowhere. |
| **R-t** | Clean up the 6 orphaned drafts + the bare tag? | ✅ **RULED (owner): fix the workflow and let the sweep do it.** No hand-deletion. Each band's next release reaps its own orphan, which makes the fix **proven by observation** instead of by argument — the same standard 10.4a's tag-prefix ruling was held to and confirmed twice. ⚠️ **This deliberately leaves `mc1.21.11-v2.2.050-build.3` standing**: the sweep enumerates *releases*, and that tag has none, so no sweep can ever reach it. It needs a separate decision or it is permanent. |
| **R-u** | `master`'s `~1.21.11` vs the bands' closed ranges | ✅ **RULED (owner): leave it — `1.21.12` does not exist and will not.** `1.21.11` is the **last** `1.21.x`; the line continues at `26.1`. Provable from this file's own scope line (*"every stable `1.21.x` (**12**)"* — `1.21` … `1.21.11` is exactly 12), so `~1.21.11`'s `>=1.21.11 <1.22` window is **empty by construction** and the asymmetry with the four bands costs nothing. 🔑 Recorded because it reads as an untidiness a future pass will "fix" — and that fix would be a `src/` change, i.e. a needless `master` release. `BandVersionLabelTest` already permits the tilde deliberately (`theRangeIsNotUnboundedAboveTheBand`), so nothing enforces the change either way. ⚠️ This is a **dated fact about Minecraft's versioning**, which stays true — not a claim about what this build targets, which would rot. |

### 11.1 — 🔴 every release run orphans a draft, one per band, forever

**Found 2026-08-13 from the Releases API.** The repo publishes 5 releases and carries **6 drafts**,
and the drafts are not half-finished uploads — each is a *formerly published* release (`draft: true`
**with** `published_at` set) still holding both jar assets:

| tag | live id | orphaned draft ids |
|---|---|---|
| `mc1.21.4-v2.2.050` | `370323008` | `370312521`, `370252596` |
| `mc1.21.11-v2.2.050` | `370315399` | `370299032` |
| `mc1.21.10-v2.2.050` | `370322940` | `370257071` |
| `mc1.21.8-v2.2.050` | `370322826` | `370255620` |
| `mc1.21.5-v2.2.050` | `370322811` | `370254125` |

**Root cause — two lines that are each correct alone:**

1. `release.yml`'s *Create and push tag* step runs `git push origin ":refs/tags/${TAG}"` before
   re-tagging, for idempotency across re-runs. **Deleting a tag out from under a published release
   converts that release to a draft; it does not delete it.** `gh release create` then mints a
   *second* release object at the same tag.
2. The reaping sweep protects the new release **by tag name** — `[ "$t" = "$TAG" ] && continue`. Two
   releases now share that name, so the orphan is skipped on every subsequent run, forever.

🔑 **The arithmetic is the proof, and it is exact.** `master` released twice → 1 orphan; `mc/1.21.4`
three times → 2; `.5`/`.8`/`.10` twice each → 3. **6.** Nothing else is a candidate explanation.

⚠️ **This is also why 10.7f's *"the sweep works"* observation was true and still missed it.** The
`-build.*` releases were reaped correctly **because their tag names differed**. Dropping the
`-build.<N>` suffix (10.4a) is what made re-releases collide on one tag name — so the defect was
*introduced by* Phase 10 and *hidden by* the very evidence that Phase 10 succeeded.

Player-facing impact is **nil** — drafts are collaborator-only. The cost is that it grows by one per
band per release: 5 today, **9 after Phase 9**, and it makes `gh release list` unreadable exactly
when a person is checking whether a release worked.

- [x] **11.1a** ⚠️⚠️ **Do NOT fix it by deleting the old release before the tag push.** That is the
      obvious fix and it destroys the property §10.8's blast-radius table depends on: publish
      succeeds *first*, then reap, so **a red build leaves the previous release standing**. Deleting
      up front would tear down a good release on behalf of a build that then fails.
- [x] **11.1b** Reap by **release id**, not tag name. After a successful publish, resolve the new
      release's id from `GET /releases/tags/{tag}` — ✅ **verified 2026-08-13 that this endpoint
      returns the published release and ignores same-tag drafts** (`mc1.21.4-v2.2.050` → `370323008`,
      `draft:false`, with two drafts present). Then delete every release on the `mc<VER>-v` prefix
      whose id differs.
- [x] **11.1c** ⚠️⚠️ **Clean up the TAG only when the tag differs.** A same-tag orphan shares the tag
      the new release is standing on, so reaping it with a tag delete would delete that tag and
      **draft the release just published** — the exact defect being fixed, inverted, and it would
      look like success. Delete the tag for a differently-named release; delete the release alone for
      a same-tag orphan.
      ✅ Also switched from `gh release delete <tag>` to `gh api -X DELETE .../releases/<id>`:
      **the tag form is ambiguous once two releases share a tag**, which is the whole premise here.
- [x] **11.1d** ✅ Dry-run read-only against the live repo: **1** deletion (`370299032`, tag kept),
      live `370315399` kept by id, all 9 other-band releases untouched.
      Then promoted to a committed guard — new **`scripts/release-sweep-selftest.sh`**, 6 cases,
      `--mutate` proving each can fail. It **extracts the step from `release.yml` itself** rather
      than copying it, because a copy drifts and then certifies the copy.
      ⚠️⚠️ **Its first draft certified itself.** YAML block scalars strip indentation, the mutation
      patterns were written at *file* indentation, and **2 of 4 mutations silently no-op'd and
      "passed"** — a vacuous *proof*, not a vacuous guard, which is a new variant of this project's
      recurring defect (**eighth sighting**). `--mutate` now asserts each pattern actually matched
      and reports `MUTATION DID NOT APPLY` rather than counting it as caught.
      🔑 M1 is the one that matters: it deletes the same-tag orphan's tag, and the harness catches
      `DELETED-TAG mc1.21.11-v2.2.050` **while the log line still says "keeping the tag"** — proof
      that a reassuring log message is not a guard.
- [x] **11.1e** ✅ **CONFIRMED BY OBSERVATION.** `f18cbef82` on `master`, run `31767425216` green.
      The sweep's own log, and it reaped **two**, not the one predicted:

      ```
      Published release id 370334085 on tag mc1.21.11-v2.2.050
      Deleting orphaned draft release 370299032 on the live tag … (keeping the tag).
      Deleting orphaned draft release 370315399 on the live tag … (keeping the tag).
      Reaped 2 release(s); mc1.21.11-v2.2.050 (id 370334085) stands.
      ```

      🔑 The second one is the mechanism caught in the act: `370315399` was the *live* release when
      the run started, and **this very run drafted it** by re-pushing the tag. The fix cleaned up the
      orphan it had just created, in the same run. The harness fixture predicted exactly this pair,
      so the stub-based cases are now known to model production.
      ✅ The tag survived and moved to `f18cbef82` — the 11.1c edge held.
- [x] **11.1f** ✅ Back-ported to all four bands (`Backport-of: f18cbef82`) at `1608d5084` /
      `6c4ec8db4` / `edd7a8932` / `4b2716be6`. Per band before commit: three files **byte-identical
      to `master`'s**, `release-sweep-selftest --mutate` 6/6 with all four mutations caught,
      `ci-watch --self-test` 6/6. All four releases green
      (`31767640640` / `31767642188` / `31767727306` / `31767646758`), each verified through the new
      ship-gate step 8.
      ⚠️ **Ship gate deliberately narrowed to steps 1 (in CI), 7 and 8** — the change is `.github/` +
      `scripts/` only, zero `src/`, zero `gradle*`, zero configs, so the jar is byte-identical to
      what gates 2–6 last cleared on each band. Stated rather than skipped silently, per 10.8c.
      ✅ Gate 7 after the pushes: self-test green, then **0 MISSING on every band**
      (20 / 7 / 8 / 11 propagated), each count up by exactly one.

      ### ✅ 11.1 RESULT — **0 drafts, 5 published, one per band**

      | Line | Release | Assets |
      |---|---|---|
      | `mc1.21.11-v2.2.050` | `370334085` | `+mc1.21.11` |
      | `mc1.21.10-v2.2.050` | `370335986` | `+mc1.21.9-1.21.10` |
      | `mc1.21.8-v2.2.050` | `370335873` | `+mc1.21.6-1.21.8` |
      | `mc1.21.5-v2.2.050` | `370336242` | `+mc1.21.5` |
      | `mc1.21.4-v2.2.050` | `370335894` | `+mc1.21.4` |

      **All seven orphans gone** — the six found, plus the one `master`'s own run minted — and not
      one was deleted by hand. R-t's *"let the sweep prove itself"* is what makes that a result
      rather than a tidy-up. ⚠️ `mc1.21.11-v2.2.050-build.3` still stands, as R-t intended.

### 11.2 — R11: a local post-push CI check (`scripts/ci-watch.sh`)

R11 has already cost a day (§10.7) and its mitigation is still *"remember to curl the Actions API"*.
`gh` is authenticated in this working copy for the first time, so the check becomes a script.

- [x] **11.2a** ✅ New `scripts/ci-watch.sh <sha|HEAD>` — resolve the sha, find the `Build & Release`
      run for it, wait for completion, print the conclusion, and **exit non-zero on failure**.
- [x] **11.2b** ✅ **Fails closed on "no run found"**, with four distinct exit codes rather than a
      boolean: `0` succeeded-or-legitimately-skipped · `1` the run failed/timed out · `2` environment
      · **`3` cannot tell** — the sha is not on the remote, or it changes paths the workflow builds on
      and no run exists. *"I could not see a run"* and *"the run passed"* are the two states R11 is
      about and they must never render alike.
      🔑 It reads the `paths:` filter **out of `release.yml`** and diffs the commit against it, so
      *"docs-only, no run expected"* is derived rather than assumed — and checking that **first**
      turns the docs-only answer from a 90-second wait into 1.6 s.
- [x] **11.2c** ✅ `--self-test`, 6 cases, stubbing `gh`: success→0, failure→1, missing-run-but-should
      -build→3, docs-only→0, unpushed→3, never-completes→1. **Mutation-proven 4 ways** (missing run
      reports OK · any conclusion counts as success · unpushed reports OK · timeout reports success);
      all four caught.
- [x] **11.2d** ✅ Ship gate gains **step 8** (below). Cherry-picked to every band in 11.1f —
      `scripts/` is propagatable under R9a, so drift-audit demands it.

⚠️ **This does not close R11 and must not be recorded as closing it.** It is still a person running a
command; it just makes the command short and its failure mode explicit. A real close needs a
notification that reaches the owner when no terminal is open.

### 11.3 — the weekly drift audit has never fired

`drift-audit.yml` landed on `master` at `3b1ddff53` (R-r) with its floor fixed at `c3351998a`, and
`gh run list --workflow=drift-audit.yml` returns **nothing**. Its `cron` is Monday 07:00, so the
first real firing is still ahead — and a guard that has never run is not known to work, which is the
rule this repo has already been burned by seven times.

- [x] **11.3a** ✅ **FIRED AND GREEN — run `31767895610`, `workflow_dispatch` on `master`.** The
      `require_bands` input was left **blank**, so it took the same route a `schedule` event does
      (`github.event.inputs` is null there, falling through to `env.BAND_COUNT`) — the only path
      worth proving; supplying a value would have tested a path nothing uses.
      🔑 **The floor resolved live**, visible in the run's own command line:
      `python scripts/drift-audit.py --require-bands "4" --json drift-audit.json`, with
      `BAND_COUNT: 4` in the step env. R-r's fix from a vacuous `0` to a real `4` is now observed
      rather than argued, and the run found all four bands and reported the same **0 MISSING** as the
      local audit.
      ⚠️ **R8's unattended leg is now known to work — and it is still weekly, and still reports to a
      tab nobody opens (R11).** What this closes is *"the guard has never run"*, nothing more.
- [x] **11.3b** ✅ **AND IT HAS NOW FIRED UNATTENDED.** Run `32005557735`, **`event: schedule`**,
      *Band drift audit*, **success**, `2026-08-17T07:22:56Z` on `4440af5d0` — the real weekly cron,
      with nobody dispatching it. 11.3a proved the *file* worked when poked; this is the first time the
      leg ran **on its own**, which is the only version of it R8 was ever counting on.
      🔑 Found incidentally while querying the API for something else (§12.3), which is R11 restating
      itself: **the run reported nowhere.** A green scheduled audit and a repo with no scheduled audit
      at all still look identical from inside this working copy.

#### 11.x — blast radius

| Step | Touches | Lost if wrong | Comes back from |
|---|---|---|---|
| 11.1b–d | `.github/workflows/release.yml` on `master` | nothing — read-only until pushed | `git revert`; `master` clean at `010cc2d5d` |
| 11.1e | **a published release, and the reaping sweep** | 🔴 a wrong `--cleanup-tag` branch deletes the live tag and **drafts the release it just published** | the jar rebuilds from the tag's commit; the release re-publishes on the next push. Bounded to the `mc1.21.11-v*` line |
| 11.1f | 4 band branches | **4 releases fire** (`release.yml` is in its own `paths:` filter) | per-band `git revert` |
| 11.2 | new `scripts/` file, `TODO.md` | nothing — read-only over the GitHub API | delete the file |
| 11.3a | nothing — the audit only reads | nothing | n/a |

🔴 **11.1e is the one destructive step**, and its blast radius is *one Minecraft line's releases*, not
the repo's. The sweep only ever matches `mc${MC_VERSION}-v*`, so a bug in it cannot reach another
band — which is exactly why it lands on `master` alone first and the bands wait for 11.1e's
observation.

### What I am NOT doing in Phase 11

- **Not hand-deleting the 6 drafts** (R-t). The sweep does it, or the fix is not proven.
- **Not deleting `mc1.21.11-v2.2.050-build.3`.** No release is attached, so no sweep reaches it; it
  needs its own owner call. Recovery is `afb2a6a6a`, already recorded in §10.8.
- **Not tightening `master`'s `~1.21.11`** (R-u) — the window it over-claims is empty.
- **Not closing R11.** 11.2 shortens the manual check; it does not make anything unattended.
- **Not starting the `mc/1.21.3` cut** until 11.1e has been observed. Cutting a band on top of a
  known-broken release sweep just mints a sixth orphan.

---

## Phase 12 — the two defective gates (2026-08-17, before 8.3)

Both were filed at the end of the `mc/1.21.3` cut and **neither is fixed**. They are the same shape as
each other and as R11: **a gate that cannot see the thing it exists to watch reports the same green as
a gate that looked and found nothing.** 8.3 runs both, so they go first.

Tier 1 each, staged as two commits. ⚠️ `scripts/` is **outside `release.yml`'s `paths:` filter**
(verified 2026-08-17 against the file: `src/**`, `build.gradle`, `settings.gradle`,
`gradle.properties`, `gradle/**`, `gradlew`, `gradlew.bat`, `.github/workflows/release.yml`) — so
**neither commit nor any of its five back-ports releases anything.** It is propagatable under R9a, so
drift-audit *will* demand the back-port.

### 12.1 — 🔴 `ci-watch.sh` reported exit 0 for a push that DID release

**Reproduced live 2026-08-17, from the API and the working copy — not from the filed note, which had
the mechanism half right.**

| Evidence | Value |
|---|---|
| the run | `31774466258` · `Build & Release` · `event: push` · **`conclusion: success`** |
| its `headSha` | **`f3ef33c0c`** |
| what `f3ef33c0c` changes | **`TODO.md`, and nothing else** |
| what the same push also carried | `bf2676292` — `src/main/…/EntityTypeSpawnOriginMixin.java` + `src/test/…/MixinApplicationTest.java` |
| `bash scripts/ci-watch.sh f3ef33c0c` | *"touches nothing in the `paths:` filter, so no run is expected. **Skipped, not passed**."* → **exit 0** |

So the band's release run was green, real, and attributed to a sha the gate declared could not build.

**Two independent defects, one symptom. Either alone produces the exit 0.**

- **A — the filter is evaluated over the TIP COMMIT ALONE.** `ci-watch.sh:62` runs
  `git diff-tree --no-commit-id --name-only -r "$sha"`, which is one commit. **GitHub evaluates
  `paths:` over every commit in the push** and then stamps the run with the push's head sha — so the
  head sha of a real run can itself sit outside the filter, which is exactly what `f3ef33c0c` is.
- **B — the filter is consulted BEFORE anything asks whether a run exists.** `ci-watch.sh:165`
  returns at the filter; the poll loop that would have found run `31774466258` starts at `:171` and is
  never reached. 🔑 **B is the load-bearing one**, and it is a doctrine error rather than a parsing
  error: the `paths:` filter is evidence about an **absence**, and it was being used to rule on a
  **presence** it had not looked for.

- [x] **12.1a** ✅ Ask the API **first** — `find_run()` is extracted and called before the filter is
      consulted. A run found at this sha is reported on whatever the filter says. This alone closes
      the observed case.
- [x] **12.1b** ✅ The filter is evaluated over the **push range** (`push_triggers_release`), not one
      commit. Resolution order, **failing closed at every step**: `CI_WATCH_BASE` override → the
      remote-tracking reflog (`<upstream>@{1}..<upstream>@{0}`, used **only** when `@{0}` really is
      this sha) → **refuse**. An undeterminable range reaches **"cannot tell" (exit 3)**, never
      *"skipped"*; `0` is now reserved for a range that was read and provably matches nothing.
      🔑 The range is read with `git log --name-only`, the **union over the commits**, not the net
      diff — a file added and then reverted inside one push still triggered GitHub, and a superset can
      only push the verdict toward *"a run was expected"*, which is the safe side.
- [x] **12.1c** ✅ Fast docs-only path kept: one API call, then the filter, then return. Verified on
      `master` `4440af5d0` — *"no run exists … nothing in the pushed range touches the filter"*, no
      90 s appear-wait.
- [x] **12.1d** ✅ Self-test **6 → 8 cases**. The one that matters — *"run EXISTS though the filter
      says docs-only → reported, not skipped"* — is **red before the fix and green after**.
- [x] **12.1e** ✅ `--mutate` added, 4 mutations, **all caught**, each proven to have applied.

**✅ 12.1 VERIFIED, and the exit code is the least interesting half.** Against the real sha, before
and after:

| | verdict | exit |
|---|---|---|
| committed version | *"touches nothing in the `paths:` filter … **Skipped, not passed**"* | 0 |
| fixed version | *"✓ `Build & Release` **succeeded** for `f3ef33c0c` (run `31774466258`)"* | 0 |

⚠️ **Both exit 0, and that is the point** — the codes coincide only because that run happened to
pass. Had it failed, the old script still reported 0 while the release was red, which is R11 wearing a
green tick. The discriminating case is the one now pinned in the self-test.

#### 🔴 12.1f — the mutation harness certified itself first (NINTH sighting)

The `--mutate` pass's first run reported **all four mutations NOT CAUGHT**, which is implausible on
its face — M4 turns every conclusion into a success and could not possibly go unnoticed. It was not a
weak guard; **the mutations were rewriting their own argument list.**

Every pattern is a literal that appears *twice* in the file: once in `watch()`, and once as the
`mutate "…" 'pattern' 'replacement'` call inside `self_test()` — and **`self_test()` sits above
`watch()`**, so `str.replace(old, new, 1)` hit the self-test's own text and left the real code
untouched. Python exited 0, the pattern was genuinely "found", and the harness scored four clean
no-ops as *"the guard is decoration"*.

🔑🔑 **`release-sweep-selftest.sh` is immune for a structural reason worth copying, not a lucky one:
it extracts the code under test into a SEPARATE FILE.** The patterns and the code never share a
document, so a collision is impossible. This harness mutates *itself*, and self-mutation needs the
test scaffolding cut out of the mutant before it can mean anything.

- [x] Build the mutant from a **`base.sh` with `self_test()` excised**, so each pattern is unique.
- [x] Assert **uniqueness, not mere presence**: `count(old) != 1` now exits `8` = *MUTATION
      AMBIGUOUS*, distinct from `9` = *absent*. [[mutation-that-never-applied]] taught "assert it
      applied"; this adds **"assert it applied *where you meant*"** — a first-occurrence replace on an
      ambiguous pattern is a silent no-op wearing a successful exit code.

### 12.2 — 🔴 `boot-check.sh` reports a STAGING failure as a MOD failure

`boot-check.sh:49-53`: when the fabric-api jar is not in the Gradle cache it prints `warn:` and
**continues**. The server then boots with no fabric-api, mcMMO cannot load, the log never reaches
`Done (`, and the verdict is `❌ FAIL: never reached 'Done ('` — *the mod broke the server* — at
**exit 1, the same code a real mod failure returns.**

⚠️ It bites hardest exactly where it is least expected: a band's **non-pinned** version. Gradle only
ever resolves the pinned coordinate, so `boot-check.sh <jar> 1.21.2` on the `mc/1.21.3` band asks for
a fabric-api Loom was never asked to fetch. That is the second boot the `mc/1.21.3` cut added (8.2.8),
i.e. the gate is weakest on the run that exists to widen coverage.

- [x] **12.2a** ✅ Cache first, then **download** from `maven.fabricmc.net`. Verified 2026-08-17, both
      coordinates `200` (`0.106.1+1.21.2`, `0.114.1+1.21.3`) — the `+` is literal in a Maven path and
      needs no escaping. Adds no new dependency class: the script already `curl`s the server launcher.
      🔑 Staged **before** the launcher download now, not after: it is the cheaper check and the one
      that used to fail silently, so failing fast costs nothing and saves a discarded download.
- [x] **12.2b** ✅ Both fail → **exit 2 (environment), never 1**, naming the coordinate, the cache dir
      and the URL. The exit-code contract is now in the file header: `1` = *the mod is bad*, `2` =
      *nothing was proven about the mod*. ⚠️ The launcher-fetch failure was **exit 1 too** and is
      fixed in the same commit — same defect, same line of reasoning, one line away.
- [x] **12.2c** ✅ `--self-test`, **4 assertions**, the first this script has ever had.
      ⚠️ **The obvious version of this test was vacuous and was rewritten.** A case using the *pinned*
      coordinate only ever hits the Gradle cache, so it leaves the entire new download path
      unexercised — the same "green without executing the thing" shape as `:test FROM-CACHE`. It now
      drives three staging states through a `BOOT_CHECK_FAPI_CACHE` seam and a stubbed `curl`
      (offline, deterministic), and **asserts the exact maven coordinate URL** rather than trusting
      that curl was called at all.
      Every case also asserts **`booted=0`** — a refusal that fires after the JVM starts has already
      lost the distinction it exists to make.

**✅ 12.2 VERIFIED three ways.**

1. `--self-test` **4/4**.
2. **Mutation-proven**: reverting the refusal to the old warn-and-continue makes **exactly one** case
   fail — *"cache miss + 404 → exit 2"* returns **0** — and the other three still pass, so the red is
   the mutation and not collateral damage.
   ⚠️ The first mutation run was thrown away: it put the mutant in `/tmp`, so `REPO` resolved to `/`,
   `prop` read no `gradle.properties`, and a case failed on **empty MC/fabric-api values** rather than
   on the mutation. A mutation proof whose red comes from its own harness proves nothing — the same
   error as 12.1f, caught the same way (the failure did not match the prediction).
3. **A real boot, forcing the new path.** `BOOT_CHECK_FAPI_CACHE=<empty>` against the `mc/1.21.3` jar
   on `1.21.3` fetched fabric-api from maven for real and then **PASSED the full boot**: canary
   rejected, mcMMO initialised, configs loaded, `/mcmmo` renders, `/mcstats` dispatches, clean
   shutdown, **0 ERROR, 0 mixin failures**. 🔑 That is what makes the download path trustworthy — not
   that a file appeared, but that the server accepted it.

### 12.3 — propagation ✅ DONE

- [x] Back-ported to all five bands, one commit each carrying **both** trailers
      (`Backport-of: f9a0e0f2c`, `Backport-of: 54db48fae` — the auditor's `TRAILER.finditer` collects
      every trailer in a message, so one commit satisfies both master commits).
      Per band **before** commit: both blobs byte-identical to `master`'s, `ci-watch --mutate` 4/4
      caught, `boot-check --self-test` 4/4 against **that band's own pinned fabric-api**.

| Branch | commit | | Branch | commit |
|---|---|---|---|---|
| `master` | `54db48fae` | | `mc/1.21.5` | `f0bd4eb8d` |
| `mc/1.21.3` | `5a3c0355a` | | `mc/1.21.8` | `3c25c8e6e` |
| `mc/1.21.4` | `1916eb71f` | | `mc/1.21.10` | `23fab559d` |

- [x] All six pushed and level. **Gate 7** — `drift-audit.py --self-test` green, then `--master
      master`: **0 MISSING on all five bands** (23 / 3 / 10 / 11 / 14 propagated).
- [x] **Gate 8 is the regression test, and it passed on all five.** Every band's `scripts/`-only push
      now returns *"no run exists … and nothing in the pushed range touches the filter"* — a stated
      skip **over a resolved two-commit range**, which is precisely the reasoning the old script could
      not do. ✅ API confirms **zero releases fired**, as the `paths:` filter predicted.

⚠️ **A process error worth recording, because nothing caught it but re-reading the output.** The first
back-port attempt ran switch + `checkout master -- <paths>` + verify in a loop with **no commit in
it**. Since all five bands held byte-identical blobs, `git switch` carried the staged changes
*forward* instead of refusing — so four bands were "verified green" while nothing was committed on
any of them, and the whole change was sitting uncommitted on whichever branch the loop ended on.
🔑 **Staging and committing are separate, and a loop that verifies without committing verifies a
working tree that is about to move.** It surfaced only because the follow-up blob check listed four
bands as *still pre-fix*. Nothing was lost — no band was ever damaged — but the loop had reported
`ALL FOUR STAGED AND GREEN`.

### 12.x — blast radius

| Step | Touches | Lost if wrong | Comes back from |
|---|---|---|---|
| 12.1 | `scripts/ci-watch.sh` on `master` | nothing — read-only over the GitHub API | `git revert`; `master` clean at `4440af5d0` |
| 12.2 | `scripts/boot-check.sh` on `master` | nothing — it only writes under `build/boot-check/` and downloads into it | `git revert` |
| 12.3 | 5 band branches | nothing — **no band releases**, `scripts/` is outside the `paths:` filter | per-band `git revert` |

**No step here is outward-facing and no step deletes anything.** The one new side effect is a network
fetch into `build/`, which is already what line 41 does.

### What I am NOT doing in Phase 12

- **Not closing R11.** Both fixes make a gate see what it was blind to; neither makes anything
  unattended. R11 closes on a notification, not on a better script.
- **Not rewriting `ci-watch.sh`'s exit-code contract.** The four states are right; the bug is that one
  path reached the wrong one.
- **Not teaching `boot-check.sh` to pick a fabric-api version for a version it was not given.** It
  takes the coordinate as `$4` on purpose. Guessing one is how a gate certifies the wrong artifact.
- **Not starting 8.3** until both are green and back-ported.

---

## The ship gate — run per band, before every push

**It is a person running seven commands, and that has not changed.** ⚠️ R-r put `release.yml` back on
every branch including `master`, so a push now *builds and runs the suite* again — but that is gate
**1 only**, it runs **after** the push rather than before it, and a red run reports to a tab nobody
watches (**R11**). Six of the seven gates have no automation at all. Run the list first; the workflow
is a backstop, never the check.

1. `./gradlew --no-daemon --stacktrace build -Pmod_version=$(grep -E '^mod_version=' gradle.properties | cut -d= -f2 | sed 's/-SNAPSHOT$//')`
   exit 0 — suite green, and the **count should match `master`** (~1719). A lower count means
   something was disabled to get there.

   ⚠️⚠️ **The `-Pmod_version` override is NOT decoration — run the gate exactly like this.** A bare
   `./gradlew build` is *not* what CI runs, and that gap is not theoretical: it is precisely how
   §10.7 shipped a guard that was green on all five branches and red on every release, blocking
   every band's release for a day with nothing reporting it. **A gate that does not reproduce the
   release command cannot certify a release.** ⚠️ And read Gradle's own exit code — `cmd | tail`
   returns *tail's*, which reported a failed build as `exit 0` during that investigation.

   ⚠️⚠️ **`BUILD SUCCESSFUL` does not mean the suite ran. Grep the log for `> Task :test`** and
   confirm it is bare — not `FROM-CACHE`, not `UP-TO-DATE`. Found 2026-08-13 running this very gate:
   it reported `BUILD SUCCESSFUL in 1m 21s` with **`> Task :test FROM-CACHE`**, so the release was
   about to be certified on results the invocation never executed, and the XML under
   `build/test-results/` was left over from an earlier run rather than produced by this one. That is
   the same shape as 10.7 one level down — *the command was right and the execution never happened*.
   To actually run them:

   ```
   ./gradlew --no-daemon --stacktrace --no-build-cache cleanTest test -Pmod_version=<resolved>
   ```

   ⚠️ **Also check `build/libs/` holds exactly one non-sources jar before reading a jar name off it.**
   `build` never cleans it, so a working copy that has built several bands accumulates them — ten
   were sitting there on 2026-08-13 (`+mc1.21.4`, `+mc1.21.5`, `+mc1.21.6-1.21.8`, `+mc1.21.9-1.21.10`,
   `+mc1.21.11`, each also as `-SNAPSHOT`). **CI is immune** — a fresh checkout starts empty, and
   `release.yml`'s publish step refuses to guess between candidates and `exit 1`s. **A local
   `boot-check.sh` glob is not immune**, and would happily boot another band's jar.
2. `python scripts/mixin-allow-audit.py --mc <version> --check` — 61/61. A `MISMATCH` is a fact to
   record, not a bug to suppress.
3. `scripts/boot-check.sh <jar> <version>` — 0 ERROR, 0 mixin failures, canary rejected.
   ⚠️ **Read the exit code, not just the verdict line: `1` = the mod is bad, `2` = ENVIRONMENT and
   nothing was proven about the mod** (§12.2). It used to warn-and-continue on a fabric-api cache
   miss and then report the resulting dead server as a mod failure at exit 1. It now fetches from
   `maven.fabricmc.net` and refuses at 2 if that fails. `--self-test` first, as with every other gate.
4. `python scripts/config-id-audit.py --check` — 0 dead-everywhere. Works on **every** band since
   8.4; it reads the committed `scripts/mc-ids.txt`, so it needs no local Loom cache at all.
   ⚠️ **Cherry-pick `extract-mc-ids.py` + `mc-ids.txt` together** — the audit imports the
   generator's parser and refuses to run without it.
5. `scripts/brew-smoke.sh` — passes **with** its vanilla control failing.
6. `scripts/gameplay-smoke.sh` — 29/29, and `GAMEPLAY_SMOKE_CONTROL=1` must **fail**.
7. `python scripts/drift-audit.py --self-test` **then** `--master master` — **0 MISSING on every
   band**. ⚠️ It audits `origin/master`, so **push first, then audit**.
8. `scripts/ci-watch.sh --mutate` **then** `scripts/ci-watch.sh HEAD` — **after** the push, the
   only gate that runs downstream of it. Exit 0 means that sha's `Build & Release` run *completed
   successfully*, read from the API. ⚠️ **This is R11's mitigation, not its close.** Steps 1–7 all
   certify a build that has not shipped yet; step 8 is the only one that looks at what actually did.
   §10.7 is what happens without it: four band releases red for a day behind five green ship gates.

   ⚠️ **Run it FROM the branch you pushed.** §12.1 made it resolve the push range from that branch's
   remote-tracking reflog, so `ci-watch.sh <band-sha>` invoked while sitting on `master` cannot
   establish a range and fails closed at **exit 3 (cannot tell)** — correct, but not the answer you
   wanted. `CI_WATCH_BASE=<sha before the push>` is the override when the reflog is gone.
   ⚠️ **And it no longer trusts the `paths:` filter over a run that exists.** It looks for a run
   first; the filter now only ever explains an *absence*. That ordering is the fix — a docs-only tip
   commit on a push that also carried `src/**` used to report a green release as *"Skipped."*

✅ **`scripts/`-only and `.github/`-only commits are now tracked** (R9a, 2026-08-13), so "cherry-pick
tooling deliberately" is enforced rather than remembered. ⚠️ **Docs are still not**, deliberately —
their correctness is checked instead by `BandDocsMatchRealityTest`, which runs inside gate 1.

---

## Risk register

| # | Risk | State |
|---|---|---|
| R1 | Band count makes "all versions" unviable | ✅ **CLOSED** — 7 bands total, 4 branches left to cut |
| R2 | CI time explodes | **Downgraded** — branches build independently. Trigger: ~30 min per band |
| R3 | Version-specific code leaks into skill logic | ✅ **CLOSED** — 26 → 0 leak sites; `PlatformBoundaryGuardTest` held on two real API breaks |
| R4 | Silent mixin misbinding via dropped `@Slice` | ✅ **CLOSED** — `allow = N` on all 61 injectors, measured from bytecode |
| R5 | Item-ID drift silently disables config rows | ✅ **CLOSED (2026-08-13, §8.4)** — `config-id-audit.py` covers all 12 versions off a committed registry manifest, plus two per-band tests (`ConfigItemIdResolutionTest` at runtime, `ConfigIdManifestTest` on the manifest). ⚠️ It stays closed only while the manifest is **cherry-picked, never regenerated per band** |
| R6 | Component-API cliff needs reimplementation | 🔴 **NOW IN SCOPE under R-l.** Confined to band `1.21.1`; it is what R-m decides |
| R7 | Live playtest disrupted | ✅ Phase 0 tag + instance backup |
| **R8** | **A fix lands on `master` and is silently never back-ported** | 🟡 **DOWNGRADED, not closed (R-r, 2026-08-13).** All three legs exist again: the convention, `drift-audit.py`, and the weekly run — restored to `master`, the only branch GitHub fires `schedule` from, and its band floor fixed from a vacuous `0` to a real `4`. ⚠️ **The unattended leg is weekly and reports to a tab nobody opens (R11)**, so between a commit and the next Monday detection is still *"somebody remembers"*. **Each new band multiplies this** — 5 bands today, 9 after Phase 9 — and the floor must be raised per cut (8.x.9) or it silently stops counting. The one open instance (`f73031ed9`) is closed by **R-q**. ✅ **2026-08-17 (§11.3b): the weekly leg has now fired UNATTENDED for the first time** — run `32005557735`, `event: schedule`, success. The leg is no longer theoretical; the reporting gap (R11) is all that is left of this row |
| **R9** | **A fix outside `src/` never reaches a band, and the docs deny a band that ships** | ✅ **CLOSED 2026-08-13 (`d6080f028`), as TWO fixes — it was never one problem.** **R9a (propagation):** `PROPAGATABLE_PREFIXES` gains **`scripts/`** and **`.github/`**. It had been only `src/`, `gradle.properties`, `build.gradle`, `settings.gradle`, so a band could silently lack the tooling its own gates need, and a divergent `release.yml` could change how it *ships*. Exercised live: the R-r floor fix reached four bands while the auditor printed **"No drift" identically before and after**, because it could not see the commit at all. Self-test extended both directions for both prefixes and mutation-proven (dropping either makes it fail, naming that prefix). Real audit after: still **0 MISSING**, propagated counts up 12→18/4→5/5→6/6→9 — the manual discipline *had* held; it is mechanical now. **R9b (correctness):** new per-band `BandDocsMatchRealityTest` asserts the documented support floor sits strictly below every version *this* branch ships, both docs state the same floor, and this band's versions appear in the README. ⚠️⚠️ **A propagation check could never have caught the recorded instance** — `mc/1.21.4` shipped while six pages said *"1.21.4 and older are not supported"*, and those pages were **byte-identical on all five branches and identically wrong**, so both the audit and `git diff master <band> -- README.md wiki/` read clean and were right to. **Cross-branch equality is not correctness.** 🔑 Docs stay deliberately OUT of the propagatable set: per-push docs failures train people to ignore the audit, and propagation is the wrong instrument anyway. ⚠️ **Next fires on `mc/1.21.3` (8.2)** — the floor sentence must move to `1.21.1` in the same commit, in **both** files |
| **R11** | **A band's release fails and nobody finds out** | 🔴 **STILL OPEN — R-r did NOT close it.** It has already happened once: Phase 10's guard defect (§10.7) failed **four** band releases on 2026-08-13 and was invisible for a day with local builds green, ship gate green, drift audit green and `git status` clean. Found only by hand-querying the Actions API. ⚠️ **R-r restores who *can* release, not who *watches*.** `master` now has a workflow, so a red run at least appears on the branch a person works on — but **nothing pushes that anywhere**, and the failure mode was never "the run was on an obscure branch", it was "nobody looked". Both mitigations remain manual: ship-gate step 1 reproduces CI's invocation (§10.7e), and `curl -s "https://api.github.com/repos/Wulfic/mcMMO-Singleplayer/actions/runs?per_page=5"` after any push touching `src/**`, `build.gradle`, `gradle.properties` or `.github/workflows/release.yml`. ⚠️ Reading a run's **log** needs admin auth (`403` unauthenticated); the *conclusion* does not. **A real close needs a notification, not a workflow** |
| | | 🟡 **DOWNGRADED 2026-08-13 (§11.2), still open.** The manual `curl` is now `scripts/ci-watch.sh` — **ship-gate step 8**, the only gate downstream of the push, self-tested 6 ways and mutation-proven 4 ways. It reports four states rather than a boolean, because *"I could not see a run"* and *"the run passed"* are the two R11 conflates: exit **3** is *cannot tell* (sha not on the remote, or it changes paths the workflow builds on and no run exists). It derives *"no run expected"* by diffing the commit against `release.yml`'s own `paths:` filter, so a docs-only push is a **stated skip**, never a silent pass. ⚠️ **It is still a person running a command.** It shortened the check and made its failure mode explicit; it did not make anything unattended. Used in anger the same session — it is what verified all five Phase 11 releases. Reading a run's **log** now works too (`gh` is authenticated here as of 2026-08-13), which the row previously recorded as `403` |
| **R10** | **Two branches resolving to the same `minecraft_version`** | 🔴 **LIVE AGAIN as of R-r (2026-08-13) — it was dormant only because `master` could not release.** The tag-reaping sweep is back on `master`, so every branch releases on push and two branches on one version means **each run deletes the other's release**. `release.yml` detects the collision and emits a `::warning::` — deliberately not a failure, so it can never take down a legitimate release, which also means **nothing stops it**. Keep the one-band-one-version rule; it is now load-bearing rather than tidy |

---

## Carried debt

- [x] ✅ **The `.gitignore` hole — CLOSED 2026-08-13.** `mc/1.21.8` and `mc/1.21.10` lacked `.agent/`
      and `.github/`, so `git status` there listed `?? .agent/`, `?? .github/copilot-instructions.md`
      and `?? .github/skills/`, and a single `git add -A` would have committed the local memory tree
      that **R-n** forbids. Every commit on those bands had been staged by path to dodge it — a
      footgun, not a procedure. Seen live again this session while back-porting the floor fix.
      Fixed on both (`Backport-of: a63ea4305`); **all five branches now hash `.gitignore` identically
      (`b432715f0`)**.
      🔑 **Deliberately a PARTIAL back-port.** `a63ea4305` did two things — added the ignore lines
      *and* deleted `.github/` from version control (R-g, master-only). Only the first half belongs
      on a band. Verified after the change that the three tracked `.github/` files are **still
      tracked**: `.gitignore` has no effect on already-tracked paths, which is also why this was safe
      on `master` in the first place.
- [x] ✅ **8.1a.A4 — CLOSED 2026-08-13.** `mc/1.21.4` `MobOriginRestampSeamTest`, 6 tests, band-only
      (`master` has no such defect and no re-stamp injector, so the mcMMO half would fail there).
      Pins both halves: **Minecraft's** — an ASM call-graph walk proving the NBT read completes
      inside `EntityType#getEntityFromNbt`, which is what makes injecting at its RETURN
      *ordering-proof*; and **mcMMO's** — an injector selects that method at RETURN and nothing
      selects `Entity#readNbt`, the racy alternative.
      🔑 **The bytecode half needs a call-graph walk, not a string scan.** `getEntityFromNbt`'s own
      body never mentions `readNbt` — the read is in the `Consumer` lambda, a synthetic method
      called `method_17839` on this band, an unmapped yarn name that must never be hardcoded. The
      walk follows `invokedynamic` bootstrap handles.
      **Mutation-proven 4 ways:** revert the fix → source test fails · add a `readNbt` injector →
      racy-target test fails · aim the detector at an absent method → bytecode test fails · stop
      following `invokedynamic` → bytecode test fails.
      ⚠️⚠️ **Mutation 1 caught a vacuity hole in the guard's own first draft that review had not.**
      That mixin is mostly javadoc and the prose names every symbol asserted on, including
      `{@link #mcmmo$restampAfterNbtRead}` — deleting the whole injector left the mention behind and
      the "the method is gone" assertion still passed. Source assertions now run against
      comment-stripped text, and **the stripper has its own test**, because one returning `""` would
      make every `assertFalse` in the file pass for free.
- [x] ✅ **R9 — CLOSED 2026-08-13, as two fixes.** See the risk row; it was never one problem.

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
