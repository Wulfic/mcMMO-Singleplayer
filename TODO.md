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
| vs `origin` | 🔴 **THIS ROW NO LONGER CARRIES A NUMBER, AND THAT IS THE FIX.** It was wrong **three times in three commits** — `1` when the truth was `2`, corrected to `3` and stale one commit later, then `six` written into the commit that made it seven. **A status row cannot count the commit it is written in**, so it stops trying. The measurement is one command and it is never stale: `git rev-list --left-right --count origin/<b>...<b>` per branch, or the loop over all nine in `.agent/memory/state.md`. What is *structurally* true: nothing is behind. ✅ **The skew that stood here is GONE** — §49 closed it and §50 pushed all nine again; every branch measured `0 behind / 5 ahead` immediately before the §50 push and `0 / 0` after. Re-measure rather than trusting this sentence |
| `master` | `minecraft_version=26.2`, `java_version=25`, `mod_version=1.3.2-SNAPSHOT` |
| releases | **NINE published at `v1.3.2`** (§50) — the declared 16-version scope is downloadable, and the amethyst/chain bonus-drop fix is in every one. Verified by `gh release list` + `git ls-remote --tags`, **zero drafts, zero prereleases**; §49's nine `v1.3.1` releases were **reaped by the success sweep**, one per Minecraft line, exactly as designed |
| build | ✅ **green on all nine**, each built on its own band this session (§44.3) |
| suite | ✅ **0 failures on all nine.** `master` **1,869** and `mc/1.21.1` **1,867** re-measured locally in §50 (+4 from `ConfigYamlBonusDropsTest`); the other seven last measured 1,855–1,863 and each went green again in its own §50 release run. ⚠️ The spread is per-band gating, not a master-vs-band split |
| gates 7/9/10/11 | ✅ **exit 0, none exit 2**, re-measured in §50 on a fresh `git clone --local --no-hardlinks` carrying all nine §50 tips. Gate 8 (`ci-watch.sh`) also exit 0 post-push, with all 5 mutations caught. ⚠️ All four prefer **remote** refs, so push first or clone locally |
| mixin gate | ✅ `--check` passes on `master` and `mc/26.1.2` (`ZERO=0 OK=60 SLICE=1`) |
| boot | ✅ `26.2` (§35) and ✅ `26.1.2` (§43.1, exit 0, 0 ERROR, 0 mixin failures) |
| gameplay | ✅ `26.2` **36 / 0 / 0**, re-measured 2026-08-27 against a jar rebuilt from HEAD (§47; was 30/30 at §35, +3 from §46 and +3 from §47). ✅ `26.1.2` 30/0/0 (§43.1) — that figure predates both phases and will read 36 on its next run. Mod-less control failing as it must |

📌 **The R-ac push hold is LIFTED** (owner, 2026-08-26, §43) and all nine branches went out.
🔴 **§44 reached `origin/master` on 2026-08-27 and its release run was REFUSED** (run `33049164237`,
step *"Refuse a stale mod_version"*) — **the push succeeded, only the release did not.** The reason was
already written down here, one owner ruling earlier: `build.gradle` sits inside
`release.yml`'s `paths:` filter, and every branch is at `1.3.0-SNAPSHOT` with `v1.3.0` already
published — so pushing it alone fires nine release runs that **R-t's stale-version gate refuses**.
✅ **CLOSED by §49** (2026-08-27): `mod_version` went to `1.3.1-SNAPSHOT` on all nine branches,
all nine were pushed, and all nine release runs went **green** at `v1.3.1`. See §44.4 for why the
hold existed and §49 for how it was lifted.

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
| `master` | `26.2` | `~26.2` | `mc26.2-v1.3.1` |
| `mc/26.1.2` | `26.1`, `26.1.1`, `26.1.2` | `>=26.1 <26.2` | `mc26.1.2-v1.3.1` |
| `mc/1.21.11` | `1.21.11` | `~1.21.11` | `mc1.21.11-v1.3.1` |
| `mc/1.21.10` | `1.21.9`, `1.21.10` | `>=1.21.9 <1.21.11` | `mc1.21.10-v1.3.1` |
| `mc/1.21.8` | `1.21.6`, `1.21.7`, `1.21.8` | `>=1.21.6 <1.21.9` | `mc1.21.8-v1.3.1` |
| `mc/1.21.5` | `1.21.5` | `>=1.21.5 <1.21.6` | `mc1.21.5-v1.3.1` |
| `mc/1.21.4` | `1.21.4` | `>=1.21.4 <1.21.5` | `mc1.21.4-v1.3.1` |
| `mc/1.21.3` | `1.21.2`, `1.21.3` | `>=1.21.2 <1.21.4` | `mc1.21.3-v1.3.1` |
| `mc/1.21.1` | `1.21`, `1.21.1` | `>=1.21 <1.21.2` | `mc1.21.1-v1.3.1` |

**Shipped coverage is continuous `1.21` → `1.21.11` plus `26.1` → `26.2` — the declared
16-version scope, closed.** `mod_version` is `1.3.1-SNAPSHOT` on every branch; **nine** releases at
`v1.3.1`, one per band.

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
| `1.21` … `1.21.11` | 12 versions, 7 bands | ✅ **SHIPPED**, all at **`v1.3.1`** (§49). This row read `v1.2.0` for the three sections after the bump landed |
| `26.2` | `26.2` | ✅ **SHIPPED — `master`.** Booted (§35), smoke **36/0/0** (§47), released `mc26.2-v1.3.1` |
| `26.1.x` | `26.1`, `26.1.1`, `26.1.2` | ✅ **CUT AND SHIPPED** as `mc/26.1.2` (§42, §43) — the three differ on **zero of 1424** records (§39), so one branch serves all three. Released `mc26.1.2-v1.3.1` |
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
collision sites, the bare loom id, the `TODO.md` invariant) is live and is the reason this section is
still here rather than in an archive. ✅ `config.yml` **left that list on 2026-08-27 (§50)**.

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
- [x] ✅ **R13 — CLOSED 2026-08-27 (§54).** `--overload-rebind` reports the sites ARMED for a
      silent rebind, from ONE branch and BEFORE a version moves: **2,251 MC call sites, 129 owner
      types resolved against the jar, ZERO armed.** 🔑 The planned cross-band descriptor diff
      **could not work** — master is official-named and the bands are yarn, so every descriptor
      differs by construction. 🔴 The load-bearing half is the **return-type** condition: without
      it all four `Mth.clamp` sites report and javac rejects every one at the use site.
      ⚠️ Composes with `--type-agnostic`; neither is complete alone. Original text: §33.4 closed
      the `equals` family only. *Any*
      method whose narrow overload is deleted while a wider one survives rebinds **silently**, because
      javac must accept it by the language rules. No gate covers the general case.
- [x] ✅ **§31.5 — the collision review list — CLOSED 2026-08-27 (§51).** `--receivers` resolves
      every site's receiver from bytecode in two stages — is it an MC type (542 → 200), and does
      that type actually carry the collision (200 → **39**). **All 39 read by hand, zero defects.**
      ⚠️ **The carried "562 over 38" was stale**; it measured **542 over 35**, closed incidentally in
      §31 – §33 with no commit updating the row.
      🔑 Zero is credible only because 51.3 re-introduced the real `Registry#getId` defect and
      **watched it survive the filter and get reported** — on the exact line it originally lived on.
      🔑🔑 **The finding is where the risk actually sits:** for all 8 surviving names the yarn and
      mojmap members differ in arity or return type, so javac catches a mis-bind. The one that got
      through did so because `equals(Object)` **erased** the type difference. See the new row below.
      Sampling says they are dominated by false positives (`Map.get`, `List.add` on plain Java
      collections sharing a name with a colliding MC member) — but `Registry#getId` was 42 sites,
      **12 of which javac never mentioned**, and one was a live `equals()` in main source returning
      false forever. Plan: (a) filter mechanically on receiver type, reporting the before/after count;
      (b) read every survivor by hand, recording the count **reviewed**, not just fixed; (c) a
      mutation re-introducing one `BuiltInRegistries.*.getId(` that must survive the filter and be
      reported — **a filter never shown to catch anything is a filter that removes everything.**
- [x] ✅ **`config.yml` joins the config-id gate — CLOSED 2026-08-27 (§50).** It is now read by
      **both** halves: `config-id-audit.py` (9 sections, +186 refs → **875 across 7 files**) and a new
      `ConfigYamlBonusDropsTest` on the live registry, inside gate 1.
      🔑 **This row understated the defect by 26×.** It named one dead id; measuring found **26 dead
      on every supported version**, and the two that mattered were **not** the one that had been
      noticed — `Block_Of_Amethyst` (not a registry id on any version, while `experience.yml` pays it
      500 XP) and a `Chain` with no `Iron_Chain` beside it (chains lost their bonus roll on the four
      newest bands). **A carried row naming a specific defect is a lower bound, never a count.**
- [x] ✅ **`advanced.yml` joins the config-id gate — CLOSED 2026-08-27 (§52), and the row was
      pointing at the wrong file.** `advanced.yml` has ONE id-keyed table (`Hunter.Tiers.Overrides`,
      two keys) and **both are live on every supported version** — the work as specified finds
      nothing. The real gap was a whole KIND: `mc-ids.txt` carried `block` and `item` only, so all
      138 entity-keyed rows had never been audited on any branch. 🔴 **Six defects, two of them
      severe**: `Vex` and `Creaking` paid **zero** combat XP (an ABSENT row, which no id audit can
      see), and `Snow_Golem`'s deliberate `0.0` was inert under the Bukkit spelling. See §52.
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
| **44** | lift javac's 100-error cap | ✅ `-Xmaxerrs=10000` on all nine; the cap had reported **100 of 150 real errors under the same exit code**, which turned two sizings into guesses. Guarded by `CompilerErrorCapTest`, which reads the **resolved `compileJava` args**, not `build.gradle`'s text. ✅ **pushed and released at `v1.3.1`** (§49) |
| **45** | R14 — stop Mockito self-attaching | ✅ the agent is installed at VM start via `-javaagent` on all nine, and the self-attach warning is absent from all 166 result files. 🔑 The remedy recorded in the risk register was **wrong**: `-XX:+EnableDynamicAgentLoading` is compared against a warning string and never reaches the self-attaching call — it silences the tell and leaves the race running. ✅ **pushed and released at `v1.3.1`** (§49) |
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

## §49 — the `mod_version` bump that releases §44 – §48 — ✅ DONE

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

- [x] **49.1** Bump `mod_version` on `master`, commit `gradle.properties` + this section together.
- [x] **49.2** Propagate to all eight bands, one commit each, **with a `Backport-of:` trailer** —
      required by rule 2 even though gate 7 cannot see the commit. ⚠️ **Do NOT cherry-pick the
      `gradle.properties` hunk blind**: each band's copy differs on `minecraft_version` and
      `supported_minecraft_versions` by construction (R-a), so the bump is applied per branch and the
      result is verified by reading `mod_version` back out of all nine, never inferred from a
      cherry-pick exiting 0.
- [x] **49.3** Run gates **7, 9, 10, 11** inside `git clone --local --no-hardlinks . <scratch>`, where
      `origin/*` maps onto the local branches. ⚠️ **A run in this working copy grades the STALE
      remote and answers a question nobody asked.** `--self-test` first on every one of them; **exit
      2 is not a pass** on 9, 10 and 11.
- [x] **49.4** Push all nine. Expect **nine green release runs** and nine `v1.3.1` releases.
- [x] **49.5** Verify by `gh release list` and `git ls-remote --tags`, **not** by the run list —
      🔑 nothing in the eleven gates reads the remote tag list, and a green run is not a release.
- [x] **49.6** Record the outcome in a separate docs commit. **A status row cannot count the commit
      it is written in** — that error has already been made three times in three commits here.

### The outcome — nine green runs, nine releases at `v1.3.1`

✅ **All nine release runs completed `success`**, and — the check that actually matters — **nine
releases are published**, verified from `gh release list` and `git ls-remote --tags`, **not** from
the run list. 🔑 **A green run is not a release**: nothing in the eleven gates reads the remote tag
list, so the run conclusion and the release set are two separate measurements and both were taken.

| Branch | run | tag now published |
|---|---|---|
| `master` | `33049929276` ✅ | `mc26.2-v1.3.1` |
| `mc/26.1.2` | `33049935170` ✅ | `mc26.1.2-v1.3.1` |
| `mc/1.21.11` | `33049937914` ✅ | `mc1.21.11-v1.3.1` |
| `mc/1.21.10` | `33049940328` ✅ | `mc1.21.10-v1.3.1` |
| `mc/1.21.8` | `33049943300` ✅ | `mc1.21.8-v1.3.1` |
| `mc/1.21.5` | `33049946991` ✅ | `mc1.21.5-v1.3.1` |
| `mc/1.21.4` | `33049949732` ✅ | `mc1.21.4-v1.3.1` |
| `mc/1.21.3` | `33049951994` ✅ | `mc1.21.3-v1.3.1` |
| `mc/1.21.1` | `33049955125` ✅ | `mc1.21.1-v1.3.1` |

**The sweep reaped the nine `v1.3.0` releases and tags, one per Minecraft line, exactly as designed.**
`git ls-remote --tags` now returns exactly those nine `mc*` tags plus the known bare
`v1.21.11-baseline`. ✅ **Zero drafts and zero prereleases** — checked explicitly with
`gh release list --json isDraft,isPrerelease`, because the *"deleting a tag DRAFTS its release"*
failure mode is what accumulated six orphans on 2026-08-13 and a name-keyed sweep skips them.

⚠️ **`master`'s run was read step-by-step, not just by conclusion.** *"Refuse a stale mod_version"*
**✓** — the same step that refused run `33049164237` — then *"Create and push tag"*, *"Build"*,
*"Publish release"*. And the suite genuinely ran: the log shows a bare **`> Task :test`** with
`8 actionable tasks: 8 executed`, not `FROM-CACHE` and not `UP-TO-DATE`.

### The gate evidence behind the push

| gate | result |
|---|---|
| **7** `drift-audit.py` | ✅ exit 0 — **0 MISSING on all eight bands**, no waiver reported STALE |
| **9** `manifest-identity-audit.py` | ✅ exit 0 — nine distinct `mc-surface.txt` |
| **10** `branch-file-identity-audit.py` | ✅ exit 0 — **50 shared paths byte-identical on nine** |
| **11** `gradle-key-identity-audit.py` | ✅ exit 0 — `mod_version` identical on nine, `minecraft_version` distinct on nine |
| **8** `ci-watch.sh` | ✅ exit 0 on `master` `fe0ebb32f`, run `33049929276` — self-test **9 passed / 0 failed** with **all 5 mutations caught** |

⚠️ **7, 9, 10 and 11 were run inside `git clone --local --no-hardlinks . <scratch>`**, where
`origin/*` maps onto the local branches. A run in the working copy would have graded the **stale
remote** — which at that moment held `master` five sections ahead of eight bands, i.e. it would have
reported drift that existed only because the bands had not gone out yet. `--self-test` passed on all
four **before** each real run; none returned exit 2.

✅ **`BandVersionLabelTest` + `BandToolchainLevelTest` were run locally against
`-Pmod_version=1.3.1`** before the push — **20 tests executed, 0 failures, 0 skipped**, counted out
of the JUnit XML rather than read off the `BUILD SUCCESSFUL` line. `1.3.1-SNAPSHOT` survives Fabric's
own parser and the resolved-version check.

### 🔑 What this section is worth remembering for

**A red run is not a failed push.** The report that opened this section was *"I pushed and forgot to
version bump so it failed"* — and `git rev-list --left-right --count origin/master...master`
returned `0 0`. **The push had succeeded; only the release refused.** The two need *opposite*
repairs, and the wrong reading (*"push again"*) was already true and would have changed nothing while
leaving the real condition — `master` alone on the remote, five sections ahead of every band —
undiagnosed. **Measure before believing any sentence about what a red run means.**

⚠️ **A `Backport-of:` trailer with no blank line before it is invisible to `%(trailers:key=...)` but
still found by `git log --grep`.** `mc/1.21.1`'s §47 commit is shaped that way and was nearly written
up here as a rule-2 violation. It is not one: `drift-audit.py:72` matches with a **multiline regex**,
not git's trailer parser, so gate 7 is unaffected — and it read 0 MISSING. **Do not "fix" it by
rewriting published history.**

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

## §50 — `config.yml` joins the config-id gate, and the two live defects it finds — ✅ DONE

### What forced it

`config.yml` has been outside ship-gate **4** since gate 4 existed. The carried row said it *"carries
at least one dead id (`Chain`)"* — measured 2026-08-27, that undercounts by a factor of twenty-six.

🔴 **And the hole is wider than the script.** TODO 5.5 has two halves: `config-id-audit.py` (the
cross-version half) and `ConfigItemIdResolutionTest` (the live-registry half, which runs unattended
inside gate 1). **`config.yml` is in neither.** The test reads `treasures.yml`,
`fishing_treasures.yml`, `repair.vanilla.yml` and `salvage.vanilla.yml` and stops. So the largest
behavioural id table in the jar — 210 references across 9 sections — has **no** id check of any kind,
automated or manual, and has had none for the whole life of the port.

### What was measured, before any edit

A throwaway probe extended the extractor and resolved `config.yml` against the committed
`scripts/mc-ids.txt` for all 14 versions. **210 references, 9 sections, 26 dead on every supported
version.** For comparison, the five files already in the gate contribute 689 references with
**zero** dead. The dead 26 fall into three classes, and the class boundary is the whole point:

| Class | n | Example | Live counterpart present? |
|---|---|---|---|
| A real id of the **wrong kind for the seam** — an **item** under a **block**-keyed section | 21 | `Bonus_Drops.Mining.Coal` | ✅ `Coal_Ore`, `Deepslate_Coal_Ore` |
| **Not a registry id at all**, counterpart already present | 4 | `Eyeblossom`, `Lapis_Lazuli_Ore`, `Redstone_Dust`, `Nether_Quartz` | ✅ `Open_Eyeblossom`, `Lapis_Ore`, `Redstone_Ore`, `Quartz` |
| 🔴 **Not a registry id, and NO counterpart** | 1 | `Bonus_Drops.Mining.Block_Of_Amethyst` | ❌ no `Amethyst_Block` row exists |

⚠️ **The class split above is measured (50.2), not counted by eye — and the first count was wrong.**
The plan as first written said *"22 + 2"*; resolving each dead token under the *opposite* kind says
**21 + 5**. Nothing downstream changed, but the arithmetic is the evidence, so it is the measured
figure that stands here.

🔑 **A 27th issue is NOT in that table, because it is not a dead row.** `Bonus_Drops.Mining.Chain`
is correctly classified *"live on an older band"* — the defect is the **absent successor**,
`Iron_Chain`, which no dead-row scan can see. Two different failure shapes, two different fixes: 25
deletions, 1 rename, 1 **addition**.

🔑 **The kind per section is traced to the call site, never guessed** — the same discipline
`experience.yml` needed, and for the same reason: the blunter rules are wrong in both directions.

| Section | Kind | Seam |
|---|---|---|
| `Bonus_Drops.Mining` | BLOCK | `MiningManager#isBonusDropsEligible(blockRegistryId, …)` |
| `Bonus_Drops.Herbalism` | BLOCK | `HerbalismManager:230` |
| `Bonus_Drops.Woodcutting` | BLOCK | `WoodcuttingManager:87/102/122` |
| `Bonus_Drops.Smelting` | ITEM | `SmeltingManager:97` — the **result**, not the input |
| `Bonus_Drops.Cooking` | ITEM | `CookingManager:336` — the **result** |
| `Green_Thumb_Replanting_Crops` | BLOCK | `GeneralConfig#isGreenThumbReplantableCrop` |
| `Skills.Cooking.Power_Cook_Effects` | ITEM (**keys only**) | `GeneralConfig#getPowerCookEffect` — the *value* is a status effect |

That is why the 22 are dead rather than merely redundant: `MiningManager` hands the seam a **broken
block** id, so `Coal` — a real item — can never match, on any version, forever.

### 🔴 The two live defects

Neither is drift. Both are wrong on **every** supported version, or on the four newest bands:

1. **Amethyst blocks pay XP and drop nothing extra.** `experience.yml` reads `Amethyst_Block: 500`;
   `config.yml` reads `Block_Of_Amethyst: true` — **not a registry id on any version**, and there is
   no `Amethyst_Block` row beside it. So mining an amethyst block has paid 500 Mining XP and been
   ineligible for double drops on every band since the port. The two files disagree about the same
   block, and only the one nothing audited is wrong.
2. **Chains lose their double drops on the newest four bands.** `minecraft:chain` became
   `minecraft:iron_chain` in the Copper Age drop. `experience.yml` already carries **both** names —
   that is the both-names pattern this repo settled on, and the reason `Chain` is correctly reported
   *"live on an older band"* rather than dead. `config.yml` carries **only** `Chain`, so on
   `1.21.10`, `1.21.11`, `26.1.2` and `26.2` — master included — a mined chain gets no bonus roll.

⚠️ **Both fixes are ADDITIONS of a leaf key, and additions reach existing installs.**
`ConfigLoader#copyMissingDefaults` back-fills any leaf present in the shipped defaults and missing
from the player's file, then saves. So this is **not** the `ConfigRetunes` shape where editing a
shipped default reaches nobody — a player with a config from §1 gets `Amethyst_Block` and
`Iron_Chain` on next boot. Deleting a dead key does **not** remove it from a player's file, which is
harmless precisely because the key is dead there too.

### The ruling — delete the 25, rename 1, add 1

Owner-ruled 2026-08-27. The 25 inert rows are **deleted**, not excluded.

🔑 **The alternative was to keep them and add a per-row exclusion list, and that is the move this
repo has a standing rule against.** An exclusion widened to turn a gate green is how gate 7 spent
weeks reporting nothing (R-ab), and a gate that cannot go green without one is not a gate. The rows
have no defenders: **every one of the 25 has a verified live counterpart in the same section**, so
deleting them is provably inert — that verification is step 50.2 and it happens before the deletion,
not after. 50.2 ran green: *25 rows proven redundant, 1 live defect identified*, with each claimed
counterpart checked twice — present in that same section, **and** resolving on the control.

**Rejected: correcting the 25 in place instead of deleting.** `Coal` → what? The section is keyed on
the broken block, and `Coal_Ore` and `Deepslate_Coal_Ore` are already listed. A "correction" would
add a duplicate of a row that exists. Deletion is the correction.

### Steps

- [x] ✅ **50.1 — DONE.** Extend `extract()` in `scripts/config-id-audit.py` with the 9 `config.yml` sections
      above, each scoped the way `_xp_rows` is scoped — **by parent section, never by key name**.
      `config.yml` is 676 lines of `Name: true` rows that are *not* ids (`Particles.Bleed`,
      `Skills.*.Level_Cap`, `Commands.Skills.URL_Links`), so an unscoped scan drowns the control.
- [x] ✅ **50.2 — DONE.** ⚠️ **Prove the counterpart before deleting anything.** For each dead row,
      the live row in the same section that covers the same object is named, then checked twice:
      **present in that section**, and **resolving on the control**. A stale or unmapped entry fails
      the script rather than being skipped. Result: **25 safe to delete, 1 not covered**
      (`Block_Of_Amethyst`) — and it caught the plan's own hand-count error. **This is gate 2 of the
      five; the deletion did not happen until it had run.**
- [x] ✅ **50.3 — DONE.** `src/main/resources/config.yml` **676 → 652 lines**: 25 deleted,
      `Block_Of_Amethyst` → `Amethyst_Block`, `Iron_Chain: true` added beside `Chain: true`.
      Applied by a dry-run-default script that anchors on exact line text **inside the resolved
      section** and refuses any anchor matching other than once. 🔑 **The section scoping earned
      itself on this one edit:** `Quartz` is listed under **both** `Bonus_Drops.Mining` (dead — the
      seam is keyed on the block) and `Bonus_Drops.Smelting` (live — the seam is keyed on the
      result). A whole-file match would have deleted the live row and nothing would have gone red.
- [x] ✅ **50.4 — DONE.** Extend `--self-test` in both directions. MUST-FIND: one row from each of the 9 new
      sections. MUST-NOT-FIND: the decoys that make this file dangerous — a `Particles`-style
      `Bleed: true`, a `Level_Cap: 0`, an `Item_Amount: 10` next to `Item_Material`, and a
      `Bonus_Drops` subsection for a skill **not** in the kind map. 🔑 **A filter never shown to
      catch anything is a filter that removes everything.**
      **Measured, not asserted: 5 mutations, all 5 CAUGHT, control green** — drop the top-level
      scoping (the `Skills.Mining.Enabled_For_PVP` decoy leaks), read the Power-Cook *value* instead
      of the key, turn the fail-closed guard into a silent skip, stop reading one `Bonus_Drops`
      section, read `Green_Thumb` at the wrong indent. Fixture: **27 refs / 24 sections**, 26 required
      refs found, 24 non-id tokens correctly ignored.
- [x] ✅ **50.5 — DONE.** Re-measure the control resolve rate **on the worst band (`1.21`)**, not on master, and
      update `MIN_CONTROL_RESOLVE_RATE`'s comment table with the new numbers. ⚠️ The comment says in
      its own words that a floor justified by a stale measurement is not justified; adding 187
      references without re-measuring makes it stale. **Move the floor only if the measurement forces
      it, and say so.**
      **Result: the floor did NOT move, and the worst case went UP** — `1.21` reads **91.8%** against
      the 91.3% recorded at 8.4, so `0.80` keeps ~12 points of headroom. 🔑 **The intuition was
      backwards and the comment now says so:** config.yml carries the *newest* blocks, so adding it
      "should" have hurt the oldest band most — it did add 12 absent rows there, but it added 186
      references, most of them ids older than the support floor. **A rate is a ratio.**
- [x] ✅ **50.6 — DONE.** 🔑 **Close the runtime half too.** Add the `config.yml` sections to
      `ConfigItemIdResolutionTest` (or a sibling), asserting against the **live registry**. This is
      the leg that matters: the script is a person running a command, this runs inside
      `./gradlew build` — ship gate 1 — on every push, on every band, with nobody remembering to.
      It must **fail if 50.3 is reverted**; a test that passes either way is the point of the
      exercise missed.
      Built as `src/test/java/com/gmail/nossr50/config/ConfigYamlBonusDropsTest.java`, **4 tests**,
      beside `ConfigItemIdResolutionTest` because it *is* that class's missing half.
      ⚠️ **The obvious assertion is wrong here and the class says why.** *"Every shipped id resolves"*
      is vacuous on the newest band and **false by design** on `1.21`, where `Firefly_Bush` and
      eleven others are correct rows for a newer band — and config.yml's tables are **not pruned at
      all**, so there is no post-prune invariant to lean on either. Two band-independent properties
      instead: **(1)** no row names the registry *opposite* to its seam (deliberately silent about a
      row resolving as *neither* — that is legitimate drift), and **(2)** whatever this version calls
      an object, the table knows *that* name — the both-names pattern asserted from the **live
      registry** rather than from a spelling. Each has a companion test feeding the detector an input
      it must flag.
      ✅ **The revert proof was run, not asserted.** With `config.yml` restored to `HEAD`, both
      load-bearing tests go **red for the right reasons**: *"21 bonus-drop row(s) under a section
      keyed on the other registry"* and *"does not cover 2 object(s) under the name this Minecraft
      version actually uses"*. 🔑 **21 is the same number the Python reached independently** — two
      implementations, one answer. Working copy restored and md5-verified afterwards.
- [x] ✅ **50.7 — DONE.** Gate 4 `--self-test` **PASS** then `--check` **exit 0**:
      **875 refs / 26 sections / 7 files, 0 dead-everywhere**, the only two unresolved being the
      correctly-classified `Chain` pair (`config.yml` + `experience.yml`), each *"live on an older
      band"*. Full suite at the release command form
      (`--no-build-cache cleanTest build -Pmod_version=1.3.1`): **1869 executed, 0 failed, 0 errors,
      0 skipped** across 167 classes — counted from the JUnit XML, not from `BUILD SUCCESSFUL`.
      ⚠️ `build/libs/` holds **42** jars from past runs; `build` never cleans it, so any local
      `boot-check.sh` glob there is ambiguous. Noted, not addressed — out of §50's scope.
- [x] ✅ **50.8 — DONE on `master`.** `mod_version` → `1.3.2-SNAPSHOT`. ⚠️ **Not optional and not taste:**
      `src/**` is in `release.yml`'s `paths:` filter and 50.3 edits `src/main/resources/config.yml`,
      so every branch fires a release run and R-t refuses a stale version. **PATCH**, because this
      changes shipped config rows and one test, not a skill's behaviour model.
- [x] ✅ **50.9 — DONE. 8/8.** Four commits × eight bands, each with a `Backport-of:` trailer.
      Verified by **reading content back out**, never inferred from a cherry-pick exiting 0: the
      `config.yml`, `config-id-audit.py` and `ConfigYamlBonusDropsTest.java` blobs are **byte-identical
      to `master`'s on all nine**, `mod_version` reads `1.3.2-SNAPSHOT` on all nine, and each band
      carries 4 trailers while `master`'s own four carry none (rule 1).
      🔑 **All eight bands held the identical pre-fix `config.yml` blob (`a0af9bbb3`)** — so both live
      defects were present on every band, and the fix reaches every player on every version.
- [x] ✅ **50.10 — DONE. All four exit 0, none exit 2.** Re-run on a **fresh** clone after 50.13,
      because the first clone was stale the moment another commit landed. `--self-test` first on all
      four, all exit 0. Gate 7: *"No drift"*, **0 MISSING on every band**. Gate 9: manifests distinct.
      Gate 10: **50 shared paths byte-identical**. Gate 11: 12 keys watched, 10 SHARED / 2 DISTINCT,
      `mod_version=1.3.2-SNAPSHOT` uniform and `minecraft_version` distinct across all nine.
      Gates **7, 9, 10, 11** inside `git clone --local --no-hardlinks . <scratch>`,
      `--self-test` first on each. ⚠️ All four prefer **remote** refs, so a run in this working copy
      grades the stale remote. ⚠️ **Gate 11 is the only instrument that sees the `mod_version` bump**
      — gate 7 is blind to it by construction (`gradle.properties` sits in both
      `PROPAGATABLE_PREFIXES` and `BAND_LOCAL_PATHS`).
- [x] ✅ **50.11 — DONE. Nine green runs, nine releases at `v1.3.2`, zero drafts.** Verified the way
      the step demanded — by `gh release list` and `git ls-remote --tags`, **not** by the run list:
      exactly nine `mc<VER>-v1.3.2` tags plus the known bare `v1.21.11-baseline`, and
      `--json isDraft,isPrerelease` returns **0**. `v1.3.1` reaped per Minecraft line by design.
      Gate **8** from `master`: `--mutate` **9 passed / 0 failed with all 5 mutations caught**, then
      `ci-watch.sh HEAD` **exit 0** on run `33115787967`.
- [x] ✅ **50.12 — the band verification §49 could skip and this one could not.** §49 declined to run
      gates 1–6 per band because its held set touched **zero `src/main/` files**, so the jar was
      unchanged. That argument does **not** transfer here: 50.3 edits `src/main/resources/config.yml`,
      which ships inside the jar. Run on **`mc/1.21.1`**, the oldest band and the one where the
      reasoning was most likely wrong: gate 4 **exit 0** (**91.8%** control resolve — the predicted
      figure, 72 rows correctly classified as band drift, **0 dead-everywhere**), and the full suite
      **1867 executed / 0 failed / 0 errors / 0 skipped**, with `ConfigYamlBonusDropsTest` **4/4**.
      🔑 **That is the band-safety proof for both new properties.** On `1.21.1` the chain block is
      `chain`, not `iron_chain`, so the both-names assertion resolves the *other* candidate and still
      passes — exactly the behaviour a naive *"every id resolves"* test would have asserted into a
      false pass.
- [x] ✅ **50.13 — a defect the band run found, fixed and propagated.** The tag on an
      unresolved-but-not-dead row read **`ok: live on an older band`**. True only on `master`, where
      the control is the newest version; on a **band** the control is that band's own older version,
      so such a row is normally live on a **NEWER** one. It now reads *"live on another supported
      version"*, correct in both directions.
      🔑 **This is why 50.12 was run rather than reasoned about.** Before §50 it was a one-row
      footnote nobody read; `config.yml` makes it **72 rows on `mc/1.21.1`** — 72 lines telling a
      reader to look the wrong way down the band list while deciding whether a row is drift or a
      defect. **No gate could ever catch this: it is a log message, and every gate was green.**
- [x] ✅ **50.14 — this commit.** Recorded **separately**, because a status row cannot count the
      commit that changes the status — the same reason §49 split its outcome out. ✅ It fires **no**
      release run: `TODO.md` is outside `release.yml`'s `paths:` filter.

### The outcome — 26 dead rows, 2 live defects, nine releases at `v1.3.2`

**Gate 4 went from 689 references over 6 files to 875 over 7, and from "config.yml is not read" to
0 dead-everywhere.** The two defects it found were shipped-and-broken on **all nine bands** — every
band carried the identical pre-fix `config.yml` blob `a0af9bbb3` — and both fixes reach existing
installs, because `ConfigLoader#copyMissingDefaults` back-fills a missing default leaf.

| | before §50 | after |
|---|---|---|
| files read by gate 4 | 6 | **7** |
| id references | 689 | **875** |
| dead-everywhere | 0 *(config.yml unread)* | **0 *(config.yml read)*** |
| `config.yml` rows | 676 lines | **652** |
| unattended leg | none for this file | `ConfigYamlBonusDropsTest`, inside gate 1 |

🔑 **The headline is not the 26 rows. It is that a file can be "outside the gate" in two places at
once.** TODO 5.5 always had two legs, and `config.yml` was missing from both — so the largest
behavioural id table in the jar was never checked by anything, and the carried row that named the
problem still understated it by 26×.

### What this section is NOT doing

- **Not touching R13, §31.5's 562 collision sites, or manifest debt piece 1.** All three stay open.
- **Not adding an exclusion list to `config-id-audit.py`.** See the ruling.
- **Not re-tuning any live value.** Every `true` that stays, stays `true`. This section deletes dead
  keys and adds two live ones; it changes no number a player has ever felt.
- **Not extending the gate to `advanced.yml`.** It is id-keyed in places too and was **not**
  measured here. That is a separate finding and gets its own section rather than riding this one.
- **Not moving the drift waiver `cutoff:` sha.**

### Rollback

🟢 **While unpushed:** `git reset --hard <tip>` per branch. Pre-§50 tips are recorded in
`.agent/memory/state.md` before the first commit, not after.

🔴 **After the push, the undo is FORWARD** — a further `mod_version` bump, never a re-point of
`v1.3.2`. ⚠️ **Do not delete a published release to undo this:** deleting a tag DRAFTS its release
rather than removing it, which is how six orphans accumulated on 2026-08-13.

⚠️ **The one irreversible-shaped step is 50.3**, and its blast radius is bounded by 50.2: 25 deleted
rows, each proven redundant *first*, in one tracked file that `git show HEAD:src/main/resources/config.yml`
restores in full.

---

## §51 — the collision review list, filtered on the RECEIVER TYPE — ✅ DONE

### What forced it

Carried out of §30 as **30.5c** and re-scoped as **§31.5**, owner-sequenced *after* §32 — which
closed at §39. The owner picked it off the open list on 2026-08-27.

The row has always been defended the same way: *sampling says it is dominated by false positives.*
That is true and it is not the point. `Registry#getId` was **42 sites**, **12 of which javac never
mentioned**, and one of those twelve was a live `equals()` in **main** source that returned `false`
forever. Sampling is exactly the instrument that misses one row in forty.

### What was measured, before any edit

* `rename-to-official.py --self-test` — **104 checks, 0 failed**, exit 0. Run first, because
  *"no collisions"* is also what a broken auditor prints.
* `--collisions` on `master` today — **542 sites over 35 names**, *not* the carried **562 over 38**.
  🔑 **The carried row is stale in the SAFE direction, which is why nothing caught it**: 20 sites and
  3 names were closed incidentally somewhere in §31 – §33 and no commit updated the row. Same
  *"a status row is never updated by the commit that changes the status"* shape that turned up three
  times in one pass across §37 – §41. **Re-measure before believing the size of anything carried.**
* ⚠️ **`build/classes` was STALE before the run** — 546 class files against 464 sources. That is the
  exact trap `extract-mc-surface.py` warns about, and this section depends on the bytecode being
  current. `./gradlew classes testClasses` → exit 0, **543 classes** (355 main + 188 test) for
  **464** sources. The inflation is inner and anonymous classes; the counts are consistent.

### The ruling — resolve the receiver from BYTECODE, not from source text

**31.5a proposed a heuristic** — parse declared field/local/parameter types out of the source and
guess what the receiver is. **Rejected.** javac already resolved every receiver *exactly* and wrote
it into the constant pool, and this repo already reads it: `extract-mc-surface.py`'s
`pool_refs_detailed()`. A second, weaker resolver is one more thing that has to agree with the
compiler, with nothing checking that it does.

Two granularities were built and measured, not argued:

| filter | sites | names |
|---|---|---|
| none — the review list as it stands | 542 | 35 |
| per **FILE** — does this file call this name on an MC owner at all? | 234 | 25 |
| per **SOURCE LINE** — `LineNumberTable` places every invoke | **200** | **25** |

Line attribution turned out to be stable: a window of ±0, ±1, ±2 and ±3 all return exactly **200**;
±5 returns 202. **The window is kept at ±2 anyway** — it costs zero sites here, and the thing it
guards against (a chained call `foo.bar()\n  .get(x)`, which javac attributes to the line the
expression *starts* on) is a **fail-CLOSED** miss, the direction that loses real findings.

Where the filter cannot judge, it **keeps** the site: a source file with no bytecode is reported,
never dropped.

### The two ways this filter can be wrong — both measured, not reasoned about

1. 🔑🔑 **Splitting the pool record on the wrong character, and calling it a clean sweep.**
   `pool_refs_detailed()` returns `<dotted.owner>#<name>` — the separator is `#`, not `.`. The first
   prototype split on the last dot, so every bucket held `Registry#getKey` instead of `getKey`, and
   the filter matched nothing: **0 kept, 542 dropped.** It reported a *perfect* result. **A filter
   that removes everything and a filter that correctly finds nothing print the same thing**, and the
   only reason it was caught is that 100% is not a credible drop rate. This is precisely why 31.5c
   is in the plan, and it fired before a line of the deliverable was written.
2. **`@Shadow` methods — blind spot #4.** A call to a shadowed member compiles to an invoke on the
   **mixin** class, not on `net/minecraft/**`, so a naive owner test drops it silently.
   ✅ **Measured empty:** all **8** `@Shadow` in the tree are **fields**, and a field access carries no
   `(`, so it is outside this audit's regex entirely. There are **zero** `@Shadow` methods.
   The rule still treats a `@Mixin`-annotated owner as MC-owned, so the hole stays shut if one is
   added later — a guard for a case that does not exist yet is cheap here and unrecoverable later.

⚠️ **A filename is not a receiver.** 39 of the 542 sites sit in paths matching `*mixin*`; **34** of
them are in `MixinApplicationTest.java` and are `Class.getName()` and `Field.getName()` — reflection
on `java.lang`, correctly dropped. The path matched and the receiver did not, which is the whole
argument for resolving the receiver instead of the file.

### Steps

- [x] ✅ **51.1** `--receivers`, on top of `--collisions`, in `scripts/rename-to-official.py`. Reads
      `build/classes` via `javap -p -v`, places every MC-owner invoke on a source line through
      `LineNumberTable`, and keeps a site only when that name is invoked on an MC owner within ±2
      lines. **Compiles first** (`gradlew classes testClasses`), so `build/classes` matches `src/`
      by construction; exit 2 — not a pass — if that compile fails. A stale tree yields a
      confidently wrong answer in the *reassuring* direction.
      🔴 **The first version compared MTIMES, and it shipped broken — see 51.7.**
- [x] ✅ **51.2** Self-test extensions, each **watched fail before being trusted**:
      the `#`-vs-`.` split (M1, the defect above); a `@Mixin` owner counted as MC (M2); a stale
      `build/classes` refused rather than reported clean (M3); the fail-open path when a file has no
      bytecode (M4).
- [x] ✅ **51.3** 🔑 **31.5c's mutation** — re-introduce one `BuiltInRegistries.*.getId(` into main
      source, rebuild, and watch it **survive the filter and get reported**. *A filter never shown to
      catch anything is a filter that removes everything*, and this section has already produced one
      of those. Reverted immediately after; it is a mutation, not a change.
- [x] ✅ **51.4** Read **every** survivor by hand. Record the count **reviewed**, not just the count
      fixed — a sweep that reports only its fixes cannot be distinguished from one that stopped early.
- [x] ✅ **51.5** Fix what is genuinely wrong. Size unknown going in, and that is accepted: the honest
      outcome of 51.4 may be zero defects, and zero-after-200-reviewed is a result, not a failure.
- [x] ✅ **51.6** Propagate, ship. `scripts/**` is in the identity set (gate 10), so 51.1 reaches all
      eight bands regardless. **If 51.5 touches `src/`, it ships in the jar** and takes a
      `mod_version` bump with it; if it does not, it does not.

### The outcome — 542 → 39, all 39 reviewed, **zero defects**

**The filter went in, the review happened, and it found nothing wrong. That is the result, not a
failure to find one** — but it is only worth anything because the instrument was shown to catch the
real thing first (51.3), on the very line the defect originally lived on.

| stage | sites | names |
|---|---|---|
| the review list as carried | 542 *(not the recorded 562)* | 35 *(not 38)* |
| stage 1 — is the receiver an MC type? | 200 | 25 |
| **stage 2 — does that type carry the collision?** | **39** | **8** |
| reviewed by hand | **39 of 39** | 8 of 8 |
| defects found | **0** | |

Reviewed: `getKey` 12 · `getBoundingBox` 10 · `teleportTo` 6 · `get` 5 · `knockback` 2 · `update` 2 ·
`drop` 1 · `offset` 1.

🔑 **Why zero is credible here, and not just "we looked".** For every one of the eight names, the
yarn member and the mojmap member that share the spelling differ in **arity or return type**, so a
leftover yarn call cannot bind silently — javac rejects it:

| name | yarn member renamed to | the mojmap member of that name is really yarn's | why a mis-bind cannot be silent |
|---|---|---|---|
| `getKey` | `getResourceKey` | `getId` | 0-arg vs 1-arg |
| `getBoundingBox` | `getLocalBoundsForPose` | `getBoundingBox` | 1-arg (pose) vs 0-arg |
| `teleportTo` | `teleport` | `requestTeleport` / `teleport` | 3-arg vs the 8-arg overload used |
| `get` | `getValue` | `getEntry` | returns `T` vs `Optional<Holder.Reference<T>>` |
| `knockback` | `blockedByItem` | `takeKnockback` | predicate vs void 5-arg |
| `update` | `tickServer` | `upgrade` | different parameters entirely |
| `drop` | `dropAllDeathLoot` | `dropItem` | `(DamageSource)` vs `(ItemStack, boolean)` |
| `offset` | `relative` | `add` | `(Direction[, int])` vs `(int, int, int)` |

🔑🔑 **So the residual risk is not spread over 39 sites — it is concentrated in the shape §30 already
found.** `Registry#getId` was dangerous *because the type difference was erased at the call site*:
`MANNEQUIN_ID.equals(<int>)` autoboxes, compiles, and returns `false` forever. The dangerous site is
one whose result is consumed **type-agnostically** — `equals(Object)`, string concatenation, `var`,
a raw generic. **That, not the collision count, is where the next one will be**, and no guard in this
repo looks for it. Logged as a new open row rather than built here.

⚠️ **The carried row was stale, and in the direction nothing catches.** It read *562 sites over 38
names*; measurement says **542 over 35**. Twenty sites and three names were closed incidentally in
§31 – §33 and no commit updated the row. A row that overstates its own size is never questioned.

⚠️ **This changes no shipped behaviour.** `scripts/**` is outside `release.yml`'s `paths:` filter,
so no branch fires a release run and **no `mod_version` bump is owed**. 51.5 found nothing to fix, so
nothing enters the jar. Gate 10 still requires the script to be byte-identical on all nine branches,
### 🔴 51.7 — the staleness guard shipped WRONG, and was caught by using it

**Found minutes after pushing to all nine branches**, by running the tool on the working copy the
propagation had just left behind.

`assert_classes_current()` refused when any source file's **mtime** was newer than the newest
`.class`. `git checkout` rewrites every source file's mtime **without changing its content**, so
checking out eight band branches made an up-to-date tree look stale — and **no rebuild could clear
it**, because Gradle is content-based and correctly did nothing:

```
FATAL: build/classes is STALE -- 148 source file(s) are newer than the newest .class.
$ ./gradlew classes testClasses   ->  exit 0
FATAL: build/classes is STALE -- 148 source file(s) are newer than the newest .class.   (forever)
```

🔑🔑 **A refusal a rebuild cannot clear is worse than the defect it guards against.** The short
review list it was protecting from is a one-time wrong answer; a guard that cannot be satisfied
teaches people to delete it. And it was **fail-closed**, the direction usually assumed safe — which
is exactly why it read as conservative rather than broken.

**The fix removes the proxy instead of tuning it:** `--receivers` now *runs* `gradlew classes
testClasses` before reading bytecode, so `build/classes` matches `src/` by construction and there is
nothing left to infer. mtime is not a staleness signal in a git working copy, and no threshold makes
it one.

⚠️ **The self-test could not have caught this**, and its replacement still cannot: the fixture built
a temp tree and set mtimes with `os.utime`, so it tested the comparison faithfully and the
comparison was answering the wrong question. **It took running the tool on a real checkout.** The
mutation now asserts the compile step is *armed by default* and refuses without a `gradlew` —
the properties that survive the proxy being gone.

which is why it is propagated anyway.

### What this section is NOT doing

- **Not making this a ship gate with a reviewed baseline.** 🔑 The survivor set is derived from
  **this band's** bytecode, so a committed baseline is a *per-band generated fact* — the exact
  `mc-surface.txt` trap, where a file that is valid for another branch is true on every line and no
  per-branch check can see it. Turning this into a ratchet needs the `manifest-identity-audit.py`
  treatment, and that is its own section with its own reasoning.
- **Not touching R13** (the general overload-rebind shape) or **manifest debt piece 1**. Both stay open.
- **Not extending the audit to FIELD accesses.** The regex requires a `(`. Four `@Shadow` fields and
  the `age`/`x`/`y`/`name` rows say there is something there, but it is a different instrument and a
  different failure shape, and bolting it on would make this section's before/after number
  uninterpretable.
- **Not re-running the rename.** `--write` is never passed; `--collisions` is a review path.
- **Not extending gate 4 to `advanced.yml`** — still owed from §50, still its own section.

### Rollback

🟢 **While unpushed:** `git reset --hard <tip>` per branch; pre-§51 tips are recorded in
`.agent/memory/state.md` **before** the first commit, not after.

🔴 **After the push the undo is FORWARD** — a further `mod_version` bump, never a re-point of a
published tag. ⚠️ Deleting a tag **DRAFTS** its release rather than removing it; that is how six
orphans accumulated on 2026-08-13.

⚠️ **51.3 writes to main source deliberately.** It is a mutation and its undo is
`git checkout -- <the one file>` — which is itself destructive, so the file is named and its clean
state confirmed with `git status --short <path>` **before** the mutation is applied, never after.
Nothing else in this section writes to `src/` unless 51.5 finds a real defect.

---


## §52 — entity ids join the config-id gate — the KIND that was never in it

### What forced it

§50 closed with one owed row: *"extend gate 4 to `advanced.yml` — it is id-keyed in places too."*
Measured 2026-08-27, before any edit, that row is **wrong about where the rot is**.

`advanced.yml` has exactly **one** id-keyed table — `Skills.Hunter.Tiers.Overrides`, two keys
(`Ghast`, `Wither_Skeleton`) — and **both are live on every supported version**. Extending the gate
to that file finds nothing, and a section that stopped there would have shipped a green gate and a
correct-sounding closure.

The actual gap is one level up, and it is a whole **kind**. `scripts/mc-ids.txt` carries
`### block` and `### item` and nothing else. Every entity-keyed config row in this repo — and there
are **138 of them across four tables** — has therefore never been checked by either half of gate 4,
on any branch, ever. The file that carries them is `experience.yml`, which has been *inside* the
gate since before §50 — its **material** sections were audited while its **entity** sections were
invisible in the same file, on the same run, in the same green line of output.

🔑 **This is the §50 lesson landing a second time and the carried row is again a LOWER BOUND.** §50's
row named one dead id and measuring found 26. This row named a file and measuring found the file
clean and the defect next door. **A carried row records where somebody last looked, not where the
defect is.**

### What was measured, before any edit

Entity ids dumped from the vanilla data generator's `reports/registries.json` for `1.21` (130 types)
and `26.2` (158 types), the two ends of the supported range. Config keys read with a real YAML
parse and put through `ConfigStringUtils`'s exact formatter, because that is what the runtime uses.

| Table | Keys | Dead on **every** supported version |
|---|---|---|
| `experience.yml:Experience_Values.Combat.Multiplier` | 86 | **7** |
| `experience.yml:Experience_Values.Taming.Animal_Taming` | 22 | **1** (`Snifflet`) |
| `experience.yml:Experience_Values.Husbandry.Animal_Breeding` | 28 | 0 |
| `advanced.yml:Skills.Hunter.Tiers.Overrides` | 2 | 0 |

The seven dead `Combat.Multiplier` keys, and **why each is dead — they are not one defect**:

- `Pig_Zombie`, `Zombie_Pigman` — two spellings of the mob Bukkit renamed in 1.16. ⚠️ **Both are
  harmless**: `Zombified_Piglin: 3.0` already sits three lines below them, so piglins are paid
  correctly and these are redundant rows. **This is the one I got wrong first** and it is the reason
  the table above is per-key: "dead key" and "unpaid mob" are different questions, and only the
  second one is a defect.
- `Mushroom_Cow` — renamed to `mooshroom`. **No `Mooshroom` row exists in this table.** 🔴 Live.
- `Snowman` — renamed to `snow_golem`. **No `Snow_Golem` row exists.** 🔴 Live.
- `Wandering_trader` — a **casing typo**; the formatter produces `Wandering_Trader`. The configured
  value is `1.0` and the fallback is also `1.0`, so nothing is mispaid. Dead, cosmetic.
- `Ghastling`, `Snifflet` — **not entity types on any version.** A ghastling is a happy ghast with
  `baby=true` and a snifflet is a baby sniffer; neither has a registry entry. Verified against both
  dumps directly rather than inferred from the rename pattern that explains the other five.

### 🔴 The live defects — and the bigger half is what is ABSENT, not what is dead

The runtime fallback in `CombatXp#baseXp` is **not uniform**, and that is what decides impact:

| category | unlisted mob gets | so a dead/absent key means |
|---|---|---|
| `MONSTER` | `getDouble` with **no default** → **0.0** | 🔴 **pays nothing, forever** |
| `ANIMAL` | `Combat.Multiplier.Animals` → 1.0 | mispaid only if configured ≠ 1.0 |
| `OTHER` | the legacy 1.0 floor | mispaid only if configured ≠ 1.0 |

Category comes from `CombatUtils#categoryOf` — `instanceof Monster` / `instanceof Animal` / else.
Resolved against the merged jar with `javap-mc.sh`, never from memory:

1. 🔴🔴 **`Vex` pays ZERO combat XP, and has on every band since the port began.** `Vex extends
   net.minecraft.world.entity.monster.Monster`, and `Combat.Multiplier` has **no Vex row at all** —
   not a dead one, an absent one. Evokers and raids spawn them; killing them pays nothing.
2. 🔴 **`Creaking` pays ZERO** — `extends monster.Monster`, no row. Affects every band from `1.21.4`.
3. 🔴 **`Snow_Golem` pays 1.0 where the config says 0.0.** `SnowGolem extends AbstractGolem`, which
   is **not** `Animal` → `OTHER` → the 1.0 floor. The `Snowman: 0.0` row was a deliberate zero — snow
   golems are trivially farmable — and it has been inert since the rename.
4. 🟡 **`Mooshroom` pays 1.0 where the config says 1.2.** `MushroomCow extends AbstractCow` → `ANIMAL`
   → the `Animals` fallback.
5. 🔴 **26.x adds more:** `sulfur_cube` and `zombie_nautilus` have no row. Their category must be
   resolved from the jar in 52.1, not assumed from the name.

🔑 **The absent-row half could never have been found by auditing the config file.** Every key in
`Combat.Multiplier` could be live and Vex would still pay zero, because the defect is a row that is
**not there**. An id audit reads what is written down; only the **live registry** can enumerate what
should have been. That is why this section has two halves and why neither is optional.

### The ruling — `entity` becomes a third kind, and the live half enumerates Monsters

**(a) Offline.** `extract-mc-ids.py` grows `entity` alongside `block` and `item`; `mc-ids.txt` is
regenerated for all 14 versions and **cherry-picked**, never regenerated per band (the standing rule
— it is a fact about Minecraft, not about a branch). `config-id-audit.py` grows an `ENTITY` kind and
reads the four entity-keyed tables above. This catches **dead keys**.

⚠️ `cross_validate` compares the registry dump against jar assets and there is **no asset
counterpart for entities** — it must skip `entity` explicitly rather than silently comparing against
an empty set, which is the shape that reports a clean pass for a scan that never ran.

**(b) Live.** A new test walks the live entity registry, and for every type whose class is a
`Monster` asserts a `Combat.Multiplier` row exists. This catches **absent rows**, and it is the only
instrument that can. It goes in gate 1 beside `ConfigYamlBonusDropsTest`.

**(c) The fixes.** Rename the three rotted keys to their registry spellings, keeping their configured
values; delete the four that name nothing (`Pig_Zombie`, `Zombie_Pigman`, `Ghastling`, `Snifflet` ×2);
add rows for the monsters that have none. **Every value a player has felt stays what it was** — this
section makes rows *reachable*, it does not re-tune. The one exception is deliberate and is the
defect: `Snow_Golem` starts paying the `0.0` it was always configured to pay.

### The two ways this can be wrong — both to be measured, not reasoned about

1. **The formatter is not what I think it is.** `title()` in the measurement is a re-implementation
   of `ConfigStringUtils`. If they disagree, every count above is wrong in a way that looks fine.
   → 52.1 drives the **shipped** formatter, not a copy. (The §51 lesson: a fixture that drives its
   own lambda never executes the shipped code, and 2 of 8 mutations stayed green.)
2. **A filter that drops everything looks exactly like a clean sweep.** If the entity kind fails to
   load, every key reads "absent" or every key reads "present" depending on the direction, and both
   render as a confident number. → the self-test asserts a known-dead key is **reported** and a
   known-live key is **not**, and the audit warns on a 0% or 100% hit rate.

### Steps

- [x] ✅ **52.1** — a control first: assert the measurement's formatter matches the shipped
      `ConfigStringUtils` over every entity id in both dumps. If it does not, everything above is
      re-measured before anything else happens.
- [x] ✅ **52.2** — `extract-mc-ids.py`: add `entity`; skip it in `cross_validate` with a stated reason;
      extend `--self-test`. Regenerate `mc-ids.txt` (dry run, read the diff, then `--write`).
- [x] ✅ **52.3** — `config-id-audit.py`: `ENTITY` kind, the four tables, self-test + control floor.
- [x] ✅ **52.4** — the live-registry Monster test (gate 1). Found `Vex` + `Creaking` at zero.
- [x] ✅ **52.5** — the config fixes, one commit, each row justified by 52.3/52.4 output.
- [x] ✅ **52.6** — full suite (1,872/0), gates, docs caveat-expiry pass (grep the **symptom**: any wiki claim
      about which mobs pay combat XP).

### What this section is NOT doing

- **Not re-tuning any multiplier.** Values move only where a rename carries one across, and where a
  monster has no row at all it gets the value its nearest sibling already has, stated per row.
- **Not adding an alias table.** `FishingTreasureConfig` has one for the same three Bukkit renames;
  that was right there (it must read sections written by users) and wrong here (this is *our* shipped
  default, which we can simply spell correctly). Aliasing would preserve the dead spelling forever.
- **Not extending the gate to `coreskills.yml`, `hidden.yml`, `skillranks.yml`, `sounds.yml`.**
  Unmeasured. Whether they are id-keyed is a separate question and gets its own row, not a guess.
- **Not touching `mc-ids.txt`'s missing `26.1`/`26.1.1` rows** — the manifest covers 14 versions
  against a declared scope of 16. Noticed here, measured nowhere; it gets its own row rather than
  riding this section.

### Rollback

🟢 While unpushed: `git reset --hard <tip>` per branch, tips recorded in `.agent/memory/state.md`
**before** the first commit. Every file touched is tracked; `git show HEAD:<path>` restores each in
full. The `mc-ids.txt` regeneration is the one bulk rewrite — it is a dry run by default, the diff
is read before `--write`, and the manifest's own declared-count parser refuses a truncated file.

---

### The outcome — 8 dead keys, 2 unpaid monsters, and the two sets do not intersect

✅ **DONE 2026-08-27.** Four commits on `master`:

| commit | what |
|---|---|
| `6b014029e` | `entity_type` becomes a third id kind; the default stops widening scope |
| `c42c55e53` | the entity kind joins `config-id-audit.py`, matched **exactly** |
| `5639036c0` | the live half + the six config fixes + the docs half |

**The gate grew from 875 references over 26 sections in 7 files to 1,013 over 30 in 8.**
Suite **1,872 executed, 0 failed, 168 classes** (was 1,869 — `CombatMultiplierCoverageTest` ×2 and
one new manifest test). `config-id-audit.py --check` exits 0; it exited 1 on 8 rows before the fix.

**What was actually wrong, by severity:**

1. 🔴🔴 **`Vex` and `Creaking` paid ZERO combat XP** — absent rows, not dead ones. Vex for the whole
   life of the port, Creaking since `1.21.4`.
2. 🔴 **`Snow_Golem`**: the deliberate `0.0` (farmable, same reasoning as `Armor_Stand`/`Mannequin`)
   was inert under the Bukkit spelling `Snowman`, so snow golems paid the 1.0 `OTHER` floor.
3. 🟡 **`Mooshroom`**: `1.2` inert under `Mushroom_Cow`, so they fell back to `Animals: 1.0`.
4. ⬜ `Wandering_trader` — dead on **case alone**. Configured value equalled the fallback, so nothing
   was mispaid. It is the reason entities are matched exactly rather than through `normalise()`.
5. ⬜ `Pig_Zombie`, `Zombie_Pigman`, `Ghastling`, `Snifflet` ×2 — dead and harmless.

### 🔑 What this section is worth remembering for

🔑🔑 **The two halves found DISJOINT defect sets.** The script found 8 dead keys; the live test found
2 absent monsters; **the intersection is empty.** That is not a coincidence, it is the argument for
having both — one grades what is written down, the other enumerates what should have been. Either
alone would have closed this section while leaving the other five defects shipping.

🔑🔑 **The carried row named the wrong file, and following it would have produced a green closure.**
§50 left *"extend gate 4 to `advanced.yml`"*. `advanced.yml` has one id-keyed table, two keys, **both
live** — the work as specified finds nothing. The rot was in `experience.yml`, which had been *inside*
the gate all along: its material sections were audited while its entity sections were invisible in the
same file, on the same run, in the same green line of output. **Second sighting of "a carried row is a
lower bound"; §50 was the first.**

🔑🔑 **`fishing_treasures.yml` has aliased these exact three renames since §F.** The fix was known,
written down, tested, applied to one file — and nothing asked whether any other file had the same
rot. A defect class fixed in one place is not a defect class closed.

🔑🔑 **Both obvious APIs for "is this a monster" were wrong, one of them silently.**
`EntityType#getBaseClass()` returns `Entity` for **every** registered type under this bootstrap,
`zombie` included: it compiles, needs no `Level`, and reports **0 monsters out of 158**. As the sole
input to *"every monster has a row"* that is a permanently green test examining nothing — caught only
by the anti-vacuity guard, which is the entire reason to write one before trusting a number.
`getCategory()` is a *different question*: 45 vs 34, and the 11-way gap is real (`slime`, `ghast`,
`phantom`, `shulker`, `hoglin`, `ender_dragon` extend `Mob`, not `Monster`).

⚠️ **A prediction in the plan above was WRONG and is corrected here rather than quietly dropped.**
It said `sulfur_cube` and `zombie_nautilus` "have no row — their category must be resolved from the
jar in 52.1, not assumed from the name." Resolved: **neither is a `Monster` subclass**, so both fall
to the safe 1.0 `OTHER` floor and neither is a defect. The plan reasoned from the name after saying
not to. The measurement is what settled it.

⚠️ **A third manifest parser existed, in Java** (`ConfigIdManifestTest`), unknown until it rejected
`### entity` and failed the suite. `config-id-audit.py` imports the Python parser rather than
reimplementing it, with a comment about how two parsers that disagree is a silent-divergence shape —
and there was a third the whole time. It now gives entities the **same live-registry treatment** as
items and blocks, which matters more for this kind: entities have no jar-asset counterpart, so the
generator's cross-check skips them and this is their **only** independent check.

⚠️ **The generator's default silently widened scope.** A plain `extract-mc-ids.py` run wanted to add
**nine** versions the manifest excludes — the whole `1.20` line R-x withdrew, plus `26.1`/`26.1.1`.
`--write` would have carried a scope change behind a diff that looks routine, and `--check` could
never pass on a machine with one extra cached version. The default is now the manifest's own list;
`--all-cached` is the opt-in. Found by **reading the dry run**, which is the only reason the gate
exists.

⚠️ **The 52.1 control passed and one of its mutations stayed GREEN.** Every registry id is already
lowercase, so a formatter that omits the tail-lowercasing is observationally identical over the whole
real input domain. Recorded in `gotchas.md`: *"I ran the shipped code" is not the same claim as
"I distinguished it from a wrong one."*

### Still open, noticed here and deliberately not ridden on this section

- ⬜ **`mc-ids.txt` covers 14 versions against a declared scope of 16** — `26.1` and `26.1.1` are
  cached and absent. Adding them is a scope act with a ruling behind it, not a side effect.
- ⬜ **`coreskills.yml`, `hidden.yml`, `skillranks.yml`, `sounds.yml`** are still outside the gate.
  Whether they are id-keyed is unmeasured — a question, not a guess.
- ⬜ **`Vex: 2.0` and `Creaking: 1.0` are judgement calls**, reasoned per row in the config comment.
  They stop a zero; they are not a measured balance figure and are cheap to retune.

## §53 — the TYPE-AGNOSTIC call site — the shape that let the one real defect through

### What forced it

§51's finding, not §51's list. The collision residue is safe **because javac rejects a mis-bind
whenever arity or return type differs** — for all 8 surviving names it does. The single defect that
ever got through did so because `MANNEQUIN_ID.equals(BuiltInRegistries.ENTITY_TYPE.getId(…))`
consumed its argument as `Object`: the `int` autoboxed, it compiled clean, and it returned `false`
forever.

**So the risk is not the collision count. It is the set of call sites whose result is consumed
type-agnostically** — `equals(Object)`, string concatenation, `var`, a raw generic, a varargs
`Object...`. At those sites the compiler is not checking anything, and every guard in this repo is
downstream of the compiler.

### The ruling

A new mode on the collision tooling — **not** a new script, because it needs the same bytecode
receiver resolution `--receivers` already does, and a second copy of that would drift.

It reports, per site: the receiver type, the member, and **which type-agnostic sink** consumes it.
It is a **review list with a reason attached**, not a pass/fail gate — the shape is legal Java and
usually correct, so a gate that failed on it would be turned off within a week.

### Steps

- [x] ✅ **53.1** — enumerate the sinks from bytecode: `equals(Ljava/lang/Object;)Z`, `StringBuilder#append`
      / `invokedynamic makeConcat*`, `Objects.equals`, `Object...` varargs, `Map#get`/`#containsKey`.
- [x] ✅ **53.2** — cross the sink set with the MC-typed receivers `--receivers` already resolves.
- [x] ✅ **53.3** — better than planned: the live run lands on the REAL line. The mutation that proves it: re-introduce the `MANNEQUIN_ID.equals(...)` defect and
      require it to be **reported**. A finder never shown to catch the one known instance is a finder
      that reports nothing. Same discipline as 51.3.
- [x] ✅ **53.4** — 18 read, zero defects. Read every survivor by hand; record the count **reviewed**, not just fixed.

### What this section is NOT doing

- **Not making it a ship gate.** It is a review instrument. Wiring it into the gate list would make
  every legitimate `equals` a release blocker.

---

### The outcome — 18 sites, all read, zero defects, and the instrument lands on the historical line

✅ **DONE 2026-08-27**, `46b4c726b`. `--type-agnostic` on `rename-to-official.py`.

**18 sites over 465 source files, all 18 read by hand, zero defects.** Every one is a correctly
typed use: `UUID` and `Long` map keys, `String.equals`, `Set<Item>.contains`.

🔑🔑 **Site `EntityDamageListener:857` is the `MANNEQUIN_ID` line itself** — now correct, since §30
fixed it — so the instrument points at the exact line the one real defect lived on **without needing
a mutation to prove it fires**. That is the strongest form of 53.3 available: not a synthetic
re-introduction, the real coordinates.

⚠️ **`HunterListener:305` is a genuine false positive**, and it is the documented limit: it pairs a
producer with a sink across a `return`, because **adjacency is not dataflow and ignores basic-block
boundaries.** Fail-open is deliberate — an over-long list gets read; a short one that quietly dropped
the real instance does not.

⚠️ **Not a gate, and always exits 0.** The shape is legal Java and usually correct. A gate that
failed on `Objects.equals` would be switched off within a week, and this repo has already recorded
what a permanently red gate detects.

🔑 **A mutation caught a vacuity in a check written minutes earlier.** `_returns_a_value` was
asserted directly, while nothing proved the pairing logic consulted it — deleting that branch left
the self-test green. **Testing a helper is not testing its caller.** Fixed by adding the void-producer
fixture; all 5 mutations now red. Self-test 139 → 147.

---

## §54 — R13, the general overload-rebind shape

### What forced it

Carried since §33, which closed the `equals` family **only**. Any method whose narrow overload is
deleted while a wider one survives rebinds **silently** — javac must accept it by the language rules,
so there is no diagnostic to catch. No gate covers the general case.

### The ruling

Compare, per band, the **resolved target descriptor** of every call site against the previous band's,
from bytecode on both sides. A call site whose descriptor changed while its source text did not is
the signal. This is mechanically the same question `--receivers` answers, asked across two versions
instead of one.

### Steps

- [x] ✅ **54.1** — REPLACED (see the outcome): resolve every call-site descriptor per band from `build/classes` (compile first —
      the 51.7 lesson: never infer freshness from mtimes).
- [x] ✅ **54.2** — REPLACED by the one-branch armed-site test. Diff descriptors across two bands; report sites whose source is identical and whose
      resolved descriptor is not.
- [x] ✅ **54.3** — done as self-test fixtures (165 checks). The mutation: delete a narrow overload in a fixture, confirm the diff **reports** it
      and that javac stays silent — proving the instrument sees what the compiler cannot.

### What this section is NOT doing

- **Not fixing what it finds in the same section.** Finding the set is the deliverable; each hit is
  judged on its own.

⚠️ **Order matters:** §54 depends on §53's descriptor plumbing. If §52 and §53 consume the session,
§54 stays open rather than shipping half-built — a partial gate that exits 0 is worse than none.

---

### The outcome — the planned instrument could not work, and the replacement answers R13 at ZERO

✅ **DONE 2026-08-27**, `9d239bcf4`. `--overload-rebind`.

🔴 **The design in the plan above is wrong, and it is left there rather than rewritten** so the
reason survives. *"Diff each call site's resolved descriptor between two bands"* cannot work here:
`master` is official-named and every `mc/**` band is yarn-mapped, so **essentially every MC
descriptor differs between them by construction** — thousands of rows, none of them findings.
Diffing two yarn bands needs two checkouts both built, and still only reports a rebind **after** a
version bump has already shipped it.

🔑🔑 **The question is answerable from ONE branch, and earlier.** A deletion can only rebind silently
if a **wider sibling overload already exists** — a property of the jar we compile against *today*.
So the instrument reports the sites that are **armed**, before anything moves.

**Two conditions, and the second is the whole finding:**

1. the arguments still bind — same arity, every parameter same-or-wider, at least one strictly wider;
2. **the sibling's return type still fits.**

🔴 **Dropping condition 2 is not conservative, it is wrong.** Without it, all four `Mth.clamp` sites
in this tree report — and every one is rejected by javac at the **use** site, because a `long` does
not fit an `int`. **Four rows that cannot fail is how a review list gets abandoned.** Condition 2 is
§51's finding applied here.

**Result: 2,251 MC call sites over 131 owner types, 129 resolved against the `26.2` jar, ZERO armed.**
Zero is a **result**, not an absence of scanning, and the report says which claim it is making and
what backs it. The 7 unjudged sites are **named, not counted** — both owners are our own `@Mixin`
accessors, legitimately absent from the jar.

⚠️ **The two instruments compose, and neither is complete alone.** The one way past a return-type
difference is a result consumed **type-agnostically**, which has no use site to reject it — and the
real defect this repo shipped was in the **intersection**: `getId`'s `int` into `equals(Object)`.

⚠️ **Known hole, written into the code rather than left to be discovered:** "wider" is decided only
where it needs no class hierarchy — reference-vs-`Object`, primitive widening, autoboxing. A sibling
wider by an **intermediate supertype** (`ServerPlayer` → `Player`) is **not** reported. Closing that
needs the hierarchy walk and is its own piece of work.

Self-test 147 → **165** checks, all driving the shipped functions.

## §55 — the roster gate for `coreskills.yml` and `sounds.yml`, and `hidden.yml`'s two dead knobs

### What forced it

The carried row out of §50/§52: *"`coreskills.yml`, `hidden.yml`, `skillranks.yml`, `sounds.yml` are
still outside the gate. Whether they are id-keyed is unmeasured — a question, not a guess."*

Measured, before any edit. **The row's premise is wrong, and this is the third carried row in a row
to name the wrong thing** — §50's named the wrong *bound* (one dead id, actually 26), §52's named
the wrong *file* (`advanced.yml`, two live keys), and this one names the wrong *gate*.

🔑 **None of the four is keyed on item/block/entity registry ids.** `config-id-audit.py` (gate 4) is
the wrong instrument for all four, and extending it — doing exactly what the row said — finds
nothing and closes the row that leads here.

Three of the four are keyed on a roster mcMMO **owns**: `PrimarySkillType`, `SubSkillType`,
`SoundType`. That is the same hole against a different roster, and it is unguarded.

### What was measured, before any edit

| file | roster | state today | guard |
|---|---|---|---|
| `skillranks.yml` | `SubSkillType` | clean | ✅ **both directions** — `RankConfigTest.everyShippedRankSectionMapsToALiveSubSkill` (yml→enum) + `RankConfig#checkConfig` (enum→yml) |
| `coreskills.yml` | `PrimarySkillType` | clean, **26 / 26** | ❌ **none, either direction** |
| `sounds.yml` | `SoundType` | clean, **17 / 17** | ❌ **none, either direction** |
| `hidden.yml` | n/a — 3 free knobs | 🔴 **2 of 3 DEAD** | ❌ none |

🔑🔑 **Both files already contain a `values()` walk that LOOKS like a roster check and is not — and
both fail by the same mechanism, a missing key resolving through a DEFAULT.** This is the third
appearance of §52's `Snowman`/`Vex` shape.

- `CoreSkillsConfig#loadKeys` walks `PrimarySkillType.values()` and reads
  `config.getBoolean(enabledPath(skill), true)`. **A missing key returns the default `true`, which is
  byte-for-byte indistinguishable from a present `true`.** Enum→yml is therefore not checked. Dead
  keys are never visited at all, so yml→enum is not checked either. ⚠️ The default is *correct* —
  failing closed would silently switch the mod off, and `primarySkillEnabledDefaultsTrueForUnlistedSkill`
  pins that deliberately. The defect is that nothing else asks the question the default suppresses.
- `SoundConfig#validateKeys` walks `SoundType.values()` and reads
  `config.getDouble("Sounds." + soundType + ".Volume")` **with no default**. A missing section yields
  `0`; `0 < 0` is false; **the validation passes.** A `SoundType` with no section is invisible.

**The dangerous direction is yml→enum.** A dead key is a switch the player sets and nothing reads —
`coreskills.yml` is written to disk and *is* player-editable, so a renamed skill leaves a row that
looks live and does nothing. That is how `Unarmed.Disarm` and `Unarmed.IronGrip` outlived their
mechanics (item 1.1), and `RankConfigTest`'s own comment already names it as the trap.

**Neither file has a live defect today** — 26/26 and 17/17 both directions. There is nothing to fix
and everything to guard: the next skill added or sound renamed drops out silently, and an added enum
constant is invisible to every incremental diff (Cooking shipped across six commits with zero wiki
mentions).

### 🔴 `hidden.yml` — 2 of 3 knobs dead, proven across the whole repo

- **`Chunklets`** — **two hits in the entire repository**, both in `hidden.yml`: the row and its own
  comment. `HiddenConfig#load` never reads it. Zero Java, zero scripts, zero docs. It is a Bukkit-era
  Chunklets metadata-store switch that has no meaning in a Fabric singleplayer port.
- **`ConversionRate`** — read into a field and exposed via `getConversionRate()`, and **that accessor
  has no caller anywhere**: 4 Java hits are the field, the `getInt`, the accessor and its `return`.
  The only other hit is `HiddenConfigTest` asserting `assertEquals(1, config.getConversionRate())` —
  **a test pinning a value nothing consumes.** It proves the plumbing, not that the knob does
  anything. Chunklets' conversion tick-rate; dead for the same reason.
- **`EnchantmentBuffs`** — live, one real consumer: `SkillUtils.java:65`. **Keep.**

🔑 **`hidden.yml` is bundled-only and never copied to disk** (`HiddenConfig`'s javadoc, confirmed by
`run/config/mcmmo/` holding the other three and not this one). So this is **dead code, not a live
player-facing defect**, and deletion carries no install-migration concern — nobody has an edited copy
to orphan. That is the opposite of §50, where `copyMissingDefaults` meant additions *did* reach
existing installs.

### The ruling

1. **Both directions, for both files, in the existing test classes** — mirroring `RankConfigTest`,
   which holds its roster test beside its behaviour tests. Read the **bundled classpath resource**,
   not the disk copy, so the guard tests what ships.
2. **Both directions, not one.** `RankConfigTest` needs only the converse because `RankConfig#checkConfig`
   supplies enum→yml. Neither of these files has that, and the two walks that look like it are the
   defect, so the test owns both halves.
3. **Delete `Chunklets` and `ConversionRate` outright** — rows, comments, field, `getInt` read,
   accessor, and the test assertion that pins it. Unreachability proven above across the whole repo
   before any deletion, per §50's precedent.

### Steps

- [x] **55.1** — `CoreSkillsConfigTest`: yml→enum (dead key) + enum→yml (missing row), vs
      `PrimarySkillType.values()`, against `/coreskills.yml` on the classpath.
- [x] **55.2** — `SoundConfigTest`: the same two directions vs `SoundType.values()`, against
      `/sounds.yml`. Section keys live under the `Sounds:` root beside the scalar `MasterVolume`,
      which is **not** a `SoundType` and must be excluded by name, not by shape.
- [x] **55.3** — delete the two dead knobs from `hidden.yml`, `HiddenConfig` and `HiddenConfigTest`.
- [x] **55.4** — **mutation-prove every new assertion.** Each must go RED for the right reason:
      add a bogus yml key; delete a real one; and for 55.3, confirm `EnchantmentBuffs` still reaches
      `SkillUtils`. A guard that has never failed is not known to work — thirteen vacuous sightings.
- [x] **55.5** — build, full suite, read the `N executed` line.
- [x] **55.6** — propagate to all eight bands with `Backport-of:`, then gate 7.

### The outcome — 2 dead knobs deleted, 5 guards added, and a 14th vacuous test

**Suite 1,872 → 1,876 executed, 168 classes, 0 failed, 0 skipped** (`--rerun-tasks`; `BUILD
SUCCESSFUL in 9s` on the first attempt was `:test` UP-TO-DATE and proved nothing).

**Neither roster had a live defect** — `coreskills.yml` 26/26 and `sounds.yml` 17/17, both
directions. That was the expected result and is not the point: both files now fail closed, and the
two `values()` walks that looked like this check are documented in the tests as not being it.

🔑🔑 **A 14th vacuous guard, and it was guarding this exact hole.**
`primarySkillEnabledDefaultsTrueForUnlistedSkill` asserted *"Mining has no entry in the bundled
default → defaults true"*. **`Mining.Enabled: true` has been present all along** — all 26 are — so it
re-ran the explicit-true branch above it and **the default branch it was named for was never
reached**. The test that claimed to cover the unguarded mechanism was the reason nobody looked.

🔑 **And that branch is UNREACHABLE through the public surface, which is the finding rather than an
excuse.** `copyMissingDefaults` back-fills any key the bundled default has, so a user deleting a row
gets it returned; the only way to reach `getBoolean(path, true)`'s default is for the **bundled**
file to omit a skill. That is exactly the drift `everyPrimarySkillHasAnExplicitRow` now forbids, so
the replacement asserts the precondition that keeps the branch dead instead of pretending to enter it.

🔴 **`hidden.yml`'s two dead knobs, deleted after proving unreachability across the whole repo:**
`Chunklets` had **two hits in the repository**, both inside `hidden.yml` — the row and its own
comment; `HiddenConfig#load` never read it. `ConversionRate` was read into a field whose accessor had
**no caller anywhere**, and was pinned by an `assertEquals(1, config.getConversionRate())` that
proved the plumbing while nothing consumed the value. `EnchantmentBuffs` is live
(`SkillUtils.java:65`) and is kept. ⚠️ **Dead code, not a live player defect** — `hidden.yml` is
bundled-only with no disk copy, so nobody had an edited copy to orphan. The opposite of §50.

### 55.4 — the mutation run, and the vacuity it caught in itself

**6 mutations, 6 caught, each reddening its OWN named assertion**, read from the JUnit XML's failing
`<testcase>` rather than from the exit code.

| # | mutation | reddens |
|---|---|---|
| M1 | delete `Mining:` from `coreskills.yml` | `everyPrimarySkillHasAnExplicitRow` |
| M2 | add `Woodcuting:` (a typo'd skill) | `everyCoreSkillsSectionMapsToALivePrimarySkill` |
| M3 | delete the `ANVIL:` section from `sounds.yml` | `everySoundTypeHasASection` (+ the existing `readsPerSoundVolumeAndPitch`, correctly) |
| M4 | add a `CHIMERA_WING:` section | `everySoundsSectionMapsToALiveSoundType` |
| M5 | re-add `Chunklets` to `hidden.yml` | `everyHiddenOptionIsRead` |
| M6 | remove the **live** `EnchantmentBuffs` | `everyHiddenOptionIsRead` |

🔑🔑 **The FIRST mutation run reported all six RED and proved absolutely nothing.** The harness
shelled out to gradle from python, where `bash` resolves to **WSL's** bash — `execvpe(/bin/bash)
failed` — so the launcher died before gradle started and returned **the same exit 1 a caught
mutation returns**. Six launcher failures, scored as six caught mutations. It was caught only
because the harness also required the output to **name the target assertion**, which never matched.
⚠️ **Record the failing testcase NAME, never the exit code.** This is the same shape as the collision
audit that under-reported by 52× while exiting 1 either way — a mutation harness is not exempt from
being the vacuous thing.

⚠️ Restores are byte-compared against a saved original and the driver **stops the whole run** on a
mismatch, so an aborted case cannot leave a mutated resource in the tree. Verified after the run:
`git status` showed exactly the six intended files, `hidden.yml` at 0 insertions / 4 deletions, and
no `Chunklets`/`Woodcuting`/`CHIMERA_WING` string anywhere in `src/main/resources`.

### Noticed here, deliberately not ridden

- ⬜ **`SoundType` carries a `minecraft:` sound-event registry id per constant**
  (`minecraft:block.anvil.place`, …) and **nothing validates them**. A real id surface, but it needs
  the MC sound registry rather than `mc-ids.txt`'s item/block/entity kinds, so it is a section of its
  own — not a widening of this one.
- ⬜ **`sounds.yml`'s `CustomSoundId` takes a registry id too.** Every shipped value is `''`, so
  there is no defect today and no guard either; it lands with the row above.
- ⬜ **`hidden.yml`'s header still says *"You will need to reset any values in this config every time
  you update mcMMO"***, which is false — the file is bundled-only and has no user copy to reset.
  Left alone to keep this diff to the ruling.


### 55.6 — propagation, and the pre-check that ran BEFORE it

**All eight bands cherry-picked clean, gate 7 green: 0 MISSING on every band**, self-test passed
first (a broken auditor prints *"No drift"* too), run inside `git clone --local --no-hardlinks`
because `band_branches()` prefers REMOTE refs and an in-place run would have graded the stale
remote.

🔑 **A static cross-band pre-check ran before the first cherry-pick**, reading each branch's
rosters straight out of git: a band whose enum disagreed would be a defect to fix on `master`
FIRST, not something to discover after eight picks. All nine branches: **26/26 skills, 17/17
sounds, both directions**. No surprise, which is the answer that made the propagation safe rather
than lucky.

⚠️ **That pre-check's FIRST run reported a `Woodcutting` orphan on all nine branches — including
`master`, whose real suite was green.** The enum regex required a trailing `,` or `;` and
`WOODCUTTING` is the last constant, terminated by the closing brace alone. **The probe was wrong,
not the branches.** It now runs a control against master's known 26/17 and refuses to report a
band verdict if it cannot reproduce it.

⚠️ **`git cherry-pick` has no `-q`.** The first propagation pass printed CONFLICT on all eight
bands and had touched nothing — the unknown flag failed the command, and `--abort` then said
*"no cherry-pick in progress"*, which is the tell. Every band was re-verified at 0/0 against its
origin tip before the retry.

⚠️ **The `Backport-of:` trailers landed with NO blank line before them**, so `git log`'s
`%(trailers)` does not see them. Harmless HERE and deliberately not rewritten: `drift-audit.py`
matches `TRAILER` as a **multiline regex over the message text** (line 72), not through git's
trailer parser, and gate 7 reads them correctly. Worth knowing before anything else is built on
`%(trailers)`.

| branch | tip | suite |
|---|---|---|
| `master` | `2aed0305e` | 168 classes, 1,876 executed, 0 failed |
| `mc/26.1.2` | `f37b54da9` | 168 / 1,876 / 0 |
| `mc/1.21.1` | `03ccb632f` | 167 / 1,874 / 0 |
| `mc/1.21.11` | `60e070735` | not built |
| `mc/1.21.10` | `db64de5bd` | not built |
| `mc/1.21.8` | `f34b5070e` | not built |
| `mc/1.21.5` | `797ec7542` | not built |
| `mc/1.21.4` | `ae2faef55` | not built |
| `mc/1.21.3` | `5233844e0` | not built |

🔴 **Six bands are propagated but NOT BUILT.** `mc/26.1.2` (official names) and `mc/1.21.1`
(yarn) were built to cover both naming schemes, and the changed files touch no Minecraft type at
all — our own enums, snakeyaml and a bundled resource — so the risk is low. **Low is not zero,
and this is the honest state: the remaining six are verified statically, not built.**
### What this section is NOT doing

- **Not extending gate 4** (`config-id-audit.py`). Measured: no registry ids in any of the four. The
  instrument would run, report clean, and mean nothing.
- **Not touching `skillranks.yml`** — already guarded both directions. Re-guarding it is ceremony.
- **Not adding a per-sub-skill switch.** `CoreSkillsConfig`'s dropped `isSkillEnabled(AbstractSubSkill)`
  is a deliberate GitHub #10 decision, not debt.
- **Not wiring `ConversionRate` up.** No consumer exists and none is owed in a singleplayer port.
- **Not generalising to a roster-audit script.** Three files, two rosters, and `RankConfigTest`
  already sets the in-suite pattern. A script would be a fourth instrument for a question the suite
  answers unattended.
- **Not bumping `mod_version` or pushing.** That is a separate ruling, made once the suite is green.

### Rollback

Working tree clean and all nine branches at their origin tip at section start (`master` `687643963`).
Every change here is a tracked-file edit, so the undo is `git restore <path>` before commit or
`git revert <sha>` after. Nothing is deleted that is not recoverable from the commit that removed it,
and nothing outside the repo is touched.

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
4. `python scripts/config-id-audit.py --self-test` **then** `--check` — **0 dead-everywhere**,
   over **875 references / 26 sections / 7 files** as of §50. Reads the committed
   `scripts/mc-ids.txt`, so it needs no local Loom cache.
   ⚠️ **Cherry-pick `extract-mc-ids.py` + `mc-ids.txt` together** — the audit imports the generator's
   parser and refuses to run without it.
   ⚠️ **Two unresolved-on-control rows are CORRECT and must stay** — the `Chain`/`Iron_Chain` pair in
   `config.yml` and `experience.yml`. Exactly one of each pair is live per version; that is the
   both-names pattern working, and only DEAD-EVERYWHERE is a defect.
   🔑 **Since §50 this gate has a second, unattended leg**: `ConfigYamlBonusDropsTest` asks the
   **live registry** the same question inside gate 1, on every push, on every band. The script is
   still the only half that can compare *across* versions, so neither replaces the other.
   ⚠️ **It fails closed on an unclassified `Bonus_Drops` sub-section.** Adding one to `config.yml`
   without a `BONUS_DROP_KIND` entry refuses the run rather than skipping the section — trace the
   skill's bonus-drop seam and record BLOCK or ITEM. Note the kinds do **not** match
   `experience.yml`'s: both call Smelting an ITEM, but this file keys it on the furnace **result**
   and that one on the **input**.
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

✅ **R14 — CLOSED on `master` 2026-08-26 by §45**, and **pushed + released at `v1.3.1`** on all
nine branches by §49. Mockito's agent is now installed at VM start via `-javaagent`, so
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
      which reads the **resolved `compileJava` args**, not `build.gradle`'s text. ✅ **Pushed and
      released at `v1.3.1`** on all nine branches (§49).
- [x] ✅ **`mc/26.1.2`'s boot check and gameplay smoke — CLOSED 2026-08-26 (§43.1),** before the
      band was ever pushed: boot-check **exit 0** (0 ERROR, 0 mixin failures) and gameplay smoke
      **30 passed / 0 failed / 0 inconclusive**, with the mod-less control failing as it must.

- [x] ✅ **R14's suite flake — CLOSED 2026-08-26 (§45).** Mockito's agent is installed at VM start
      via `-javaagent` on `master` (`b92be8721`) and cherry-picked to all eight bands; all nine built
      green with the self-attach warning gone from every result file. Guarded by
      `MockitoAgentPreinstalledTest`, which reads **this JVM**, not `build.gradle`'s text. ✅
      **Pushed and released at `v1.3.1`** on all nine branches (§49).
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
- [x] ✅ **R13 — CLOSED 2026-08-27 (§54)**, `--overload-rebind`: 2,251 sites, ZERO armed.
      Carried out of §33; detail under §9 and §54.
      ✅ §31.5 is CLOSED (§51) — 39 sites reviewed, zero defects.
- [x] ✅ **The TYPE-AGNOSTIC call site — CLOSED 2026-08-27 (§53)**, `--type-agnostic`: 18 sites,
      all read, zero defects; it lands on the historical defect's own line.
      Originally raised out of §51 (2026-08-27).** §51 proved the collision
      residue is safe *because javac rejects a mis-bind whenever arity or return type differs*. The
      one defect that ever got through — `MANNEQUIN_ID.equals(BuiltInRegistries.ENTITY_TYPE.getId(…))`
      — got through because **`equals(Object)` erased the difference**: the `int` autoboxed, it
      compiled, and it returned `false` forever. **The risk is not the collision count; it is the
      set of call sites whose result is consumed type-agnostically** — `equals(Object)`, string
      concatenation, `var`, a raw generic. Nothing in this repo looks for that shape. It is a
      different instrument from `--receivers` and gets its own section.

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
