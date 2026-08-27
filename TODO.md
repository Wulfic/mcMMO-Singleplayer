# Multi-Version Support — Development TODO

**Scope:** Fabric only. Target: every stable **`1.21.x` (12)** and **`26.x` (4)** = **16 versions**.
NeoForge/Forge deferred (see bottom). The `1.20` line was ruled IN by R-v and back OUT by **R-x**
(2026-08-20) before any of it was built — it is out of scope on **scope grounds, never measured**.

**Strategy:** branch-per-band (ruling **R-a**). `master` **is** the newest band; `mc/**` exists only
for older bands and is cut by hand. A **band** = a contiguous range of MC versions across which
mcMMO's touched surface is identical, measured by `scripts/probe-bands.py` against the 1415-record
manifest — a lookup, not a judgment call.

> **Archives — four files, and they hold the evidence this one summarises.**
> Phases 0–7: [plans/completed/TODO-multiversion-phases-0-7.md](plans/completed/TODO-multiversion-phases-0-7.md).
> Everything through Phase 21 (verbatim copy at `06eaaf7ae`):
> [plans/completed/TODO-multiversion-through-phase-21.md](plans/completed/TODO-multiversion-through-phase-21.md).
> **§8.3 and §22 – §33** (verbatim copy at `d5fb36dbf`, the whole `26.2` port):
> [plans/completed/TODO-multiversion-through-section-33.md](plans/completed/TODO-multiversion-through-section-33.md).
> **§37 – §47** (verbatim copy at `ee57abdec`, the nine-branch ship and the harness work):
> [plans/completed/TODO-multiversion-through-section-47.md](plans/completed/TODO-multiversion-through-section-47.md).
>
> ⚠️ **Do not re-derive a number that lives in an archive, and do not re-open a call recorded there.**
> The `2,639 → 0` compile ladder, the `54 → 0` injector re-derivation and the `186 → 1` suite triage
> each cost a session; this file carries the *result*, the archive carries *how it was arrived at* and
> what was refuted on the way. **Everything below is forward work.**

---

## 🔴 WHERE THIS STANDS RIGHT NOW — read before touching anything

✅ **Re-measured 2026-08-26 (session 25) from `git rev-list --count origin/<b>..<b>`, `gradle.properties`
and this session's own build runs — not carried forward.** Four separate editions of this file have now
described a status that had already changed, the most recent being this very block: it still said
*"the push hold STANDS"* and *"`26.1.2` NOT boot-checked"* **after** §43 lifted the hold, smoked the
band and pushed all nine. **A status sentence is never updated by the commit that changes the
status, because nothing reads it.** Re-measure before quoting this table.

| | state |
|---|---|
| branches | **NINE, all on the remote.** `master` (`26.2`) + `mc/26.1.2` + the seven `1.21.x` bands |
| vs `origin` | 🔴 **THIS ROW NO LONGER CARRIES A NUMBER, AND THAT IS THE FIX.** It was wrong **three times in three commits** — `1` when the truth was `2`, corrected to `3` and stale one commit later, then `six` written into the commit that made it seven. **A status row cannot count the commit it is written in**, so it stops trying. The measurement is one command and it is never stale: `git rev-list --left-right --count origin/<b>...<b>` per branch, or the loop over all nine in `.agent/memory/state.md`. What is *structurally* true: nothing is behind. 🔴 **The branches NO LONGER carry the same unpushed set** — `master` was pushed on 2026-08-27 and the eight bands were not, so `master` is briefly five sections ahead of every band **on the remote**, which is what gates 7/9/10/11 grade. §49 closes that skew; until it does, a gate sweep reports drift that exists only because the bands have not gone out. ⚠️ **The band-side set only grows until the next `mod_version` bump** |
| `master` | `minecraft_version=26.2`, `java_version=25`, `mod_version=1.3.0-SNAPSHOT` |
| releases | **NINE published at `v1.3.0`** (§43.4) — the declared 16-version scope is downloadable |
| build | ✅ **green on all nine**, each built on its own band this session (§44.3) |
| suite | ✅ **0 failures on all nine.** `master` and `mc/26.1.2` 1,861; the `1.21.x` bands 1,855–1,863. ⚠️ The spread is per-band gating, not a master-vs-band split |
| gates 7/9/10/11 | ✅ exit 0, measured post-push in §43.4. 🔴 **They have not seen §44 on any BAND** — all four prefer **remote** refs; `master`'s §44 commit is now pushed and the bands' are not, so a sweep run before §49's push measures that skew rather than the code |
| mixin gate | ✅ `--check` passes on `master` and `mc/26.1.2` (`ZERO=0 OK=60 SLICE=1`) |
| boot | ✅ `26.2` (§35) and ✅ `26.1.2` (§43.1, exit 0, 0 ERROR, 0 mixin failures) |
| gameplay | ✅ `26.2` **36 / 0 / 0**, re-measured 2026-08-27 against a jar rebuilt from HEAD (§47; was 30/30 at §35, +3 from §46 and +3 from §47). ✅ `26.1.2` 30/0/0 (§43.1) — that figure predates both phases and will read 36 on its next run. Mod-less control failing as it must |

📌 **The R-ac push hold is LIFTED** (owner, 2026-08-26, §43) and all nine branches went out.
🔴 **§44 reached `origin/master` on 2026-08-27 and its release run was REFUSED** (run `33049164237`,
step *"Refuse a stale mod_version"*) — **the push succeeded, only the release did not.** The reason was
already written down here, one owner ruling earlier: `build.gradle` sits inside
`release.yml`'s `paths:` filter, and every branch is at `1.3.0-SNAPSHOT` with `v1.3.0` already
published — so pushing it alone fires nine release runs that **R-t's stale-version gate refuses**.
It rides the next `mod_version` bump. See §44.4.

⚠️ **A clean compile and a green gate are STRUCTURAL.** §32 found a mixin bound to the *wrong live
method* while every structural gate read green, `mc/1.21.1` shipped a `/summon` origin gap past
67/67 injectors and a clean boot, and §42 found an injector on the new band that compiled perfectly
and bound to **nothing**. Application is not coverage.

**When the next `mod_version` bump ships:** run gates 7/9/10/11 in a local clone first (they grade the
remote otherwise), and expect §44 to ride out on all nine branches in that same push.

---

## What ships today — **all nine branches pushed and released**

✅ **Re-measured 2026-08-26 (session 25)** from `git ls-remote --tags`, each branch's
`gradle.properties` and its own `fabric.mod.json`. The previous edition of this table predated
§43's push: it was headed *"6 branches pushed"*, omitted `mc/26.1.2` entirely, said `master` had
**nothing released**, and listed every tag at `v1.2.0`. All four were false.

⚠️ **There is no per-version jar and there never was. One jar covers a band**, via the range in its
own `fabric.mod.json`.

| Branch | MC versions covered | `depends.minecraft` | Released tag |
|---|---|---|---|
| `master` | `26.2` | `~26.2` | `mc26.2-v1.3.0` |
| `mc/26.1.2` | `26.1`, `26.1.1`, `26.1.2` | `>=26.1 <26.2` | `mc26.1.2-v1.3.0` |
| `mc/1.21.11` | `1.21.11` | `~1.21.11` | `mc1.21.11-v1.3.0` |
| `mc/1.21.10` | `1.21.9`, `1.21.10` | `>=1.21.9 <1.21.11` | `mc1.21.10-v1.3.0` |
| `mc/1.21.8` | `1.21.6`, `1.21.7`, `1.21.8` | `>=1.21.6 <1.21.9` | `mc1.21.8-v1.3.0` |
| `mc/1.21.5` | `1.21.5` | `>=1.21.5 <1.21.6` | `mc1.21.5-v1.3.0` |
| `mc/1.21.4` | `1.21.4` | `>=1.21.4 <1.21.5` | `mc1.21.4-v1.3.0` |
| `mc/1.21.3` | `1.21.2`, `1.21.3` | `>=1.21.2 <1.21.4` | `mc1.21.3-v1.3.0` |
| `mc/1.21.1` | `1.21`, `1.21.1` | `>=1.21 <1.21.2` | `mc1.21.1-v1.3.0` |

**Shipped coverage is continuous `1.21` → `1.21.11` plus `26.1` → `26.2` — the declared
16-version scope, closed.** `mod_version` is `1.3.0-SNAPSHOT` on every branch; **nine** releases at
`v1.3.0`, one per band.

🔑 **Nothing in the eleven gates reads the remote TAG list.** Gates 9/10/11 compare branches; the
release sweep enumerates `gh release list`, which a bare tag is invisible to. **Re-read
`git ls-remote --tags` before repeating any statement about which tags exist.** ⚠️ One bare tag
does exist and is not a release: `v1.21.11-baseline`.

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
gate 6's `gameplay-smoke.sh` count. ✅ **Both have now run on `26.2`** — the suite at 1,861 green
and the smoke at **36 / 0 / 0** (§47) — and on `mc/26.1.2` too (§43.1).
⚠️ **That closes the evidence gap this paragraph was written for; it does not upgrade the audit.**
Identical source still proves only that the roster is uniform.

---

## What is genuinely missing — **nothing in the declared scope**

| Band | MC versions | Status |
|---|---|---|
| `1.21` … `1.21.11` | 12 versions, 7 bands | ✅ **SHIPPED**, all at **`v1.3.0`** (§43.4). This row read `v1.2.0` for the three sections after the bump landed |
| `26.2` | `26.2` | ✅ **SHIPPED — `master`.** Booted (§35), smoke **36/0/0** (§47), released `mc26.2-v1.3.0` |
| `26.1.x` | `26.1`, `26.1.1`, `26.1.2` | ✅ **CUT AND SHIPPED** as `mc/26.1.2` (§42, §43) — the three differ on **zero of 1424** records (§39), so one branch serves all three. Released `mc26.1.2-v1.3.0` |
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
| **R-ab** | **Gate 7 is permanently red — how? (owner-ruled 2026-08-26)** | ✅ **RULED (owner): a waiver file, `scripts/drift-waivers.txt`.** The same **11** `master`-only `26.x` commits read MISSING on every band; they cannot be back-ported by construction, they should have carried `Backport-not-needed:`, and **six are already published** so amending them is off the table — AGENTS.md forbids applying the opt-out retroactively regardless. So gate 7 fails on every run for a reason no work clears, which is exactly how the **12th** missing commit — a genuine forgotten back-port — becomes invisible. 🔑 **The waiver is structurally retroactive-only: it declares a `cutoff:` sha and REFUSES any waiver whose commit is not an ancestor of it**, so it cannot decay into a general escape hatch that repeals rule 3 — widening the exception means moving the cutoff, which is one reviewable line in a diff. **Rejected:** a `Backport-base:` marker moving each band's base below the `26.x` rename — cheaper to read, but it drops any genuine pre-rename drift out of the window along with the 11. ⚠️ **Explicitly NOT ruled:** lowering `--require-bands` or not running the gate. Both are the make-the-symptom-disappear move AGENTS.md's attempt-budget section names outright. Built in §40. |
| **R-ac** | **§41 + §42 scope and the push hold (owner-ruled 2026-08-26)** | ✅ **RULED (owner), three parts.** (1) **Build the whole R-aa bundle** — the per-band `java_version` key, the `mod_version` bump, §37's deferred commit B and the docs pass, as ONE change per branch, because each alone touches `gradle.properties` and fires a release run R-t refuses. (2) **Cut `mc/26.1.2` this session**, which closes the declared 16-version scope at nine branches — subject to the one check §39 left open: whether `0.155.2+26.1.2` actually LOADS on `26.1`, read out of the jar's own `fabric.mod.json`, not assumed. (3) 📌 **The push hold STANDS**, re-confirmed for the third session running. Nothing is pushed; re-ask. R14's ~24% suite flake is a second reason — a red release run is currently indistinguishable from a real regression. |

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

## §9 — the `26.x` band — ✅ CLOSED (shipped on `master` and `mc/26.1.2`)

🔴 **Its residue is NOT closed** — the open list at the foot of this section (R13, §31.5's
collision sites, `config.yml`, the bare loom id, the `TODO.md` invariant) is live and is the
reason this section is still here rather than in an archive.

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
| **9.3** translate the source **and** the tooling | ✅ **SOURCE DONE (§28–§33)**: 2,639 → 0 compile errors, 54 → 0 dead injectors, 186 → 1 red tests. ✅ **TOOLING DONE (§38, §36)** — see the checklist below, every box ticked |
| **9.4** cut the band | ✅ **DONE (§42).** (a) *Which branch?* — ruled: `26.x` **becomes `master`** and `1.21.11` was cut to `mc/1.21.11` (R-z, honouring R-f). (b) *One band or two?* — ✅ **TWO, and it is MEASURED now (§38), not inferred.** `probe-bands.py --versions 26.1,26.2 --control 26.2` on `master`: control green, **84 of 1424 records vary**, and the two versions do **not** collapse into one band. The ecosystem split (`[26.1, 26.1.1, 26.1.2]` vs `[26.2]`) reached the same conclusion by a different route. **`master` takes `26.2` alone; `26.1.x` is a future band.** ✅ **§39 closed the residue**: `26.1.1` and `26.1.2` were Loom-resolved and probed, and all three `26.1.x` versions are **identical on 1424 of 1424 records** — so the `26.1.x` line is ONE band, `mc/26.1.2`, and the declared 16-version scope needs exactly **one more branch** |
| **9.5** full ship gate | ✅ **RUN (§35, §43.4).** Gate 1 (suite 1,861), gate 2 (`ZERO=0 OK=60 SLICE=1`), gate 3 (`boot-check.sh`, exit 0) and gate 6 (**36/0/0**, control failing as it must) are all green on `26.2`; gates 7/8/9/10/11 ran post-push in §43.4 and 7/9/10/11 again in a local clone after §47. ⚠️ **Gates 4 (`config-id-audit.py`) and 5 (`brew-smoke.sh`) have NO recorded `26.2` run in this file** — that is an absence of evidence, not a failure, and it is the honest state of the ship gate on this branch |

### ✅ 9.3's tooling half — DONE (§36, §38). What used to read yarn names

The band cannot run its own gates until its tooling speaks official names.

- [x] ✅ **`probe-bands.py` speaks official names (§38)** — jar resolution moved to the shared
      `scripts/loomjar.py`, plus a **cross-naming refusal**, a **non-relocating control** and a
      `--self-test`. `master` is probeable, and 9.4(b) is now measured rather than inferred.
- [x] ✅ **`javap-mc.sh` resolves through the same module (§38)** — and it had been serving a
      **wrong answer on `1.21.11` since §33**: `sort | head -1` picked the mojmap `loom.mappings`
      jar over the yarn one, so `net.minecraft.item.ItemStack` read *class not found* under a
      confident `# javap against Minecraft 1.21.11` banner. Now has a `--self-test` too.
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
- [x] ✅ **The docs pass — DONE.** `README.md` carries the `26.2` and `26.1 – 26.1.2` rows, and the
      *"neither is the `26.x` line yet"* floor sentence is gone from `README.md` and `wiki/**` alike
      (grepped 2026-08-26, zero hits). The suite is 0-failure on all nine, so the deliberately-red
      test is green too. ⚠️ This row sat unchecked while the work was already shipped — the same
      *"a status row is never updated by the commit that changes the status"* shape as the vs-origin
      row above, and the second instance found in one pass.
- [x] ✅ **The owed gate-10/11 sweep — DONE (§37).** Gates 9, 10 and 11 all exit 0 at `--local`.
      **9 paths** for gate 10 (all `scripts/**`; `master` proved the winner on every one) and **1 key**
      for gate 11 (`mockito_version`). ⚠️ **`.gitignore` was NOT owed** — the `30.6` / `.hprof` claim
      that stood here was stale, all eight branches already carry blob `b432715f0`.
      🔑 Mockito had to move: `5.14.2` carries **Byte Buddy 1.15.4**, which rejects **Java 25 (class
      file 69)** outright, so every mocking test threw on the 26.x toolchain. *(An earlier edition of
      this line blamed `1.17.7` — that is the version `5.23.0` carries, i.e. the fix, not the fault.)*
      It stays `SHARED`, and its **floor is set by the newest JDK any band uses**, which R-aa makes
      band-local.
      ✅ **"A newer Mockito is fine on the bands' Java 21" is now MEASURED, not asserted** (§37): the
      full suite ran on all seven bands under `5.23.0` — 1846 to 1854 executed, 0 failures each, with
      75 test files importing `org.mockito`.
      🔴 `gradle.properties` is inside `release.yml`'s `paths:` filter, so the mockito half **rides the
      `mod_version` bump with R-aa** or it fires seven release runs R-t refuses. §37 splits the commits.
- [x] ✅ **R-aa — the per-band `java_version` key — BUILT (§41.1).** `release.yml` no longer pins a
      number: a *"Read the JDK level this band builds with"* step reads `java_version` from
      `gradle.properties`, errors on an absent or non-integer value, and feeds
      `java-version: ${{ steps.jdk.outputs.java_version }}` at line 142. Verified against the file
      2026-08-26. ⚠️ This row still read *"Ruled, not built. `release.yml:117` still pins `'21'`"*
      four sections after §41 marked all five of its sub-boxes done — third instance in one pass.
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

## §8.3, §22 – §47 — closed, and where the reasoning lives

Full text in **two** archives:
[TODO-multiversion-through-section-33.md](plans/completed/TODO-multiversion-through-section-33.md)
holds §8.3 and §22 – §33 (verbatim at `d5fb36dbf`), and
[TODO-multiversion-through-section-47.md](plans/completed/TODO-multiversion-through-section-47.md)
holds §37 – §47 (verbatim at `ee57abdec`, moved by §48 below).

🔑 **How to resolve a `§n` reference with no heading in this file:** §37 – §47 are in the
section-47 archive; §8.3 and §22 – §33 are in the section-33 archive; anything numbered lower
(§10.7, §21.6, the Pass-1 `item N` numbers cited in source comments) is in
`TODO-multiversion-through-phase-21.md`. Nothing has been deleted — only moved.
⚠️ **§34, §35 and §36 never had a heading in any file.** They were worked; their outcomes were
recorded in §9's table and 9.3's checklist above and nowhere else. A `§35` reference resolves to
those rows — and to `.agent/memory/`, which is not committed (R-n) and so does not exist in a fresh
clone.
🔑 **Source comments cite these numbers.** `CompilerErrorCapTest` names *TODO.md 44.2*;
`MockitoAgentPreinstalledTest` names *45.1* and *45.3*; `BandVersionLabelTest` names *Phase 13* and
*Phase 10*. Every one of them now resolves through an archive. **Do not renumber a section to tidy
this file** — the reference is in Java source that no doc pass reads.

| § | what it was | outcome |
|---|---|---|
| **8.3** | `mc/1.21.1`, the last `1.21.x` band | ✅ SHIPPED `mc1.21.1-v1.2.0`. Re-scoped by R-m′ — nothing ships disabled |
| **22** | the `1.20` line | 🚫 **WITHDRAWN (R-x)** before any of it was built. 22.1 (the `MACES` gate) had already shipped and stays |
| **23** | back-port §22.1, ship `v1.2.0` | ✅ seven releases at `v1.2.0`. Exposed **R-w′** — `mod_version` fell between two cross-branch guards |
| **24** | docs join the identity guard (R-y) | ✅ gate 10, 24 → 44 paths (**48 since §37**). Its **first run** found `master` + 5 bands serving a wiki claim false on `mc/1.21.1` — and the **band** was right |
| **25** | is the yarn→official table derivable? | ✅ yes — 100% of 1,389 symbols. 9.1's premise was measured **false**: yarn's `official` column is the *obfuscated* name |
| **26** | gate-10 sweep for the new script | ✅ all seven branches. 🔑 `drift-audit.py --master master` **prefers remote refs** — a pre-push run grades stale bands and prints `No drift` |
| **27** | the `26.x` toolchain, measured | ✅ builds on the **existing** Loom; `master` pinned to `26.2`. Java 25 collides with gate 10 → **R-aa** |
| **28** | drive the 33 ambiguous records to 0 | ✅ **33 → 4**, all truncated mixin selectors no tool will ever fix. 🔑 one record needs **two** mojmap names, so a name→name table is wrong — **drive the rename by CALL SITES** |
| **29** | the compiler-driven rename script | ✅ 2,643 → 126 errors. 🔑 the compiler loop is **necessary but not sufficient** — blind spot 1 above |
| **30** | apply the rename to `master`'s `src/` | ✅ 2,639 → **56**. The collision audit was under-reporting **52×** in its default mode (13th vacuous-guard sighting) |
| **31** | the genuine `26.x` API delta | ✅ main **and** test tree to 0. Then found **54 of 61 injectors dead** — *the mod compiled and did nothing* |
| **32** | re-derive every injector target | ✅ `--check` **passes**, `ZERO 54 → 0`, `OK 6 → 60`. Found blind spots 2, 3 and 4 |
| **33** | the first real suite on `26.2` | ✅ **186 red → 1**, 1,852 executed. Found blind spot 5; the last red is the deferred docs row |
| **34 – 36** | boot `26.2`; the manifest under official names | ✅ **No section was ever written for these three.** The outcomes are 9.3's and 9.5's rows above: `boot-check.sh` green on `26.2`, `mc-surface.txt` regenerated **1415 yarn → 1433 official**, nested-type spelling normalised at the import step (dotted rows **3 → 0**). 🔑 The `26.2` boot found that Loom registers **no `remapJar`** on `26.x` — a per-band build-graph difference no gate here looks at |
| **37** | the owed gate-10/11 sweep | ✅ gates 9, 10 and 11 exit 0 at `--local`. 🔑 Mockito had to move: `5.14.2` carries Byte Buddy `1.15.4`, which rejects **class file 69 (Java 25)** outright, so every mocking test threw on the `26.x` toolchain. Its `gradle.properties` half was split out as commit B and deferred to §41 |
| **38** | 9.3's tooling half — the scripts speak official names | ✅ `probe-bands.py` and `javap-mc.sh` resolve through the new shared `scripts/loomjar.py`, with a cross-naming refusal and a `--self-test` each. 🔑 `javap-mc.sh` had been serving a **wrong answer on `1.21.11` since §33** under a confident banner — `sort \| head -1` picked the mojmap jar over the yarn one |
| **39** | all four non-beta `26.x` releases, probed | ✅ the `26.1.x` line is **ONE band**: the three versions differ on **0 of 1424** records, so the declared 16-version scope needed exactly one more branch. 🔑 The jars were verified **before** the probe ran, not after |
| **40** | gate 7 was permanently red — how? (R-ab) | ✅ `scripts/drift-waivers.txt`, excusing the eleven un-propagatable `26.x` rename commits and **nothing else**, locked to a `cutoff:` sha so it can never cover anything newer. 🔑 Its own self-test was **vacuous in two ways** on the first pass |
| **41** | the R-aa bundle | ✅ one change per branch, all eight: the per-band `java_version` key (`release.yml` no longer pins `'21'`), the `mod_version` bump, §37's commit B (`mockito_version=5.23.0`) and the docs pass — bundled because each alone touches `gradle.properties` and fires a release run R-t refuses |
| **42** | cut `mc/26.1.2` | ✅ **the declared 16-version scope closed at nine branches.** 🔑 The cut tripped gate 9 immediately — the guard earning its keep — and an injector on the new band **compiled perfectly and bound to nothing** |
| **43** | the live harness on the new band, then THE PUSH | ✅ nine branches pushed, nine green runs, **nine releases at `v1.3.0`**. 🔑 Live evidence goes **before** a push, not after it. ⚠️ `BAND_COUNT` is **8, not 9** — `--require-bands` counts `mc/**` only and `master` lives outside that namespace |
| **44** | lift javac's 100-error cap | ✅ `-Xmaxerrs=10000` on all nine; the cap had reported **100 of 150 real errors under the same exit code**, which turned two sizings into guesses. Guarded by `CompilerErrorCapTest`, which reads the **resolved `compileJava` args**, not `build.gradle`'s text. 🔴 committed, **not pushed** |
| **45** | R14 — stop Mockito self-attaching | ✅ the agent is installed at VM start via `-javaagent` on all nine, and the self-attach warning is absent from all 166 result files. 🔑 The remedy recorded in the risk register was **wrong**: `-XX:+EnableDynamicAgentLoading` is compared against a warning string and never reaches the self-attaching call — it silences the tell and leaves the race running. 🔴 committed, **not pushed** |
| **46** | `SPAWN_ITEM_USE` gets harness coverage | ✅ `combat-spawn-egg-control` drives a real `mooshroom_spawn_egg` through carpet's `use once`, green on `26.2`, both mutations red. The recorded 08-19 verdict — *"carpet's `use once` will not place a spawn egg"* — was **false**, and its recorded fallback would have covered a **different** origin constant while reporting this gap closed |
| **47** | `DISPENSER` gets harness coverage | ✅ `combat-dispenser-control` fires a real `sniffer_spawn_egg` from a real dispenser on a redstone rising edge: **36 passed / 0 / 0** on `26.2`, three mutations red. **All three `PLAYER_PLACED` origins now have live coverage — `COMMAND`, `SPAWN_ITEM_USE`, `DISPENSER` — and there is no fourth** |

---

## §48 — the TODO cleanup: closed narrative to a fourth archive — ✅ DONE

**Tier 1, docs only.** Owner-scoped 2026-08-27: *"clean it up of old items, then tell me what is
left."* Three calls taken before the first edit — archive rather than delete; propagate to all eight
bands; **trim closed sections only**, leaving every open box and risk row exactly as written.

### Why this was owed

`TODO.md` had reached **2,619 lines, of which §37 – §47 were ~1,900 and every one of them closed.**
That is not a cosmetic problem. This file's own recurring defect is *"a status sentence is never
updated by the commit that changes the status, because nothing reads it"* — and it had struck **four
more times**, each one found by reading the file against disk rather than against the diff:

| the claim | what disk said |
|---|---|
| the seven `1.21.x` bands are *"all at `v1.2.0`"* | `v1.3.0`, since §43.4 — three sections above the claim |
| `26.2` *"has never booted"* | booted in §35, smoked at 36/0/0 in §47 |
| `26.1.x` *"**Not cut yet**"* | cut in §42, pushed and released in §43 |
| gate 1's suite and gate 6's smoke — *"**neither has ever run on `26.2`**"* | both green on `26.2`; the smoke line even carried the stale `29/29` |

🔑 **All four sat in the FIRST 135 lines** — the part of the file every session reads first, and the
part that is never touched by the commit doing the work. **Length is what made them survivable:** the
correction is always further down, in a section nobody re-reads once it is marked ✅.

### What moved, and what did not

- [x] ✅ **`plans/completed/TODO-multiversion-through-section-47.md`** — §37 – §47, **verbatim**, in
      file order (which is why §38 precedes §37). Fourth archive; the header block at the top of
      `TODO.md` now names four.
- [x] ✅ **One-line outcome rows** for §34 – §47 appended to the closed-sections table, and its
      heading widened to *§8.3, §22 – §47*.
- [x] ✅ **The `§n` resolution rule extended**, including the fact that **§34, §35 and §36 never had
      a heading in any file** — their outcomes are 9.3's and 9.5's rows and nothing else. That was
      discovered by grepping every archive for a `§3[456]` heading and finding none.
- [x] ✅ **The four false claims corrected**, plus the `⬜`/`🟡` markers that this file's own `[x]`
      boxes already contradicted (9.3's tooling half, 9.4, 9.5, §9's own heading).
- [x] ✅ **Every open box kept, verbatim.** Verified mechanically, not by reading: the set of
      `- [ ]` lines was diffed before and after.

**What I did NOT do:**

* **Not re-litigating any open item.** The owner scoped this to closed sections; R13, §31.5, the
  live play-test and manifest debt piece 1 are carried across untouched, wording included.
* **Not deleting anything.** Every line removed from `TODO.md` is in the new archive.
* **Not pushing.** The hold stands (owner, 2026-08-27); this rides out with §44 – §47 on the next
  `mod_version` bump. `TODO.md` is **not** in `release.yml`'s `paths:` filter, but a push carries the
  four held commits regardless — §44 touches `build.gradle`, which is.
* **Not touching `README.md` or `wiki/**`.** They are gate 10's byte-identity set; nothing here is a
  player-facing claim.

### 🔑 The one thing this cleanup could have broken

**Java source comments cite `§n` numbers from the moved sections** — `CompilerErrorCapTest` names
*TODO.md 44.2*, `MockitoAgentPreinstalledTest` names *45.1* and *45.3*. A cleanup that renumbered or
deleted those sections would break a cross-reference **no doc pass and no test reads**, and nothing
would go red. They resolve through the new archive, and both files say so.

### Propagation and rollback

`TODO.md` was blob `6dd7c0440` on **all nine branches** before this change, so the cleanup is
cherry-picked to all eight bands with a `Backport-of:` trailer — a docs-only commit reaches no band
on its own (Phase 21), and `drift-audit.py` prints *"No drift"* either way.

🟢 **Blast radius: two files, no `src/` change, nothing published, nothing pushed.** Undo is
`git revert <sha>` per branch, or `git reset --hard <tip>` while unpushed. The pre-§48 tips are
recorded in `.agent/memory/state.md`; the archive is a new file, so a revert also removes it.

---

## §49 — the `mod_version` bump that releases §44 – §48 — ⬜ IN PROGRESS

### What forced it

**`master` was pushed on 2026-08-27 and its release run was REFUSED** — run `33049164237`, failing
step *"Refuse a stale mod_version"*. `mc26.2-v1.3.0` already pointed at §43's commit, this run was
`284c129b`, and re-pointing a tag every clone has fetched would have orphaned the published release
as a same-tag draft. **R-t's gate did precisely its job**; the run is red because the guard worked,
not because anything is broken.

🔑 **The push SUCCEEDED. Only the release refused.** Those are different events and the distinction
is the whole shape of this section: all eight held commits (§44 – §48) are on `origin/master`, while
the eight bands are still `behind=0 ahead=8`. So the remote briefly carries **`master` five sections
ahead of every band** — and gates 7, 9, 10 and 11 all prefer **remote** refs, so a sweep run right
now would grade that skew and report drift that only exists because the bands have not gone out yet.

### The ruling

`mod_version` `1.3.0-SNAPSHOT` → **`1.3.1-SNAPSHOT`**, on all nine branches, in one commit each.

**PATCH, not minor, and the reason is measurable rather than a matter of taste:** the entire held set
touches **zero `src/main/` files**. It is `build.gradle` (§44's `-Xmaxerrs`, §45's `-javaagent`), two
guard tests, `scripts/gameplay_smoke_scenario.py` (§46 – §47), and docs. **The jar a player downloads
behaves identically to `v1.3.0`.** A minor bump would advertise a gameplay change that does not exist
and would spend the number the next real feature needs.

⚠️ **Nine releases whose only delta is build and test infrastructure is the honest outcome here, not
a wasteful one.** The bump is not optional: `build.gradle` and `gradle.properties` are both inside
`release.yml`'s `paths:` filter, and **a `paths:` filter matches the WHOLE PUSH** — so there is no
way to land §44 without firing a release run, and no way to make that run pass without a new version.
The alternative is leaving five sections held indefinitely.

### Why gate 11 is the instrument, and gate 7 is deliberately blind here

`gradle.properties` sits in `PROPAGATABLE_PREFIXES` **and** in `BAND_LOCAL_PATHS`
(`scripts/drift-audit.py:97-109`), so a commit touching only that file is excluded from gate 7 **by
construction** — a band pins its own `minecraft_version` there, and master's toolchain bumps must
never be reported missing. A `gradle.properties`-only commit therefore produces **no gate-7 row at
all**, in either direction.

🔴 **That is exactly the gap R-w′ was built for, and it is the failure this change could cause.** A
band left behind on `mod_version` does not go red anywhere obvious — it trips R-t's stale-version
gate and simply **stops releasing**, in a repo where a red release run is already the ordinary
outcome of a push. §23 found that by hand. **Gate 11 (`gradle-key-identity-audit.py`) is the only
mechanical check that the bump reached all nine**, so it is not optional this session.

### Steps

- [ ] **49.1** Bump `mod_version` on `master`, commit `gradle.properties` + this section together.
- [ ] **49.2** Propagate to all eight bands, one commit each, **with a `Backport-of:` trailer** —
      required by rule 2 even though gate 7 cannot see the commit. ⚠️ **Do NOT cherry-pick the
      `gradle.properties` hunk blind**: each band's copy differs on `minecraft_version` and
      `supported_minecraft_versions` by construction (R-a), so the bump is applied per branch and the
      result is verified by reading `mod_version` back out of all nine, never inferred from a
      cherry-pick exiting 0.
- [ ] **49.3** Run gates **7, 9, 10, 11** inside `git clone --local --no-hardlinks . <scratch>`, where
      `origin/*` maps onto the local branches. ⚠️ **A run in this working copy grades the STALE
      remote and answers a question nobody asked.** `--self-test` first on every one of them; **exit
      2 is not a pass** on 9, 10 and 11.
- [ ] **49.4** Push all nine. Expect **nine green release runs** and nine `v1.3.1` releases.
- [ ] **49.5** Verify by `gh release list` and `git ls-remote --tags`, **not** by the run list —
      🔑 nothing in the eleven gates reads the remote tag list, and a green run is not a release.
- [ ] **49.6** Record the outcome in a separate docs commit. **A status row cannot count the commit
      it is written in** — that error has already been made three times in three commits here.

### What this section is NOT doing

- **Not running gates 1 – 6 per band.** All nine were built green on their own band in §44.3, the
  suite was 0-failures on all nine, and the held set adds no `src/main/` change — so the jar's
  content is unchanged and a nine-band rebuild would re-measure what §44.3 already measured. **Gate 1
  still runs per branch via `release.yml` on the push**, which is what actually certifies these jars.
- **Not touching R13, §31.5's 562 collision sites, manifest debt piece 1, or `config.yml`.** All four
  stay open and owner-sequenced.
- **Not moving the drift waiver `cutoff:` sha.** Nothing here needs waiving.

### Rollback

🟢 **Blast radius before the push: one line in one file per branch, plus this section.** Undo is
`git reset --hard <tip>` per branch while unpushed.

🔴 **After the push it is not free, and this is the honest statement of it:** nine tags
`mc<VER>-v1.3.1` exist and nine releases are published, and the success path's own sweep **deletes
the previous release and tag on the same Minecraft line** — so `v1.3.0` is reaped by design. The undo
is therefore *forward*: a further bump, never a re-point of `v1.3.1`. ⚠️ **Do not delete a published
release to "undo" this** — deleting a tag DRAFTS its release rather than removing it, which is how
six orphaned drafts accumulated on 2026-08-13.
Pre-§49 tips are the §48 tips recorded in `.agent/memory/state.md`.

---

## Other open work — harness and playtest

*Closed items are summarised in one line each; the full reasoning is in the archives.*

**Closed 2026-08-19/20 — do not re-open:** `gradle-key-identity-audit.py` (ship-gate **11**, per-KEY,
closes R-w′) · `brew-smoke.sh` refuses an ambiguous jar glob instead of taking `find | head -1` ·
`combat-egg-control` → `combat-summon-control`, now asserting the **origin stamp** directly rather
than inferring it from XP staying flat · the smoke scorer discovers gated skills from the boot log
instead of grepping one hardcoded skill name (⚠️ the wording in `SkillAvailability#probe` is now an
**interface**, not prose) · the anti-vacuity floor is **derived**, not the constant `3 + sum(...)` it
used to be — exact at 30 when that closed, 36 as of §47, and it moves by itself with every phase
· R-y ruled `README.md`/`wiki/` **into** the identity guard (§24).

- [x] ✅ **THE SPAWN-EGG HALF IS DONE (§46, 2026-08-26) — and the 08-19 verdict was WRONG.**
      `combat-spawn-egg-control` drives a real `mooshroom_spawn_egg` through carpet's `use once`,
      green on `26.2`, both mutations red at exit 1. **Carpet's `use once` places a spawn egg
      perfectly well**; the recorded *"it will not"* was a false conclusion drawn from three correct
      refutations, and the recorded fallback was worse than useless — a **dispenser** yields
      `SpawnReason.DISPENSER`, a DIFFERENT constant, so it would have covered a third origin while
      reporting this gap closed. Full reasoning and the five refuted hypotheses in §46.
      ⚠️ `DISPENSER` was left as the only `PLAYER_PLACED` constant with no harness coverage.

- [x] ✅ **AND SO IS THE DISPENSER HALF (§47, 2026-08-27) — the set is CLOSED.**
      `combat-dispenser-control` loads a real `sniffer_spawn_egg` into a real dispenser and fires it
      with a redstone rising edge, green on `26.2` at **36 passed / 0 / 0**, with **three** mutations
      red at exit 1. The species is `sniffer` and not the mooshroom deliberately: the phase above
      creates a mooshroom of its own, and a probe that can tag the previous phase's mob is a **false
      PASS**. 🔑 M3 (power before loading) turned the rising-edge reasoning from an argument into a
      measurement. **All three constants mapping to `PLAYER_PLACED` — `COMMAND`, `SPAWN_ITEM_USE`,
      `DISPENSER` — now have live harness coverage, and there is no fourth.**

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
- [x] ✅ **`gameplay-smoke.sh`'s path bridge — CLOSED 2026-08-26 (§43.1).** The three call sites that
      needed a running server (`--commands`, `--check <log>`, `--check --profile`) all executed in the
      `26.1.2` run, which scored 30/30 with the mod-less control failing as it must.
- [x] ✅ **`ci-watch.sh --mutate` on Windows — CLOSED 2026-08-26 (§43.4).** Demonstrated, not
      asserted: 9 cases pass including *"path bridge holds with MSYS conversion OFF"*, and mutation
      **M5** (*hand the raw bash path to a native child*) is caught.

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
6. `scripts/gameplay-smoke.sh` — **36 passed / 0 failed / 0 inconclusive** on `master`, and
   `GAMEPLAY_SMOKE_CONTROL=1` must **fail**.
   ⚠️ **This number moves whenever a phase is added, and it went stale unnoticed once already** — it
   read `29/29` through both §46 (+3) and up to §47 (+3), i.e. the caveat-expiry pass missed it twice
   because the phase commits touched the scenario file and not this line. The total is
   `3 + <version gates the boot log declares> + sum(len(up) + len(flat))` over the phase table, so a
   band declaring a different number of gates legitimately differs by that much. **Read the count out
   of `PHASES` rather than trusting this line**, and a total *below* the floor is the scorer's own
   anti-vacuity failure, not a phase failure.
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

✅ **R14 — CLOSED on `master` 2026-08-26 by §45** (committed, **not pushed** — it rides the next
`mod_version` bump with §44). Mockito's agent is now installed at VM start via `-javaagent`, so
`PremainAttachAccess` returns at **step 1** and never reaches `ByteBuddyAgent.install()`. Proved by
mechanism, not by one green run: the *"Mockito is currently self-attaching"* line and the dynamic
agent-load warning are **absent from all 166 files** in `build/test-results/test/`, where they were
present before. Guarded by `MockitoAgentPreinstalledTest`, 4/4 mutations observed.
🔴 **The remedy recorded below was WRONG, and wrong in the direction that hides the defect.**
`-XX:+EnableDynamicAgentLoading` is compared against a warning string at step 3 and is **never
consulted at step 4**, which self-attaches regardless — it silences the only visible tell and leaves
the race running. The guard now asserts that flag is **absent**. The original text is kept below
because the diagnosis was right and only the fix was wrong.

🆕 **R14 — the suite flakes at ~24% of tests, and the flake is indistinguishable from a regression.**
Byte Buddy's inline mock maker attaches an agent per test fork through an external helper process;
`maxParallelForks = 4` races it. Measured 2026-08-25 on `mc/1.21.10`: **449 of 1846 red**, then
**1846/0** on a clean re-run of the same commit. **Not caused by the Mockito bump** — the inline
mock maker is the 5.x default in `5.14.2` too. `release.yml` runs this suite on every push, where a
red run is already the normal outcome, so the failure mode is that a REAL regression gets re-run
away as "probably the flake". Remedy (`-XX:+EnableDynamicAgentLoading` or fewer forks) touches
`build.gradle`, which is inside `release.yml`'s `paths:` filter — same deferral shape as R-aa.


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

- [x] ✅ **`-Xmaxerrs` — CLOSED 2026-08-26 (§44).** Lifted to 10,000 on `master` (`ee1340bd7`) and
      cherry-picked to all eight bands; all nine built green. Guarded by `CompilerErrorCapTest`,
      which reads the **resolved `compileJava` args**, not `build.gradle`'s text. 🔴 **Committed, NOT
      pushed** — it rides the next `mod_version` bump, see §44.4.
- [x] ✅ **`mc/26.1.2`'s boot check and gameplay smoke — CLOSED 2026-08-26 (§43.1),** before the
      band was ever pushed: boot-check **exit 0** (0 ERROR, 0 mixin failures) and gameplay smoke
      **30 passed / 0 failed / 0 inconclusive**, with the mod-less control failing as it must.

- [x] ✅ **R14's suite flake — CLOSED 2026-08-26 (§45).** Mockito's agent is installed at VM start
      via `-javaagent` on `master` (`b92be8721`) and cherry-picked to all eight bands; all nine built
      green with the self-attach warning gone from every result file. Guarded by
      `MockitoAgentPreinstalledTest`, which reads **this JVM**, not `build.gradle`'s text. 🔴
      **Committed, NOT pushed** — it rides the next `mod_version` bump with §44, see §45.6.
      ⚠️ R14's originally-recorded remedy (`-XX:+EnableDynamicAgentLoading`) was **wrong**: it is
      compared against a warning string and never reaches the self-attaching call. Do not re-add it.

- [ ] 🔴 **Manifest debt piece 1** — see *Other open work*. Piece 2 shipped as
      `scripts/manifest-identity-audit.py` (Phase 18).
- [ ] 🟡 **The `--require-bands` floors are hand-maintained** in `.github/workflows/drift-audit.yml`
      and in ship-gate steps 9, 10 and 11. **Now 8**, raised in §43.3 in the same push that put
      `mc/26.1.2` and `mc/1.21.11` on the remote. ⚠️ **8, not 9** — `--require-bands` counts
      `mc/**` only, and `master` lives outside that namespace; one too many returns exit 2 while
      the same run still prints *"No drift"*. R-x withdrew R-v's extra cuts, so the declared scope is
      closed and **no further raise is owed**. It stays listed because nothing reminds you: a stale
      floor is under-strict and the audit still passes.
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
