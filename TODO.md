# Multi-Version Support — Development TODO

**Scope:** Fabric only. Targets: all stable `1.21.x` (12 releases) and the `26.x` line (4 stable
today, growing). NeoForge/Forge are explicitly **out of scope** — see "Deferred" at the bottom.

**Status:** Phase 0 done. Phase 1 in progress.

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

- [ ] **OPEN — release topology.** Pick one before cutting any band branch:
      - **(A) master = newest band.** `mc/1.21.11` is deleted; `mc/**` exists only for *older* bands.
        Master keeps releasing exactly as today. Requires disabling the "ensure branch" step, or it
        recreates `mc/1.21.11` on the next push.
      - **(B) master = integration trunk, never releases.** Every band including the newest lives on
        `mc/**`; drop `master` from the workflow's `branches:` trigger. Conceptually clean; every
        release becomes a `master` → `mc/<newest>` merge.

### The cherry-pick discipline R-a requires (3.3 — nothing else prevents drift)

The doc's core objection to branches stands and is not answered by tooling: **11 of the last 12
issue fixes were version-agnostic logic bugs**, and under branch-per-band each becomes N
applications whose failure mode is silent. Mitigations, all mandatory:

- [ ] Fixes land on `master` **first**, always. A fix authored directly on a band branch is a defect.
- [ ] Every band-propagation commit carries a `Backport-of: <sha>` trailer, making
      `git log --grep='Backport-of: <sha>'` the mechanical answer to *"did this reach every band?"*
- [ ] A drift audit script that, for each `master` fix commit, reports which band branches lack a
      matching `Backport-of` trailer. **Without this, R-a has no drift detection at all.**
- [ ] Run the drift audit in CI on a schedule, not by memory.

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
            → **164 distinct, not 162.** The doc's count was already stale; the script recounts on
            every run so it cannot drift again.
      - [x] 1.1b Every mixin `@Mixin` target, `method =` value, `@At target =` constant, plus
            `@Accessor`/`@Invoker` bindings (not required by the doc, same fragility, free to collect)
      - [x] 1.1c Emit `scripts/mc-surface.txt` — `TYPE<TAB>VALUE`, one record per line
      - **Acceptance: PASS — 266 records** (≥215 required), all 42 mixin files contribute a target.
        `CLASS 164 · METHOD 44 · MIXINCLASS 37 · ATTARGET 19 · ACCESSOR 2`.
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
      - [ ] Write `scripts/probe-bands.sh`: for each version, force Loom to resolve the jar, then
            resolve every line of `mc-surface.txt` against it; record PRESENT / ABSENT / SIGNATURE
      - [ ] Emit `plans/BAND_TABLE.md` — a 16-row matrix of the ~215 symbols
      - **Acceptance:** zero UNKNOWN rows. Every symbol resolves to a definite state on every
        version, or the probe is broken and must be fixed before proceeding.

- [ ] 1.4 Collapse into bands and publish the ruling
      - [ ] Group versions with identical resolution; name each band by its newest member
      - [ ] For each band boundary, record **which specific symbols changed** — that list *is* the
            port work for that band
      - [ ] Expected 4–6 bands. If it comes out 12+, escalate: the scope is not viable as stated
            and the target list must be cut before any more work happens.

- [ ] 1.5 Sanity-check the two suspected cliffs
      - [ ] **Component API churn** — the mod uses `ConsumableComponent`, `FoodComponent`,
            `PotionContentsComponent`, `FireworksComponent`, `ItemEnchantmentsComponent`,
            `NbtComponent`, `LoreComponent`, `DataComponentTypes`. Early `1.21.x` predates much of
            this. Find the exact version where the eating seam (`FoodComponentMixin`, behind
            Farmer's/Fisherman's Diet) stops resolving. Expect a **reimplementation**, not a directive.
      - [ ] **`1.21.11` → `26.1`** — the first cross-scheme hop. Verify it is an ordinary bump.

**Phase 1 output is the deliverable that makes the rest of this document estimable. Do not start
Phase 3 without `BAND_TABLE.md`.**

---

## Phase 2 — Seal the platform boundary (strategy-independent)

26 files leak `net.minecraft` outside `fabric/` and `platform/`. Under a single tree, each leak is a
place a preprocessor directive would end up inside skill logic. Under branches, each is a file that
diverges per branch. Both are bad.

**The 175 MC-free files, the 357 resources, and the 101 MC-free tests must stay clean, permanently.**

- [ ] 2.1 Triage the 26 leak sites — for each, decide *adapter method* vs *move into `fabric/`*

      commands/McMMOCommands.java                    skills/alchemy/AlchemyManager.java
      commands/skills/HunterStatsRenderer.java       skills/alchemy/AlchemyPotionBrewer.java
      commands/skills/SkillStatsRenderer.java        skills/maces/MacesManager.java
      config/skills/alchemy/PotionConfig.java        skills/repair/RepairManager.java
      config/skills/repair/RepairConfig.java         skills/woodcutting/TreeFellerProcessor.java
      config/skills/salvage/SalvageConfig.java       util/BlockUtils.java
      datatypes/skills/ToolType.java                 util/ItemUtils.java
      datatypes/skills/alchemy/AlchemyPotion.java    util/MobOrigins.java
      datatypes/skills/alchemy/PotionStage.java      util/MobTiers.java
      locale/LocaleLoader.java                       util/PotionUtil.java
      util/experience/ExperienceBarWrapper.java      util/player/NotificationManager.java
      util/skills/CombatUtils.java                   util/skills/ParticleEffectUtils.java
      util/sounds/SoundManager.java                  util/text/TextUtils.java

- [ ] 2.2 Work the list to zero. Order: `util/` first (widest blast radius), `alchemy` last (biggest
      cluster — `PotionConfig` + `AlchemyPotion` + `PotionStage` + `PotionUtil` + the two managers
      move together or not at all)
- [ ] 2.3 **Add the build guard.** A test or Gradle check that fails the build on any
      `net.minecraft` / `net.fabricmc` import outside `fabric/` and `platform/`.
      - Guard must be **converse-checked**: add a deliberate violating import, confirm it reddens,
        revert. A guard that has never failed is not known to work.

      > Prior burns: `agility-subskill-reparenting` shipped a **vacuous** guard driven by the same
      > table it validated; `audit-item-1-2` shipped one where the wrong source produced the right
      > number. Assert the property, then prove the assertion can fail.

- [ ] 2.4 Re-run the count from 2.1 — must be **0 files**
- [ ] 2.5 Full suite green; boot the mod; smoke-test one skill per affected area (alchemy brew,
      repair on anvil, tree feller, a combat kill, `/mcstats`)

**Acceptance:** `grep -rl "^import net\.minecraft" src/main/java | grep -v '/fabric/' |
grep -v '/platform/'` returns nothing, the guard is proven to fail on a violation, and the suite is
green.

---

## Phase 3 — STRATEGY GATE

- [ ] 3.1 Review `BAND_TABLE.md` against the recommendation above
- [ ] 3.2 Choose: **single tree + Stonecutter** (recommended) or **branch-per-band**
- [ ] 3.3 Record the decision + rationale in memory. If branches were chosen, also record the
      cherry-pick discipline that will keep them from drifting, because nothing else will.

Phases 4–6 below assume the recommended path. If branches are chosen, Phase 4 is replaced by
"cut one branch per band off `master`" and Phases 5–6 stand as written, executed per branch.

---

## Phase 4 — Adopt Stonecutter

`dev.kikugie.stonecutter`, 0.9.7 as of 2026-07-19. Already used in the wild against `26.2-fabric`
targets, so the new version scheme is supported.

- [ ] 4.1 Read the current Stonecutter docs before writing any build script. Do not copy a template
      from a blog post.
- [ ] 4.2 Add `stonecutter.gradle.kts` / settings wiring with `1.21.11` as the **only** active
      version. Nothing else changes yet.
- [ ] 4.3 Verify parity: build `1.21.11`, diff the jar against the Phase 0 archived artifact.
      **Acceptance:** functionally identical jar, full suite still green. If this step is not clean,
      stop — do not add a second version on top of a broken single-version build.
- [ ] 4.4 Split the test suite by cost
      - [ ] 101 MC-free tests → run **once**, version-independent
      - [ ] 39 MC-typed tests → full run on the primary version, smoke subset elsewhere
      - **Why:** `Bootstrap.initialize()` costs ~53s per JVM fork and is classload-bound, not
        GC-bound. Per `gradle-build-tuning`, 2 forks is optimal — more is *slower*. Five bands ×
        39 MC tests at default settings turns every commit into a coffee break.
      - [ ] Re-measure `maxParallelForks` after the split; the current `4` was tuned for one version
- [ ] 4.5 Template `fabric.mod.json` so `depends.minecraft` emits the correct range per band
- [ ] 4.6 Confirm the configuration cache still holds (`org.gradle.configuration-cache=true`) — if
      Stonecutter breaks it, decide explicitly whether to keep it, don't let it silently regress

---

## Phase 5 — First back-port (one band only)

Prove the loop before committing to the matrix. Pick the band **adjacent** to `1.21.11`.

- [ ] 5.1 Add the band's newest version as a second Stonecutter target
- [ ] 5.2 Compile. Work the errors against `BAND_TABLE.md` — every error should already be a known
      row. An error that is *not* in the table means the probe has a hole; fix the probe.
- [ ] 5.3 Apply directives. **Confine them to `fabric/` and `platform/`.** A directive in a skill
      manager is a Phase 2 regression — fix the boundary instead.
- [ ] 5.4 Mixins are the tax. For each of the 42:
      - [ ] Verify the target still resolves on the new version
      - [ ] **Every mixin gets `allow = N`** before it multiplies across versions. Per
        `mixin-slice-allow-guard`, an unresolvable `@Slice` is *silently dropped* and the injector
        then binds everywhere; `defaultRequire=1` does not catch it. On one version that is a bug.
        Across five it is five silent bugs.
- [ ] 5.5 Item-ID config drift check. ~3,868 lines of item-keyed data ship in the jar:
      `potions.yml` (1,902), `fishing_treasures.yml` (843), `repair.vanilla.yml` (377),
      `salvage.vanilla.yml` (371), `treasures.yml` (375). Items present in `26.2` may not exist in
      early `1.21.x`.
      - Good news: `platform/Materials.java` guards with `containsId` before `Registries.ITEM.get`,
        so it degrades rather than crashing — and it avoids the defaulted-registry trap that bit
        Hunter (`Registries.ENTITY_TYPE` unknown id ⇒ PIG).
      - [ ] Add a per-band test asserting every id in those five files either resolves **or** is
            skipped cleanly, with a log line. Silent skips are not acceptable.
- [ ] 5.6 Boot the band's version. Smoke-test: block break XP, a combat kill, a repair, a brew,
      a cook, `/mcstats`, and one super ability.
- [ ] 5.7 Write the band's port notes to memory — what broke, what the directive was, what the
      probe missed. Band 3 will be much cheaper if band 2 is written down.

**Acceptance:** both versions build from one tree, both suites green, both boot and pass smoke.

---

## Phase 6 — Remaining bands

- [ ] 6.1 Order bands cheapest-first, using the changed-symbol count from Phase 1.4
- [ ] 6.2 Per band, repeat 5.1–5.7
- [ ] 6.3 Re-evaluate at the suspected component-API cliff (1.5). If the eating seam needs a full
      reimplementation, that band is its own mini-project — **size it separately, do not absorb it
      into a sweep.**
- [ ] 6.4 Stop-loss: if any single band exceeds the largest completed band by 3×, stop and re-scope.
      Dropping a low-population version is a legitimate outcome, not a failure.

---

## Phase 7 — CI, release, docs

- [ ] 7.1 Extend the GH Actions workflow (see `release-workflow` memory) to matrix over bands
      - [ ] `gradlew` is not `+x` in git — the existing workaround must survive the matrix change
      - [ ] Version the artifacts per band; run-number versioning still applies
- [ ] 7.2 Enforce the CI split from 4.4 — MC-free suite once, MC suite per band
- [ ] 7.3 Update `README.md` + the 17-page `wiki/` with the supported-version matrix
      - Per `readme-and-github-wiki`: audit the wiki against the *roster*, not the diff. A
        per-commit doc pass cannot catch a page that was never created.
      - [ ] Add a caveat-expiry pass — 4 stale caveats have already outlived their defects
- [ ] 7.4 Publish per-band jars to Modrinth/CurseForge with correct version ranges

---

## Risk register

| # | Risk | Mitigation | Owner phase |
|---|---|---|---|
| R1 | Band count comes out 12+, making "all versions" unviable | Phase 1 escalation gate at 1.4 | 1 |
| R2 | CI time explodes (53s bootstrap × forks × bands) | Test split at 4.4; re-tune forks | 4 |
| R3 | Directives leak into skill logic, destroying readability + MC-free tests | Phase 2 build guard, converse-checked | 2 |
| R4 | Silent mixin misbinding across versions via dropped `@Slice` | `allow = N` on all 42 mixins | 5 |
| R5 | Item-ID drift silently disables config rows on older versions | Per-band resolution test at 5.5 | 5 |
| R6 | Component API cliff needs reimplementation, not a directive | Identified early at 1.5, sized separately | 1, 6 |
| R7 | Live playtest disrupted mid-refactor | Phase 0 tag + instance backup | 0 |

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
