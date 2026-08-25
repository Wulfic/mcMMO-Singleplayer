# Multi-Version Support — Development TODO

**Scope:** Fabric only. Target: every stable **`1.21.x` (12)** and **`26.x` (4)** = **16 versions**.
NeoForge/Forge deferred (see bottom). The `1.20` line was ruled IN by R-v and back OUT by **R-x**
(2026-08-20) before any of it was built — it is out of scope on **scope grounds, never measured**.

**Strategy:** branch-per-band (ruling **R-a**). `master` **is** the newest band; `mc/**` exists only
for older bands and is cut by hand. A **band** = a contiguous range of MC versions across which
mcMMO's touched surface is identical, measured by `scripts/probe-bands.py` against the 1415-record
manifest — a lookup, not a judgment call.

> **Archives — three files, and they hold the evidence this one summarises.**
> Phases 0–7: [plans/completed/TODO-multiversion-phases-0-7.md](plans/completed/TODO-multiversion-phases-0-7.md).
> Everything through Phase 21 (verbatim copy at `06eaaf7ae`):
> [plans/completed/TODO-multiversion-through-phase-21.md](plans/completed/TODO-multiversion-through-phase-21.md).
> **§8.3 and §22 – §33** (verbatim copy at `d5fb36dbf`, the whole `26.2` port):
> [plans/completed/TODO-multiversion-through-section-33.md](plans/completed/TODO-multiversion-through-section-33.md).
>
> ⚠️ **Do not re-derive a number that lives in an archive, and do not re-open a call recorded there.**
> The `2,639 → 0` compile ladder, the `54 → 0` injector re-derivation and the `186 → 1` suite triage
> each cost a session; this file carries the *result*, the archive carries *how it was arrived at* and
> what was refuted on the way. **Everything below is forward work.**

---

## 🔴 WHERE THIS STANDS RIGHT NOW — read before touching anything

✅ **Measured 2026-08-25 from `git ls-remote`, `git branch`, `gradle.properties` and the last suite
run — not carried forward from the previous edition.** Three separate editions of this file described
a status that had already changed: a status sentence is never updated by the commit that changes the
status, because nothing reads it. **Re-measure before quoting this table.**

| | state |
|---|---|
| `master` | `d5fb36dbf`, `minecraft_version=26.2`, **18 commits ahead of `origin/master`, unpushed** |
| `origin/master` | `af584eb42`, already at `26.2` — **the `26.x` conversion IS pushed**; §31–§33 is what is not |
| `mc/1.21.11` | `e3b356c0b`, **local only, never pushed**. Holds the `1.21.11` band; released as `mc1.21.11-v1.2.0` back when `master` *was* that band |
| six older bands | pushed, released at `v1.2.0`, untouched by any of this |
| build | `compileJava` + `compileTestJava` **green** on `26.2` |
| suite | **1,852 executed, 1,851 green, 1 red** — the red is the owner-deferred docs row |
| mixin gate | `mixin-allow-audit.py --check` **passes**: `ZERO=0  MISMATCH=0  OK=60`, plus 1 `SLICE` row hand-verified |
| boot | ✅ **PASSED on `26.2`, 2026-08-25 (§35).** Real standalone Fabric server, loader `0.19.3`, fabric-api `0.158.0+26.2`. Canary rejected, mcMMO initialised, configs loaded, `/mcmmo` rendered, `/mcstats` dispatched, clean shutdown. **0 ERROR/FATAL, 0 mixin failures.** Harness `--self-test` 4/4 first. |
| gameplay | ✅ **PASSED on `26.2`, 2026-08-25 (§35).** `gameplay-smoke.sh` — **30 passed, 0 failed, 0 inconclusive**, a real carpet player through mining, digging, both combat paths, repair, cooking, a super ability and the version gates. Carpet `26.2` exists on Modrinth. ⚠️ Its FIRST run reported `repair` red; that was **the harness, not the mod** — see §35 below. |

✅ **BOTH of the old reasons to hold are now discharged.** The version collision was measured false
on 2026-08-24 (`origin/master` is at `26.2`, `mc/1.21.11` is absent from the remote, so no two
branches share a `minecraft_version` — **R10 and gates 9/11 already clear**), and on 2026-08-25
**`boot-check.sh` went green on `26.2`**, which was the R-z hold condition itself.

🔴 **The hold is now an OWNER DECISION, not a missing gate** (2026-08-25): nothing is pushed and
`mc/1.21.11` stays held while the rest of the §9 list is worked locally. Do not read the discharged
gates as permission — re-ask before any push.

⚠️ **A clean boot is still not coverage.** §32 found a mixin bound to the *wrong live method* while
every structural gate read green, and `mc/1.21.1` shipped a `/summon` origin gap past 67/67 injectors
and a clean boot. Boot proves the jar LOADS; the earning paths are `gameplay-smoke.sh`'s job.

**Push order, when the owner lifts the hold:** `master` and `mc/1.21.11` together, after
gates 7/9/10/11 pass at `--local`, carrying the owed gate-10/11 sweep and **R-aa** in the same push.

---

## What ships today — 6 branches pushed, plus `master`

⚠️ **There is no per-version jar and there never was. One jar covers a band**, via the range in its
own `fabric.mod.json`.

| Branch | MC versions covered | `depends.minecraft` | Released tag |
|---|---|---|---|
| `master` | `26.2` | `~26.2` | ⬜ **nothing released yet** — §9 in flight |
| `mc/1.21.11` | `1.21.11` | `~1.21.11` | `mc1.21.11-v1.2.0`, tagged while `master` held the band ⚠️ **branch unpushed** |
| `mc/1.21.10` | `1.21.9`, `1.21.10` | `>=1.21.9 <1.21.11` | `mc1.21.10-v1.2.0` |
| `mc/1.21.8` | `1.21.6`, `1.21.7`, `1.21.8` | `>=1.21.6 <1.21.9` | `mc1.21.8-v1.2.0` |
| `mc/1.21.5` | `1.21.5` | `>=1.21.5 <1.21.6` | `mc1.21.5-v1.2.0` |
| `mc/1.21.4` | `1.21.4` | `>=1.21.4 <1.21.5` | `mc1.21.4-v1.2.0` |
| `mc/1.21.3` | `1.21.2`, `1.21.3` | `>=1.21.2 <1.21.4` | `mc1.21.3-v1.2.0` |
| `mc/1.21.1` | `1.21`, `1.21.1` | `>=1.21 <1.21.2` | `mc1.21.1-v1.2.0` |

**Shipped coverage is continuous `1.21` → `1.21.11` — the whole `1.21` line, 12 of 12.** `mod_version`
is `1.2.0-SNAPSHOT` on every branch; **seven** releases at `v1.2.0`, 0 drafts, one per band.

🔑 **Nothing in the eleven gates reads the remote TAG list.** Gates 9/10/11 compare branches; the
release sweep enumerates `gh release list`, which a bare tag is invisible to. **Re-read
`git ls-remote --tags` before repeating any statement about which tags exist.**

---

## Skill coverage per band — audited 2026-08-19

**Does every band ship every skill?** Answered mechanically, by comparing git **blob shas** across
branches rather than by reading the code on one of them — `PrimarySkillType.java`,
`SkillAvailability.java` and `SkillGating.java` were byte-identical on all seven.

✅ **26 skill constants**, identical everywhere. *(AGILITY is deliberately absent — retired
2026-08-17, its perks re-parented onto Parkour, Swimming and Flying.)*

✅ **Exactly one version gate exists.** `SkillAvailability#isSkillSupported` returns `true` for
everything except entries in its **skill → required-id-paths** map, and each entry is decided by a
**registry probe** — *does this version have the item?* — never by a version number. `SPEARS` is the
only row that fires on a real band.

🟡 **The `MACES` row is INERT on every in-scope version.** `Items.MACE` ships from `1.20.5` and R-x
withdrew the `1.20` line, so nothing that exists can disable it. The code stays — the *mechanism* is
live via `SPEARS`, and `26.x` will need it — but this is the **vacuity shape this repo has caught
thirteen times**. Its disabling half is reachable only through `setSupportedForTesting`.
⚠️ **Do not read a green `MACES` test as evidence the gate works on a real band.**
⚠️ **Do not close a future gap by adding a version number.** One registry expression, correct on every
band, needing no edit when the next band is cut: add a `GATED` map entry, never a second hardcoded
field.
⚠️ **Residual (risk R12):** the map is hand-maintained. A NEW skill whose items postdate the floor is
added to `PrimarySkillType` and to nothing else, and nothing goes red.

🔴 **That audit is a statement about SOURCE.** Identical source proves the skill *roster* is uniform;
it does not prove a skill *fires*. The per-band evidence for "it fires" is gate 1's suite count and
gate 6's `gameplay-smoke.sh` 29/29 — **neither has ever run on `26.2`.**

---

## What is genuinely missing — **all 4 `26.x`** — `1.21.x` is COMPLETE

| Band | MC versions | Status |
|---|---|---|
| `1.21` … `1.21.11` | 12 versions, 7 bands | ✅ **SHIPPED**, all at `v1.2.0` |
| `26.2` | `26.2` | 🔴 **IN FLIGHT — `master`.** Compiles, suite green, **has never booted** |
| `26.1.x` | `26.1`, `26.1.1`, `26.1.2` | ⬜ **a future band**, not part of this one — three ecosystem projects draw the line at `26.2` (§27) |
| `1.20.x` | `1.20` … `1.20.6` | 🚫 **OUT OF SCOPE (R-x)** — withdrawn on scope, **never priced** |

⚠️⚠️ **Read a probe-row count as *rows to look at*, never as work to do.** The completed bands are the
calibration and the counts over-predict by 3–6× (`mc/1.21.10`: 10 rows → **1** real change;
`mc/1.21.8`: 32 → **6**; `mc/1.21.1`: 125 → mostly one mechanical rename apiece). What a row count
cannot price is the difference between a **signature change** and an **absence**: `getEntityWorld`
cost `mc/1.21.5` **3** broken sites and `mc/1.21.8` **57**, from the same one row.
🔑 **And the estimate errs the same way at the top of the range.** §31 priced **54 seam redesigns**;
there were **0**. §32.0 priced **8 handler rewrites**; there were **4**, only 2 touching a handler.
**An over-estimate reads as diligence and nobody audits it.** Re-derive the work from the symbols
before trusting any multiplier.

---

## RULINGS

Carried forward and still binding: **R-a** branch-per-band · **R-c/P2-a…e** full platform seal ·
**R-d** playtest stays on master builds · **R-e** `26.x` is its own mini-project · **R-f** master =
newest band · **R-g** as narrowed by **R-r** (`.github/` is back on `master`, holding three files) ·
**R-h** pushes are mine once gates are green · **R-i/R-j/R-k** shared docs are byte-identical on
every branch, the live wiki is never pushed · **R-n** `.agent/` is not committed · **R-o** push all
branches · **R-p** keep the `2.2.050`-style padding *(superseded in practice by Phase 13's `1.x`
line)* · **R-q** band-appropriate equivalents carry `Backport-of:` · **R-s/R-t/R-u** · **P16-1**
`--check` is read-only · **P19-1** the shared governance layer is byte-identical on every branch.

| # | Question | Ruling |
|---|---|---|
| **R-l** | Support floor (2026-08-12) | ✅ **RULED (owner) — superseded R-b's `1.21.5` floor.** Floor moved to **`1.21`**; ship all 12 `1.21.x` + all 4 `26.x`. R-v superseded it for one day; **R-x
withdrew R-v, so R-l's 16-version target is LIVE again and is the current scope.** |
| **R-m** | Band `1.21.1`'s "three absent subsystems" | 🔴 **SUPERSEDED by R-m′ — its premise was measured FALSE. Nothing is disabled.** |
| **R-m′** | What band `1.21.1` really needs (2026-08-19) | ✅ **RULED (owner).** Measured against the real `1.21.1` merged jar with `scripts/javap-mc.sh`: the `EntityAttributes` family is a **rename**, not an absence (all 31 fields present, under a prefix); the eating seam and the sneak seam are absent **as named** but each has a direct predecessor. **Nothing ships disabled**, the `SkillGating` work is **cancelled**, and 8.3 needs **no `master`-side change**. Detail in §8.3. |
| **R-v** | **Extend the floor to `1.20` (owner-ruled 2026-08-19)** | 🔴 **WITHDRAWN BY R-x (2026-08-20) — one day live, nothing built under it.** It had ruled: **support the FULL `1.20` line — `1.20` through `1.20.6`, all 7 versions.** Asked explicitly because of the cost cliff: the DataComponents API does not exist below `1.20.5`, and the mod's item-data, enchantment, food and potion layers are written entirely against it. The owner was shown that this is a **data-layer re-implementation, not a rename sweep**, and chose the full line anyway. **Superseded R-l's floor and deleted the "versions below `1.21`: not requested" line from Deferred.** Target rose 16 → 23 versions. **All of that is reversed.** |
| **R-x** | **Drop the `1.20` line (owner-ruled 2026-08-20)** | ✅ **RULED (owner): the supported range is `1.21` – `1.21.11` plus `26.x`. No `1.20.x` version is supported.** Withdraws R-v and restores R-l's **16-version** target. ⚠️⚠️ **This is a SCOPE ruling, not a feasibility finding.** 22.0 never ran to completion — it was stopped mid-run — so **the `1.20` line was never priced**, and nothing may be written anywhere claiming it was found too expensive. §9 (`26.x`) is explicitly **unaffected** and remains the next project. |
| **R-w** | **`mod_version` for this cycle (owner-ruled 2026-08-20)** | ✅ **RULED (owner): `1.2.0-SNAPSHOT`, minor not patch.** §22.1's `MACES` gate is a user-visible behaviour change — a skill can now vanish on a band — not a bug fix. Nothing has released since `v1.1.0`, and R-t's gate has been refusing every push on all seven branches since. Per R-p the value is identical on every branch; per **R-w′** below, no gate checks that. |
| **R-y** | **Does the identity guard cover `README.md`/`wiki/`? (owner-ruled 2026-08-20)** | ✅ **RULED (owner): YES — both are IN `branch-file-identity-audit.py`.** Closes the call carried from §21.6. R9's noise argument is about a *per-push* audit and does not transfer to a **ship gate**. 🔑 **It found a real defect on its first run**: `mc/1.21.1` had corrected a `wiki/Husbandry.md` sentence that is false on that band, **on that band only** — a rule-1 violation, invisible to `drift-audit.py` by design (it asks whether a `master` commit reached a band, never whether a band holds a fix `master` lacks), so six branches served wrong text to a shipped band's players with every gate green. 🔴 **Depends on R-x.** `BandDocsMatchRealityTest` needs the documented floor strictly below every version a branch ships; `1.20.6` covers all seven **only because no band ships below `1.21`**. Reopen the `1.20` line and these two files must leave the set in the same change, or no state satisfies both guards. |
| **R-z** | **Which branch becomes `26.x`? (owner-ruled 2026-08-20)** | ✅ **RULED (owner): `26.x` becomes `master`, and `1.21.11` is cut to `mc/1.21.11`.** Follows from R-f (master = newest supported band) once `26.1 > 1.21.11` is granted. 🔴 **It trips R10 for as long as the cut is held**: `mc/1.21.11` and `origin/master` both sit at `minecraft_version=1.21.11`, and two branches on one value means each release run reaps the other's release. The mitigation is that the cut stays **unpushed** until `master` compiles — so **R-z is the reason nothing may be pushed**, not merely a topology note. ⚠️ Recorded here on 2026-08-20 because it had been ruled in §27 and written only to `.agent/memory/`, which is not committed (R-n) and therefore invisible to a fresh clone. |
| **R-aa** | **Java 25 vs gate 10 (owner-ruled 2026-08-20)** | ✅ **RULED (owner): read the Java level from a new per-band `gradle.properties` key.** `26.x` needs Java 25 (Mojang’s own manifest requirement, §27) while `release.yml:117` pins `'21'` and must stay byte-identical on every branch under **P19-1** — no single state satisfies both. The workflow text becomes identical again by referring to the key; the **value** is per-band, exactly like `minecraft_version`. Classifies as `BAND_LOCAL` in `gradle-key-identity-audit.py`, so it needs **no new mechanism** — the existing per-key guard already covers it. **Rejected:** installing both JDKs on every branch — it keeps the text identical too, but selects the level *implicitly* via toolchain resolution instead of declaring it, and R-y’s precedent is that a shared file states its per-band facts rather than inferring them. ⚠️ **Ruled, not built.** It lands in the same change that makes `master` pushable; building it earlier would put a `25` in a workflow on six branches that need `21`. |

### 🔑 What R-m′ taught, and why it is written down here

R-m was a **cost** re-scope, not a feasibility finding: stop-loss 6.4 fired because `1.21.1` shows
125 probe rows against the largest completed band's 32 (**3.9×**). That was the right rule to apply —
but **a probe-row count measures SYMBOLS THAT MOVED, not WORK.**

⚠️ R-m had also gone stale on its own terms: it named **Agility**, retired 2026-08-17, and predated
the Taming reach fix. Neither error was visible from the ruling itself. **This is the GitHub #7 shape
— a decision recorded as the reason for code, which stopped being true and was never re-checked.**
Apply the same suspicion to R-v's own cost estimates — and note that R-v never got as far as a
measurement before R-x withdrew it, so there is nothing to re-derive: **there is no `1.20` cost
figure in this repo, and there must not be one written from memory.**

---

## The per-band recipe — used by every band cut

Each branch is cut **from `master`, never from the previous band** — otherwise band N inherits band
N−1's back-compat fixes and the diffs stop being independent. The *learning* transfers even though
the branch does not.

- [ ] **x.1** `git switch -c mc/<band>` off `master`.
- [ ] **x.2** First commit pins that band's toolchain in `gradle.properties` **and nothing else**:
      `minecraft_version`, `yarn_mappings`, `loader_version`, `fabric_version`, ModMenu, Cloth.
      ⚠️ **Look the yarn build number up** — it is not derivable from the version
      (`1.21` → `build.9`, `1.21.1` → `build.3`, `1.21.2` → `build.1`, `1.21.3` → `build.2`,
      `1.21.4` → `build.8`).
- [ ] **x.3** `fabric.mod.json` `depends.minecraft` = the band's **range**, not its newest version.
- [ ] **x.4** Verify `.github` inheritance: `git ls-tree -r --name-only HEAD -- .github` on the fresh
      branch must list **exactly three paths** (`FUNDING.yml`, `workflows/release.yml`,
      `workflows/drift-audit.yml`). `.github/` is in `.gitignore`, so anything absent must be re-added
      with `git add -f` **by explicit path** — never `git add -f .github`, which sweeps in the 12
      untracked Copilot files no branch tracks.
- [ ] **x.5** Compile. Work errors against `plans/BAND_TABLE.md`. **Fix inside `fabric/` and
      `platform/` only** — `PlatformBoundaryGuardTest` must stay green. Phase 2's blast-radius cap has
      held on two real MC API breaks.
- [ ] **x.6** 🔑 **Ask first, every band: can `master` absorb the difference instead?** Widening
      `CHEAT_COMMAND` to `Predicate` on `master` cut `mc/1.21.10`'s whole main-source diff to one
      token. It fails when there is no overlapping name on both sides (`getEntityPos`), so ask, don't
      assume.

      ⚠️⚠️ **Then measure the absorption's actual reach — MC API availability is NOT monotonic.**
      `f73031ed9` absorbed the world accessor and its commit message claimed that made one expression
      correct on every band. It does not. Measured across all 12 cached merged jars, reading **both**
      `Entity` and `ServerPlayerEntity` because javap never lists inherited members:

      | MC | `Entity#getEntityWorld()` | `ServerPlayerEntity` covariant | the expression that compiles |
      |---|---|---|---|
      | `1.21` – `1.21.5` | ✅ returns `World` | ❌ none (`getServerWorld()`) | `(ServerWorld) getEntityWorld()` — cast **required** |
      | `1.21.6` – `1.21.8` | ❌ **absent** | `ServerWorld getWorld()` | `getWorld()` — the cast form **does not compile** |
      | `1.21.9` – `1.21.11` | ✅ | ✅ `ServerWorld getEntityWorld()` | either; the cast is a no-op |

      Present at `1.21.5`, gone at `1.21.6`–`1.21.8`, **back** at `1.21.9` — yarn mapping churn, not a
      linear deprecation. An absorption verified against the newest and the oldest version in scope
      can still be **false in the middle**, and `master` compiles either way so nothing would show it.
- [ ] **x.7** 🔑 **Run ship-gate 2 (`mixin-allow-audit.py`) BEFORE gate 1.** 8.2.5b's lesson:
      *"20 compile errors → 0" was NOT the finish line* — four more injectors were broken and
      **compiled perfectly**. Then run the full gate. Then push.
- [ ] **x.8** Back-port anything that belongs on `master` **to `master` first**, then to every other
      band with `Backport-of:` trailers.
- [ ] **x.9** Raise the weekly drift audit's floor: `--require-bands` in
      `.github/workflows/drift-audit.yml` goes to the **new** band count, on `master` first and then
      on every band. The floor is what makes *"found no bands"* fail instead of reading as a clean
      audit. Leaving it stale is under-strict rather than noisy — the audit still passes — which is
      exactly why nothing will remind you to do it. ✅ **At `6` since 2026-08-19** — raised as 8.3's
      x.9, one release cycle late, which is itself the evidence: 8.3 shipped and released with the
      floor still admitting five bands, and every gate stayed green throughout.
- [ ] **x.10** ⚠️ **Move the documented support floor in the SAME commit.** `README.md` and
      `wiki/Installation.md` both carry a *"Minecraft **&lt;version&gt; and older are not supported**"*
      sentence — `1.20.6` as of 8.3. That
      sentence is **false on any band below it** and `BandDocsMatchRealityTest` will fail there. Both
      files, on every branch.

---

## §9 — the `26.x` band — 🔴 IN FLIGHT on `master`

**Its own mini-project (R-e). Do not absorb it into a sweep.**

From `26.1` Minecraft **ships unobfuscated** — verified against the real artifact (`26.2` server jar:
7,434 `net/minecraft/*` classes, zero obfuscated names). Mappings are absent because they are no
longer *needed*, not because tooling is missing. But **Mojang names are not yarn names, and the
schemes differ structurally** (`net.minecraft.item.ItemStack` → `net.minecraft.world.item.ItemStack`;
`ServerPlayerEntity` → `ServerPlayer`; `FoodComponent` → `FoodProperties`), so this band is a
**wholesale rename of the entire MC-facing surface**.

🔑🔑 **This is what vindicated R-a.** No preprocessor directive can bridge an identifier rename of
this size; a branch is the only honest representation.

🔑🔑 **And it is what vindicated Phase 2's platform seal.** The rename hit **96 of the 295 main source
files — every one of them in `fabric/` (74) or `platform/` (22), and 0 of the other 189.** Recipe
**x.5** predicted exactly that on the evidence of two ordinary API breaks; it then held against the
largest input it will ever be given.

| | state |
|---|---|
| **9.1** derive the yarn→official table | ✅ **DONE (§25).** `scripts/derive-official-names.py`, three-way join through Mojang's own ProGuard map, 43-check self-test, **100% of the 1,389 MC symbols**. ⚠️ The table is `1.21.11`→`1.21.11`: it prices the **translation**, never the `26.1` API delta. Never quote the 100% as a §9 estimate |
| **9.2** toolchain | ✅ **DONE (§27), and its premise was measured FALSE.** `26.x` builds on the **existing Loom 1.17.13**. What changed: plugin id → `net.fabricmc.fabric-loom`, `mappings` line **removed entirely**, `modImplementation` → `implementation`, Java 21 → **25** (Mojang's own manifest requirement) |
| **9.3** translate the source **and** the tooling | ✅ **SOURCE DONE (§28–§33)**: 2,639 → 0 compile errors, 54 → 0 dead injectors, 186 → 1 red tests. ⬜ **TOOLING HALF STILL OPEN** — see below |
| **9.4** cut the band | 🟡 **HALF DONE.** (a) *Which branch?* — ruled: `26.x` **becomes `master`** and `1.21.11` was cut to `mc/1.21.11` (R-z, honouring R-f). (b) *One band or two?* — **answered: TWO.** Fabric API, ModMenu and Cloth Config independently draw `[26.1, 26.1.1, 26.1.2]` vs `[26.2]`, so `master` takes `26.2` alone and `26.1.x` is a future band. ⚠️ Three ecosystem projects agreeing is not proof about *this mod's* surface; the definitive check is `probe-bands.py`, which needs 9.3's tooling half |
| **9.5** full ship gate | 🟡 **HALF DONE (§35).** `boot-check.sh` ✅ and `gameplay-smoke.sh` ✅ both green on `26.2`. The expected version-specific fixture work **did not materialise**: carpet publishes a `26.2` build and every command in the scenario parsed unchanged. What DID bite was a latent harness defect that `26.2` merely happened to expose — see §35. Gates 3–11 of the ship gate are still unrun on this branch |

### ⬜ 9.3's tooling half — what still reads yarn names

The band cannot run its own gates until its tooling speaks official names.

- [ ] **`probe-bands.py`** — still yarn-only, so **this branch cannot be probed at all**, which is why
      9.4(b) rests on ecosystem evidence rather than on our own surface.
- [ ] **`javap-mc.sh`** — still yarn-only.
- [x] ✅ **`scripts/mc-surface.txt` regenerated under official names (§36).** 1415 yarn records →
      **1433 official**, `--check` **PASS**. `./gradlew classes testClasses` ran first and
      `build/classes` was complete (537 class files, the same number `--check` disassembles).
- [x] ✅ **Nested-type spelling normalised in `extract-mc-surface.py` (§36)**, bundled with the
      regeneration exactly as planned. `normalise_nested()` + `import_map()` fold `Outer.Inner` to the
      JVM binary `Outer$Inner` **at the import step**, which is where the two scans diverged: the
      bytecode scan always reads `$` out of a constant pool, the source scan took the spelling
      straight off an `import` line. One import — `AttributeModifier.Operation` in
      `SkillAttributeService` — produced a dotted `CLASS` row and two dotted `STATICFIELD` rows for a
      type the bytecode side spelled with `$`. **Dotted-nested rows: 3 → 0.**
      🔑 **It was survivable only because TWO consumers carried the workaround** (`probe-bands.py`
      and `derive-official-names.py` both have a `name_candidates()` that maps the dotted tail back).
      A third consumer that forgets simply reports the type ABSENT on every band — a false positive
      shaped exactly like a real API removal.
      ⚠️ Split at the **outermost** capitalised segment. Stopping at the first `Upper.Upper` pair from
      the end renders `Outer.Inner.Leaf` as `Outer.Inner$Leaf`, a binary name nothing resolves.
      ✅ Seven unit cases (including three negatives and the non-MC `java.util.Map.Entry`), plus a
      **mutation verified to go red on 5 checks** when the pre-fix behaviour is restored.
- [x] ✅ **`mixin-allow-audit.py` runs on a `26.x` branch** — `find_jar` falls back to
      `minecraft-merged-deobf-<mc>.jar`. 26.x ships unobfuscated and yarn publishes nothing for it, so
      the old glob for the *yarn-remapped* artifact could never match: **the one gate that can see a
      mixin selector was unusable on the only branch whose selectors had just been rewritten.**
- [x] ✅ **`scripts/mixin-target-sizer.py`** — new. Classifies every injector target off `26.2`
      bytecode, and `--shadows` covers the `@Shadow`/`@Accessor`/`@Invoker` blind spot below.

### ⬜ Carried out of §31 – §33 — the open list

- [x] ✅ **`boot-check.sh` on `26.2`** — PASSED 2026-08-25 (§35). The R-z hold condition, discharged.
      🔑 **Loom registers NO `remapJar` on `26.x`**, because Minecraft ships unobfuscated there and
      `build.gradle` names no mappings artifact — so the shipping artifact is the plain `jar` task.
      `./gradlew remapJar` fails with *"Task 'remapJar' not found"* on this branch and works on every
      band branch, which is a per-band build-graph difference no gate in this repo looks at.
- [ ] 🔴 **The docs pass** — `README.md` + `wiki/**`, all seven branches, **one commit** (owner-
      sequenced). `README.md` has no `26.2` row and its floor sentence still says *"neither is the
      `26.x` line yet"*; that is the single red test in the suite, deliberately left red. Both files
      are in R-y's byte-identity set, so the edit lands everywhere at once or not at all.
- [ ] 🔴 **The owed gate-10/11 sweep** — 🔧 **IN FLIGHT as §37; the scope is measured there, not here.**
      **9 paths** for gate 10 (all `scripts/**`; `master` proved the winner on every one) and **1 key**
      for gate 11 (`mockito_version`). ⚠️ **`.gitignore` was NOT owed** — the `30.6` / `.hprof` claim
      that stood here was stale, all eight branches already carry blob `b432715f0`.
      🔑 Mockito had to move: Byte Buddy 1.17.7 rejects **Java 25 (69)** outright, so every mocking
      test threw. It stays `SHARED` — a newer Mockito is *expected* to run on the bands' Java 21, and
      §37 step 4 is the first thing that will ever have **tested** that — but its
      **floor is now set by the newest JDK any band uses**, which R-aa makes band-local.
      🔴 `gradle.properties` is inside `release.yml`'s `paths:` filter, so the mockito half **rides the
      `mod_version` bump with R-aa** or it fires seven release runs R-t refuses. §37 splits the commits.
- [ ] 🔴 **R-aa — the per-band `java_version` key.** Ruled, not built. `release.yml:117` still pins
      `'21'` on all eight branches while `26.x` needs 25, and the workflow must stay byte-identical
      under P19-1. Deferred deliberately: `release.yml` is inside its own `paths:` filter, so touching
      it on the six live bands fires six release runs that R-t's stale-version gate refuses. **Bundle
      it with the `mod_version` bump when `26.2` actually ships.**
- [ ] 🟡 **R13 — the general overload-rebind shape.** §33.4 closed the `equals` family only. *Any*
      method whose narrow overload is deleted while a wider one survives rebinds **silently**, because
      javac must accept it by the language rules. No gate covers the general case.
- [ ] 🟡 **§31.5 — the 562 unreviewed collision sites over 38 names.** Owner-sequenced after §32.
      Sampling says they are dominated by false positives (`Map.get`, `List.add` on plain Java
      collections sharing a name with a colliding MC member) — but `Registry#getId` was 42 sites,
      **12 of which javac never mentioned**, and one was a live `equals()` in main source returning
      false forever. Plan: (a) filter mechanically on receiver type, reporting the before/after count;
      (b) read every survivor by hand, recording the count **reviewed**, not just fixed; (c) a
      mutation re-introducing one `BuiltInRegistries.*.getId(` that must survive the filter and be
      reported — **a filter never shown to catch anything is a filter that removes everything.**
- [ ] 🟡 **`config.yml` is outside the config-id gate entirely**, and carries at least one dead id
      (`Chain`). `config-id-audit.py` never reads it.
- [ ] ⬜ **`build.gradle:2`'s bare `fabric-loom` id.** Resolved on `master` (it is the explicit
      non-remap id); what the **bare** id does on the `1.21.x` branches is inferred, not measured. It
      matters the next time a band's toolchain is touched.
- [ ] ⬜ **`TODO.md`'s one-blob-on-every-branch invariant.** `master` no longer describes the same
      product as the bands, so propagation cannot fix the drift this time. Decide at 9.5 whether the
      invariant survives the `26.x` split at all.

### 🔑🔑 The five blind spots §29 – §33 found — every one read GREEN on every gate

**This is the part of the archive worth re-reading before writing any guard.** Each is a defect class
this repo's whole gate stack reports as passing, and three are blind **by construction**:

1. **A member that is PRESENT but WRONG.** A compiler loop cannot see it. All 27
   `int cannot be dereferenced` errors were one row (`Registry#getId`) and were caught only by luck of
   the return type; 12 more sites produced **no diagnostic at all**.
2. **A mixin that APPLIES but binds the wrong live method.** `FireworkRocketEntityMixin` selected
   `explode` where the real work moved to `dealExplosionDamage`; `allow=1 computed=1` — a clean row.
   **Application is not correctness, and `--check` cannot tell the two states apart.**
3. **A seam that became a PASS-THROUGH.** `EntityTypeSpawnOriginMixin`'s `create` chain inverted, so
   every caller building its own `EntitySpawnRequest` walked past the injector unstamped — and an
   unstamped mob counts toward mob mastery. The only symptom was `allow=2 computed=1`, which **loads
   fine, because `allow` is an upper bound.**
4. **`@Shadow` / `@Accessor` / `@Invoker` members.** A `@Shadow` is declared *in the mixin*, so javac
   type-checks it against the mixin's own declaration and never asks whether the target has it;
   `@Accessor` names its field in a **string**. `mixin-allow-audit.py` scores injectors only. It fails
   at mixin **apply** time — game start, after every gate is green. **4 of 8 were still yarn, and none
   were broken by 26.x: they were missed by the original port and wrong on every band since.**
5. **A Minecraft name surviving as a STRING LITERAL.** No renamer parses it, javac cannot see inside
   it. §33.5's Husbandry failure was exactly this.

⚠️ **`26.1 > 1.21.11` sorts correctly under semver**, so version *predicates* need no special-casing.
The obstacle was never the version string.

---

## §37 — the owed gate-10/11 sweep — 🔴 IN FLIGHT

**Tier 2.** Seven band branches, a generated artifact per band, and a key that changes what a push
does. Written down before the first edit.

### What the two gates actually report — measured 2026-08-25, `--local`

Gate 10 (`branch-file-identity-audit.py`) — **9 violating paths**, all `scripts/**`:

| path | shape | winner |
|---|---|---|
| `derive-official-names.py` | DIFFERS (7 bands / master) | master |
| `extract-mc-ids.py` | DIFFERS | master |
| `extract-mc-surface.py` | DIFFERS | master |
| `gameplay_smoke_scenario.py` | DIFFERS | master |
| `mc-ids.txt` | DIFFERS | master |
| `mixin-allow-audit.py` | DIFFERS | master |
| `mixin-target-sizer.py` | ABSENT on all 7 | master |
| `rename-damage-audit.py` | ABSENT on all 7 | master |
| `rename-to-official.py` | ABSENT on all 7 | master |

Gate 11 (`gradle-key-identity-audit.py`) — **1 key**: `mockito_version` is `5.14.2` on all seven
bands and `5.23.0` on `master`, and the key is classified `SHARED`.

✅ **`.gitignore` is NOT owed.** All eight branches carry blob `b432715f0`. The `30.6` / `.hprof`
line in the carried list was stale — the change had already reached every branch. Removed from the
list rather than re-carried.

### 🔑 The "which side is correct" gate, discharged mechanically

Gate 10 names a difference, never a culprit, and R-y's first run found `master` wrong. So this was
**measured, not assumed**: for each of the six DIFFERS paths, walk `git rev-list master -- <path>`
and ask whether the bands' blob is a state `master` itself passed through.

**All six: yes.** The band blob is an ancestor state on `master` in every case, so no band-authored
content exists to lose and `master` is the winner on all nine paths. *(The check is cheap and it is
the only thing separating this from the R-y shape — run it, do not skip it because rule 1 says
`master` usually wins.)*

### 🔴 Why this cannot be a `git checkout master -- scripts/` and walk away

**`extract-mc-surface.py` changes GENERATED output on every band.** §36's `normalise_nested()` folds
`Outer.Inner` to the JVM binary `Outer$Inner` at the import step — and **every band carries the same
dotted import**, `EntityAttributeModifier.Operation` in `SkillAttributeService`. So carrying the
generator without regenerating each band's `scripts/mc-surface.txt` turns **gate 8 red on seven
branches in order to make gate 10 green**.

That is the standing rule, and it is the whole cost of this sweep: **the generator back-ports, the
generated file is REGENERATED.** One build per band.
⚠️ `mc-surface.txt` is gate 9's artifact and must stay **distinct** per branch — it is excluded from
gate 10 and must NEVER be carried. Regenerate it; never copy it.

### 🔴 The mockito bump changes what a PUSH does — so it gets its own commit

`gradle.properties` is inside `release.yml`'s `paths:` filter. Every previous gate-10 carry commit
was `scripts/**`-only and said so explicitly *("this push neither builds nor releases")*. This one is
not: pushing `mockito_version` to seven bands fires **seven release runs**, and R-t's stale-version
gate refuses every one of them because `mod_version` has not moved since `v1.2.0` shipped. **Seven
red runs, reporting to the tab nobody watches (R11).**

That is the identical shape that deferred **R-aa**. So the sweep lands as **two commits per band**:

* **A — `scripts/**` + the regenerated `mc-surface.txt`.** Outside the `paths:` filter. Pushable on
  its own, whenever the hold lifts. Closes gate 10.
* **B — `gradle.properties`, `mockito_version` only.** Inside the filter. **Rides the `mod_version`
  bump, with R-aa**, exactly as the push order already says. Closes gate 11.

Gate 11 therefore stays RED until the version bump, deliberately and on the record. Splitting the
commits is what makes that a choice rather than a trap.

### The per-band recipe — seven times, `mc/1.21.1 · .3 · .4 · .5 · .8 · .10 · .11`

1. `git checkout <band>` — tree must be clean first.
2. Carry the nine paths **by name**, `git checkout master -- <path>` each. 🔴 **Never
   `git checkout master -- .`** and never `-- scripts/`, which would drag `mc-surface.txt` across.
3. `mockito_version` → `5.23.0`, that key alone, in `gradle.properties`.
4. `./gradlew --no-daemon --stacktrace --no-build-cache build -Pmod_version=1.2.0` — gate 1.
   ⚠️ Read the **`N executed`** line, not `BUILD SUCCESSFUL`; confirm `> Task :test` is **bare**, not
   `FROM-CACHE`. This is the leg that proves **Mockito 5.23.0 actually runs on the bands' Java 21**
   rather than asserting it — the claim in `gradle.properties` is currently untested on every band.
5. `python scripts/extract-mc-surface.py` — regenerate. Then `--check` must **PASS**.
   ⚠️ Record the **record count**. §17: `compileJava` came back `FROM-CACHE` on every band and the
   per-band record count was the only thing proving the classes were that band's own.
6. `python scripts/mixin-allow-audit.py --mc <version> --check` — the carried gate must still pass
   on a `1.21.x` branch. Its `find_jar` fallback was added for `26.x`; **nothing has ever run the
   new file on a band.**
7. `python scripts/config-id-audit.py --self-test` then `--check` — `mc-ids.txt` gained a `26.2`
   section; prove that does not disturb a band's own section.
8. Commit A, then commit B. `Backport-of:` trailer per source commit.

### After all seven — the cross-branch gates, `--self-test` first, then `--local`

* **9** `manifest-identity-audit.py --require-bands 8` — 0 collisions. 🔴 **This is the one that can
  fail as a RESULT of the sweep**: seven manifests regenerated in one session on one box is the
  build-cache-hit scenario gate 9 exists for. Exit 2 is not a pass.
* **10** `branch-file-identity-audit.py --require-bands 8` — expect **0** differing paths.
* **11** `gradle-key-identity-audit.py --require-bands 8` — expect gate 11 GREEN once commit B is on
  every band, and it will be, locally.

### What I am NOT doing

* **Not pushing anything.** The hold is an owner decision; every commit here is local. `origin` is
  untouched, and `git ls-remote` gets re-read before any history operation.
* **Not touching `release.yml` / R-aa.** Same deferral, same reason, bundled with the version bump.
* **Not carrying `mc-surface.txt`** — gate 9 requires it to differ.
* **Not doing the docs pass** — still owner-sequenced, still one commit across all seven.
* **Not translating `probe-bands.py` / `javap-mc.sh`** — that is 9.3's remaining tooling half and a
  separate item; carrying them unchanged is correct here because they are identical already.

### Rollback

Pre-sweep tips, recorded before the first edit:

```
master     13fefb85a   mc/1.21.5    e9bd67a60
mc/1.21.1  ce3f91c41   mc/1.21.8    b64088dcf
mc/1.21.3  14206a5aa   mc/1.21.10   f90571abe
mc/1.21.4  e0f9ab825   mc/1.21.11   e3b356c0b
```

Undo for any band: `git reset --hard <sha above>` on that branch. Nothing is pushed, so no remote
state is at risk and no history is rewritten. 🔴 `git checkout -- <path>` is NOT the undo here — it
destroys uncommitted work; check `git status --short` first.

---

## §8.3, §22 – §33 — closed, and where the reasoning lives

Full text in
[plans/completed/TODO-multiversion-through-section-33.md](plans/completed/TODO-multiversion-through-section-33.md).

🔑 **How to resolve a `§n` reference with no heading in this file:** §8.3 and §22 – §33 are in the
archive linked above; anything numbered lower (§10.7, §21.6, the Pass-1 `item N` numbers cited in
source comments) is in `TODO-multiversion-through-phase-21.md`. Nothing has been deleted — only moved.

| § | what it was | outcome |
|---|---|---|
| **8.3** | `mc/1.21.1`, the last `1.21.x` band | ✅ SHIPPED `mc1.21.1-v1.2.0`. Re-scoped by R-m′ — nothing ships disabled |
| **22** | the `1.20` line | 🚫 **WITHDRAWN (R-x)** before any of it was built. 22.1 (the `MACES` gate) had already shipped and stays |
| **23** | back-port §22.1, ship `v1.2.0` | ✅ seven releases at `v1.2.0`. Exposed **R-w′** — `mod_version` fell between two cross-branch guards |
| **24** | docs join the identity guard (R-y) | ✅ gate 10, 24 → 44 paths. Its **first run** found `master` + 5 bands serving a wiki claim false on `mc/1.21.1` — and the **band** was right |
| **25** | is the yarn→official table derivable? | ✅ yes — 100% of 1,389 symbols. 9.1's premise was measured **false**: yarn's `official` column is the *obfuscated* name |
| **26** | gate-10 sweep for the new script | ✅ all seven branches. 🔑 `drift-audit.py --master master` **prefers remote refs** — a pre-push run grades stale bands and prints `No drift` |
| **27** | the `26.x` toolchain, measured | ✅ builds on the **existing** Loom; `master` pinned to `26.2`. Java 25 collides with gate 10 → **R-aa** |
| **28** | drive the 33 ambiguous records to 0 | ✅ **33 → 4**, all truncated mixin selectors no tool will ever fix. 🔑 one record needs **two** mojmap names, so a name→name table is wrong — **drive the rename by CALL SITES** |
| **29** | the compiler-driven rename script | ✅ 2,643 → 126 errors. 🔑 the compiler loop is **necessary but not sufficient** — blind spot 1 above |
| **30** | apply the rename to `master`'s `src/` | ✅ 2,639 → **56**. The collision audit was under-reporting **52×** in its default mode (13th vacuous-guard sighting) |
| **31** | the genuine `26.x` API delta | ✅ main **and** test tree to 0. Then found **54 of 61 injectors dead** — *the mod compiled and did nothing* |
| **32** | re-derive every injector target | ✅ `--check` **passes**, `ZERO 54 → 0`, `OK 6 → 60`. Found blind spots 2, 3 and 4 |
| **33** | the first real suite on `26.2` | ✅ **186 red → 1**, 1,852 executed. Found blind spot 5; the last red is the deferred docs row |

---

## Other open work — harness and playtest

*Closed items are summarised in one line each; the full reasoning is in the archives.*

**Closed 2026-08-19/20 — do not re-open:** `gradle-key-identity-audit.py` (ship-gate **11**, per-KEY,
closes R-w′) · `brew-smoke.sh` refuses an ambiguous jar glob instead of taking `find | head -1` ·
`combat-egg-control` → `combat-summon-control`, now asserting the **origin stamp** directly rather
than inferring it from XP staying flat · the smoke scorer discovers gated skills from the boot log
instead of grepping one hardcoded skill name (⚠️ the wording in `SkillAvailability#probe` is now an
**interface**, not prose) · the anti-vacuity floor is derived and exact at **30**, not `3 + sum(...)`
· R-y ruled `README.md`/`wiki/` **into** the identity guard (§24).

- [ ] 🔴 **THE SPAWN-EGG HALF IS NOT DONE — attempt budget exhausted, phase withdrawn.**
      A `combat-spawn-egg-control` phase was written and **removed before commit** rather than ship a
      red ship-gate to seven branches. The three refuted hypotheses are worth more than the code was:

      **Symptom:** `player Tester use once` holding `minecraft:cow_spawn_egg` spawns nothing and logs
      nothing — no error, no effect. Measured twice, identically.

      **Refuted, each with log evidence — do NOT re-test these:**
      1. *Wrong item id* — `Replaced a slot on Tester with [Cow Spawn Egg]`, so it IS in mainhand.
      2. *Bad aim / nothing to click* — rewritten to the idiom `cook-campfire` and `super-ability`
         both use successfully (`setblock 2 -60 0`, then `_look(2.0, -59.5, 0.5)` — AT the face
         plane). Rotation dumps confirm the aim took.
      3. *Gamemode restriction* — `Set Tester's game mode to Survival Mode`, `spawn-protection=0`.

      **Where to look next:** whether fabric-carpet's `use once` reaches `ItemStack#useOnBlock` at
      all, or only the block's own `onUse`. Every `use once` phase that works is a **block**
      interaction; placing an entity is an **item** interaction, and no phase in this harness has ever
      proven that path. If carpet cannot drive it, a **dispenser** loaded with the egg reaches the
      same `spawnFromItemStack` seam with no player raycast at all.

      ⚠️ Until then `SPAWN_ITEM_USE` is **covered by unit tests only** and the harness covers
      `COMMAND` alone. That is a real gap, and it is the gap `mc/1.21.1` fell through.
- [ ] 🔴 **THE LIVE PLAY-TEST — owner only. Oldest debt in the queue.**
      **Taming:** shoot a zombie at ~25 blocks with a wolf at your heels in **passive** mode and watch
      it close; then sneak-right-click it with a bone. **Skills tab:** neither the tab, nor a locked
      row, nor the greyed state has ever been seen rendered. Next suspect if a boosted wolf still will
      not close: `FollowOwnerGoal` outranking `MeleeAttackGoal`. **Budget: 3 attempts.**
- [ ] ⬜ **Manifest debt, piece 1 — the last red row.** Validate manifest symbols against the band's
      merged jar; refuse a manifest naming a symbol the band does not have. Needs a Loom-cached jar
      and `probe-bands.py`'s resolver.
      ⚠️ It would **not** have caught the `1c480efc4` incident — every symbol in that blob was real.
      🔑🔑 **That blob was a perfectly valid manifest, for the wrong branch.** No per-branch check,
      automated or human, can tell "correct manifest" from "correct manifest belonging to a different
      branch" — on the branch it came from, every record is true. Only this piece can.
- [ ] ⬜ **`gameplay-smoke.sh`'s path bridge is only PARTIALLY demonstrated** — three call sites need a
      running server and were fixed by inspection. Confirm on the next real smoke run.
- [ ] ⬜ **`ci-watch.sh --mutate` on Windows.** Fixed by Phase 20's `cygpath -w` bridge; re-confirm on
      the next ship gate that step 8's failure mode is *demonstrated* rather than asserted.

---

## The ship gate — run per band, before every push

**It is a person running ten commands, and that has not changed.** ⚠️ R-r put `release.yml` back on
every branch including `master`, so a push now *builds and runs the suite* again — but that is gate
**1 only**, it runs **after** the push rather than before it, and a red run reports to a tab nobody
watches (**R11**). Run the list first; the workflow is a backstop, never the check.

⚠️ **Only gates 1, 7, 9, 10 and 11 have any unattended leg at all, and four of those are weekly.**
Gate 1 fires per push via `release.yml`; gates **7**, **9**, **10** and **11** run from
`.github/workflows/drift-audit.yml`, which GitHub fires **weekly and only from the default branch** —
inert on every band by construction. **The other six have no automation whatsoever.**
⚠️ **Eleven gates are listed. Update this sentence when you add one; nothing else counts them.**

1. `./gradlew --no-daemon --stacktrace build -Pmod_version=$(grep -E '^mod_version=' gradle.properties | cut -d= -f2 | sed 's/-SNAPSHOT$//')`
   — exit 0, suite green, count matching `master` (~1719). A lower count means something was disabled
   to get there.

   ⚠️⚠️ **The `-Pmod_version` override is NOT decoration.** A bare `./gradlew build` is not what CI
   runs, and that gap is how §10.7 shipped a guard green on all five branches and red on every
   release, blocking every band for a day with nothing reporting it. **A gate that does not reproduce
   the release command cannot certify a release.** ⚠️ Read Gradle's own exit code — `cmd | tail`
   returns *tail's*.

   ⚠️⚠️ **`BUILD SUCCESSFUL` does not mean the suite ran. Grep for `> Task :test`** and confirm it is
   bare — not `FROM-CACHE`, not `UP-TO-DATE`. Caught live: `BUILD SUCCESSFUL in 1m 21s` with
   `> Task :test FROM-CACHE`, about to certify a release on results the invocation never executed.
   ⚠️⚠️ **And a docs-only change leaves `test` up-to-date entirely** — `README.md`/`wiki/` are read via
   `Path.of(...)` and are **not declared Gradle inputs**, so the two doc guards silently do not run.
   **Read the `N executed` line, not the SUCCESSFUL line.** To force it:
   ```
   ./gradlew --no-daemon --stacktrace --no-build-cache cleanTest test -Pmod_version=<resolved>
   ```
   ⚠️ **Check `build/libs/` holds exactly one non-sources jar** before reading a jar name off it.
   `build` never cleans it; ten had accumulated on 2026-08-13. CI is immune (fresh checkout); a local
   `boot-check.sh` glob is not.
2. `python scripts/mixin-allow-audit.py --mc <version> --check` — 61/61. 🔑 **Run this BEFORE gate 1.**
   A `MISMATCH` is a fact to record, not a bug to suppress.
3. `scripts/boot-check.sh <jar> <version>` — 0 ERROR, 0 mixin failures, canary rejected.
   ⚠️ **Read the exit code: `1` = the mod is bad, `2` = ENVIRONMENT and nothing was proven about the
   mod.** `--self-test` first, as with every gate.
4. `python scripts/config-id-audit.py --check` — 0 dead-everywhere. Reads the committed
   `scripts/mc-ids.txt`, so it needs no local Loom cache.
   ⚠️ **Cherry-pick `extract-mc-ids.py` + `mc-ids.txt` together** — the audit imports the generator's
   parser and refuses to run without it.
5. `scripts/brew-smoke.sh` — passes **with** its vanilla control failing.
6. `scripts/gameplay-smoke.sh` — 29/29, and `GAMEPLAY_SMOKE_CONTROL=1` must **fail**.
7. `python scripts/drift-audit.py --self-test` **then** `--master master` — **0 MISSING on every
   band**. ⚠️ It audits `origin/master`, so **push first, then audit**.
   ⚠️⚠️ **It cannot see a docs-only commit** (Phase 21, defect B): docs are excluded from
   `PROPAGATABLE_PREFIXES` by design, so a docs edit propagates **iff its commit also touched `src/`**.
   Five bands once documented Agility as live while their jars had it retired, and the auditor printed
   *"No drift"* with **unchanged counts** throughout. A green run is not evidence about docs.
8. `scripts/ci-watch.sh --mutate` **then** `scripts/ci-watch.sh HEAD` — **after** the push; the only
   gate downstream of it. ⚠️ **Run it FROM the branch you pushed**, or it fails closed at exit 3
   (*cannot tell*). `CI_WATCH_BASE=<sha before the push>` is the override when the reflog is gone.
9. `python scripts/manifest-identity-audit.py --self-test` **then** `--require-bands <count>` —
   **0 collisions**; every branch's `scripts/mc-surface.txt` distinct.
   ⚠️ **Defaults to `origin/**`, so push first — or pass `--local`.**
   ⚠️ **Exit 2 is not a pass** — fewer than two branches means zero pairs compared.
   🔑 **Distinct is not correct.** Six manifests that all differ can all six be wrong.
10. `python scripts/branch-file-identity-audit.py --self-test` **then** `--require-bands <count>` —
    **0 differing paths**. The **inverse** of gate 9: `AGENTS.md`, `.gitignore`,
    `.github/workflows/*.yml`, `scripts/**` and — since **R-y** — `README.md` + `wiki/**` are one
    artifact every branch shares.
    ⚠️ **A gate-10 failure names a difference, not a culprit.** Decide which side is *correct* before
    converging: rule 1 says `master` usually is, but R-y's first run found the opposite — `master`
    and five bands carrying a wiki sentence that was **false**, fixed on `mc/1.21.1` alone.
    ⚠️ **`README.md` and `wiki/Installation.md` also carry the support-floor sentence that
    `BandDocsMatchRealityTest` requires to sit strictly below every version the branch ships.** One
    value (`1.20.6`) satisfies all seven **only while no band ships below `1.21`** (R-x). If that
    changes, those two files leave gate 10 in the same change — see the R-w′/gate-9 shape.
    ⚠️⚠️ **Gates 9 and 10 hold opposite invariants over `scripts/`.** `mc-surface.txt` must be
    **distinct** (gate 9) and is therefore **excluded** from gate 10. If it ever appears in both sets,
    no state satisfies both and nothing can ship. **Do not resolve a gate-10 failure by widening its
    exclusion list.**
    ⚠️ **Exit 2 is not a pass**, and this gate has an extra way to hit it: an empty path set means the
    include globs matched nothing.
    🔑 **Identical is not correct.** Six copies that agree can be six copies of the same wrong file.
11. `python scripts/gradle-key-identity-audit.py --self-test` **then** `--require-bands <count>` —
    **0 violations**. The **per-KEY** guard (**R-w'**), and the reason it is a third script rather
    than a flag on gate 9 or 10: `gradle.properties` is the one shared file that can never be
    compared whole. `mod_version` must be **identical** on every branch (R-p) while
    `minecraft_version` must **differ** (R-a) — so gate 7 excludes the file and gate 10 cannot demand
    it, and the gap between them was exactly one key wide.
    🔴 **The failure it catches is silent:** a band left behind on `mod_version` hits R-t's stale-
    version gate and simply **stops releasing**, in a repo where a red release run is already the
    normal outcome of an ordinary push. §23 found it by hand; a table in this file was the only check.
    ⚠️ **Defaults to `origin/**`, so push first — or pass `--local`.**
    ⚠️ **Exit 2 is not a pass** — fewer than two branches means zero pairs compared.
    ⚠️ **It fails closed on an UNCLASSIFIED key only when that key DIFFERS between branches.** A new
    key that agrees everywhere is not reported, deliberately: a rule demanding every tuning knob be
    classified is one nobody maintains.
    🔑 **Agreement is not correctness.** Seven branches agreeing on `mod_version` proves they agree —
    not that the number is right, and not that anything released. `gh release list` is still the only
    thing that answers that.

⚠️⚠️ **Nothing checks that these REMEDIES compose.** Phase 20: `MSYS2_ARG_CONV_EXCL='*'` — prescribed
by this repo's own gotchas for the Phase-18 `rev-parse` trap — silently turned two gate steps off. **A
test run in the shell that hides the bug proves nothing.**

---

## Risk register

| # | Risk | State |
|---|---|---|
| R1 | Band count makes "all versions" unviable | ✅ **CLOSED AGAIN by R-x (2026-08-20).** R-v had re-opened it at ~11 bands; the `1.20` line is withdrawn, so the ceiling is **8 branches today** (`master` at `26.2` + 7 `mc/**`, one of them still held) **and 9 once `26.1.x` is cut**. Re-opens the moment the floor moves again |
| R2 | CI time explodes | **Downgraded** — branches build independently. Trigger: ~30 min per band |
| R3 | Version-specific code leaks into skill logic | ✅ **CLOSED** — 26 → 0 leak sites; `PlatformBoundaryGuardTest` held on two real API breaks |
| R4 | Silent mixin misbinding via dropped `@Slice` | ✅ **CLOSED** — `allow = N` on all 61 injectors, measured from bytecode |
| R5 | Item-ID drift silently disables config rows | ✅ **CLOSED** — `config-id-audit.py` off a committed registry manifest, plus two per-band tests. ⚠️ Stays closed only while the manifest is **cherry-picked, never regenerated per band**. ⚠️ R-v's requirement to regenerate it for `1.20.x` is **withdrawn (R-x)**. `26.x` will still need its own regeneration, under **official** names — see 9.3 |
| R6 | Component-API cliff needs reimplementation | ✅ **CLOSED BY SCOPE (R-x, 2026-08-20)** — closed by moving the range, not by solving it. R-v had re-opened it at full height and the reasoning was sound: below `1.20.5` the DataComponents API does not exist at all, and 19 `DataComponentTypes` records plus the entire `ItemEnchantmentsComponent` layer have no predecessor there — only a different data model. **That cliff now sits outside the supported range**; every in-scope version has components. ⚠️ **Re-opens at full height the instant anyone proposes a floor below `1.20.5`.** The measurement is preserved in §22; the cost is not, because it was never taken |
| R7 | Live playtest disrupted | ✅ Phase 0 tag + instance backup |
| R8 | A fix lands on `master` and is silently never back-ported | 🟡 **DOWNGRADED, not closed.** All three legs exist: the convention, `drift-audit.py`, and the weekly run — which fires only from `master` and has now fired unattended (run `32005557735`). ⚠️ **The unattended leg is weekly and reports to a tab nobody opens (R11)**, so between a commit and the next Monday detection is still *"somebody remembers"*. **Each new band multiplies this** — 7 today, 8 once `26.x` lands (R-x withdrew R-v's ~11) — and the floor must be raised per cut (x.9) |
| R9 | A fix outside `src/` never reaches a band, and the docs deny a band that ships | 🟡 **RE-OPENED IN PART by Phase 21.** R9a (propagation of `scripts/`+`.github/`) and R9b (`BandDocsMatchRealityTest`) both hold. But Phase 21 found a **third** hole: **a docs edit propagates iff its commit also touched `src/`** — the effective policy was never *"docs are not propagated"*, it was a coin flip that reads as a deliberate exclusion in every document describing it. ⚠️ `BandDocsMatchRealityTest` is not broken and **could never catch it**: it asks *"is what this branch's docs say true HERE?"* and was correctly green on all five. **Cross-branch equality is not correctness; correctness-per-branch is not equality.** The open owner call in *Other open work* is the candidate fix |
| R10 | Two branches resolving to the same `minecraft_version` | 🟡 **DISCHARGED FOR NOW, and the reason it was thought LIVE is itself the lesson.** Measured 2026-08-24: `origin/master` is at `26.2` and `mc/1.21.11` is **absent from the remote**, so no two branches share a value. The plan had asserted for four days that both sat at `1.21.11` — true when written, false the moment `master` was pushed at `26.2`, and nothing reported the change. ⚠️ **It re-arms the instant `mc/1.21.11` is pushed**, which is why the two must diverge *before* either goes out. The tag-reaping sweep is live on every branch, `release.yml` detects a collision and emits a `::warning::` — deliberately not a failure, so **nothing stops it** |
| R11 | A band's release fails and nobody finds out | 🟡 **DOWNGRADED, still open.** It has happened once: §10.7 failed **four** band releases and was invisible for a day behind green local builds, a green ship gate, a green drift audit and a clean `git status`. `scripts/ci-watch.sh` (gate 8) reports four states rather than a boolean, because *"I could not see a run"* and *"the run passed"* are the two R11 conflates. ⚠️ **It is still a person running a command. A real close needs a notification, not a workflow** |
| **R12** | **A skill is inert on a band and nothing says so** | 🟡 **MITIGATED 2026-08-19 (§22.1).** `SkillAvailability` now carries a **skill → required-id-paths** map rather than one field per skill; `MACES` is gated alongside `SPEARS`, and gating the next one is a single `GATED` entry with no call-site edit. The javadoc claim that every other skill *"predates the floor of the supported range"* is gone — it was load-bearing prose and R-v falsified it in a day. ⚠️ **R-x makes that sentence true again and it stays out**: it was only ever true by accident of the floor. Proven by 21 tests (was 15), and by mutation: making the gate dead (`return true`) reddens exactly the 4 wiring tests. ⚠️ **The registry-driven test did NOT fail under that mutation** — this band has both items, so only the `setSupportedForTesting` seam reaches the disabling half. Vacuity confirmed empirically, not argued. ⚠️ **Residual 1:** the map is still a hand-maintained list; a NEW skill whose items postdate the floor is added to `PrimarySkillType` and to nothing else, and nothing goes red. Auditing skills against required ids is not yet mechanical. ⚠️ **Residual 2 (R-x):** with the `1.20` line withdrawn, the `MACES` entry can never fire on any in-scope version — the only row that still exercises the gate on a real band is `SPEARS` |

---

## Carried debt (open items only — closed rows are in the archives)

- [ ] 🔴 **Manifest debt piece 1** — see *Other open work*. Piece 2 shipped as
      `scripts/manifest-identity-audit.py` (Phase 18).
- [ ] 🟡 **The `--require-bands` floors are hand-maintained** in `.github/workflows/drift-audit.yml`
      and in ship-gate steps 9, 10 and 11. **Now 6** (8.3's x.9, raised one cycle late). ⚠️ **The next
      raise is already earned and not yet made:** `mc/1.21.11` is cut and held, so the floor goes
      **6 → 7 in the same push that publishes it**. R-x withdrew R-v's extra cuts, so `26.1.x` is the
      only one after that. Nothing reminds you; a stale floor is under-strict and the audit still
      passes.
- [ ] 🟡 **R13 — the general overload-rebind shape**, and 🟡 **§31.5's 562 collision sites**. Both
      carried out of §33; detail under §9.

---

## Standing rules that keep biting

- **Fixes land on `master` FIRST**, always. A fix authored directly on a band branch is a defect.
  Every band-propagation commit carries `Backport-of: <sha>`; a `master` commit that must not
  propagate says `Backport-not-needed: <reason>` **in the commit that made the decision**.
- **A docs-only commit reaches NO band.** Phase 21. If a docs fix must propagate, either give it a
  `src/` half or back-port it by hand — the auditor will print *"No drift"* either way.
- **Never pin a comment to the build's Minecraft version.** A dated observation (*"removed in
  1.21.11"*) stays true; a claim about what this build targets goes false silently on the next cut.
  Four have already rotted, one cited as the *reason* for absent code (GitHub #7).
- **Never resolve a band difference by changing `minecraft_version` on `master`.**
- **A guard that has never failed is not known to work.** Every script here carries a `--self-test` or
  a control run — because *"found nothing"* and *"there is nothing to find"* render identically.
  🔑 **Thirteen vacuous-assertion sightings so far** — the most recent a collision audit under-
  reporting by **52×** in the mode people actually run, exiting 1 either way so the exit code looked
  identical. Assume the next one is in whatever you are writing now.
- **A green gate is not evidence the code RUNS.** Five defect classes found in §29 – §33 read green on
  every gate this repo owns, three of them blind *by construction*: a member present but wrong, a
  mixin bound to the wrong live method, a seam that became a pass-through (`allow` is an **upper
  bound**), a `@Shadow`/`@Accessor` member javac never type-checks against the target, and a
  Minecraft name surviving as a **string literal**. Full detail under §9 — **read it before writing a
  guard**, because each one was found by pulling on a row that looked like tooling noise.
- **`BUILD SUCCESSFUL` is not "the tests ran."** Read the `N executed` line.
- **Caveat-expiry pass** on every docs change: grep the **symptom**, not the file you edited. One wiki
  serves every band, so *"X works in \<version\>"* reads as *"X works for you"* three bands down. And
  audit the skill roster against `PrimarySkillType.values()`, never against the diff.
- **Docs are CRLF in the working copy** (`core.autocrlf=true`). A byte-level splice must emit CRLF or
  the diff becomes the whole file. ⚠️ `git show <ref>:<path>` returns the **LF** blob — do not compare
  it against a working-copy file without normalising. ⚠️ And `sed` in this environment strips the CR:
  a `sed -n 'a,bp'` splice of a CRLF file silently produces LF output.

---

## Deferred (explicitly out of scope)

- **NeoForge / Forge.** Blocked on `platform/` being real interfaces — today `PlatformPlayer`,
  `PlatformBlock`, `PlatformItem` and 7 others are `public final class` importing `net.minecraft`
  directly. A final class cannot have a second platform implementation. Never caught because
  Mockito 5's inline mock maker mocks final classes happily.
  🔑 **§22.2's item-data seam would have been the first step toward this with an independent reason to
  exist** — R-x withdrew it, so nothing in the queue moves `platform/` toward real interfaces. That
  work now has no sponsor, and it is worth knowing that this is what was lost with the `1.20` line.
- **The whole `1.20` line — `1.20` … `1.20.6` (7 versions).** ⬜ **Ruled out by R-x (2026-08-20)**,
  the day after R-v ruled it in. ⚠️ **Withdrawn on SCOPE, not on measured cost** — 22.0 never
  completed, so no `1.20` cost figure exists. See §22 for what *was* measured.
- **Versions below `1.20`.** Not requested.
- **Snapshot targets** (`26.3-snapshot-*`). Revisit once `26.3` is stable.
- **Test-suite split by cost** (old Phase 4.4). Trigger: any band's build exceeding ~30 min.
- **Trophy Hunter gameplay proof.** Wiring-proven on `mc/1.21.8` and `mc/1.21.5` but not
  gameplay-proven — it is rank-gated and the smoke player is Hunter 0. First thing to add if
  `gameplay-smoke.sh` is extended.
