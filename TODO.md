# Multi-Version Support — Development TODO

**Scope:** Fabric only. Targets: all stable `1.21.x` (12 releases) and the `26.x` line (4 stable
today, growing). NeoForge/Forge are explicitly **out of scope** — see "Deferred" at the bottom.

**Status:** Phases 0, 1, 2 and 3 are closed. **Phase 4 (Stonecutter) is struck** — R-a chose
branches, so there is no preprocessor to adopt; it is replaced by **Phase 4′**, below. The
cherry-pick discipline and risk R4 are both **done and landed on `master` first**, so all three band
branches inherit them.

🎉 **`mc/1.21.10` is cut and green, locally: 1704 tests / 0 failures / 0 skipped on 1.21.10,
`./gradlew build` exit 0, `boot-check.sh` PASSED, `mixin-allow-audit --mc 1.21.10 --check` PASSED,
`drift-audit.py` reports the band clean.** ⬜ **It is NOT pushed** — a push builds and releases
(Phase 4′.5).

✅ **2026-08-12 — 5.6b and 5.7 are DONE; Phase 5 is complete except the push (5.8).** The gameplay
half needed a harness that did not exist, because **every earning path needs a player**:
`scripts/gameplay-smoke.sh` drives a real `ServerPlayerEntity` (fabric-carpet `/player`) through
nine scenarios and scores them from `/mcstats` + the profile YAML. **29/29 on `1.21.11` AND on
`1.21.10`**, with the mod-less control run failing as it must. It also closes Phase 0's stated gap:
`/mcstats` is now proven to *render real data*, not merely to dispatch.

🔬 **2026-08-11 — the probe holes 5.2 found are CLOSED, and the manifest more than doubled:
266 → 566 records** (static constants were never indexed; `src/test/java` was never scanned).
**The 6 bands and their membership are unchanged** — but `1.21.8` and `1.21.5` each gained real,
previously-invisible port work (`EntityType#COPPER_GOLEM`, `SoundCategory#UI`). Fix that before
cutting either. Also fixed: **three comments that pinned the build to `1.21.11`**, two of which were
already false on `mc/1.21.10`.

✅ **2026-08-11 — the back-port sweep is DONE and `mc/1.21.10` is green again.** All three
outstanding `master` commits (probe holes, entity-by-id, the Spears gate) carry `Backport-of:`
trailers on the band; `drift-audit.py --master master` reports **4 propagated, 0 waived, 0 MISSING**
(after `--self-test` passed, so the auditor is known to still detect drift). Band: `./gradlew build`
exit 0, **1698 tests / 0 failures / 0 skipped**, `boot-check.sh … 1.21.10` PASSED,
`mixin-allow-audit --mc 1.21.10 --check` PASSED (61/61).

🎉 **The Spears ruling is IMPLEMENTED** (`dbd72590a` on master, `89402fb84` on the band) — see
Phase 5's ruling entry below. It is a **capability probe, not a version pin**: mcMMO asks the item
registry whether any of the seven spear ids exist, so one expression is correct on every band and
nothing has to be remembered at the next cut. **Observed firing in both directions on a real
server**, which is the part that matters:

| Band | boot log |
|---|---|
| `1.21.11` (master) | *"Version support: this Minecraft version has spear items, so the Spears skill is available."* |
| `1.21.10` (band) | *"… has none of the spear items (…), so the Spears skill is disabled — it gains no XP, procs nothing, and is not listed by /mcstats."* |

**Rule for this document:** a task is not checked off until its stated *acceptance criteria* pass.
"It compiles" is not acceptance criteria. Neither is "it looked right in game."

---

## RULINGS (owner, 2026-08-10) — decided, do not re-litigate

| # | Question | Ruling |
|---|---|---|
| R-a | Strategy | **Branch-per-band.** Overrides the single-tree recommendation below. The doc's own fallback clause applies: branch **per band, not per version**, and Phases 0–2 happen regardless. |
| R-b | Ship targets | **Floor at `1.21.5`.** Ship `1.21.5`–`1.21.11` + `26.1`, `26.1.1`, `26.1.2`, `26.2` = **11 targets**. Pre-`1.21.5` is dropped to dodge the component-API cliff (1.5). Still **probe all 16** so the cut is on record with data, not a hunch. <br>🧱 **Re-sequenced, not reduced** — `26.x` is unobfuscated and uses Mojang names, so those four collapse into **one band whose cost is a full yarn→official rename**. Near-term ship list is **7** (`1.21.5`–`1.21.11`); `26.x` is a separate later project. The `1.21.5` floor is unaffected. |
| R-e | `26.x` handling | **Own mini-project, gated behind Phases 1–2 and one completed ordinary back-port.** Not band 2, not absorbed into a sweep (Phase 6.3). |
| R-f | Release topology | **master = newest band.** `mc/**` is for OLDER bands only, cut by hand. `mc/1.21.11` deleted (local + remote); the workflow's auto-branch step is removed and replaced by a collision warning. |
| R-c | Phase 2 depth | **Full seal to zero, then the guard.** Work all 26 leak sites down before the guard lands. |
| P2-a | Phase 2 — the 6 core-logic leak sites | **Extend the `platform/` adapters until they are MC-free.** Explicitly NOT a bodily move into `fabric/`: that relabels the boundary instead of sealing it, and leaves skill logic band-fragile. |
| P2-b | Phase 2 — the alchemy cluster | **Seal it like the rest.** Build the potion adapter surface; all 6 go MC-free. No relocation, no waiver. |
| P2-c | Phase 2 — guard allowlist | **Zero exceptions, hard fail.** No escape hatch, no exempt list. *An allowlist is where sealed boundaries go to rot.* |
| R-d | Playtest | **Keeps running on master builds.** Master stays green + bootable at every commit; no forensic gap in `advancements/<uuid>.json`. |

Because R-a was decided up front, **Phase 3 is closed** and Phase 4 is replaced (see Phase 4′).

### ⚠️ R-a is cheaper than the doc assumed — the infrastructure already exists

`.github/workflows/release.yml` **already implements branch-per-MC-version** and predates this
document:

- Releases build from `master` **and** `mc/**` (line 34–36).
- Step *"Ensure Minecraft release branch"* (line 180) creates `mc/<minecraft_version>` the first
  time a new `minecraft_version` is built, and **never overwrites an existing one** — the comment at
  line 21 states outright that this is *"so back-port commits on old lines are safe."*
- Tags are already `mc<MCVER>-v<version>`; exactly one release is kept per MC line (line 160).

So Phase 4′ is mostly *configuration*, not construction. Phase 7.1's "matrix over bands" is largely
already satisfied — each band branch triggers its own run.

### ⚠️⚠️ …but it ships an armed footgun that R-a triggers

Two live problems, found 2026-08-10:

1. **`mc/1.21.11` was 90 commits stale** — created 2026-07-23 at `691f3631` ("test2") and never
   updated, because the "ensure branch" step only creates a branch when it is *absent*. Had anyone
   pushed to it, it would have built July-23 code and **deleted master's current release** for the
   1.21.11 line. Fast-forwarded to `90424f239` locally; the remote push is gated on the topology
   call below.
2. **`master` and `mc/1.21.11` both resolve to `minecraft_version=1.21.11`**, so both tag
   `mc1.21.11-v*` and both run the *"delete previous release on this Minecraft line"* sweep. They
   fight over one release slot: **whichever pushes last deletes the other's release.** Dormant only
   because `mc/**` is never pushed today. R-a arms it.

- [x] **RESOLVED — release topology → (A), per R-f.** `mc/1.21.11` deleted local + remote (only
      `master` exists now); the auto-creating *"Ensure Minecraft release branch"* step is **removed**
      from `release.yml` and replaced by a collision warning (line ~197–215) that fires if a
      `mc/<newest>` branch ever reappears claiming master's own Minecraft line.
      The original options, for the record:
      - **(A) master = newest band.** `mc/1.21.11` is deleted; `mc/**` exists only for *older* bands.
        Master keeps releasing exactly as today. Requires disabling the "ensure branch" step, or it
        recreates `mc/1.21.11` on the next push.
      - **(B) master = integration trunk, never releases.** Every band including the newest lives on
        `mc/**`; drop `master` from the workflow's `branches:` trigger. Conceptually clean; every
        release becomes a `master` → `mc/<newest>` merge.

### The cherry-pick discipline R-a requires (3.3 — nothing else prevents drift)

The doc's core objection to branches stands and is not answered by tooling: **11 of the last 12
issue fixes were version-agnostic logic bugs**, and under branch-per-band each becomes N
applications whose failure mode is silent. Mitigations, all mandatory — **✅ all four DONE**,
commit `0560054dc`, landed before the first branch was cut:

- [x] Fixes land on `master` **first**, always. A fix authored directly on a band branch is a defect.
      → written into `AGENTS.md`, so it binds every future session rather than living only here.
- [x] Every band-propagation commit carries a `Backport-of: <sha>` trailer, making
      `git log --grep='Backport-of: <sha>'` the mechanical answer to *"did this reach every band?"*
      → plus `Backport-not-needed: <reason>` for a `master` commit that genuinely must not
      propagate. **An opt-out, not an allowlist**: it lives in the commit that made the decision and
      cannot be applied retroactively to one somebody merely forgot.
- [x] A drift audit script that, for each `master` fix commit, reports which band branches lack a
      matching `Backport-of` trailer. **Without this, R-a has no drift detection at all.**
      → `scripts/drift-audit.py`. Also flags a `Backport-of` naming no real `master` commit — a
      typo'd sha otherwise buys silent credit for nothing. `gradle.properties` is excluded by
      construction: a band pins its own `minecraft_version`, so toolchain bumps must not read as
      missing there.
- [x] Run the drift audit in CI on a schedule, not by memory.
      → `.github/workflows/drift-audit.yml`, weekly. Deliberately **not** per-push: drift is
      slow-moving, and a per-push run would fail every PR between "fix lands" and "fix is
      back-ported" until everyone learned to ignore it.

🔑🔑 **The converse check is the load-bearing part, and it had to be synthetic.** With zero band
branches a live run has nothing to compare — and *"no drift"* is exactly what a completely broken
auditor also prints. So `--self-test` builds a throwaway repo containing known drift and asserts the
auditor detects the forgotten fix, **clears the back-ported one** (without that, a script reporting
everything as drift also passes), ignores docs-only and waived commits, and flags a typo'd trailer.
CI runs the self-test *before* the real audit. For the same reason the live run **refuses to report
success** when it finds zero band branches, and `--require-bands N` makes that a hard failure once
bands exist — so a renamed branch or a shallow fetch cannot degrade the scheduled run into a green
no-op.

- [ ] **Raise `--require-bands` to the live band count** in `drift-audit.yml` as each branch is cut
      (1 after `mc/1.21.10`, 2 after `mc/1.21.8`, 3 after `mc/1.21.5`).

---

## Target matrix (verified 2026-08-10 against meta.fabricmc.net)

| Line | Stable releases |
|---|---|
| 1.21 | `1.21`, `1.21.1`, `1.21.2`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.6`, `1.21.7`, `1.21.8`, `1.21.9`, `1.21.10`, `1.21.11` |
| 26 | `26.1`, `26.1.1`, `26.1.2`, `26.2` (`26.3` in snapshot) |

Mojang moved to year-based numbering (`YY.drop.patch`) in 2026; `26.x` is the **continuation** of
`1.21.x`, not a parallel line. `26.1 > 1.21.11` sorts correctly under semver comparison, so version
predicates need no special-casing. Current shipping target is `1.21.11`.

All 16 version strings were re-confirmed present and `stable` in `/v2/versions/game` on 2026-08-10.

### 🧱 `26.x` IS THE HARD BAND BOUNDARY — Minecraft is unobfuscated from `26.1`

⚠️ **Correction (2026-08-11).** An earlier revision of this section concluded `26.x` was
"unbuildable by anyone". **That was wrong.** Every measurement below is accurate; the *inference*
was not. Mappings are absent because from `26.1` Minecraft **ships unobfuscated** and mappings are
no longer needed — not because tooling is missing. Recording the error deliberately: the raw data
was right and the conclusion drawn from it was still false, which is this project's most persistent
failure mode.

**Verified against the real artifact** (`26.2` server jar, `piston-data`, 2026-08-11): the inner
`META-INF/versions/26.2/server-26.2.jar` holds **7,434 `net/minecraft/*` classes and zero
obfuscated-looking names.** Mojang's own names ship in the jar.

**But they are Mojang names, not yarn names — and the two schemes differ structurally:**

| | official (`26.x`) | yarn (what this mod is written in) |
|---|---|---|
| item stack | `net.minecraft.world.item.ItemStack` | `net.minecraft.item.ItemStack` |
| server player | `net.minecraft.server.level.ServerPlayer` | `net.minecraft.server.network.ServerPlayerEntity` |
| food | `net.minecraft.world.food.FoodProperties` | `net.minecraft.component.type.FoodComponent` |
| consumable | `net.minecraft.world.item.component.Consumable` | `…type.ConsumableComponent` |

So the `26.x` port is a **wholesale rename of the entire MC-facing surface** — all 164 imports, all
42 mixins, all 44 method selectors and 19 `@At` descriptors, plus every MC type named in a method
body. It is not a directive and not a back-port; it is its own project, and Phase 6.3's rule
applies with force: **size it separately, never absorb it into a sweep.**

🔑🔑 **This vindicates R-a.** A yarn-named tree and a Mojang-named tree cannot be reconciled by
preprocessor directives — the identifiers differ on essentially every MC-touching line. Stonecutter
could not have bridged this; a branch is the only honest representation. The single-tree
recommendation this document opens with was written without knowing that, and R-a is right.

- [ ] `scripts/mc-surface.txt` is **yarn-named and therefore does not apply to the `26.x` band.**
      A translation table is needed before that band can be probed at all.
      - 🔑 Likely cheapest route: yarn's `v2` mappings carry `official → intermediary → named`
        columns, so a yarn→official table can be *derived* for `1.21.11` and largely reused, rather
        than hand-written. Confirm before budgeting the rename as manual.
- [ ] Toolchain: `26.x` needs a newer Loom than our **1.17.13**. Stable is **1.17.19**; the
      **`1.18.0-alpha.*`** line is the active track (alpha.15, updated 2026-08-10 — it moves daily).
      **Confirm exact plugin coordinates when the band is actually attempted; do not pin from this
      note, it will be stale.**
- [ ] Treat `26.x` as a **separate, later mini-project**, gated behind Phases 1–2 and at least one
      completed ordinary back-port. Do not start it as band 2.

### The measurements (all correct; only the old conclusion was wrong)

Measured 2026-08-10:

| Probe | `1.21.x` | `26.1` – `26.2` |
|---|---|---|
| `meta.fabricmc.net/v2/versions/yarn/<v>` | populated | **`[]` — empty for all four** |
| Newest yarn in `maven.fabricmc.net` metadata | `1.21.11+build.6` (lastUpdated 2026-05-27) | **nothing newer exists** |
| `maven.fabricmc.net/.../yarn/26.2+build.1` | — | **HTTP 404** |
| `/v2/versions/intermediary/<v>` | real (`intermediary:1.21.11`) | **`0.0.0` sentinel** |
| Mojang `version_manifest_v2` → `downloads` keys | `client`, **`client_mappings`**, `server`, **`server_mappings`** | **`client`, `server` only** |

**Why:** from `26.1` Minecraft is unobfuscated, so Mojang no longer needs to publish obfuscation
maps, Fabric has dropped yarn, and yarn/intermediary stopped updating after `1.21.11`. The absence
above is a *consequence of deobfuscation*, not a gap in tooling.

⚠️ Note `fabric-loader 0.19.3` lists `26.2` as supported, and that listing is **correct** — with an
unobfuscated jar there is nothing to remap. What does *not* work is our specific build:
`build.gradle:30` pins `net.fabricmc:yarn:${yarn_mappings}:v2`, which 404s for every `26.x`, and
Loom `1.17.13` predates the no-remap path.

⚠️ The Phase 4 claim that Stonecutter "is already used in the wild against `26.2-fabric` targets, so
the new version scheme is supported" is **true but misleading**: the version scheme is supported and
irrelevant. The obstacle was never the version *string*, it is that `26.x` uses a different
*naming* scheme for every Minecraft identifier. Second premise in this document to survive as
written but fail on meaning (cf. the 162-vs-164 import count).

**Consequence for R-b:** the `1.21.5` floor stands unchanged. The four `26.x` targets are real and
reachable, but they are **one band with a rename-sized cost**, not four cheap tail entries. Near-term
ship list is **7** (`1.21.5` – `1.21.11`); `26.x` follows as its own project.

---

## Strategy decision (OPEN — gate at Phase 3)

### Recommendation: single tree + Stonecutter. Not branch-per-version.

Measured on this repo, 2026-08-10:

| Fact | Value | Consequence for branch-per-version |
|---|---|---|
| Files in `src/main/java` | 282 | — |
| Files with **zero** `net.minecraft` imports | **175 (62%)** | byte-identical on every branch = pure duplication |
| Resource files (locale, yml, assets) | **357** | byte-identical on every branch |
| Test files | 140, of which **101 need no MC** | byte-identical on every branch |
| Last 40 commits touching `src/main/java` | **32 (80%)** | every one must be replicated per branch |
| Last 40 commits, resource/docs only | 8 | locale + yml + wiki — also apply to every branch |

Effectively **100% of ongoing work has to reach every branch.** The GitHub issue queue is live
(#1–#12 fixed in recent weeks); this is not a frozen codebase where branches quietly diverge and
nobody cares. It is under active gameplay bugfixing, which is the single worst case for
branch-per-version, because cherry-pick drift is *silent* — a fix that lands on 1.21.11 and gets
missed on 26.2 produces no error, just a bug that came back.

With one tree, a fix to `CombatUtils` or a skill manager lands **once** and rebuilds for every band.
Only genuine per-version differences carry a directive.

### Honest counterpoint (do not skip this)

25 of those 40 commits touched `fabric/` or `platform/` — i.e. much of the real work *is* in
version-fragile code. That work is harder under **either** model. Stonecutter does not make MC API
churn free; it makes it a directive instead of a manual re-port. The win is that the other 62% of
the codebase stops being copied around.

### If you still want branches — do this at minimum

Branch **per band, not per version** (see Phase 1). That is ~4–6 branches instead of 16, and it is
the difference between viable and not. Do Phases 0–2 regardless; they are strategy-independent and
required either way.

---

## Support policy — when a branch IS the right call

> Answers: *"how do we bugfix old versions without breaking new ones, years down the line?"*

### The bugs this project actually gets are not version-specific

Measured over the last 40 commits, 2026-08-10:

| | Count |
|---|---|
| Commits touching `fabric/mixin/` (the genuinely version-fragile surface) | **6 of 40** |
| Commits touching listeners or `platform/` | 19 |
| Commits touching only MC-free logic | 7 |
| **Recent issue-driven fixes touching zero mixins** | **11 of 12** |

#11 (dropped conjunct in `canUseSubSkill`), #7 (Spears wired to nothing), #3 (Husbandry payout cap),
#10 (per-skill toggle), #2 (pet teleport follow), the off-hand torch defect — every one is a logic
bug **present identically on all 16 versions**. Under branch-per-band those 11 fixes become ~55
applications, and the failure mode is silent: *"fixed on 1.21.1, forgot 26.2"* raises no error and
ships as a regression that came back.

**Branches do not protect old versions. They hide whether the old version was ever fixed.**

### The three mechanisms that protect old versions in a single tree

1. **CI matrix = mechanical proof, per commit.** Every push compiles and tests every band. A change
   that breaks `26.2` while fixing `1.21.1` goes red *before merge*. Branch-per-version provides no
   such signal at all — the first report comes from a user.
2. **Directives are explicit and greppable.** `//? if <1.21.5` states what is version-specific and
   why, in the file, to whoever reads it in 2028. Branch divergence is a diff nobody runs.
3. **Phase 2 caps the blast radius.** Only ~107 files can hold a version-specific bug, and the truly
   fragile part is 42 mixins — a surface touched 6 times in 40 commits.

### Where the single tree genuinely degrades

- **A band drops out of the CI matrix.** Keep 16 targets forever and someone eventually trims the
  matrix for build time; that band then rots silently. Identical failure to branches, just later.
- **A band diverges past readability.** A file that is 40% preprocessor is worse than a separate
  file. The early-`1.21.x` component-API cliff (see 1.5) is the live candidate.

### Ruling: branch at END-OF-LIFE, not at release

- [ ] Adopt a written support policy with three tiers:
      - **Active** — in the single tree, in the CI matrix, gets every fix and every feature.
      - **Maintenance** — still in the tree and matrix, critical fixes only, no new skills.
      - **Frozen (EOL)** — cut to its own branch `legacy/<band>`, removed from the matrix, tagged.
        No further work expected. This is the *only* branch this project should ever cut per version.
- [ ] Promotion to Frozen requires an explicit decision + a memory record, never silent neglect
- [ ] **Trigger to cut a band loose early:** its directive density in any single file exceeds ~25%,
      or its band-specific symbol count from `BAND_TABLE.md` exceeds 3× the median band. Both are
      measurable — check them at the end of each Phase 6 band rather than arguing about it.
- [ ] Hard cap: if the CI matrix exceeds ~30 min wall clock, a band moves to Maintenance or Frozen.
      Never solve it by dropping a band from the matrix while still claiming to support it.

- [ ] **DECISION RECORD** — once chosen, write the ruling and its date into memory
      (`multiversion-strategy-decision`) and link it from `MEMORY.md`. Do not leave this implicit.

---

## Phase 0 — Freeze the known-good build

A live playtest is running on a PrismLauncher instance. Do not start a build refactor without a
rollback point.

- [x] 0.1 Confirm working tree clean and full suite green on `1.21.11`
      → tree clean, **1619 tests / 0 failures / 0 skipped**, `./gradlew build` exit 0.
- [x] 0.2 Tag the current commit `v1.21.11-baseline`; push the tag
      → annotated tag `dc1a3926` → `90424f239`, pushed to origin.
      🔑 Deliberately **not** named `mc1.21.11-v*`: that prefix is what the release workflow's
      "delete previous release on this Minecraft line" sweep reaps (release.yml:167). A baseline
      tag matching the release convention would eventually delete itself.
- [x] 0.3 Build and archive the release jar for that tag outside the repo
      → `C:\Users\tyler\mcmmo-baseline-2026-08-10\jar\` — `mcmmo-2.2.050-baseline.jar`
      (`99ece597…`) + sources + `SHA256SUMS.txt`.
- [x] 0.4 Back up the live PrismLauncher instance's `players/*.yml` + config directory
      → 16 files under `…\live-instance\`: all 11 `config/mcmmo/*.yml`, the save's
      `mcmmo/players/<uuid>.yml` + `placed_blocks.dat`, **and** `advancements/` + `stats/`
      (the forensic channel per `live-playtest-instance-forensics`), plus the jar the owner is
      actually running (`build.17`).

**Acceptance:** the tagged jar boots a world and `/mcstats` renders, from the archived artifact —
not from a fresh build.

⚠️ **The acceptance criterion as written is not satisfiable headlessly.** `McMMOCommands.stats()`
opens with `source.getPlayerOrThrow()`, so `/mcstats` **cannot execute from the server console** —
it needs a connected player. Substituted proof, on a standalone Fabric server (`fabric-server-launch`
0.19.3 / installer 1.1.2) booting the **archived jar matched by sha256** (`99ece597…`), not a rebuild:

- [x] loads and runs: `Done (5.084s)`, **0 ERROR, 0 mixin failures**, clean `Stopping server`
- [x] mcMMO initialises: *"mcMMO (Fabric) initializing"* → *"configs loaded"* → the Smelting table
      builds (*"20 ore block(s) … eligible for the Understanding the Art vanilla-XP boost"*), and the
      shutdown hook runs (*"saving and cleaning up data … Finished save operation"*)
- [x] `/mcmmo` **renders real output**: *"mcMMO (Fabric singleplayer port) / Use /mcstats to view
      your skills."*
- [x] `/mcstats` and `/mcstats mining` both return *"A player is required to run this command here"*
      — not `Unknown command`. That is `getPlayerOrThrow()` throwing, which proves each command
      registered **and** dispatched into its executor.
- [x] `help mcstats` lists `/mcstats [<skill>]`, proving the optional-argument node registered too.
- Corroborating: master `90424f239` differs from the boot-verified `4b36d1b16` by **docs/wiki only**
      (zero files under `src/`, `build.gradle`, `gradle.properties`).

🔑 **Reusable — promote to `scripts/boot-check.sh` before the first back-port.** Phases 5.6/6.2 need
exactly this per band. Two constraints are load-bearing:

1. ⚠️⚠️ **Never feed the server JVM from a `mkfifo` under git-bash.** MSYS emulates the FIFO and a
   native Win32 JVM cannot read it: the console handler dies at once with
   `java.io.IOException: The handle is invalid`, **the server keeps running and silently discards
   every command**. The first run of this harness hit exactly that — it still reached `Done (` and
   still logged 0 errors, so **the failure was indistinguishable from success.** Pipe `tail -f` on a
   regular file instead.
2. 🔑 **Send a deliberately-invalid canary command first and assert it is rejected in the log.**
   That is the converse check that the console is live; without it, a green boot proves nothing about
   any command that follows. This project's standing rule — *a guard that has never failed is not
   known to work* — applies to test harnesses too.

---

## Phase 1 — Band discovery (do this FIRST; everything downstream is sized by it)

A **band** = a contiguous range of MC versions across which mcMMO's touched surface is identical.
The touched surface is finite and already counted:

- **162 distinct `net.minecraft` symbols** imported across `src/main/java`
- **53 mixin `method =` targets**; 29 mixin files use full `Lnet/minecraft/...` descriptors
- **22 `@At` `target =` constants** pointing into MC internals

≈215 symbols total. Whether 1.21.6 and 1.21.7 are one band is a **lookup, not a judgment call.**

> Prior burn: `issue-7` — a stale MC fact was written down as the *reason* for absent code and
> copied into four docs, so all four agreed and all four were wrong. Resolve every symbol against a
> real jar. Guess nothing.

- [x] 1.1 Extract the symbol surface into a machine-readable manifest
      → `scripts/extract-mc-surface.py` (regeneratable; `--check` enforces the acceptance criteria).
      - [x] 1.1a the `import net.minecraft.*` symbols out of `src/main/java`
            → **162 `CLASS` + 2 `STATICMEMBER` = 164 import lines.** ⚠️ I first reported "164, the
            doc's 162 is stale" — **the doc was right and I was wrong.** Two of the 164 are
            `import static …CommandManager.{literal,argument}`, which are *method* imports, not
            types. Filing them as classes made the probe report them ABSENT on **every** version
            including the one the mod compiles against. 🔑 *A static import is not a class; counting
            import lines is not counting types.*
      - [x] 1.1b Every mixin `@Mixin` target, `method =` value, `@At target =` constant, plus
            `@Accessor`/`@Invoker` bindings (not required by the doc, same fragility, free to collect)
      - [x] 1.1c Emit `scripts/mc-surface.txt` — `TYPE<TAB>VALUE`, one record per line
      - **Acceptance: PASS — 266 records** (≥215 required), all 42 mixin files contribute a target.
        `CLASS 164 · METHOD 44 · MIXINCLASS 37 · ATTARGET 19 · ACCESSOR 2`.
        🔁 **Superseded 2026-08-11 — now 566 records**, after 5.2 found the manifest was blind to
        static constants and to `src/test/java` entirely:
        `CLASS 175 · STATICFIELD 287 · METHOD 44 · MIXINCLASS 37 · ATTARGET 19 · ACCESSOR 2 ·
        STATICMEMBER 2`.
        *37 < 42 is correct, not a miss: four `LivingEntity*Mixin` files share one target class.*
      - ⚠️ Two parsing traps the extractor has to handle, both present in this codebase:
        **(a)** `@Mixin` appears in both simple-name and fully-qualified form
        (`@Mixin(net.minecraft.block.BeehiveBlock.class)`), so simple names must be resolved through
        that file's own import list; **(b)** selectors are frequently **truncated descriptors**
        (`"dropExperience(Lnet/minecraft/server/world/ServerWorld;"`) because mixin prefix-matches —
        they are emitted verbatim, since that exact string is what must keep matching. Comments are
        stripped first: these files quote real descriptors in javadoc constantly.

- [x] 1.2 Generalise `scripts/javap-mc.sh` across versions
      - [x] Version is recognised **by shape** anywhere in the arg list (`1.21.11`, `26.2`, `26.1.1`),
            so the old `javap-mc.sh <class>` form still works and `-p` composes freely
      - [x] Default now read from `gradle.properties`, **not hardcoded** — the hardcoded `1.21.11`
            at old line 15 was the whole reason this was a TODO item
      - [x] Absent jar ⇒ exit 1, naming the gradle invocation *and* the meta URL for the yarn build
      - [x] `--list-versions` reports what is actually cached
      - **Acceptance: PASS.** Verified against the two versions cached locally (`1.21.1`, `1.21.11`);
        `1.21.8` correctly fails loudly with exit 1.
      - ⚠️ **The trailing `-` in the jar glob is load-bearing**: without it `1.21.1` also matches the
        `1.21.11` directory. Explicitly regression-tested, and re-checked after matching. This same
        prefix hazard is what makes the release workflow's tag-reaping glob safe (release.yml:167) —
        it keeps recurring in this project and is never obvious.
      - 🔑 Free finding: a `1.21.1` merged jar (yarn `1.21.1+build.3`) was **already in the Loom
        cache**, giving a second probe point for free — and one *below* the R-b floor, useful for
        evidencing 1.5. `ItemStack` already differs between them: `MAP_CODEC` on 1.21.11 vs
        `ITEM_CODEC: Codec<RegistryEntry<Item>>` on 1.21.1.

- [ ] 1.3 Resolve all 16 versions → **revised to 12**: the four `26.x` have no mappings to resolve
        against (see blocker). Probing them is not "expensive", it is *impossible*.
      - [x] Look up the correct `yarn_mappings` build for each version from meta.fabricmc.net —
            **do not extrapolate the build number from the version**. Confirmed emphatically: the
            builds are `9, 3, 1, 2, 8, 1, 1, 8, 1, 1, 3, 6` for `1.21` → `1.21.11`. There is no
            pattern; `1.21.4+build.8` sits between two `build.1`s.

            | MC | yarn | MC | yarn |
            |---|---|---|---|
            | `1.21` | `1.21+build.9` | `1.21.6` | `1.21.6+build.1` |
            | `1.21.1` | `1.21.1+build.3` | `1.21.7` | `1.21.7+build.8` |
            | `1.21.2` | `1.21.2+build.1` | `1.21.8` | `1.21.8+build.1` |
            | `1.21.3` | `1.21.3+build.2` | `1.21.9` | `1.21.9+build.1` |
            | `1.21.4` | `1.21.4+build.8` | `1.21.10` | `1.21.10+build.3` |
            | `1.21.5` | `1.21.5+build.1` | `1.21.11` | `1.21.11+build.6` |
            | | | `26.1`–`26.2` | **none exist** |
      - [x] `scripts/probe-bands.py` (Python, not `.sh` — the javap parsing and hierarchy walk are
            not shell work). All 12 resolved via a throwaway Loom project, **12 OK / 0 FAILED**.
      - [x] Emit `plans/BAND_TABLE.md` + `plans/BAND_TABLE.json` (raw cache, so re-analysis is
            instant instead of a 20-minute re-probe)
      - **Acceptance: PASS — zero UNKNOWN rows.** All 266 records resolve to a definite state on
        all 12 versions. 🔁 Re-run 2026-08-11 against the repaired manifest: **all 566** do.

      🔑🔑 **The probe carries its own converse check and it is the reason to trust the output.**
      `--control 1.21.11` asserts that the version the mod demonstrably compiles and boots against
      resolves **100% of records**; any ABSENT there is a bug in the probe, not a fact about
      Minecraft, and the run exits 3. **The first draft failed it with 6 false ABSENTs**, all real
      defects: static member imports filed as classes, nested types written `Outer.Inner` instead
      of `Outer$Inner`, and — the subtle one — **`javap` never lists inherited members**, so
      `BlockState#onExploded` (declared on `AbstractBlock.AbstractBlockState`) and
      `WorldAccess#setBlockState` (from `ModifiableWorld`) read as absent until the probe walked
      the supertype closure. Without the control check those 6 would have shipped as "port work"
      on every band. *A probe with no known-good baseline is indistinguishable from a broken one.*

- [x] 1.4 Collapse into bands and publish the ruling
      - [x] **6 bands** — inside the expected 4–6, nowhere near the 12+ escalation trigger (R1 clear)

      | Band | Versions | Records differing from `1.21.11` | In scope under R-b? |
      |---|---|---|---|
      | `1.21.1` | `1.21`, `1.21.1` | **54** (was 35) | ❌ below floor |
      | `1.21.4` | `1.21.2`, `1.21.3`, `1.21.4` | **18** (was 15) | ❌ below floor |
      | `1.21.5` | `1.21.5` | **12** (was 10) | ✅ |
      | `1.21.8` | `1.21.6`, `1.21.7`, `1.21.8` | **9** (was 8) | ✅ |
      | `1.21.10` | `1.21.9`, `1.21.10` | **2** (unchanged) | ✅ |
      | `1.21.11` | `1.21.11` | 0 — this is `master` | ✅ |

      🔁 **Re-cut 2026-08-11 against the repaired 566-record manifest** (see 5.2 — the probe was
      missing static constants and the whole test tree). **The 6 bands and their membership did not
      change**; only the per-band cost rose. The boundaries were right, the counts were low.

      🎉 **Consequence: only THREE band branches to cut** — `mc/1.21.10`, `mc/1.21.8`, `mc/1.21.5`
      — and the largest carries **12 changed records out of 566** (was 10 of 266). Under R-a that is
      three back-ports of low-double-digit symbol counts, not the sprawl the risk register feared.
      - [x] Per-boundary changed symbols are enumerated per band in `BAND_TABLE.md` §"Phase 1.4",
            split into **absent** vs **signature-changed**. The signature-changed rows are the
            dangerous ones: they compile-break rather than resolve-fail, and a present/absent-only
            probe passes them silently.
      - [x] Order for Phase 6.1, cheapest first: **`1.21.10` (2) → `1.21.8` (9) → `1.21.5` (12)**
            — the ordering is unchanged by the re-cut.

- [x] 1.5 Sanity-check the suspected cliffs
      - [x] **Component API cliff FOUND, and it is at `1.21.2` — not `1.21.5`.**
            `ConsumableComponent` and, decisively, **`FoodComponent#onConsume`** — the exact eating
            seam behind Farmer's/Fisherman's Diet that this item predicted — are **ABSENT on `1.21`
            and `1.21.1`, PRESENT from `1.21.2` onward.** The prediction was correct in kind and
            wrong in location.
            - ⇒ **R-b's `1.21.5` floor is safe but more conservative than the data requires.** The
              reimplementation risk lives entirely in the `1.21`/`1.21.1` band, which is already
              out of scope. `1.21.2`–`1.21.4` would cost 15 records and need **no** eating-seam
              rewrite — a floor of `1.21.2` is defensible if wider support is ever wanted.
            - Same boundary also gates `LivingEntity#travelGliding`, `#forEachShearedItem`,
              `ExplosionImpl`, `PlayerInput`, `AbstractBoatEntity`, `EntityConversionContext`.
      - [x] **`1.21.11` → `26.1` is NOT an ordinary bump** — it is the unobfuscation/rename
            boundary. See the `26.x` section above. This item's own phrasing ("verify it is an
            ordinary bump") was the assumption that hid it.

**Phase 1 output is the deliverable that makes the rest of this document estimable.**
✅ **Delivered:** `plans/BAND_TABLE.md`.

---

## Phase 2 — Seal the platform boundary (strategy-independent) — ✅ **COMPLETE**

26 files leaked `net.minecraft` outside `fabric/` and `platform/`. Under a single tree, each leak is a
place a preprocessor directive would end up inside skill logic. Under branches, each is a file that
diverges per branch. Both are bad. **All 26 are sealed and `PlatformBoundaryGuardTest` holds the
line at zero.**

🔑 **The rule that decided how the whole phase was worked: relocation does not shrink a band-fragile
surface — only extraction does.** Growing `PlatformBlock`/`PlatformLivingEntity` until a file
compiles MC-free *in place* just moves the same divergent lines into wrapper methods; the count is
unchanged. That is what ruling P2-d encodes, and it is why 4b-2 pulled 14 predicates out into
`util/BlockRules` and why slice 5 built `PotionSpec` instead of wrapping `ItemStack`.

**The 175 MC-free files, the 357 resources, and the 101 MC-free tests must stay clean, permanently.**

- [x] 2.1 Triage the 26 leak sites — for each, decide *adapter method* vs *move into `fabric/`*
      → Triaged into **5 slices** in the `phase2-platform-boundary-seal` memory. The owner's rulings
      P2-a…P2-e scope it; **P2-d is the operative one: extract the logic, THEN relocate the
      remainder.**

      ⚠️⚠️ **P2-a's premise did not survive tracing.** It was written believing the six "core logic"
      files had MC-typed callers in the skill managers. **Not one did** — every apparent call in
      `HerbalismManager`, `MiningManager`, `WoodcuttingManager`, `TreasureConfig`, `HunterManager`,
      `HusbandryListener` and `SmeltingListener` is a `{@code …}` **javadoc mention**.
      🔑🔑 *`grep -rl '<Symbol>\.'` counts comments as usage.* Four of the five load-bearing claims
      behind a ruling were comments.

- [x] 2.2 Work the list to zero. Order: `util/` first (widest blast radius), `alchemy` last (biggest
      cluster — `PotionConfig` + `AlchemyPotion` + `PotionStage` + `PotionUtil` + the two managers
      move together or not at all)

      **26 → 0 leak sites.** Every commit green + bootable, per R-d.

      | Slice | Commit | Sites | Suite |
      |---|---|---|---|
      | 1 — `SoundCategory` (a pure enum leak: 5 files imported MC to name a volume slider) | `f14a20711` | 26 → 21 | 1621 |
      | 2 — `Text` | `35dad2003` | 21 → 16 | 1621 |
      | 3 — `ItemStack`/`Registries` | `04c4d0f88` | 16 → 14 | 1621 |
      | 4a — the two MC-plumbing files (`McMMOCommands` → `fabric/commands/`, `ExperienceBarWrapper` → `platform/`) | `9dc2140c1` | 14 → 12 | 1621 |
      | 4b-1 — the five thin adapters, relocated | `1e1f7bc3f` | 12 → 7 | 1621 |
      | 4b-2 — `BlockRules` extracted, bridge relocated | `47a4910c5` | 7 → 6 | **1637** |
      | 5 — the alchemy cluster | `1d951ac41` | **6 → 0** | **1664** |

      🔑 **Relocation does not shrink a band-fragile surface — only extraction does.** Growing
      `PlatformBlock`/`PlatformLivingEntity` until a file compiles MC-free *in place* moves the same
      divergent lines into wrapper methods; the count is unchanged. That argument is what decided
      P2-d, and it is the reason 4b-2 pulled 14 predicates plus the whole placed-block policy out of
      `BlockUtils` into the MC-free `util/BlockRules` rather than just moving the file.
      Measurable payoff: **11 assertions left the `fabric-loader-junit` harness**, which Phase 4.4
      pays at ~53s of `Bootstrap.initialize()` per fork **per band**.

      ⚠️⚠️ **Two things a seal must never do to semantics**, both hit in 4b-2:
      1. **Do not convert a block *identity* check into an id-path comparison.**
         `isOf(Blocks.SNOW)` / `== Blocks.OBSIDIAN` would silently broaden across namespaces as
         `"snow".equals(idPath)`. Both stayed MC-typed, one line each. (The surrounding
         `MaterialMapStore` whitelists are already path-keyed — that existing design was deliberately
         not relitigated. The rule is about *changing* an exact check, not about paths.)
      2. **Do not let an argument be evaluated eagerly across the new boundary.**
         `isIn(TagKey)` **throws** while tags are unbound, and the Hylian flower/bush arms return
         before touching it — so two plain `boolean`s would have crashed every Hylian Luck break.
         They cross as `BooleanSupplier`s. **Second occurrence of this exact trap**
         (cf. `ParticleEffectUtils#spawnAtEyes`).

      ⚠️ Moving a class out of its package widens access modifiers:
      `WoodcuttingManager#processTreeFellerXPGains` had to go `public`. Documented at the
      declaration — keeping an MC-typed class in an MC-free package to preserve an access modifier is
      the trade this phase exists to refuse.

      **Slice 5 shipped as scoped**: MC-free `PotionSpec`/`EffectSpec`/`PotionForm` records, the old
      `util/PotionUtil` split into MC-free `util/PotionNames` (legacy Bukkit names + the
      `strong_`/`long_`/`water` prefix predicates) and `platform/Potions` (registry lookups,
      `specOf`, `applyContents`), plus a new `platform/PlatformInventory` view so the brewer still
      mutates the brewing stand's own `DefaultedList`. `PotionStage` is now pure arithmetic over
      `PotionSpec`.
      - 🔑 **`PotionSpec` carries the NAMESPACED base potion id, not the bare path.** The comparison
        it replaced was `RegistryEntry` identity; bare-path equality would let another mod's
        `swiftness` match vanilla's. The prefix predicates still read the path, as before.
      - 🔑 **The queried item's spec is read once per `getPotion`, not once per candidate.** Vanilla
        calls `BrewingStandBlockEntity#canCraft` from `tick`, which is what `isValidBrew` hangs off —
        a per-candidate registry lookup would be paid every tick of every brew.
      - Payoff: **+27 MC-free assertions** (`PotionNamesTest`, `PotionSpecStageTest`) that need no
        `McTestRegistries.bootstrap()`, covering stage combinations the shipped `potions.yml` does
        not contain and the config-driven test could never reach. Feeds Phase 4.4's test split.
- [x] 2.3 **Add the build guard.** → `PlatformBoundaryGuardTest`, commit `40dd525c0`.
      - **Scope: `src/main/java` only** (ruling P2-e), matching this section's own acceptance
        criterion. Test-side MC imports stay legal — `McTestRegistries` and
        `McRegistryBootstrapProbeTest` are registry-bootstrap harnesses and *cannot* be MC-free, so
        policing `src/test/java` would immediately require the exempt list P2-c forbids.
      - ⚠️⚠️ **It polices TWO forms, because the import is only the obvious one.** A
        fully-qualified `net.minecraft.Foo` written inline needs no import at all. Mutation-tested:
        `static final net.minecraft.item.ItemStack SNEAKY` in `PotionStage` **compiles and passes an
        import-only guard.** Zero such references exist today, so closing it cost nothing.
      - **Converse-checked both ways.** By real mutation (a deliberate MC import, then the FQN
        variant — each reddened and named the file; reverted from a `cp` backup, never
        `git checkout --`), *and* permanently inside the test: the detector is run over fabricated
        sources and must fire on a plain import, a **static** import and an inline FQN while staying
        quiet on the same names in javadoc, a line comment and a string literal.
      - Anti-vacuity: the walk must find >250 sources, both boundary packages must be non-empty, and
        `fabric/`+`platform/` must still contain MC imports — so a mis-resolved working directory
        cannot pass as "no violations".

      > Prior burns: `agility-subskill-reparenting` shipped a **vacuous** guard driven by the same
      > table it validated; `audit-item-1-2` shipped one where the wrong source produced the right
      > number. Assert the property, then prove the assertion can fail.

- [x] 2.4 Re-run the count from 2.1 — **0 files.**
- [x] 2.5 Full suite green (**1671**); `scripts/boot-check.sh` **PASSED** (0 ERROR, 0 mixin
      failures, clean shutdown); alchemy brew smoke-tested **in a live world** via the new
      `scripts/brew-smoke.sh`.
      - 🔑🔑 **That script runs the scenario twice, with and without the mod, and fails if the
        control also brews.** The obvious smoke test — water + sugar → mundane — passes with mcMMO
        *uninstalled*; so does water + breeze_rod. Both were tried and only the control run revealed
        it. **A gameplay assertion vanilla also satisfies is indistinguishable from the mod being
        absent.** The discriminating scenario is `AWKWARD + GOLDEN_APPLE → POTION_OF_RESISTANCE`
        (vanilla: no recipe, no potion), which also exercises an `UNCRAFTABLE` base, a custom effect
        and the legacy `DAMAGE_RESISTANCE → minecraft:resistance` mapping at once.
      - Repair on anvil / tree feller / a combat kill are **untouched by slice 5** (their seal
        shipped in 4b-1/4b-2 and is already in the live playtest); per R-d gameplay smoke for those
        stays with the owner's PrismLauncher instance. `/mcstats` dispatch is covered by
        `boot-check.sh` — it cannot execute headlessly (`getPlayerOrThrow`), as Phase 0 established.

**Acceptance: ✅ MET.** `grep -rl "^import net\.minecraft" src/main/java | grep -v '/fabric/' |
grep -v '/platform/'` returns nothing, the guard is proven to fail on a violation, and the suite is
green.

---

## Phase 3 — STRATEGY GATE — ✅ **CLOSED by ruling R-a, decided up front**

- [x] 3.1 Review `BAND_TABLE.md` against the recommendation above
- [x] 3.2 Choose: **branch-per-band** (R-a). The `26.x` finding vindicated it — a yarn-named tree and
      a Mojang-named tree cannot be reconciled by preprocessor directives.
- [x] 3.3 Decision + rationale recorded in the `multiversion-strategy-decision` memory. The
      cherry-pick discipline is written up above ("The cherry-pick discipline R-a requires") and its
      four items are **still open** — the drift-audit script in particular. Nothing else prevents
      drift.

Phases 4–6 below assume the recommended path. If branches are chosen, Phase 4 is replaced by
"cut one branch per band off `master`" and Phases 5–6 stand as written, executed per branch.

---

## ~~Phase 4 — Adopt Stonecutter~~ — ❌ **STRUCK by ruling R-a**

Kept as a record of what was decided against, not as work. R-a chose branch-per-band, so there is no
single tree and nothing for a preprocessor to do. The `26.x` finding is what settled it: a
yarn-named tree and a Mojang-named tree differ on essentially every MC-touching line, and **no
directive can bridge an identifier rename of that size** (see the `26.x` section above).

What was in it, and where each item went:

| Was | Now |
|---|---|
| 4.1–4.3 adopt + parity-check Stonecutter | dropped entirely |
| 4.4 split the test suite by cost | **deferred** — see below |
| 4.5 template `fabric.mod.json` per band | absorbed into **Phase 4′.3**; under branches it is a literal edit, not a template |
| 4.6 keep the configuration cache | moot; nothing is changing the build plugin set |

**4.4 (test split) is deferred, not dropped.** Under one tree it was load-bearing: five bands × 39
MC-typed tests × ~53s of `Bootstrap.initialize()` per fork, in *one* build. Under branches each band
builds independently, so the multiplication never happens in a single run and the split buys much
less. **Trigger to revisit:** the existing hard cap — *if any band's CI exceeds ~30 min wall clock*.
Re-measure `maxParallelForks` then; the current `4` was tuned for one version, and per
`gradle-build-tuning` **more forks is slower** (2 is optimal) because the bootstrap is classload-
bound, not GC-bound.

---

## Phase 4′ — Cut a band branch (replaces Phase 4)

The per-branch recipe. Mostly *configuration*: `.github/workflows/release.yml` already builds and
releases from `mc/**` and predates this document.

⚠️ **The invariant, stated in `release.yml` itself: NO TWO BRANCHES MAY RESOLVE TO THE SAME
`minecraft_version`.** `master` and a band branch that both read `1.21.11` would both tag
`mc1.21.11-v*` and both run the "delete previous release on this Minecraft line" sweep — **whichever
pushes last deletes the other's release.** That is not hypothetical; it is what the deleted
auto-created `mc/1.21.11` branch had armed. So:

- [ ] 4′.1 `git switch -c mc/<band>` off `master`. **Never** off another band branch.
- [ ] 4′.2 **First commit on the branch pins the band's own toolchain** in `gradle.properties`:
      `minecraft_version`, `yarn_mappings` (look the build number up — it is **not** derivable from
      the version), `loader_version`, `fabric_version`, and the ModMenu / Cloth Config versions for
      that MC. Nothing else in the same commit.
- [ ] 4′.3 Set `depends.minecraft` in `fabric.mod.json` to the band's **range**, not its newest
      version — band `1.21.10` covers `1.21.9` too, and the release is tagged for only one of them.
- [ ] 4′.4 Raise `--require-bands` in `.github/workflows/drift-audit.yml` to the new band count.
- [ ] 4′.5 Do **not** push the branch until 5.2–5.7 pass locally. A push builds and releases.

---

## Phase 5 — First back-port (one band only)

Prove the loop before cutting the rest. Cheapest first: **`mc/1.21.10` (2 changed records) →
`mc/1.21.8` (8) → `mc/1.21.5` (10)**.

- [x] 5.1 Cut the branch per Phase 4′. → `mc/1.21.10`, commit `24a70dbf5` (toolchain pin only).
- [x] 5.2 Compile. Work the errors against `BAND_TABLE.md`. **Main source produced exactly ONE error,
      exactly the predicted record** (`CommandManager.GAMEMASTERS_CHECK`).
      - The band's *other* differing record needed **no work at all**. `BAND_TABLE.md` flags
        `World#createExplosion` as signature-changed, but it printed both signatures truncated at the
        same column so the actual difference was invisible: it is the **13-arg abstract** overload,
        where `Pool<BlockParticleEffect>` became `WeightedPool<BlockParticleEffect>`.
        `TntExplodeMixin` targets the **9-arg** overload, byte-identical on both, and nothing in
        `src/` names either `Pool` type. 🔑 *Read the bytecode, not the table's summary of it.*

      - [x] ⚠️⚠️ **THE PROBE HAD TWO HOLES, found by compiling — both CLOSED 2026-08-11.**
            `src/test/java` failed on `Items.IRON_SPEAR`, which was **not one of the 266 records**:
            1. **Static constants were not indexed.** The records covered CLASS / METHOD /
               MIXINCLASS / ATTARGET / ACCESSOR. `Items.IRON_SPEAR` is a *field* on a class that
               exists on every version, so the import resolved and the probe saw a clean row.
               🔑 **A class-granular manifest cannot see a field that vanished from a class that
               survived.**
            2. **`extract-mc-surface.py` scanned `src/main/java` only.** A test that will not
               compile fails the build exactly as hard as main code.
            - [x] Extended the extractor: a new **`STATICFIELD`** record type indexing
                  `<McClass>.<CONSTANT>` references, and **both** source trees are walked.
                  `probe-bands.py` learned the type; `AGENTS.md`'s tooling table updated.
            - **The holes were much bigger than the estimate.** The doc predicted "20 unaudited
              `Items.<CONST>` in main". Actual: **287 `STATICFIELD` records** across both trees
              (21 `Items.*` in main, **67 more in test**), plus **11 CLASS records that exist only
              in `src/test/java`** and had never been probed at all.
              **266 → 566 records; 184 distinct classes; 290 main + 146 test files scanned.**
            - **Control check PASSED on the first run: all 566 records resolve on `1.21.11`.**
              Zero false positives — the constant detector invented nothing. That is the only
              evidence that makes the 287 new records believable.
            - 🎉 **The band STRUCTURE survived intact: still exactly 6 bands, same membership**,
              with the record set more than doubled. The Phase 1.4 boundaries were right; they were
              just *under-counted*. Per-band cost rose to **54 / 18 / 12 / 9 / 2 / 0**
              (was 35 / 15 / 10 / 8 / 2 / 0). Varying records: 35 → **54 of 566**.
            - 🔑🔑 **It immediately found real, unpredicted port work on the two bands not yet
              cut** — each would have been a compile error discovered the hard way:

              | New record | ABSENT on | Why it was invisible before |
              |---|---|---|
              | `STATICFIELD EntityType#COPPER_GOLEM` | `1.21.5`, `1.21.8` | `EntityType` exists on every version |
              | `STATICFIELD SoundCategory#UI` | `1.21.5` (added in `1.21.6`) | `SoundCategory` exists on every version |

              `mc/1.21.10` is **unchanged at 2 records**, which independently corroborates that the
              band already cut was correctly sized.
            - Below the R-b floor the effect is far larger and worth knowing if the floor ever
              moves: the **entire `EntityAttributes#*` family (8 records)** plus four
              `SpawnReason` constants and `EquipmentSlot#VALUES` are absent on `1.21`/`1.21.1`,
              which is most of why that band went 35 → 54.
            - 🔑 **The detector carries `--self-test` and it was mutation-killed in both
              directions**: a detector stubbed to find nothing fails the 3 positive cases, and one
              that ignores comments/strings fails the 3 negative ones (`Items.IRON_SPEAR` named in
              a line comment, a block comment and a string literal). Written because *"found
              nothing"* and *"there is nothing to find"* render identically in a manifest.
            - ⚠️ Resolution is **per referring file**, through that file's own import list, and the
              inline fully-qualified form (`net.minecraft.item.Items.STICK`) is handled explicitly —
              it needs no import and is invisible to an import-driven scan. Same hole
              `PlatformBoundaryGuardTest` found the same way (2.3).
- [x] 5.3 Fix the errors **inside `fabric/` and `platform/` only.** Held: the one main-source change
      is in `fabric/commands/`. `PlatformBoundaryGuardTest` stayed green.
      - 🔑 **The band diff was shrunk on `master` before the branch was cut, not patched after.**
        `CHEAT_COMMAND` was declared as the version-specific `PermissionSourcePredicate`; widening it
        to `java.util.function.Predicate` (`8177bf35f`) — which both versions' return types implement
        — cut the band's main-source difference from *an import + a field type + a call* down to
        **one token**. Ask this first on every band: *can master absorb the difference instead?*
- [x] 5.4 ~~Mixins are the tax~~ — **DONE on `master`, commit `62788874e`**, so every band inherits
      it instead of paying it three times.
      - [x] **All 61 injectors carry `allow = N`** (38 had none). Per `mixin-slice-allow-guard` an
            unresolvable `@Slice` is *silently dropped* and the injector then binds everywhere;
            `defaultRequire = 1` cannot catch that, because `require` is a **minimum**.
      - [x] The values are **measured, not chosen**: `scripts/mixin-allow-audit.py` disassembles each
            `@Mixin` target from the Loom jar and counts what each `@At` actually selects. Its
            `--check` control asserts it reproduces every already-shipped, boot-proven value first —
            it reproduced all 22 on the first run.
      - [x] `MixinApplicationTest` loads all **37 distinct target classes** so Mixin actually applies
            to them. 🔑🔑 **This closes the hole `BAND_TABLE.md` names explicitly**: a javap probe
            sees only that a callee still *exists*, never that the injected method still *calls* it.
            It also covers what `boot-check.sh` structurally cannot — a headless flat world never
            loads `SheepEntity`, `BoggedEntity` or `ArmadilloEntity`.
      - [ ] **Per band: re-run `scripts/mixin-allow-audit.py --mc <version> --check`.** The counts
            legitimately differ per version (a new guard clause upstream changes a `RETURN` count),
            so a `MISMATCH` on a band is a fact to record, not a bug to suppress. `MixinApplicationTest`
            is the runtime backstop and runs in that band's own build.
      - 🔑 `allow` is **per target class**, not a cross-target total — `InjectionInfo` is built from a
        single `MixinTargetContext`. `ShearableInteractMixin`'s four targets get `allow = 1`, not 4.
      - ⚠️ A guard test must **not** live in `com.gmail.nossr50.fabric.mixin`: that is the package
        `mcmmo.mixins.json` declares, so under Knot the transformer claims the *test* and it fails to
        load before a single assertion runs.
- [x] 5.5 Item-ID config drift check — **DONE.** Commits `40486acc2` (the cross-band script),
      `f0bddd64b` (3 live XP bugs it found), `c0b868782` (loader hardening + the per-band test).
      `./gradlew build` exit 0, **1704 tests / 0 failures / 0 skipped**.
      - ⚠️⚠️ **The "five files" scope was itself a probe hole.** `experience.yml` carries **~340
        block/item ids** in the XP tables and was not on the list — the same shape as the two holes
        5.2 found. Scope is now **six files, 689 id references**.
      - Good news, and it held: `platform/Materials.java` guards with `containsId` before
        `Registries.ITEM.get`, so it degrades rather than crashing — and it avoids the
        defaulted-registry trap that bit Hunter (`Registries.ENTITY_TYPE` unknown id ⇒ PIG).
      - [x] **`scripts/config-id-audit.py`** resolves all 689 against all 8 cached jars ≥ 1.21.4.
            🔑🔑 Its whole point is a split **a per-band test structurally cannot make**:
            **ABSENT-ON-BAND** (expected drift, the row stays) vs **DEAD-EVERYWHERE** (a defect).
            On one version the two are identical, and "expected drift" is the reading a reviewer
            reaches for. `--check` fails on the second only — failing on the first would punish the
            both-names pattern that keeps one config correct on every band.
      - [x] **Per-band test** `ConfigItemIdResolutionTest` — every id either resolves **or** is
            recorded and named in a summary line. Silent skips are not acceptable, and that is now
            the assertion rather than the intent. **3 mutations, 3 kills**, including
            *dropped-but-not-recorded*.
      - **Measured drift (the ship gate's real numbers):** `1.21.10` **15** · `1.21.8`/`1.21.5`
        **43** — the 7 spears (repair + salvage), the whole copper equipment tier
        (9 items × fishing/repair/salvage) and `copper_nugget`.

      🎉 **The audit found three live XP holes on `master`, each invisible to every compiler, test
      and boot log** — a config key matching no registry id is just a lookup that misses:

      | Row | Truth |
      |---|---|
      | `Mining.Chain: 100` | `chain` → **`iron_chain`** in the Copper Age drop — **paid zero on the shipping build** |
      | `Mining.End_Bricks: 50` | the block is `end_stone_bricks`; dead on all 8 versions |
      | `Smelting.Deepslate_Lapis_Lazuli_Ore` | the item is `deepslate_lapis_ore` |

      `Chain` keeps its row **and gains `Iron_Chain`** — the both-names pattern the file already used
      (`Lapis_Ore`/`Lapis_Lazuli_Ore`). One row is live per version, so nothing must be remembered at
      the next cut. 🔑 All three fixes **ADD a key**, so `copyMissingDefaults` reaches existing
      installs; a changed *value* would have reached nobody (`ConfigRetunes`).
      🔑 **5 dead legacy doubles were deleted, and they are why `End_Bricks` went unnoticed** — a
      table with known-dead rows in it has no signal left to read.

      ⚠️⚠️ **BOTH obvious registry-id sources are wrong, and neither fails visibly.** Use
      `assets/minecraft/blockstates/<id>.json` + `assets/minecraft/items/<id>.json` (generated per
      *registered* object). `javap Blocks` gives **yarn FIELD NAMES, not registry ids** — and
      `Blocks.COPPER_CHAINS` is a `CopperBlockSet` holding several ids under one field, invisible to
      a type-matching regex. `lang/en_us.json` **keeps stale keys through renames**: 1.21.11 still
      carries `block.minecraft.chain`, so the very bug found here reads as clean there.

      ⚠️⚠️ **A registry probe in an MC-free config's load path poisons the whole test fork.**
      `TreasureConfig`/`FishingTreasureConfig`/`ExperienceConfig` are MC-free by design and their
      tests run without bootstrap; initializing `Registries` there **throws**, and every later touch
      in that fork gets `NoClassDefFoundError`. Doing it in the constructor cost **351 failures
      across 8 unrelated classes**. The probe now runs once from `onServerStarting` — same point and
      same argument as `SkillAvailability#probe`.
- [x] 5.6a **Suite + boot on 1.21.10: PASSED.** `./gradlew build` exit 0, **1682 tests / 0 failures /
      0 skipped** — the same count as master, because the spear tests assert absence rather than
      skipping. `scripts/boot-check.sh <jar> 1.21.10` PASSED (0 ERROR, 0 mixin failures, clean
      shutdown). `mixin-allow-audit.py --mc 1.21.10 --check` PASSED — **all 61 `allow` values are
      identical on 1.21.10**, and `MixinApplicationTest` loaded all 37 targets on the older jar, so
      every mixin is proven to apply there too.
      - 🔁 **Re-verified 2026-08-11 after the three-commit back-port sweep**: `./gradlew build` exit 0,
        **1698 / 0 / 0** (still the same count as master), `boot-check.sh … 1.21.10` PASSED,
        `mixin-allow-audit --mc 1.21.10 --check` PASSED 61/61, `drift-audit.py --master master`
        clean. The band's own build is the only thing that proves a back-port landed intact — the
        cherry-pick exiting 0 does not.
- [x] 5.6b **DONE — all three harnesses pass on `1.21.10`.** `boot-check.sh … 1.21.10` PASSED,
      `brew-smoke.sh` PASSED (with its discriminating vanilla control), and the new
      **`scripts/gameplay-smoke.sh` PASSED 29/29 on both bands** — it covers the whole rest of the
      list (block break XP, a combat kill, a repair, a cook, `/mcstats`, one super ability), which
      had no harness at all because **every one of those needs a player**.
      Commits: master `d30410e12`, band `22a27d4a7` (`Backport-of:`).
      - **The "no new harness is needed" clause above was wrong**, and it is why this item sat open.
        `boot-check.sh` proves the jar boots; the seven gameplay items each need a *player*, and a
        headless server has none. That is the same wall Phase 0 hit (`/mcstats` dies on
        `getPlayerOrThrow` from the console) and the reason `brew-smoke.sh` explicitly leaves the XP
        award to the live playtest — an unattended brewing stand is the one path that completes with
        nobody present. **fabric-carpet's `/player` spawns a real `ServerPlayerEntity`**, which
        mcMMO's listeners cannot tell from a human. Carpet is fetched per MC version into the
        harness work directory only, never a build dependency; `boot-check.sh` keeps the hard
        zero-ERROR gate on a mod list of mcMMO + fabric-api alone.
      - ⚠️⚠️ **A gameplay assertion vanilla also satisfies is indistinguishable from the mod being
        uninstalled.** `brew-smoke.sh` runs its scenario twice, with and without the mod, and fails
        if the control also brews. Water+sugar and water+breeze_rod are *vanilla* brews and both
        passed with mcMMO absent.
      - 🔑🔑 **That exact device does not transfer, and copying it would have been wrong.** Every
        number `gameplay-smoke.sh` reads comes from `/mcstats` and mcMMO's own profile YAML, neither
        of which exists without the mod — so a mod-less run earns nothing *trivially* and proves
        nothing about the scenario. The discriminator is a **per-phase delta with a NEGATIVE
        co-assertion**: the skill that must move, and at least one that must not. Three phases are
        pure negatives, each asserting a behaviour vanilla has no notion of — a **player-placed**
        block pays nothing (K9), a **`/summon`-ed** mob pays nothing (the egg-farm guard), and the
        same double-click on a **non-anvil** block pays nothing.
      - ⚠️⚠️ **A negative phase is vacuous when its ACTION never happened** — *"I mined a placed
        block and got no XP"* and *"I never placed the block"* read identically. Each carries an
        `/execute if` probe that must fire first, or the phase scores **INCONCLUSIVE, never PASS**.
      - **Both converse checks are wired and were run.** The scorer's `--self-test` runs *before the
        server boots* (the `drift-audit.py` pattern) and kills 8 mutations plus an anti-vacuity floor
        on the assertion count; `GAMEPLAY_SMOKE_CONTROL=1` re-runs the whole scenario with **mcMMO
        removed** and inverts the verdict — it must fail, and it does.
      - 🔑 **The Spears gate is now verified through gameplay, version-agnostically.** The harness
        does not know which band it is on: it reads the capability decision the probe logged at boot
        and asserts `/mcstats` agrees. **1.21.11 → 24 skills listed, Spears present; 1.21.10 → 23
        listed, Spears correctly omitted.** Same script, opposite expectations, nothing to remember
        at the next cut.
- [x] 5.7 Band port notes written to memory → `gameplay-smoke-harness`, alongside the existing
      `band-branch-first-cut`, `probe-holes-staticfield-and-test-tree`,
      `skill-version-capability-gate` and `config-id-audit-and-dead-xp-rows`. Four assumptions in the
      harness's first draft were wrong and **every one was silent**: Carpet has no `mine` action
      (breaking a block is `attack`); **`attack continuous` mines blocks but never hits a mob**
      (it models a held button, exactly as vanilla does — a NoAI cow sat at 10.0/10.0 health through
      25 s with the aim provably correct); a **`/summon`-ed mob pays zero combat XP by design**
      (`COMMAND → PLAYER_PLACED → Eggs.Multiplier: 0`); and **Repair needs two clicks** within a 3 s
      confirmation window.
- [ ] 5.8 Only now push the branch, and confirm the release tagged `mc<band>-v*` and did **not**
      touch master's `mc1.21.11-v*` release. Then raise `--require-bands` to 1 (Phase 4′.4) — not
      before, or the scheduled audit fails looking for a branch that is not on the remote.

### The two decisions the `mc/1.21.10` cut surfaced

- [x] **RULED (owner, 2026-08-11): SPEARS is DISABLED on every band below `1.21.11`** — the version
      that added spear items. Not left inert, not dropped from `PrimarySkillType`.
      **✅ IMPLEMENTED**: master `dbd72590a`, band `89402fb84` (`Backport-of:`).
      Verified from code, not recalled: `ItemUtils.isSpear` → `MaterialMapStore#isSpear`, a fixed
      `HashSet<String>` of seven id paths (`wooden_spear` … `netherite_spear`), and
      `SpearsManager`'s constructor touches no item. So the skill is *inert* on such a band, not
      broken — but it is still listed by `/mcstats`, still in the configs, and can never leave
      level 0, which is what the ruling removes.
      - ⚠️⚠️ **Implementation constraint, and it is the whole difficulty:** flipping the shipped
        config default for the issue-#10 per-skill toggle is **not sufficient**. Per
        `ConfigRetunes`, `copyMissingDefaults` back-fills only *absent* keys, so a changed default
        reaches **nobody who has already run the mod once on that band**. The gate has to hold
        regardless of what is already on disk.
      - **The shape it took: a capability probe, not a version pin.** `SkillAvailability#probe()`
        runs from `onServerStarting` and asks the item registry whether **any of the seven spear ids
        `MaterialMapStore` already classifies** exists; the answer is ANDed into
        `SkillGating#isSkillEnabled`, which is the GitHub #10 funnel, so all six meanings of
        "disabled" (no XP, no procs, no super ability, no XP bar, no `/mcstats` line, no plaques)
        close at once. `/mcstats <skill>` now distinguishes the two reasons — pointing a player at
        `coreskills.yml` for a skill the *version* cannot furnish sends them to edit a key that will
        not help.
      - 🔑🔑 **One expression, correct on every band, with nothing to remember at the next cut.** A
        per-band flag or a version constant would be a claim no compiler and no test can check —
        `AGENTS.md`'s standing rule — *and* a step somebody must repeat when `mc/1.21.8` and
        `mc/1.21.5` are cut. Asking the registry needs neither.
      - ⚠️⚠️ **Probed ONCE at server start, never lazily on first use.** A lazy probe must read an
        empty registry as *"cannot tell yet"*, and in the test suite whether the registry is
        populated depends on **which Gradle fork a test class landed in** — one sharing a fork with a
        `McTestRegistries.bootstrap()` sees a live registry, one that does not sees an empty one. On a
        band without spears that turns every Spears assertion in the suite into a coin flip decided by
        test scheduling. This was built the lazy way first and changed for exactly that reason.
      - ⚠️⚠️ **The first wiring test was VACUOUS and was caught before commit.** It asserted
        `isSkillEnabled(SPEARS) == true` on master — which a completely absent gate satisfies just as
        well. Every band above the spear boundary *has* spears, so the disabling half is unreachable
        from the branch the code is written on. Fixed by taking `decide`'s inputs as arguments and
        adding a `setSupportedForTesting` seam, so both directions are provable on **every** band.
        *(Fifth vacuous-guard sighting in this project.)*
      - **Converse-checked in production, not only in tests:** the decision is logged **in both
        directions**, so a boot log tells a probe that decided *"on"* apart from a probe that never
        ran. Observed firing correctly on both bands — see the table at the top of this document.
      - **3 mutations, 3 kills**: gate removed from `SkillGating` (2 tests red), `decide` never
        disabling (1 red), the empty-registry guard dropped (1 red — this is the one that would have
        disabled Spears on *every* version and looked just as confident in the log).
- [x] **Stale version-pinned comments — FIXED on `master` (three of them).**
      `McMMOPlayer.java:205` read `// 1.21.11 always has Spears (pinned)`, and
      `SkillTools#buildCombatSkills` justified its fixed list with *"the port pins MC 1.21.11 …
      which has both Spears and Maces"*. **Both were already false on `mc/1.21.10`.** A sweep for
      the same shape found a third (`PotionConfig`'s *"this 1.21.11 target"*). All three now state
      the code fact that holds on every band and **name no version at all**.
      - 🔑🔑 **The defect was not the wrong number — it was pinning a comment to the build's MC
        version at all.** A comment is read by no compiler and no test, so it goes false in silence.
        Written into `AGENTS.md` as a standing rule so it binds future sessions rather than living
        only here. ⚠️ This is the *exact* shape behind GitHub #7: an MC fact recorded as the reason
        for code, which stopped being true and was never re-checked.
      - The sweep deliberately left alone the ~20 *dated observations* (*"`isShotFromCrossbow()` was
        removed in 1.21.11"*, *"verified against the 1.21.11 merged jar"*). Those stay true; only a
        claim about what the current build targets rots.

**Acceptance:** both branches build, both suites green, both boot and pass smoke, `drift-audit.py`
reports the new band clean, and each branch released to its own Minecraft line.

---

## Phase 6 — Remaining bands

- [ ] 6.1 Order bands cheapest-first, using the changed-symbol count from Phase 1.4:
      **`mc/1.21.10` (2) → `mc/1.21.8` (8) → `mc/1.21.5` (10)**
- [ ] 6.2 Per band, repeat 4′.1–5.8. **Each branch is cut from `master`**, never from the previous
      band — otherwise band N inherits band N−1's back-compat fixes and the diffs stop being
      independent.
- [ ] 6.3 Re-evaluate at the suspected component-API cliff (1.5). If the eating seam needs a full
      reimplementation, that band is its own mini-project — **size it separately, do not absorb it
      into a sweep.**
- [ ] 6.4 Stop-loss: if any single band exceeds the largest completed band by 3×, stop and re-scope.
      Dropping a low-population version is a legitimate outcome, not a failure.

---

## Phase 7 — CI, release, docs

- [x] 7.1 ~~Extend the GH Actions workflow to matrix over bands~~ — **already satisfied.**
      `release.yml` triggers on `mc/**`, so each band branch runs its own build and release; there is
      no matrix to add. The `gradlew` `+x` workaround is per-run and unaffected, and run-number
      versioning already applies per branch.
- [ ] 7.2 ~~Enforce the CI split from 4.4~~ — **deferred with 4.4.** Each branch's build is
      independent, so the cross-band multiplication never occurs in one run. Revisit only at the
      ~30 min-per-band cap.
- [ ] 7.3 Update `README.md` + the 17-page `wiki/` with the supported-version matrix
      - Per `readme-and-github-wiki`: audit the wiki against the *roster*, not the diff. A
        per-commit doc pass cannot catch a page that was never created.
      - [ ] Add a caveat-expiry pass — 4 stale caveats have already outlived their defects
- [ ] 7.4 Publish per-band jars to Modrinth/CurseForge with correct version ranges

---

## Risk register

| # | Risk | Mitigation | Owner phase |
|---|---|---|---|
| R1 | Band count comes out 12+, making "all versions" unviable | ✅ **CLOSED** — 6 bands, 3 branches to cut (Phase 1.4) | 1 |
| R2 | CI time explodes (53s bootstrap × forks × bands) | **Downgraded.** Branches build independently, so the multiplication never happens in one run. Test split deferred; trigger is the ~30 min-per-band cap | 4′ |
| R3 | Directives leak into skill logic, destroying readability + MC-free tests | ✅ **CLOSED** — 26 → 0 leak sites; `PlatformBoundaryGuardTest` (zero allowlist, converse-checked, catches inline FQNs as well as imports) | 2 |
| R4 | Silent mixin misbinding across versions via dropped `@Slice` | ✅ **CLOSED** — `allow = N` on all **61 injectors**, measured from bytecode by `scripts/mixin-allow-audit.py` (control-checked against 22 shipped values); `MixinAllowCoverageTest` holds coverage, `MixinApplicationTest` loads all 37 targets so Mixin really applies. Both mutation-killed | 5 |
| R5 | Item-ID drift silently disables config rows on older versions | Per-band resolution test at 5.5 | 5 |
| R6 | Component API cliff needs reimplementation, not a directive | Identified early at 1.5, sized separately — and it sits at `1.21.2`, below the `1.21.5` floor, so it is out of scope | 1, 6 |
| R7 | Live playtest disrupted mid-refactor | Phase 0 tag + instance backup | 0 |
| **R8** | **A fix lands on `master` and is silently never back-ported** — the one risk R-a *creates*, and the likeliest of all: 11 of the last 12 issue fixes were version-agnostic | ✅ **CLOSED** — `Backport-of:` convention in `AGENTS.md`, `scripts/drift-audit.py` (self-tested against synthetic drift), weekly CI | 3 |

---

## Deferred (explicitly out of scope)

- **NeoForge / Forge.** Blocked on `platform/` being real interfaces. Today `PlatformPlayer`,
  `PlatformBlock`, `PlatformItem`, `PlatformLivingEntity`, `PlatformSender`, `MetadataStore`,
  `BlockDrops`, `Materials`, `ItemSpecBuilder`, `SkillAttributeService` are all `public final class`
  importing `net.minecraft` directly — convenience wrappers, not seams. A final class cannot have a
  second platform implementation. (`platform/scheduler/TaskScheduler` *is* a proper interface —
  that is the shape the others need.)
  Note this was never caught because **Mockito 5's inline mock maker mocks final classes**, so the
  tests stood in for them happily and never applied the pressure.
  Phase 2 makes this conversion cheap later; it is not required for Fabric-only.
- **Versions below `1.21`.** Not requested.
- **Snapshot targets** (`26.3-snapshot-*`). Revisit once `26.3` is stable.
