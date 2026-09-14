# Multi-Version Support — Development TODO

**Scope:** Fabric only. Target: every stable **`1.21.x` (12)** and **`26.x` (4)** = **16 versions**.
NeoForge/Forge deferred (see bottom). The `1.20` line was ruled IN by R-v and back OUT by **R-x**
(2026-08-20) before any of it was built — it is out of scope on **scope grounds, never measured**.

**Strategy:** branch-per-band (ruling **R-a**). `master` **is** the newest band; `mc/**` exists only
for older bands and is cut by hand. A **band** = a contiguous range of MC versions across which
mcMMO's touched surface is identical, measured by `scripts/probe-bands.py` against the 1415-record
manifest — a lookup, not a judgment call.

> **Archives — five files, and they hold the evidence this one summarises.**
> Phases 0–7: [plans/completed/TODO-multiversion-phases-0-7.md](plans/completed/TODO-multiversion-phases-0-7.md).
> Everything through Phase 21 (verbatim copy at `06eaaf7ae`):
> [plans/completed/TODO-multiversion-through-phase-21.md](plans/completed/TODO-multiversion-through-phase-21.md).
> **§8.3 and §22 – §33** (verbatim copy at `d5fb36dbf`, the whole `26.2` port):
> [plans/completed/TODO-multiversion-through-section-33.md](plans/completed/TODO-multiversion-through-section-33.md).
> **§37 – §47** (verbatim copy at `ee57abdec`, the nine-branch ship and the harness work):
> [plans/completed/TODO-multiversion-through-section-47.md](plans/completed/TODO-multiversion-through-section-47.md).
> **§48 – §61** (verbatim copy at `11708ca06`, the config-id gate's growth to every kind, the
> `26.x` rename's tail, and the three-gate push that closed the declared range):
> [plans/completed/TODO-multiversion-through-section-61.md](plans/completed/TODO-multiversion-through-section-61.md).
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
| `master` | `minecraft_version=26.2`, `java_version=25`. 🔴 **`mod_version` LEFT THIS ROW ON PURPOSE.** It sat here reading `1.3.2-SNAPSHOT` through the `1.3.3` **and** `1.3.4` bumps. It is also never a `master` fact: **R-p** requires it identical on all nine and **gate 11** enforces that, so a value written here is a value that rots on nine branches at once. Measure it: `grep -E '^mod_version=' gradle.properties`, on the branch you are on |
| releases | 🔴 **THIS ROW NO LONGER CARRIES A VERSION, AND THAT IS THE FIX** — the same remedy the `vs origin` row above already arrived at. It said `v1.3.2` while `v1.3.3` and then `v1.3.4` were the published set. What is *structurally* true: **nine releases, one per band**, tagged `mc<VER>-v<mod_version>`, and the declared 16-version scope is downloadable only when all nine are green. `gh release list` is the one thing that answers *“did it ship”* — ⚠️ a branch **agreeing** on `mod_version` is not evidence it released (gate 11), and §57 found one band of nine silently stuck a release behind. ⚠️ Deleting a tag **DRAFTS** its release; never undo one that way |
| build | ✅ **green on all nine**, each built on its own band this session (§44.3) |
| suite | ✅ **0 failures.** `master` measured **2026-09-01**: **170 classes / 1,882 executed / 0 failures / 0 skipped**, read off the JUnit XML with `> Task :test` confirmed **bare** (not `FROM-CACHE`) under `--no-build-cache cleanTest test`. 🔴 **This is a FOURTH figure, not a tie-break over the other three** — 1,869 (§50), 1,879 (§56.1) and gate 1's old `~1719` were each correct when written, and §57 and §56.4 landed between them. ✅ **Independently reproduced**: §57's table records `master` at **170 / 1,882** from a separate invocation by another session — two runs, two sessions, same number. ⚠️ **Per-band counts legitimately DIFFER** and the spread is per-band gating; §57's same-date table holds **six distinct totals across the nine**, with `mc/1.21.4` (**1,884**) *above* `master`. 🔴 **No branch is expected to lead**, and §56.2's older figures predate §57/§56.4 so they do not subtract against this one. **Re-measure your own branch; never match someone else's total** |
| gates 7/9/10/11 | ✅ **exit 0, none exit 2**, re-measured in §50 on a fresh `git clone --local --no-hardlinks` carrying all nine §50 tips. Gate 8 (`ci-watch.sh`) also exit 0 post-push, with all 5 mutations caught. ⚠️ All four prefer **remote** refs, so push first or clone locally |
| mixin gate | ✅ `--check` passes on `master` and `mc/26.1.2` (`ZERO=0 OK=60 SLICE=1`) |
| boot | ✅ **ALL 16 DECLARED VERSIONS**, not two — §59 booted the nine band primaries against the shipped jars, §60 the seven that had never been booted at all, §61.6 all sixteen in one sweep. The old wording of this row named `26.2` (§35) and `26.1.2` (§43.1) alone, which is where it stood before §59 |
| gameplay | ✅ **ALL 16 DECLARED VERSIONS at 36 / 0 / 0**, against the shipped `v1.3.4` jars (§59, §60, §61.6), mod-less control failing as it must. ⚠️ `1.21.4` read **red** on gate 6 in §61.6's sweep and **it was the harness, not the mod** — re-run on the same jar scored 36/0/0. 🔑 The confirm window is **2.0s, not 3s**, and the phase was landing its click on **401 ms of slack**. The old wording of this row carried `26.1.2` at a stale 30/0/0 |

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

✅ **Re-measured 2026-09-10 (§62)** from `git ls-remote --tags origin`, each branch's
`gradle.properties` and its own `fabric.mod.json`.
🔴 **This table has now gone stale TWICE, and the second time was worse than the first.**
The 2026-08-26 edition predated §43's push: headed *"6 branches pushed"*, `mc/26.1.2` omitted
entirely, `master` recorded as having **nothing released**, every tag at `v1.2.0` — all four false,
and all four **noticed**. The edition that replaced them then sat at `v1.3.1` through the `1.3.2`,
`1.3.3` **and** `1.3.4` bumps, and was noticed by nobody until this cleanup went looking.
🔑 **A row that has rotted twice will rot a third time.** The `vs origin` and `releases` rows
above already gave up carrying their numbers for exactly this reason; this table keeps its numbers
only because a **date and the command that produced them** are attached. Re-run the command.

⚠️ **There is no per-version jar and there never was. One jar covers a band**, via the range in its
own `fabric.mod.json`.

| Branch | MC versions covered | `depends.minecraft` | Released tag |
|---|---|---|---|
| `master` | `26.2` | `~26.2` | `mc26.2-v1.3.4` |
| `mc/26.1.2` | `26.1`, `26.1.1`, `26.1.2` | `>=26.1 <26.2` | `mc26.1.2-v1.3.4` |
| `mc/1.21.11` | `1.21.11` | `~1.21.11` | `mc1.21.11-v1.3.4` |
| `mc/1.21.10` | `1.21.9`, `1.21.10` | `>=1.21.9 <1.21.11` | `mc1.21.10-v1.3.4` |
| `mc/1.21.8` | `1.21.6`, `1.21.7`, `1.21.8` | `>=1.21.6 <1.21.9` | `mc1.21.8-v1.3.4` |
| `mc/1.21.5` | `1.21.5` | `>=1.21.5 <1.21.6` | `mc1.21.5-v1.3.4` |
| `mc/1.21.4` | `1.21.4` | `>=1.21.4 <1.21.5` | `mc1.21.4-v1.3.4` |
| `mc/1.21.3` | `1.21.2`, `1.21.3` | `>=1.21.2 <1.21.4` | `mc1.21.3-v1.3.4` |
| `mc/1.21.1` | `1.21`, `1.21.1` | `>=1.21 <1.21.2` | `mc1.21.1-v1.3.4` |

**Shipped coverage is continuous `1.21` → `1.21.11` plus `26.1` → `26.2` — the declared
16-version scope, closed.** ✅ **Re-measured 2026-09-10**, because this table had gone three bumps
stale: `gradle.properties` reads **`1.3.4-SNAPSHOT`** on `master`, and `git ls-remote --tags origin`
returns **nine `v1.3.4` tags**, one per band. ✅ **The clone now holds all nine** — it held eight
until §63 fetched the missing `mc26.1.2-v1.3.4` (2026-09-10). **`git tag --list` is still not the
instrument for this question**; `git ls-remote --tags` is. 🔑 The reason is unchanged by the fetch:
a local tag list is a **cache**, and it was wrong in both directions at once — six tags the remote
did not have, one the remote did. Agreement today is not a property of the instrument.
✅ **The six `v2.2.050` tags are DELETED and their provenance is settled (§63, 2026-09-10).** They were
not a mystery and `2.2.050` was not an impossible number: it **was** this repo's `mod_version` until R-s.
🔴 **They were a lower bound, not a count — 62 local tags are absent from the remote**, same cause,
and the other 56 are still here pending the owner's call (see *Carried debt*).

🔑 **Nothing in the twelve gates reads the remote TAG list.** Gates 9/10/11 compare branches; the
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
✅ **Residual (risk R12) — CLOSED by §64.2 (2026-09-10).** This read *"the map is hand-maintained.
A NEW skill whose items postdate the floor is added to `PrimarySkillType` and to nothing else, and
nothing goes red."* `SkillAvailability.UNGATED` now records the other half of the decision and
`SkillGatePartitionTest` requires every constant to sit in exactly one of the two sets, so a skill
added to the enum alone fails the build naming both remedies. 🔑 **It found a real omission on its
first run** — `WOODCUTTING`, the last constant, which a comma-anchored regex over the enum source
silently drops. The enum has **26** constants; two sessions independently measured 25.

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
| `1.21` … `1.21.11` | 12 versions, 7 bands | ✅ **SHIPPED**, all at **`v1.3.4`** (re-measured 2026-09-10). 🔴 This row has now been stale **twice**: it read `v1.2.0` for three sections after that bump, then `v1.3.1` through the `1.3.2`, `1.3.3` **and** `1.3.4` bumps |
| `26.2` | `26.2` | ✅ **SHIPPED — `master`.** Booted (§35), smoke **36/0/0** (§47), released `mc26.2-v1.3.4` |
| `26.1.x` | `26.1`, `26.1.1`, `26.1.2` | ✅ **CUT AND SHIPPED** as `mc/26.1.2` (§42, §43) — the three differ on **zero of 1424** records (§39), so one branch serves all three. Released `mc26.1.2-v1.3.4` |
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
- [ ] **x.9** Declare the new band: add its branch name to **`scripts/expected-bands.txt`**, on
      `master` first and then on every band. The floor is what makes *"found no bands"* fail instead
      of reading as a clean audit. Leaving it stale is under-strict rather than noisy — the audit
      still passes — which is exactly why nothing will remind you to do it.
      ✅ **§64.3 collapsed four hand-kept numbers into one declared list.** The floor used to be
      typed into `BAND_COUNT` in `.github/workflows/drift-audit.yml` **and** into ship-gate steps 9,
      10 and 11; `BAND_COUNT` is **gone**, and every consumer now reads
      `python scripts/expected_bands.py --count`. Adding a band is **one line in one file**.
      🔑 **And the check got stronger in the same move: a count became a SET.** A *renamed*
      band keeps the count and breaks the set — the old floor could never see it.
      `python scripts/expected_bands.py --verify` reports both directions by name.
      ⚠️ **It is still hand-maintained.** What changed is the number of copies and the strength of
      the check, not that a human declares it. Run `--self-test` first: *"sets match"* is also what a
      broken comparator prints.
      🔴 **Never regenerate the declaration by globbing `mc/**`.** That makes the comparison
      `len(x) >= len(x)` — always true — and deletes the guard while leaving it looking present.
      `--require-bands` exists *precisely* because that enumeration can come back short.
      ⚠️ **They are allowed to differ by design** — `--require-bands` counts `mc/**` only and
      `master` lives outside that namespace, so a floor one too high returns exit 2 while the same
      run still prints *"No drift"*.
      🔑 **This line used to carry the number, and the number rotted.** It read *"At `6` since
      2026-08-19"* until 2026-09-10, left behind by §43.3's raise to 8 in the ordinary way: the commit
      that changed the status did not update the row that states it. Same defect §62 found six times,
      same fix L587 already prescribes — **stop carrying the number, name the command.** 🔴 It costs
      more here than in a status table, because **a recipe is read at the NEXT band cut**: `6` would
      have set the floor two bands too low, the audit would have passed, and nothing would have said so.
      Raised as 8.3's x.9 one release cycle late, which is itself the evidence: 8.3 shipped and
      released with the floor still admitting five bands, and every gate stayed green throughout.
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
| **9.5** full ship gate | ✅ **RUN (§35, §43.4).** Gate 1 (suite 1,861), gate 2 (`ZERO=0 OK=60 SLICE=1`), gate 3 (`boot-check.sh`, exit 0) and gate 6 (**36/0/0**, control failing as it must) are all green on `26.2`; gates 7/8/9/10/11 ran post-push in §43.4 and 7/9/10/11 again in a local clone after §47. ✅ **Gates 4 and 5 have BOTH now run on `26.2`, and this row's caveat was FALSE when read.** It said neither had a recorded `26.2` run — but §56.1 recorded gate 5's first `26.2` run, PASSED, **in this same file**, and the row was never revisited. 🔴 **It was not inert: a peer session quoted it back as fact on 2026-09-01**, which is what a stale caveat costs. §59 then ran both on `26.2` against the shipped `v1.3.4` jar — gate 5 PASSED with its vanilla control failing as it must, and gate 4 self-test PASS + `--check` exit 0 over **1011 references / 30 sections / 8 files**, 0 dead-on-every-version. ⚠️ *Absence of evidence* was the right words for the wrong row — the evidence existed two thousand lines below |

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
- [x] ✅ **`build.gradle:2`'s bare `fabric-loom` id — MEASURED, CLOSED by §64.1 (2026-09-10).**
      The wording above was *"Resolved on `master` (it is the explicit non-remap id); what the
      **bare** id does on the `1.21.x` branches is inferred, not measured"* — and §62 carried it
      across **verbatim, deliberately, "including wording I think is now wrong"**. 🔑 **Measuring it
      is the event that earns the rewrite; until 64.1 there was nothing to replace it with.**
      Read out of Loom 1.17.13's bytecode: five plugin ids are registered, and
      `LoomGradleExtensionImpl`'s constructor branches on `hasPlugin("net.fabricmc.fabric-loom")`,
      setting `disableObfuscation=true` and `finalizeValue()`-ing it — forced and unoverridable —
      which forces `dontRemap`. The bare id leaves both computed. So the parenthetical was **right**,
      and §35's *"Loom registers no `remapJar` on `26.x`"* now has its mechanism.
      ⚠️ **Reading the wrapper classes alone gives the opposite, convincing answer**: both
      `LoomNoRemapGradlePlugin` and `LoomRemapGradlePlugin` merely `plugins.apply("fabric-loom")`.
      🔴 **The row was right that it matters, and understated how**: a *coordinated* conversion of a
      band to `master`'s whole posture is internally coherent and no gate inspects that line —
      `build.gradle` is outside gate 10's identity set by design and gate 11 reads
      `gradle.properties` keys only. Guarded by `BandLoomRemapPostureTest`, anchored to
      `minecraft_version` rather than to self-consistency, because self-consistency calls the
      converted band correct.
      ⚠️ **Corrected while measuring:** the conversion does fail today, but *incidentally* — on
      `cloth-config`'s access widener, an optional dependency, with an error naming the wrong
      culprit. Not "no gate catches it"; "nothing names it, and the accidental catch can vanish".
- [ ] ⬜ **`TODO.md`'s one-blob-on-every-branch invariant.** `master` no longer describes the same
      product as the bands, so propagation cannot fix the drift this time. Decide at 9.5 whether the
      invariant survives the `26.x` split at all.
      📌 **Measured 2026-09-01: the invariant HOLDS — `git rev-parse <b>:TODO.md` is one blob on
      all nine.** 🔴 **That measurement does NOT answer this row, and must not be read as closing it.**
      The row is about whether the CONTENT is true per band; byte-identity is a fact about BYTES. Nine
      identical copies of a document describing `master` is exactly consistent with the concern — it is
      arguably the concern itself. **R-y's first run found `master` and five bands serving a claim that
      was FALSE on `mc/1.21.1`, with byte-identity intact throughout, and the BAND was right.**
      🔑 **Cross-branch equality is not correctness.** What the measurement does buy: declining to
      propagate a `TODO.md` edit would break a nine-way identity by OMISSION, which is this row being
      decided by default rather than at 9.5.

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

## §8.3, §22 – §61 — closed, and where the reasoning lives

Full text in **three** archives:
[TODO-multiversion-through-section-33.md](plans/completed/TODO-multiversion-through-section-33.md)
holds §8.3 and §22 – §33 (verbatim at `d5fb36dbf`), and
[TODO-multiversion-through-section-47.md](plans/completed/TODO-multiversion-through-section-47.md)
holds §37 – §47 (verbatim at `ee57abdec`, moved by §48), and
[TODO-multiversion-through-section-61.md](plans/completed/TODO-multiversion-through-section-61.md)
holds §48 – §61 (verbatim at `11708ca06`, moved by §62 below).

🔑 **How to resolve a `§n` reference with no heading in this file:** §48 – §61 are in the
section-61 archive; §37 – §47 are in the
section-47 archive; §8.3 and §22 – §33 are in the section-33 archive; anything numbered lower
(§10.7, §21.6, the Pass-1 `item N` numbers cited in source comments) is in
`TODO-multiversion-through-phase-21.md`. Nothing has been deleted — only moved.
⚠️ **§34, §35 and §36 never had a heading in any file.** They were worked; their outcomes were
recorded in §9's table and 9.3's checklist above and nowhere else. A `§35` reference resolves to
those rows — and to `.agent/memory/`, which is not committed (R-n) and so does not exist in a fresh
clone.
🔑 **Source comments cite these numbers — 71 of them, across 11 files, counted 2026-09-10.**
The section-61 archive carries the bulk: §52 is named 18 times, §60 14, §61 12, §50 11, §51 5.
`config-id-audit.py`, `brew-smoke.sh`, `rename-to-official.py`, `extract-mc-ids.py`,
`version-sweep.sh`, `gameplay-smoke.sh`, `boot-check.sh`, `gameplay_smoke_scenario.py`,
`ConfigYamlBonusDropsTest`, `ConfigIdManifestTest` and `CombatMultiplierCoverageTest` are the
eleven. From the older archives: `CompilerErrorCapTest` names *TODO.md 44.2*;
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
| **48** | the fourth archive — the last TODO cleanup | ✅ §37 – §47 moved out; the file went 2,619 → ~700 lines. Found **four** false claims, **all four in the first 135 lines**. 🔑 Its sentence for the shape is still the best one: *a status sentence is never updated by the commit that changes the status, because nothing reads it* |
| **49** | the `mod_version` bump that releases §44 – §48 | ✅ `1.3.1-SNAPSHOT` on all nine, nine green release runs, nine `v1.3.1` releases. 🔑 §44's **push had SUCCEEDED — only the release was refused**, and from a clean `git status` those two look identical |
| **50** | `config.yml` joins the config-id gate | ✅ **two live defects**: `Block_Of_Amethyst` (not a registry id on **any** version, while `experience.yml` paid it 500 XP) and a `Chain` with no `Iron_Chain` beside it (chains lost their bonus roll on the four newest bands). 🔑 `config.yml` was in **neither half** of the gate, and the carried row naming one defect was a **lower bound, never a count** |
| **51** | the collision review list, filtered on the RECEIVER TYPE | ✅ the audit had been under-reporting **52×** in its default mode — the 13th vacuous-guard sighting |
| **52** | entity ids join the config-id gate — the KIND that was never in it | ✅ **six defects, two of them severe**: `Vex` and `Creaking` paid **zero** combat XP (an ABSENT row, which no id audit can see) and `Snow_Golem`'s deliberate `0.0` was inert under the Bukkit spelling. 🔑 The carried row that raised this named `advanced.yml` — the **wrong file**; the real gap was a whole **KIND**, 138 entity-keyed rows never audited on any branch |
| **53** | the TYPE-AGNOSTIC call site | ✅ 18 sites read, **zero** defects, and the instrument lands on the historical line rather than on a hypothesis |
| **54** | R13, the general overload-rebind shape | ✅ **R13 CLOSED** — and the planned instrument **could not have worked**. The replacement answers R13 at **zero** cost |
| **55** | the roster gate for `coreskills.yml` and `sounds.yml` | ✅ 2 dead `hidden.yml` knobs deleted, 5 guards added. 🔑 The gate is **roster-keyed, not id-keyed** — the carried row had named the wrong gate — and the **14th** vacuous test caught here was the **mutation harness itself** |
| **56** | the four pieces the owner picked over the push | ✅ 56.1 – 56.4 shipped; **56.5 measured, then DECLINED** (and the recorded reason for declining was itself false — `sound_event` is in the same registry dump; three sessions mis-estimated it, each reading a different part). Ship gate **12** came out of 56.4. 🔴 **56.3's gate-5 row — *"the eight bands still have no recorded run"* — is FALSE as of §59/§60**; the archive preserves the stale wording, **this row is the correction** |
| **57** | the fork race that stopped `mc/26.1.2` releasing | ✅ `fabric.modsFolder`, not a repo-root `mkdir`. One band of nine had been **silently a release behind** while every gate read green. 🔑 The **17th** vacuous assertion found in this repo was **mine, in this section's own new guard** |
| **58** | every kind is MAPPED; none proven CROSS-CHECKED | ✅ and 58.1 found the same shape one layer along: a **missing kind section round-trips as a FALSE ZERO**, which reads exactly like a clean audit |
| **59** | gates 3/5/6 across the bands, against the SHIPPED artifact | ✅ nine bands × three gates, **zero defects**. 🔑 `gameplay-smoke.sh`'s CONTROL mode **INVERTS its exit code**, and **seven declared versions across five bands had never been booted by anything** — §56.4's defect turning up in three more instruments |
| **60** | the range gap — the seven never-booted versions | ✅ **all 16 declared versions** boot, brew and play. 🔑 Three harnesses, one blind spot, **one cause**: the fabric-api cache always hits for a band's **primary**, so no other path had ever been exercised. ⚠️ **Read WHY a control failed, never merely that it failed** — a vacuous control passes for the wrong reason |
| **61** | one command for the declared range | ✅ `scripts/version-sweep.sh`, propagated to all eight bands (61.7 — 48/48 trailers, **master-empty control returned 0**, gate 7 clean, gate 10 at 51 shared paths). 🔑 **A busy 25565 made all three gates say THE MOD IS BAD**, and gate 5's `both` mode printed a **false ✅ about a server that never started**. The sweep's one red was the **harness, twice over** |

---

## §62 — the TODO cleanup: closed narrative to a fifth archive — ✅ DONE

**Tier 1, docs only.** Owner-scoped 2026-09-10: *"if the todo list has been completed then clean it
up."* **The condition was checked before anything moved, and the honest answer is *partly*.**
§48 – §61 are closed — fourteen sections, **2,703 lines, zero `- [ ]` boxes** — while every genuinely
open item sits **outside** that range and is carried across untouched: the live play-test, §9's two
`⬜` rows, the whole risk register, the carried-debt list, and the band-cut recipe (whose `x.1 – x.10`
boxes are a **reusable procedure, not open work** — they are unchecked by design and must stay that
way).

### What moved

- [x] ✅ **`plans/completed/TODO-multiversion-through-section-61.md`** — §48 – §61, **verbatim**, in
      file order (which is why **§58 precedes §57**, the same accident the section-47 archive records
      for §38 and §37). Fifth archive; the header block at the top of this file now names five.
- [x] ✅ **One-line outcome rows for §48 – §61** appended to the closed-sections table, its heading
      widened to *§8.3, §22 – §61*, and the `§n` resolution rule extended to name the fifth archive.
- [x] ✅ **Every open box kept, verbatim.** Verified mechanically rather than by reading: **17**
      `^- [ ]` lines before, **17** after, and the two sets diffed identical.
- [x] ✅ **Six stale claims corrected** — the table below.
- [x] ✅ **One line edited on the way into the archive, and named there** — §61's heading said
      *"(Tier 2, in progress)"* and §61 closed on 2026-09-10. A heading states a section's **state**,
      not its history, so it was corrected rather than archived false.

### 🔑 The verification that nearly went wrong

**Counting `- [ ]` unanchored reports one match inside the moved range, and it is not a box.**
`TODO.md:533` — inside §48, *the section that invented this very check* — reads *"the set of `- [ ]`
lines was diffed before and after"*: a **code span inside a sentence describing the technique**.
Anchored on `^- [ ]` the in-range count is **0** and the file total is **17**.

A cleanup that trusted the unanchored count would have gone hunting for an open box that does not
exist, in the one section guaranteed to talk about open boxes. 🔑 **The false positive was generated
by the documentation of the check itself** — which is a shape worth remembering, because it gets
worse as this file gets better at recording its own methods.

### What was false, measured against disk rather than against the diff

| the claim | what disk said on 2026-09-10 |
|---|---|
| nine releases at **`v1.3.1`**, tags `mc<VER>-v1.3.1` — in **two** separate tables | `git ls-remote --tags origin` returns **nine `v1.3.4` tags**. Three bumps stale |
| `mod_version` is **`1.3.1-SNAPSHOT`** on every branch | `gradle.properties` reads **`1.3.4-SNAPSHOT`** |
| §61 — *"(Tier 2, in progress)"* | closed 2026-09-10, when 61.7 reached all eight bands |
| boot: `26.2` (§35) and `26.1.2` (§43.1) | **all 16 declared versions** boot (§59, §60, §61.6) |
| gameplay: `26.2` 36/0/0, `26.1.2` **30/0/0** | **all 16** at 36/0/0 (§60, §61.6) |
| §56.3 — *"the eight bands still have no recorded run"* for gate 5 | closed by §59 (nine bands) and §60 (all 16 versions) |

🔑 **Five of the six sat in the first 143 lines** — the part every session reads first, and the part
no commit doing the work ever touches. **This is the second consecutive cleanup to find that exact
distribution**; §48 found four false claims and reported *"all four sat in the FIRST 135 lines"*.
Length is what makes them survivable: the correction is always further down, in a section nobody
re-reads once it is marked ✅.

⚠️ **The `v1.3.1` row had already been corrected once, from `v1.2.0`, and went stale again through
three more bumps.** A row that has rotted twice will rot a third time — the fix is the one the
`vs origin` and `releases` rows already arrived at: **stop carrying the number, name the command**.

### 🔴 Found while measuring, deliberately not acted on

**Six local-only tags exist that no release ever produced:** `mc<VER>-v2.2.050` on `mc/1.21.3`,
`mc/1.21.4`, `mc/1.21.5`, `mc/1.21.8`, `mc/1.21.10` and `mc/1.21.11`. They are **absent from the
remote**, and they point at unrelated August commits (`edd7a8932` *"the sweep skipped its own
orphans"*, `44e3dc1d0` *"the Taming rulings"*) rather than at any release.

✅ **RESOLVED by §63 (2026-09-10) — and the sentence that stood here was FALSE.** It read
*"`2.2.050` is a version this repo has never carried"*. It was carried:
`git log --all -p -- gradle.properties | grep mod_version` returns **`mod_version=2.2.050-SNAPSHOT`**,
retired by **R-s** (2026-08-18) in favour of `1.0.0-SNAPSHOT`. 🔑 **The claim was reached by checking
the number against the CURRENT value and the release table — both of which post-date R-s.** Asking
*"has this repo ever carried X?"* of any present-tense source answers a different question; the
instrument is `git log --all -p`. Full provenance in §63.

**Not deleted, and that is a decision rather than an omission.** Deleting a tag **DRAFTS** its
release (this file's own standing warning), these are not in scope for a docs cleanup, and nobody has
established where they came from. 🔑 **It also means `git tag --list` is the wrong instrument for
"what shipped"** — as measured that day it **held six tags the remote did not, and was missing**
`mc26.1.2-v1.3.4`, **which the remote had**: wrong in both directions at once.
**`git ls-remote --tags origin` is the instrument.** ✅ Both directions were closed by §63 on the
same day (six deleted, the missing one fetched) — but the lesson is about the **instrument**, not
the counts, and it survives them being reconciled.

### What I did NOT do

* **Not re-litigating any open item.** The live play-test, `build.gradle:2`'s bare `fabric-loom` id,
  the `TODO.md` one-blob row, R8 – R12 and every carried-debt row are carried across **verbatim**,
  wording included — including wording I think is now wrong.
* **Not deleting anything.** Every line removed from this file is in the new archive.
* **Not correcting the false claim inside the archive.** §56.3's stale gate-5 row is preserved as
  written; an archive records what was written, and the correction belongs in the live §56 row.
* **Not pushing.** The hold stands (owner, 2026-09-03). `master` was **28 ahead**, the eight bands
  **24 ahead**, nothing behind — ⚠️ **re-measure, never quote**.
* **Not touching `README.md`, `wiki/**`, `AGENTS.md` or `scripts/**`.** Nothing here is a
  player-facing claim, and gate 10's identity set is untouched by construction.

### 🔑 What the gates do NOT check here

**Neither propagation gate verifies this work, and the `Backport-of:` trailer buys no checking.**
`drift-audit.py` ignores a docs-only commit **by design**, and `TODO.md` is **not** in gate 10's
identity set (`AGENTS.md`, `.gitignore`, `.github/workflows/*.yml`, `scripts/**`, `README.md`,
`wiki/**`). A green gate 7 and a green gate 10 after this commit therefore mean **nothing about it**.

The one-blob invariant is verified **directly**, and this command is the evidence — not the gates:

```
for b in master mc/26.1.2 mc/1.21.11 mc/1.21.10 mc/1.21.8 mc/1.21.5 mc/1.21.4 mc/1.21.3 mc/1.21.1; do
  git rev-parse $b:TODO.md
done | sort -u | wc -l          # must print 1
```

⚠️ **And if you do run `drift-audit.py`, run it inside `git clone --local --no-hardlinks . <scratch>`.**
`band_branches()` prefers **remote** refs, and with the push held `origin` is dozens of commits
stale — a run in this working copy grades the remote and answers a question nobody asked.

### Propagation and rollback

`TODO.md` was blob `50a11cb3e` on **all nine** branches before this change, so the cleanup is
cherry-picked to all eight bands with a `Backport-of:` trailer, from a **scratch clone pushing band
refs back** so `HEAD` never moves (§61.7's pattern — git mechanically refuses a push to a
checked-out branch).

🟢 **Blast radius: two files, no `src/` change, nothing published, nothing pushed.** Undo, while
unpushed, is:

```
git checkout 11708ca06 -- TODO.md
rm plans/completed/TODO-multiversion-through-section-61.md
```

or `git revert <sha>` per branch. The pre-§62 tips are recorded in `.agent/memory/state.md`; the
archive is a **new** file, so a revert also removes it.

---

## §63 — the tag provenance, and a floor that rotted — ✅ DONE

**Asked:** *"if the todo list has been completed then clean it up"*, then *"fix it and then fix the
tags"*. 🔑 **The condition was CHECKED and the honest answer was NO** — §62 had already archived
§48 – §61 hours earlier, and the 18 remaining `^- \[ \]` boxes are live work, not residue. **10 of
the 18 are not work at all**: they are the per-band recipe, a template that is unchecked on purpose.
Reporting *"already done, and here is what is actually left"* was the deliverable; the two repairs
below are what came out of measuring it.

### The six `v2.2.050` tags — provenance settled, tags deleted

§62 raised them as *"nobody can account for"* and declined to delete, which was the right call on
the evidence it had. The account, from this repo's own history:

1. **`2.2.050` WAS this repo's `mod_version`.** `git log --all -p -- gradle.properties` returns
   `mod_version=2.2.050-SNAPSHOT`. It was upstream mcMMO's *Bukkit plugin* number, borrowed by a
   singleplayer Fabric fork sharing none of its cadence. **R-s** (2026-08-18) retired it for
   `1.0.0-SNAPSHOT`, partly because its padded patch does not survive Fabric's parser
   (`Version.parse("2.2.050")` → `2.2.50`), so the jar filename and ModMenu disagreed.
2. **They point at ordinary commits because the tag MOVED.** R-s records that the tag step
   *force-deletes and re-pushes the ref*, and `mc<VER>-v2.2.050` was **re-used on every push for
   roughly a month** because nothing forced a bump — the defect R-t's stale-version gate now blocks.
   A ref re-pointed on every push is not a release marker; it lands wherever that push's HEAD was.
   🔑 R-s's own note measures `mc1.21.11-v2.2.050` as *local `f18cbef82` vs origin `44e3dc1d0`*.
   **`44e3dc1d0` is exactly where this clone's copy pointed** — what survived here is the origin
   side of that very measurement, and `f18cbef82` is still in the repo to check it against.
3. **The remote was clean because the reap sweep worked.** R-s's sweep retired each band's
   `2.2.050` release *and its remote tag* automatically. **`git fetch` never deletes a local tag**
   without `--prune-tags`, so the local copies simply stayed.

**Deleted** (local only, nothing pushed, no remote release to draft). ↩️ **Undo:**
`git tag mc1.21.3-v2.2.050 f3ef33c0c` · `mc1.21.4 4b2716be6` · `mc1.21.5 edd7a8932` ·
`mc1.21.8 6c4ec8db4` · `mc1.21.10 1608d5084` · `mc1.21.11 44e3dc1d0`. Verified before the delete:
**all 62 local-only tags are reachable from a live branch**, so no commit was orphaned; verified
after: the six commits still resolve and the remote still has its 11 refs.

### 🔑🔑 Three things worth carrying

**1. The false claim was reached by asking a PRESENT-TENSE source a HISTORICAL question.**
*"`2.2.050` is a version this repo has never carried"* was checked against the current
`mod_version` and the release table — **both of which post-date R-s, the ruling that removed it.**
Every instrument consulted agreed, and all of them were answering a different question.
🔴 *"Has this repo ever…"* has exactly one instrument: `git log --all -p`.

**2. "Six" was a lower bound, and the count was never the finding.** Measuring the six properly
showed **62** local tags absent from the remote — the six plus **56** superseded release tags
(`v1.0.0` … `v1.3.3`), same cause, never pruned. Local held **71**, remote **10**. Same shape as the
`config.yml` row that understated its defect by 26×: **a carried row naming a specific defect is a
lower bound, never a count.** The 56 are left for the owner — 62 is a different blast radius than 6,
and *"they authorised the six"* does not carry.

**3. The obvious one-command fix is the dangerous one.** `git fetch --prune --prune-tags` fixes both
directions at once and is **wrong here**: it re-queries the remote and deletes every local tag not in
the answer, so a hiccup returning an empty list deletes **all 71**. It fails OPEN. The safe shape is
freeze the list to a file → read it → delete from the frozen list, which is what was done.

### The drift-audit floor — the number was removed, not re-numbered

Recipe step **x.9** claimed *"At `6` since 2026-08-19"*. Measured: `BAND_COUNT: '8'` at
`.github/workflows/drift-audit.yml:127`, against **8** `mc/**` branches. §43.3 raised it and left the
row behind — the ordinary shape, *the commit that changes the status does not update the row that
states it*, which §62 hit six times in one file.
🔴 **It was NOT re-numbered to 8**, per this file's own L587 ruling — **stop carrying the number,
name the command.** Re-numbering buys one edition and rots at the next band cut. The row now names
`BAND_COUNT` and the two commands that compare it. **A recipe is read at the NEXT cut**, so `6` would
have set the floor two bands too low with every gate still green — which is the whole reason the
floor exists.
✅ **Superseded by §64.3 (same day): the row no longer names `BAND_COUNT`, because `BAND_COUNT` no
longer exists.** The floor is declared once in `scripts/expected-bands.txt` and read with
`python scripts/expected_bands.py --count`. §63's fix — *stop carrying the number, name the command*
— is intact and is what made this edit one line instead of five; only the command changed.

### What I did NOT do

- **Did not delete the other 56 stale tags.** Safe (reachability-checked) but unauthorised; filed
  as an open box in *Carried debt* with the command and the `--prune-tags` warning.
- **Did not push.** The hold from §60 stands; `master` and the eight bands stay ahead of `origin`.
- **Did not touch the remote.** No tag push, no release, no draft — the delete was local by design.

### Propagation and rollback

- pre-§63 `master` tip: **`22cd71a60`** · pre-§63 `TODO.md` blob: **`0d36d940e`**, byte-identical on
  **all nine** branches (verified before the first write).
- undo, while unpushed: `git checkout 22cd71a60 -- TODO.md`, plus the six `git tag` commands above.
- ⚠️ **A `master`-only `TODO.md` edit silently breaks the nine-way blob identity, and NEITHER gate
  reports it** — `drift-audit.py` ignores docs-only commits by design and `TODO.md` is not in gate
  10's identity set. Verify directly, never by a green gate:
  `for b in master mc/26.1.2 mc/1.21.11 mc/1.21.10 mc/1.21.8 mc/1.21.5 mc/1.21.4 mc/1.21.3 mc/1.21.1;`
  `do git rev-parse $b:TODO.md; done | sort -u | wc -l` **must print 1.**

---

## §64 — three code items: the Loom id, the skill-gate partition, the band floor — 🚧 IN PROGRESS

**Owner-scoped 2026-09-10:** *"continue with the code portion of the todo list"*, then all three
candidates picked explicitly, **with propagation to all eight bands**. The push stays **HELD**.

🔑 **The open-box list has no `src/` item in it, and that is a finding rather than an obstacle.**
The composition of the box list is §63's finding and is **not restated here** — see it there, in one
place. What §64 adds is the consequence: **the real code work was not in the checkboxes at all.**
64.2 is **R12 Residual 1**, a line in the *risk register*; nothing in the box list points at it, and
a session working the boxes top-to-bottom would never reach it.

⚠️ **Do not inherit a box count from this file, including from this paragraph.** It read **18**
when §64 was drafted and **19** an hour later — a peer's `ce2b6d5d7` opened the CR-strip box at
L1059 mid-session. Three sessions independently derived 18 and all three were about to be wrong
together. `grep -c '^- \[ \]' TODO.md`, at the moment you need it.

**Rollback anchors, recorded BEFORE the first write:**
- pre-§64 `master` tip: **`ce2b6d5d7`** · pre-§64 `TODO.md` blob on `master`: **`d6b90cb4d`**
- undo this plan edit, while unpushed: `git checkout ce2b6d5d7 -- TODO.md`
- 🔴 **These are the SECOND set of anchors. The first were stale within minutes, and the way that
  was caught is the point.** They were recorded as tip `d998b81a4` / blob `f0ad94a35` **on all
  nine**, both verified directly. A peer session then landed `ce2b6d5d7` (a `master`-only `TODO.md`
  commit) in the gap between recording them and the first write. `git status --short` read **clean
  immediately before the write** and was truthful — the peer had *committed*, not left a dirty tree,
  so the L1183 check cannot see it and neither can gate 7 or gate 10. 🔑 **What caught it was the
  insert script asserting BYTE GROWTH against a byte count it had taken earlier**: 124,667 measured,
  127,034 found. An edit-in-place that trusted `status` would have committed a plan citing two
  anchors that no longer existed. **A recorded anchor is a measurement with a timestamp; re-verify
  it at the write, not at the plan.**
- ✅ **RESOLVED, and the resolution is the same lesson pointing the other way.** This bullet was
  written as *"the nine-way blob is BROKEN — `master` `d6b90cb4d`, the eight bands `f0ad94a35`"*,
  measured directly and true when measured. **It was already stale when written**: the peer
  propagated `ce2b6d5d7` in that same window. Re-measured — **one blob `d6b90cb4d` on all nine**,
  and `git log --all --grep='Backport-of: ce2b6d5d7'` returns **8**. 🔑 **Both halves of this
  section's anchor story are the same defect** — I caught the peer's stale claim by re-measuring
  and then published a stale claim of my own from a measurement two minutes older. Neither read was
  careless; both were timestamps. **The only safe form is the command, never the number** — which
  is why the verification line below is a command and this bullet keeps its refuted text instead of
  quietly showing the right answer.
- each of 64.1–64.3 lands as its **own commit**, so any one reverts alone.

### 64.1 — `build.gradle:2`'s plugin id: MEASURED, and it is load-bearing

Closes the L431 box. The row asked what the **bare** `fabric-loom` id does on the `1.21.x` bands and
recorded the answer as *inferred, not measured*. **Now measured, from Loom 1.17.13's own bytecode.**

Loom registers **five** plugin ids, not two — `META-INF/gradle-plugins/*.properties`:

| plugin id | implementation class |
|---|---|
| `fabric-loom` | `LoomGradlePlugin` |
| `net.fabricmc.fabric-loom` | `LoomNoRemapGradlePlugin` |
| `net.fabricmc.fabric-loom-remap` | `LoomRemapGradlePlugin` |
| `net.fabricmc.fabric-loom-companion` | `LoomCompanionGradlePlugin` |
| `net.fabricmc.fabric-loom-repositories` | `LoomRepositoryPlugin` |

⚠️ **The wrapper classes alone do NOT answer the question, and reading only those gives the
wrong answer.** Both `LoomNoRemapGradlePlugin` and `LoomRemapGradlePlugin` merely
`plugins.apply("fabric-loom")` — the no-remap one first throwing
`IllegalStateException("net.fabricmc.fabric-loom must be applied before fabric-loom")` if the bare id
is already applied. On that evidence the two ids look behaviourally identical. **They are not.**

The branch is in `LoomGradleExtensionImpl`'s constructor, which asks *which id was applied*:

```java
if (project.getPluginManager().hasPlugin("net.fabricmc.fabric-loom")) {
    disableObfuscation.set(true);
    disableObfuscation.finalizeValue();          // forced, and unoverridable
} else {
    disableObfuscation.set(project.provider(...));   // computed
    disableObfuscation.finalizeValueOnRead();
}
dontRemap.set(disableObfuscation.map(d -> d || getBooleanProperty(project, "fabric.loom.dontRemap")));
```

| | `master` + `mc/26.1.2` — `net.fabricmc.fabric-loom` | the seven bands — bare `fabric-loom` |
|---|---|---|
| `disableObfuscation` | **forced `true`**, `finalizeValue()` | computed, `finalizeValueOnRead()` |
| `dontRemap` | `true` | computed ‖ `-Pfabric.loom.dontRemap` |
| shipping artifact | plain `jar` — no remap tasks exist | `remapJar` |

✅ **So the row's parenthetical was correct** — `net.fabricmc.fabric-loom` really is the explicit
non-remap id — and §35's *"Loom registers NO `remapJar` on `26.x`"* now has its mechanism. §35
attributed it to `build.gradle` naming no mappings artifact; that is a **consistent second half**,
not the cause. The id forces it on its own.

🔴 **The finding that matters is the guard-shaped one: `build.gradle:2` is a REQUIRED per-band
difference that NO gate looks at.** `build.gradle` is not in gate 10's identity set (it must differ),
and gate 11 compares `gradle.properties` keys only. Unifying line 2 across branches is exactly the
kind of tidy-up that reads as correct in review — and it would make the seven bands ship
**unremapped, yarn-named jars** that cannot run, with every existing gate green.

**Build:** `BandLoomRemapPostureTest` — a per-branch, checkout-local test (the
`BandDocsMatchRealityTest` shape: it asks *"is this branch self-consistent?"*, needs no remote and no
cross-branch view, so it is correct on all nine). It parses `build.gradle` and asserts the posture is
coherent in **both** directions:
- applies `net.fabricmc.fabric-loom` ⇔ there is **no** `mappings` dependency line
- bare `fabric-loom` ⇒ `mappings` present **and** loader/fabric-api on `modImplementation`
- qualified id ⇒ loader/fabric-api on plain `implementation`

### 64.2 — R12 Residual 1: a new skill is wired to nothing and nothing goes red

Not a checkbox — the open residual under **R12** in the risk register:
*"the map is still a hand-maintained list; a NEW skill whose items postdate the floor is added to
`PrimarySkillType` and to nothing else, and nothing goes red. Auditing skills against required ids
is not yet mechanical."*

Today `SkillAvailability.GATED` is `Map.of(SPEARS, MACES)` against **25** `PrimarySkillType`
constants. The other 23 are ungated **by assumption**, recorded nowhere.

**Build:** a partition guard — every `PrimarySkillType` constant must be either in `GATED` or in an
explicitly declared *acknowledged-ungated* set. A constant in neither fails the build, naming the
constant and both remedies. Adding a skill then forces a recorded decision instead of a silent
default.

⚠️ **The vacuity trap here is real and this repo has hit its shape ~17 times.** `MACES` is
**inert on every in-scope version** (R-x withdrew the `1.20` line; `Items.MACE` ships from `1.20.5`),
so the disabling half is reachable only through `setSupportedForTesting`. A partition test written
against the live enum can therefore pass forever without ever being *able* to fail. **The partition
check is extracted as a pure function over an injected skill universe**, and a case feeds it a
synthetic unknown constant and asserts it is reported — so the guard is shown to say NO, not just
observed saying YES. 🔑 A one-sided guard pair proves only that it can say NO (§61.6).

### 64.3 — the `--require-bands` floor: one declared list, not four hand-kept numbers

The L1147 box. The floor is hand-maintained in `.github/workflows/drift-audit.yml` (`BAND_COUNT: '8'`)
and in ship-gate steps 9, 10 and 11.

🔴 **The obvious fix is VACUOUS and must not be built.** Deriving the floor from the same
`band_branches()` enumeration the scripts glob makes the comparison `len(x) >= len(x)` — always
true. `--require-bands` exists *precisely* because that enumeration can come back short (a shallow
clone, a rename, missing remote refs) while the audit still prints *"No drift"*. A derived floor
would delete the guard and leave it looking present.

**Build:** `scripts/expected-bands.txt` — a committed declaration of the band branch names, read as
the floor. It is **independent** of the live enumeration, which is the whole point, and it lands
under `scripts/**`, already in gate 10's identity set, so it is kept byte-identical on all nine
branches with no new mechanism. Two upgrades fall out for free:
- **count → set.** A *renamed* band keeps the count and breaks the set; the number could never see it.
- **one place, not four.** Recipe step x.9 becomes *"add a line"* instead of *"raise four numbers"*.

⚠️ **It is still hand-maintained, and saying otherwise would be the same false-closure this file
keeps catching.** What changes is the count of copies and the strength of the check, not the fact
that a human declares it.

### What I am NOT doing

- **Not pushing.** The §60 hold stands; `master` and the eight bands stay ahead of `origin`.
- **Not deleting the 56 stale tags** — still unauthorised, still an open box.
- **Not touching the live play-test** — owner-only.
- **Not repairing the sixteen `Backport-of:` trailers** — ruled deliberately unrepaired (L1168).
- **Not unifying `build.gradle:2`.** 64.1 measured the divergence as **required**; it gets a guard,
  not a merge.
- **Not lowering or removing `--require-bands`** — named in R-ab as the make-the-symptom-disappear
  move.
- **Not changing `SkillAvailability`'s log wording.** `"Version support: {} is available"` is a
  **parsed interface**, not prose: `gameplay_smoke_scenario.py:947`'s `_GATE_RE` reads it and the
  generator at :1040 rebuilds it. A reword scores **zero gated skills** instead of erroring — a
  green, blind run. Guarded by that script's `--self-test` `drop-gate-lines` case, which is run
  before the 64.2 commit.

### Blast radius

| step | touches | lost if wrong | comes back from |
|---|---|---|---|
| 64.1 | one new test file | nothing — additive | `git revert` of one commit |
| 64.2 | `SkillAvailability.java` + tests | a wrong partition reddens the build loudly; no runtime path changes | one commit |
| 64.3 | new `scripts/expected-bands.txt`, the workflow, the three scripts' floor read | a wrong list turns gates 9/10/11 **exit 2** — loud, never silently clean | one commit |
| propagation | eight band refs, from a scratch clone | band refs only; `HEAD` never moves and nothing is pushed | every pre-move band tip recorded below before the first move |

⚠️ **A `master`-only `TODO.md` edit silently breaks the nine-way blob identity and NEITHER gate
reports it.** Verify directly after every commit that touches it, never by a green gate:
`for b in master mc/26.1.2 mc/1.21.11 mc/1.21.10 mc/1.21.8 mc/1.21.5 mc/1.21.4 mc/1.21.3 mc/1.21.1;`
`do git rev-parse $b:TODO.md; done | sort -u | wc -l` **must print 1.**

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
- [x] ✅ **Manifest debt, piece 1 — CLOSED by §56.4 (2026-08-31).** Shipped as ship gate **12**:
      `probe-bands.py --check`, read-only, validating every record against **every version in
      `supported_minecraft_versions`** rather than `minecraft_version` alone. 7/7 mutations.
      ⚠️ The old wording of this row is corrected in §56.4 and repeated here because this is the copy
      people find: *"only this piece can"* tell a correct manifest from a correct manifest belonging
      to another branch was **FALSE**. §39 measured the `26.x` bands as differing on **zero of 1,424**
      records, so a manifest swapped between them resolves clean on both and this instrument is blind
      there **by construction**. That case is gate **10**'s (`manifest-identity-audit.py`, byte
      identity), and it is already green.
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
inert on every band by construction. **The other seven have no automation whatsoever.**
⚠️ **Twelve gates are listed. Update this sentence when you add one.**
🔴 **This line used to end *"nothing else counts them"*, and that was FALSE when written.**
L114 said *"the **eleven** gates"* (bolded here so this citation does not match the grep below) — 915 lines earlier, stale since §56.4 added gate 12, and found by a
peer session in §64, not by this warning. 🔑 **The countermeasure was attached to the list rather
than to the number that rots**, and it read as sufficient precisely because it sounded like an
inventory. Worse, the stale sentence's *claim* was still true — no gate reads the remote tag list,
gate 12 included — so reading it carefully left you agreeing with it. **Grep the number, not the
noun:** `grep -nE '(eleven|twelve|thirteen|[0-9]+) gates' TODO.md` against
`sed -n '/^## The ship gate/,/^## Risk register/p' TODO.md | grep -cE '^[0-9]+\. '`.

1. `./gradlew --no-daemon --stacktrace build -Pmod_version=$(grep -E '^mod_version=' gradle.properties | cut -d= -f2 | sed 's/-SNAPSHOT$//')`
   — exit 0, suite green. 🔴 **DO NOT MATCH `master`'s TOTAL.** This line read *“count
   matching `master` (~1719)”* long after the real figure passed 1,800 — and the stale number hid a
   second defect in the instruction itself: **per-band counts are SUPPOSED to differ.** §57's
   table — the newest one taken on a SINGLE date, so the only one a comparison may use — spans
   **six distinct totals across the nine** (1,876 ×3 · 1,877 · 1,878 · 1,880 · 1,882 ×2 · 1,884).
   **Differing counts are the evidence each branch ran its OWN suite; a uniform number would be
   the suspicious result.** Read your own branch's `N executed` off the JUnit XML and compare it
   against **that branch's** own last figure. A count that DROPS means something was disabled to
   get there.
   🔴 **NO BRANCH IS EXPECTED TO LEAD, and the claim is direction-agnostic ON PURPOSE.** §23
   recorded bands running *higher* than `master`; on §57's same-date table `mc/1.21.4` (**171 /
   1,884**) is above `master` (**170 / 1,882**) while six bands sit below it. Both readings are
   real, and **either one stated as a rule sends someone to “fix” a branch that is fine.**
   ⚠️ **Do not compare across dates.** `master`'s 1,882 sits above every figure in §56.2's table
   purely because those predate §57 and §56.4 — a cross-date artifact, not a fact about `master`.
   **A figure from one date and a figure from another do not subtract, in either direction.**

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
   🔑 **Run it across the whole DECLARED RANGE, not just `minecraft_version`** —
   `scripts/version-sweep.sh` (§61) drives gates 3, 5 and 6 over every entry in
   `supported_minecraft_versions`, resolving each version's fabric-api itself. ⚠️ **That script is a
   DRIVER, not a thirteenth gate** — do not count it as one.
   ⚠️ **`BOOT_CHECK_PORT=<n>`** when something already holds 25565. Until §61 a busy port spent 420
   seconds and then reported exit **1** — the mod is bad — for a purely environmental fact.
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
   ⚠️ **`BREW_SMOKE_PORT=<n>`**, as gate 3. 🔴 **And read the exit code here too, which until §61 you
   could not:** `both` mode captured each run's output with `$( )` and never read `$?`, so an
   ENVIRONMENT refusal was discarded and the run reported `❌ mcMMO did not brew` — having also
   printed `✅ mcMMO consumed the ingredient` about a server that never started. It now exits **2**.
   ⚠️ **The mcMMO config is regenerated every run as of §61.** It used to persist, and because the
   work dir is keyed on `$mode` rather than `$MC`, one 2026-08-14 tree served all sixteen versions.
6. `scripts/gameplay-smoke.sh` — **36 passed / 0 failed / 0 inconclusive** on `master`, and
   `GAMEPLAY_SMOKE_CONTROL=1` must **fail**.
   ⚠️⚠️ **The control run INVERTS its exit code**: **0** means it failed as it must, **1** means it
   PASSED without mcMMO and the scenario discriminates nothing. A driver that assumes the ordinary
   convention brands every correct control vacuous.
   ⚠️ **`GAMEPLAY_SMOKE_PORT=<n>`**, as gates 3 and 5.
   ⚠️ **This number moves whenever a phase is added, and it went stale unnoticed once already** — it
   read `29/29` through both §46 (+3) and up to §47 (+3), i.e. the caveat-expiry pass missed it twice
   because the phase commits touched the scenario file and not this line. The total is
   `3 + <version gates the boot log declares> + sum(len(up) + len(flat))` over the phase table, so a
   band declaring a different number of gates legitimately differs by that much. **Read the count out
   of `PHASES` rather than trusting this line**, and a total *below* the floor is the scorer's own
   anti-vacuity failure, not a phase failure.
7. `python scripts/expected_bands.py --self-test` **then** `--verify`, **then**
   `python scripts/drift-audit.py --self-test` **then**
   `--master master --require-bands "$(python scripts/expected_bands.py --count)"` — **0 MISSING on
   every band**. ⚠️ It audits `origin/master`, so **push first, then audit**.
   🔑 **§64.3: the floor is declared once, in `scripts/expected-bands.txt`.** `--verify` is the
   half a count cannot do — it catches a **renamed** band, which leaves the count untouched.
   ⚠️⚠️ **It cannot see a docs-only commit** (Phase 21, defect B): docs are excluded from
   `PROPAGATABLE_PREFIXES` by design, so a docs edit propagates **iff its commit also touched `src/`**.
   Five bands once documented Agility as live while their jars had it retired, and the auditor printed
   *"No drift"* with **unchanged counts** throughout. A green run is not evidence about docs.
8. `scripts/ci-watch.sh --mutate` **then** `scripts/ci-watch.sh HEAD` — **after** the push; the only
   gate downstream of it. ⚠️ **Run it FROM the branch you pushed**, or it fails closed at exit 3
   (*cannot tell*). `CI_WATCH_BASE=<sha before the push>` is the override when the reflog is gone.
9. `python scripts/manifest-identity-audit.py --self-test` **then**
   `--require-bands "$(python scripts/expected_bands.py --count)"` —
   **0 collisions**; every branch's `scripts/mc-surface.txt` distinct.
   ⚠️ **Defaults to `origin/**`, so push first — or pass `--local`.**
   ⚠️ **Exit 2 is not a pass** — fewer than two branches means zero pairs compared.
   🔑 **Distinct is not correct.** Six manifests that all differ can all six be wrong.
10. `python scripts/branch-file-identity-audit.py --self-test` **then**
    `--require-bands "$(python scripts/expected_bands.py --count)"` —
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
11. `python scripts/gradle-key-identity-audit.py --self-test` **then**
    `--require-bands "$(python scripts/expected_bands.py --count)"` —
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

12. `python scripts/probe-bands.py --self-test` **then** `python scripts/probe-bands.py --check` —
    **exit 0**. Every record in this branch's `mc-surface.txt` must resolve against the merged jar of
    **every version in `supported_minecraft_versions`**, not just `minecraft_version` (§56.4).
    🔑🔑 **The old single-version control is the defect this replaces.** `mc/26.1.2` ships to `26.1`,
    `26.1.1` and `26.1.2` and validated **only the last** — so a manifest naming a symbol that does
    not exist on `26.1` passed the probe, passed the build, passed the suite, and reached players as
    a `NoSuchMethodError`. **Two internally consistent facts with nothing joining them**, the fifth
    instance after §50, §52, §55 and §56.3.
    ⚠️ **An ABSENT on the primary and an ABSENT on a secondary are DIFFERENT findings** and the
    script says which: on `minecraft_version` the mod demonstrably compiles, so an ABSENT is a bug in
    the probe (`PROBE IS UNTRUSTWORTHY`); on any other declared version nothing compiles anything, so
    an ABSENT is a real shipping defect (`SHIPPING DEFECT`). Do not collapse the two.
    ⚠️ **`--check` is READ-ONLY, and that is load-bearing.** A bare run writes the tracked
    `plans/BAND_TABLE.md`, so a gate falling through to the writer would regenerate its own subject —
    the P16-1 defect. Proved read-only across a *failing* run, not just a passing one.
    ⚠️ **`--check` refuses `--allow-control-failures`** (exit 2). A gate that can be told to pass is
    not a gate.
    ⚠️ **A declared version whose jar is not Loom-cached is REFUSED (exit 3), never skipped** — a
    skipped control reads as a clean run, which is how §37 printed *"No drift"* over an exit of 2.
    ⚠️ **Exit 1 means `minecraft_version` is not in `supported_minecraft_versions`** — the branch
    compiling against a version it does not claim to ship to. Fix `gradle.properties`, not the script.

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
| **R12** | **A skill is inert on a band and nothing says so** | 🟡 **MITIGATED 2026-08-19 (§22.1).** `SkillAvailability` now carries a **skill → required-id-paths** map rather than one field per skill; `MACES` is gated alongside `SPEARS`, and gating the next one is a single `GATED` entry with no call-site edit. The javadoc claim that every other skill *"predates the floor of the supported range"* is gone — it was load-bearing prose and R-v falsified it in a day. ⚠️ **R-x makes that sentence true again and it stays out**: it was only ever true by accident of the floor. Proven by 21 tests (was 15), and by mutation: making the gate dead (`return true`) reddens exactly the 4 wiring tests. ⚠️ **The registry-driven test did NOT fail under that mutation** — this band has both items, so only the `setSupportedForTesting` seam reaches the disabling half. Vacuity confirmed empirically, not argued. ✅ **Residual 1 — CLOSED by §64.2 (2026-09-10).** It read *"the map is still a hand-maintained list; a NEW skill whose items postdate the floor is added to `PrimarySkillType` and to nothing else, and nothing goes red. Auditing skills against required ids is not yet mechanical."* `UNGATED` + `SkillGatePartitionTest` make the ungated case an explicit claim: every constant must be in exactly one of the two sets, checked by a pure function over an **injected** universe so the rejection cases can build the state a new constant creates — which cannot be manufactured on a live enum, the reason a values()-walking test could pass forever without being able to fail. 🔑 It caught `WOODCUTTING` on its first run (26 constants, not 25). ⚠️ **The map is still hand-maintained**; what is now mechanical is that forgetting it is LOUD. ⚠️ **Residual 2 (R-x):** with the `1.20` line withdrawn, the `MACES` entry can never fire on any in-scope version — the only row that still exercises the gate on a real band is `SPEARS` |

---

## Carried debt (open items only — closed rows are in the archives)

- [x] ✅ **The six `v2.2.050` tags — CLOSED by §63 (2026-09-10). Provenance settled, tags deleted.**
      They were an ordinary **moving release tag**, not an anomaly. `mod_version` **was**
      `2.2.050-SNAPSHOT` (upstream mcMMO's Bukkit number, borrowed); **R-s** retired it on 2026-08-18.
      🔑 **Why they sat on unrelated August commits:** R-s records that the tag step *force-deletes
      and re-pushes the ref*, and `mc<VER>-v2.2.050` was **re-used on every push for roughly a month**
      because nothing forced a bump. A tag that is re-pointed on every push is not a release marker;
      it lands wherever that push's HEAD was. R-s's own note measures `mc1.21.11-v2.2.050` as
      *local `f18cbef82` vs origin `44e3dc1d0`* — and `44e3dc1d0` is **exactly** where this clone's
      copy pointed, so what survived here was the origin side of that very measurement.
      🔑 **Why the remote is clean and the local copy was not:** R-s's reap sweep retired each band's
      `2.2.050` release *and its remote tag* automatically, and **`git fetch` never deletes a local
      tag** without `--prune-tags`. Nothing pushed, nothing outward-facing.
      ⚠️ The old text warned *"deleting a tag DRAFTS its release"*. True in general, **inapplicable
      here** — no remote tag, no remote release, so there is nothing to draft. Stated rather than
      assumed, because this repo already collected six orphaned drafts making that mistake.
      ↩️ **Undo:** `git tag mc1.21.3-v2.2.050 f3ef33c0c` · `mc1.21.4 4b2716be6` · `mc1.21.5 edd7a8932`
      · `mc1.21.8 6c4ec8db4` · `mc1.21.10 1608d5084` · `mc1.21.11 44e3dc1d0`. All six commits are
      reachable from a live branch, so the delete orphaned nothing (verified for all 62, not just six).

- [ ] ⬜ **The CR-strip at `gameplay-smoke.sh:466` is one REFACTOR from a silent catastrophic
      no-op** (raised by §63, 2026-09-10, jointly with a peer session).
      🔴 **The shipped code is CORRECT — this is a latent hazard, not a defect.** `line="${line%$'\r'}"`
      strips properly at top level. **Move that same line inside a command substitution and it becomes
      a silent no-op**, because an unquoted `$'\r'` re-lexes to EMPTY inside `$( )`, so `${line%}` strips
      an empty suffix. Measured: top level `73746f70 0d` → `73746f70`; nested → `73746f70 0d` unchanged.
      Same syntax, same exit status, **no error**.
      🔴 **The symptom is the documented catastrophic one.** That line's own comment records that the
      harness's first run *"lost every `gamerule`, every `mine continuous` and every `attack
      continuous`"* — brigadier reads `false\r` as an invalid boolean — while commands with a greedy
      last argument still went through, **so the run looked like a partly-working scenario rather than
      a broken pipe.** A green-ish smoke run is the failure mode.
      ⚠️ **Second site: `gen-milestone-advancements.sh:272`**, identical construct.
      ✅ **`ci-watch.sh:423-425` is NOT affected** — it strips `$'\t'`, and **TAB survives the re-lex**;
      measured, nested and top level both yield `field`. Only **CR** is discarded. Do not "fix" those.
      ✅ **The immune form, if the code must move:** hold the CR in a variable —
      `CR=$(printf '\r'); line="${line%"$CR"}"` — measured correct **nested and at top level**.
      🔑 **Why this is a row and not a footnote: nothing in this repo asserts that the strip strips.**
      No test, no gate. Tidying that line into a shared helper called via `$( )` is an ordinary,
      well-intentioned refactor, and every instrument would stay green.
      ✅ **Not a hazard for `prop()`** (`boot-check.sh:27`, `brew-smoke.sh:53`, `gameplay-smoke.sh:307`):
      `gradle.properties` is 100% CRLF, but that is **output**-side, where `$( )` strips only the
      trailing `\r`\n` — and MSYS `grep` has already stripped the CR from the line it emits, with
      `tr -d '[:space:]'` as an explicit second guard. **Argument side and output side behave
      OPPOSITELY**; see `.agent/memory/gotchas.md` under 2026-09-10.

- [ ] ⬜ **56 MORE stale local-only tags, same cause — owner's call, raised by §63 (2026-09-10).**
      🔑🔑 **"Six" was a lower bound, and the count was never the finding.** Measured:
      `comm -23 <(git tag -l | sort) <(git ls-remote --tags origin | sed 's|.*refs/tags/||' |
      grep -v '\^{}' | sort -u)` returns **62** — the six `v2.2.050` plus **56** superseded release
      tags (`v1.0.0` … `v1.3.3`) the reap sweep retired on the remote and nobody pruned locally.
      **Local 71, remote 10.** This is the same shape as the `config.yml` row that understated its
      defect by 26×: *a carried row naming a specific defect is a lower bound, never a count.*
      ⚠️ **Not deleted, deliberately** — the owner authorised the six, and 62 is a different blast
      radius than 6. All 62 were reachability-checked (0 unreachable), so the delete is safe whenever
      it is wanted. 🔴 **Do NOT reach for `git fetch --prune --prune-tags`**: it re-queries the remote
      and deletes whatever is not in the answer, so a network hiccup returning an empty tag list
      deletes **all 71**. Freeze the list to a file, read it, delete from the frozen list.
      ✅ **The other direction is CLOSED:** `mc26.1.2-v1.3.4` was on the remote and un-fetched here;
      §63 fetched it, so the local set is no longer missing anything (`comm -13` returns 0).
      🔑 **The lesson is about the INSTRUMENT and outlives the counts.** `git tag --list` was wrong in
      **both** directions at once — 62 tags the remote did not have, one it did — which is why it is the
      wrong instrument for *"what shipped"* even now that the two agree. **Agreement today is not a
      property of the instrument.** `git ls-remote --tags origin` is the instrument. A local tag list
      answering a question about releases is how §57 nearly missed a band being a release behind.
      ⚠️⚠️ **This row's own text was stale within the hour, and §63 wrote it that way:** the
      *"was never fetched here"* sentence was authored **before** the same session ran the fetch that
      falsified it. 🔑 **A fix and the sentence describing it are two separate writes, and only the
      first one is on anybody's checklist** — which is the whole reason the caveat-expiry pass greps for
      the **symptom** rather than the file just edited. Caught by a peer session re-reading the diff.
- [x] ✅ **CLOSED by §60 (2026-09-01) — all seven now boot, brew and play, and with §59's nine primaries that is ALL 16 declared versions.** Every one scored gate 3 clean, gate 5 with its vanilla control discriminating, and gate 6 **36 / 0 / 0** with a meaningful control. 🔑 **Closing it required a script change, not just runs**: `brew-smoke.sh` could only read `gradle.properties` (`2e29ec0cd`). 🔴 **And the sweep found a THIRD instance of the class** — `gameplay-smoke.sh` warned that a run would fail without fabric-api and then ran it, turning five environment failures into “the mod is bad” **and making their controls vacuous** (`801afafdd`). The original row read:
      Raised by §59, measured across all nine `supported_minecraft_versions` (2026-09-01):

      | band | declared | primary | NEVER exercised by gates 3/5/6 |
      |---|---|---|---|
      | `mc/26.1.2` | `26.1, 26.1.1, 26.1.2` | `26.1.2` | **`26.1`, `26.1.1`** |
      | `mc/1.21.8` | `1.21.6, 1.21.7, 1.21.8` | `1.21.8` | **`1.21.6`, `1.21.7`** |
      | `mc/1.21.10` | `1.21.9, 1.21.10` | `1.21.10` | **`1.21.9`** |
      | `mc/1.21.3` | `1.21.2, 1.21.3` | `1.21.3` | **`1.21.2`** |
      | `mc/1.21.1` | `1.21, 1.21.1` | `1.21.1` | **`1.21`** |

      `master`, `mc/1.21.11`, `mc/1.21.5` and `mc/1.21.4` declare exactly their primary and are
      genuinely covered. **§59 ran all nine bands green — at `minecraft_version` only.**
      🔑🔑 **This is §56.4's defect reappearing in three more gates, and that is the point.** §56.4
      found *"the manifest control validated ONE version while a band ships a RANGE"* and fixed it
      **for `probe-bands.py` alone** (gate 12, which now spans `supported_minecraft_versions`).
      Gates **3, 5 and 6 still mean per-PRIMARY-VERSION while calling themselves per-band.**
      **Fixing an instrument does not fix the CLASS.** A player installing the band's jar on `1.21.6`
      — which the release page tells them is supported — is running a configuration no gate has ever
      executed. ⚠️ **It is also not hypothetical for `26.1`/`26.1.1`**: those two were the exact pair
      §56.3 found gate 4 was blind to on a shipped band.
      ✅ **Mechanically closable — measured, not assumed.** Every one of the seven has fabric-api
      builds on `maven.fabricmc.net` (`1.21`→23, `1.21.2`→15, `1.21.6`→27, `1.21.7`→4, `1.21.9`→24,
      `26.1`→34, `26.1.1`→3), and gates 3 and 6 already take `<jar> <MC> <loader> <fapi>` as
      arguments, so they need no change at all — only driving.
      🔴 **Gate 5 is the one that cannot**: `brew-smoke.sh` reads `minecraft_version`,
      `loader_version` and `fabric_version` from `gradle.properties` with **no override**, so it can
      only ever test a band's primary. Its two siblings take them as arguments. Closing gate 5's half
      means giving it the same three arguments — a real change to a shared script, and therefore a
      cherry-pick to all nine branches under the identity guard.
      ⚠️ **Do not close this row by running gates 3 and 6 alone and calling the range covered** —
      that is the same "one instrument fixed, class still open" move this row exists to name.

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

- [x] ✅ **Manifest debt piece 1 — CLOSED by §56.4 as ship gate 12.** Piece 2 shipped as
      `scripts/manifest-identity-audit.py` (Phase 18).
      🔑 **This row and *Other open work* disagreed — one ⬜, one ✅ — and the disagreement was
      resolved by reading the CODE, not by trusting the more recently edited row.** Piece 1 asked to
      *“validate manifest symbols against the band's merged jar; refuse a manifest naming a symbol
      the band does not have.”* `probe-bands.py --check` resolves every `mc-surface.txt` record
      against the merged jar of every declared version and returns **exit 3** with `❌ SHIPPING
      DEFECT` when one is absent. That is the mechanism, and it is **wider** than asked (every
      version in `supported_minecraft_versions`, not just the primary).
      🔴 **But the row's stated RATIONALE was false and does not close with it.** It claimed *“only
      this piece can”* tell a correct manifest from a correct manifest belonging to another branch.
      It cannot: §39 measured the `26.x` bands as differing on **zero of 1,424** records, so a manifest
      swapped between them resolves clean on both and gate 12 is blind there **by construction**.
      That case belongs to gate **10** (`manifest-identity-audit.py`, byte identity). ⚠️ **Closing a
      mechanism does not close the claim someone attached to it.**
- [ ] 🟡 **The `--require-bands` floor is hand-maintained** — now in **one** place,
      `scripts/expected-bands.txt`, read by every consumer as
      `python scripts/expected_bands.py --count`.
      ⚠️ **The box stays OPEN on purpose: a human still declares the list.** §64.3 changed the
      number of copies and the strength of the check, not that fact, and the declaration file says
      so itself. It stays listed because nothing reminds you: a stale floor is under-strict and the
      audit still passes.
      🔴 **This row's BODY was stale for one commit and the checkbox was not, which is the
      distinction worth keeping.** It read *"hand-maintained in `.github/workflows/drift-audit.yml`
      and in ship-gate steps 9, 10 and 11 — **Now 8**, raised in §43.3"*, and `ccb97fc4e` deleted
      `BAND_COUNT` from that workflow while claiming to close this box. **The mechanism changed and
      the claim attached to it did not** — the row two above already says exactly that, so this is
      that lesson recurring one row later and inside the commit that cites it. Caught in review by a
      peer session before it propagated to nine branches, not by any gate.
      ✅ **What §64.3 added beyond fewer copies: a count became a SET.** A *renamed* band keeps the
      count and breaks the set; `--verify` names both directions. Measured — renaming `mc/1.21.10`
      to `mc/1.21.010` leaves `--count` at 8 and `--verify` still exits 1.
      ⚠️ **8, not 9** — the declaration lists `mc/**` only, and `master` lives outside that
      namespace; one too many returns exit 2 while the same run still prints *"No drift"*.
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

- [ ] ⬜ **Sixteen `mc/**` commits of 2026-08-31 carry a `Backport-of:` trailer git's own parser
      CANNOT see.** §55's propagation appended the trailer directly after the last body line, with no
      blank line, so `git log --format='%(trailers:key=Backport-of,valueonly)'` returns **empty** for
      all sixteen. ✅ **Gate 7 is unaffected and this is deliberately NOT being repaired:**
      `drift-audit.py` matches `^\s*Backport-of:\s*([0-9a-fA-F]{7,40})\s*$` as a **multiline regex
      over the message text** (line 72), not through git's trailer parser, so the audit reads them
      correctly — a history rewrite would cost more than the defect. 🔴 **The reason this is a row
      and not a footnote: anything NEW built on `%(trailers)` will silently skip exactly those
      sixteen commits and report a clean pass.** Check against `drift-audit.py`'s regex, or accept
      the gap knowingly. ✅ The remedy for new work, verified on all eight bands:
      `printf '%s\n\nBackport-of: %s\n' "$(git log -1 --format=%B)" "$SRC"` — **the DOUBLE `\n` is
      the fix**, because `$(...)` strips `%B`'s own trailing newline. Verify with git's parser, and
      note the control: the source commit on `master` must return **empty**. A check asserting
      non-empty on all nine would pass a trailer wrongly applied to the source.

- [ ] ⬜ **Two agents in ONE working copy: `git add <path>` silently commits the other's work.**
      Every collision note in this repo assumes the conflict is on a **branch or a checkout** — two
      agents switching `HEAD`, a build reading a half-switched tree. 🔑🔑 **The hazard that actually
      nearly landed on 2026-09-01 was inside a SINGLE FILE's working copy**, and it is worse because
      git raises nothing: `git add TODO.md` stages the **whole file**, so a one-row edit would have
      shipped 44 lines of another session's unfinished §58 plan **under the wrong commit message**,
      with no conflict, no warning and an entirely ordinary-looking diff. ⚠️ **Path-scoping does NOT
      save you** — `git add <path>` is precisely the thing that does it, because their in-flight file
      and your target were the same file; scoping only helps when the two agents are in different
      files, which is the case everyone pictures and was not the case here. **The rule:**
      `git status --short` **and** `git diff --numstat <your exact paths>` **immediately before**
      staging — not at the top of the task — and treat a modified file you did not modify as a
      **stop, not a merge**. Never `git commit -a`. 🔑 **Liveness is irrelevant:** an uncommitted
      foreign diff is capturable whether that session is typing right now or stopped an hour ago, so
      the response never changes. ⚠️ **A peer's *"tree is free/clean"* is a TIMESTAMP, not a lock** —
      it was true when sent and false when acted on; require `git status --short` behind the claim
      and re-measure anyway, because their read is as stale as yours by the time it arrives.

- [ ] ⬜ **To tell live editing from residue read the MTIME, not a diff-size delta.** ⚠️ **This row
      exists because the rule was first recorded BACKWARDS and had to be falsified by another
      session.** The original claim was that two `--numstat` reads seconds apart distinguish active
      editing from a stale dirty tree (`62/3 → 66/4` across 37 seconds "proved someone typing").
      **False:** the mtimes were `01:22:42` and `01:23:06`, so those lines landed in the **two
      seconds after the first read** and the file was then **static for 35 seconds**. 🔑 **Two
      samples of a DERIVED quantity bracketing a single write are indistinguishable from continuous
      editing** — a step function read as a keystroke stream. 🔑🔑 **The direct measurement was in
      the same `ls -l` output the whole time**; the proxy was chosen with the better instrument
      already in hand, which is the part that generalises. ⚠️ And per the row above, the answer
      **changes no decision** — which is the tell: *a rule that cannot change a decision should be
      suspected of being decoration before it is trusted as detection.* The same test that has been
      finding vacuous guards here all along, applied to a note instead of an assertion.

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
